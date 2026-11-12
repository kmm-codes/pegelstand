package app.pegelstand.ui.history

import app.pegelstand.domain.DayTotal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Where a day stands relative to the current logical day. */
enum class DayKind { PAST, TODAY, FUTURE }

/** One day of the week chart or the month calendar. Future days always carry a total of 0. */
data class DayCell(val date: LocalDate, val totalMl: Int, val goalMl: Int, val kind: DayKind) {
    val isFuture: Boolean get() = kind == DayKind.FUTURE
    val isToday: Boolean get() = kind == DayKind.TODAY
    val reached: Boolean get() = !isFuture && goalMl > 0 && totalMl >= goalMl

    /** total / goal, clamped to 0..1 (for the month drops). */
    val fraction: Float get() = if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
}

/** First and last month the calendar may show. */
data class MonthRange(val first: YearMonth, val last: YearMonth) {
    /** Number of months in the range, at least 1. */
    val size: Int get() = (monthsBetween(first, last) + 1).coerceAtLeast(1)

    fun monthAt(index: Int): YearMonth = first.plusMonths(index.toLong())

    fun indexOf(month: YearMonth): Int = monthsBetween(first, month).coerceIn(0, size - 1)

    private companion object {
        fun monthsBetween(a: YearMonth, b: YearMonth): Int = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)
    }
}

/**
 * Pure models behind the history sections (Streak, Woche, Monat). [days] is what
 * [app.pegelstand.data.WaterRepository.dayTotals] returns: only days with intake, each with the
 * goal stored for it. Days without an entry count as 0 and are judged against [fallbackGoalMl].
 */
object History {
    val WEEK_START: DayOfWeek = DayOfWeek.MONDAY

    /** Length of the window of the average shown under "Streak". */
    const val AVERAGE_DAYS = 7L

    fun byDay(days: Collection<DayTotal>): Map<LocalDate, DayTotal> = days.associateBy { it.day }

    /** The first day with a recorded intake, or null if there is none. */
    fun firstRecordedDay(days: Collection<DayTotal>): LocalDate? =
        days.asSequence().filter { it.totalMl > 0 }.minOfOrNull { it.day }

    fun cell(date: LocalDate, today: LocalDate, byDay: Map<LocalDate, DayTotal>, fallbackGoalMl: Int): DayCell {
        val kind = when {
            date < today -> DayKind.PAST
            date == today -> DayKind.TODAY
            else -> DayKind.FUTURE
        }
        val entry = byDay[date]
        val total = if (kind == DayKind.FUTURE) 0 else entry?.totalMl ?: 0
        return DayCell(date, total, entry?.goalMl ?: fallbackGoalMl, kind)
    }

    /** The current week, Monday to Sunday. */
    fun week(today: LocalDate, byDay: Map<LocalDate, DayTotal>, fallbackGoalMl: Int): List<DayCell> {
        val monday = today.with(TemporalAdjusters.previousOrSame(WEEK_START))
        return (0L until 7L).map { cell(monday.plusDays(it), today, byDay, fallbackGoalMl) }
    }

    /**
     * Top of the week chart's scale in ml: room above the goal line, and above the highest bar,
     * so neither touches the top edge.
     */
    fun weekScaleMaxMl(week: List<DayCell>, goalMl: Int): Int {
        val highest = week.maxOfOrNull { it.totalMl } ?: 0
        return maxOf(goalMl * 5 / 4, highest * 21 / 20, 1)
    }

    /**
     * Calendar grid of [month]: rows of seven cells, Monday first; null where a row reaches into
     * the previous or next month. As many rows as the month needs (4 to 6).
     */
    fun monthGrid(month: YearMonth, today: LocalDate, byDay: Map<LocalDate, DayTotal>, fallbackGoalMl: Int): List<List<DayCell?>> {
        val first = month.atDay(1)
        val leading = Math.floorMod(first.dayOfWeek.value - WEEK_START.value, 7)
        val length = month.lengthOfMonth()
        val rows = (leading + length + 6) / 7
        return List(rows) { row ->
            List(7) { column ->
                val dayOfMonth = row * 7 + column - leading + 1
                if (dayOfMonth in 1..length) cell(month.atDay(dayOfMonth), today, byDay, fallbackGoalMl) else null
            }
        }
    }

    /**
     * Months the calendar may show: from the month of the first recorded day up to the current
     * month. Without history (or with a first day after today) only the current month.
     */
    fun monthRange(firstRecordedDay: LocalDate?, today: LocalDate): MonthRange {
        val last = YearMonth.from(today)
        val first = firstRecordedDay?.let(YearMonth::from)?.takeIf { it <= last } ?: last
        return MonthRange(first, last)
    }

    /**
     * Mean daily total in ml over the [AVERAGE_DAYS] logical days before [today] (days without
     * intake count as 0). If the first recorded day is younger than that window, the mean runs
     * over the days from it to yesterday. Null if there is no completed day yet (no history, or
     * the first recorded day is today). Rounded to whole ml.
     */
    fun averageMl(days: Collection<DayTotal>, today: LocalDate): Int? {
        val first = firstRecordedDay(days) ?: return null
        val yesterday = today.minusDays(1)
        val from = maxOf(today.minusDays(AVERAGE_DAYS), first)
        if (from > yesterday) return null
        val count = yesterday.toEpochDay() - from.toEpochDay() + 1
        val sum = days.asSequence().filter { it.day in from..yesterday }.sumOf { it.totalMl.toLong() }
        return Math.round(sum.toDouble() / count).toInt()
    }
}
