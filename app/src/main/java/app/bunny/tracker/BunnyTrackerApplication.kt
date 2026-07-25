package app.bunny.tracker

import android.app.Application
import app.bunny.tracker.data.BUNNY_DATABASE_FILE
import app.bunny.tracker.data.BUNNY_SCHEMA_VERSION
import app.bunny.tracker.data.PRESERVED_DIRECTORY
import app.bunny.tracker.data.preserveBeforeWipe
import app.bunny.tracker.data.readUserVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * A wipe that has been prepared but not yet consented to: the copy is already taken, and the
 * original is still on disk untouched, waiting for the owner to press the one button.
 *
 * @param preservedCopy where the copy landed, under `filesDir`.
 * @param fromVersion the schema version the file on disk was written at.
 * @param toVersion the version this build expects.
 */
data class PendingWipe(
    val preservedCopy: File,
    val fromVersion: Int,
    val toVersion: Int,
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
class BunnyTrackerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val _pendingWipe = MutableStateFlow<PendingWipe?>(null)

    /** Non-null while the blocking consent screen must be shown instead of the app. */
    val pendingWipe: StateFlow<PendingWipe?> = _pendingWipe.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        val databaseFile = getDatabasePath(BUNNY_DATABASE_FILE)
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
            _pendingWipe.value =
                PendingWipe(
                    preservedCopy = preserved,
                    fromVersion = onDiskVersion,
                    toVersion = BUNNY_SCHEMA_VERSION,
                )
        }
    }

    /**
     * The consent screen's one forward button. Opens the database **explicitly**, so the destruction
     * happens while the owner is still looking at the screen that described it rather than at
     * whatever later moment some flow first collects (ADR-0007), then lets the app through.
     */
    fun consentToWipe() {
        openDatabase { _pendingWipe.value = null }
    }

    /** Forces the container's `lazy` — the gate — and does the blocking open off the main thread. */
    private fun openDatabase(onOpened: () -> Unit = {}) {
        scope.launch {
            container.openDatabase()
            onOpened()
        }
    }
}
