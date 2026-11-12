package app.pegelstand.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.pegelstand.R
import app.pegelstand.data.TodayState
import app.pegelstand.domain.AmountFormat
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.bottle.BottleRenderer
import app.pegelstand.ui.bottle.BottleView
import app.pegelstand.ui.theme.LocalPegelExtras
import app.pegelstand.ui.theme.buttonColor
import app.pegelstand.ui.theme.deepColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Height of the bottle on the main screen. */
private val BottleHeight = 300.dp

/**
 * "Heute": the animated bottle, amount and goal, the add button and undo.
 *
 * @param celebrating true while the goal celebration plays; [onCelebrationDone] ends it.
 */
@Composable
fun TodaySection(
    today: TodayState,
    animationsEnabled: Boolean,
    celebrating: Boolean,
    onCelebrationDone: () -> Unit,
    onAdd: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = today.shownFill
    val level = remember { Animatable(target) }
    LaunchedEffect(target, animationsEnabled) {
        if (animationsEnabled) level.animateTo(target, tween(LEVEL_EASE_MS, easing = FastOutSlowInEasing)) else level.snapTo(target)
    }
    val wavePhase = rememberWavePhase(animationsEnabled)

    val celebration = remember { Animatable(0f) }
    val done by rememberUpdatedState(onCelebrationDone)
    LaunchedEffect(celebrating, animationsEnabled) {
        if (!celebrating) return@LaunchedEffect
        if (animationsEnabled) {
            celebration.snapTo(0f)
            celebration.animateTo(1f, tween(CELEBRATION_MS, easing = LinearEasing))
        }
        celebration.snapTo(0f)
        done()
    }

    val percent = (target * 100).roundToInt()
    val bottleDescription = stringResource(R.string.main_bottle_description, percent)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().height(BottleHeight),
            contentAlignment = Alignment.Center,
        ) {
            val waterColor = today.waterColor
            val extras = LocalPegelExtras.current
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = celebration.value
                if (t > 0f) drawCelebrationGlow(t, extras.gold, extras.goldLight)
            }
            BottleView(
                fill = { level.value },
                wavePhase = { wavePhase.value },
                waterColor = waterColor,
                goalReached = today.goalReached,
                modifier = Modifier
                    .size(width = BottleHeight * BottleRenderer.ASPECT, height = BottleHeight)
                    .graphicsLayer {
                        val pop = 1f + POP_SCALE * sin(PI.toFloat() * celebration.value)
                        scaleX = pop
                        scaleY = pop
                    }
                    .semantics { contentDescription = bottleDescription },
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = celebration.value
                if (t > 0f) drawCelebrationBurst(t, waterColor, extras.gold)
            }
        }

        Spacer(Modifier.height(16.dp))
        AmountLine(today)
        Spacer(Modifier.height(20.dp))
        ActionRow(today, onAdd, onUndo)
    }
}

@Composable
private fun AmountLine(today: TodayState) {
    val extras = LocalPegelExtras.current
    val numberColor = if (today.goalReached) extras.goldText else MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val total = AmountFormat.liters(today.totalMl)
    val goal = AmountFormat.litersWithUnit(today.goalMl)
    val description = stringResource(R.string.main_amount_description, total, goal)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = numberColor)) { append(total) }
        withStyle(
            SpanStyle(
                color = muted,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                fontWeight = FontWeight.Medium,
            ),
        ) { append("  /  $goal") }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium,
        maxLines = 1,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun ActionRow(today: TodayState, onAdd: () -> Unit, onUndo: () -> Unit) {
    val addDescription = stringResource(R.string.main_add_description, today.mlPerTap)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Undo on the left, a matching empty slot on the right: the add button stays centred.
        UndoSlot(visible = today.hasIntakeToday, onUndo = onUndo)
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .widthIn(min = 184.dp)
                .semantics { contentDescription = addDescription },
            colors = ButtonDefaults.buttonColors(
                containerColor = today.waterColor.buttonColor,
                contentColor = Color.White,
            ),
            contentPadding = ButtonDefaults.ContentPadding,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.add_intake, today.mlPerTap),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(16.dp))
        Spacer(Modifier.size(UndoSize))
    }
}

/** The undo button, or an empty slot of the same size so the add button keeps its place. */
@Composable
private fun UndoSlot(visible: Boolean, onUndo: () -> Unit) {
        Box(modifier = Modifier.size(UndoSize), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                FilledTonalIconButton(
                    onClick = onUndo,
                    modifier = Modifier.size(UndoSize),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_undo),
                        contentDescription = stringResource(R.string.main_undo),
                    )
                }
            }
        }
}

private val UndoSize = 56.dp

/** Continuous wave phase in radians; constant when animations are off. */
@Composable
private fun rememberWavePhase(enabled: Boolean): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(RESTING_PHASE) }
    val transition = rememberInfiniteTransition(label = "wave")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
}

/** One burst particle: direction, travel, size, and whether it is a gold star or a water drop. */
private class Particle(val angle: Float, val reach: Float, val size: Float, val star: Boolean, val spin: Float)

private val Particles: List<Particle> = Random(7).let { random ->
    List(PARTICLE_COUNT) { i ->
        Particle(
            angle = (i.toFloat() / PARTICLE_COUNT + random.nextFloat() * 0.03f) * 2f * PI.toFloat(),
            reach = 1.15f + random.nextFloat() * 0.3f,
            size = 0.5f + random.nextFloat() * 0.6f,
            star = i % 3 != 2,
            spin = (random.nextFloat() - 0.5f) * 3f,
        )
    }
}

private val StarPath: Path = Path().apply {
    // Four-pointed star of radius 1 around the origin, like the bottle's sparkles.
    moveTo(0f, -1f)
    quadraticTo(0.12f, -0.12f, 1f, 0f)
    quadraticTo(0.12f, 0.12f, 0f, 1f)
    quadraticTo(-0.12f, 0.12f, -1f, 0f)
    quadraticTo(-0.12f, -0.12f, 0f, -1f)
    close()
}

/** Soft gold glow behind the bottle: swells in the first part of the celebration, then fades. */
private fun DrawScope.drawCelebrationGlow(t: Float, gold: Color, goldLight: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val unit = size.height / 2f
    val alpha = 0.6f * sin(PI.toFloat() * (t * 1.25f).coerceAtMost(1f))
    val radius = unit * (0.8f + 0.4f * easeOut(t))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(goldLight.copy(alpha = alpha), gold.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * A ring of gold stars and water drops bursting out from behind the bottle's edge, drawn in
 * front of it, and fading out in the second half of the celebration.
 */
private fun DrawScope.drawCelebrationBurst(t: Float, waterColor: WaterColor, gold: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val unit = size.height / 2f
    val travel = easeOut((t / 0.7f).coerceAtMost(1f))
    val alpha = if (t < 0.5f) 1f else (1f - (t - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val appear = (t / 0.08f).coerceAtMost(1f)
    for (p in Particles) {
        val dx = cos(p.angle)
        val dy = sin(p.angle)
        // Start on a box just around the bottle's silhouette, so no particle sits on the bottle;
        // those heading down travel less, they would otherwise run into the amount below.
        val edge = 1f / maxOf(abs(dx) / START_HALF_WIDTH, abs(dy) / START_HALF_HEIGHT)
        val reach = if (dy > 0.3f) 1f + (p.reach - 1f) * 0.5f else p.reach
        val distance = unit * edge * (1f + (reach - 1f) * travel)
        val x = cx + dx * distance
        val y = cy + dy * distance
        val r = unit * 0.075f * p.size * appear
        if (p.star) {
            translate(x, y) {
                rotate(degrees = p.spin * 90f * t, pivot = Offset.Zero) {
                    scale(r, r, pivot = Offset.Zero) {
                        drawPath(StarPath, color = gold.copy(alpha = alpha))
                    }
                }
            }
        } else {
            drawCircle(color = waterColor.deepColor.copy(alpha = alpha), radius = r * 0.5f, center = Offset(x, y))
        }
    }
}

/** Half size of the box the particles start on, in units of half the bottle height. */
private const val START_HALF_WIDTH = 0.86f
private const val START_HALF_HEIGHT = 1.0f

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private const val LEVEL_EASE_MS = 900
private const val WAVE_PERIOD_MS = 3_600
private const val CELEBRATION_MS = 1_500
private const val POP_SCALE = 0.05f
private const val PARTICLE_COUNT = 18

/** Wave phase when nothing moves; the same resting wave as the widget. */
private const val RESTING_PHASE = 0.9f
