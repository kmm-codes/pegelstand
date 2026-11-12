package app.pegelstand.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pegelstand.LOG_TAG
import app.pegelstand.appContainer

/** The single reminder alarm fired: day rollover refresh, maybe a reminder, next alarm. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context, "Reminder check") { context.appContainer.repository.runReminderCheck() }
    }
}

/** The notification's "+N ml" action: adds one tap without opening the app. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync(context, "Intake from notification") { context.appContainer.repository.addIntake() }
    }
}

/**
 * Re-arms the alarm after boot, app update, and time or time-zone change (alarms do not survive
 * a reboot or an update, and a time change moves the next check). The day may have changed as
 * well, so the widgets are refreshed too.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        Log.i(LOG_TAG, "Rescheduling after ${intent.action}")
        runAsync(context, "Reschedule after ${intent.action}") {
            val container = context.appContainer
            container.repository.rescheduleReminders()
            container.widgetRefresher.refreshAll()
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
