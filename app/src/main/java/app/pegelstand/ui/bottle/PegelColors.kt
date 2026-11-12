package app.pegelstand.ui.bottle

/**
 * The bottle palette as plain ARGB ints, shared by the widget (android.graphics) and the app
 * (Compose: `Color(argb)`). Water colours come from [app.pegelstand.domain.WaterColor].
 */
object PegelColors {
    /** Accent for "goal reached": cap, outline and sparkles. Never used for water. */
    const val GOLD: Int = 0xFFFFB300.toInt()

    /** A darker gold for edges on the gold cap and for text on light backgrounds. */
    const val GOLD_DEEP: Int = 0xFFC77C00.toInt()

    class Palette(
        /** Empty part of the bottle. */
        val glassTint: Int,
        /** Bottle outline and cap. */
        val outline: Int,
        /** Glass reflection drawn over water and tint. */
        val highlight: Int,
        /** Widget surface behind the bottle. */
        val surface: Int,
        /** Amount text and undo icon on [surface]. */
        val onSurface: Int,
        /** Amount text on [surface] once the goal is reached. */
        val goldText: Int,
        /** Background of the streak chip. */
        val chip: Int,
        /** Text on [chip]. */
        val onChip: Int,
    )

    val Light = Palette(
        glassTint = 0xFFE8F4FB.toInt(),
        outline = 0xFF0D47A1.toInt(),
        highlight = 0x8CFFFFFF.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        onSurface = 0xFF0D47A1.toInt(),
        goldText = 0xFFB86E00.toInt(),
        chip = 0xFFFFF1D6.toInt(),
        onChip = 0xFF7A4A00.toInt(),
    )

    val Dark = Palette(
        glassTint = 0xFF1C2A35.toInt(),
        outline = 0xFFB3E5FC.toInt(),
        highlight = 0x40FFFFFF,
        surface = 0xFF10181F.toInt(),
        onSurface = 0xFFE1F5FE.toInt(),
        goldText = 0xFFFFC94D.toInt(),
        chip = 0xFF3A2C12.toInt(),
        onChip = 0xFFFFD98A.toInt(),
    )

    fun of(dark: Boolean): Palette = if (dark) Dark else Light
}
