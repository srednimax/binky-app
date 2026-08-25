package app.binky.tracker

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.PRESERVED_DIRECTORY
import app.binky.tracker.data.SchemaGate
import app.binky.tracker.data.ThemeMode
import app.binky.tracker.data.backup.adoptRestoredDatabase
import app.binky.tracker.data.destructiveMigrationAllowed
import app.binky.tracker.data.preserveBeforeWipe
import app.binky.tracker.data.readUserVersion
import app.binky.tracker.data.schemaGateDecision
import app.binky.tracker.theme.applyThemeMode
import app.binky.tracker.work.ensureSweepEnqueued
import app.binky.tracker.work.postBackupExclusionNoticeIfDue
import app.binky.tracker.work.rescheduleDoseAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A database this build cannot open as it stands. The copy is already taken and the original is
 * still on disk untouched; what happens next depends on which build found it (ADR-0023).
 *
 * @param preservedCopy where the copy landed, under `filesDir`.
 * @param fromVersion the schema version the file on disk was written at.
 * @param toVersion the version this build expects.
 * @param wipeOnConsent whether continuing would destroy the file and let the app through. True in a
 *   debug build, where a schema bump is still free; false in a release build, where the open would
 *   throw instead — so the screen is a dead end offering the copy, not a button that destroys a
 *   bunny's history on a path where nothing was going to destroy it.
 */
data class SchemaMismatch(
    val preservedCopy: File,
    val fromVersion: Int,
    val toVersion: Int,
    val wipeOnConsent: Boolean,
)

/**
 * Holds the one [AppContainer] for the process, and **the wipe guard that stands in front of it**.
 *
 * ADR-0007's guard is *structural*, and this class is the structure. The pre-Room version check and
 * the copy-aside run in [onCreate] — four bytes out of a file header, no Room and no container
 * involved — and [container] sits behind a `lazy` that is forced only once any pending wipe has
 * been consented to. No Room object exists, so no collection of one can exist, and the property
 * stays true however the container grows later.
 *
 * The tempting alternative — leave the container constructed and merely stop it from *collecting* —
 * works today and is one eager `stateIn` away from silently breaking, in an app that goes on to add
 * reminder rescheduling at process start. A guard by absence-of-subscription is unwritten and
 * unenforceable, and it would be load-bearing for the only copy of unretypeable data.
 *
 * Kotlin note: `by lazy` computes on first read and caches — like a memoised getter, but here the
 * *first read* is the event that matters, which is why nothing may touch [container] before consent.
 * (This is not the decorative `database by lazy`: this lazy **is** the gate.)
 */
class BinkyApplication :
    Application(),
    Configuration.Provider {
    /**
     * The one [AppPreferences] for the process, held **in front of the gate** and handed to the
     * container rather than created by it.
     *
     * The theme has to know whether Material You is on (ADR-0027) before a single frame is drawn —
     * and the first frame may be the schema-mismatch screen, which exists precisely because
     * [container] must not be touched yet. Reading `container.preferences` there would force the
     * `lazy` below, which *is* ADR-0007's guard. Preferences are a small key-value file and no Room
     * object at all, so reading one in front of the guard destroys nothing and proves nothing wrong.
     */
    val preferences: AppPreferences by lazy { AppPreferences(preferencesStore) }

    val container: AppContainer by lazy { AppContainer(this, preferences = preferences) }

    /**
     * The light/dark override as it stood when the process started, read once in [onCreate] and kept
     * so nothing has to read it off disk twice.
     *
     * `MainActivity` collects the live flow — the owner can change this while the app is open — and
     * uses this as the flow's *initial* value, which is the only way the first composition can be
     * the right colour. `lateinit` rather than a `by lazy`, because the read has to happen at a
     * known moment: before [applyThemeMode], before any Activity, and in front of ADR-0007's gate.
     */
    lateinit var startupThemeMode: ThemeMode
        private set

    /**
     * WorkManager's configuration, supplied **on demand**: the manifest removes its `androidx.startup`
     * initializer, so nothing exists until the first `WorkManager.getInstance` call and this property
     * is what that call builds from.
     *
     * The default initializer runs inside a `ContentProvider`, *before* `Application.onCreate` — and
     * `onCreate` below is where the wipe guard lives. Initialisation order between "this database may
     * be about to be destroyed" and "background work may start touching it" should be a decision, not
     * whatever the merged manifest happens to produce.
     *
     * Kotlin note: `Configuration.Provider` declares a `val`, not a getter method, so this is an
     * `override val` with an initialiser rather than a function — the property *is* the override.
     */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
                .build()

    private val _schemaMismatch = MutableStateFlow<SchemaMismatch?>(null)

    /** Non-null while the blocking screen must be shown instead of the app. */
    val schemaMismatch: StateFlow<SchemaMismatch?> = _schemaMismatch.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // **First, and before any Activity exists.** AppCompat does not persist a night mode the
        // way it persists a language, so every cold start comes up following the phone until this
        // line runs — and the window background is painted from the theme before Compose composes,
        // so "until this line runs" would be a visible flash if it ran any later. The read blocks;
        // `AppPreferences.themeModeAtStartup` explains why that is the cheaper of the two mistakes,
        // and it is a fraction of what the database work below already does on this thread.
        startupThemeMode = preferences.themeModeAtStartup()
        applyThemeMode(startupThemeMode)

        val databaseFile = getDatabasePath(BUNNY_DATABASE_FILE)

        // Before anything reads the file: a database restored by Auto Backup arrives in a staging
        // directory and is normally moved into place by `BinkyBackupAgent.onRestoreFinished()`,
        // long before this runs. This is the backstop for the callback never firing, and it has to
        // happen *first* — the version check below would otherwise read a database that is not yet
        // the one the owner restored (ADR-0005).
        adoptRestoredDatabase(filesDir = filesDir, databaseFile = databaseFile)

        // Read before the copy: preserving does not change the file, but the order states the
        // intent — find out what is there, then protect it.
        val onDiskVersion = readUserVersion(databaseFile)
        val preserved =
            preserveBeforeWipe(
                databaseFile = databaseFile,
                preservedDir = File(filesDir, PRESERVED_DIRECTORY),
            )

        // The copy is taken whenever the file is not already at this build's shape, in **both**
        // builds and now on the migrating path too: a release preserves before a *failure* rather
        // than before a wipe (ADR-0023), and an upgrade preserves before a migration, which is the
        // one moment an owner's only copy is being rewritten. It costs one file copy per schema bump.
        when (schemaGateDecision(onDiskVersion)) {
            // A fresh install, a file already at this schema, or — the case this gate used to get
            // wrong — an upgrade the registered migrations can walk. Room migrates on the way in.
            SchemaGate.Open -> openDatabase()
            SchemaGate.Consent, SchemaGate.Refuse ->
                _schemaMismatch.value =
                    SchemaMismatch(
                        // Non-null on both of these paths: neither is reachable unless the versions
                        // differ, which is exactly when `preserveBeforeWipe` copies.
                        preservedCopy = preserved!!,
                        fromVersion = onDiskVersion,
                        toVersion = BUNNY_SCHEMA_VERSION,
                        wipeOnConsent = destructiveMigrationAllowed(),
                    )
        }
    }

    /**
     * The consent screen's one forward button, which exists in a debug build only. Opens the
     * database **explicitly**, so the destruction happens while the owner is still looking at the
     * screen that described it rather than at whatever later moment some flow first collects
     * (ADR-0007), then lets the app through.
     */
    fun consentToWipe() {
        openDatabase { _schemaMismatch.value = null }
    }

    /** Forces the container's `lazy` — the gate — and does the blocking open off the main thread. */
    private fun openDatabase(onOpened: () -> Unit = {}) {
        scope.launch {
            container.openDatabase()
            // **After the gate, never before it.** The sweep guards itself against a pending wipe
            // too (ADR-0007), but arming it while the consent screen is still up would mean the app
            // scheduled background work over a database it had just told the owner it could not
            // open. This is also the "re-enqueued on next launch" path the sweep relies on when it
            // finds a mismatch and returns having done nothing.
            //
            // Off the main thread, because this coroutine is back on it: `openDatabase` moves its
            // own blocking work to IO and returns, and the first `WorkManager.getInstance` call is
            // what builds WorkManager's own database object.
            withContext(Dispatchers.IO) {
                ensureSweepEnqueued(this@BinkyApplication)
                // **The only rebuild that survives a force-stop** (ADR-0025). A force-stopped app
                // runs no receivers and no workers until somebody opens it, so on a phone where
                // autostart is denied — HyperOS's default, and unreadable — this is the one occasion
                // that can put a lost dose alarm back. After the gate for the same reason the sweep
                // is: it reads the database header, and doing that while the consent screen is up
                // would be the app working over a database it had just said it could not open.
                rescheduleDoseAlarm()
                // **The app posts what the agent could only write down** (PLAN 5h). The marker may
                // record that last night's automatic backup left documents behind; the agent had no
                // way to say so and no way to remember having said it. Here, once, and never again
                // until the condition clears.
                postBackupExclusionNoticeIfDue(container.preferences)
            }
            onOpened()
        }
    }
}
