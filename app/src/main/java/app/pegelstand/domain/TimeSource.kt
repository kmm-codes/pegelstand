package app.pegelstand.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Source of "now" for the data layer. The zone is read on every call (unlike a fixed
 * [java.time.Clock]) so a time-zone change is picked up without restarting the process.
 * The domain functions never call this themselves; they take the instant and zone as parameters.
 */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId

    object System : TimeSource {
        override fun now(): Instant = Instant.now()
        override fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
