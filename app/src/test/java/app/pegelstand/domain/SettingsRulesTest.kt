package app.pegelstand.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {
    @Test
    fun `goal is clamped to 1000-5000 and snapped to 250`() {
        assertEquals(1000, SettingsRules.coerceGoal(0))
        assertEquals(5000, SettingsRules.coerceGoal(9000))
        assertEquals(2000, SettingsRules.coerceGoal(2000))
        assertEquals(2250, SettingsRules.coerceGoal(2130))
        assertEquals(2000, SettingsRules.coerceGoal(2120))
    }

    @Test
    fun `ml per tap is clamped to 50-1000 and snapped to 50`() {
        assertEquals(50, SettingsRules.coerceMlPerTap(-5))
        assertEquals(1000, SettingsRules.coerceMlPerTap(1200))
        assertEquals(250, SettingsRules.coerceMlPerTap(260))
        assertEquals(300, SettingsRules.coerceMlPerTap(275))
    }

    @Test
    fun `reminder window bounds`() {
        assertTrue(SettingsRules.isValidWindow(LocalTime.of(8, 0), LocalTime.of(22, 0)))
        assertTrue(SettingsRules.isValidWindow(LocalTime.of(4, 0), LocalTime.of(23, 59)))
        assertTrue(SettingsRules.isValidWindow(LocalTime.of(10, 0), LocalTime.of(12, 0)))
        assertFalse(SettingsRules.isValidWindow(LocalTime.of(3, 59), LocalTime.of(22, 0)))
        assertFalse(SettingsRules.isValidWindow(LocalTime.of(10, 0), LocalTime.of(11, 59)))
        assertFalse(SettingsRules.isValidWindow(LocalTime.of(22, 0), LocalTime.of(8, 0)))
    }

    @Test
    fun `defaults`() {
        val settings = Settings()
        assertEquals(2000, settings.dailyGoalMl)
        assertEquals(250, settings.mlPerTap)
        assertEquals(WaterColor.BLUE, settings.waterColor)
        assertTrue(settings.remindersEnabled)
        assertEquals(LocalTime.of(8, 0), settings.remindFrom)
        assertEquals(LocalTime.of(22, 0), settings.remindUntil)
    }
}
