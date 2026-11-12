package app.pegelstand.ui.bottle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.theme.LocalPegelExtras

/**
 * The shared bottle, drawn by the same [BottleRenderer] as the widget. [fill] and [wavePhase] are
 * read in the draw phase only, so an animation redraws without recomposing.
 */
@Composable
fun BottleView(
    fill: () -> Float,
    wavePhase: () -> Float,
    waterColor: WaterColor,
    goalReached: Boolean,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { BottleRenderer() }
    val dark = LocalPegelExtras.current.dark
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            renderer.draw(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                fillFraction = fill(),
                wavePhase = wavePhase(),
                waterColor = waterColor,
                goalReached = goalReached,
                dark = dark,
            )
        }
    }
}
