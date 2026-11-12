package app.pegelstand.ui.permission

/** What the "allow reminders" button does. */
enum class PermissionAction {
    /** Nothing to do: the permission is granted (or not needed on this Android version). */
    NONE,

    /** Show the system permission dialog. */
    REQUEST,

    /** Denied permanently: the dialog would not appear any more, open the app's notification settings. */
    OPEN_SETTINGS,
}

object NotificationPermissionRule {
    /**
     * Android shows no dialog after the user denied the permission twice (or chose "don't ask
     * again"). That state is recognised by: asked before, and the system no longer wants a
     * rationale. Before the first request the rationale flag is false as well, hence [askedBefore].
     */
    fun action(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): PermissionAction = when {
        granted -> PermissionAction.NONE
        askedBefore && !shouldShowRationale -> PermissionAction.OPEN_SETTINGS
        else -> PermissionAction.REQUEST
    }

    /** The card on the main screen shows while reminders are on and the permission is missing. */
    fun showCard(remindersEnabled: Boolean, granted: Boolean): Boolean = remindersEnabled && !granted
}
