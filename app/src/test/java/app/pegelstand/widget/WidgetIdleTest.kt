package app.pegelstand.widget

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIdleTest {
    private val twoPi = (2 * PI).toFloat()

    // Pixel-class display (1080 x 2424): cap 6 x w x h bytes.
    private val cap = 6L * 1080 * 2424

    @Test
    fun `frame 0 has the static image's phase and the frames span exactly one period`() {
        for (frames in WidgetIdle.FRAME_COUNTS) {
            assertEquals(WidgetIdle.STILL_PHASE, WidgetIdle.phase(0, frames), 0f)
            val step = twoPi / frames
            for (k in 1 until frames) {
                assertEquals("even step at $k of $frames", step, WidgetIdle.phase(k, frames) - WidgetIdle.phase(k - 1, frames), 1e-5f)
            }
            // The step from the last frame back to frame 0 (one period later) is like any other.
            val wrap = WidgetIdle.phase(0, frames) + twoPi - WidgetIdle.phase(frames - 1, frames)
            assertEquals("wrap step of $frames", step, wrap, 1e-5f)
        }
    }

    @Test
    fun `fewer frames keep the loop's length`() {
        assertEquals(100, WidgetIdle.intervalMs(24))
        for (frames in WidgetIdle.FRAME_COUNTS) {
            assertEquals(WidgetIdle.LOOP_MS, WidgetIdle.intervalMs(frames) * frames)
        }
    }

    @Test
    fun `budget picks the most frames that fit next to the static images`() {
        val small = 244L * 315 * 4 // 2x2 bottle on the emulator
        val stills = 4 * small
        assertEquals(24, WidgetIdle.frameCount(cap, stills, small))

        // Larger bottles step down through the ladder, never scaled, never over the share.
        val sizes = listOf(300L * 385, 340L * 436, 399L * 512, 440L * 564, 520L * 667)
        val seen = mutableSetOf<Int?>()
        for (px in sizes) {
            val bytes = px * 4
            val count = WidgetIdle.frameCount(cap, 4 * bytes, bytes)
            seen += count
            if (count != null) {
                assertTrue(count in WidgetIdle.FRAME_COUNTS)
                assertTrue("within the share at $px px", 4 * bytes + count * bytes <= cap * WidgetMotion.BUDGET_SHARE)
                val more = WidgetIdle.FRAME_COUNTS.firstOrNull { it > count && 4 * bytes + it * bytes <= cap * WidgetMotion.BUDGET_SHARE }
                assertNull("a larger count fits at $px px", more)
            }
        }
        assertTrue("ladder exercised: $seen", seen.size >= 3)
    }

    @Test
    fun `no idle wave when not even 8 frames fit`() {
        val bytes = 600L * 770 * 4
        assertNull(WidgetIdle.frameCount(cap, 4 * bytes, bytes))
        assertNull(WidgetIdle.frameCount(0, 0, bytes))
        assertNull(WidgetIdle.frameCount(cap, 0, 0))
    }

    @Test
    fun `plan is off when the setting or the system animations are off`() {
        val bytes = 244L * 315 * 4
        assertEquals(IdlePlan.Frames(24), WidgetIdle.plan(waveSetting = true, animationsEnabled = true, cap, 4 * bytes, bytes))
        assertEquals(IdlePlan.Off, WidgetIdle.plan(waveSetting = false, animationsEnabled = true, cap, 4 * bytes, bytes))
        assertEquals(IdlePlan.Off, WidgetIdle.plan(waveSetting = true, animationsEnabled = false, cap, 4 * bytes, bytes))
        val huge = 600L * 770 * 4
        assertEquals(IdlePlan.NoRoom, WidgetIdle.plan(waveSetting = true, animationsEnabled = true, cap, 4 * huge, huge))
        // Off wins over a budget that would not fit anyway.
        assertEquals(IdlePlan.Off, WidgetIdle.plan(waveSetting = false, animationsEnabled = true, cap, 4 * huge, huge))
    }

    @Test
    fun `change animation keeps the wave moving at loop speed and ends on the loop's frame 0`() {
        val frames = WidgetMotion.MOTION_CHILDREN
        val phases = WidgetMotion.framePhases(frames)
        assertEquals(WidgetIdle.STILL_PHASE, phases.last(), 0f)
        val perTick = twoPi * WidgetMotion.TICK_MS / WidgetIdle.LOOP_MS
        for (k in 1 until frames) {
            assertEquals("loop speed at $k", perTick, phases[k] - phases[k - 1], 1e-4f)
        }
        // The first frame is one motion's worth of wave before the end, minus its own tick.
        val expectedFirst = WidgetIdle.STILL_PHASE - twoPi * (WidgetMotion.DURATION_MS - WidgetMotion.TICK_MS) / WidgetIdle.LOOP_MS
        assertEquals(expectedFirst, phases.first(), 1e-4f)
        // With fewer distinct frames the last one still has the loop's first phase.
        for (few in listOf(8, 10, 12)) assertEquals(WidgetIdle.STILL_PHASE, WidgetMotion.framePhases(few).last(), 0f)
    }

    @Test
    fun `motion phase is the still phase at and after the end`() {
        assertEquals(WidgetIdle.STILL_PHASE, WidgetIdle.motionPhase(528f, 528f), 0f)
        assertEquals(WidgetIdle.STILL_PHASE, WidgetIdle.motionPhase(600f, 528f), 0f)
        assertTrue(WidgetIdle.motionPhase(0f, 528f) < WidgetIdle.STILL_PHASE)
    }
}
