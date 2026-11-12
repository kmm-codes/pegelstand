package app.pegelstand.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.edit
import app.pegelstand.LOG_TAG
import app.pegelstand.data.TodayState
import app.pegelstand.data.WidgetRefresher
import app.pegelstand.ui.bottle.BottleRenderer
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only sender of widget updates. A change of today's level animates (see [WidgetMotion]):
 * a start update carries a fresh flipper with the frames, which the launcher plays by itself, and
 * a closing update [WidgetMotion.stopDelayMs] later re-reads the real state, shows it statically
 * and removes the flipper. Everything else (placement, resize, day rollover, colour, goal or
 * direction change, process start, theme switch) is a static update.
 *
 * Every static update carries the static image and, on top, the idle wave (see [WidgetIdle]):
 * a looping flipper the launcher plays by itself. The closing update's idle frames are rendered
 * while the level animation plays, so it still lands inside the animation's hold.
 *
 * Sends are serialised by a mutex. Each change bumps a generation; only the newest generation
 * may send its closing update, so rapid taps retarget from the level visible at that moment. A
 * closing update whose wait is cancelled for any other reason is still sent. What each widget
 * shows is remembered per widget id, in memory and in a small preferences file, so a tap that
 * starts the process again still animates from the right level.
 *
 * Never throws: failures are logged. A failed start update falls back to a static one at once; a
 * static update that is rejected with its idle wave is sent again without it.
 */
class WidgetAnimator(
    context: Context,
    private val scope: CoroutineScope,
    private val today: suspend () -> TodayState,
) : WidgetRefresher {
    private val appContext: Context = context.applicationContext
    private val views = WidgetViews(appContext)
    private val manager: AppWidgetManager by lazy { AppWidgetManager.getInstance(appContext) }
    private val records: SharedPreferences by lazy { appContext.getSharedPreferences(RECORDS, Context.MODE_PRIVATE) }

    private val mutex = Mutex()

    @Volatile
    private var generation = 0L

    // Guarded by mutex.
    private var closing: Job? = null
    private val shown = HashMap<Int, Shown>()

    /** Running estimate of rendering plus sending a start update; the lead for retargeting. */
    private var startCostMs = INITIAL_START_COST_MS

    /** After a write or a theme switch: animate a level change, otherwise update statically. */
    override suspend fun refreshAll() = guarded("Widget refresh") { change() }

    /** Static update of [ids] (placed, resized, launcher asked): never animates. */
    suspend fun refreshStatic(ids: IntArray) = guarded("Static widget update") {
        mutex.withLock {
            val state = today()
            ids.forEach { sendStatic(it, state) }
        }
    }

    /** Forgets removed widgets. */
    fun forget(ids: IntArray) {
        scope.launch {
            guarded("Forgetting widgets") {
                mutex.withLock {
                    records.edit { ids.forEach { id -> shown.remove(id); remove(key(id)) } }
                }
            }
        }
    }

    private suspend fun change() {
        mutex.withLock {
            val ids = manager.getAppWidgetIds(ComponentName(appContext, PegelWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            ids.forEach(::load)
            val state = today()
            val context = state.lookContext()
            val target = state.look()
            // A second refresh for the same write (e.g. a debug action refreshing again) must not
            // cut the running animation short.
            val running = closing?.isActive == true
            if (running && ids.all { WidgetMotion.alreadyHeadingThere(shown[it], context, target) }) return

            val gen = ++generation
            closing?.cancel()
            closing = null
            val animationsEnabled = animationsEnabled()
            val now = SystemClock.elapsedRealtime()
            val started = ArrayList<Int>(ids.size)
            for (id in ids) {
                val change = WidgetMotion.decide(shown[id], context, target, now, animationsEnabled, startCostMs)
                if (change is WidgetChange.Animate && sendStart(id, state, change)) {
                    started += id
                } else {
                    sendStatic(id, state)
                }
            }
            if (started.isNotEmpty()) {
                val startedAt = SystemClock.elapsedRealtime()
                closing = scope.launch { close(gen, started, state, startedAt) }
            }
        }
    }

    /**
     * Renders the closing update for the [expected] final state while the motion plays, waits until
     * [WidgetMotion.stopDelayMs] after [startedAt], then sends it (see [sendClosing]).
     */
    private suspend fun close(gen: Long, ids: List<Int>, expected: TodayState, startedAt: Long) {
        var prepared: Map<Int, StaticUpdate> = emptyMap()
        try {
            prepared = ids.mapNotNull { id -> prepare(id, expected)?.let { id to it } }.toMap()
            delay((startedAt + WidgetMotion.stopDelayMs() - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        } catch (e: CancellationException) {
            // Superseded by a newer change: that one closes. Any other cancellation: close anyway.
            if (generation == gen) withContext(NonCancellable) { sendClosing(gen, ids, prepared) }
            throw e
        }
        sendClosing(gen, ids, prepared)
    }

    /** The closing update: re-reads the real state; a prepared update is used only if it still matches. */
    private suspend fun sendClosing(gen: Long, ids: List<Int>, prepared: Map<Int, StaticUpdate>) = guarded("Closing widget update") {
        mutex.withLock {
            if (generation != gen) return@withLock
            val state = today()
            ids.forEach { sendStatic(it, state, prepared[it]) }
            Log.d(LOG_TAG, "widget stop gen=$gen ids=${ids.joinToString()}")
        }
    }

    /** Renders the frames and sends the start update. False if the frames do not fit: send static. */
    private suspend fun sendStart(id: Int, state: TodayState, change: WidgetChange.Animate): Boolean {
        val sizes = WidgetViews.sizesOf(manager, id)
        val layout = views.layout(state, WidgetViews.currentSize(sizes, landscape()))
        val finalSize = views.bottlePixels(layout)
        val budget = WidgetMotion.frameBudget(bitmapCapBytes(), finalSize.bytes)
        if (budget == null) {
            Log.w(LOG_TAG, "Widget $id: frames do not fit the bitmap budget, static update")
            return false
        }
        val renderStart = SystemClock.elapsedRealtime()
        val dark = views.isNight()
        val frames = withContext(Dispatchers.Default) {
            val renderer = BottleRenderer()
            val looks = WidgetMotion.frameLooks(change.from, change.to, budget.frames)
            val phases = WidgetMotion.framePhases(budget.frames)
            val between = PixelSize(
                (finalSize.width * budget.scale).roundToInt().coerceAtLeast(1),
                (finalSize.height * budget.scale).roundToInt().coerceAtLeast(1),
            )
            val images: List<Bitmap> = looks.mapIndexed { index, look ->
                // The last frame is full size at the loop's first phase: pixel for pixel the static
                // image that follows, and the idle wave's frame 0.
                views.render(renderer, if (index == looks.lastIndex) finalSize else between, state, look, dark, phases[index])
            }
            List(WidgetMotion.CHILDREN) { child -> images[WidgetMotion.frameForChild(child, budget.frames)] }
        }
        val remoteViews = views.animated(id, state, sizes, frames)
        try {
            manager.updateAppWidget(id, remoteViews)
        } catch (e: IllegalArgumentException) {
            // Over the launcher's bitmap cap after all: the cached views are gone, go static now.
            Log.w(LOG_TAG, "Widget $id: start update rejected, static update", e)
            return false
        }
        val sentAt = SystemClock.elapsedRealtime()
        startCostMs = (startCostMs * 3 + (sentAt - renderStart)) / 4
        Log.d(
            LOG_TAG,
            "widget start id=$id gen=$generation ${change.from} -> ${change.to} frames=${budget.frames} " +
                "scale=${budget.scale} px=${finalSize.width}x${finalSize.height} render+send=${sentAt - renderStart}ms",
        )
        record(id, Shown(state.lookContext(), change.to, Motion(sentAt, change.from, change.to, WidgetMotion.DURATION_MS)))
        return true
    }

    /**
     * Sends the static state of [state]: [prepared] if it was built for exactly this state, size,
     * orientation, night mode and animation setting, else a freshly built one. If the launcher
     * rejects it (bitmap cap), the static image goes out again without the idle wave.
     */
    private suspend fun sendStatic(id: Int, state: TodayState, prepared: StaticUpdate? = null) {
        try {
            val key = staticKey(id, state)
            val update = prepared?.takeIf { it.key == key } ?: build(id, key)
            try {
                manager.updateAppWidget(id, update.views)
            } catch (e: RuntimeException) {
                if (update.idleFrames == 0) throw e
                Log.w(LOG_TAG, "Widget $id: static update with idle wave rejected, without it", e)
                manager.updateAppWidget(id, views.still(id, state, key.sizes, idle = null))
            }
            val sizes = update.key.sizes.joinToString(",") { "${it.width.roundToInt()}x${it.height.roundToInt()}" }
            Log.d(
                LOG_TAG,
                "widget static id=$id idle=${update.idleFrames} frame=${update.frameSize} sizes=$sizes " +
                    "bytes=${update.bytes} (still ${update.stillBytes}) build=${update.buildMs}ms " +
                    "prepared=${update === prepared}",
            )
            record(id, Shown(state.lookContext(), state.look(), motion = null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Widget $id: static update failed", e)
        }
    }

    /** Builds the closing update ahead of time; null if that fails (it is then built when sent). */
    private suspend fun prepare(id: Int, state: TodayState): StaticUpdate? = try {
        build(id, staticKey(id, state))
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Log.w(LOG_TAG, "Widget $id: preparing the closing update failed", e)
        null
    }

    /** What a static update depends on besides the widget id. */
    private data class StaticKey(
        val state: TodayState,
        val sizes: List<SizeDp>,
        val landscape: Boolean,
        val dark: Boolean,
        val animationsEnabled: Boolean,
    )

    /**
     * A built static update: [idleFrames] 0 = no idle wave, else of [frameSize] pixels; [bytes] = all
     * its bitmaps, [stillBytes] of them the static images.
     */
    private class StaticUpdate(
        val key: StaticKey,
        val views: RemoteViews,
        val idleFrames: Int,
        val frameSize: String,
        val bytes: Long,
        val stillBytes: Long,
        val buildMs: Long,
    )

    private fun staticKey(id: Int, state: TodayState) =
        StaticKey(state, WidgetViews.sizesOf(manager, id), landscape(), views.isNight(), animationsEnabled())

    private suspend fun build(id: Int, key: StaticKey): StaticUpdate {
        val start = SystemClock.elapsedRealtime()
        val stillBytes = views.stillBytes(key.state, key.sizes)
        val idle = idleFrames(id, key, stillBytes)
        val frameBytes = idle?.bitmaps?.distinct()?.sumOf { it.allocationByteCount.toLong() } ?: 0L
        val remoteViews = withContext(Dispatchers.Default) { views.still(id, key.state, key.sizes, idle) }
        val frameSize = idle?.bitmaps?.first()?.let { "${it.width}x${it.height}" } ?: "-"
        return StaticUpdate(
            key, remoteViews, idle?.bitmaps?.size ?: 0, frameSize, stillBytes + frameBytes, stillBytes,
            SystemClock.elapsedRealtime() - start,
        )
    }

    /**
     * The idle wave for [key]: frames for the widget's current size and night mode, as many as the
     * bitmap budget allows next to the static images ([stillBytes]). Null if it is switched off, if
     * not even the fewest frames fit, or if the surface is flat (empty or full bottle), where every
     * frame would be the same.
     */
    private suspend fun idleFrames(id: Int, key: StaticKey, stillBytes: Long): IdleFrames? {
        val size = views.bottlePixels(views.layout(key.state, WidgetViews.currentSize(key.sizes, key.landscape)))
        val count = when (val plan = WidgetIdle.plan(key.state.widgetIdleWave, key.animationsEnabled, bitmapCapBytes(), stillBytes, size.bytes)) {
            IdlePlan.Off -> return null
            IdlePlan.NoRoom -> {
                Log.w(LOG_TAG, "Widget $id: idle wave does not fit the bitmap budget (${size.width}x${size.height} px), none")
                return null
            }
            is IdlePlan.Frames -> plan.count
        }
        return withContext(Dispatchers.Default) {
            val renderer = BottleRenderer()
            val look = key.state.look()
            fun frame(k: Int) = views.render(renderer, size, key.state, look, key.dark, WidgetIdle.phase(k, count))
            val half = count / 2
            val first = frame(0)
            val opposite = frame(half)
            // Half a period apart every wave crest has become a trough; equal pixels mean a flat surface.
            if (first.sameAs(opposite)) {
                Log.d(LOG_TAG, "widget $id: flat surface, no idle wave")
                return@withContext null
            }
            val bitmaps = List(count) { k ->
                ensureActive()
                when (k) {
                    0 -> first
                    half -> opposite
                    else -> frame(k)
                }
            }
            IdleFrames(bitmaps, WidgetIdle.intervalMs(count), key.dark)
        }
    }

    private fun load(id: Int) {
        if (id !in shown) WidgetMotion.decode(records.getString(key(id), null))?.let { shown[id] = it }
    }

    private fun record(id: Int, value: Shown) {
        shown[id] = value
        records.edit { putString(key(id), WidgetMotion.encode(value)) }
    }

    private fun landscape(): Boolean = appContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** The launcher's cap for the bitmaps of one update: 6 x display width x height bytes. */
    private fun bitmapCapBytes(): Long {
        // App display metrics never exceed the real display, so this errs on the safe side.
        val metrics = appContext.resources.displayMetrics
        return 6L * metrics.widthPixels * metrics.heightPixels
    }

    /** Respects "remove animations" (animator duration scale 0). */
    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "$what failed", e)
        }
    }

    private companion object {
        const val RECORDS = "widget_shown"

        /** First guess for [startCostMs] (rendering 16 frames and sending them, on a phone). */
        const val INITIAL_START_COST_MS = 80L

        fun key(id: Int) = "widget_$id"

        fun TodayState.look() = BottleLook(shownFill, if (goalReached) 1f else 0f)

        fun TodayState.lookContext() = LookContext(day.toEpochDay(), goalMl, bottleMode.id, waterColor.id)
    }
}
