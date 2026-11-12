package app.pegelstand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pegelstand.ui.main.MainScreen
import app.pegelstand.ui.main.MainViewModel
import app.pegelstand.ui.permission.rememberNotificationPermissionState
import app.pegelstand.ui.settings.SettingsScreen
import app.pegelstand.ui.settings.SettingsViewModel
import app.pegelstand.ui.theme.PegelTheme
import app.pegelstand.widget.WidgetPinning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The single activity: the main screen, and the settings on top of it. */
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModel.factory(appContainer.repository) }
    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(appContainer.repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PegelTheme {
                PegelApp(mainViewModel, settingsViewModel)
            }
        }
    }
}

@Composable
private fun PegelApp(mainViewModel: MainViewModel, settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val mainScroll = rememberScrollState()
    val permission = rememberNotificationPermissionState()

    // The launcher's pin dialog runs on top of this activity; the result is known on return.
    var widgetPlaced by rememberSaveable { mutableStateOf(true) }
    var pinManually by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        widgetPlaced = WidgetPinning.isWidgetPlaced(context)
        // The launcher may bind the new widget a moment after the dialog closed.
        val recheck = scope.launch {
            delay(WIDGET_RECHECK_MS)
            widgetPlaced = WidgetPinning.isWidgetPlaced(context)
        }
        onPauseOrDispose { recheck.cancel() }
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(tween(TRANSITION_MS)) { it / 6 * direction } + fadeIn(tween(TRANSITION_MS)))
                    .togetherWith(slideOutHorizontally(tween(TRANSITION_MS)) { -it / 6 * direction } + fadeOut(tween(TRANSITION_MS / 2)))
            },
            label = "screen",
        ) { settings ->
            if (settings) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    permission = permission,
                    onBack = { showSettings = false },
                )
            } else {
                MainScreen(
                    viewModel = mainViewModel,
                    scrollState = mainScroll,
                    permission = permission,
                    widgetPlaced = widgetPlaced,
                    pinManually = pinManually,
                    onPinWidget = { pinManually = !WidgetPinning.requestPin(context) },
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}

private const val WIDGET_RECHECK_MS = 1_000L
private const val TRANSITION_MS = 280
