package app.binky.tracker.work

import android.content.BroadcastReceiver
import android.content.Context
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.readUserVersion
import app.binky.tracker.data.schemaMismatchPending
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * **ADR-0007's guard, asked from a background entry point** — the one shape every worker and every
 * receiver in this package needs before it touches anything.
 *
 * The hazard is specific: the OS can start this process to run a worker or deliver a broadcast with
 * no UI and no owner present, and anything that forces the container over a stale schema destroys
 * the database in the background on a phone nobody is looking at. So the question is asked out of
 * four bytes of the file header rather than by opening anything, and the answer is `true` exactly
 * when the right move is to do nothing and let the next launch's consent screen resolve it.
 *
 * One function rather than the same three lines in five places. `schemaMismatchPending` is the pure
 * predicate underneath and is tested as one in `DatabasePreserveTest`; this is only the file read in
 * front of it.
 */
internal fun Context.schemaWipePending(): Boolean =
    schemaMismatchPending(
        readUserVersion(getDatabasePath(BUNNY_DATABASE_FILE)),
        BUNNY_SCHEMA_VERSION,
    )

/**
 * Runs [work] off the main thread while holding the broadcast open, and lets go when it finishes.
 *
 * `onReceive` runs on the **main thread** and the receiver is considered finished the moment it
 * returns — after which the process is a candidate for death. Everything a dose receiver does is
 * disk-bound (the header read above, and from 5d a Room query), so doing it inline would be a
 * StrictMode violation on the main thread and doing it in a bare thread would race the process
 * teardown. `goAsync()` is the platform's answer to both: it hands back a token that keeps the
 * broadcast alive until `finish()`.
 *
 * Kotlin note: `CoroutineScope(Dispatchers.IO).launch` is a fire-and-forget background job — the
 * closest thing to an un-awaited `async` call in JS. There is no lifecycle to scope it to here (a
 * receiver has none), which is exactly why the `finish()` in `finally` is not optional: it is what
 * tells Android the work is over, and skipping it on a throw would hold the process open for the
 * ten seconds Android allows before killing it anyway.
 */
internal fun BroadcastReceiver.rebuildInBackground(
    context: Context,
    work: (Context) -> Unit,
) {
    val pending = goAsync()
    // The receiver's own Context is short-lived; the application one outlives the broadcast.
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        try {
            work(appContext)
        } finally {
            pending.finish()
        }
    }
}
