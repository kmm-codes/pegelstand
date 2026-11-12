package app.pegelstand.domain

import java.time.Duration
import java.time.LocalTime

/** User settings with their defaults and allowed ranges, in one place. */
data class Settings(
    val dailyGoalMl: Int = SettingsRules.DEFAULT_GOAL_ML,
    val mlPerTap: Int = SettingsRules.DEFAULT_ML_PER_TAP,
    val waterColor: WaterColor = WaterColor.DEFAULT,
    val bottleMode: BottleMode = BottleMode.DEFAULT,
    /** The widget's idle wave (the water moving while nothing changes); level changes animate regardless. */
    val widgetIdleWave: Boolean = true,
    val remindersEnabled: Boolean = true,
    val remindFrom: LocalTime = SettingsRules.DEFAULT_REMIND_FROM,
    val remindUntil: LocalTime = SettingsRules.DEFAULT_REMIND_UNTIL,
)

object SettingsRules {
    const val DEFAULT_GOAL_ML = 2000
    const val MIN_GOAL_ML = 1000
    const val MAX_GOAL_ML = 5000
    const val GOAL_STEP_ML = 250

    const val DEFAULT_ML_PER_TAP = 250
    const val MIN_ML_PER_TAP = 50
    const val MAX_ML_PER_TAP = 1000
    const val ML_PER_TAP_STEP = 50

    val DEFAULT_REMIND_FROM: LocalTime = LocalTime.of(8, 0)
    val DEFAULT_REMIND_UNTIL: LocalTime = LocalTime.of(22, 0)
    val EARLIEST_REMIND_FROM: LocalTime = LogicalDay.START
    val LATEST_REMIND_UNTIL: LocalTime = LocalTime.of(23, 59)
    val MIN_WINDOW: Duration = Duration.ofHours(2)

    /** Clamps into range and snaps to the nearest step. */
    fun coerceGoal(ml: Int): Int = snap(ml, MIN_GOAL_ML, MAX_GOAL_ML, GOAL_STEP_ML)

    /** Clamps into range and snaps to the nearest step. */
    fun coerceMlPerTap(ml: Int): Int = snap(ml, MIN_ML_PER_TAP, MAX_ML_PER_TAP, ML_PER_TAP_STEP)

    /** from >= 04:00, until <= 23:59, until - from >= 2 h. */
    fun isValidWindow(from: LocalTime, until: LocalTime): Boolean =
        from >= EARLIEST_REMIND_FROM &&
            until <= LATEST_REMIND_UNTIL &&
            Duration.between(from, until) >= MIN_WINDOW

    private fun snap(value: Int, min: Int, max: Int, step: Int): Int {
        val clamped = value.coerceIn(min, max)
        val steps = Math.floorDiv(clamped - min + step / 2, step)
        return (min + steps * step).coerceIn(min, max)
    }
}
