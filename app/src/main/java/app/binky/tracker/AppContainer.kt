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
import app.binky.tracker.data.CareRepository
import app.binky.tracker.data.DocumentRepository
import app.binky.tracker.data.DoseAlarmScheduler
import app.binky.tracker.data.FluffleRepository
import app.binky.tracker.data.MedicationRepository
import app.binky.tracker.data.ObservationRepository
import app.binky.tracker.data.PRESERVED_DIRECTORY
import app.binky.tracker.data.PhotoRepository
import app.binky.tracker.data.SetupState
import app.binky.tracker.data.StoredSelection
import app.binky.tracker.data.SymptomRepository
import app.binky.tracker.data.VetRepository
import app.binky.tracker.data.VisitRepository
import app.binky.tracker.data.WatchRepository
import app.binky.tracker.data.WeightRepository
import app.binky.tracker.data.backup.BackupExporter
import app.binky.tracker.data.backup.BackupRestorer
import app.binky.tracker.data.backup.EXPORTS_DIRECTORY
import app.binky.tracker.data.buildBunnyDatabase
import app.binky.tracker.data.resolveSelection
import app.binky.tracker.data.resolveSetupState
import app.binky.tracker.media.MediaFiles
import app.binky.tracker.scan.CameraDocumentScanner
import app.binky.tracker.scan.DocumentScanner
import app.binky.tracker.scan.MlKitDocumentScanner
import app.binky.tracker.work.CareNotifier
import app.binky.tracker.work.ExportNotifier
import app.binky.tracker.work.WatchNotifier
import app.binky.tracker.work.rescheduleDoseAlarm
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

    /**
     * **What every write does to the one pending dose alarm** (ADR-0025).
     *
     * Declared before the repositories that take it, and deliberately routed through the no-argument
     * `rescheduleDoseAlarm()` — which resolves this container back out of the application at *call*
     * time. Passing `medicationRepository` directly would need it to exist before it is constructed;
     * resolving late costs nothing, because by the time any write happens the container plainly
     * exists. ADR-0007's guard is inside that call, so a rebuild triggered over a schema this build
     * must not open still touches nothing.
     */
    private val doseAlarms = DoseAlarmScheduler { appContext.rescheduleDoseAlarm() }

    val fluffleRepository = FluffleRepository(database)

    val bunnyRepository = BunnyRepository(database, fluffleRepository, preferences, mediaFiles, doseAlarms)

    val weightRepository = WeightRepository(database)

    val observationRepository = ObservationRepository(database)

    val symptomRepository = SymptomRepository(database)

    val photoRepository = PhotoRepository(database, mediaFiles)

    val careRepository = CareRepository(database)

    val vetRepository = VetRepository(database)

    /**
     * Takes [weightRepository] rather than the database alone: a visit's weighing is a weighing, and
     * writing it through the repository that owns ADR-0001's watermark is what keeps the trend flag
     * from going stale behind a vet's number (ADR-0017).
     */
    val visitRepository = VisitRepository(database, weightRepository)

    val watchRepository = WatchRepository(database)

    /**
     * Courses, their schedules and the doses recorded against them. What is *due* is derived on read
     * (ADR-0002), so there is no scheduler state to hold — [doseAlarms] is the whole of the coupling,
     * and it reaches the system through a `Context` this container already has.
     */
    val medicationRepository = MedicationRepository(database, doseAlarms)

    /** Scanned paperwork and the page files behind it (ADR-0017, ADR-0020). */
    val documentRepository = DocumentRepository(database, mediaFiles)

    /**
     * **The whole of ADR-0009's scanner contingency, in one line.**
     *
     * ML Kit's scanner is delivered by Play services and absent without them, so it sits behind
     * [DocumentScanner] with [CameraDocumentScanner] underneath — and if the dependency ever has to
     * go, for the merged manifest or the AAB size or the Play-services-absent path, this line
     * becomes `CameraDocumentScanner(appContext)` and nothing else in the app changes. Documents as
     * *data* bring none of those costs; only the guided UX does.
     *
     * Which path a given scan takes is resolved at use, inside `start` — see [DocumentScanner].
     */
    val documentScanner: DocumentScanner = MlKitDocumentScanner(fallback = CameraDocumentScanner(appContext))

    /**
     * Posting and cancelling care notifications, which needs a `Context` that a `ViewModel` has no
     * business holding. Both ends of 4c reach it through here: the sweep posts, and a completion on
     * the Care screen cancels.
     */
    val careNotifier = CareNotifier(appContext)

    /**
     * The watch nag's other end. Same shape and same reason as [careNotifier]: the sweep posts, and
     * an observation landing for that bunny — or the watch being closed — cancels.
     */
    val watchNotifier = WatchNotifier(appContext)

    /**
     * The export prompt's other end (ADR-0005). The odd one out of the three: it is about the app
     * rather than about a bunny, so the sweep posts it from preferences alone and an export — by
     * either path — cancels it.
     */
    val exportNotifier = ExportNotifier(appContext)

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

    /**
     * Whether first-run setup still has to run (ADR-0006) — the same resolve-on-read shape as
     * [selectedBunny] above, and for the same reason: what was stored is only half the answer.
     *
     * Archived bunnies are counted too. An owner whose only bunny is archived has plainly used the
     * app before, and being walked through "add your first bunny" after a bereavement would be the
     * worst possible time for it (ADR-0004).
     *
     * `WhileSubscribed` rather than [selectedBunny]'s `Eagerly`: only the navigation gate reads
     * this, so there is no always-readable value to keep warm — and `stateIn` holds the last value
     * across a resubscription, so returning from the background does not flash the gate's blank
     * loading state.
     */
    val setupState: StateFlow<SetupState> =
        combine(
            preferences.setupProgress,
            bunnyRepository.activeBunnies,
            bunnyRepository.archivedBunnies,
        ) { progress, active, archived ->
            resolveSetupState(progress, hasBunny = active.isNotEmpty() || archived.isNotEmpty())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SetupState.Loading)

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
