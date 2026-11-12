package app.pegelstand.ui.history

import app.pegelstand.domain.DayTotal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {
    // Thursday.
    private val today = LocalDate.of(2026, 11, 12)

    private fun day(date: LocalDate, total: Int, goal: Int = 2000) = DayTotal(date, total, goal)

    // --- Week ---

    @Test
    fun `week runs Monday to Sunday around today`() {
        val week = History.week(today, emptyMap(), 2000)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 11, 9), week.first().date)
        assertEquals(DayOfWeek.MONDAY, week.first().date.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 15), week.last().date)
        assertEquals(listOf(DayKind.PAST, DayKind.PAST, DayKind.PAST, DayKind.TODAY, DayKind.FUTURE, DayKind.FUTURE, DayKind.FUTURE), week.map { it.kind })
    }

    @Test
    fun `week starting on Monday and ending on Sunday`() {
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(monday, History.week(monday, emptyMap(), 2000).first().date)
        assertTrue(History.week(monday, emptyMap(), 2000).first().isToday)
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(monday, History.week(sunday, emptyMap(), 2000).first().date)
        assertTrue(History.week(sunday, emptyMap(), 2000).last().isToday)
    }

    @Test
    fun `week cells carry totals, stored goals and reached flags`() {
        val byDay = History.byDay(
            listOf(
                day(LocalDate.of(2026, 11, 9), 2250),
                day(LocalDate.of(2026, 11, 10), 1500),
                day(LocalDate.of(2026, 11, 11), 1500, goal = 1500),
                day(today, 500),
            ),
        )
        val week = History.week(today, byDay, fallbackGoalMl = 2500)
        assertEquals(listOf(2250, 1500, 1500, 500, 0, 0, 0), week.map { it.totalMl })
        assertEquals(listOf(true, false, true, false, false, false, false), week.map { it.reached })
        assertEquals(1500, week[2].goalMl)
        // A day without an entry is judged against the fallback goal.
        assertEquals(2500, week[5].goalMl)
    }

    @Test
    fun `future days stay empty even if data exists after a clock change`() {
        val byDay = History.byDay(listOf(day(today.plusDays(1), 3000)))
        val cell = History.week(today, byDay, 2000)[4]
        assertTrue(cell.isFuture)
        assertEquals(0, cell.totalMl)
        assertFalse(cell.reached)
    }

    @Test
    fun `week scale leaves room above the goal and the highest bar`() {
        val week = History.week(today, emptyMap(), 2000)
        assertEquals(2500, History.weekScaleMaxMl(week, 2000))
        val high = History.week(today, History.byDay(listOf(day(today, 4000))), 2000)
        assertEquals(4200, History.weekScaleMaxMl(high, 2000))
    }

    // --- Month ---

    @Test
    fun `november 2026 starts on a Sunday and needs six rows`() {
        val grid = History.monthGrid(YearMonth.of(2026, 11), today, emptyMap(), 2000)
        assertEquals(6, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertNull(grid[0][5])
        assertEquals(LocalDate.of(2026, 11, 1), grid[0][6]!!.date)
        assertEquals(LocalDate.of(2026, 11, 2), grid[1][0]!!.date)
        assertEquals(LocalDate.of(2026, 11, 30), grid[5][0]!!.date)
        assertNull(grid[5][1])
        assertEquals(30, grid.flatten().count { it != null })
    }

    @Test
    fun `february 2027 starting on Monday fits four rows`() {
        val grid = History.monthGrid(YearMonth.of(2027, 2), today, emptyMap(), 2000)
        assertEquals(4, grid.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid[0][0]!!.date)
        assertEquals(LocalDate.of(2027, 2, 28), grid[3][6]!!.date)
    }

    @Test
    fun `month cells know today, future and fill fraction`() {
        val byDay = History.byDay(listOf(day(LocalDate.of(2026, 11, 4), 1750), day(LocalDate.of(2026, 11, 5), 2500)))
        val cells = History.monthGrid(YearMonth.of(2026, 11), today, byDay, 2000).flatten().filterNotNull().associateBy { it.date.dayOfMonth }
        assertEquals(0.875f, cells.getValue(4).fraction, 0.0001f)
        assertFalse(cells.getValue(4).reached)
        assertEquals(1f, cells.getValue(5).fraction, 0.0001f)
        assertTrue(cells.getValue(5).reached)
        assertTrue(cells.getValue(12).isToday)
        assertTrue(cells.getValue(13).isFuture)
        assertEquals(DayKind.PAST, cells.getValue(6).kind)
        assertEquals(0f, cells.getValue(6).fraction, 0f)
    }

    @Test
    fun `month range runs from the first recorded month to the current month`() {
        val range = History.monthRange(LocalDate.of(2026, 9, 30), today)
        assertEquals(YearMonth.of(2026, 9), range.first)
        assertEquals(YearMonth.of(2026, 11), range.last)
        assertEquals(3, range.size)
        assertEquals(YearMonth.of(2026, 10), range.monthAt(1))
        assertEquals(2, range.indexOf(YearMonth.of(2026, 11)))
        assertEquals(0, range.indexOf(YearMonth.of(2025, 1)))
    }

    @Test
    fun `month range across a year boundary`() {
        val range = History.monthRange(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 3))
        assertEquals(2, range.size)
        assertEquals(YearMonth.of(2026, 1), range.monthAt(1))
    }

    @Test
    fun `month range without history or with a future first day is the current month`() {
        assertEquals(1, History.monthRange(null, today).size)
        assertEquals(YearMonth.of(2026, 11), History.monthRange(null, today).first)
        assertEquals(YearMonth.of(2026, 11), History.monthRange(today.plusMonths(2), today).first)
    }

    // --- Average of the last 7 days ---

    @Test
    fun `average is null without a completed day`() {
        assertNull(History.averageMl(emptyList(), today))
        assertNull(History.averageMl(listOf(day(today, 1500)), today))
    }

    @Test
    fun `average over the seven days before today, empty days count as zero`() {
        val days = listOf(
            day(today.minusDays(30), 3000), // long before: makes the window a full week
            day(today.minusDays(7), 2000),
            day(today.minusDays(6), 2000),
            day(today.minusDays(3), 1500),
            day(today.minusDays(1), 2500),
            day(today, 9999), // today does not count
        )
        // (2000 + 2000 + 0 + 0 + 1500 + 0 + 2500) / 7 = 1142.86
        assertEquals(1143, History.averageMl(days, today))
    }

    @Test
    fun `average over the days since the first record when it is younger than a week`() {
        val days = listOf(day(today.minusDays(3), 2000), day(today.minusDays(1), 1000))
        // Window: 3 days before today .. yesterday = 3 days; (2000 + 0 + 1000) / 3.
        assertEquals(1000, History.averageMl(days, today))
        assertEquals(2000, History.averageMl(listOf(day(today.minusDays(1), 2000)), today))
    }

    @Test
    fun `days older than the window do not count`() {
        val days = listOf(day(today.minusDays(8), 5000), day(today.minusDays(2), 1400))
        assertEquals(200, History.averageMl(days, today))
    }

    @Test
    fun `first recorded day ignores zero entries`() {
        assertEquals(LocalDate.of(2026, 11, 2), History.firstRecordedDay(listOf(day(LocalDate.of(2026, 11, 1), 0), day(LocalDate.of(2026, 11, 2), 250))))
        assertNull(History.firstRecordedDay(emptyList()))
    }
}
