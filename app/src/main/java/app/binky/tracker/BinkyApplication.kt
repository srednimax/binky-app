package app.binky.tracker

import android.app.Application
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.PRESERVED_DIRECTORY
import app.binky.tracker.data.backup.adoptRestoredDatabase
import app.binky.tracker.data.destructiveMigrationAllowed
import app.binky.tracker.data.preserveBeforeWipe
import app.binky.tracker.data.readUserVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
class BinkyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val _schemaMismatch = MutableStateFlow<SchemaMismatch?>(null)

    /** Non-null while the blocking screen must be shown instead of the app. */
    val schemaMismatch: StateFlow<SchemaMismatch?> = _schemaMismatch.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

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

        if (preserved == null) {
            // The ordinary case: a fresh install, or a file already at this schema. Nothing to
            // consent to, so the gate opens immediately.
            openDatabase()
        } else {
            // The copy is taken in **both** builds. In a release it is preserving before a
            // *failure* rather than before a wipe, which is a better reason than the one
            // `preserveBeforeWipe` was written for (ADR-0023).
            _schemaMismatch.value =
                SchemaMismatch(
                    preservedCopy = preserved,
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
            onOpened()
        }
    }
}
