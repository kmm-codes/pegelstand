package app.pegelstand.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import app.pegelstand.LOG_TAG

/** For the app's "Widget zum Startbildschirm hinzufügen" card. */
object WidgetPinning {

    /** True if at least one Pegelstand widget is on a home screen. */
    fun isWidgetPlaced(context: Context): Boolean = try {
        AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context)).isNotEmpty()
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Querying placed widgets failed", e)
        false
    }

    /**
     * Asks the launcher to show its "add widget" dialog. Returns false if the launcher does not
     * support pinning (the user then has to add it from the widget picker). True only means the
     * request was handed to the launcher; the user can still cancel the dialog.
     */
    fun requestPin(context: Context): Boolean = try {
        val manager = AppWidgetManager.getInstance(context)
        manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(provider(context), null, null)
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Pin request failed", e)
        false
    }

    private fun provider(context: Context) = ComponentName(context, PegelWidgetReceiver::class.java)
}
