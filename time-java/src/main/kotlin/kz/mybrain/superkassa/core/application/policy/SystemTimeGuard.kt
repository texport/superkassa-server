package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.TimeValidationResult
import kz.mybrain.superkassa.core.domain.port.TimeValidatorPort
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import kotlin.math.abs

/**
 * Проверка системного времени по принципу браузеров:
 * 1) диапазон разумного времени;
 * 2) контроль "скачков" по монотонным часам;
 * 3) сверка с внешним эталоном (HTTP Date).
 */
object SystemTimeGuard : TimeValidatorPort {
    private val logger = LoggerFactory.getLogger(SystemTimeGuard::class.java)
    private const val MIN_ALLOWED_MS = 1577836800000L // 2020-01-01T00:00:00Z
    private const val MAX_ALLOWED_MS = 4102444800000L // 2100-01-01T00:00:00Z
    private const val MAX_MONOTONIC_SKEW_MS = 2 * 60 * 1000L
    private const val MAX_REFERENCE_SKEW_MS = 5 * 60 * 1000L
    private const val REFERENCE_TTL_MS = 10 * 60 * 1000L
    private const val RETRY_COOL_DOWN_MS = 60 * 1000L // 1 минута кулдауна при ошибках сети
    internal var referenceUrls = listOf(
        "https://www.cloudflare.com",
        "https://www.google.com",
        "https://www.microsoft.com"
    )

    private val lock = Any()
    private var lastWallMs: Long? = null
    private var lastMonoNs: Long? = null
    private var referenceMs: Long? = null
    private var referenceFetchedAtMs: Long? = null
    private var lastFetchAttemptMs: Long? = null

    override fun validate(clock: ClockPort): TimeValidationResult {
        val now = clock.now()
        if (now !in MIN_ALLOWED_MS..MAX_ALLOWED_MS) {
            return TimeValidationResult(
                ok = false,
                reason = "RANGE",
                messageRu = "Системное время вне допустимого диапазона (2020-2100)",
                messageKk = "Жүйелік уақыт рұқсат етілген ауқымнан тыс (2020-2100)",
                messageEn = "System time is out of allowed range (2020-2100)"
            )
        }
        synchronized(lock) {
            val lastWall = lastWallMs
            val lastMono = lastMonoNs
            if (lastWall != null && lastMono != null) {
                val deltaMonoMs = (System.nanoTime() - lastMono) / 1_000_000
                val expected = lastWall + deltaMonoMs
                val skew = abs(now - expected)
                if (skew > MAX_MONOTONIC_SKEW_MS) {
                    logger.error("Обнаружен скачок системного времени относительно монотонных часов: skew={} ms", skew)
                    return TimeValidationResult(
                        ok = false,
                        reason = "MONOTONIC_SKEW",
                        messageRu = "Обнаружен сбой монотонного времени (перевод стрелок)",
                        messageKk = "Монотонды уақыттың ауытқуы анықталды (сағат тілі ауыстырылды)",
                        messageEn = "Monotonic clock skew detected (system clock adjusted)"
                    )
                }
            }
            lastWallMs = now
            lastMonoNs = System.nanoTime()
        }
        val reference = ensureReference(now)
        if (reference != null) {
            val skew = abs(now - reference)
            if (skew > MAX_REFERENCE_SKEW_MS) {
                return TimeValidationResult(
                    ok = false,
                    reason = "REFERENCE_SKEW",
                    messageRu = "Время рассинхронизировано с эталонным",
                    messageKk = "Уақыт эталонды уақытпен синхрондалмаған",
                    messageEn = "Time is desynchronized from reference source"
                )
            }
        }
        return TimeValidationResult(true, null)
    }

    @Suppress("unused")
    fun logStatus(clock: ClockPort) {
        val result = validate(clock)
        if (result.ok) {
            logger.info("Системное время проверено: ok")
        } else {
            logger.error("Системное время некорректно: причина={}", result.reason)
        }
    }

    private fun ensureReference(now: Long): Long? {
        synchronized(lock) {
            val cached = referenceMs
            val fetchedAt = referenceFetchedAtMs

            // Если есть свежий успешный замер, отдаем его
            if (cached != null && fetchedAt != null && now - fetchedAt <= REFERENCE_TTL_MS) {
                return cached
            }

            // Если последняя попытка была совсем недавно, не опрашиваем сеть повторно,
            // чтобы не спамить запросами и не блокировать потоки.
            val lastAttempt = lastFetchAttemptMs
            if (lastAttempt != null && now - lastAttempt <= RETRY_COOL_DOWN_MS) {
                return cached
            }

            // Обновляем время попытки
            lastFetchAttemptMs = now
        }

        // Выполняем сетевой запрос вне синхронизированного блока, чтобы не держать монитор
        val fetched = fetchReferenceTime()

        synchronized(lock) {
            if (fetched != null) {
                referenceMs = fetched
                referenceFetchedAtMs = now
            }
            return referenceMs
        }
    }

    private fun fetchReferenceTime(): Long? {
        for (url in referenceUrls) {
            val time = fetchSingleReferenceTime(url)
            if (time != null) return time
        }
        return null
    }

    @Suppress("TooGenericExceptionCaught", "ReplaceSizeCheckWithIsNotEmpty")
    private fun fetchSingleReferenceTime(url: String): Long? {
        try {
            val connection = java.net.URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            connection.connect()
            val dateHeader = connection.getHeaderField("Date")
            connection.disconnect()
            if (dateHeader != null && dateHeader.length > 0) {
                val format = java.text.SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz",
                    java.util.Locale.US
                ).apply {
                    timeZone = java.util.TimeZone.getTimeZone("GMT")
                }
                return format.parse(dateHeader).time
            }
        } catch (e: Exception) {
            logger.debug("Не удалось получить эталонное время с {}", url, e)
        }
        return null
    }
}
