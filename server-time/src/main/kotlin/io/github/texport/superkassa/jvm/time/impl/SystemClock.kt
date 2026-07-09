package io.github.texport.superkassa.jvm.time.impl

import kz.mybrain.superkassa.core.domain.port.ClockPort

/**
 * Реализация интерфейса [ClockPort] для системного времени (J2SE-реализация).
 *
 * Потокобезопасность: Синглтон-объект, неизменяем и полностью потокобезопасен.
 */
object SystemClock : ClockPort {
    override fun now(): Long = System.currentTimeMillis()

    override fun currentYear(): Int =
        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year

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
