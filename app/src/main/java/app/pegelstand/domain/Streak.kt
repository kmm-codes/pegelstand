package app.pegelstand.domain

import java.time.LocalDate

/** Total of one logical day, judged against the goal that was stored for that day. */
data class DayTotal(val day: LocalDate, val totalMl: Int, val goalMl: Int) {
    val goalReached: Boolean get() = goalMl > 0 && totalMl >= goalMl
}

data class Streak(val current: Int, val longest: Int) {
    companion object {
        val NONE = Streak(0, 0)
    }
}

object Streaks {
    /**
     * Current streak: consecutive reached days ending yesterday, plus today if today is already
     * reached. Today not being reached yet does not break it. Longest: the longest run of
     * consecutive reached days in the whole history up to and including today.
     *
     * Days after [today] (possible after a clock change) are ignored. Duplicate days in [days] are
     * not expected; if present, a day counts as reached if any entry for it is reached.
     */
    fun compute(days: Collection<DayTotal>, today: LocalDate): Streak {
        val reached: Set<LocalDate> = days.asSequence()
            .filter { it.goalReached && it.day <= today }
            .map { it.day }
            .toSet()
        if (reached.isEmpty()) return Streak.NONE

        var cursor = if (today in reached) today else today.minusDays(1)
        var current = 0
        while (cursor in reached) {
            current++
            cursor = cursor.minusDays(1)
        }

        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in reached.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }
        return Streak(current = current, longest = maxOf(longest, current))
    }
}
