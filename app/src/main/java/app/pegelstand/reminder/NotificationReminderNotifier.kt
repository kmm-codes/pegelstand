package app.pegelstand.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.pegelstand.LOG_TAG
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pegelstand.MainActivity
import app.pegelstand.R
import app.pegelstand.data.ReminderNotifier

/**
 * The single reminder notification: channel "Trink-Erinnerungen", one id (replaced, never
 * stacked), an action that adds one tap through [ReminderActionReceiver], body tap opens the app.
 * Without the notification permission [show] does nothing.
 */
class NotificationReminderNotifier(context: Context) : ReminderNotifier {
    private val appContext = context.applicationContext

    override fun show(glassesBehind: Int, mlPerTap: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(LOG_TAG, "Reminder due, but the notification permission is missing")
                return
            }
            val notifications = NotificationManagerCompat.from(appContext)
            if (!notifications.areNotificationsEnabled()) return
            ensureChannel()
            val resources = appContext.resources
            val text = resources.getQuantityString(R.plurals.reminder_text, glassesBehind, glassesBehind)
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_drop)
                .setContentTitle(resources.getString(R.string.reminder_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .addAction(R.drawable.ic_stat_drop, resources.getString(R.string.add_intake, mlPerTap), addIntakeIntent())
                .build()
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Notification permission revoked meanwhile", e)
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Showing the reminder failed", e)
        }
    }

    override fun cancel() {
        try {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Cancelling the reminder failed", e)
        }
    }

    private fun ensureChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = appContext.getString(R.string.reminder_channel_description) }
        manager.createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun addIntakeIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, ReminderActionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_ID = 1
    }
}
