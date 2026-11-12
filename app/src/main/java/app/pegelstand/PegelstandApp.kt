package app.pegelstand

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Log tag of the whole app. */
internal const val LOG_TAG = "Pegelstand"

class PegelstandApp : Application() {
    lateinit var container: AppContainer
        private set

    /** The night-mode bits of the configuration the widgets were last drawn for. */
    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        // A widget update on every process start: if the process died between an animation's
        // start and closing update, the widget's flipper would keep looping until the next update.
        // Same as any refresh: static unless the level changed meanwhile. Never throws.
        container.applicationScope.launch { container.widgetAnimator.refreshAll() }
        // Arms the reminder alarm on every process start, so a fresh install has one even before
        // the first intake. Replacing the single alarm is idempotent.
        container.applicationScope.launch {
            try {
                container.repository.rescheduleReminders()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Initial reschedule failed", e)
            }
        }
    }

    /**
     * A theme switch while the process lives: the widgets' idle wave was drawn for the other mode
     * and is hidden by the launcher now, so redraw it for the new mode at once.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night == nightMode) return
        nightMode = night
        Log.d(LOG_TAG, "Night mode changed ($night), refreshing widgets")
        container.applicationScope.launch { container.widgetAnimator.refreshAll() }
    }
}

/** The [AppContainer] from any context of this app (activity, receiver, widget). */
val Context.appContainer: AppContainer
    get() = (applicationContext as PegelstandApp).container
