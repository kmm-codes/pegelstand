package app.pegelstand.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.Settings
import app.pegelstand.domain.WaterColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mapping of stored DataStore preferences to settings; bad stored data must never crash. */
class SettingsMappingTest {
    private val waterColor = stringPreferencesKey("waterColor")
    private val bottleMode = stringPreferencesKey("bottleMode")

    @Test
    fun `empty store gives the defaults`() {
        assertEquals(Settings(), SettingsStore.toSettings(emptyPreferences()))
    }

    @Test
    fun `stored colour id is read back`() {
        assertEquals(WaterColor.CORAL, SettingsStore.toSettings(preferencesOf(waterColor to "CORAL")).waterColor)
    }

    @Test
    fun `unknown colour id falls back to blue`() {
        assertEquals(WaterColor.BLUE, SettingsStore.toSettings(preferencesOf(waterColor to "GOLD")).waterColor)
        assertEquals(WaterColor.BLUE, SettingsStore.toSettings(preferencesOf(waterColor to "")).waterColor)
    }

    @Test
    fun `stored bottle mode is read back`() {
        assertEquals(BottleMode.DRAIN, SettingsStore.toSettings(preferencesOf(bottleMode to "DRAIN")).bottleMode)
        assertEquals(BottleMode.FILL, SettingsStore.toSettings(preferencesOf(bottleMode to "FILL")).bottleMode)
    }

    @Test
    fun `missing or unknown bottle mode falls back to fill`() {
        assertEquals(BottleMode.FILL, SettingsStore.toSettings(emptyPreferences()).bottleMode)
        assertEquals(BottleMode.FILL, SettingsStore.toSettings(preferencesOf(bottleMode to "EMPTY")).bottleMode)
        assertEquals(BottleMode.FILL, SettingsStore.toSettings(preferencesOf(bottleMode to "")).bottleMode)
    }

    @Test
    fun `out of range numbers are coerced`() {
        val prefs = preferencesOf(intPreferencesKey("dailyGoalMl") to 99999, intPreferencesKey("mlPerTap") to 7)
        val settings = SettingsStore.toSettings(prefs)
        assertEquals(5000, settings.dailyGoalMl)
        assertEquals(50, settings.mlPerTap)
    }

    @Test
    fun `valid stored window is read back as minutes of day`() {
        val prefs = preferencesOf(intPreferencesKey("remindFrom") to 9 * 60 + 30, intPreferencesKey("remindUntil") to 23 * 60 + 59)
        val settings = SettingsStore.toSettings(prefs)
        assertEquals(LocalTime.of(9, 30), settings.remindFrom)
        assertEquals(LocalTime.of(23, 59), settings.remindUntil)
    }

    @Test
    fun `invalid or half-stored window falls back to the default window`() {
        val tooShort = preferencesOf(intPreferencesKey("remindFrom") to 600, intPreferencesKey("remindUntil") to 660)
        val garbage = preferencesOf(intPreferencesKey("remindFrom") to -1, intPreferencesKey("remindUntil") to 5000)
        val onlyFrom = preferencesOf(intPreferencesKey("remindFrom") to 600)
        for (prefs in listOf(tooShort, garbage, onlyFrom)) {
            val settings = SettingsStore.toSettings(prefs)
            assertEquals(LocalTime.of(8, 0), settings.remindFrom)
            assertEquals(LocalTime.of(22, 0), settings.remindUntil)
        }
    }

    @Test
    fun `reminder bookkeeping`() {
        val empty = SettingsStore.toBookkeeping(emptyPreferences())
        assertNull(empty.lastReminderAt)
        assertNull(empty.remindersSentDay)
        assertEquals(0, empty.remindersSentCount)

        val prefs = preferencesOf(
            longPreferencesKey("lastReminderAtMs") to 1_780_000_000_000L,
            longPreferencesKey("remindersSentDay") to LocalDate.of(2026, 6, 10).toEpochDay(),
            intPreferencesKey("remindersSentCount") to 2,
        )
        val stored = SettingsStore.toBookkeeping(prefs)
        assertEquals(Instant.ofEpochMilli(1_780_000_000_000L), stored.lastReminderAt)
        assertEquals(LocalDate.of(2026, 6, 10), stored.remindersSentDay)
        assertEquals(2, stored.remindersSentCount)
    }
}
