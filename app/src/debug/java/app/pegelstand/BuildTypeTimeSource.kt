package app.pegelstand

import android.content.Context
import app.pegelstand.debug.DebugClock
import app.pegelstand.domain.TimeSource

/**
 * The app's clock for this build type. Debug: the system clock unless adb froze it at a fixed
 * time through [app.pegelstand.debug.DebugReceiver]. The release variant returns the system clock.
 */
internal fun Context.buildTypeTimeSource(): TimeSource = DebugClock(this)
