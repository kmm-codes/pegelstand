package app.pegelstand.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {
    @Test
    fun `liters with decimal comma and no trailing zeros`() {
        assertEquals("0", AmountFormat.liters(0))
        assertEquals("0,25", AmountFormat.liters(250))
        assertEquals("1,25", AmountFormat.liters(1250))
        assertEquals("2", AmountFormat.liters(2000))
        assertEquals("2,5", AmountFormat.liters(2500))
        assertEquals("0,05", AmountFormat.liters(50))
        assertEquals("10", AmountFormat.liters(10000))
    }

    @Test
    fun `at most two decimals`() {
        assertEquals("1,26", AmountFormat.liters(1255))
        assertEquals("1,25", AmountFormat.liters(1254))
        assertEquals("1", AmountFormat.liters(999))
    }

    @Test
    fun `total of goal line`() {
        assertEquals("1,25 / 2 L", AmountFormat.totalOfGoal(1250, 2000))
        assertEquals("0 / 2 L", AmountFormat.totalOfGoal(0, 2000))
        assertEquals("2,5 / 2,25 L", AmountFormat.totalOfGoal(2500, 2250))
    }

    @Test
    fun `goal part on its own`() {
        assertEquals("/ 2 L", AmountFormat.ofGoal(2000))
        assertEquals("/ 2,25 L", AmountFormat.ofGoal(2250))
    }

    @Test
    fun `amount with capital L unit`() {
        assertEquals("1,75 L", AmountFormat.litersWithUnit(1750))
        assertEquals("0 L", AmountFormat.litersWithUnit(0))
    }
}
