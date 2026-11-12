package app.pegelstand.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pegelstand.R
import app.pegelstand.domain.AmountFormat
import app.pegelstand.domain.BottleMode
import app.pegelstand.domain.SettingsRules
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.common.ScreenPadding
import app.pegelstand.ui.common.SoftCard
import app.pegelstand.ui.labelRes
import app.pegelstand.ui.permission.NotificationPermissionState
import app.pegelstand.ui.permission.PermissionAction
import app.pegelstand.ui.theme.deepColor
import app.pegelstand.ui.theme.lightColor
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private enum class WindowEnd { FROM, UNTIL }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    permission: NotificationPermissionState,
    onBack: () -> Unit,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose { viewModel.discardWindowDraft() }
    }
    var picking by rememberSaveable { mutableStateOf<WindowEnd?>(null) }
    val background = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                }
                Spacer(Modifier.width(4.dp))
                Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            val state = loaded ?: return@Column

            SoftCard {
                SectionLabel(stringResource(R.string.settings_section_drinking))
                Stepper(
                    label = stringResource(R.string.settings_goal),
                    value = AmountFormat.litersWithUnit(state.settings.dailyGoalMl),
                    decreaseDescription = stringResource(R.string.settings_goal_decrease),
                    increaseDescription = stringResource(R.string.settings_goal_increase),
                    canDecrease = state.canDecreaseGoal,
                    canIncrease = state.canIncreaseGoal,
                    onStep = viewModel::stepGoal,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                Stepper(
                    label = stringResource(R.string.settings_tap),
                    value = stringResource(R.string.amount_ml, state.settings.mlPerTap),
                    decreaseDescription = stringResource(R.string.settings_tap_decrease),
                    increaseDescription = stringResource(R.string.settings_tap_increase),
                    canDecrease = state.canDecreaseTap,
                    canIncrease = state.canIncreaseTap,
                    onStep = viewModel::stepMlPerTap,
                )
            }

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_color),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(state.settings.waterColor.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Swatches(selected = state.settings.waterColor, onSelect = viewModel::setWaterColor)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                BottleModeChoice(selected = state.settings.bottleMode, onSelect = viewModel::setBottleMode)
            }

            SoftCard {
                val enabled = state.settings.remindersEnabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = enabled, role = Role.Switch) { on ->
                            viewModel.setRemindersEnabled(on)
                            if (on && !permission.granted) permission.request()
                        }
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.settings_reminders_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = enabled, onCheckedChange = null)
                }
                if (enabled && !permission.granted) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_reminders_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = permission::request, modifier = Modifier.heightIn(min = 48.dp)) {
                            val label = if (permission.action == PermissionAction.OPEN_SETTINGS) R.string.permission_card_settings else R.string.permission_card_button
                            Text(stringResource(label))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(
                        label = stringResource(R.string.settings_from),
                        time = state.remindFrom,
                        invalid = !state.windowValid,
                        onClick = { picking = WindowEnd.FROM },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = stringResource(R.string.settings_until),
                        time = state.remindUntil,
                        invalid = !state.windowValid,
                        onClick = { picking = WindowEnd.UNTIL },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                val minHours = SettingsRules.MIN_WINDOW.toHours().toInt()
                val rule = pluralStringResource(
                    R.plurals.settings_window_rule,
                    minHours,
                    SettingsRules.EARLIEST_REMIND_FROM.format(TimeFormat),
                    SettingsRules.LATEST_REMIND_UNTIL.format(TimeFormat),
                    minHours,
                )
                Text(
                    text = if (state.windowValid) rule else stringResource(R.string.settings_window_unsaved) + " " + rule,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.windowValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }

            when (picking) {
                WindowEnd.FROM -> TimeDialog(
                    title = stringResource(R.string.settings_pick_from),
                    initial = state.remindFrom,
                    onDismiss = { picking = null },
                    onConfirm = { picking = null; viewModel.setRemindFrom(it) },
                )
                WindowEnd.UNTIL -> TimeDialog(
                    title = stringResource(R.string.settings_pick_until),
                    initial = state.remindUntil,
                    onDismiss = { picking = null },
                    onConfirm = { picking = null; viewModel.setRemindUntil(it) },
                )
                null -> Unit
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.safeDrawing)
                .background(background.copy(alpha = 0.94f)),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (Int) -> Unit,
) {
    val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.primary,
    )
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = { onStep(-1) }, enabled = canDecrease, colors = buttonColors, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = decreaseDescription)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 96.dp).padding(horizontal = 6.dp),
        )
        FilledTonalIconButton(onClick = { onStep(1) }, enabled = canIncrease, colors = buttonColors, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = increaseDescription)
        }
    }
}

@Composable
private fun Swatches(selected: WaterColor, onSelect: (WaterColor) -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    val selectedLabel = stringResource(R.string.settings_color_selected)
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        WaterColor.entries.forEach { color ->
            val isSelected = color == selected
            val label = stringResource(color.labelRes)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(color) }
                    .semantics { contentDescription = if (isSelected) "$label, $selectedLabel" else label }
                    .then(if (isSelected) Modifier.border(2.5.dp, ring, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 36.dp else 40.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(color.lightColor, color.deepColor))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "Flasche": fills up or drains as she drinks. */
@Composable
private fun BottleModeChoice(selected: BottleMode, onSelect: (BottleMode) -> Unit) {
    Text(
        text = stringResource(R.string.settings_bottle),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    val modes = BottleMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(
                        when (mode) {
                            BottleMode.FILL -> R.string.settings_bottle_fill
                            BottleMode.DRAIN -> R.string.settings_bottle_drain
                        },
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TimeField(label: String, time: LocalTime, invalid: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (invalid) scheme.errorContainer else scheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (invalid) scheme.onErrorContainer else scheme.onSurfaceVariant,
            )
            Text(
                text = time.format(TimeFormat),
                style = MaterialTheme.typography.titleLarge,
                color = if (invalid) scheme.onErrorContainer else scheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.settings_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}
