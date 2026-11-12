package app.pegelstand.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.os.BundleCompat
import app.pegelstand.MainActivity
import app.pegelstand.R
import app.pegelstand.data.TodayState
import app.pegelstand.ui.bottle.BottleRenderer
import app.pegelstand.ui.bottle.PegelColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A widget size in dp, as the launcher reports it. */
internal data class SizeDp(val width: Float, val height: Float)

/**
 * The idle wave of one static update: [bitmaps], one per flipper child (one per distinct frame),
 * shown [intervalMs] apart, drawn for night mode if [dark].
 */
internal class IdleFrames(val bitmaps: List<Bitmap>, val intervalMs: Int, val dark: Boolean)

/** Bottle bitmap size in pixels. */
internal data class PixelSize(val width: Int, val height: Int) {
    val bytes: Long get() = width.toLong() * height * 4
}

/**
 * Builds the widget's RemoteViews: one entry per reported widget size (a size map on Android 12+,
 * a portrait/landscape pair before), each from the layout of the [WidgetShape] that
 * [WidgetLayout] picks for that size. Every update is a full one; nothing here sends anything.
 *
 * Before Android 12 view sizes cannot be set from code: the shape, texts, font sizes, padding and
 * visibility still follow the reported size, while the views take their XML sizes (bottle in the
 * space the other views leave, mini label a little below the bottle's centre).
 */
internal class WidgetViews(context: Context) {
    private val context: Context = context.applicationContext

    /**
     * A static state: the static bottle image (both modes on Android 12+) and, on top, the idle
     * wave's looping flipper if [idle] is given. Replaces any level animation's flipper (removing
     * it stops it). Every size entry shares the same idle bitmaps.
     */
    fun still(id: Int, state: TodayState, sizes: List<SizeDp>, idle: IdleFrames?): RemoteViews =
        combine(sizes) { size -> entry(id, state, size, Bottle.Still(idle)) }

    /** Bytes of the static images [still] renders for [sizes] (a light and a dark one on Android 12+). */
    fun stillBytes(state: TodayState, sizes: List<SizeDp>): Long {
        val images = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 2 else 1
        return sizes.sumOf { bottlePixels(layout(state, it)).bytes * images }
    }

    /**
     * The animation's start: the new texts and controls, the static image hidden, and a fresh
     * flipper whose children show [frames] (one bitmap per flipper child; repeated instances are
     * stored once). Every size entry shares the same bitmaps.
     */
    fun animated(id: Int, state: TodayState, sizes: List<SizeDp>, frames: List<Bitmap>): RemoteViews {
        require(frames.size == FRAME_IDS.size) { "one bitmap per flipper child" }
        return combine(sizes) { size -> entry(id, state, size, Bottle.Frames(frames)) }
    }

    /** The layout of one size: shared by the RemoteViews and by the bitmap size. */
    fun layout(state: TodayState, size: SizeDp): WidgetLayout =
        WidgetLayout.compute(size.width, size.height, BottleRenderer.ASPECT, texts(state))

    /** Pixel size of the bottle bitmap for [layout]; the longest side is capped at [MAX_BITMAP_PX]. */
    fun bottlePixels(layout: WidgetLayout): PixelSize {
        val density = context.resources.displayMetrics.density
        val widthPx = layout.bottleWidth * density
        val heightPx = layout.bottleHeight * density
        val scale = min(1f, MAX_BITMAP_PX / max(1f, max(widthPx, heightPx)))
        return PixelSize(max(1, (widthPx * scale).roundToInt()), max(1, (heightPx * scale).roundToInt()))
    }

    /** One bottle image at wave phase [phase]. The same arguments always give the same pixels. */
    fun render(
        renderer: BottleRenderer,
        size: PixelSize,
        state: TodayState,
        look: BottleLook,
        dark: Boolean,
        phase: Float = WidgetIdle.STILL_PHASE,
    ): Bitmap {
        val bitmap = createBitmap(size.width, size.height)
        renderer.draw(
            canvas = Canvas(bitmap),
            width = size.width.toFloat(),
            height = size.height.toFloat(),
            fillFraction = look.fill,
            wavePhase = phase,
            waterColor = state.waterColor,
            goldMix = look.gold,
            dark = dark,
        )
        return bitmap
    }

    fun isNight(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private sealed interface Bottle {
        class Still(val idle: IdleFrames?) : Bottle

        class Frames(val bitmaps: List<Bitmap>) : Bottle
    }

    private fun combine(sizes: List<SizeDp>, entry: (SizeDp) -> RemoteViews): RemoteViews {
        if (sizes.size == 1) return entry(sizes[0])
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(sizes.associate { SizeF(it.width, it.height) to entry(it) })
        }
        // Before Android 12 the launcher reports a portrait and a landscape size (see sizesOf).
        return RemoteViews(entry(sizes[1]), entry(sizes[0]))
    }

    private fun entry(id: Int, state: TodayState, size: SizeDp, bottle: Bottle): RemoteViews {
        val layout = layout(state, size)
        val texts = texts(state)
        val shape = layout.shape
        val views = RemoteViews(context.packageName, layoutOf(shape))
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val density = context.resources.displayMetrics.density
        val fontScale = max(context.resources.configuration.fontScale, 1f)
        val dip = TypedValue.COMPLEX_UNIT_DIP
        fun textSize(viewId: Int, dp: Float) = views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, dp / fontScale)

        // Tap zones: only the bottle's box (the frames container: always visible, the bottle
        // image's size, flippers inside it take no taps) adds an intake; the root, that is every
        // other spot out to the widget's edges, opens the app; chip box and undo lie on top. The
        // mini label lies on top too but takes no taps, so a tap on it drinks.
        val padding = (layout.padding * density).roundToInt()
        views.setViewPadding(R.id.widget_content, padding, padding, padding, padding)
        if (modern) views.setBoolean(ROOT, "setClipToOutline", true)
        views.setOnClickPendingIntent(ROOT, openAppIntent(id))
        views.setContentDescription(ROOT, context.getString(R.string.widget_open_description))
        views.setOnClickPendingIntent(R.id.widget_frames, tapIntent(WidgetTapReceiver.ACTION_ADD, id))
        views.setContentDescription(R.id.widget_frames, context.getString(R.string.widget_add_description, state.mlPerTap))

        // Amount: "drunk / goal" in one line; slim: the drunk amount (the goal line follows below);
        // mini: the drunk amount with unit, on the label. Taps on it fall through.
        val amount = when (shape) {
            WidgetShape.SLIM -> texts.drunk
            WidgetShape.MINI -> texts.label
            WidgetShape.STACKED, WidgetShape.SIDE -> texts.line
        }
        views.setTextViewText(R.id.widget_amount, amount)
        views.setViewVisibility(R.id.widget_amount, if (layout.amountShown) View.VISIBLE else View.GONE)
        if (layout.amountShown) textSize(R.id.widget_amount, layout.amountFontSize)
        if (modern) {
            val color = if (state.goalReached) R.color.widget_gold_text else R.color.widget_on_surface
            views.setColorStateList(R.id.widget_amount, "setTextColor", color)
            if (shape != WidgetShape.MINI) views.setViewLayoutHeight(R.id.widget_amount, layout.amountRowHeight, dip)
        } else {
            val palette = PegelColors.of(isNight())
            views.setTextColor(R.id.widget_amount, if (state.goalReached) palette.goldText else palette.onSurface)
        }

        when (shape) {
            WidgetShape.STACKED, WidgetShape.SIDE -> {
                // Streak chip, only from a streak of one day and where it fits.
                val stacked = shape == WidgetShape.STACKED
                views.setViewVisibility(if (stacked) R.id.widget_chip_box else R.id.widget_chip, if (layout.chipFits) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_chip, texts.chip.orEmpty())
                textSize(R.id.widget_chip, layout.chipFontSize)
                // Side by side the chip lies in the text column (root: opens the app). Stacked its
                // touch box opens the app itself, since it may overlap the bottle box.
                if (stacked) {
                    views.setOnClickPendingIntent(R.id.widget_chip_box, openAppIntent(id))
                    views.setViewVisibility(R.id.widget_control_row, if (layout.controlRowHeight > 0f) View.VISIBLE else View.GONE)
                    if (modern) {
                        views.setViewLayoutHeight(R.id.widget_chip_box, layout.controlSize, dip)
                        if (layout.controlRowHeight > 0f) views.setViewLayoutHeight(R.id.widget_control_row, layout.controlRowHeight, dip)
                    }
                } else if (modern) {
                    // Exact widths on both sides of the row, so the XML weights have nothing left to share.
                    views.setViewLayoutWidth(R.id.widget_text_column, layout.textColumnWidth, dip)
                }
            }
            WidgetShape.SLIM -> {
                views.setTextViewText(R.id.widget_amount_goal, texts.goal)
                textSize(R.id.widget_amount_goal, layout.goalFontSize)
                if (modern) views.setViewLayoutHeight(R.id.widget_amount_goal, layout.goalRowHeight, dip)
                // The two lines are read once, as the one-line amount.
                views.setContentDescription(R.id.widget_amount_box, texts.line)
            }
            WidgetShape.MINI -> {
                if (modern) views.setViewLayoutMargin(R.id.widget_amount, RemoteViews.MARGIN_TOP, layout.labelOffset, dip)
            }
        }

        // Undo, only when today has an intake and where it fits. Slim keeps its row while it is
        // hidden, so the bottle does not move with the first intake of the day.
        if (shape != WidgetShape.MINI) {
            val hidden = if (shape == WidgetShape.SLIM) View.INVISIBLE else View.GONE
            views.setViewVisibility(R.id.widget_undo, if (layout.undoFits && state.hasIntakeToday) View.VISIBLE else hidden)
            views.setOnClickPendingIntent(R.id.widget_undo, tapIntent(WidgetTapReceiver.ACTION_UNDO, id))
            if (modern) {
                views.setViewLayoutWidth(R.id.widget_undo, layout.controlSize, dip)
                views.setViewLayoutHeight(R.id.widget_undo, layout.controlSize, dip)
                views.setViewLayoutWidth(R.id.widget_undo_icon, layout.controlSize * 0.5f, dip)
                views.setViewLayoutHeight(R.id.widget_undo_icon, layout.controlSize * 0.5f, dip)
            }
        }

        if (modern) {
            // Stacked and slim: image and flipper container sized alike; side and mini: their common box.
            val bottleViews = when (shape) {
                WidgetShape.STACKED, WidgetShape.SLIM -> intArrayOf(R.id.widget_bottle, R.id.widget_frames)
                WidgetShape.SIDE, WidgetShape.MINI -> intArrayOf(R.id.widget_bottle_box)
            }
            for (viewId in bottleViews) {
                views.setViewLayoutWidth(viewId, layout.bottleWidth, dip)
                views.setViewLayoutHeight(viewId, layout.bottleHeight, dip)
            }
        }

        val percent = (state.shownFill * 100).roundToInt()
        val bottleDescription = context.getString(R.string.widget_bottle_description, percent)
        // A mini widget too small for its label still tells the amount, once.
        views.setContentDescription(R.id.widget_bottle, if (layout.amountShown) bottleDescription else "$bottleDescription, ${texts.line}")
        // Both cases replace the flipper container's content: a static update detaches (and so
        // stops) any level animation; each update inflates a fresh flipper that starts at child 0.
        views.removeAllViews(R.id.widget_frames)
        when (bottle) {
            is Bottle.Still -> {
                setStillImage(views, state, layout)
                views.setViewVisibility(R.id.widget_bottle, View.VISIBLE)
                bottle.idle?.let { views.addView(R.id.widget_frames, idleFlipper(it)) }
            }
            is Bottle.Frames -> {
                views.setViewVisibility(R.id.widget_bottle, View.INVISIBLE)
                views.addView(R.id.widget_frames, flipper(bottle.bitmaps))
            }
        }
        return views
    }

    /**
     * On Android 12+ the image carries a light and a dark bitmap and the launcher picks one by its
     * night mode, so a theme switch needs no update. Before that the bitmap matches the mode now.
     */
    private fun setStillImage(views: RemoteViews, state: TodayState, layout: WidgetLayout) {
        val size = bottlePixels(layout)
        val look = BottleLook(state.shownFill, if (state.goalReached) 1f else 0f)
        val renderer = BottleRenderer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val light = render(renderer, size, state, look, dark = false)
            val dark = render(renderer, size, state, look, dark = true)
            views.setIcon(R.id.widget_bottle, "setImageIcon", Icon.createWithBitmap(light), Icon.createWithBitmap(dark))
        } else {
            views.setImageViewBitmap(R.id.widget_bottle, render(renderer, size, state, look, isNight()))
        }
    }

    private fun flipper(bitmaps: List<Bitmap>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_flipper)
        FRAME_IDS.forEachIndexed { child, viewId -> views.setImageViewBitmap(viewId, bitmaps[child]) }
        return views
    }

    /**
     * The idle loop: one child per frame, [IdleFrames.intervalMs] apart. Its layout's style shows it
     * only while the launcher's night mode matches the mode the frames were drawn for.
     */
    private fun idleFlipper(idle: IdleFrames): RemoteViews {
        val views = RemoteViews(context.packageName, if (idle.dark) R.layout.widget_idle_dark else R.layout.widget_idle_light)
        views.setInt(R.id.widget_idle, "setFlipInterval", idle.intervalMs)
        for (bitmap in idle.bitmaps) {
            val frame = RemoteViews(context.packageName, R.layout.widget_idle_frame)
            frame.setImageViewBitmap(R.id.widget_idle_frame, bitmap)
            views.addView(R.id.widget_idle, frame)
        }
        return views
    }

    private fun tapIntent(action: String, id: Int): PendingIntent {
        val intent = Intent(context, WidgetTapReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAppIntent(id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val ROOT = android.R.id.background

        /** Longest bitmap side, so a large widget does not push a huge bitmap through the launcher. */
        private const val MAX_BITMAP_PX = 600f

        private val DEFAULT_SIZE = SizeDp(110f, 110f)

        /** The flipper's children in `widget_flipper.xml`, in order. */
        val FRAME_IDS = intArrayOf(
            R.id.widget_frame_00, R.id.widget_frame_01, R.id.widget_frame_02, R.id.widget_frame_03,
            R.id.widget_frame_04, R.id.widget_frame_05, R.id.widget_frame_06, R.id.widget_frame_07,
            R.id.widget_frame_08, R.id.widget_frame_09, R.id.widget_frame_10, R.id.widget_frame_11,
            R.id.widget_frame_12, R.id.widget_frame_13, R.id.widget_frame_14, R.id.widget_frame_15,
            R.id.widget_frame_16, R.id.widget_frame_17, R.id.widget_frame_18, R.id.widget_frame_19,
            R.id.widget_frame_20, R.id.widget_frame_21, R.id.widget_frame_22, R.id.widget_frame_23,
            R.id.widget_frame_24, R.id.widget_frame_25, R.id.widget_frame_26, R.id.widget_frame_27,
            R.id.widget_frame_28, R.id.widget_frame_29, R.id.widget_frame_30, R.id.widget_frame_31,
            R.id.widget_frame_32, R.id.widget_frame_33, R.id.widget_frame_34, R.id.widget_frame_35,
            R.id.widget_frame_36, R.id.widget_frame_37, R.id.widget_frame_38, R.id.widget_frame_39,
            R.id.widget_frame_40, R.id.widget_frame_41, R.id.widget_frame_42, R.id.widget_frame_43,
            R.id.widget_frame_44, R.id.widget_frame_45, R.id.widget_frame_46, R.id.widget_frame_47,
        )

        fun texts(state: TodayState): WidgetTexts = WidgetTexts.of(state.totalMl, state.goalMl, state.streak.current)

        private fun layoutOf(shape: WidgetShape): Int = when (shape) {
            WidgetShape.STACKED -> R.layout.widget_stacked
            WidgetShape.SIDE -> R.layout.widget_side
            WidgetShape.SLIM -> R.layout.widget_slim
            WidgetShape.MINI -> R.layout.widget_mini
        }

        /**
         * The sizes the launcher reports for widget [id], in dp. Android 12+: its size list.
         * Before: portrait (min width x max height) first, then landscape (max width x min height).
         */
        fun sizesOf(manager: AppWidgetManager, id: Int): List<SizeDp> {
            val options = manager.getAppWidgetOptions(id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                if (!sizes.isNullOrEmpty()) return sizes.map { SizeDp(it.width, it.height) }.distinct()
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (minWidth <= 0 || maxHeight <= 0) return listOf(DEFAULT_SIZE)
            val portrait = SizeDp(minWidth.toFloat(), maxHeight.toFloat())
            val landscape = SizeDp(max(minWidth, maxWidth).toFloat(), max(1, minHeight).toFloat())
            return listOf(portrait, landscape).distinct()
        }

        /** The size the widget has right now: the tallest one in portrait, the widest in landscape. */
        fun currentSize(sizes: List<SizeDp>, landscape: Boolean): SizeDp =
            if (landscape) sizes.minBy { it.height / it.width } else sizes.maxBy { it.height / it.width }
    }
}
