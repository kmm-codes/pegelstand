package app.pegelstand.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionRuleTest {
    @Test
    fun `granted needs nothing`() {
        assertEquals(PermissionAction.NONE, NotificationPermissionRule.action(granted = true, askedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun `first time asks with the system dialog`() {
        assertEquals(PermissionAction.REQUEST, NotificationPermissionRule.action(granted = false, askedBefore = false, shouldShowRationale = false))
    }

    @Test
    fun `denied once still asks`() {
        assertEquals(PermissionAction.REQUEST, NotificationPermissionRule.action(granted = false, askedBefore = true, shouldShowRationale = true))
    }

    @Test
    fun `denied permanently opens the settings`() {
        assertEquals(PermissionAction.OPEN_SETTINGS, NotificationPermissionRule.action(granted = false, askedBefore = true, shouldShowRationale = false))
    }

    @Test
    fun `card only while reminders are on and the permission is missing`() {
        assertTrue(NotificationPermissionRule.showCard(remindersEnabled = true, granted = false))
        assertFalse(NotificationPermissionRule.showCard(remindersEnabled = true, granted = true))
        assertFalse(NotificationPermissionRule.showCard(remindersEnabled = false, granted = false))
    }
}
