package app.pegelstand

import android.app.Application
import android.content.Context

class PegelstandApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The [AppContainer] from any context of this app (activity, receiver, widget). */
val Context.appContainer: AppContainer
    get() = (applicationContext as PegelstandApp).container
