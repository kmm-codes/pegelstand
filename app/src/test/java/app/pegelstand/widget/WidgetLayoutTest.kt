package app.pegelstand.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private val aspect = 0.78f

    // Typical phone launcher cell sizes (dp): 2x2, 2x3, 3x2, 4x2.
    private val sizes = listOf(170f to 180f, 170f to 280f, 260f to 180f, 350f to 180f, 110f to 110f)
    private val longest = "10,25 / 5 L"

    @Test
    fun `wide widgets put the bottle beside the text`() {
        assertFalse(WidgetLayout.compute(170f, 180f, aspect, "0 / 2 L").sideBySide)
        assertFalse(WidgetLayout.compute(170f, 280f, aspect, "0 / 2 L").sideBySide)
        assertTrue(WidgetLayout.compute(350f, 180f, aspect, "0 / 2 L").sideBySide)
    }

    @Test
    fun `everything fits inside the widget at every size`() {
        for ((w, h) in sizes) {
            val l = WidgetLayout.compute(w, h, aspect, longest)
            val innerW = w - 2 * l.padding
            val innerH = h - 2 * l.padding
            assertTrue("bottle height at $w x $h", l.bottleHeight > 0f)
            assertEquals("bottle aspect at $w x $h", aspect, l.bottleWidth / l.bottleHeight, 0.02f)
            if (l.sideBySide) {
                assertTrue("bottle height at $w x $h", l.bottleHeight <= innerH)
                val textW = innerW - l.bottleWidth - 12f
                assertTrue("text width at $w x $h", WidgetLayout.estimateWidthEm(longest) * l.amountFontSize <= textW)
            } else {
                assertTrue("column height at $w x $h", l.controlRowHeight + l.bottleHeight + l.amountRowHeight <= innerH + 0.5f)
                assertTrue("bottle width at $w x $h", l.bottleWidth <= innerW)
                assertTrue("text width at $w x $h", WidgetLayout.estimateWidthEm(longest) * l.amountFontSize <= innerW)
            }
        }
    }

    @Test
    fun `stacked corners never overlap the bottle`() {
        for ((w, h) in sizes) {
            val l = WidgetLayout.compute(w, h, aspect, longest)
            if (l.sideBySide || l.controlRowHeight > 0f) continue
            // Controls beside the bottle: the bottle leaves at least 3/4 of a control on each side.
            val innerW = w - 2 * l.padding
            assertTrue("side room at $w x $h", (innerW - l.bottleWidth) / 2 >= l.controlSize * 0.75f - 0.5f)
        }
    }

    /** Every size from the minimum (110 dp) to a large tablet cell, in 10 dp steps. */
    private val sweep = (110..500 step 10).flatMap { w -> (110..500 step 10).map { h -> w.toFloat() to h.toFloat() } }

    @Test
    fun `stacked open strip covers the amount row down to the edge and leaves bottle and corners`() {
        var withRow = 0
        var withoutRow = 0
        for ((w, h) in sweep) {
            val l = WidgetLayout.compute(w, h, aspect, longest)
            if (l.sideBySide) continue
            if (l.controlRowHeight > 0f) withRow++ else withoutRow++
            val stripTop = h - l.openStripHeight
            assertTrue("strip touch height at $w x $h", l.openStripHeight >= 48f)
            // The bottle (centred in the space above the amount row) stays outside the strip.
            assertTrue("bottle above strip at $w x $h", l.padding + l.controlRowHeight + l.bottleHeight <= stripTop + 0.5f)
            // Chip and undo boxes in the top corners do not reach down into the strip.
            assertTrue("corners above strip at $w x $h", l.padding + l.controlSize <= stripTop)
        }
        assertTrue("sweep covers stacked with a control row", withRow > 0)
        assertTrue("sweep covers stacked without a control row", withoutRow > 0)
    }

    @Test
    fun `side by side open column starts at the text column and reaches the end edge`() {
        var count = 0
        for ((w, h) in sweep) {
            val l = WidgetLayout.compute(w, h, aspect, longest)
            if (!l.sideBySide) continue
            count++
            val columnStart = l.padding + WidgetLayout.SIDE_GAP + l.bottleWidth + WidgetLayout.SIDE_GAP
            assertEquals("column reaches the edge at $w x $h", w, columnStart + l.openColumnWidth, 0.01f)
            assertTrue("bottle left of the column at $w x $h", l.padding + l.bottleWidth < columnStart)
            assertTrue("amount fits the text column at $w x $h", WidgetLayout.estimateWidthEm(longest) * l.amountFontSize <= l.textColumnWidth)
            assertTrue("column touch width at $w x $h", l.openColumnWidth >= 48f)
        }
        assertTrue("sweep covers side by side", count > 0)
    }

    @Test
    fun `touch targets are 48 dp when the widget is large enough`() {
        assertEquals(48f, WidgetLayout.compute(170f, 180f, aspect, longest).controlSize)
        assertEquals(40f, WidgetLayout.compute(110f, 110f, aspect, longest).controlSize)
    }

    @Test
    fun `text estimate is wider for longer amounts`() {
        assertTrue(WidgetLayout.estimateWidthEm("10,25 / 5 L") > WidgetLayout.estimateWidthEm("0 / 2 L"))
    }
}
