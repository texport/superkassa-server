package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.TimeValidationResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

@Suppress("HttpUrlsUsage", "RedundantBackticks")
class SystemTimeGuardTest {

    private val testClock = object : ClockPort {
        var currentTime = System.currentTimeMillis()
        override fun now(): Long = currentTime
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
    fun testNormalTimeValidation() {
        val result = SystemTimeGuard.validate(testClock)
        // Note: Reference fetch might fail if offline, but it should fall back to passing the validation
        assertTrue(result.ok, "Normal time should be valid (or fail only on reference skew if online clock is off)")
    }

    @Test
    fun testRangeValidation() {
        // 1. Time in the past (2010)
        testClock.currentTime = 1262304000000L // 2010-01-01
        var result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)

        // 2. Time in the future (2110)
        testClock.currentTime = 4417968000000L // 2110-01-01
        result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok)
        assertEquals("RANGE", result.reason)
    }

    @Test
    fun testMonotonicClockSkewDetection() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now

        // First validation to establish baseline
        var result = SystemTimeGuard.validate(testClock)
        assertTrue(result.ok)

        // Inject skew manually: wall time remains at 'now', but monotonic time elapsed only 1 second while wall was set 3 minutes ahead
        val lastWallMsField = SystemTimeGuard::class.java.getDeclaredField("lastWallMs").apply { isAccessible = true }
        val lastMonoNsField = SystemTimeGuard::class.java.getDeclaredField("lastMonoNs").apply { isAccessible = true }

        // Simulate last wall time was 3 minutes ago
        lastWallMsField.set(SystemTimeGuard, now - 3 * 60 * 1000L)
        // Simulate only 1 second elapsed in monotonic CPU clock
        lastMonoNsField.set(SystemTimeGuard, System.nanoTime() - 1_000_000_000L)

        // Validate -> skew should be 3 mins - 1 sec = 2 mins 59 secs (exceeding 2 min limit)
        result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok, "Validation must fail when manual system clock skew is detected")
        assertEquals("MONOTONIC_SKEW", result.reason)
    }

    @Test
    fun testReferenceClockSkewDetection() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now

        // Inject reference time that is 10 minutes in the past relative to system wall clock,
        // and set fetchedAt to now so it is cached and not fetched again.
        val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply { isAccessible = true }
        val referenceFetchedAtMsField = SystemTimeGuard::class.java.getDeclaredField(
            "referenceFetchedAtMs"
        ).apply { isAccessible = true }

        referenceMsField.set(SystemTimeGuard, now - 10 * 60 * 1000L) // Reference is 10 min behind
        referenceFetchedAtMsField.set(SystemTimeGuard, now) // Fetched just now (TTL check passes)

        val result = SystemTimeGuard.validate(testClock)
        assertFalse(result.ok, "Validation must fail when reference time skew exceeds limit")
        assertEquals("REFERENCE_SKEW", result.reason)
    }

    @Test
    fun testReferenceFetchThrottlingOnFailure() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now

        // Set referenceUrls directly
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:9999")

        try {
            // Reset lastFetchAttemptMs before starting
            val lastFetchAttemptMsField = SystemTimeGuard::class.java
                .getDeclaredField("lastFetchAttemptMs")
            lastFetchAttemptMsField.isAccessible = true
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            // First validation -> should attempt fetch and fail
            SystemTimeGuard.validate(testClock)

            val lastAttempt = lastFetchAttemptMsField.get(SystemTimeGuard) as? Long
            assertTrue(lastAttempt != null, "lastFetchAttemptMs must be set after fetch attempt")

            // Simulate second validation 10 seconds later (within cool-down of 60 seconds)
            testClock.currentTime = now + 10_000L

            // Set lastFetchAttemptMs manually to a sentinel value to verify it isn't overwritten
            val sentinel = now - 5000L
            lastFetchAttemptMsField.set(SystemTimeGuard, sentinel)

            SystemTimeGuard.validate(testClock)

            // It should not have performed a new fetch, so lastFetchAttemptMs should remain the sentinel
            val secondAttempt = lastFetchAttemptMsField.get(SystemTimeGuard) as? Long
            assertEquals(sentinel, secondAttempt, "Should not retry network fetch within cool-down period")
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
        }
    }

    @Test
    fun testSystemClock() {
        val now = SystemClock.now()
        val systemNow = System.currentTimeMillis()
        assertTrue(now in (systemNow - 1000)..(systemNow + 1000))
    }

    @Test
    fun testLogStatus() {
        // Test with valid clock
        testClock.currentTime = System.currentTimeMillis()
        SystemTimeGuard.logStatus(testClock)

        // Test with invalid clock (triggers the else branch)
        testClock.currentTime = 1262304000000L // 2010
        SystemTimeGuard.logStatus(testClock)
    }

    @Test
    fun testTrilingualMessage() {
        val successResult = TimeValidationResult(true)
        assertEquals(null, successResult.trilingualMessage())

        val failResult = TimeValidationResult(
            ok = false,
            reason = "TEST",
            messageRu = "Р",
            messageKk = "К",
            messageEn = "Е"
        )
        assertEquals("RU: Р | KK: К | EN: Е", failResult.trilingualMessage())
    }

    @Test
    fun testReferenceFetchSuccessWithMockServer() {
        val now = System.currentTimeMillis()
        val format = java.text.SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT")
        }
        val dateStr = format.format(java.util.Date(now))

        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.set("Date", dateStr)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val resultMocked = SystemTimeGuard.validate(testClock)
            assertTrue(resultMocked.ok, "Mock server success failed: reason=${resultMocked.reason}, msg=${resultMocked.messageEn}")
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }

    @Test
    fun testReferenceFetchMissingDateHeader() {
        val now = System.currentTimeMillis()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            // Send empty Date header to cause parsing failure
            exchange.responseHeaders.set("Date", "")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val result = SystemTimeGuard.validate(testClock)
            // Lacking a reference clock, it falls back to success (since wall time range check is valid)
            assertTrue(result.ok, "Missing date header validation failed: reason=${result.reason}, msg=${result.messageEn}")
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }

    @Test
    fun `test TimeValidationResult getters for coverage`() {
        val res = TimeValidationResult(false, "TEST", "RuMsg", "KkMsg", "EnMsg")
        assertEquals("RuMsg", res.messageRu)
        assertEquals("KkMsg", res.messageKk)
        assertEquals("EnMsg", res.messageEn)
    }

    @Test
    fun `test ensureReference cache and cooldown expirations`() {
        val now = System.currentTimeMillis()
        testClock.currentTime = now

        val referenceFetchedAtMsField = SystemTimeGuard::class.java.getDeclaredField("referenceFetchedAtMs").apply { isAccessible = true }
        val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply { isAccessible = true }
        val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply { isAccessible = true }

        // 1. Cache is expired (Fetched 11 minutes ago)
        referenceMsField.set(SystemTimeGuard, now - 1000L)
        referenceFetchedAtMsField.set(SystemTimeGuard, now - 11 * 60 * 1000L)
        // Cooldown has also expired (Attempted 2 minutes ago)
        lastFetchAttemptMsField.set(SystemTimeGuard, now - 2 * 60 * 1000L)

        // This will force validate to attempt a fetch. To prevent real network calls, set URLs to empty list
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = emptyList()
        try {
            SystemTimeGuard.validate(testClock)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
        }
    }

    @Test
    fun `test fetchReferenceTime with null date header`() {
        val now = System.currentTimeMillis()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            // Do not set Date header (it will be null)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val result = SystemTimeGuard.validate(testClock)
            assertTrue(result.ok)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }

    @Test
    fun testMonotonicClockSkewPartialNull() {
        val lastWallMsField = SystemTimeGuard::class.java.getDeclaredField("lastWallMs").apply { isAccessible = true }
        val lastMonoNsField = SystemTimeGuard::class.java.getDeclaredField("lastMonoNs").apply { isAccessible = true }

        // Setup: lastWallMs is not null, but lastMonoNs is null
        lastWallMsField.set(SystemTimeGuard, System.currentTimeMillis())
        lastMonoNsField.set(SystemTimeGuard, null)

        val result = SystemTimeGuard.validate(testClock)
        assertTrue(result.ok)
    }

    @Test
    fun testEnsureReferencePartialNull() {
        val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply { isAccessible = true }
        val referenceFetchedAtMsField = SystemTimeGuard::class.java.getDeclaredField("referenceFetchedAtMs").apply { isAccessible = true }

        // Setup: referenceMs is not null, but referenceFetchedAtMs is null
        referenceMsField.set(SystemTimeGuard, System.currentTimeMillis())
        referenceFetchedAtMsField.set(SystemTimeGuard, null)

        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = emptyList()
        try {
            SystemTimeGuard.validate(testClock)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
        }
    }

    @Test
    fun testFetchReferenceTimeWithFallbackToSecondUrl() {
        val now = System.currentTimeMillis()
        val format = java.text.SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT")
        }
        val dateStr = format.format(java.util.Date(now))

        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            exchange.responseHeaders.set("Date", dateStr)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls

        // First url is invalid and will fail, second url points to the mock server
        SystemTimeGuard.referenceUrls = listOf("http://invalid-url-12345.com", "http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val result = SystemTimeGuard.validate(testClock)
            assertTrue(result.ok)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }

    @Test
    fun testTrilingualMessageWithNullFields() {
        val result = TimeValidationResult(false, "TEST", null, null, null)
        assertNull(result.trilingualMessage())
    }

    @Test
    fun testFetchReferenceTimeWithBlankDateHeader() {
        val now = System.currentTimeMillis()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            // Send Date header with spaces to check isNullOrBlank() blank branch
            exchange.responseHeaders.set("Date", "    ")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val result = SystemTimeGuard.validate(testClock)
            assertTrue(result.ok)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }

    @Test
    fun testFetchReferenceTimeWithEmptyDateHeader() {
        val now = System.currentTimeMillis()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            // Send empty Date header
            exchange.responseHeaders.set("Date", "")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        val originalUrls = SystemTimeGuard.referenceUrls
        SystemTimeGuard.referenceUrls = listOf("http://127.0.0.1:$port")

        try {
            val lastFetchAttemptMsField = SystemTimeGuard::class.java.getDeclaredField("lastFetchAttemptMs").apply {
                isAccessible = true
            }
            lastFetchAttemptMsField.set(SystemTimeGuard, null)

            val referenceMsField = SystemTimeGuard::class.java.getDeclaredField("referenceMs").apply {
                isAccessible = true
            }
            referenceMsField.set(SystemTimeGuard, null)

            testClock.currentTime = now

            val result = SystemTimeGuard.validate(testClock)
            assertTrue(result.ok)
        } finally {
            SystemTimeGuard.referenceUrls = originalUrls
            server.stop(0)
        }
    }
}
