package app.pegelstand.widget

import kotlin.math.PI

/** The result of [WidgetIdle.plan]. */
sealed interface IdlePlan {
    /** Switched off (setting or "remove animations"): no idle flipper. */
    data object Off : IdlePlan

    /** Not even the fewest frames fit the bitmap budget: no idle flipper. */
    data object NoRoom : IdlePlan

    /** An idle flipper with [count] distinct frames. */
    data class Frames(val count: Int) : IdlePlan
}

/**
 * The idle wave as pure logic (no Android): while nothing changes, a looping flipper on top of the
 * static bottle shows the same bottle at evenly spaced wave phases over one full period, so the
 * surface moves calmly and the step from the last frame back to the first is like any other.
 *
 * Frame 0 has [STILL_PHASE], the phase of the static image, so it is that image pixel for pixel.
 * Fewer distinct frames (when the bitmap budget is tight) keep the loop's length: each frame is
 * then shown longer, [intervalMs] apart.
 */
object WidgetIdle {
    /** Wave phase of the static image (radians); also the last frame of every change animation. */
    const val STILL_PHASE = 0.9f

    /** One full wave period. */
    const val LOOP_MS = 2400

    /** Distinct frame counts, preferred first; all divide [LOOP_MS] into whole milliseconds. */
    val FRAME_COUNTS = listOf(24, 16, 12, 8)

    private const val TWO_PI = (2 * PI).toFloat()

    /** Wave phase of loop frame [k] of [frames]. */
    fun phase(k: Int, frames: Int): Float {
        require(frames >= 1 && k in 0 until frames) { "frame $k of $frames" }
        return STILL_PHASE + TWO_PI * k / frames
    }

    /** How long each of [frames] frames is shown, so the loop always takes [LOOP_MS]. */
    fun intervalMs(frames: Int): Int {
        require(frames in FRAME_COUNTS) { "unsupported frame count $frames" }
        return LOOP_MS / frames
    }

    /**
     * How many idle frames of [frameBytes] each fit into a static update whose bitmap cap is
     * [capBytes], next to the static images that update carries anyway ([stillBytes]): the
     * largest of [FRAME_COUNTS] within [WidgetMotion.BUDGET_SHARE] of the cap. Null if not even
     * the smallest fits: then the update has no idle wave.
     */
    fun frameCount(capBytes: Long, stillBytes: Long, frameBytes: Long): Int? {
        if (capBytes <= 0 || stillBytes < 0 || frameBytes <= 0) return null
        val limit = capBytes * WidgetMotion.BUDGET_SHARE
        return FRAME_COUNTS.firstOrNull { stillBytes + it * frameBytes <= limit }
    }

    /**
     * Whether a static update gets an idle wave and with how many frames: off unless the user's
     * setting [waveSetting] and the system's animations ([animationsEnabled]) are both on, then
     * [frameCount]. (A flat surface, where all frames would be equal, is found when rendering.)
     */
    fun plan(waveSetting: Boolean, animationsEnabled: Boolean, capBytes: Long, stillBytes: Long, frameBytes: Long): IdlePlan {
        if (!waveSetting || !animationsEnabled) return IdlePlan.Off
        return frameCount(capBytes, stillBytes, frameBytes)?.let { IdlePlan.Frames(it) } ?: IdlePlan.NoRoom
    }

    /**
     * Wave phase of a change animation frame shown at [elapsedMs] of a motion lasting [durationMs]:
     * the wave moves at the loop's speed and reaches [STILL_PHASE] (the loop's frame 0) exactly
     * at the motion's end, so the rise or fall leads straight into the loop.
     */
    fun motionPhase(elapsedMs: Float, durationMs: Float): Float {
        val remaining = (durationMs - elapsedMs).coerceAtLeast(0f)
        if (remaining == 0f) return STILL_PHASE
        return STILL_PHASE - TWO_PI * remaining / LOOP_MS
    }
}
