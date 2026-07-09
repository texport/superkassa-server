package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи отладочных сообщений и логов для модуля времени (server-time).
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class TimeDebugKey(override val code: String) : ErrorKey {
    /** Обнаружен скачок системного времени относительно монотонных часов */
    CLOCK_SKEW_DETECTED("TIME_DEBUG_CLOCK_SKEW_DETECTED"),

    /** Источник эталонного времени не является HTTP URL */
    REFERENCE_NOT_HTTP("TIME_DEBUG_REFERENCE_NOT_HTTP"),

    /** Некорректный URL эталонного времени */
    INVALID_REFERENCE_URL("TIME_DEBUG_INVALID_REFERENCE_URL"),

    /** Не удалось получить эталонное время */
    FAILED_TO_FETCH_REFERENCE("TIME_DEBUG_FAILED_TO_FETCH_REFERENCE"),

    /** Не удалось разобрать Date header эталонного времени */
    FAILED_TO_PARSE_DATE("TIME_DEBUG_FAILED_TO_PARSE_DATE")
}
