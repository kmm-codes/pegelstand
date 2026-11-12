package app.pegelstand.ui.bottle

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import app.pegelstand.domain.WaterColor
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the Pegelstand bottle: rounded shoulders, short neck, cap, water with a gentle wave.
 *
 * Used by the widget (into a bitmap) and by the app (Compose `Canvas` via `nativeCanvas`), so
 * both show the identical bottle. One instance may be reused for every frame of an animation:
 * paths, paints, rects and the gradient are kept and only rebuilt when the size or the water
 * colour changes; the wave path is reset, not reallocated. Not thread-safe; use one instance
 * per drawing thread.
 */
class BottleRenderer {
    private val outlinePath = Path()
    private val wavePath = Path()
    private val sparklePath = Path().apply {
        // Four-pointed star of radius 1 around the origin; scaled at draw time.
        moveTo(0f, -1f)
        quadTo(0.12f, -0.12f, 1f, 0f)
        quadTo(0.12f, 0.12f, 0f, 1f)
        quadTo(-0.12f, 0.12f, -1f, 0f)
        quadTo(-0.12f, -0.12f, 0f, -1f)
        close()
    }
    private val capRect = RectF()
    private val highlightRect = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val shaderMatrix = Matrix()
    private var waterShader: LinearGradient? = null
    private var shaderColor: WaterColor? = null

    // Geometry of the last size, in canvas pixels.
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var boxLeft = 0f
    private var boxTop = 0f
    private var boxWidth = 0f
    private var boxHeight = 0f
    private var bottleLeft = 0f
    private var bottleWidth = 0f
    private var stroke = 0f
    private var innerLeft = 0f
    private var innerTop = 0f
    private var innerWidth = 0f
    private var innerHeight = 0f

    /**
     * Draws the bottle centred into a [width] x [height] area at the origin of [canvas], scaled to
     * fit while keeping [ASPECT].
     *
     * @param fillFraction water level, clamped to 0..1 (0 = empty, 1 = filled into the neck).
     * @param wavePhase phase of the surface wave in radians; animate it for a moving wave.
     * @param goalReached cap and outline switch to gold and sparkles appear next to the bottle.
     * @param dark selects the dark variant of [PegelColors].
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        fillFraction: Float,
        wavePhase: Float,
        waterColor: WaterColor,
        goalReached: Boolean,
        dark: Boolean,
    ) {
        if (width <= 0f || height <= 0f) return
        if (width != lastWidth || height != lastHeight) layout(width, height)
        val palette = PegelColors.of(dark)
        val fraction = if (fillFraction.isNaN()) 0f else fillFraction.coerceIn(0f, 1f)
        val accent = if (goalReached) PegelColors.GOLD else palette.outline

        // Glass, water and reflection, clipped to the inside of the bottle.
        canvas.withClip(outlinePath) {
            fillPaint.color = palette.glassTint
            drawPath(outlinePath, fillPaint)
            if (fraction > 0f) drawWater(this, fraction, wavePhase, waterColor)
            fillPaint.color = palette.highlight
            val radius = highlightRect.width() / 2f
            drawRoundRect(highlightRect, radius, radius, fillPaint)
        }

        strokePaint.color = accent
        strokePaint.strokeWidth = stroke
        canvas.drawPath(outlinePath, strokePaint)

        fillPaint.color = accent
        val capRadius = capRect.height() * 0.35f
        canvas.drawRoundRect(capRect, capRadius, capRadius, fillPaint)
        if (goalReached) {
            strokePaint.color = PegelColors.GOLD_DEEP
            strokePaint.strokeWidth = stroke * 0.5f
            canvas.drawRoundRect(capRect, capRadius, capRadius, strokePaint)
            drawSparkles(canvas)
        }
    }

    private fun drawWater(canvas: Canvas, fraction: Float, wavePhase: Float, waterColor: WaterColor) {
        val bottom = y(1f)
        val full = y(FULL_LEVEL)
        val level = bottom - fraction * (bottom - full)
        // The wave flattens out near empty and near full, so 0 % and 100 % look calm.
        val taper = min(1f, fraction / WAVE_TAPER) * min(1f, (1f - fraction) / WAVE_TAPER)
        val amplitude = boxHeight * WAVE_AMPLITUDE * max(0f, taper)
        val left = innerLeft - stroke
        val right = innerLeft + innerWidth + stroke
        val span = right - left

        wavePath.reset()
        for (i in 0..WAVE_SEGMENTS) {
            val t = i.toFloat() / WAVE_SEGMENTS
            val x = left + span * t
            val y = level + amplitude * sin(wavePhase + t * WAVE_CYCLES * 2f * PI.toFloat())
            if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
        }
        wavePath.lineTo(right, bottom + stroke)
        wavePath.lineTo(left, bottom + stroke)
        wavePath.close()

        val top = level - amplitude
        shaderMatrix.setScale(1f, max(1f, bottom - top))
        shaderMatrix.postTranslate(0f, top)
        val shader = shaderFor(waterColor)
        shader.setLocalMatrix(shaderMatrix)
        waterPaint.shader = shader
        canvas.drawPath(wavePath, waterPaint)
    }

    private fun shaderFor(waterColor: WaterColor): LinearGradient {
        val cached = waterShader
        if (cached != null && shaderColor == waterColor) return cached
        // Unit gradient (0..1 vertically); positioned per frame through the local matrix.
        return LinearGradient(0f, 0f, 0f, 1f, waterColor.light, waterColor.deep, Shader.TileMode.CLAMP).also {
            waterShader = it
            shaderColor = waterColor
        }
    }

    private fun drawSparkles(canvas: Canvas) {
        fillPaint.color = PegelColors.GOLD
        for (i in SPARKLES.indices step 3) {
            val cx = boxLeft + SPARKLES[i] * boxWidth
            val cy = boxTop + SPARKLES[i + 1] * boxHeight
            val r = SPARKLES[i + 2] * boxWidth
            canvas.withTranslation(cx, cy) {
                scale(r, r)
                drawPath(sparklePath, fillPaint)
            }
        }
    }

    private fun layout(width: Float, height: Float) {
        lastWidth = width
        lastHeight = height
        if (width / height > ASPECT) {
            boxHeight = height
            boxWidth = height * ASPECT
        } else {
            boxWidth = width
            boxHeight = width / ASPECT
        }
        boxLeft = (width - boxWidth) / 2f
        boxTop = (height - boxHeight) / 2f
        bottleWidth = boxWidth * BOTTLE_WIDTH_SHARE
        bottleLeft = boxLeft + (boxWidth - bottleWidth) / 2f
        stroke = max(MIN_STROKE_PX, bottleWidth * STROKE_SHARE)
        innerLeft = bottleLeft + stroke / 2f
        innerTop = boxTop + stroke / 2f
        innerWidth = bottleWidth - stroke
        innerHeight = boxHeight - stroke

        val corner = innerWidth * BOTTOM_CORNER
        val k = corner * 0.45f // cubic approximation of a quarter circle
        val bottom = y(1f)
        val leftX = x(0f)
        val rightX = x(1f)
        outlinePath.reset()
        outlinePath.moveTo(x(NECK_LEFT), y(CAP_BOTTOM * 0.8f))
        outlinePath.lineTo(x(NECK_LEFT), y(NECK_BOTTOM))
        outlinePath.cubicTo(x(SHOULDER_CTRL_X), y(SHOULDER_CTRL1_Y), leftX, y(SHOULDER_CTRL_Y), leftX, y(SHOULDER_BOTTOM))
        outlinePath.lineTo(leftX, bottom - corner)
        outlinePath.cubicTo(leftX, bottom - k, leftX + k, bottom, leftX + corner, bottom)
        outlinePath.lineTo(rightX - corner, bottom)
        outlinePath.cubicTo(rightX - k, bottom, rightX, bottom - k, rightX, bottom - corner)
        outlinePath.lineTo(rightX, y(SHOULDER_BOTTOM))
        outlinePath.cubicTo(rightX, y(SHOULDER_CTRL_Y), x(1f - SHOULDER_CTRL_X), y(SHOULDER_CTRL1_Y), x(1f - NECK_LEFT), y(NECK_BOTTOM))
        outlinePath.lineTo(x(1f - NECK_LEFT), y(CAP_BOTTOM * 0.8f))
        outlinePath.close()

        capRect.set(x(CAP_LEFT), boxTop + stroke * 0.25f, x(1f - CAP_LEFT), y(CAP_BOTTOM))
        highlightRect.set(x(HIGHLIGHT_LEFT), y(HIGHLIGHT_TOP), x(HIGHLIGHT_RIGHT), y(HIGHLIGHT_BOTTOM))
    }

    private fun x(fraction: Float): Float = innerLeft + fraction * innerWidth

    private fun y(fraction: Float): Float = innerTop + fraction * innerHeight

    companion object {
        /** Width / height of the drawing including the sparkle margins left and right. */
        const val ASPECT = 0.78f

        private const val BOTTLE_WIDTH_SHARE = 0.72f
        private const val STROKE_SHARE = 0.03f
        private const val MIN_STROKE_PX = 1.5f

        // Silhouette, as fractions of the inner bottle width (x) and height (y).
        private const val CAP_LEFT = 0.27f
        private const val CAP_BOTTOM = 0.095f
        private const val NECK_LEFT = 0.33f
        private const val NECK_BOTTOM = 0.165f
        private const val SHOULDER_CTRL_X = 0.12f
        private const val SHOULDER_CTRL1_Y = 0.185f
        private const val SHOULDER_CTRL_Y = 0.25f
        private const val SHOULDER_BOTTOM = 0.37f
        private const val BOTTOM_CORNER = 0.14f
        private const val HIGHLIGHT_LEFT = 0.10f
        private const val HIGHLIGHT_RIGHT = 0.18f
        private const val HIGHLIGHT_TOP = 0.40f
        private const val HIGHLIGHT_BOTTOM = 0.86f

        /** Level at fillFraction 1: into the neck, just below the cap. */
        private const val FULL_LEVEL = 0.125f

        private const val WAVE_AMPLITUDE = 0.025f
        private const val WAVE_TAPER = 0.08f
        private const val WAVE_CYCLES = 1.4f
        private const val WAVE_SEGMENTS = 32

        /** Sparkles beside the bottle: x and y as fractions of the drawing box, radius of its width. */
        private val SPARKLES = floatArrayOf(
            0.075f, 0.30f, 0.065f,
            0.10f, 0.16f, 0.035f,
            0.925f, 0.22f, 0.05f,
            0.92f, 0.52f, 0.07f,
            0.07f, 0.70f, 0.045f,
        )
    }
}
