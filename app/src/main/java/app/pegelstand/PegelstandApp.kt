package app.pegelstand

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Log tag of the whole app. */
internal const val LOG_TAG = "Pegelstand"

class PegelstandApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
}

/** The [AppContainer] from any context of this app (activity, receiver, widget). */
val Context.appContainer: AppContainer
    get() = (applicationContext as PegelstandApp).container
