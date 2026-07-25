package app.bunny.tracker

import android.app.Application

/**
 * Holds the one [AppContainer] for the process. Registered via `android:name` in the manifest;
 * screens reach it through their `viewModelFactory`.
 */
class BunnyTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
