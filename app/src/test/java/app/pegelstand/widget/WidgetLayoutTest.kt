package app.pegelstand.widget

import app.pegelstand.widget.WidgetLayout.Companion.CORNER_GAP
import app.pegelstand.widget.WidgetLayout.Companion.LABEL_PADDING
import app.pegelstand.widget.WidgetLayout.Companion.LINE_HEIGHT
import app.pegelstand.widget.WidgetLayout.Companion.MIN_BOTTLE
import app.pegelstand.widget.WidgetLayout.Companion.MIN_FONT
import app.pegelstand.widget.WidgetLayout.Companion.SIDE_GAP
import app.pegelstand.widget.WidgetLayout.Companion.TEXT_PADDING
import app.pegelstand.widget.WidgetLayout.Companion.estimateWidthEm
import app.pegelstand.widget.WidgetShape.MINI
import app.pegelstand.widget.WidgetShape.SIDE
import app.pegelstand.widget.WidgetShape.SLIM
import app.pegelstand.widget.WidgetShape.STACKED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private val aspect = 0.78f

    private fun layout(w: Float, h: Float, texts: WidgetTexts = typical) = WidgetLayout.compute(w, h, aspect, texts)

    private val typical = WidgetTexts.of(1250, 2250, 7)

    /** Texts that stress the widths: long and short amounts, wide goals, no chip and a wide chip. */
    private val textSets = listOf(
        typical,
        WidgetTexts.of(0, 1000, 0),
        WidgetTexts.of(250, 1000, 1),
        WidgetTexts.of(2000, 2250, 7),
        WidgetTexts.of(8750, 4750, 12),
        WidgetTexts.of(12750, 5000, 365),
    )

    /** Every size from tiny to a large tablet cell, in 4 dp steps. */
    private val steps = (24..520 step 4).map { it.toFloat() }

    // Cell sizes on the development phone (Pixel 9a, portrait), in dp.
    private val oneCol = 78f
    private val oneRow = 104f

    @Test
    fun `regular sizes keep their arrangement`() {
        assertEquals(STACKED, layout(170f, 180f).shape)
        assertEquals(STACKED, layout(172f, 224f).shape)
        assertEquals(STACKED, layout(172f, 344f).shape)
        assertEquals(STACKED, layout(266f, 224f).shape)
        assertEquals(SIDE, layout(360f, 224f).shape)
        assertEquals(SIDE, layout(350f, 180f).shape)
    }

    @Test
    fun `small sizes get the slim, mini and flat shapes`() {
        assertEquals(MINI, layout(oneCol, oneRow).shape)
        assertEquals(SLIM, layout(oneCol, 224f).shape)
        assertEquals(SLIM, layout(oneCol, 344f).shape)
        for (w in listOf(172f, 266f, 360f)) assertEquals("flat at $w", SIDE, layout(w, oneRow).shape)
        // Other launchers' cells: smaller and larger single cells are mini as well.
        for ((w, h) in listOf(64f to 80f, 70f to 90f, 100f to 120f, 110f to 110f)) assertEquals("$w x $h", MINI, layout(w, h).shape)
    }

    @Test
    fun `slim bottle is as wide as the widget allows and the drunk amount is the larger line`() {
        for (h in listOf(224f, 344f)) {
            val l = layout(oneCol, h)
            assertEquals("bottle width at $h", oneCol - 2 * l.padding, l.bottleWidth, 1f)
            assertTrue("large over small at $h", l.amountFontSize > l.goalFontSize)
            assertTrue("undo row at $h", l.undoFits)
            assertFalse("no chip at $h", l.chipFits)
        }
    }

    @Test
    fun `flat drops undo and chip only where they would touch the text`() {
        val twoByOne = layout(172f, oneRow)
        assertFalse("2x1 has no room for undo", twoByOne.undoFits)
        assertTrue("2x1 has room for the chip", twoByOne.chipFits)
        val fourByOne = layout(360f, oneRow)
        assertTrue(fourByOne.undoFits)
        assertTrue(fourByOne.chipFits)
        val threeByOne = layout(266f, oneRow)
        assertTrue(threeByOne.undoFits)
    }

    @Test
    fun `mini label lies on the glass of a one-cell widget`() {
        val l = layout(oneCol, oneRow)
        assertTrue(l.amountShown)
        val labelW = estimateWidthEm(typical.label) * l.amountFontSize + 2 * LABEL_PADDING
        assertTrue("label width $labelW on glass ${l.bottleWidth * WidgetLayout.BODY_SHARE}", labelW <= l.bottleWidth * WidgetLayout.BODY_SHARE)
        assertTrue(l.amountFontSize >= 12f)
    }

    @Test
    fun `every size maps to one shape whose parts fit without clipping or touching`() {
        val seen = HashSet<WidgetShape>()
        for (texts in textSets) for (w in steps) for (h in steps) {
            val l = layout(w, h, texts)
            seen += l.shape
            check(l, w, h, texts)
        }
        assertEquals(WidgetShape.entries.toSet(), seen)
    }

    @Test
    fun `growing a widget never returns to a shape it has left`() {
        for (texts in textSets) {
            for (h in steps) assertNoReturn("width sweep at h=$h", steps.map { layout(it, h, texts).shape })
            for (w in steps) assertNoReturn("height sweep at w=$w", steps.map { layout(w, it, texts).shape })
        }
    }

    @Test
    fun `shape and controls stay the same as she drinks, below 10 L`() {
        for (goal in listOf(1000, 2000, 2250, 5000)) for (w in steps) for (h in steps) {
            val first = layout(w, h, WidgetTexts.of(0, goal, 7))
            for (total in 250..9750 step 250) {
                val l = layout(w, h, WidgetTexts.of(total, goal, 7))
                val where = "$w x $h, $total of $goal"
                assertEquals(where, first.shape, l.shape)
                assertEquals(where, first.bottleWidth, l.bottleWidth)
                assertEquals(where, first.undoFits, l.undoFits)
                assertEquals(where, first.chipFits, l.chipFits)
                if (l.shape == SLIM || l.shape == MINI) assertEquals(where, first.amountFontSize, l.amountFontSize)
            }
        }
    }

    @Test
    fun `touch targets are 48 dp when the widget is large enough`() {
        assertEquals(48f, layout(170f, 180f).controlSize)
        assertEquals(48f, layout(oneCol, 224f).controlSize)
        assertEquals(40f, layout(172f, oneRow).controlSize)
    }

    @Test
    fun `text estimate is wider for longer amounts and counts an emoji once`() {
        assertTrue(estimateWidthEm("10,25 / 5 L") > estimateWidthEm("0 / 2 L"))
        assertEquals(1.25f * 1.08f, estimateWidthEm("🔥"), 0.001f)
    }

    @Test
    fun `fit texts are the wider of the shown amount and the reference`() {
        val t = WidgetTexts.of(250, 1000, 0)
        assertEquals("0,25 / 1 L", t.line)
        assertEquals("0,25", t.drunk)
        assertEquals("/ 1 L", t.goal)
        assertEquals("0,25 L", t.label)
        assertEquals(null, t.chip)
        val none = WidgetTexts.of(0, 1000, 0)
        assertEquals("8,88 / 1 L", none.lineFit)
        assertEquals("8,88", none.drunkFit)
        assertEquals("8,88 L", none.labelFit)
        val long = WidgetTexts.of(12750, 5000, 3)
        assertEquals("12,75 / 5 L", long.lineFit)
        assertEquals("12,75 L", long.labelFit)
        assertEquals("🔥 3", long.chip)
    }

    private fun assertNoReturn(what: String, shapes: List<WidgetShape>) {
        val runs = shapes.fold(mutableListOf<WidgetShape>()) { acc, s -> acc.apply { if (lastOrNull() != s) add(s) } }
        assertEquals("$what: $runs", runs.size, runs.toSet().size)
    }

    private fun check(l: WidgetLayout, w: Float, h: Float, t: WidgetTexts) {
        val where = "${l.shape} at $w x $h for ${t.line}"
        val innerW = w - 2 * l.padding
        val innerH = h - 2 * l.padding
        assertTrue("bottle width $where", l.bottleWidth >= 0f && l.bottleWidth <= innerW + 0.01f)
        assertTrue("bottle height $where", l.bottleHeight >= 0f && l.bottleHeight <= innerH + 0.01f)
        // Both sides rounded down to whole dp.
        assertEquals("bottle aspect $where", l.bottleWidth / aspect, l.bottleHeight, 1.3f)
        if (l.shape != MINI) assertTrue("bottle not tiny $where", l.bottleHeight >= MIN_BOTTLE)
        if (l.amountShown) assertTrue("font $where", l.amountFontSize >= MIN_FONT)

        when (l.shape) {
            STACKED -> {
                assertTrue("text $where", fits(t.line, l.amountFontSize, innerW - 2 * TEXT_PADDING))
                assertTrue("column $where", l.controlRowHeight + l.bottleHeight + l.amountRowHeight <= innerH + 0.5f)
                assertTrue("amount touch height $where", l.amountRowHeight + l.padding >= 40f)
                if (l.controlRowHeight == 0f) {
                    // Corners beside the bottle: it leaves at least 3/4 of a control on each side.
                    assertTrue("side room $where", (innerW - l.bottleWidth) / 2 >= l.controlSize * 0.75f - 0.5f)
                }
                if (l.chipFits) {
                    val chip = WidgetLayout.chipWidth(t.chip!!, l.chipFontSize)
                    assertTrue("chip and undo apart $where", chip + CORNER_GAP + l.controlSize <= innerW)
                }
            }
            SIDE -> {
                val columnStart = l.bottleWidth + 2 * SIDE_GAP
                assertEquals("column reaches the padding $where", innerW, columnStart + l.textColumnWidth, 0.01f)
                val textW = estimateWidthEm(t.line) * l.amountFontSize + 2 * TEXT_PADDING
                assertTrue("text $where", textW <= l.textColumnWidth)
                val chipH = if (l.chipFits) WidgetLayout.chipHeight(l.chipFontSize) else 0f
                assertTrue("column height $where", l.amountRowHeight + chipH <= innerH + 0.01f)
                if (l.undoFits) {
                    val top = (innerH - l.amountRowHeight - chipH) / 2f
                    val glyph = l.amountFontSize * LINE_HEIGHT
                    val text = Box(columnStart + (l.textColumnWidth - textW) / 2f, top + (l.amountRowHeight - glyph) / 2f, textW, glyph)
                    val undo = Box(innerW - l.controlSize, 0f, l.controlSize, l.controlSize)
                    assertFalse("undo touches the text $where", undo.overlaps(text))
                    if (l.chipFits) {
                        val chipW = WidgetLayout.chipWidth(t.chip!!, l.chipFontSize)
                        val chip = Box(columnStart + (l.textColumnWidth - chipW) / 2f, top + l.amountRowHeight, chipW, chipH)
                        assertFalse("undo touches the chip $where", undo.overlaps(chip))
                    }
                }
            }
            SLIM -> {
                assertTrue("drunk $where", fits(t.drunk, l.amountFontSize, innerW))
                assertTrue("goal $where", fits(t.goal, l.goalFontSize, innerW))
                assertTrue("goal font $where", l.goalFontSize >= MIN_FONT)
                assertTrue("rows $where", l.amountRowHeight >= l.amountFontSize * LINE_HEIGHT && l.goalRowHeight >= l.goalFontSize * LINE_HEIGHT)
                assertTrue("column $where", l.bottleHeight + l.amountRowHeight + l.goalRowHeight + l.controlSize <= innerH + 0.01f)
                assertFalse("no chip $where", l.chipFits)
            }
            MINI -> {
                assertFalse("no undo $where", l.undoFits)
                assertFalse("no chip $where", l.chipFits)
                if (l.amountShown) {
                    val labelW = estimateWidthEm(t.label) * l.amountFontSize + 2 * LABEL_PADDING
                    assertTrue("label inside the bottle's box $where", labelW <= l.bottleWidth)
                    val centre = l.bottleHeight / 2f + l.labelOffset
                    assertTrue("label below the shoulder $where", centre - l.amountRowHeight / 2f >= l.bottleHeight * WidgetLayout.BODY_TOP - 0.01f)
                    assertTrue("label above the base $where", centre + l.amountRowHeight / 2f <= l.bottleHeight * WidgetLayout.BODY_BOTTOM + 0.01f)
                }
            }
        }
    }

    private fun fits(text: String, font: Float, width: Float) = estimateWidthEm(text) * font <= width + 0.01f

    private data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun overlaps(o: Box): Boolean = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    }
}
