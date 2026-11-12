package app.pegelstand.ui.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pegelstand.R
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.bottle.BottleRenderer
import app.pegelstand.ui.bottle.BottleView
import app.pegelstand.ui.common.ScreenPadding
import app.pegelstand.ui.common.SoftCard
import app.pegelstand.ui.common.rememberAnimationsEnabled
import app.pegelstand.ui.permission.NotificationPermissionRule
import app.pegelstand.ui.permission.NotificationPermissionState
import app.pegelstand.ui.permission.PermissionAction
import app.pegelstand.ui.theme.LocalPegelExtras
import java.time.LocalDate

/**
 * The one scrolling screen: widget card (while no widget is placed), Heute, the permission card
 * (while reminders are on and notifications are not allowed), then Streak, Woche, Monat.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    scrollState: ScrollState,
    permission: NotificationPermissionState,
    widgetPlaced: Boolean,
    pinManually: Boolean,
    onPinWidget: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    val celebratingDay by viewModel.celebrating.collectAsStateWithLifecycle()
    val animationsEnabled = rememberAnimationsEnabled()
    val background = MaterialTheme.colorScheme.background
    val state = loaded ?: run {
        Box(Modifier.fillMaxSize().background(background))
        return
    }
    val today = state.today
    LaunchedEffect(today.day, today.goalReached, state.celebratedDay) {
        viewModel.checkCelebration(state)
    }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedDay = selectedEpochDay?.let(LocalDate::ofEpochDay) ?: today.day

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(today.day, onOpenSettings)
            if (!widgetPlaced) WidgetCard(today.waterColor, pinManually, onPinWidget)
            TodaySection(
                today = today,
                animationsEnabled = animationsEnabled,
                celebrating = celebratingDay == today.day,
                onCelebrationDone = viewModel::celebrationFinished,
                onAdd = viewModel::addIntake,
                onUndo = viewModel::undoLast,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (NotificationPermissionRule.showCard(state.remindersEnabled, permission.granted)) {
                PermissionCard(permission)
            }
            StatsSection(today.streak, state.averageMl)
            WeekSection(state.week, today.goalMl, today.waterColor)
            MonthSection(
                today = today.day,
                history = state.history,
                firstRecordedDay = state.firstRecordedDay,
                defaultGoalMl = state.defaultGoalMl,
                waterColor = today.waterColor,
                selectedDay = selectedDay,
                onSelectDay = { selectedEpochDay = it.toEpochDay() },
            )
        }
        // Keeps scrolled content from running under the status bar icons.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.safeDrawing)
                .background(background.copy(alpha = 0.94f)),
        )
    }
}

@Composable
private fun Header(day: LocalDate, onOpenSettings: () -> Unit) {
    val format = rememberGermanFormatter(R.string.pattern_header_date)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.format(format),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.main_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetCard(waterColor: WaterColor, pinManually: Boolean, onPin: () -> Unit) {
    SoftCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottleView(
                fill = { 0.62f },
                wavePhase = { 0.9f },
                waterColor = waterColor,
                goalReached = false,
                modifier = Modifier.size(width = 56.dp * BottleRenderer.ASPECT, height = 56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.widget_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(if (pinManually) R.string.widget_card_manual else R.string.widget_card_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        if (!pinManually) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButtonEnd(text = stringResource(R.string.widget_card_button), onClick = onPin)
        }
    }
}

@Composable
private fun PermissionCard(permission: NotificationPermissionState) {
    SoftCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = null,
                tint = LocalPegelExtras.current.goldText,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.permission_card_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val label = if (permission.action == PermissionAction.OPEN_SETTINGS) R.string.permission_card_settings else R.string.permission_card_button
        FilledTonalButtonEnd(text = stringResource(label), onClick = permission::request, container = MaterialTheme.colorScheme.surfaceContainerLowest)
    }
}

/** A tonal button aligned to the end of a card. */
@Composable
private fun FilledTonalButtonEnd(text: String, onClick: () -> Unit, container: Color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.height(48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = container,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
