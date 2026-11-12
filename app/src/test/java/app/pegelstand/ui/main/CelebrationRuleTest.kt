package app.pegelstand.ui.main

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationRuleTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `celebrates when the goal is reached and today was not celebrated`() {
        assertTrue(CelebrationRule.shouldCelebrate(today, goalReached = true, celebratedDay = null))
        assertTrue(CelebrationRule.shouldCelebrate(today, goalReached = true, celebratedDay = today.minusDays(1)))
    }

    @Test
    fun `never twice on the same logical day`() {
        assertFalse(CelebrationRule.shouldCelebrate(today, goalReached = true, celebratedDay = today))
    }

    @Test
    fun `not before the goal is reached`() {
        assertFalse(CelebrationRule.shouldCelebrate(today, goalReached = false, celebratedDay = null))
        assertFalse(CelebrationRule.shouldCelebrate(today, goalReached = false, celebratedDay = today.minusDays(1)))
    }

    @Test
    fun `a new day may celebrate again`() {
        assertTrue(CelebrationRule.shouldCelebrate(today.plusDays(1), goalReached = true, celebratedDay = today))
    }
}
