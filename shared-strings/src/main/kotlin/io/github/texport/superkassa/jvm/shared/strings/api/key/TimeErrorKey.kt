package io.github.texport.superkassa.jvm.shared.strings.api.key

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey

/**
 * Ключи ошибок для валидации системного времени.
 *
 * Потокобезопасность: Перечисления (enums) являются потокобезопасными по своей природе.
 */
enum class TimeErrorKey(override val code: String) : ErrorKey {
    /** Системное время вне допустимого диапазона (2020-2100) */
    TIME_OUT_OF_RANGE("TIME_OUT_OF_RANGE"),

    /** Обнаружен сбой монотонного времени (перевод стрелок) */
    TIME_MONOTONIC_SKEW("TIME_MONOTONIC_SKEW"),

    /** Время рассинхронизировано с эталонным источником */
    TIME_REFERENCE_SKEW("TIME_REFERENCE_SKEW")
}
