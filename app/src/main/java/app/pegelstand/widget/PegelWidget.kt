package app.pegelstand.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pegelstand.MainActivity
import app.pegelstand.R
import app.pegelstand.appContainer
import app.pegelstand.data.TodayState
import app.pegelstand.domain.AmountFormat
import app.pegelstand.ui.bottle.BottleRenderer
import app.pegelstand.ui.bottle.PegelColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The home-screen widget: the bottle, the amount line, a streak chip and an undo control.
 * Tap anywhere = one intake; tap the amount = open the app; tap undo = remove the last intake.
 *
 * The state is collected from [app.pegelstand.data.WaterRepository.today] while the Glance
 * session runs, so changes from the app or the notification show up without a manual refresh;
 * [GlanceWidgetRefresher] restarts the session when none is running.
 */
class PegelWidget : GlanceAppWidget() {

    // Exact: the bottle bitmap is rendered for the widget's actual size.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.appContainer.repository
        val initial = repository.currentToday()
        provideContent {
            val today by repository.today.collectAsState(initial)
            WidgetContent(today)
        }
    }
}

@Composable
private fun WidgetContent(today: TodayState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val amountText = AmountFormat.totalOfGoal(today.totalMl, today.goalMl)
    val layout = WidgetLayout.compute(size.width.value, size.height.value, BottleRenderer.ASPECT, amountText)
    val fontScale = context.resources.configuration.fontScale

    val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier
            .background(dayNight { it.surface })
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(ImageProvider(R.drawable.widget_background))
    }
    val addLabel = context.getString(R.string.widget_add_description, today.mlPerTap)
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .then(background)
        .clickable(actionRunCallback<AddIntakeAction>())
        .semantics { contentDescription = addLabel }
        .padding(layout.padding.dp)

    Box(modifier = root) {
        if (layout.sideBySide) SideBySide(today, layout, amountText, fontScale) else Stacked(today, layout, amountText, fontScale)
        if (!layout.sideBySide && today.streak.current >= 1) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(modifier = GlanceModifier.height(layout.controlSize.dp), contentAlignment = Alignment.CenterStart) {
                    StreakChip(today.streak.current, layout.chipFontSize, fontScale)
                }
            }
        }
        if (today.hasIntakeToday) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                UndoButton(layout.controlSize)
            }
        }
    }
}

@Composable
private fun Stacked(today: TodayState, layout: WidgetLayout, amountText: String, fontScale: Float) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (layout.controlRowHeight > 0f) Spacer(modifier = GlanceModifier.height(layout.controlRowHeight.dp))
        Box(
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Bottle(today, layout.bottleWidth, layout.bottleHeight)
        }
        AmountText(today, amountText, layout, fontScale)
    }
}

@Composable
private fun SideBySide(today: TodayState, layout: WidgetLayout, amountText: String, fontScale: Float) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = GlanceModifier.width(6.dp))
        Bottle(today, layout.bottleWidth, layout.bottleHeight)
        Spacer(modifier = GlanceModifier.width(6.dp))
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmountText(today, amountText, layout, fontScale)
            if (today.streak.current >= 1) StreakChip(today.streak.current, layout.chipFontSize, fontScale)
        }
    }
}

@Composable
private fun Bottle(today: TodayState, widthDp: Float, heightDp: Float) {
    val context = LocalContext.current
    val fill = if (today.goalMl > 0) today.totalMl.toFloat() / today.goalMl else 0f
    val percent = (fill.coerceIn(0f, 1f) * 100).roundToInt()
    val description = context.getString(R.string.widget_bottle_description, percent)
    val views = remember(today.totalMl, today.goalMl, today.waterColor, widthDp, heightDp) {
        bottleViews(context, today, fill, widthDp, heightDp, description)
    }
    AndroidRemoteViews(
        remoteViews = views,
        modifier = GlanceModifier.size(widthDp.dp, heightDp.dp),
    )
}

/**
 * The bottle as an ImageView. On Android 12+ it carries a light and a dark bitmap and the launcher
 * picks one by its current night mode, so a theme switch needs no re-render. Before Android 12 the
 * launcher does not support that; the bitmap matches the mode at render time.
 */
private fun bottleViews(
    context: Context,
    today: TodayState,
    fill: Float,
    widthDp: Float,
    heightDp: Float,
    description: String,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_bottle)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val light = renderBottle(context, today, fill, widthDp, heightDp, dark = false)
        val dark = renderBottle(context, today, fill, widthDp, heightDp, dark = true)
        views.setIcon(R.id.widget_bottle, "setImageIcon", Icon.createWithBitmap(light), Icon.createWithBitmap(dark))
    } else {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        views.setImageViewBitmap(R.id.widget_bottle, renderBottle(context, today, fill, widthDp, heightDp, night))
    }
    views.setContentDescription(R.id.widget_bottle, description)
    return views
}

private fun renderBottle(
    context: Context,
    today: TodayState,
    fill: Float,
    widthDp: Float,
    heightDp: Float,
    dark: Boolean,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthPx = widthDp * density
    val heightPx = heightDp * density
    val scale = min(1f, MAX_BITMAP_PX / max(widthPx, heightPx))
    val bitmap = createBitmap(max(1, (widthPx * scale).roundToInt()), max(1, (heightPx * scale).roundToInt()))
    BottleRenderer().draw(
        canvas = Canvas(bitmap),
        width = bitmap.width.toFloat(),
        height = bitmap.height.toFloat(),
        fillFraction = fill,
        wavePhase = WIDGET_WAVE_PHASE,
        waterColor = today.waterColor,
        goalReached = today.goalReached,
        dark = dark,
    )
    return bitmap
}

@Composable
private fun AmountText(today: TodayState, amountText: String, layout: WidgetLayout, fontScale: Float) {
    val color = if (today.goalReached) dayNight { it.goldText } else dayNight { it.onSurface }
    Box(
        modifier = GlanceModifier
            .height(layout.amountRowHeight.dp)
            .padding(horizontal = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = amountText,
            maxLines = 1,
            style = TextStyle(
                color = color,
                fontSize = (layout.amountFontSize / max(fontScale, 1f)).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun StreakChip(streak: Int, fontSizeDp: Float, fontScale: Float) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_chip))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🔥 $streak",
            maxLines = 1,
            style = TextStyle(
                color = dayNight { it.onChip },
                fontSize = (fontSizeDp / max(fontScale, 1f)).sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun UndoButton(sizeDp: Float) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .clickable(actionRunCallback<UndoAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_undo),
            contentDescription = context.getString(R.string.widget_undo_description),
            colorFilter = ColorFilter.tint(dayNight { it.onSurface }),
            modifier = GlanceModifier.size((sizeDp * 0.5f).dp),
        )
    }
}

private inline fun dayNight(pick: (PegelColors.Palette) -> Int): ColorProvider =
    ColorProvider(day = Color(pick(PegelColors.Light)), night = Color(pick(PegelColors.Dark)))

/** Longest bitmap side, so a large widget does not push a huge bitmap through the launcher. */
private const val MAX_BITMAP_PX = 600f

/** A fixed wave phase: the widget shows the wave at rest, one step per tap, no animation. */
private const val WIDGET_WAVE_PHASE = 0.9f
