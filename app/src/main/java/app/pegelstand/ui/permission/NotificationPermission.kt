package app.pegelstand.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pegelstand.LOG_TAG

/**
 * The POST_NOTIFICATIONS permission (Android 13+) as Compose state. Never requested on its own:
 * only [request], called from a button the user tapped, asks. Re-checked on every resume, so a
 * grant in the system settings shows up on return.
 */
@Stable
class NotificationPermissionState internal constructor(
    private val context: Context,
    private val activity: Activity?,
) {
    internal var launcher: ActivityResultLauncher<String>? = null

    var granted: Boolean by mutableStateOf(isGranted(context))
        private set

    /** What [request] will do; drives the button label. */
    var action: PermissionAction by mutableStateOf(currentAction())
        private set

    /** Shows the system dialog, or opens the app's notification settings if it was denied permanently. */
    fun request() {
        when (currentAction()) {
            PermissionAction.NONE -> Unit
            PermissionAction.REQUEST -> {
                prefs(context).edit { putBoolean(KEY_ASKED, true) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionAction.OPEN_SETTINGS -> openNotificationSettings()
        }
    }

    internal fun refresh() {
        granted = isGranted(context)
        action = currentAction()
    }

    private fun currentAction(): PermissionAction = NotificationPermissionRule.action(
        granted = isGranted(context),
        askedBefore = prefs(context).getBoolean(KEY_ASKED, false),
        shouldShowRationale = shouldShowRationale(),
    )

    private fun shouldShowRationale(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            (activity ?: context).startActivity(if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Opening notification settings failed", e)
        }
    }

    private companion object {
        const val PREFS = "ui_state"
        const val KEY_ASKED = "notificationPermissionAsked"

        fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun isGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val current = remember(context, activity) { NotificationPermissionState(context, activity) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        current.refresh()
    }
    SideEffect { current.launcher = launcher }
    LifecycleResumeEffect(current) {
        current.refresh()
        onPauseOrDispose { }
    }
    return current
}
