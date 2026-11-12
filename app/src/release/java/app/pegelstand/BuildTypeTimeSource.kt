package app.pegelstand

import android.content.Context
import app.pegelstand.domain.TimeSource

/**
 * The app's clock for this build type. Release: always the system clock. The debug variant of
 * this file (in `src/debug`) returns a clock that adb can set to a fixed time.
 */
internal fun Context.buildTypeTimeSource(): TimeSource = TimeSource.System
