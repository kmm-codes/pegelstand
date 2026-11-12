package app.pegelstand.debug

import android.content.Context
import androidx.core.content.edit
import app.pegelstand.domain.TimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Debug-only clock: the system clock, or a fixed "now" set from adb (the emulator image is not
 * rooted, so the device clock cannot be moved). The fixed time stands still until it is changed
 * or cleared, and survives process restarts so alarms and receivers see the same time.
 */
class DebugClock(context: Context) : TimeSource {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var fixedMs: Long = prefs.getLong(KEY_FIXED_MS, UNSET)

    /** The fixed time, or null while the system clock is used. */
    val fixed: Instant?
        get() = fixedMs.takeIf { it != UNSET }?.let(Instant::ofEpochMilli)

    override fun now(): Instant = fixed ?: Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    /** Freezes "now" at [instant]; null returns to the system clock. */
    fun setFixed(instant: Instant?) {
        fixedMs = instant?.toEpochMilli() ?: UNSET
        prefs.edit(commit = true) {
            if (instant == null) remove(KEY_FIXED_MS) else putLong(KEY_FIXED_MS, instant.toEpochMilli())
        }
    }

    private companion object {
        const val PREFS = "debug_clock"
        const val KEY_FIXED_MS = "fixedMs"
        const val UNSET = Long.MIN_VALUE
    }
}
