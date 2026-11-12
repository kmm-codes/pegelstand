package app.pegelstand.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pegelstand.domain.Settings
import app.pegelstand.domain.SettingsRules
import app.pegelstand.domain.WaterColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reminder bookkeeping persisted next to the settings. */
data class ReminderBookkeeping(
    val lastReminderAt: Instant?,
    val remindersSentDay: LocalDate?,
    val remindersSentCount: Int,
)

/**
 * DataStore-backed settings. Reading never fails on bad stored data: out-of-range values are
 * coerced, an invalid reminder window falls back to the default window, an unknown colour id to
 * [WaterColor.DEFAULT]. Writes are internal to the data layer; the app goes through
 * [WaterRepository] so the side effects (widget refresh, reschedule) are not forgotten.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map(::toSettings)

    val bookkeeping: Flow<ReminderBookkeeping> = dataStore.data.map(::toBookkeeping)

    /** The logical day whose goal celebration the app has already played, or null. */
    val celebratedDay: Flow<LocalDate?> = dataStore.data.map { prefs -> prefs[Keys.CELEBRATED_DAY]?.let(LocalDate::ofEpochDay) }

    suspend fun current(): Settings = settings.first()

    suspend fun currentBookkeeping(): ReminderBookkeeping = bookkeeping.first()

    internal suspend fun setDailyGoal(ml: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL_ML] = SettingsRules.coerceGoal(ml) }
    }

    internal suspend fun setMlPerTap(ml: Int) {
        dataStore.edit { it[Keys.ML_PER_TAP] = SettingsRules.coerceMlPerTap(ml) }
    }

    internal suspend fun setWaterColor(color: WaterColor) {
        dataStore.edit { it[Keys.WATER_COLOR] = color.id }
    }

    internal suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.REMINDERS_ENABLED] = enabled }
    }

    internal suspend fun setReminderWindow(from: LocalTime, until: LocalTime) {
        require(SettingsRules.isValidWindow(from, until)) { "Invalid reminder window $from-$until" }
        dataStore.edit {
            it[Keys.REMIND_FROM_MIN] = minuteOfDay(from)
            it[Keys.REMIND_UNTIL_MIN] = minuteOfDay(until)
        }
    }

    internal suspend fun setCelebratedDay(day: LocalDate) {
        dataStore.edit { it[Keys.CELEBRATED_DAY] = day.toEpochDay() }
    }

    /** Records one sent reminder: count restarts at 1 on a new logical day. */
    internal suspend fun recordReminderSent(at: Instant, day: LocalDate) {
        dataStore.edit {
            val sameDay = it[Keys.REMINDERS_SENT_DAY] == day.toEpochDay()
            val count = if (sameDay) (it[Keys.REMINDERS_SENT_COUNT] ?: 0) + 1 else 1
            it[Keys.LAST_REMINDER_AT_MS] = at.toEpochMilli()
            it[Keys.REMINDERS_SENT_DAY] = day.toEpochDay()
            it[Keys.REMINDERS_SENT_COUNT] = count
        }
    }

    /** Back to defaults: removes every setting and the reminder bookkeeping (debug tooling). */
    internal suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val DAILY_GOAL_ML = intPreferencesKey("dailyGoalMl")
        val ML_PER_TAP = intPreferencesKey("mlPerTap")
        val WATER_COLOR = stringPreferencesKey("waterColor")
        val REMINDERS_ENABLED = booleanPreferencesKey("remindersEnabled")
        val REMIND_FROM_MIN = intPreferencesKey("remindFrom")
        val REMIND_UNTIL_MIN = intPreferencesKey("remindUntil")
        val LAST_REMINDER_AT_MS = longPreferencesKey("lastReminderAtMs")
        val REMINDERS_SENT_DAY = longPreferencesKey("remindersSentDay")
        val REMINDERS_SENT_COUNT = intPreferencesKey("remindersSentCount")
        val CELEBRATED_DAY = longPreferencesKey("celebratedDay")
    }

    companion object {
        /** Pure mapping from stored preferences to [Settings]; exposed for unit tests. */
        internal fun toSettings(prefs: Preferences): Settings {
            val from = prefs[Keys.REMIND_FROM_MIN]?.let(::timeOfMinute)
            val until = prefs[Keys.REMIND_UNTIL_MIN]?.let(::timeOfMinute)
            val window = if (from != null && until != null && SettingsRules.isValidWindow(from, until)) {
                from to until
            } else {
                SettingsRules.DEFAULT_REMIND_FROM to SettingsRules.DEFAULT_REMIND_UNTIL
            }
            return Settings(
                dailyGoalMl = SettingsRules.coerceGoal(prefs[Keys.DAILY_GOAL_ML] ?: SettingsRules.DEFAULT_GOAL_ML),
                mlPerTap = SettingsRules.coerceMlPerTap(prefs[Keys.ML_PER_TAP] ?: SettingsRules.DEFAULT_ML_PER_TAP),
                waterColor = WaterColor.fromId(prefs[Keys.WATER_COLOR]),
                remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: true,
                remindFrom = window.first,
                remindUntil = window.second,
            )
        }

        internal fun toBookkeeping(prefs: Preferences) = ReminderBookkeeping(
            lastReminderAt = prefs[Keys.LAST_REMINDER_AT_MS]?.let(Instant::ofEpochMilli),
            remindersSentDay = prefs[Keys.REMINDERS_SENT_DAY]?.let(LocalDate::ofEpochDay),
            remindersSentCount = prefs[Keys.REMINDERS_SENT_COUNT] ?: 0,
        )

        private fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

        private fun timeOfMinute(minute: Int): LocalTime? =
            if (minute in 0 until 24 * 60) LocalTime.of(minute / 60, minute % 60) else null
    }
}
