package app.pegelstand.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMotionTest {
    private val day = LookContext(epochDay = 20_000, goalMl = 2000, modeId = "FILL", colorId = "BLUE")
    private val empty = BottleLook(0f, 0f)
    private val quarter = BottleLook(0.25f, 0f)
    private val half = BottleLook(0.5f, 0f)
    private val goal = BottleLook(1f, 1f)
    private val almost = BottleLook(0.875f, 0f)

    @Test
    fun `ease starts fast, ends at rest and stays within bounds`() {
        assertEquals(0f, WidgetMotion.ease(0f), 0f)
        assertEquals(1f, WidgetMotion.ease(1f), 0f)
        assertEquals(1f, WidgetMotion.ease(2f), 0f)
        assertEquals(0f, WidgetMotion.ease(-1f), 0f)
        // Ease-out: more than half of the way after the first third of the time.
        assertTrue(WidgetMotion.ease(1f / 3f) > 0.6f)
        var previous = 0f
        for (i in 1..100) {
            val v = WidgetMotion.ease(i / 100f)
            assertTrue("monotonic at $i", v >= previous)
            previous = v
        }
    }

    @Test
    fun `last frame is exactly the target, first frame already moves`() {
        val looks = WidgetMotion.frameLooks(quarter, half, WidgetMotion.MOTION_CHILDREN)
        assertEquals(WidgetMotion.MOTION_CHILDREN, looks.size)
        assertSame(half, looks.last())
        assertTrue(looks.first().fill > quarter.fill)
        for (i in 1 until looks.size) assertTrue("rising at $i", looks[i].fill >= looks[i - 1].fill)
    }

    @Test
    fun `falling motion for undo`() {
        val looks = WidgetMotion.frameLooks(half, quarter, WidgetMotion.MOTION_CHILDREN)
        for (i in 1 until looks.size) assertTrue("falling at $i", looks[i].fill <= looks[i - 1].fill)
        assertSame(quarter, looks.last())
    }

    @Test
    fun `gold blends in over the last part of a rise into the goal`() {
        val looks = WidgetMotion.frameLooks(almost, goal, WidgetMotion.MOTION_CHILDREN)
        assertEquals(0f, looks.first().gold, 0f)
        assertTrue("half-way still mostly blue", looks[3].gold < 0.5f)
        for (i in 1 until looks.size) assertTrue("gold never decreases at $i", looks[i].gold >= looks[i - 1].gold)
        assertEquals(1f, looks.last().gold, 0f)
    }

    @Test
    fun `gold drops early when leaving the goal`() {
        val looks = WidgetMotion.frameLooks(goal, almost, WidgetMotion.MOTION_CHILDREN)
        assertTrue("gold mostly gone after a third", looks[5].gold < 0.2f)
        assertEquals(0f, looks.last().gold, 0f)
    }

    @Test
    fun `children map onto frames in order and hold the final frame`() {
        val frames = WidgetMotion.MOTION_CHILDREN
        for (child in 0 until WidgetMotion.MOTION_CHILDREN) assertEquals(child, WidgetMotion.frameForChild(child, frames))
        for (child in WidgetMotion.MOTION_CHILDREN until WidgetMotion.CHILDREN) {
            assertEquals(frames - 1, WidgetMotion.frameForChild(child, frames))
        }
        // With fewer images each one is shown for one or two ticks, never out of order.
        val few = 10
        var previous = 0
        for (child in 0 until WidgetMotion.CHILDREN) {
            val frame = WidgetMotion.frameForChild(child, few)
            assertTrue(frame in previous..previous + 1)
            previous = frame
        }
        assertEquals(few - 1, WidgetMotion.frameForChild(WidgetMotion.MOTION_CHILDREN - 1, few))
    }

    @Test
    fun `a change on the same day animates from the shown level`() {
        val shown = Shown(day, quarter, motion = null)
        assertEquals(WidgetChange.Animate(quarter, half), WidgetMotion.decide(shown, day, half, nowMs = 1_000, animationsEnabled = true))
    }

    @Test
    fun `day rollover, goal, direction or colour change is static`() {
        val shown = Shown(day, half, motion = null)
        val changes = listOf(
            day.copy(epochDay = day.epochDay + 1),
            day.copy(goalMl = 2500),
            day.copy(modeId = "DRAIN"),
            day.copy(colorId = "MINT"),
        )
        for (context in changes) {
            assertEquals(context.toString(), WidgetChange.Static, WidgetMotion.decide(shown, context, quarter, 1_000, true))
        }
    }

    @Test
    fun `unknown previous look, unchanged level or disabled animations are static`() {
        assertEquals(WidgetChange.Static, WidgetMotion.decide(null, day, half, 1_000, true))
        assertEquals(WidgetChange.Static, WidgetMotion.decide(Shown(day, half, null), day, BottleLook(0.50001f, 0f), 1_000, true))
        assertEquals(WidgetChange.Static, WidgetMotion.decide(Shown(day, quarter, null), day, half, 1_000, animationsEnabled = false))
    }

    @Test
    fun `retarget mid-motion starts from the frame visible now`() {
        val start = 10_000L
        val motion = Motion(start, quarter, half, WidgetMotion.DURATION_MS)
        val shown = Shown(day, half, motion)
        val now = start + 5 * WidgetMotion.TICK_MS + 10 // child 5 is showing
        val visible = WidgetMotion.lookAt(quarter, half, WidgetMotion.childProgress(5))
        val change = WidgetMotion.decide(shown, day, BottleLook(0.75f, 0f), now, true)
        assertEquals(WidgetChange.Animate(visible, BottleLook(0.75f, 0f)), change)
        assertTrue(visible.fill > quarter.fill && visible.fill < half.fill)
    }

    @Test
    fun `retarget looks ahead by the time the new frames need to reach the screen`() {
        val start = 10_000L
        val shown = Shown(day, half, Motion(start, quarter, half, WidgetMotion.DURATION_MS))
        val now = start + 2 * WidgetMotion.TICK_MS
        val plain = WidgetMotion.decide(shown, day, BottleLook(0.75f, 0f), now, true) as WidgetChange.Animate
        val ahead = WidgetMotion.decide(shown, day, BottleLook(0.75f, 0f), now, true, leadMs = 3 * WidgetMotion.TICK_MS) as WidgetChange.Animate
        assertEquals(WidgetMotion.lookAt(quarter, half, WidgetMotion.childProgress(5)), ahead.from)
        assertTrue(ahead.from.fill > plain.from.fill)
        // The lead is capped, and past the motion's end it is the final look.
        val capped = WidgetMotion.decide(shown, day, BottleLook(0.75f, 0f), now, true, leadMs = 10_000) as WidgetChange.Animate
        assertEquals(WidgetMotion.lookAt(Motion(start, quarter, half, WidgetMotion.DURATION_MS), now + WidgetMotion.MAX_LEAD_MS), capped.from)
    }

    @Test
    fun `tap then undo mid-motion falls back from the visible level`() {
        val start = 10_000L
        val shown = Shown(day, half, Motion(start, quarter, half, WidgetMotion.DURATION_MS))
        val change = WidgetMotion.decide(shown, day, quarter, start + 2 * WidgetMotion.TICK_MS, true)
        change as WidgetChange.Animate
        assertTrue(change.from.fill > quarter.fill && change.from.fill < half.fill)
        assertEquals(quarter, change.to)
    }

    @Test
    fun `finished or clock-reset motion counts as its final look`() {
        val start = 10_000L
        val motion = Motion(start, quarter, half, WidgetMotion.DURATION_MS)
        assertSame(half, WidgetMotion.lookAt(motion, start + WidgetMotion.DURATION_MS))
        assertSame(half, WidgetMotion.lookAt(motion, start - 1))
        // A record left from a process that died mid-motion: same target again is static, no replay.
        assertEquals(WidgetChange.Static, WidgetMotion.decide(Shown(day, half, motion), day, half, start + 60_000, true))
    }

    @Test
    fun `a running motion to the same target needs no new update`() {
        val motion = Motion(0, quarter, half, WidgetMotion.DURATION_MS)
        assertTrue(WidgetMotion.alreadyHeadingThere(Shown(day, half, motion), day, half))
        assertTrue(!WidgetMotion.alreadyHeadingThere(Shown(day, half, motion), day, quarter))
        assertTrue(!WidgetMotion.alreadyHeadingThere(Shown(day, half, motion), day.copy(colorId = "PINK"), half))
        assertTrue(!WidgetMotion.alreadyHeadingThere(Shown(day, half, null), day, half))
    }

    @Test
    fun `closing update lands inside the hold`() {
        val stop = WidgetMotion.stopDelayMs()
        // Never before the motion has ended (the closing update would cut it short).
        assertTrue(stop >= WidgetMotion.DURATION_MS)
        // The closing update carries the idle wave and was measured to take up to ~400 ms to show
        // in the launcher: the hold leaves at least twice that after the closing update is sent.
        assertTrue(WidgetMotion.CHILDREN * WidgetMotion.TICK_MS - stop >= 800)
    }

    @Test
    fun `budget prefers all frames at full size`() {
        // Pixel-class display: cap 6 x 1080 x 2424, a 300 x 385 px bottle.
        val cap = 6L * 1080 * 2424
        val budget = WidgetMotion.frameBudget(cap, 300L * 385 * 4)
        assertEquals(FrameBudget(WidgetMotion.MOTION_CHILDREN, 1f), budget)
    }

    @Test
    fun `budget scales in-between frames, then drops frames, then gives up`() {
        val cap = 6L * 720 * 1280
        val final = 400L * 520 * 4
        val scaled = requireNotNull(WidgetMotion.frameBudget(cap, final))
        val total = final + (scaled.frames - 1) * final * scaled.scale * scaled.scale
        assertTrue("within budget: $scaled", total <= cap * WidgetMotion.BUDGET_SHARE)
        assertTrue(scaled.scale < 1f)

        val tight = WidgetMotion.frameBudget(6L * 480 * 800, 300L * 385 * 4)
        if (tight != null) {
            assertTrue(tight.frames >= WidgetMotion.MIN_FRAMES)
            assertTrue(tight.scale >= WidgetMotion.MIN_SCALE)
            assertTrue(300L * 385 * 4 * (1 + (tight.frames - 1) * tight.scale * tight.scale) <= 6L * 480 * 800 * WidgetMotion.BUDGET_SHARE)
        }
        assertNull(WidgetMotion.frameBudget(6L * 200 * 200, 300L * 385 * 4))
    }

    @Test
    fun `record round-trips and garbage is ignored`() {
        val still = Shown(day, half, null)
        assertEquals(still, WidgetMotion.decode(WidgetMotion.encode(still)))
        val moving = Shown(day.copy(modeId = "DRAIN"), goal, Motion(123_456_789L, almost, goal, WidgetMotion.DURATION_MS))
        assertEquals(moving, WidgetMotion.decode(WidgetMotion.encode(moving)))
        assertNull(WidgetMotion.decode(null))
        assertNull(WidgetMotion.decode(""))
        assertNull(WidgetMotion.decode("v1;x;2000;FILL;BLUE;0.5;0"))
        assertNull(WidgetMotion.decode("v0;20000;2000;FILL;BLUE;0.5;0"))
    }
}

