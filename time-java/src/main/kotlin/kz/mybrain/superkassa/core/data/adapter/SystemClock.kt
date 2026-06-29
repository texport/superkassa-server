package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.port.ClockPort

/**
 * Системное время (J2SE реализация).
 */
object SystemClock : ClockPort {
    override fun now(): Long = System.currentTimeMillis()
    override fun currentYear(): Int = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year
    override fun parseDateTimeToMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        java.time.LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
