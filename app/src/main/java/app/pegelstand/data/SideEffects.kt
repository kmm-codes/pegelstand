package app.pegelstand.data

import app.pegelstand.domain.ReminderState
import java.time.Instant

// Side effects of a write, called by WaterRepository only. AppContainer wires the no-op
// implementations below until the widget and reminder implementations replace them there.

/** Re-renders every placed home-screen widget from the current data. */
fun interface WidgetRefresher {
    suspend fun refreshAll()
}

/**
 * Keeps exactly one pending alarm. Implementations schedule it at
 * [app.pegelstand.domain.nextWakeUp]`(state, now)`, replacing any previous one.
 */
fun interface ReminderScheduler {
    fun schedule(state: ReminderState, now: Instant)
}

/** Shows or removes the single reminder notification (one id, replaced, never stacked). */
interface ReminderNotifier {
    /** [glassesBehind] is already rounded and at least 1; [mlPerTap] labels the action button. */
    fun show(glassesBehind: Int, mlPerTap: Int)

    fun cancel()
}

object NoOpWidgetRefresher : WidgetRefresher {
    override suspend fun refreshAll() = Unit
}

object NoOpReminderScheduler : ReminderScheduler {
    override fun schedule(state: ReminderState, now: Instant) = Unit
}

object NoOpReminderNotifier : ReminderNotifier {
    override fun show(glassesBehind: Int, mlPerTap: Int) = Unit

    override fun cancel() = Unit
}
