package io.github.texport.superkassa.jvm.time.impl

import kz.mybrain.superkassa.core.domain.port.ClockPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemTimeGuardGreyCasesTest {
    private companion object {
        const val MIN_ALLOWED_MS = 1_577_836_800_000L
        const val MAX_ALLOWED_MS = 4_102_444_800_000L
        const val REFERENCE_TTL_MS = 10 * 60 * 1000L
        const val RETRY_COOL_DOWN_MS = 60 * 1000L
        const val MAX_REFERENCE_SKEW_MS = 5 * 60 * 1000L
    }

    private val testClock = object : ClockPort {
        var currentTime: Long = 1_800_000_000_000L

        override fun now(): Long = currentTime

        override fun currentYear(): Int = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year

        override fun parseDateTimeToMillis(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int
        ): Long =
            java.time.LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }

    private val originalReferenceUrls = SystemTimeGuard.referenceUrls

    @BeforeEach
    fun resetGuardState() {
        setField("lastWallMs", null)
        setField("lastMonoNs", null)
        setField("referenceMs", null)
        setField("referenceFetchedAtMs", null)
        setField("lastFetchAttemptMs", null)
        SystemTimeGuard.referenceUrls = emptyList()
    }

    @AfterEach
    fun restoreReferenceUrls() {
        SystemTimeGuard.referenceUrls = originalReferenceUrls
    }

    @Test
    fun `validate accepts one millisecond after minimum allowed time`() {
        testClock.currentTime = MIN_ALLOWED_MS + 1

        val result = SystemTimeGuard.validate(testClock)

        assertNotEquals("RANGE", result.reason)
    }

    @Test
    fun `validate accepts one millisecond before maximum allowed time`() {
        testClock.currentTime = MAX_ALLOWED_MS - 1

        val result = SystemTimeGuard.validate(testClock)

        assertNotEquals("RANGE", result.reason)
    }

    @Test
    fun `validate rejects zero epoch as range error`() {
        testClock.currentTime = 0L

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `validate rejects negative epoch as range error`() {
        testClock.currentTime = -1L

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `validate rejects Long MIN_VALUE as range error`() {
        testClock.currentTime = Long.MIN_VALUE

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `validate rejects Long MAX_VALUE as range error`() {
        testClock.currentTime = Long.MAX_VALUE

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun `reference skew exactly at positive threshold is accepted`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setFreshReference(referenceMs = now - MAX_REFERENCE_SKEW_MS)

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok, "Skew exactly at threshold must be accepted")
    }

    @Test
    fun `reference skew one millisecond over positive threshold is rejected`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setFreshReference(referenceMs = now - MAX_REFERENCE_SKEW_MS - 1)

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("REFERENCE_SKEW", result.reason)
    }

    @Test
    fun `reference skew exactly at negative threshold is accepted`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setFreshReference(referenceMs = now + MAX_REFERENCE_SKEW_MS)

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok, "Negative skew exactly at threshold must be accepted")
    }

    @Test
    fun `reference skew one millisecond over negative threshold is rejected`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setFreshReference(referenceMs = now + MAX_REFERENCE_SKEW_MS + 1)

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("REFERENCE_SKEW", result.reason)
    }

    @Test
    fun `fresh reference cache prevents network attempt`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setFreshReference(referenceMs = now)

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok)
        assertNull(getField("lastFetchAttemptMs"))
    }

    @Test
    fun `stale reference is returned during active retry cooldown`() {
        val now = 1_800_000_000_000L
        val lastAttempt = now - RETRY_COOL_DOWN_MS
        testClock.currentTime = now
        setField("referenceMs", now - MAX_REFERENCE_SKEW_MS - 1)
        setField("referenceFetchedAtMs", now - REFERENCE_TTL_MS - 1)
        setField("lastFetchAttemptMs", lastAttempt)

        val result = SystemTimeGuard.validate(testClock)

        assertFalse(result.ok)
        assertEquals("REFERENCE_SKEW", result.reason)
        assertEquals(lastAttempt, getField("lastFetchAttemptMs"))
    }

    @Test
    fun `expired cooldown allows a new reference fetch attempt`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        setField("referenceMs", now)
        setField("referenceFetchedAtMs", now - REFERENCE_TTL_MS - 1)
        setField("lastFetchAttemptMs", now - RETRY_COOL_DOWN_MS - 1)

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok)
        assertEquals(now, getField("lastFetchAttemptMs"))
    }

    @Test
    fun `expired cache without reference urls keeps previous reference value`() {
        val now = 1_800_000_000_000L
        val oldReference = now - 1_000L
        testClock.currentTime = now
        setField("referenceMs", oldReference)
        setField("referenceFetchedAtMs", now - REFERENCE_TTL_MS - 1)

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok)
        assertEquals(oldReference, getField("referenceMs"))
        assertEquals(now, getField("lastFetchAttemptMs"))
    }

    @Test
    fun `malformed reference url falls back to local validation success`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        SystemTimeGuard.referenceUrls = listOf("://bad-url")

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok)
        assertEquals(now, getField("lastFetchAttemptMs"))
    }

    @Test
    fun `non http reference url falls back to local validation success`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        SystemTimeGuard.referenceUrls = listOf("file:/tmp/reference-time.txt")

        val result = SystemTimeGuard.validate(testClock)

        assertTrue(result.ok)
        assertEquals(now, getField("lastFetchAttemptMs"))
    }

    @Test
    fun `missing reference date header falls back to local validation success`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        val serverSocket = java.net.ServerSocket(0)
        val serverThread = Thread {
            serverSocket.accept().use { socket ->
                socket.getInputStream().read(ByteArray(1024))
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray()
                )
            }
        }
        serverThread.start()
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:${serverSocket.localPort}")

        try {
            val result = SystemTimeGuard.validate(testClock)

            assertTrue(result.ok)
            assertEquals(now, getField("lastFetchAttemptMs"))
        } finally {
            serverSocket.close()
            serverThread.join(1000)
        }
    }

    @Test
    fun `malformed reference date header falls back to local validation success`() {
        val now = 1_800_000_000_000L
        testClock.currentTime = now
        val serverSocket = java.net.ServerSocket(0)
        val serverThread = Thread {
            serverSocket.accept().use { socket ->
                socket.getInputStream().read(ByteArray(1024))
                socket.getOutputStream().write(
                    "HTTP/1.1 200 OK\r\nDate: not a date\r\nContent-Length: 0\r\n\r\n".toByteArray()
                )
            }
        }
        serverThread.start()
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:${serverSocket.localPort}")

        try {
            val result = SystemTimeGuard.validate(testClock)

            assertTrue(result.ok)
            assertEquals(now, getField("lastFetchAttemptMs"))
        } finally {
            serverSocket.close()
            serverThread.join(1000)
        }
    }

    @Test
    fun `system clock parses leap day`() {
        val millis = SystemClock.parseDateTimeToMillis(2028, 2, 29, 12, 30, 15)

        assertTrue(millis > 0)
    }

    @Test
    fun `system clock rejects invalid month`() {
        assertFailsWith<java.time.DateTimeException> {
            SystemClock.parseDateTimeToMillis(2026, 13, 1, 0, 0, 0)
        }
    }

    @Test
    fun `system clock rejects invalid leap day`() {
        assertFailsWith<java.time.DateTimeException> {
            SystemClock.parseDateTimeToMillis(2027, 2, 29, 0, 0, 0)
        }
    }

    @Test
    fun `system clock parsed seconds advance by one second`() {
        val first = SystemClock.parseDateTimeToMillis(2026, 7, 4, 10, 20, 30)
        val second = SystemClock.parseDateTimeToMillis(2026, 7, 4, 10, 20, 31)

        assertEquals(1_000L, second - first)
    }

    @Test
    fun `system clock current year matches UTC year`() {
        val expected = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year

        assertEquals(expected, SystemClock.currentYear())
    }

    private fun setFreshReference(referenceMs: Long) {
        setField("referenceMs", referenceMs)
        setField("referenceFetchedAtMs", testClock.currentTime)
    }

    private fun setField(name: String, value: Any?) {
        SystemTimeGuard::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(SystemTimeGuard, value)
        }
    }

    private fun getField(name: String): Any? {
        return SystemTimeGuard::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(SystemTimeGuard)
        }
    }
}
