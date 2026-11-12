package app.pegelstand.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import app.pegelstand.LOG_TAG
import app.pegelstand.appContainer
import app.pegelstand.reminder.runAsync

/**
 * The home-screen widget's provider. Placed widgets are bound to this class name, so it must not
 * be renamed. Placement, resize and the launcher's update requests get a static update; level
 * changes come from [WidgetAnimator] after a write.
 */
class PegelWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context, "Widget update") { context.appContainer.widgetAnimator.refreshStatic(appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        runAsync(context, "Widget resize") { context.appContainer.widgetAnimator.refreshStatic(intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        context.appContainer.widgetAnimator.forget(appWidgetIds)
    }
}

/**
 * Taps on the widget: [ACTION_ADD] (anywhere) adds one intake, [ACTION_UNDO] removes the last one,
 * each with a vibration tick. Not exported: only the widget's own pending intents reach it.
 *
 * The broadcast finishes once the write and the animation's start update are done; the closing
 * update follows on the app scope. Holding the broadcast open until the closing update would
 * queue the next tap behind it (manifest receivers get one broadcast at a time), so rapid taps
 * would play one after another, seconds late, and their ticks too.
 */
class WidgetTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = context.appContainer.repository
        when (intent.action) {
            ACTION_ADD -> {
                WidgetHaptics.tick(context)
                runAsync(context, "Intake from widget") { repository.addIntake() }
            }
            ACTION_UNDO -> {
                WidgetHaptics.tick(context)
                runAsync(context, "Undo from widget") { repository.undoLast() }
            }
        }
    }

    companion object {
        const val ACTION_ADD = "app.pegelstand.widget.ADD"
        const val ACTION_UNDO = "app.pegelstand.widget.UNDO"
    }
}

internal object WidgetHaptics {
    /**
     * A short tick. The widget's receiver runs as a background process, where Android 12+ drops
     * touch-usage vibrations; hardware-feedback usage (a tactile button press) is allowed there.
     */
    fun tick(context: Context) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            } else {
                vibrator.vibrate(effect)
            }
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Vibration failed", e)
        }
    }

    private const val TICK_MS = 15L
}
