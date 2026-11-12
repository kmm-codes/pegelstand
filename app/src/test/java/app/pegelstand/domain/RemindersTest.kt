package app.pegelstand.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 6, 10)

    private fun at(time: String, date: LocalDate = day): Instant =
        LocalDateTime.of(date, LocalTime.parse(time)).atZone(berlin).toInstant()

    private fun state(
        enabled: Boolean = true,
        goalMl: Int = 2000,
        totalMl: Int = 0,
        from: LocalTime = LocalTime.of(8, 0),
        until: LocalTime = LocalTime.of(22, 0),
        lastIntakeAt: Instant? = null,
        lastReminderAt: Instant? = null,
        sentDay: LocalDate? = null,
        sentCount: Int = 0,
    ) = ReminderState(enabled, goalMl, totalMl, from, until, berlin, lastIntakeAt, lastReminderAt, sentDay, sentCount)

    /**
     * At 14:00 with 250 ml (expected 857, deficit 607 >= 500), last intake 2 h ago, last reminder
     * 3 h ago, one reminder sent today: every condition holds.
     */
    private val dueAt = at("14:00")
    private val due = state(
        totalMl = 250,
        lastIntakeAt = at("12:00"),
        lastReminderAt = at("11:00"),
        sentDay = day,
        sentCount = 1,
    )

    // --- isReminderDue: all conditions hold, then each fails on its own -------------------------

    @Test
    fun `due when every condition holds`() {
        assertTrue(isReminderDue(due, dueAt))
    }

    @Test
    fun `not due when reminders are disabled`() {
        assertFalse(isReminderDue(due.copy(enabled = false), dueAt))
    }

    @Test
    fun `not due after the window although the deficit is large`() {
        val late = due.copy(remindUntil = LocalTime.of(13, 59))
        assertFalse(isReminderDue(late, dueAt))
        assertTrue(isReminderDue(due.copy(remindUntil = LocalTime.of(14, 0)), dueAt)) // end inclusive
    }

    @Test
    fun `not due before the window`() {
        val notStarted = due.copy(remindFrom = LocalTime.of(14, 1), remindUntil = LocalTime.of(22, 0))
        assertFalse(isReminderDue(notStarted, dueAt))
    }

    @Test
    fun `not due when the goal is reached`() {
        assertFalse(isReminderDue(due.copy(totalMl = 2000), at("22:00")))
        assertFalse(isReminderDue(due.copy(totalMl = 2600), at("22:00")))
    }

    @Test
    fun `not due when the deficit is below a quarter of the goal`() {
        // 14:00 expected 857: 358 ml leaves a deficit of 499.
        assertFalse(isReminderDue(due.copy(totalMl = 358), dueAt))
        assertTrue(isReminderDue(due.copy(totalMl = 357), dueAt))
    }

    @Test
    fun `deficit threshold boundary is exact`() {
        // total 0: expected reaches 500 at 08:00 + 14 h / 4 = 11:30.
        val empty = state()
        assertFalse(isReminderDue(empty, at("11:29:59.999")))
        assertTrue(isReminderDue(empty, at("11:30")))
    }

    @Test
    fun `not due within 120 min of the last reminder`() {
        assertFalse(isReminderDue(due.copy(lastReminderAt = dueAt.minus(Duration.ofMinutes(119))), dueAt))
        assertTrue(isReminderDue(due.copy(lastReminderAt = dueAt.minus(Duration.ofMinutes(120))), dueAt))
    }

    @Test
    fun `not due within 60 min of the last intake`() {
        assertFalse(isReminderDue(due.copy(lastIntakeAt = dueAt.minus(Duration.ofMinutes(59))), dueAt))
        assertTrue(isReminderDue(due.copy(lastIntakeAt = dueAt.minus(Duration.ofMinutes(60))), dueAt))
    }

    @Test
    fun `not due after four reminders on this logical day`() {
        assertFalse(isReminderDue(due.copy(remindersSentCount = 4), dueAt))
        assertTrue(isReminderDue(due.copy(remindersSentCount = 3), dueAt))
    }

    @Test
    fun `reminder count of an earlier day does not count today`() {
        assertTrue(isReminderDue(due.copy(remindersSentDay = day.minusDays(1), remindersSentCount = 4), dueAt))
    }

    // --- nextCheckTime: concrete cases -----------------------------------------------------------

    @Test
    fun `next check solves the deficit inequality`() {
        assertEquals(at("11:30"), nextCheckTime(state(), at("09:00")))
        // 250 ml: expected must reach 750 -> 14 h * 3/8 = 5 h 15 min after 08:00.
        assertEquals(at("13:15"), nextCheckTime(state(totalMl = 250), at("09:00")))
    }

    @Test
    fun `next check before the window and before the day boundary`() {
        assertEquals(at("11:30"), nextCheckTime(state(), at("05:00")))
        assertEquals(at("11:30"), nextCheckTime(state(), at("04:00")))
    }

    @Test
    fun `next check honours the two minimum gaps`() {
        assertEquals(at("14:00"), nextCheckTime(state(totalMl = 250, lastIntakeAt = at("13:00")), at("13:00")))
        assertEquals(at("14:00"), nextCheckTime(state(totalMl = 250, lastReminderAt = at("12:00")), at("13:00")))
    }

    @Test
    fun `next check is now when a reminder is due now`() {
        assertEquals(dueAt, nextCheckTime(due, dueAt))
    }

    @Test
    fun `next check exactly at the window end when the deficit only gets there at the end`() {
        // 1500 ml: deficit at 22:00 is exactly 500.
        assertEquals(at("22:00"), nextCheckTime(state(totalMl = 1500), at("09:00")))
    }

    @Test
    fun `no next check when no reminder is possible any more today`() {
        assertNull(nextCheckTime(state(enabled = false), at("09:00")))
        assertNull(nextCheckTime(state(totalMl = 2000), at("09:00")))
        assertNull(nextCheckTime(state(totalMl = 1550), at("09:00"))) // deficit never reaches 500
        assertNull(nextCheckTime(state(), at("22:00:00.001")))
        assertNull(nextCheckTime(state(sentDay = day, sentCount = 4), at("09:00")))
        assertNull(nextCheckTime(state(lastIntakeAt = at("21:30")), at("21:30"))) // gap ends 22:30
        assertNull(nextCheckTime(state(lastReminderAt = at("20:30")), at("21:00"))) // gap ends 22:30
        // 02:00 belongs to the previous logical day, whose window is over.
        assertNull(nextCheckTime(state(), at("02:00", day.plusDays(1))))
    }

    @Test
    fun `next wake-up is the next check or the next 04_00, whichever is first`() {
        assertEquals(at("11:30"), nextWakeUp(state(), at("09:00")))
        assertEquals(at("04:00", day.plusDays(1)), nextWakeUp(state(totalMl = 2000), at("09:00")))
        assertEquals(at("04:00", day.plusDays(1)), nextWakeUp(state(), at("02:00", day.plusDays(1))))
    }

    // --- nextCheckTime: property over many states ------------------------------------------------

    @Test
    fun `next check is the earliest instant at which a reminder is due, or null if there is none`() {
        val days = listOf(day, LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))
        val times = listOf("00:30", "03:59", "04:00", "07:00", "08:00", "10:17", "13:00", "17:45", "21:59", "22:00", "23:30")
        val totals = listOf(0, 250, 500, 1000, 1500, 1750, 2000)
        val windows = listOf(LocalTime.of(8, 0) to LocalTime.of(22, 0), LocalTime.of(4, 0) to LocalTime.of(23, 59))
        var checkedSome = 0
        var checkedNull = 0
        for (d in days) for (time in times) for (total in totals) for ((from, until) in windows) {
            val now = at(time, d)
            val gapsBefore = listOf(null, Duration.ofMinutes(30), Duration.ofMinutes(100), Duration.ofHours(3))
            for (intakeAgo in gapsBefore) for (reminderAgo in gapsBefore) for (count in listOf(0, 3, 4)) {
                val s = state(
                    totalMl = total,
                    from = from,
                    until = until,
                    lastIntakeAt = intakeAgo?.let { now.minus(it) },
                    lastReminderAt = reminderAgo?.let { now.minus(it) },
                    sentDay = logicalDay(now, berlin),
                    sentCount = count,
                )
                val next = nextCheckTime(s, now)
                val label = "day=$d time=$time total=$total window=$from-$until intakeAgo=$intakeAgo reminderAgo=$reminderAgo count=$count"
                if (next != null) {
                    checkedSome++
                    assertFalse("next before now: $label", next < now)
                    assertTrue("not due at next=$next: $label", isReminderDue(s, next))
                    if (next > now) {
                        assertFalse("already due 1 ms earlier: $label", isReminderDue(s, next.minusMillis(1)))
                        assertNoneDue(s, now, next, label)
                    }
                } else {
                    checkedNull++
                    assertNoneDue(s, now, LogicalDay.nextStart(now, berlin), label)
                }
            }
        }
        // The grid must exercise both branches substantially.
        assertTrue(checkedSome > 500)
        assertTrue(checkedNull > 500)
    }

    /** No minute in [from, to) has a due reminder. */
    private fun assertNoneDue(s: ReminderState, from: Instant, to: Instant, label: String) {
        var t = from
        while (t < to) {
            assertFalse("due at $t although not expected: $label", isReminderDue(s, t))
            t = t.plus(Duration.ofMinutes(1))
        }
    }

    // --- deficit and glasses for the notification text -------------------------------------------

    @Test
    fun `deficit in ml and in glasses`() {
        assertEquals(607, deficitMl(due, dueAt)) // 2000 * 6/14 = 857 expected
        assertEquals(-250, deficitMl(state(totalMl = 250), at("08:00")))
        assertEquals(2, glassesBehind(607, 250))
        assertEquals(3, glassesBehind(625, 250)) // 2.5 rounds up
        assertEquals(1, glassesBehind(100, 250)) // at least one
        assertEquals(1, glassesBehind(0, 250))
    }
}
