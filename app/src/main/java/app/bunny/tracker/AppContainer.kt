package app.bunny.tracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.bunny.tracker.data.AppPreferences
import app.bunny.tracker.data.BUNNY_DATABASE_FILE
import app.bunny.tracker.data.BunnyDatabase
import app.bunny.tracker.data.BunnyRepository
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.data.FluffleRepository
import app.bunny.tracker.data.PRESERVED_DIRECTORY
import app.bunny.tracker.data.StoredSelection
import app.bunny.tracker.data.preserveBeforeWipe
import app.bunny.tracker.data.resolveSelection
import app.bunny.tracker.media.MediaFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "bunny_preferences")

/**
 * Manual dependency injection — deliberately not Hilt. At roughly fifteen screens, constructing
 * things by hand is clearer than generated graphs, and migrating later is mechanical.
 *
 * Lives at the package root with the app shell: like `Navigation.kt`, it describes how the app
 * hangs together rather than belonging to any one screen.
 */
class AppContainer(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val appContext = context.applicationContext

    /**
     * Declaration order is load-bearing: this runs **before** [database] below is built, because
     * once Room opens a file whose schema has moved it is already too late (ADR-0007). Null when
     * there was nothing to preserve, which is the ordinary case.
     *
     * Phase 2 puts a blocking screen in front of this; Phase 1 keeps the copy without the consent.
     */
    val preservedDatabase: File? =
        preserveBeforeWipe(
            databaseFile = appContext.getDatabasePath(BUNNY_DATABASE_FILE),
            preservedDir = File(appContext.filesDir, PRESERVED_DIRECTORY),
        )

    private val database: BunnyDatabase =
        Room
            .databaseBuilder(appContext, BunnyDatabase::class.java, BUNNY_DATABASE_FILE)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    val preferences = AppPreferences(appContext.preferencesStore)

    /** The single path for persisting images (house rule, ADR-0020). */
    val mediaFiles = MediaFiles(appContext)

    val fluffleRepository = FluffleRepository(database)

    val bunnyRepository = BunnyRepository(database, fluffleRepository, preferences)

    /**
     * The read-only scope onto an archived bunny. In memory only — a background kill must not
     * reopen the app into a memorial (ADR-0015).
     */
    private val archivedScope = MutableStateFlow<String?>(null)

    /**
     * Which bunny the per-bunny screens are scoped to. App-wide state, not per-screen.
     *
     * Kotlin note: `combine` re-runs its block whenever *any* input emits — closer to a reactive
     * `combineLatest` than to `Promise.all`, which settles once. `stateIn` turns that cold `Flow`
     * into a hot `StateFlow` with an always-readable current value, which is what a Compose screen
     * needs. It is `Loading` only until the first database and preferences emissions arrive.
     */
    val selectedBunny: StateFlow<BunnySelection> =
        combine(
            preferences.selection,
            bunnyRepository.activeBunnies.map { bunnies -> bunnies.map { it.id } },
            archivedScope,
        ) { stored, activeIds, archived -> resolveSelection(stored, activeIds, archived) }
            .stateIn(scope, SharingStarted.Eagerly, BunnySelection.Loading)

    suspend fun select(bunnyId: String) = preferences.setSelection(StoredSelection.Bunny(bunnyId))

    suspend fun selectAllBunnies() = preferences.setSelection(StoredSelection.All)

    /** Entered only from the archived list, and never persisted. */
    fun openArchived(bunnyId: String) {
        archivedScope.value = bunnyId
    }

    fun closeArchived() {
        archivedScope.value = null
    }
}
