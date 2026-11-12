package app.pegelstand.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import app.pegelstand.LOG_TAG
import app.pegelstand.data.ReminderScheduler
import app.pegelstand.domain.AlarmTiming
import app.pegelstand.domain.ReminderState
import app.pegelstand.domain.nextWakeUp
import java.time.Instant

/**
 * Keeps exactly one inexact alarm ([AlarmManager.setAndAllowWhileIdle], no exact-alarm
 * permission) at [nextWakeUp]. The same PendingIntent is reused, so every call replaces the
 * previous alarm. The alarm runs on the elapsed-realtime clock; see [AlarmTiming].
 */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext

    override fun schedule(state: ReminderState, now: Instant) {
        try {
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
            val wakeUp = nextWakeUp(state, now)
            val triggerAt = AlarmTiming.elapsedTriggerAt(SystemClock.elapsedRealtime(), wakeUp, now)
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent(appContext))
            Log.i(LOG_TAG, "Next wake-up at $wakeUp (in ${AlarmTiming.delayMs(wakeUp, now) / 1000} s)")
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Scheduling the reminder alarm failed", e)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}
