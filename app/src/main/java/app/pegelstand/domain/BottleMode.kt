package app.pegelstand.domain

/**
 * Direction of the bottle. [FILL]: empty at the start of the day, fills with what was drunk.
 * [DRAIN]: full at the start of the day (it holds the daily goal) and empties as she drinks.
 * German labels live in `strings.xml`, not here.
 */
enum class BottleMode(val id: String) {
    FILL("FILL"),
    DRAIN("DRAIN");

    companion object {
        val DEFAULT: BottleMode = FILL

        /** Maps a stored id back to a mode; a missing or unknown id falls back to [DEFAULT]. */
        fun fromId(id: String?): BottleMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * The bottle level shown for [totalMl] drunk of [goalMl], clamped to 0..1. The only place that
 * knows the direction: widget and app both draw this value. A goal of 0 or less counts as reached
 * (as `total >= goal` does), so it never divides by zero.
 */
fun shownFill(totalMl: Int, goalMl: Int, mode: BottleMode): Float {
    val drunk = if (goalMl <= 0) 1f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)
    return when (mode) {
        BottleMode.FILL -> drunk
        BottleMode.DRAIN -> 1f - drunk
    }
}
