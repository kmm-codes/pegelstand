package app.pegelstand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pegelstand.data.TodayState
import app.pegelstand.domain.AmountFormat
import app.pegelstand.widget.WidgetPinning
import kotlinx.coroutines.launch

/**
 * Minimal storage check: today's total as text and one button that calls addIntake(), plus a
 * temporary "pin widget" button for testing the widget. Replaced by the real app UI in a later phase.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = appContainer.repository
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val today by repository.today.collectAsStateWithLifecycle(initialValue = null)
                    val scope = rememberCoroutineScope()
                    TodayPlaceholder(
                        today = today,
                        onAdd = { scope.launch { repository.addIntake() } },
                        onPinWidget = { WidgetPinning.requestPin(this@MainActivity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayPlaceholder(today: TodayState?, onAdd: () -> Unit, onPinWidget: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (today == null) return@Column
        Text(
            text = stringResource(R.string.today_total, AmountFormat.totalOfGoal(today.totalMl, today.goalMl)),
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = onAdd) {
            Text(text = stringResource(R.string.add_intake, today.mlPerTap))
        }
        OutlinedButton(onClick = onPinWidget) {
            Text(text = "Widget anheften (Test)")
        }
    }
}
