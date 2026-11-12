package app.pegelstand.widget

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Sizes of the widget's parts for one widget size, all in dp. Pure, so it is unit-tested.
 *
 * Two arrangements:
 * - **Stacked** (square and tall widgets): bottle above the amount line. The streak chip sits in
 *   the top-left and the undo control in the top-right corner. If the bottle is width-limited and
 *   there is spare height, the corners get their own row above the bottle; otherwise the bottle is
 *   narrowed so the corners stay beside it. Whichever gives the larger bottle wins.
 * - **Side by side** (wide widgets): bottle on the left at full height, amount line and chip to
 *   its right, undo in the top-right corner.
 *
 * Tap zones (see SPEC): only the bottle's box ([bottleWidth] x [bottleHeight]) adds an intake;
 * every other spot opens the app, undo undoes.
 */
data class WidgetLayout(
    val sideBySide: Boolean,
    val padding: Float,
    val bottleWidth: Float,
    val bottleHeight: Float,
    /** Height reserved above the bottle for chip and undo (stacked only; 0 = controls beside it). */
    val controlRowHeight: Float,
    val amountRowHeight: Float,
    val amountFontSize: Float,
    val chipFontSize: Float,
    /** Undo touch target edge. */
    val controlSize: Float,
    /** Width of the text column (amount and chip); side by side only, 0 when stacked. */
    val textColumnWidth: Float,
) {
    companion object {
        const val PADDING = 10f

        /** Margin on each side of the bottle in the side-by-side layout (widget_side.xml). */
        const val SIDE_GAP = 6f
        private const val MIN_TOUCH = 48f
        private const val SMALL_TOUCH = 40f
        private const val WIDE_RATIO = 1.45f
        private const val MAX_FONT = 30f
        private const val MIN_FONT = 11f
        private const val GAP = 6f

        /**
         * @param aspect width / height of the bottle drawing.
         * @param amountText the text that must fit on one line, e.g. "1,25 / 2 L".
         */
        fun compute(widthDp: Float, heightDp: Float, aspect: Float, amountText: String): WidgetLayout {
            val w = max(widthDp, 40f)
            val h = max(heightDp, 40f)
            val control = if (min(w, h) >= 120f) MIN_TOUCH else SMALL_TOUCH
            val innerW = w - 2 * PADDING
            val innerH = h - 2 * PADDING
            val textEm = estimateWidthEm(amountText)

            if (w >= h * WIDE_RATIO) {
                // A little air above and below, so the cap and base do not touch the rounded edge.
                val bottleH = innerH - 2 * GAP
                val bottleW = min(bottleH * aspect, innerW * 0.5f)
                val textW = innerW - bottleW - 2 * GAP - control / 2f
                val font = fitFont(textW, textEm, maxFont = min(MAX_FONT, innerH * 0.24f))
                val bottleWidth = floor(bottleW)
                return WidgetLayout(
                    sideBySide = true,
                    padding = PADDING,
                    bottleWidth = bottleWidth,
                    bottleHeight = floor(bottleW / aspect),
                    controlRowHeight = 0f,
                    amountRowHeight = max(control, font * 1.6f),
                    amountFontSize = font,
                    chipFontSize = chipFont(font),
                    controlSize = control,
                    // Exact, so the XML weights have nothing left to share.
                    textColumnWidth = max(0f, innerW - bottleWidth - 2 * SIDE_GAP),
                )
            }

            val amountRow = (h * 0.2f).coerceIn(if (h >= 160f) MIN_TOUCH else SMALL_TOUCH, 56f)
            val font = fitFont(innerW, textEm, maxFont = min(MAX_FONT * 0.8f, amountRow * 0.5f))
            val bottleAreaH = innerH - amountRow
            // Option A: corners beside the bottle (bottle narrowed by both corner columns).
            val besideW = min(innerW - 2 * control * 0.75f, bottleAreaH * aspect)
            // Option B: corners in their own row above the bottle.
            val rowH = control * 0.75f
            val aboveW = min(innerW, (bottleAreaH - rowH) * aspect)
            val above = aboveW > besideW
            val bottleW = max(0f, if (above) aboveW else besideW)
            return WidgetLayout(
                sideBySide = false,
                padding = PADDING,
                bottleWidth = floor(bottleW),
                bottleHeight = floor(bottleW / aspect),
                controlRowHeight = if (above) rowH else 0f,
                amountRowHeight = amountRow,
                amountFontSize = font,
                chipFontSize = chipFont(font),
                controlSize = control,
                textColumnWidth = 0f,
            )
        }

        /** Font size (dp) at which [em] ems fit into [availableWidth], clamped to [MIN_FONT]..maxFont. */
        private fun fitFont(availableWidth: Float, em: Float, maxFont: Float): Float {
            val fit = availableWidth / max(em, 1f)
            return floor(fit.coerceIn(MIN_FONT, max(MIN_FONT, maxFont)))
        }

        private fun chipFont(amountFont: Float): Float = floor((amountFont * 0.72f).coerceIn(11f, 15f))

        /**
         * Conservative width of [text] in ems for a medium-weight sans-serif (digits are
         * tabular, about 0.56 em), with 8 % headroom so the line is never ellipsized.
         */
        fun estimateWidthEm(text: String): Float {
            var em = 0f
            for (c in text) {
                em += when (c) {
                    in '0'..'9' -> 0.58f
                    ',', '.', ' ' -> 0.28f
                    '/' -> 0.42f
                    'l', 'i' -> 0.26f
                    else -> 0.62f
                }
            }
            return em * 1.08f
        }
    }
}
