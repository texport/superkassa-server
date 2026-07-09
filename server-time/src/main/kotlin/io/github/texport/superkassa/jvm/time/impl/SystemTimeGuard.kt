package io.github.texport.superkassa.jvm.time.impl

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeDebugKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import kz.mybrain.superkassa.core.domain.exception.TrilingualMessage
import kz.mybrain.superkassa.core.domain.model.common.TimeValidationResult
import kz.mybrain.superkassa.core.domain.port.ClockPort
import kz.mybrain.superkassa.core.domain.port.TimeValidatorPort
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Защитник времени системы (TimeValidatorPort).
 * Проверяет корректность системных часов относительно допустимого диапазона,
 * отсутствия скачков времени (monotonic skew) и синхронизации с внешними HTTP-серверами (Google, Cloudflare и др.).
 */
object SystemTimeGuard : TimeValidatorPort {
    private val logger = LoggerFactory.getLogger(SystemTimeGuard::class.java)
    private val resolver: ErrorResolver = DefaultErrorResolver()

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
            val msg = resolver.resolve(TimeErrorKey.TIME_OUT_OF_RANGE)
            return TimeValidationResult(
                ok = false,
                reason = "RANGE",
                trilingualMessage = TrilingualMessage(
                    ru = msg.ru,
                    kk = msg.kk,
                    en = msg.en
                )
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
                    logger.error(
                        resolver.resolve(TimeDebugKey.CLOCK_SKEW_DETECTED).formatArgs(skew).en
                    )
                    val msg = resolver.resolve(TimeErrorKey.TIME_MONOTONIC_SKEW)
                    return TimeValidationResult(
                        ok = false,
                        reason = "MONOTONIC_SKEW",
                        trilingualMessage = TrilingualMessage(
                            ru = msg.ru,
                            kk = msg.kk,
                            en = msg.en
                        )
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
                val msg = resolver.resolve(TimeErrorKey.TIME_REFERENCE_SKEW)
                return TimeValidationResult(
                    ok = false,
                    reason = "REFERENCE_SKEW",
                    trilingualMessage = TrilingualMessage(
                        ru = msg.ru,
                        kk = msg.kk,
                        en = msg.en
                    )
                )
            }
        }
        return TimeValidationResult(true, null)
    }

    private fun ensureReference(now: Long): Long? {
        synchronized(lock) {
            val cached = referenceMs
            val fetchedAt = referenceFetchedAtMs

            // Если есть свежий успешный замер, отдаем его
            if (cached != null && fetchedAt != null && now - fetchedAt <= REFERENCE_TTL_MS) {
                return cached + (now - fetchedAt)
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

    private fun fetchSingleReferenceTime(url: String): Long? {
        var connection: HttpURLConnection? = null
        try {
            connection = URI.create(url).toURL().openConnection() as? HttpURLConnection
            if (connection == null) {
                logger.debug(resolver.resolve(TimeDebugKey.REFERENCE_NOT_HTTP).formatArgs(url).en)
                return null
            }
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            connection.connect()
            val dateHeader = connection.getHeaderField("Date")
            if (!dateHeader.isNullOrEmpty()) {
                val format = SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss zzz",
                    Locale.US
                ).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                return format.parse(dateHeader).time
            }
        } catch (e: IllegalArgumentException) {
            logger.debug(resolver.resolve(TimeDebugKey.INVALID_REFERENCE_URL).formatArgs(url).en, e)
        } catch (e: IOException) {
            logger.debug(resolver.resolve(TimeDebugKey.FAILED_TO_FETCH_REFERENCE).formatArgs(url).en, e)
        } catch (e: ParseException) {
            logger.debug(resolver.resolve(TimeDebugKey.FAILED_TO_PARSE_DATE).formatArgs(url).en, e)
        } finally {
            connection?.disconnect()
        }
        return null
    }
}
