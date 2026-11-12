package app.pegelstand.widget

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** What the bottle image shows: water level 0..1 and the "goal reached" gold blend 0..1. */
data class BottleLook(val fill: Float, val gold: Float) {
    /** Equal up to float noise, so a recomputed level does not count as a change. */
    fun sameAs(other: BottleLook): Boolean =
        abs(fill - other.fill) < LOOK_EPSILON && abs(gold - other.gold) < LOOK_EPSILON

    private companion object {
        const val LOOK_EPSILON = 1e-4f
    }
}

/**
 * Everything besides the level that decides whether a change may animate: the logical day, the
 * day's goal, the bottle direction and the water colour. A change of any of them is static.
 */
data class LookContext(val epochDay: Long, val goalMl: Int, val modeId: String, val colorId: String)

/** One running level animation; [startMs] is on a monotonic clock (elapsed realtime). */
data class Motion(val startMs: Long, val from: BottleLook, val to: BottleLook, val durationMs: Long)

/**
 * What a widget shows, as far as the animation cares. [look] is the final look of what was sent
 * last; [motion] is set from the start update until the closing update has been sent.
 */
data class Shown(val context: LookContext, val look: BottleLook, val motion: Motion?)

/** Frames that fit into one update: [frames] distinct images, the in-between ones scaled by [scale]. */
data class FrameBudget(val frames: Int, val scale: Float)

/** The result of [WidgetMotion.decide]. */
sealed interface WidgetChange {
    /** Show the final state at once (no animation). */
    data object Static : WidgetChange

    /** Animate the bottle from [from] to [to]. */
    data class Animate(val from: BottleLook, val to: BottleLook) : WidgetChange
}

/**
 * The widget's level animation as pure logic (no Android): when to animate, which look each
 * frame shows, how the launcher-timed frames map onto the flipper's children, how many frames fit
 * into one update, and when the closing update goes out.
 *
 * Timing model: the start update carries a flipper with [CHILDREN] images that the launcher shows
 * one after another, [TICK_MS] apart, beginning with child 0 when the flipper is attached. The
 * first [MOTION_CHILDREN] children are the motion (the last of them is the final look), the rest
 * repeat the final look as a hold. The closing update, [STOP_SLACK_MS] after the motion, lands in
 * that hold, replaces the flipper by the static image (and the idle loop, see [WidgetIdle]) and so
 * stops it before it would wrap around.
 */
object WidgetMotion {
    /** Flipper interval; must match `android:flipInterval` in `widget_flipper.xml`. */
    const val TICK_MS = 33L

    /**
     * Children of the flipper in `widget_flipper.xml`: the motion, then a hold of about a second.
     * The closing update carries the idle wave's frames and can take a few hundred ms to show up in
     * the launcher; the long hold keeps it from arriving after the flipper has wrapped around to
     * the old level.
     */
    const val CHILDREN = 48

    /** Children that carry the motion; the remaining ones hold the final look. */
    const val MOTION_CHILDREN = 16

    /** The motion's length: about half a second. */
    const val DURATION_MS = MOTION_CHILDREN * TICK_MS

    /** How long after the motion the closing update is sent; well inside the hold. */
    const val STOP_SLACK_MS = 150L

    /** Share of the launcher's bitmap cap that one update may use. */
    const val BUDGET_SHARE = 0.6

    /** Fewer distinct frames than this do not read as motion; then the change is static. */
    const val MIN_FRAMES = 8

    /** In-between frames are never scaled below this (the final frame is always full size). */
    const val MIN_SCALE = 0.5f

    /** The gold blend of a rise into the goal runs over this last part of the eased level. */
    private const val GOLD_IN_FROM = 0.55f

    /** Leaving the goal (undo) drops the gold over this first part of the eased level. */
    private const val GOLD_OUT_UNTIL = 0.45f

    /** Ease-out cubic: fast start, gentle settle, like water finding its level. */
    fun ease(x: Float): Float {
        val t = 1f - x.coerceIn(0f, 1f)
        return 1f - t * t * t
    }

    /**
     * The look at [progress] (0..1 of the motion's time) of a motion from [from] to [to].
     * Progress 1 returns [to] itself, so the last frame equals the static final image.
     */
    fun lookAt(from: BottleLook, to: BottleLook, progress: Float): BottleLook {
        if (progress >= 1f) return to
        if (progress <= 0f) return from
        val level = ease(progress)
        val goldShare = when {
            to.gold > from.gold -> smoothstep(GOLD_IN_FROM, 1f, level)
            to.gold < from.gold -> smoothstep(0f, GOLD_OUT_UNTIL, level)
            else -> 0f
        }
        return BottleLook(
            fill = from.fill + (to.fill - from.fill) * level,
            gold = from.gold + (to.gold - from.gold) * goldShare,
        )
    }

    /**
     * The look a running [motion] shows at [nowMs]: the flipper child visible then, assuming the
     * launcher attached it at [Motion.startMs]. A clock before the start (for example after a
     * reboot) or past the end gives the final look.
     */
    fun lookAt(motion: Motion, nowMs: Long): BottleLook {
        val elapsed = nowMs - motion.startMs
        if (elapsed < 0 || elapsed >= motion.durationMs) return motion.to
        val child = (elapsed / TICK_MS).toInt().coerceIn(0, MOTION_CHILDREN - 1)
        return lookAt(motion.from, motion.to, childProgress(child))
    }

    /** What [shown] shows at [nowMs]: the running motion's current frame, else its final look. */
    fun visibleLook(shown: Shown, nowMs: Long): BottleLook =
        shown.motion?.let { lookAt(it, nowMs) } ?: shown.look

    /** Upper bound for the expected time from a decision until its frames are on screen. */
    const val MAX_LEAD_MS = 250L

    /**
     * Animate or static. Animates only if the widget's previous look is known, belongs to the same
     * [context] (same logical day, goal, direction and colour), animations are enabled, and the
     * level or gold actually changes from what is visible now (mid-motion that is the running
     * frame, so a rapid second tap continues from where the bottle is).
     *
     * @param leadMs expected time until the new frames replace the running ones (rendering and
     *   sending). The running motion keeps playing meanwhile, so it is evaluated that much later;
     *   otherwise the new motion would start below the frame on screen and step back.
     */
    fun decide(
        shown: Shown?,
        context: LookContext,
        target: BottleLook,
        nowMs: Long,
        animationsEnabled: Boolean,
        leadMs: Long = 0,
    ): WidgetChange {
        if (shown == null || shown.context != context || !animationsEnabled) return WidgetChange.Static
        val visible = visibleLook(shown, nowMs + leadMs.coerceIn(0, MAX_LEAD_MS))
        return if (visible.sameAs(target)) WidgetChange.Static else WidgetChange.Animate(visible, target)
    }

    /**
     * True if a change to [target] needs no new update because the motion that is still running
     * (its closing update pending) already heads there in the same [context].
     */
    fun alreadyHeadingThere(shown: Shown?, context: LookContext, target: BottleLook): Boolean {
        val motion = shown?.motion ?: return false
        return shown.context == context && motion.to.sameAs(target)
    }

    /** Time progress (0..1] of motion child [child]: child 0 already moves, the last one arrives. */
    fun childProgress(child: Int): Float = (child + 1).toFloat() / MOTION_CHILDREN

    /**
     * The looks of [frames] distinct images from [from] to [to], evenly spaced in time; the last
     * one is [to].
     */
    fun frameLooks(from: BottleLook, to: BottleLook, frames: Int): List<BottleLook> {
        require(frames >= 1) { "frames must be at least 1" }
        return List(frames) { k -> lookAt(from, to, (k + 1).toFloat() / frames) }
    }

    /**
     * The wave phase of each of [frames] distinct images, at the same moments as [frameLooks]: the
     * wave keeps moving at the idle loop's speed and the last image has the loop's first phase.
     */
    fun framePhases(frames: Int): List<Float> {
        require(frames >= 1) { "frames must be at least 1" }
        val duration = DURATION_MS.toFloat()
        return List(frames) { k -> WidgetIdle.motionPhase(duration * (k + 1) / frames, duration) }
    }

    /**
     * Which of [frames] distinct images flipper child [child] shows. With fewer images than motion
     * children an image is shown for several ticks; hold children show the last (final) image.
     */
    fun frameForChild(child: Int, frames: Int): Int {
        require(child in 0 until CHILDREN) { "child $child out of range" }
        if (child >= MOTION_CHILDREN) return frames - 1
        val index = ceil((child + 1).toDouble() * frames / MOTION_CHILDREN).toInt() - 1
        return index.coerceIn(0, frames - 1)
    }

    /** When the closing update is sent, counted from the start update. */
    fun stopDelayMs(durationMs: Long = DURATION_MS): Long = durationMs + STOP_SLACK_MS

    /**
     * How many frames fit into one update whose bitmap cap is [capBytes], when the final frame
     * takes [finalBytes] (it is always full size, so it matches the static image pixel for pixel).
     * Prefers all [MOTION_CHILDREN] frames at full size, then scales the in-between frames down
     * (to [MIN_SCALE]), then uses fewer frames. Null if not even [MIN_FRAMES] fit: then static.
     */
    fun frameBudget(capBytes: Long, finalBytes: Long): FrameBudget? {
        if (capBytes <= 0 || finalBytes <= 0) return null
        val limit = capBytes * BUDGET_SHARE
        val rest = limit - finalBytes
        if (rest <= 0) return null
        val between = MOTION_CHILDREN - 1
        if (rest >= between.toDouble() * finalBytes) return FrameBudget(MOTION_CHILDREN, 1f)
        val scale = sqrt(rest / (between.toDouble() * finalBytes)).toFloat()
        // Rounded down a little: scaled sizes are rounded up to whole pixels when rendered.
        val safeScale = floor(scale * 100f) / 100f
        if (safeScale >= MIN_SCALE) return FrameBudget(MOTION_CHILDREN, safeScale)
        val fit = 1 + floor(rest / (finalBytes.toDouble() * MIN_SCALE * MIN_SCALE * 1.05)).toInt()
        return if (fit >= MIN_FRAMES) FrameBudget(fit.coerceAtMost(MOTION_CHILDREN), MIN_SCALE) else null
    }

    /** Serialises [shown] for the widget's small persistent record (survives process death). */
    fun encode(shown: Shown): String {
        val c = shown.context
        val parts = mutableListOf(
            FORMAT, c.epochDay, c.goalMl, c.modeId, c.colorId, shown.look.fill, shown.look.gold,
        )
        shown.motion?.let { m -> parts += listOf(m.startMs, m.from.fill, m.from.gold, m.to.fill, m.to.gold, m.durationMs) }
        return parts.joinToString(SEPARATOR)
    }

    /** Reads a record written by [encode]; anything unreadable gives null (= next change is static). */
    fun decode(text: String?): Shown? {
        val p = text?.split(SEPARATOR) ?: return null
        if (p.firstOrNull() != FORMAT || (p.size != 7 && p.size != 13)) return null
        return try {
            val context = LookContext(p[1].toLong(), p[2].toInt(), p[3], p[4])
            val look = BottleLook(p[5].toFloat(), p[6].toFloat())
            val motion = if (p.size == 13) {
                Motion(p[7].toLong(), BottleLook(p[8].toFloat(), p[9].toFloat()), BottleLook(p[10].toFloat(), p[11].toFloat()), p[12].toLong())
            } else {
                null
            }
            Shown(context, look, motion)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private const val FORMAT = "v1"
    private const val SEPARATOR = ";"

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
