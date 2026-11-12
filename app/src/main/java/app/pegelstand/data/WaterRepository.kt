package app.pegelstand.data

import app.pegelstand.data.db.DayTotalRow
import app.pegelstand.data.db.IntakeEntity
import app.pegelstand.data.db.WaterDao
import app.pegelstand.data.db.DayGoalEntity
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.DayTotal
import app.pegelstand.domain.LogicalDay
import app.pegelstand.domain.ReminderState
import app.pegelstand.domain.Settings
import app.pegelstand.domain.Streak
import app.pegelstand.domain.Streaks
import app.pegelstand.domain.TimeSource
import app.pegelstand.domain.WaterColor
import app.pegelstand.domain.deficitMl
import app.pegelstand.domain.glassesBehind
import app.pegelstand.domain.isReminderDue
import app.pegelstand.domain.logicalDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Everything the widget and the "Heute" section show, for the current logical day. */
data class TodayState(
    val day: LocalDate,
    val totalMl: Int,
    val goalMl: Int,
    val mlPerTap: Int,
    val waterColor: WaterColor,
    val bottleMode: BottleMode,
    val streak: Streak,
) {
    val hasIntakeToday: Boolean get() = totalMl > 0
    val goalReached: Boolean get() = totalMl >= goalMl

    /** The bottle level to draw (0..1), in the direction of [bottleMode]. */
    val shownFill: Float get() = app.pegelstand.domain.shownFill(totalMl, goalMl, bottleMode)
}

/**
 * The only write path. Widget, notification action and app call [addIntake] / [undoLast]; none of
 * them writes on its own. Every write is followed, in this order, by: refresh all widgets, cancel
 * a showing reminder notification (intake writes only), reschedule the next check. A write and
 * its side effects run to completion even if the caller's scope is cancelled meanwhile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WaterRepository(
    private val dao: WaterDao,
    private val settingsStore: SettingsStore,
    private val time: TimeSource,
    private val widgets: WidgetRefresher,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
) {

    val settings: Flow<Settings> = settingsStore.settings

    /** Bumped by [recheckClock]; restarts the day evaluation of every collector at once. */
    private val clockGeneration = MutableStateFlow(0L)

    /**
     * The current logical day. Re-evaluated at the next 04:00 boundary, at least once a minute,
     * and immediately on [recheckClock] (so a clock or time-zone change is picked up while
     * collected); emits only on change.
     */
    val currentDay: Flow<LocalDate> = clockGeneration.flatMapLatest {
        flow {
            while (true) {
                val now = time.now()
                val zone = time.zone()
                emit(logicalDay(now, zone))
                val untilBoundaryMs = Duration.between(now, LogicalDay.nextStart(now, zone)).toMillis() + 1
                delay(minOf(untilBoundaryMs, DAY_POLL_MS))
            }
        }
    }.distinctUntilChanged()

    /**
     * Makes every collector of [currentDay] / [today] re-read the clock now instead of at its next
     * poll. Called by the alarm check and the reschedule path (time or time-zone change), so a
     * widget whose update session is still running does not render the previous day.
     */
    fun recheckClock() {
        clockGeneration.update { it + 1 }
    }

    /** Today's state; follows intakes, settings and the day rollover. */
    val today: Flow<TodayState> = currentDay.flatMapLatest { day ->
        val epochDay = day.toEpochDay()
        combine(
            dao.observeTotalOfDay(epochDay),
            dao.observeGoalOfDay(epochDay),
            dao.observeAllDayTotals(),
            settingsStore.settings,
        ) { total, storedGoal, history, settings ->
            val goal = storedGoal ?: settings.dailyGoalMl
            val days = history.map { it.toDomain(settings.dailyGoalMl) }
            TodayState(
                day = day,
                totalMl = total,
                goalMl = goal,
                mlPerTap = settings.mlPerTap,
                waterColor = settings.waterColor,
                bottleMode = settings.bottleMode,
                streak = Streaks.compute(days, day),
            )
        }
    }

    /** One-shot snapshot of [today], e.g. for rendering a widget. */
    suspend fun currentToday(): TodayState = today.first()

    /**
     * Totals of the days in [from]..[to] (inclusive) that have at least one intake, ascending.
     * Each day carries its stored goal; a day without a stored goal falls back to the current goal.
     * Days without intake are absent; the caller fills them with 0.
     */
    fun dayTotals(from: LocalDate, to: LocalDate): Flow<List<DayTotal>> =
        combine(dao.observeDayTotals(from.toEpochDay(), to.toEpochDay()), settingsStore.settings) { rows, settings ->
            rows.map { it.toDomain(settings.dailyGoalMl) }
        }

    /** Adds one tap of the current `mlPerTap` to the current logical day. */
    suspend fun addIntake() = withContext(NonCancellable) {
        val now = time.now()
        val settings = settingsStore.current()
        val day = logicalDay(now, time.zone()).toEpochDay()
        dao.addIntake(
            IntakeEntity(timestampMs = now.toEpochMilli(), day = day, amountMl = settings.mlPerTap),
            currentGoalMl = settings.dailyGoalMl,
        )
        afterIntakeWrite(now)
    }

    /**
     * Removes the most recent intake of the current logical day. Returns false (and has no side
     * effects) if today has none.
     */
    suspend fun undoLast(): Boolean = withContext(NonCancellable) {
        val now = time.now()
        val removed = dao.deleteLatestOfDay(logicalDay(now, time.zone()).toEpochDay())
        if (removed) afterIntakeWrite(now)
        removed
    }

    /** Changes the goal and today's stored goal; earlier days keep theirs. */
    suspend fun setDailyGoal(ml: Int) = withContext(NonCancellable) {
        settingsStore.setDailyGoal(ml)
        val now = time.now()
        val day = logicalDay(now, time.zone()).toEpochDay()
        dao.upsertGoal(DayGoalEntity(day = day, goalMl = settingsStore.current().dailyGoalMl))
        afterSettingsWrite(now, affectsWidget = true)
    }

    suspend fun setMlPerTap(ml: Int) = withContext(NonCancellable) {
        settingsStore.setMlPerTap(ml)
        afterSettingsWrite(time.now(), affectsWidget = true)
    }

    suspend fun setWaterColor(color: WaterColor) = withContext(NonCancellable) {
        settingsStore.setWaterColor(color)
        afterSettingsWrite(time.now(), affectsWidget = true)
    }

    /** A change of direction, not of the level: the widgets update statically. */
    suspend fun setBottleMode(mode: BottleMode) = withContext(NonCancellable) {
        settingsStore.setBottleMode(mode)
        afterSettingsWrite(time.now(), affectsWidget = true)
    }

    suspend fun setRemindersEnabled(enabled: Boolean) = withContext(NonCancellable) {
        settingsStore.setRemindersEnabled(enabled)
        afterSettingsWrite(time.now(), affectsWidget = false)
    }

    /** @throws IllegalArgumentException unless from >= 04:00, until <= 23:59, until - from >= 2 h. */
    suspend fun setReminderWindow(from: LocalTime, until: LocalTime) = withContext(NonCancellable) {
        settingsStore.setReminderWindow(from, until)
        afterSettingsWrite(time.now(), affectsWidget = false)
    }

    /**
     * The logical day whose "goal reached" celebration the app has played. Persisted so the
     * celebration plays at most once per day, across app restarts and widget intakes. App UI
     * state only: no widget refresh or reschedule follows.
     */
    val celebratedDay: Flow<LocalDate?> = settingsStore.celebratedDay

    suspend fun markCelebrated(day: LocalDate) = withContext(NonCancellable) {
        settingsStore.setCelebratedDay(day)
    }

    /** Snapshot of everything [isReminderDue] / [app.pegelstand.domain.nextCheckTime] need at [now]. */
    suspend fun reminderState(now: Instant = time.now()): ReminderState {
        val zone = time.zone()
        val day = logicalDay(now, zone).toEpochDay()
        val settings = settingsStore.current()
        val bookkeeping = settingsStore.currentBookkeeping()
        return ReminderState(
            enabled = settings.remindersEnabled,
            goalMl = dao.goalOfDay(day) ?: settings.dailyGoalMl,
            totalMl = dao.totalOfDay(day),
            remindFrom = settings.remindFrom,
            remindUntil = settings.remindUntil,
            zone = zone,
            lastIntakeAt = dao.lastIntakeTimestampMs()?.let(Instant::ofEpochMilli),
            lastReminderAt = bookkeeping.lastReminderAt,
            remindersSentDay = bookkeeping.remindersSentDay,
            remindersSentCount = bookkeeping.remindersSentCount,
        )
    }

    /** For boot, app update and time/time-zone change: replace the pending alarm. */
    suspend fun rescheduleReminders() = withContext(NonCancellable) {
        recheckClock()
        val now = time.now()
        scheduler.schedule(reminderState(now), now)
    }

    /**
     * What the alarm does when it fires: refresh widgets (day rollover), show a reminder if one is
     * due, schedule the next alarm. The reminder is recorded before it is shown, so a failing
     * notification cannot make the next alarm fire immediately again.
     */
    suspend fun runReminderCheck() = withContext(NonCancellable) {
        val now = time.now()
        recheckClock()
        widgets.refreshAll()
        val state = reminderState(now)
        if (isReminderDue(state, now)) {
            settingsStore.recordReminderSent(now, logicalDay(now, state.zone))
            val mlPerTap = settingsStore.current().mlPerTap
            notifier.show(glassesBehind(deficitMl(state, now), mlPerTap), mlPerTap)
        }
        scheduler.schedule(reminderState(now), now)
    }

    private suspend fun afterIntakeWrite(now: Instant) {
        widgets.refreshAll()
        notifier.cancel()
        scheduler.schedule(reminderState(now), now)
    }

    private suspend fun afterSettingsWrite(now: Instant, affectsWidget: Boolean) {
        if (affectsWidget) widgets.refreshAll()
        scheduler.schedule(reminderState(now), now)
    }

    private fun DayTotalRow.toDomain(fallbackGoalMl: Int) =
        DayTotal(day = LocalDate.ofEpochDay(day), totalMl = totalMl, goalMl = goalMl ?: fallbackGoalMl)

    private companion object {
        const val DAY_POLL_MS = 60_000L
    }
}
