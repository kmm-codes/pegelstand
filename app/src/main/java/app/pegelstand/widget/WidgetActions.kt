package app.pegelstand.widget

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import app.pegelstand.LOG_TAG
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import app.pegelstand.appContainer
import kotlinx.coroutines.CancellationException

class PegelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PegelWidget()
}

/** Tap anywhere on the widget: one intake, with a vibration tick as confirmation. */
class AddIntakeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetHaptics.tick(context)
        guarded("Intake from widget") { context.appContainer.repository.addIntake() }
    }
}

/** Tap on the undo control: removes the last intake of today. */
class UndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetHaptics.tick(context)
        guarded("Undo from widget") { context.appContainer.repository.undoLast() }
    }
}

private suspend fun guarded(what: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(LOG_TAG, "$what failed", e)
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
