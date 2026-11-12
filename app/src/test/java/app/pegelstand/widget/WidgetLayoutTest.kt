package app.pegelstand.widget

import app.pegelstand.data.TodayState
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.Streak
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.bottle.BottleRenderer
import app.pegelstand.widget.WidgetLayout.Companion.CORNER_GAP
import app.pegelstand.widget.WidgetLayout.Companion.LABEL_PADDING
import app.pegelstand.widget.WidgetLayout.Companion.LINE_HEIGHT
import app.pegelstand.widget.WidgetLayout.Companion.MIN_BOTTLE
import app.pegelstand.widget.WidgetLayout.Companion.MIN_FONT
import app.pegelstand.widget.WidgetLayout.Companion.MIN_TOUCH
import app.pegelstand.widget.WidgetLayout.Companion.READABLE_FONT
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
import java.time.LocalDate

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

    /** Sizes the user's phone (Pixel 9 Pro, Pixel Launcher) reported, with the shape each must get. */
    private val phoneSizes = mapOf(
        (85f to 208f) to SLIM,
        (186f to 208f) to STACKED,
        (186f to 99f) to SIDE,
        (85f to 99f) to MINI,
        (184f to 122f) to SIDE,
    )

    /** The test emulator's cells (Pixel 9a). */
    private val emulatorSizes = mapOf(
        (78f to 104f) to MINI,
        (78f to 224f) to SLIM,
        (172f to 104f) to SIDE,
        (172f to 224f) to STACKED,
    )

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
            assertFalse("no chip at $h", l.chipRoom)
        }
    }

    @Test
    fun `flat drops undo and chip only where they would touch the text`() {
        val twoByOne = layout(172f, oneRow)
        assertFalse("2x1 has no room for undo", twoByOne.undoFits)
        assertTrue("2x1 has room for the chip", twoByOne.chipRoom)
        val fourByOne = layout(360f, oneRow)
        assertTrue(fourByOne.undoFits)
        assertTrue(fourByOne.chipRoom)
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
    fun `shape, sizes and every box ignore the day's state`() {
        // Each combination of no streak / a streak (first day, longer, three digits) with nothing
        // drunk, some, just below the goal, the goal, above it, and every total below 10 L.
        val sizes = steps.flatMap { w -> steps.map { h -> w to h } } + phoneSizes.keys + emulatorSizes.keys
        for (goal in listOf(1000, 2000, 2250, 5000)) {
            val totals = (listOf(0, 250, 1250, goal - 250, goal, goal + 750) + (500..9750 step 250)).distinct()
            val texts = listOf(0, 1, 7, 365).flatMap { streak ->
                totals.map { total -> "$total of $goal, streak $streak" to WidgetViews.texts(state(total, goal, streak)) }
            }
            for ((w, h) in sizes) {
                val first = layout(w, h, texts[0].second)
                val firstBoxes = first.boxes(w, h)
                for ((what, t) in texts) {
                    val l = layout(w, h, t)
                    assertEquals("$w x $h, $what", first, l)
                    assertEquals("$w x $h, $what", firstBoxes, l.boxes(w, h))
                }
            }
        }
    }

    @Test
    fun `phone and emulator cells get their shape in every state`() {
        for ((size, shape) in phoneSizes + emulatorSizes) for (streak in listOf(0, 1, 12)) for (total in listOf(0, 1250, 2000, 2750)) {
            val (w, h) = size
            val l = WidgetLayout.compute(w, h, BottleRenderer.ASPECT, WidgetViews.texts(state(total, 2000, streak)))
            assertEquals("$w x $h, $total of 2000, streak $streak", shape, l.shape)
            if (shape == STACKED || shape == SIDE) assertTrue("readable at $w x $h", l.amountFontSize >= READABLE_FONT)
        }
    }

    @Test
    fun `the reported 1x2 widget is slim before and after the goal, undo centred at the bottom`() {
        val before = WidgetViews.texts(state(1250, 2000, 0))
        val after = WidgetViews.texts(state(2000, 2000, 1))
        val l = WidgetLayout.compute(85f, 208f, BottleRenderer.ASPECT, before)
        assertEquals(SLIM, l.shape)
        assertEquals(l, WidgetLayout.compute(85f, 208f, BottleRenderer.ASPECT, after))
        assertEquals(BoxDp(0f, 208f - MIN_TOUCH, 85f, MIN_TOUCH), l.boxes(85f, 208f).undo)
    }

    @Test
    fun `the emulator's 2x2 keeps its layout`() {
        val l = WidgetLayout.compute(172f, 224f, BottleRenderer.ASPECT, WidgetViews.texts(state(1250, 2000, 7)))
        assertEquals(STACKED, l.shape)
        assertEquals(93f, l.bottleWidth)
        assertEquals(120f, l.bottleHeight)
        assertEquals(36f, l.controlRowHeight)
        assertEquals(48f, l.amountRowHeight)
        assertEquals(24f, l.amountFontSize)
        assertEquals(15f, l.chipFontSize)
        assertEquals(48f, l.controlSize)
        assertTrue(l.undoFits && l.chipRoom)
    }

    @Test
    fun `a hidden chip keeps its slot and every streak below 1000 days fits it`() {
        for (w in steps) for (h in steps) {
            val l = layout(w, h)
            if (!l.chipRoom) continue
            assertFalse("no chip without a streak at $w x $h", l.chipShown(WidgetTexts.of(1250, 2250, 0)))
            for (streak in listOf(1, 9, 99, 999)) assertTrue("streak $streak at $w x $h", l.chipShown(WidgetTexts.of(1250, 2250, streak)))
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

    private fun state(totalMl: Int, goalMl: Int, streak: Int) = TodayState(
        day = LocalDate.of(2026, 11, 12),
        totalMl = totalMl,
        goalMl = goalMl,
        mlPerTap = 250,
        waterColor = WaterColor.entries.first(),
        bottleMode = BottleMode.FILL,
        streak = Streak(streak, streak),
        widgetIdleWave = true,
    )

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

        val boxes = l.boxes(w, h)
        val widget = BoxDp(0f, 0f, w, h)
        assertEquals("bottle box $where", l.bottleWidth to l.bottleHeight, boxes.bottle.width to boxes.bottle.height)
        for (box in listOfNotNull(boxes.bottle, boxes.amount, boxes.chip, boxes.undo)) assertTrue("$box inside $where", box.inside(widget))
        boxes.amount?.let { if (l.shape != MINI) assertFalse("amount clear of the bottle $where", it.overlaps(boxes.bottle)) }
        boxes.chip?.let { chip -> boxes.undo?.let { assertFalse("chip slot clear of undo $where", chip.overlaps(it)) } }

        when (l.shape) {
            STACKED -> {
                assertTrue("readable $where", l.amountFontSize >= READABLE_FONT)
                assertTrue("text $where", fits(t.line, l.amountFontSize, innerW - 2 * TEXT_PADDING))
                assertTrue("column $where", l.controlRowHeight + l.bottleHeight + l.amountRowHeight <= innerH + 0.5f)
                assertTrue("amount touch height $where", l.amountRowHeight + l.padding >= 40f)
                if (l.controlRowHeight == 0f) {
                    // Corners beside the bottle: it leaves at least 3/4 of a control on each side.
                    assertTrue("side room $where", (innerW - l.bottleWidth) / 2 >= l.controlSize * 0.75f - 0.5f)
                }
                // The slot is reserved in every stacked widget, sized for the reference chip.
                assertTrue("chip slot $where", l.chipRoom)
                val chip = WidgetLayout.chipWidth(WidgetTexts.REFERENCE_CHIP, l.chipFontSize)
                assertTrue("chip and undo apart $where", chip <= l.chipSlotWidth && l.chipSlotWidth + CORNER_GAP + l.controlSize <= innerW + 0.01f)
            }
            SIDE -> {
                assertTrue("readable $where", l.amountFontSize >= READABLE_FONT)
                val columnStart = l.bottleWidth + 2 * SIDE_GAP
                assertEquals("column reaches the padding $where", innerW, columnStart + l.textColumnWidth, 0.01f)
                val textW = estimateWidthEm(t.line) * l.amountFontSize + 2 * TEXT_PADDING
                assertTrue("text $where", textW <= l.textColumnWidth)
                val chipH = if (l.chipRoom) WidgetLayout.chipHeight(l.chipFontSize) else 0f
                assertTrue("column height $where", l.amountRowHeight + chipH <= innerH + 0.01f)
                if (l.undoFits) {
                    val top = (innerH - l.amountRowHeight - chipH) / 2f
                    val glyph = l.amountFontSize * LINE_HEIGHT
                    val text = BoxDp(columnStart + (l.textColumnWidth - textW) / 2f, top + (l.amountRowHeight - glyph) / 2f, textW, glyph)
                    val undo = BoxDp(innerW - l.controlSize, 0f, l.controlSize, l.controlSize)
                    assertFalse("undo touches the text $where", undo.overlaps(text))
                    if (l.chipRoom) {
                        val chipW = WidgetLayout.chipWidth(WidgetTexts.REFERENCE_CHIP, l.chipFontSize)
                        val chip = BoxDp(columnStart + (l.textColumnWidth - chipW) / 2f, top + l.amountRowHeight, chipW, chipH)
                        assertFalse("undo touches the chip $where", undo.overlaps(chip))
                    }
                }
            }
            SLIM -> {
                assertTrue("drunk $where", fits(t.drunk, l.amountFontSize, innerW))
                assertTrue("goal $where", fits(t.goal, l.goalFontSize, innerW))
                assertTrue("goal font $where", l.goalFontSize >= MIN_FONT)
                assertTrue("rows $where", l.amountRowHeight >= l.amountFontSize * LINE_HEIGHT && l.goalRowHeight >= l.goalFontSize * LINE_HEIGHT)
                assertTrue("column $where", l.bottleHeight + l.amountRowHeight + l.goalRowHeight + l.undoRowHeight <= innerH + 0.01f)
                assertFalse("no chip $where", l.chipRoom)
                // The undo strip: full width, at least 48 dp, down to the bottom edge, starting where
                // the amount ends, clear of the bottle.
                val undo = boxes.undo!!
                assertEquals("strip $where", BoxDp(0f, h - undo.height, w, undo.height), undo)
                assertTrue("strip height $where", undo.height >= MIN_TOUCH)
                assertEquals("strip below the amount $where", boxes.amount!!.bottom, undo.y, 0.01f)
                assertFalse("strip clear of the bottle $where", undo.overlaps(boxes.bottle))
            }
            MINI -> {
                assertFalse("no undo $where", l.undoFits)
                assertFalse("no chip $where", l.chipRoom)
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

    private fun BoxDp.inside(o: BoxDp): Boolean =
        x >= o.x - 0.01f && y >= o.y - 0.01f && right <= o.right + 0.01f && bottom <= o.bottom + 0.01f
}
