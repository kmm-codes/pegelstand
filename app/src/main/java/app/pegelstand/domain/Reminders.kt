package app.pegelstand.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Everything the reminder decision needs, captured at one moment.
 *
 * [totalMl] and [goalMl] belong to the logical day of the `now` the state is evaluated at.
 * [remindersSentDay]/[remindersSentCount] are the raw bookkeeping: the count only applies when
 * [remindersSentDay] is the logical day of `now`, otherwise no reminder was sent today yet.
 */
data class ReminderState(
    val enabled: Boolean,
    val goalMl: Int,
    val totalMl: Int,
    val remindFrom: LocalTime,
    val remindUntil: LocalTime,
    val zone: ZoneId,
    val lastIntakeAt: Instant?,
    val lastReminderAt: Instant?,
    val remindersSentDay: LocalDate?,
    val remindersSentCount: Int,
)

/** All reminder constants live here. */
object ReminderRules {
    val MIN_GAP_AFTER_REMINDER: Duration = Duration.ofMinutes(120)
    val MIN_GAP_AFTER_INTAKE: Duration = Duration.ofMinutes(60)
    const val MAX_PER_DAY = 4

    /** A reminder needs a deficit of at least goal / [DEFICIT_DIVISOR]. */
    const val DEFICIT_DIVISOR = 4
}

/**
 * True only if all hold at [now]: reminders enabled, [now] inside today's window (both ends
 * inclusive), goal not reached, deficit >= goal / 4, >= 120 min since the last reminder,
 * >= 60 min since the last intake, fewer than 4 reminders on this logical day.
 */
fun isReminderDue(state: ReminderState, now: Instant): Boolean {
    if (!state.enabled || state.goalMl <= 0 || state.totalMl >= state.goalMl) return false
    val day = logicalDay(now, state.zone)
    if (sentOn(state, day) >= ReminderRules.MAX_PER_DAY) return false
    val window = ReminderWindow.of(day, state) ?: return false
    if (now < window.start || now > window.end) return false
    val lastReminder = state.lastReminderAt
    if (lastReminder != null && now < lastReminder + ReminderRules.MIN_GAP_AFTER_REMINDER) return false
    val lastIntake = state.lastIntakeAt
    if (lastIntake != null && now < lastIntake + ReminderRules.MIN_GAP_AFTER_INTAKE) return false
    return window.deficitThresholdMet(state, now)
}

/**
 * The earliest instant `t >= now` at which [isReminderDue] becomes true if nothing else changes
 * (no intake, no settings change, no reminder sent). Returns [now] itself if a reminder is due
 * right now, and `null` if no reminder is possible any more on the current logical day.
 *
 * The deficit grows linearly through the window, so the deficit inequality is solved for t
 * (exactly, in integer milliseconds) and the window start and the two minimum gaps are applied as
 * lower bounds.
 */
fun nextCheckTime(state: ReminderState, now: Instant): Instant? {
    if (!state.enabled || state.goalMl <= 0 || state.totalMl >= state.goalMl) return null
    val day = logicalDay(now, state.zone)
    if (sentOn(state, day) >= ReminderRules.MAX_PER_DAY) return null
    val window = ReminderWindow.of(day, state) ?: return null
    val deficitFrom = window.earliestDeficitInstant(state) ?: return null

    var t = maxOf(now, window.start, deficitFrom)
    state.lastReminderAt?.let { t = maxOf(t, it + ReminderRules.MIN_GAP_AFTER_REMINDER) }
    state.lastIntakeAt?.let { t = maxOf(t, it + ReminderRules.MIN_GAP_AFTER_INTAKE) }
    return if (t > window.end) null else t
}

/**
 * When the single pending alarm should fire: the next reminder check, or the next day boundary
 * (04:00) if that comes first or no reminder is possible today.
 */
fun nextWakeUp(state: ReminderState, now: Instant): Instant {
    val dayBoundary = LogicalDay.nextStart(now, state.zone)
    val check = nextCheckTime(state, now) ?: return dayBoundary
    return minOf(check, dayBoundary)
}

/** Expected intake at [now] minus the actual total, in ml (negative when ahead of plan). */
fun deficitMl(state: ReminderState, now: Instant): Int {
    val window = ReminderWindow.of(logicalDay(now, state.zone), state) ?: return 0
    val expected = state.goalMl.toLong() * window.elapsedMs(now) / window.spanMs
    return (expected - state.totalMl).toInt()
}

/** Deficit expressed in glasses of [mlPerTap], rounded, at least 1 (for the notification text). */
fun glassesBehind(deficitMl: Int, mlPerTap: Int): Int {
    require(mlPerTap > 0) { "mlPerTap must be positive" }
    return maxOf(1, Math.round(deficitMl.toDouble() / mlPerTap).toInt())
}

private fun sentOn(state: ReminderState, day: LocalDate): Int =
    if (state.remindersSentDay == day) state.remindersSentCount else 0

private class ReminderWindow(val start: Instant, val end: Instant) {
    val spanMs: Long = end.toEpochMilli() - start.toEpochMilli()

    fun elapsedMs(now: Instant): Long = (now.toEpochMilli() - start.toEpochMilli()).coerceIn(0, spanMs)

    /** expected - total >= goal / 4, multiplied out by 4 * span to stay in exact integers. */
    fun deficitThresholdMet(state: ReminderState, now: Instant): Boolean {
        val goal = state.goalMl.toLong()
        val lhs = ReminderRules.DEFICIT_DIVISOR * (goal * elapsedMs(now) - state.totalMl.toLong() * spanMs)
        return lhs >= goal * spanMs
    }

    /** Smallest t in the window with the deficit threshold met, or null if it never is today. */
    fun earliestDeficitInstant(state: ReminderState): Instant? {
        val goal = state.goalMl.toLong()
        val divisor = ReminderRules.DEFICIT_DIVISOR.toLong()
        // elapsed >= span * (goal + divisor * total) / (divisor * goal), rounded up.
        val numerator = spanMs * (goal + divisor * state.totalMl)
        val denominator = divisor * goal
        val needMs = Math.floorDiv(numerator + denominator - 1, denominator)
        if (needMs > spanMs) return null
        return Instant.ofEpochMilli(start.toEpochMilli() + needMs)
    }

    companion object {
        fun of(day: LocalDate, state: ReminderState): ReminderWindow? {
            val start = day.atTime(state.remindFrom).atZone(state.zone).toInstant()
            val end = day.atTime(state.remindUntil).atZone(state.zone).toInstant()
            return if (end > start) ReminderWindow(start, end) else null
        }
    }
}
