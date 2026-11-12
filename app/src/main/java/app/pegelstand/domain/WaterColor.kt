package app.pegelstand.domain

/**
 * Water colour presets. [light] and [deep] are the two ends of the water gradient as plain ARGB
 * ints. Gold is deliberately not a preset: it is reserved for "goal reached".
 * German labels live in `strings.xml`, not here.
 */
enum class WaterColor(val id: String, val light: Int, val deep: Int) {
    BLUE("BLUE", 0xFF5CC8F5.toInt(), 0xFF1E88E5.toInt()),
    TURQUOISE("TURQUOISE", 0xFF5FE0D0.toInt(), 0xFF00A99D.toInt()),
    MINT("MINT", 0xFF8EE6A8.toInt(), 0xFF2EAD6B.toInt()),
    LAVENDER("LAVENDER", 0xFFC3B1F5.toInt(), 0xFF7E57C2.toInt()),
    PINK("PINK", 0xFFF8A8CF.toInt(), 0xFFE0559A.toInt()),
    CORAL("CORAL", 0xFFFFB199.toInt(), 0xFFF4664A.toInt());

    companion object {
        val DEFAULT: WaterColor = BLUE

        /** Maps a stored id back to a preset; a missing or unknown id falls back to [DEFAULT]. */
        fun fromId(id: String?): WaterColor = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
