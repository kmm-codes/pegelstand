package app.pegelstand.widget

import android.content.Context
import android.util.Log
import app.pegelstand.LOG_TAG
import androidx.glance.appwidget.updateAll
import app.pegelstand.data.WidgetRefresher
import kotlinx.coroutines.CancellationException

/** Updates every placed instance of [PegelWidget]. Never throws (failures are logged). */
class GlanceWidgetRefresher(context: Context) : WidgetRefresher {
    private val appContext = context.applicationContext

    override suspend fun refreshAll() {
        try {
            PegelWidget().updateAll(appContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Widget refresh failed", e)
        }
    }
}
