package app.pegelstand

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.pegelstand.data.ReminderNotifier
import app.pegelstand.data.ReminderScheduler
import app.pegelstand.data.SettingsStore
import app.pegelstand.data.WaterRepository
import app.pegelstand.data.WidgetRefresher
import app.pegelstand.data.db.WaterDatabase
import app.pegelstand.domain.TimeSource
import app.pegelstand.reminder.AlarmReminderScheduler
import app.pegelstand.reminder.NotificationReminderNotifier
import app.pegelstand.widget.GlanceWidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Hand-written dependency container, owned by [PegelstandApp]. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** For work that must outlive a screen or a broadcast receiver (use with goAsync). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The system clock in release builds; a settable clock in debug builds (see `src/debug`). */
    val timeSource: TimeSource = appContext.buildTypeTimeSource()

    /** Internal so debug tooling can clear and seed it; app code goes through [repository]. */
    internal val database: WaterDatabase by lazy { WaterDatabase.create(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    // Side effects of writes, called by the repository in this order.
    val widgetRefresher: WidgetRefresher = GlanceWidgetRefresher(appContext)
    val reminderNotifier: ReminderNotifier = NotificationReminderNotifier(appContext)
    val reminderScheduler: ReminderScheduler = AlarmReminderScheduler(appContext)

    val repository: WaterRepository by lazy {
        WaterRepository(
            dao = database.waterDao(),
            settingsStore = settingsStore,
            time = timeSource,
            widgets = widgetRefresher,
            notifier = reminderNotifier,
            scheduler = reminderScheduler,
        )
    }
}
