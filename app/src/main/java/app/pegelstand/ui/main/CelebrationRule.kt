package app.pegelstand.ui.main

import java.time.LocalDate

/** When the "goal reached" celebration plays: at most once per logical day. */
object CelebrationRule {
    /**
     * True if today's goal is reached and the celebration has not played for [today] yet.
     * [celebratedDay] is the persisted day of the last celebration, so reopening the app, or
     * reaching the goal through the widget and opening the app later, plays it at most once.
     */
    fun shouldCelebrate(today: LocalDate, goalReached: Boolean, celebratedDay: LocalDate?): Boolean =
        goalReached && celebratedDay != today
}
