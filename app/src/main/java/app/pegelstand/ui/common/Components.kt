package app.pegelstand.ui.common

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Corner radius shared by every card of the app. */
val CardShape = RoundedCornerShape(28.dp)

/** Side margin of the screens. */
val ScreenPadding: Dp = 20.dp

/** A soft tinted surface without an outline: the one card style of the app. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Content colour set explicitly: surfaceVariant and surfaceContainer share the glass tint, so
    // contentColorFor() would pick the muted onSurfaceVariant.
    Surface(modifier = modifier.fillMaxWidth(), shape = CardShape, color = color, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * False when the system animator duration scale is 0 ("remove animations"); then the app skips
 * motion and shows end states. Re-read on every resume, since the setting can change meanwhile.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabled = ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose { }
    }
    return enabled
}
