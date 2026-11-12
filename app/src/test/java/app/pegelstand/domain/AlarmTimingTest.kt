package app.pegelstand.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 11, 12, 15, 0, 0, 0, zone).toInstant()

    @Test
    fun `delay is the distance from now to the wake-up`() {
        assertEquals(90 * 60_000L, AlarmTiming.delayMs(now.plusSeconds(90 * 60), now))
    }

    @Test
    fun `a wake-up now or in the past fires at once`() {
        assertEquals(0L, AlarmTiming.delayMs(now, now))
        assertEquals(0L, AlarmTiming.delayMs(now.minusSeconds(30), now))
    }

    @Test
    fun `trigger time is on the elapsed clock, independent of the wall clock`() {
        val elapsed = 1_234_567L
        assertEquals(elapsed + 60_000L, AlarmTiming.elapsedTriggerAt(elapsed, now.plusSeconds(60), now))
        // The same wake-up seen from a faked "now" ten hours earlier is ten hours further away.
        val fakeNow = now.minusSeconds(10 * 3600)
        assertEquals(elapsed + 10 * 3_600_000L + 60_000L, AlarmTiming.elapsedTriggerAt(elapsed, now.plusSeconds(60), fakeNow))
    }

    @Test
    fun `delay to the next wake-up of a real state`() {
        // 15:00, nothing drunk yet, 2000 ml goal, window 08-22: a reminder is due right now.
        val behind = state(totalMl = 0)
        assertEquals(0L, AlarmTiming.delayMs(nextWakeUp(behind, now), now))
        // On track (1250 ml at 15:00): expected reaches 1750 ml (deficit = goal/4) at 08:00 + 0.875 * 14 h = 20:15.
        val onTrack = state(totalMl = 1250)
        assertEquals(315 * 60_000L, AlarmTiming.delayMs(nextWakeUp(onTrack, now), now))
        // Goal reached: nothing until the next day boundary at 04:00 (13 h).
        val done = state(totalMl = 2000)
        assertEquals(13 * 3_600_000L, AlarmTiming.delayMs(nextWakeUp(done, now), now))
    }

    private fun state(totalMl: Int, lastIntakeAt: Instant? = null) = ReminderState(
        enabled = true,
        goalMl = 2000,
        totalMl = totalMl,
        remindFrom = LocalTime.of(8, 0),
        remindUntil = LocalTime.of(22, 0),
        zone = zone,
        lastIntakeAt = lastIntakeAt,
        lastReminderAt = null,
        remindersSentDay = null,
        remindersSentCount = 0,
    )
}
