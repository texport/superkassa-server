package io.github.texport.superkassa.jvm.time.impl

import io.github.texport.superkassa.core.domain.api.port.integration.ClockPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MoreSystemTimeGuardTests {

    private val testClock = object : ClockPort {
        var currentTime = System.currentTimeMillis()
        override fun now(): Long = currentTime
        override fun currentYear(): Int = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year
        override fun parseDateTimeToMillis(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int
        ): Long = 0L
    }

    @BeforeEach
    fun resetGuardState() {
        val lastWallMsField = SystemTimeGuard::class.java.getDeclaredField("lastWallMs").apply {
            isAccessible = true
        }
        val lastMonoNsField = SystemTimeGuard::class.java.getDeclaredField("lastMonoNs").apply {
            isAccessible = true
        }
        val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
            isAccessible = true
        }
        val referenceFetchedAtMsField = SystemTimeGuard::class.java.getDeclaredField("referenceFetchedAtMs").apply {
            isAccessible = true
        }
        val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
            isAccessible = true
        }

        lastWallMsField.set(SystemTimeGuard, null)
        lastMonoNsField.set(SystemTimeGuard, null)
        referenceMsField.set(SystemTimeGuard, null)
        referenceFetchedAtMsField.set(SystemTimeGuard, null)
        lastFetchAttemptMsField.set(SystemTimeGuard, null)
    }

    @Test
    fun `validate at exactly MIN_ALLOWED_MS is valid`() {
        testClock.currentTime = 1577836800000L // 2020-01-01T00:00:00Z
        val result = SystemTimeGuard.validate(testClock)
        assertNotEquals("RANGE", result.reason)
    }

    @Test
    fun `validate at MIN_ALLOWED_MS minus one ms is invalid`() {
        testClock.currentTime = 1577836800000L - 1
        val result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `validate at exactly MAX_ALLOWED_MS is valid`() {
        testClock.currentTime = 4102444800000L // 2100-01-01T00:00:00Z
        val result = SystemTimeGuard.validate(testClock)
        assertNotEquals("RANGE", result.reason)
    }

    @Test
    fun `validate at MAX_ALLOWED_MS plus one ms is invalid`() {
        testClock.currentTime = 4102444800000L + 1
        val result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `validate handles concurrent multi-threaded calls safely`() {
        val executor = Executors.newFixedThreadPool(8)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)

        repeat(100) {
            executor.submit {
                try {
                    SystemTimeGuard.validate(testClock)
                } catch (_: Exception) {
                    errors.incrementAndGet()
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(0, errors.get(), "Concurrent validation threw exceptions")
    }

    @Test
    fun `validate monotonic check handles system clock drift backwards`() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now
        assertTrue(SystemTimeGuard.validate(testClock).ok)

        val lastWallMsField = SystemTimeGuard::class.java.getDeclaredField("lastWallMs").apply { isAccessible = true }
        val lastMonoNsField = SystemTimeGuard::class.java.getDeclaredField("lastMonoNs").apply { isAccessible = true }

        lastWallMsField.set(SystemTimeGuard, now)
        lastMonoNsField.set(SystemTimeGuard, System.nanoTime() - 1_000_000_000L)
        testClock.currentTime = now - 3 * 60 * 1000L

        val result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok, "Should fail when clock shifts backward")
        assertEquals("MONOTONIC_SKEW", result.reason)
    }

    @Test
    fun `validate monotonic check self-heals on subsequent call after skew`() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now
        assertTrue(SystemTimeGuard.validate(testClock).ok)

        val lastWallMsField = SystemTimeGuard::class.java.getDeclaredField("lastWallMs").apply { isAccessible = true }
        val lastMonoNsField = SystemTimeGuard::class.java.getDeclaredField("lastMonoNs").apply { isAccessible = true }

        // Introduce skew
        lastWallMsField.set(SystemTimeGuard, now - 3 * 60 * 1000L)
        lastMonoNsField.set(SystemTimeGuard, System.nanoTime() - 1_000_000_000L)

        // Эталона нет — первый вызов отказывает: подтвердить время нечем.
        SystemTimeGuard::class.java.getDeclaredField("referenceMs")
            .apply { isAccessible = true }.set(SystemTimeGuard, null)
        SystemTimeGuard::class.java.getDeclaredField("referenceFetchedAtMs")
            .apply { isAccessible = true }.set(SystemTimeGuard, null)
        SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs")
            .apply { isAccessible = true }.set(SystemTimeGuard, null)
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("https://reference.invalid")
        val result1 = try {
            SystemTimeGuard.validate(testClock)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
        }
        assertFalse(result1.ok)
        assertEquals("MONOTONIC_SKEW", result1.reason)

        // Базис сдвинут, часы идут ровно — следующий вызов проходит.
        testClock.currentTime = now + 1000L
        val result2 = SystemTimeGuard.validate(testClock)
        assertTrue(result2.ok, "Should self-heal and pass on subsequent call after baseline is updated")
    }
}
