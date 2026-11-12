package app.pegelstand.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterColorTest {
    @Test
    fun `every preset round-trips through its id`() {
        WaterColor.entries.forEach { assertEquals(it, WaterColor.fromId(it.id)) }
    }

    @Test
    fun `missing or unknown id falls back to blue`() {
        assertEquals(WaterColor.BLUE, WaterColor.fromId(null))
        assertEquals(WaterColor.BLUE, WaterColor.fromId(""))
        assertEquals(WaterColor.BLUE, WaterColor.fromId("GOLD"))
        assertEquals(WaterColor.BLUE, WaterColor.fromId("turquoise")) // ids are case-sensitive
    }

    @Test
    fun `six presets with the specified ARGB values`() {
        assertEquals(6, WaterColor.entries.size)
        assertEquals(0xFF5CC8F5.toInt(), WaterColor.BLUE.light)
        assertEquals(0xFF1E88E5.toInt(), WaterColor.BLUE.deep)
        assertEquals(0xFF00A99D.toInt(), WaterColor.TURQUOISE.deep)
        assertEquals(0xFFF4664A.toInt(), WaterColor.CORAL.deep)
        assertEquals(WaterColor.BLUE, WaterColor.DEFAULT)
    }
}
