package app.pegelstand.domain

import java.time.Duration
import java.time.Instant

/**
 * Converts the wall-clock instant of the next wake-up into a trigger time on the elapsed-realtime
 * clock. The delay is measured against the app's own "now" (which a debug build can fake), and
 * the alarm is armed on the monotonic clock, so a later wall-clock jump does not move it; time
 * and time-zone changes reschedule explicitly.
 */
object AlarmTiming {
    /** Milliseconds from [now] until [wakeUp]; 0 if [wakeUp] is not in the future. */
    fun delayMs(wakeUp: Instant, now: Instant): Long =
        Duration.between(now, wakeUp).toMillis().coerceAtLeast(0L)

    /** Trigger time on the elapsed-realtime clock, given that clock's current value. */
    fun elapsedTriggerAt(elapsedNowMs: Long, wakeUp: Instant, now: Instant): Long =
        elapsedNowMs + delayMs(wakeUp, now)
}
