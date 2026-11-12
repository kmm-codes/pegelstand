package app.pegelstand.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.util.Log
import app.pegelstand.AppContainer
import app.pegelstand.LOG_TAG
import app.pegelstand.appContainer
import app.pegelstand.data.db.DayGoalEntity
import app.pegelstand.data.db.IntakeEntity
import app.pegelstand.domain.AmountFormat
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.WaterColor
import app.pegelstand.domain.isReminderDue
import app.pegelstand.domain.logicalDay
import app.pegelstand.domain.nextWakeUp
import app.pegelstand.ui.bottle.BottleRenderer
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only control surface for adb (see docs/debug-commands.md). Exported, but guarded by the
 * DUMP permission, which the adb shell holds and ordinary apps cannot get. Every action is
 * followed by a widget refresh and a reschedule of the alarm, and the broadcast result carries a
 * one-line status (fake time, today's total, next wake-up), which `am broadcast` prints.
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.appContainer
        container.applicationScope.launch {
            val result = try {
                val message = handle(context, container, intent)
                container.repository.recheckClock()
                container.widgetRefresher.refreshAll()
                container.repository.rescheduleReminders()
                listOfNotNull(message, status(container)).joinToString(" | ")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Debug action ${intent.action} failed", e)
                "ERROR ${e.javaClass.simpleName}: ${e.message}"
            }
            Log.i(LOG_TAG, "debug ${intent.action}: $result")
            pending.resultData = result
            pending.finish()
        }
    }

    private suspend fun handle(context: Context, container: AppContainer, intent: Intent): String? {
        val repository = container.repository
        val clock = container.timeSource as DebugClock
        return when (intent.action) {
            ACTION_SET_TIME -> {
                val time = LocalTime.parse(requireNotNull(intent.getStringExtra("time")) { "extra 'time' (HH:mm) missing" })
                val date = intent.getStringExtra("date")?.let(LocalDate::parse) ?: LocalDate.now(clock.zone())
                clock.setFixed(LocalDateTime.of(date, time).atZone(clock.zone()).toInstant())
                null
            }
            ACTION_ADVANCE_TIME -> {
                val minutes = intent.getIntExtra("minutes", 0).toLong()
                clock.setFixed(clock.now().plusSeconds(minutes * 60))
                null
            }
            ACTION_CLEAR_TIME -> {
                clock.setFixed(null)
                null
            }
            ACTION_RUN_CHECK -> {
                repository.runReminderCheck()
                null
            }
            ACTION_SET_COLOR -> {
                val id = intent.getStringExtra("color")
                val color = requireNotNull(WaterColor.entries.firstOrNull { it.id == id }) {
                    "unknown colour '$id', one of ${WaterColor.entries.joinToString { it.id }}"
                }
                repository.setWaterColor(color)
                null
            }
            ACTION_SET_BOTTLE_MODE -> {
                val id = intent.getStringExtra("mode")
                val mode = requireNotNull(BottleMode.entries.firstOrNull { it.id == id }) {
                    "unknown mode '$id', one of ${BottleMode.entries.joinToString { it.id }}"
                }
                repository.setBottleMode(mode)
                null
            }
            ACTION_SET_GOAL -> {
                repository.setDailyGoal(requireIntExtra(intent, "ml"))
                null
            }
            ACTION_SET_ML_PER_TAP -> {
                repository.setMlPerTap(requireIntExtra(intent, "ml"))
                null
            }
            ACTION_ADD_INTAKE -> {
                repeat(intent.getIntExtra("count", 1)) { repository.addIntake() }
                null
            }
            ACTION_UNDO -> {
                repository.undoLast()
                null
            }
            ACTION_CLEAR_DATA -> {
                container.database.clearAllTables()
                container.settingsStore.clearAll()
                container.reminderNotifier.cancel()
                null
            }
            ACTION_SEED_HISTORY -> seedHistory(container, intent)
            ACTION_RENDER_BOTTLE -> renderBottle(context, intent)
            ACTION_STATUS -> null
            else -> "unknown action ${intent.action}"
        }
    }

    /**
     * Writes past days through the DAO (debug-only exception to the single write path). `totals`
     * is a comma-separated list of ml, oldest first; the last value is yesterday. Each listed day
     * is replaced: its intakes are removed, then one intake of that total is written at 12:00
     * with the day's goal (`goal` extra, default the current goal). A 0 leaves the day empty.
     */
    private suspend fun seedHistory(container: AppContainer, intent: Intent): String {
        val totals = intent.getStringExtra("totals")?.split(',')?.map { it.trim().toInt() }
            ?: List(intent.getIntExtra("days", 0)) { intent.getIntExtra("ml", 0) }
        require(totals.isNotEmpty()) { "give --es totals 2000,1500,... or --ei days N --ei ml X" }
        val goal = intent.getIntExtra("goal", container.settingsStore.current().dailyGoalMl)
        val zone = container.timeSource.zone()
        val today = logicalDay(container.timeSource.now(), zone)
        val dao = container.database.waterDao()
        totals.forEachIndexed { index, total ->
            val day = today.minusDays((totals.size - index).toLong())
            val epochDay = day.toEpochDay()
            while (dao.deleteLatestOfDay(epochDay)) {
                // Removes the day's intakes one by one until none is left.
            }
            if (total > 0) {
                val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                dao.addIntake(IntakeEntity(timestampMs = noon, day = epochDay, amountMl = total), currentGoalMl = goal)
                dao.upsertGoal(DayGoalEntity(day = epochDay, goalMl = goal))
            }
        }
        return "seeded ${totals.size} days ${today.minusDays(totals.size.toLong())}..${today.minusDays(1)}"
    }

    /** Renders one bottle into files/bottle.png (for the widget picker preview and for review). */
    private fun renderBottle(context: Context, intent: Intent): String {
        val width = intent.getIntExtra("width", 390)
        val height = intent.getIntExtra("height", 500)
        val bitmap = createBitmap(width, height)
        BottleRenderer().draw(
            canvas = Canvas(bitmap),
            width = width.toFloat(),
            height = height.toFloat(),
            fillFraction = intent.getFloatExtra("fill", 0.6f),
            wavePhase = intent.getFloatExtra("phase", 0.9f),
            waterColor = WaterColor.fromId(intent.getStringExtra("color")),
            goalReached = intent.getBooleanExtra("goal", false),
            dark = intent.getBooleanExtra("dark", false),
        )
        val file = File(context.filesDir, "bottle.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "wrote ${file.path}"
    }

    private suspend fun status(container: AppContainer): String {
        val clock = container.timeSource as DebugClock
        val now = clock.now()
        val zone = clock.zone()
        val today = container.repository.currentToday()
        val state = container.repository.reminderState(now)
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val local = { instant: Instant -> instant.atZone(zone).format(format) }
        val yesterday = today.day.minusDays(1)
        val yesterdayMl = container.repository.dayTotals(yesterday, yesterday).first().sumOf { it.totalMl }
        return listOf(
            "now=${local(now)}${if (clock.fixed != null) " (fixed)" else ""}",
            "day=${today.day}",
            "yesterday=$yesterdayMl ml",
            "total=${AmountFormat.totalOfGoal(today.totalMl, today.goalMl)} (${today.totalMl}/${today.goalMl} ml)",
            "tap=${today.mlPerTap}",
            "color=${today.waterColor.id}",
            "mode=${today.bottleMode.id}",
            "streak=${today.streak.current}/${today.streak.longest}",
            "reminderDue=${isReminderDue(state, now)}",
            "remindersToday=${if (state.remindersSentDay == today.day) state.remindersSentCount else 0}",
            "nextWakeUp=${local(nextWakeUp(state, now))}",
        ).joinToString(" ")
    }

    private fun requireIntExtra(intent: Intent, name: String): Int {
        require(intent.hasExtra(name)) { "extra '$name' (--ei) missing" }
        return intent.getIntExtra(name, 0)
    }

    private companion object {
        const val PREFIX = "app.pegelstand.debug."
        const val ACTION_SET_TIME = PREFIX + "SET_TIME"
        const val ACTION_ADVANCE_TIME = PREFIX + "ADVANCE_TIME"
        const val ACTION_CLEAR_TIME = PREFIX + "CLEAR_TIME"
        const val ACTION_RUN_CHECK = PREFIX + "RUN_CHECK"
        const val ACTION_SET_COLOR = PREFIX + "SET_COLOR"
        const val ACTION_SET_BOTTLE_MODE = PREFIX + "SET_BOTTLE_MODE"
        const val ACTION_SET_GOAL = PREFIX + "SET_GOAL"
        const val ACTION_SET_ML_PER_TAP = PREFIX + "SET_ML_PER_TAP"
        const val ACTION_ADD_INTAKE = PREFIX + "ADD_INTAKE"
        const val ACTION_UNDO = PREFIX + "UNDO"
        const val ACTION_CLEAR_DATA = PREFIX + "CLEAR_DATA"
        const val ACTION_SEED_HISTORY = PREFIX + "SEED_HISTORY"
        const val ACTION_RENDER_BOTTLE = PREFIX + "RENDER_BOTTLE"
        const val ACTION_STATUS = PREFIX + "STATUS"
    }
}
