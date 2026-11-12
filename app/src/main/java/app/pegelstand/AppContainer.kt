package app.pegelstand

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.pegelstand.data.NoOpReminderNotifier
import app.pegelstand.data.NoOpReminderScheduler
import app.pegelstand.data.NoOpWidgetRefresher
import app.pegelstand.data.ReminderNotifier
import app.pegelstand.data.ReminderScheduler
import app.pegelstand.data.SettingsStore
import app.pegelstand.data.WaterRepository
import app.pegelstand.data.WidgetRefresher
import app.pegelstand.data.db.WaterDatabase
import app.pegelstand.domain.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Hand-written dependency container, owned by [PegelstandApp]. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    /** For work that must outlive a screen or a broadcast receiver (use with goAsync). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = TimeSource.System

    private val database: WaterDatabase by lazy { WaterDatabase.create(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    // Side effects of writes. The widget and reminder implementations replace these no-ops.
    val widgetRefresher: WidgetRefresher = NoOpWidgetRefresher
    val reminderNotifier: ReminderNotifier = NoOpReminderNotifier
    val reminderScheduler: ReminderScheduler = NoOpReminderScheduler

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
