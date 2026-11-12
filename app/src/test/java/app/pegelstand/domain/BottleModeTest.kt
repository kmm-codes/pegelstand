package app.pegelstand.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BottleModeTest {
    @Test
    fun `fill mode shows what was drunk`() {
        assertEquals(0f, shownFill(0, 2000, BottleMode.FILL), 0f)
        assertEquals(0.375f, shownFill(750, 2000, BottleMode.FILL), 1e-6f)
        assertEquals(1f, shownFill(2000, 2000, BottleMode.FILL), 0f)
        assertEquals(1f, shownFill(3250, 2000, BottleMode.FILL), 0f)
    }

    @Test
    fun `drain mode shows what is left of the goal`() {
        assertEquals(1f, shownFill(0, 2000, BottleMode.DRAIN), 0f)
        assertEquals(0.625f, shownFill(750, 2000, BottleMode.DRAIN), 1e-6f)
        assertEquals(0f, shownFill(2000, 2000, BottleMode.DRAIN), 0f)
        assertEquals(0f, shownFill(3250, 2000, BottleMode.DRAIN), 0f)
    }

    @Test
    fun `a goal of zero counts as reached and does not divide by zero`() {
        assertEquals(1f, shownFill(0, 0, BottleMode.FILL), 0f)
        assertEquals(0f, shownFill(0, 0, BottleMode.DRAIN), 0f)
        assertEquals(1f, shownFill(500, -1, BottleMode.FILL), 0f)
    }

    @Test
    fun `every mode round-trips through its id`() {
        BottleMode.entries.forEach { assertEquals(it, BottleMode.fromId(it.id)) }
    }

    @Test
    fun `missing or unknown id falls back to fill`() {
        assertEquals(BottleMode.FILL, BottleMode.fromId(null))
        assertEquals(BottleMode.FILL, BottleMode.fromId(""))
        assertEquals(BottleMode.FILL, BottleMode.fromId("drain")) // ids are case-sensitive
        assertEquals(BottleMode.FILL, BottleMode.DEFAULT)
    }
}
