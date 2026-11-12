package app.pegelstand.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The logical day [instant] belongs to. A logical day starts at [LogicalDay.START] local
 * wall-clock time, so a glass at 01:30 still counts for the evening before.
 *
 * The boundary is applied to the local wall-clock time, not by subtracting four elapsed hours from
 * the instant: on a DST change day the two differ (04:00 CEST on the spring-forward day is only
 * three real hours after midnight), and the day must still start exactly at 04:00 local.
 */
fun logicalDay(instant: Instant, zone: ZoneId): LocalDate {
    val local = instant.atZone(zone).toLocalDateTime()
    val date = local.toLocalDate()
    return if (local.toLocalTime() < LogicalDay.START) date.minusDays(1) else date
}

object LogicalDay {
    /** Start of a logical day. A constant by design, not a setting. */
    val START: LocalTime = LocalTime.of(4, 0)

    /** The instant at which [day] begins (04:00 local; shifted forward if that time does not exist). */
    fun startOf(day: LocalDate, zone: ZoneId): Instant = day.atTime(START).atZone(zone).toInstant()

    /** The next day boundary strictly after [now]: the start of the following logical day. */
    fun nextStart(now: Instant, zone: ZoneId): Instant = startOf(logicalDay(now, zone).plusDays(1), zone)
}
