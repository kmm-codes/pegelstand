package app.pegelstand.widget

import app.pegelstand.domain.AmountFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How the widget arranges bottle, amount and controls; chosen by [WidgetLayout.compute]. */
enum class WidgetShape {
    /** Bottle above the one-line amount; streak chip and undo in the top corners. */
    STACKED,

    /** Bottle on the left, one-line amount and chip to its right, undo top right ("flat" at one row). */
    SIDE,

    /** Narrow: bottle, the amount in two lines (drunk large, "/ goal" small and muted), undo below. */
    SLIM,

    /** Smallest: the bottle alone, the drunk amount on a small label on its body. */
    MINI,
}

/**
 * The texts of one state, and the texts whose widths decide the layout. Each `…Fit` text is the
 * wider of the shown text and the same text for [REFERENCE_TOTAL_ML]. Digits are estimated alike,
 * so for every total below 10 L the shape and the controls stay the same as she drinks; only a
 * longer amount can change them.
 */
data class WidgetTexts(
    /** One-line amount `1,25 / 2 L` (stacked, side; spoken in slim). */
    val line: String,
    /** The drunk amount alone, `1,25` (slim, large). */
    val drunk: String,
    /** The goal part, `/ 2 L` (slim, small). */
    val goal: String,
    /** The drunk amount with unit, `1,25 L` (mini label). */
    val label: String,
    /** Streak chip `🔥 6`; null below a streak of one day. */
    val chip: String?,
    val lineFit: String,
    val drunkFit: String,
    val labelFit: String,
) {
    companion object {
        /** A total as wide as any total below 10 L: one digit, two decimals. */
        const val REFERENCE_TOTAL_ML = 8880

        fun of(totalMl: Int, goalMl: Int, streak: Int): WidgetTexts {
            val line = AmountFormat.totalOfGoal(totalMl, goalMl)
            val drunk = AmountFormat.liters(totalMl)
            val label = AmountFormat.litersWithUnit(totalMl)
            return WidgetTexts(
                line = line,
                drunk = drunk,
                goal = AmountFormat.ofGoal(goalMl),
                label = label,
                chip = if (streak >= 1) "🔥 $streak" else null,
                lineFit = wider(line, AmountFormat.totalOfGoal(REFERENCE_TOTAL_ML, goalMl)),
                drunkFit = wider(drunk, AmountFormat.liters(REFERENCE_TOTAL_ML)),
                labelFit = wider(label, AmountFormat.litersWithUnit(REFERENCE_TOTAL_ML)),
            )
        }

        private fun wider(a: String, b: String): String =
            if (WidgetLayout.estimateWidthEm(b) > WidgetLayout.estimateWidthEm(a)) b else a
    }
}

/**
 * Sizes of the widget's parts for one widget size, all in dp. Pure, so it is unit-tested.
 *
 * The shape follows from what fits at the reported size, not from cell counts. In order of
 * preference (a wide widget tries [WidgetShape.SIDE] first, any other [WidgetShape.STACKED]):
 * - **Stacked**: needs the one-line amount at [MIN_FONT], a bottle of at least [MIN_BOTTLE] and
 *   room for chip and undo side by side at the top. The corners get their own row above the
 *   bottle if that gives the larger bottle, else the bottle is narrowed to leave them room.
 * - **Side** (flat at one row): needs the one-line amount at [MIN_FONT] beside a bottle of at
 *   least [MIN_BOTTLE]. Chip and undo only where they do not touch the text.
 * - **Slim**: needs the two-line amount at [MIN_FONT] and a bottle of at least [MIN_BOTTLE] above
 *   it, with the undo row below.
 * - **Mini**: always possible; the label is left out only where not even [MIN_FONT] fits.
 *
 * Tap zones (see SPEC): only the bottle's box ([bottleWidth] x [bottleHeight]) adds an intake;
 * every other spot opens the app, undo undoes.
 */
data class WidgetLayout(
    val shape: WidgetShape,
    /** Padding of `widget_content` on every side. */
    val padding: Float,
    val bottleWidth: Float,
    val bottleHeight: Float,
    /** Stacked: height reserved above the bottle for chip and undo (0 = controls beside it). */
    val controlRowHeight: Float,
    /** The amount's height: one-line row (stacked, side), large line (slim), label (mini). */
    val amountRowHeight: Float,
    /** Font of the one-line amount, of the large drunk amount (slim) or of the label (mini). */
    val amountFontSize: Float,
    /** Slim: the "/ goal" line; 0 in the other shapes. */
    val goalRowHeight: Float,
    val goalFontSize: Float,
    val chipFontSize: Float,
    /** Undo touch target edge (0 in mini, which has no undo). */
    val controlSize: Float,
    /** Width of the text column (amount and chip); side only, 0 otherwise. */
    val textColumnWidth: Float,
    /** The amount is shown; false only for a mini widget too small for its label. */
    val amountShown: Boolean,
    /** There is room for this chip (false when there is no chip). */
    val chipFits: Boolean,
    /** There is room for the undo control (shown only when today has an intake). */
    val undoFits: Boolean,
    /** Mini: how far the label's centre lies below the centre of the bottle's box; 0 otherwise. */
    val labelOffset: Float,
) {
    companion object {
        const val PADDING = 10f

        /** Padding of the slim and mini shapes: the bottle's box has empty corners. */
        const val SMALL_PADDING = 6f

        /** Margin on each side of the bottle in the side-by-side layout (widget_side.xml). */
        const val SIDE_GAP = 6f

        /** Horizontal padding of the one-line amount (`paddingHorizontal` in widget_stacked/side.xml). */
        const val TEXT_PADDING = 4f

        /** Padding of the mini label (widget_mini.xml): horizontal and vertical. */
        const val LABEL_PADDING = 4f
        const val LABEL_PADDING_V = 1f

        /** Padding of the streak chip (widget_stacked/side.xml): horizontal and vertical. */
        const val CHIP_PADDING = 7f
        const val CHIP_PADDING_V = 2f

        /** Smallest font of any text, and the smallest bottle a shape with text may use. */
        const val MIN_FONT = 11f
        const val MIN_BOTTLE = 48f

        /** Height of one line of text per dp of font size (the font's line box with its padding). */
        const val LINE_HEIGHT = 1.4f

        /** Width of the glass relative to the bottle's box (BottleRenderer: the rest is sparkle margin). */
        const val BODY_SHARE = 0.72f

        /** The mini label's centre and the body it must stay on, as fractions of the bottle's height. */
        const val LABEL_CENTRE = 0.66f
        const val BODY_TOP = 0.37f
        const val BODY_BOTTOM = 0.97f

        /** Gap between the stacked chip and undo. */
        const val CORNER_GAP = 4f

        private const val MIN_TOUCH = 48f
        private const val SMALL_TOUCH = 40f
        private const val WIDE_RATIO = 1.45f
        private const val MAX_FONT = 30f
        private const val GAP = 6f
        private const val SLIM_MAX_FONT = 24f
        private const val SLIM_FONT_SHARE = 0.12f
        private const val SLIM_GOAL_SHARE = 0.6f
        private const val SLIM_GAP = 4f
        private const val LABEL_MAX_FONT = 13f

        /** The label may reach past the glass into the sparkle margins, up to this share of the box. */
        private const val LABEL_MAX_SHARE = 0.94f

        /** @param aspect width / height of the bottle drawing. */
        fun compute(widthDp: Float, heightDp: Float, aspect: Float, texts: WidgetTexts): WidgetLayout {
            val w = max(widthDp, 1f)
            val h = max(heightDp, 1f)
            val regular = if (w >= h * WIDE_RATIO) {
                side(w, h, aspect, texts) ?: stacked(w, h, aspect, texts)
            } else {
                stacked(w, h, aspect, texts) ?: side(w, h, aspect, texts)
            }
            return regular ?: slim(w, h, aspect, texts) ?: mini(w, h, aspect, texts)
        }

        /** Stacked with 48 dp touch targets where the size allows it, else with the smaller ones. */
        private fun stacked(w: Float, h: Float, aspect: Float, t: WidgetTexts): WidgetLayout? {
            val control = controlFor(w, h)
            return stacked(w, h, aspect, t, control) ?: if (control > SMALL_TOUCH) stacked(w, h, aspect, t, SMALL_TOUCH) else null
        }

        private fun stacked(w: Float, h: Float, aspect: Float, t: WidgetTexts, control: Float): WidgetLayout? {
            val innerW = w - 2 * PADDING
            val innerH = h - 2 * PADDING
            val textW = innerW - 2 * TEXT_PADDING
            if (!fits(t.lineFit, MIN_FONT, textW)) return null
            val amountRow = (h * 0.2f).coerceIn(if (h >= 160f) MIN_TOUCH else SMALL_TOUCH, 56f)
            val maxFont = min(MAX_FONT * 0.8f, amountRow * 0.5f)
            // Chip (top left) and undo (top right) share the top edge and must not touch.
            if (t.chip != null && chipWidth(t.chip, chipFont(maxFont)) + CORNER_GAP + control > innerW) return null
            val font = fitFont(textW, estimateWidthEm(t.line), maxFont)
            val bottleAreaH = innerH - amountRow
            // Option A: corners beside the bottle (bottle narrowed by both corner columns).
            val besideW = min(innerW - 2 * control * 0.75f, bottleAreaH * aspect)
            // Option B: corners in their own row above the bottle.
            val rowH = control * 0.75f
            val aboveW = min(innerW, (bottleAreaH - rowH) * aspect)
            val above = aboveW > besideW
            val exactW = max(0f, if (above) aboveW else besideW)
            val bottleW = floor(exactW)
            val bottleH = floor(exactW / aspect)
            if (bottleH < MIN_BOTTLE) return null
            return WidgetLayout(
                shape = WidgetShape.STACKED,
                padding = PADDING,
                bottleWidth = bottleW,
                bottleHeight = bottleH,
                controlRowHeight = if (above) rowH else 0f,
                amountRowHeight = amountRow,
                amountFontSize = font,
                goalRowHeight = 0f,
                goalFontSize = 0f,
                chipFontSize = chipFont(font),
                controlSize = control,
                textColumnWidth = 0f,
                amountShown = true,
                chipFits = t.chip != null,
                undoFits = true,
                labelOffset = 0f,
            )
        }

        private fun side(w: Float, h: Float, aspect: Float, t: WidgetTexts): WidgetLayout? {
            val control = controlFor(w, h)
            val innerW = w - 2 * PADDING
            val innerH = h - 2 * PADDING
            // A little air above and below, so the cap and base do not touch the rounded edge; and
            // never so wide that the amount no longer fits beside it at the smallest font.
            val textRoom = estimateWidthEm(t.lineFit) * MIN_FONT + 2 * TEXT_PADDING
            val exactW = max(0f, minOf((innerH - 2 * GAP) * aspect, innerW * 0.5f, innerW - 2 * SIDE_GAP - textRoom))
            val bottleW = floor(exactW)
            val bottleH = floor(exactW / aspect)
            if (bottleH < MIN_BOTTLE) return null
            // Exact, so the XML weights have nothing left to share.
            val column = max(0f, innerW - bottleW - 2 * SIDE_GAP)
            if (!fits(t.lineFit, MIN_FONT, column - 2 * TEXT_PADDING)) return null
            val maxFont = min(MAX_FONT, innerH * 0.24f)

            // The column's content (amount row, chip) is centred vertically. Undo (top right) is
            // clear of it if the text stays below the control even at the largest font with the
            // chip, or if the centred text is narrowed to leave a control's width on both sides.
            val tallest = amountRow(maxFont, control) + (t.chip?.let { chipHeight(chipFont(maxFont)) } ?: 0f)
            val textTop = (innerH - tallest) / 2f + (amountRow(maxFont, control) - maxFont * LINE_HEIGHT) / 2f
            val below = textTop >= control
            val narrowed = column - 2 * control
            val undoFits = below || fits(t.lineFit, MIN_FONT, narrowed - 2 * TEXT_PADDING)
            val textWidth = if (below || !undoFits) column else narrowed

            val font = fitFont(textWidth - 2 * TEXT_PADDING, estimateWidthEm(t.line), maxFont)
            val amountRow = amountRow(font, control)
            val chipFont = chipFont(font)
            val chipFits = t.chip != null &&
                amountRow + chipHeight(chipFont) <= innerH &&
                chipWidth(t.chip, chipFont) <= textWidth
            return WidgetLayout(
                shape = WidgetShape.SIDE,
                padding = PADDING,
                bottleWidth = bottleW,
                bottleHeight = bottleH,
                controlRowHeight = 0f,
                amountRowHeight = amountRow,
                amountFontSize = font,
                goalRowHeight = 0f,
                goalFontSize = 0f,
                chipFontSize = chipFont,
                controlSize = control,
                textColumnWidth = column,
                amountShown = true,
                chipFits = chipFits,
                undoFits = undoFits,
                labelOffset = 0f,
            )
        }

        private fun slim(w: Float, h: Float, aspect: Float, t: WidgetTexts): WidgetLayout? {
            val innerW = w - 2 * SMALL_PADDING
            val innerH = h - 2 * SMALL_PADDING
            if (!fits(t.drunkFit, MIN_FONT, innerW) || !fits(t.goal, MIN_FONT, innerW)) return null
            // Sized for the reference amount, so the number does not change size as she drinks.
            val large = fitFont(innerW, estimateWidthEm(t.drunkFit), min(SLIM_MAX_FONT, innerH * SLIM_FONT_SHARE))
            val small = fitFont(innerW, estimateWidthEm(t.goal), large * SLIM_GOAL_SHARE)
            val largeRow = ceil(large * LINE_HEIGHT)
            val smallRow = ceil(small * LINE_HEIGHT)
            val rest = innerH - largeRow - smallRow - SLIM_GAP
            // A 48 dp undo target where it costs the bottle nothing (the width limits it), else 40 dp.
            val control = if (rest - MIN_TOUCH >= innerW / aspect) MIN_TOUCH else SMALL_TOUCH
            val bottleW = floor(max(0f, min(innerW, (rest - control) * aspect)))
            val bottleH = floor(bottleW / aspect)
            if (bottleH < MIN_BOTTLE) return null
            return WidgetLayout(
                shape = WidgetShape.SLIM,
                padding = SMALL_PADDING,
                bottleWidth = bottleW,
                bottleHeight = bottleH,
                controlRowHeight = 0f,
                amountRowHeight = largeRow,
                amountFontSize = large,
                goalRowHeight = smallRow,
                goalFontSize = small,
                chipFontSize = 0f,
                controlSize = control,
                textColumnWidth = 0f,
                amountShown = true,
                chipFits = false,
                undoFits = true,
                labelOffset = 0f,
            )
        }

        private fun mini(w: Float, h: Float, aspect: Float, t: WidgetTexts): WidgetLayout {
            val innerW = max(0f, w - 2 * SMALL_PADDING)
            val innerH = max(0f, h - 2 * SMALL_PADDING)
            val bottleW = floor(min(innerW, innerH * aspect))
            val bottleH = floor(bottleW / aspect)
            // The label sits on the glass; a long amount may reach into the sparkle margins at the
            // smallest font, never out of the bottle's box (the add-intake zone).
            val em = estimateWidthEm(t.labelFit)
            val onGlass = bottleW * BODY_SHARE - 2 * LABEL_PADDING
            val inBox = bottleW * LABEL_MAX_SHARE - 2 * LABEL_PADDING
            val font = when {
                fits(t.labelFit, MIN_FONT, onGlass) -> floor(min(LABEL_MAX_FONT, onGlass / em))
                fits(t.labelFit, MIN_FONT, inBox) -> MIN_FONT
                else -> 0f
            }
            val labelH = ceil(font * LINE_HEIGHT) + 2 * LABEL_PADDING_V
            val centre = bottleH * LABEL_CENTRE
            val shown = font > 0f && centre - labelH / 2f >= bottleH * BODY_TOP && centre + labelH / 2f <= bottleH * BODY_BOTTOM
            return WidgetLayout(
                shape = WidgetShape.MINI,
                padding = SMALL_PADDING,
                bottleWidth = bottleW,
                bottleHeight = bottleH,
                controlRowHeight = 0f,
                amountRowHeight = if (shown) labelH else 0f,
                amountFontSize = if (shown) font else 0f,
                goalRowHeight = 0f,
                goalFontSize = 0f,
                chipFontSize = 0f,
                controlSize = 0f,
                textColumnWidth = 0f,
                amountShown = shown,
                chipFits = false,
                undoFits = false,
                labelOffset = centre - bottleH / 2f,
            )
        }

        private fun controlFor(w: Float, h: Float): Float = if (min(w, h) >= 120f) MIN_TOUCH else SMALL_TOUCH

        /** Height of the one-line amount's row (stacked, side) at [font]. */
        private fun amountRow(font: Float, control: Float): Float = max(control, font * 1.6f)

        /** True if [text] at [font] fits into [width] (estimated, see [estimateWidthEm]). */
        fun fits(text: String, font: Float, width: Float): Boolean = estimateWidthEm(text) * font <= width

        /** Font size (dp) at which [em] ems fit into [availableWidth], clamped to [MIN_FONT]..maxFont. */
        private fun fitFont(availableWidth: Float, em: Float, maxFont: Float): Float {
            val fit = availableWidth / max(em, 1f)
            return floor(fit.coerceIn(MIN_FONT, max(MIN_FONT, maxFont)))
        }

        fun chipFont(amountFont: Float): Float = floor((amountFont * 0.72f).coerceIn(11f, 15f))

        /** Outer width of the streak chip [chip] at [font], with its padding. */
        fun chipWidth(chip: String, font: Float): Float = estimateWidthEm(chip) * font + 2 * CHIP_PADDING

        /** Outer height of the streak chip at [font], with its padding. */
        fun chipHeight(font: Float): Float = font * LINE_HEIGHT + 2 * CHIP_PADDING_V

        /**
         * Conservative width of [text] in ems for a bold sans-serif (digits are tabular, about
         * 0.56 em; an emoji, two UTF-16 units, about 1.25 em), with 8 % headroom so a line is
         * never clipped or ellipsized.
         */
        fun estimateWidthEm(text: String): Float {
            var em = 0f
            for (c in text) {
                em += when {
                    c in '0'..'9' -> 0.58f
                    c == ',' || c == '.' || c == ' ' -> 0.28f
                    c == '/' -> 0.42f
                    c == 'l' || c == 'i' -> 0.26f
                    Character.isSurrogate(c) -> 0.625f
                    else -> 0.62f
                }
            }
            return em * 1.08f
        }
    }
}
