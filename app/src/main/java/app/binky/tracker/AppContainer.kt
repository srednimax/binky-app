package app.binky.tracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.BunnyDatabase
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.FluffleRepository
import app.binky.tracker.data.ObservationRepository
import app.binky.tracker.data.PRESERVED_DIRECTORY
import app.binky.tracker.data.PhotoRepository
import app.binky.tracker.data.StoredSelection
import app.binky.tracker.data.SymptomRepository
import app.binky.tracker.data.WeightRepository
import app.binky.tracker.data.backup.BackupExporter
import app.binky.tracker.data.backup.BackupRestorer
import app.binky.tracker.data.backup.EXPORTS_DIRECTORY
import app.binky.tracker.data.buildBunnyDatabase
import app.binky.tracker.data.resolveSelection
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
    databaseName: String = BUNNY_DATABASE_FILE,
) {
    private val appContext = context.applicationContext

    /**
     * **Constructing this does not open the file.** Room's `build()` only assembles the object; the
     * file is touched on the first query, or by [openDatabase] below. That is what lets ADR-0007's
     * consent screen block in front of a whole `AppContainer` that has not yet destroyed anything —
     * and `BinkyApplication` keeps the container behind a `lazy` so that even this much does
     * not happen before consent.
     *
     * The preserve-and-check half lives in `Application.onCreate`, deliberately not here: a guard
     * that depended on nothing *collecting* would be one eager `stateIn` away from silently
     * breaking, and [selectedBunny] below is exactly that eager `stateIn`.
     */
    private val database: BunnyDatabase = buildBunnyDatabase(appContext, databaseName)

    /**
     * Opens the database file for real, destroying it first if this build's schema has moved on.
     *
     * Called on consent, so the destruction the screen just described happens while the owner is
     * still looking at the screen — rather than at whatever unpredictable later moment some flow
     * first collects (ADR-0007).
     *
     * Kotlin note: `suspend` + `withContext(Dispatchers.IO)` is how blocking work moves off the
     * caller's thread. Unlike an `async` function in JS, a `suspend` function does not pick its own
     * thread — the dispatcher does, and without this one the open would run wherever it was called.
     */
    suspend fun openDatabase() {
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase }
    }

    val preferences = AppPreferences(appContext.preferencesStore)

    /**
     * Where ADR-0007's pre-wipe copies land. Settings lists them, shares them off the phone and
     * deletes them; `BinkyApplication` writes them before this container exists, so the two
     * agree on the path through [PRESERVED_DIRECTORY] rather than by both hardcoding it.
     */
    val preservedDir: File = File(appContext.filesDir, PRESERVED_DIRECTORY)

    /**
     * The app's private files root — where the media directories, `preserved/` and Auto Backup's
     * marker live. Held here so a screen can read the marker without a `Context` of its own.
     */
    val filesDir: File = appContext.filesDir

    /**
     * Scratch space. Whatever lands here is disposable by definition — the debug fixture's generated
     * images go through the media pipeline like any other source and their originals are then rubbish.
     */
    val cacheDir: File = appContext.cacheDir

    /**
     * Where an export lands on its way to the share sheet. **Cache**, deliberately: a share the owner
     * abandons is reclaimed by the OS rather than doubling the app's footprint (ADR-0005).
     */
    val exportsDir: File = File(appContext.cacheDir, EXPORTS_DIRECTORY)

    /** The single path for persisting images (house rule, ADR-0020). */
    val mediaFiles = MediaFiles(appContext)

    /**
     * Manual export (ADR-0005). Built from paths rather than from this container, because 3e's
     * backup agent needs the same pieces and cannot reach a container at all.
     */
    val backupExporter =
        BackupExporter(
            databaseFile = appContext.getDatabasePath(databaseName),
            filesDir = appContext.filesDir,
            scratchDir = appContext.cacheDir,
        )

    /** Restore, which is the most destructive thing the app does — see [BackupRestorer]. */
    val backupRestorer =
        BackupRestorer(
            context = appContext,
            filesDir = appContext.filesDir,
            preservedDir = preservedDir,
            scratchDir = appContext.cacheDir,
            exporter = backupExporter,
            databaseName = databaseName,
        )

    val fluffleRepository = FluffleRepository(database)

    val bunnyRepository = BunnyRepository(database, fluffleRepository, preferences, mediaFiles)

    val weightRepository = WeightRepository(database)

    val observationRepository = ObservationRepository(database)

    val symptomRepository = SymptomRepository(database)

    val photoRepository = PhotoRepository(database, mediaFiles)

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
