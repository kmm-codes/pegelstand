package app.pegelstand.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import app.pegelstand.LOG_TAG
import app.pegelstand.appContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Runs [block] after [BroadcastReceiver.onReceive] returned, on the app's long-lived scope, and
 * finishes the broadcast when it is done. Failures are logged, never rethrown.
 */
internal fun BroadcastReceiver.runAsync(context: Context, what: String, block: suspend () -> Unit) {
    val pending = goAsync()
    context.appContainer.applicationScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "$what failed", e)
        } finally {
            pending.finish()
        }
    }
}
