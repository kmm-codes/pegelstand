package app.pegelstand.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pegelstand.data.TodayState
import app.pegelstand.data.WaterRepository
import app.pegelstand.domain.DayTotal
import app.pegelstand.ui.history.DayCell
import app.pegelstand.ui.history.History
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the main screen shows. */
data class MainUiState(
    val today: TodayState,
    /** Day of the last played goal celebration (persisted). */
    val celebratedDay: LocalDate?,
    /** Days with intake, up to today, by day. */
    val history: Map<LocalDate, DayTotal>,
    val firstRecordedDay: LocalDate?,
    val averageMl: Int?,
    val week: List<DayCell>,
    /** The current goal setting: fallback for days without a stored goal. */
    val defaultGoalMl: Int,
    val remindersEnabled: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: WaterRepository) : ViewModel() {

    private val history = repository.currentDay.flatMapLatest { day ->
        repository.dayTotals(LocalDate.ofEpochDay(0), day)
    }

    val state: StateFlow<MainUiState?> = combine(
        repository.today,
        repository.celebratedDay,
        history,
        repository.settings,
    ) { today, celebratedDay, days, settings ->
        val byDay = History.byDay(days)
        MainUiState(
            today = today,
            celebratedDay = celebratedDay,
            history = byDay,
            firstRecordedDay = History.firstRecordedDay(days),
            averageMl = History.averageMl(days, today.day),
            week = History.week(today.day, byDay, settings.dailyGoalMl),
            defaultGoalMl = settings.dailyGoalMl,
            remindersEnabled = settings.remindersEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _celebrating = MutableStateFlow<LocalDate?>(null)
    private var startedDay: LocalDate? = null

    /** The day whose celebration is playing right now, or null. */
    val celebrating: StateFlow<LocalDate?> = _celebrating.asStateFlow()

    fun addIntake() {
        viewModelScope.launch { repository.addIntake() }
    }

    fun undoLast() {
        viewModelScope.launch { repository.undoLast() }
    }

    /**
     * Called by the visible screen whenever today's state changes. Starts the celebration if
     * [CelebrationRule] says so and records the day at once, so it never plays twice.
     */
    fun checkCelebration(state: MainUiState) {
        val day = state.today.day
        if (!CelebrationRule.shouldCelebrate(day, state.today.goalReached, state.celebratedDay)) return
        // Guards the moment between starting and the persisted day coming back through the flow.
        if (startedDay == day) return
        startedDay = day
        _celebrating.value = day
        viewModelScope.launch { repository.markCelebrated(day) }
    }

    fun celebrationFinished() {
        _celebrating.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(repository: WaterRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(repository) }
        }
    }
}
