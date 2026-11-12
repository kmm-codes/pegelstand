package app.pegelstand.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pegelstand.data.WaterRepository
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.Settings
import app.pegelstand.domain.SettingsRules
import app.pegelstand.domain.WaterColor
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val settings: Settings,
    /** The reminder window as shown: the stored one, or the one being edited. */
    val remindFrom: LocalTime,
    val remindUntil: LocalTime,
    /** False while the edited window breaks [SettingsRules.isValidWindow]; it is then not saved. */
    val windowValid: Boolean,
) {
    val canDecreaseGoal: Boolean get() = settings.dailyGoalMl > SettingsRules.MIN_GOAL_ML
    val canIncreaseGoal: Boolean get() = settings.dailyGoalMl < SettingsRules.MAX_GOAL_ML
    val canDecreaseTap: Boolean get() = settings.mlPerTap > SettingsRules.MIN_ML_PER_TAP
    val canIncreaseTap: Boolean get() = settings.mlPerTap < SettingsRules.MAX_ML_PER_TAP
}

/** Settings screen. Every change goes through [WaterRepository], which refreshes the widget. */
class SettingsViewModel(private val repository: WaterRepository) : ViewModel() {

    /** An edited reminder window; saved only once it is valid, shown until the screen closes. */
    private val draftWindow = MutableStateFlow<Pair<LocalTime, LocalTime>?>(null)

    val state: StateFlow<SettingsUiState?> = combine(repository.settings, draftWindow) { settings, draft ->
        val (from, until) = draft ?: (settings.remindFrom to settings.remindUntil)
        SettingsUiState(settings, from, until, SettingsRules.isValidWindow(from, until))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Serialises writes, so fast taps on a stepper each build on the previous value. */
    private val writes = Mutex()

    fun stepGoal(steps: Int) = write {
        val current = repository.settings.first().dailyGoalMl
        repository.setDailyGoal(SettingsRules.coerceGoal(current + steps * SettingsRules.GOAL_STEP_ML))
    }

    fun stepMlPerTap(steps: Int) = write {
        val current = repository.settings.first().mlPerTap
        repository.setMlPerTap(SettingsRules.coerceMlPerTap(current + steps * SettingsRules.ML_PER_TAP_STEP))
    }

    fun setWaterColor(color: WaterColor) = write { repository.setWaterColor(color) }

    fun setBottleMode(mode: BottleMode) = write { repository.setBottleMode(mode) }

    fun setRemindersEnabled(enabled: Boolean) = write { repository.setRemindersEnabled(enabled) }

    fun setRemindFrom(time: LocalTime) = editWindow(time, currentWindow().second)

    fun setRemindUntil(time: LocalTime) = editWindow(currentWindow().first, time)

    /** Drops an unsaved (invalid) window edit, e.g. when the screen is left. */
    fun discardWindowDraft() {
        draftWindow.value = null
    }

    private fun currentWindow(): Pair<LocalTime, LocalTime> {
        val shown = state.value ?: return SettingsRules.DEFAULT_REMIND_FROM to SettingsRules.DEFAULT_REMIND_UNTIL
        return shown.remindFrom to shown.remindUntil
    }

    private fun editWindow(from: LocalTime, until: LocalTime) {
        draftWindow.value = from to until
        // An invalid window is never handed to the repository (it would throw); it stays a draft.
        if (SettingsRules.isValidWindow(from, until)) write { repository.setReminderWindow(from, until) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }

    companion object {
        fun factory(repository: WaterRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
