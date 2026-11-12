package app.pegelstand.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private val today = LocalDate.of(2026, 6, 10)

    /** [daysAgo] to total, with goal 2000. */
    private fun history(vararg entries: Pair<Int, Int>, goal: Int = 2000): List<DayTotal> =
        entries.map { (daysAgo, total) -> DayTotal(today.minusDays(daysAgo.toLong()), total, goal) }

    @Test
    fun `empty history`() {
        assertEquals(Streak(0, 0), Streaks.compute(emptyList(), today))
    }

    @Test
    fun `today not yet reached does not break the streak`() {
        val days = history(3 to 2000, 2 to 2500, 1 to 2000, 0 to 500)
        assertEquals(Streak(current = 3, longest = 3), Streaks.compute(days, today))
    }

    @Test
    fun `today with no intake at all does not break the streak`() {
        val days = history(2 to 2000, 1 to 2000)
        assertEquals(Streak(current = 2, longest = 2), Streaks.compute(days, today))
    }

    @Test
    fun `today reached counts`() {
        val days = history(2 to 2000, 1 to 2000, 0 to 2000)
        assertEquals(Streak(current = 3, longest = 3), Streaks.compute(days, today))
    }

    @Test
    fun `only today reached`() {
        assertEquals(Streak(1, 1), Streaks.compute(history(0 to 2250), today))
    }

    @Test
    fun `a gap ends the current streak and the longest run is kept`() {
        // 6..4 days ago reached (run of 3), 3 days ago missed, 2..1 reached (run of 2), today open.
        val days = history(6 to 2000, 5 to 2000, 4 to 2000, 3 to 1999, 2 to 2000, 1 to 2000, 0 to 0)
        assertEquals(Streak(current = 2, longest = 3), Streaks.compute(days, today))
    }

    @Test
    fun `a day without any intake is a gap`() {
        val days = history(4 to 2000, 3 to 2000, 1 to 2000) // 2 days ago missing entirely
        assertEquals(Streak(current = 1, longest = 2), Streaks.compute(days, today))
    }

    @Test
    fun `yesterday missed means current is zero while today is open`() {
        val days = history(3 to 2000, 2 to 2000, 1 to 100, 0 to 1000)
        assertEquals(Streak(current = 0, longest = 2), Streaks.compute(days, today))
    }

    @Test
    fun `goal changed mid-history - old days are judged by their stored goal`() {
        val days = listOf(
            // Goal was 1500 back then: 1500 reached it.
            DayTotal(today.minusDays(3), 1500, 1500),
            DayTotal(today.minusDays(2), 1750, 1500),
            // Goal raised to 2500: 2000 no longer reaches it.
            DayTotal(today.minusDays(1), 2000, 2500),
            DayTotal(today, 2500, 2500),
        )
        assertEquals(Streak(current = 1, longest = 2), Streaks.compute(days, today))
    }

    @Test
    fun `goal lowered - a smaller total under the old higher goal stays missed`() {
        val days = listOf(
            DayTotal(today.minusDays(2), 1500, 3000),
            DayTotal(today.minusDays(1), 1500, 1500),
        )
        assertEquals(Streak(current = 1, longest = 1), Streaks.compute(days, today))
    }

    @Test
    fun `days after today are ignored`() {
        val days = history(1 to 2000) + DayTotal(today.plusDays(1), 3000, 2000)
        assertEquals(Streak(current = 1, longest = 1), Streaks.compute(days, today))
    }

    @Test
    fun `order of input does not matter`() {
        val days = history(1 to 2000, 3 to 2000, 2 to 2000).reversed()
        assertEquals(Streak(current = 3, longest = 3), Streaks.compute(days, today))
    }
}
