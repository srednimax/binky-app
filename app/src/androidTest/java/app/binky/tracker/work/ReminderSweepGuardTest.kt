package app.binky.tracker.work

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.readUserVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * **The race ADR-0007 named, from the side no hand can drive** (PLAN 4g).
 *
 * The OS can start this process to run a worker with no activity, no UI and nobody looking. If the
 * sweep touched a repository at that moment while a schema mismatch was pending, forcing the
 * container would wipe the owner's database in the background — the exact failure ADR-0007 made its
 * guard structural to prevent. `DatabasePreserveTest` covers the predicate; this covers the
 * **worker**, which is the thing the OS actually wakes.
 *
 * Instrumented rather than JVM because `WorkerParameters` has no public constructor and
 * `TestListenableWorkerBuilder` is the sanctioned way in — and this project has no Robolectric and
 * is not adding one (PLAN). CI runs the instrumented suite on every pull request at API 26 / 34 / 36,
 * which is what makes this always-on rather than a boundary ceremony.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSweepGuardTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /**
     * A context that lies about one thing only: where the database lives.
     *
     * `getApplicationContext` has to be overridden to return `this`, or the override is undone —
     * `TestListenableWorkerBuilder` unwraps whatever it is handed down to the real application
     * context, and the worker would then read the real database rather than the stale fixture.
     *
     * Note what this context deliberately is **not**: a `BinkyApplication`. Everything past the
     * guard begins `applicationContext as BinkyApplication`, so a worker that got that far would
     * throw `ClassCastException` here. That is the point — see the test below.
     */
    private class StaleDatabaseContext(
        base: Context,
        private val databaseFile: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = databaseFile
    }

    /** A file long enough to have a SQLite header, carrying [userVersion] at byte 60. */
    private fun databaseFile(userVersion: Int): File {
        val file = temporaryFolder.newFile("bunny.db")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(100))
            raf.seek(60)
            raf.writeInt(userVersion)
            raf.seek(100)
            raf.write("a real owner's bunny history".toByteArray())
        }
        return file
    }

    /**
     * **Success, and nothing done.** Three claims in one run, and the third is the load-bearing one:
     *
     * 1. It returns `success` — not `retry`, which would have the OS wake it again over the same
     *    unopenable file, and not `failure`, since there is nothing wrong with the *work*.
     * 2. The database is **byte-for-byte** what it was. No wipe.
     * 3. **No crash — and this is asserted by construction rather than by hope.** The context above
     *    is not a `BinkyApplication`, so any path past the guard would `ClassCastException` before
     *    it reached a repository. The run completing at all is therefore proof the guard returned
     *    first, which is stronger than asserting on a result the worker could have reached two ways.
     */
    @Test
    fun aWorkerWokenOverAPendingSchemaMismatchDoesNothing() {
        val stale = databaseFile(userVersion = BUNNY_SCHEMA_VERSION - 1)
        val before = stale.readBytes()

        val context =
            StaleDatabaseContext(ApplicationProvider.getApplicationContext(), stale)
        val worker = TestListenableWorkerBuilder<ReminderSweepWorker>(context).build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("the database was modified", before.contentEquals(stale.readBytes()))
        assertEquals(
            "the schema version moved",
            BUNNY_SCHEMA_VERSION - 1,
            readUserVersion(stale),
        )
    }

    /**
     * The **downgrade** direction, which is not the same case and is the one an owner actually
     * reaches: a backup from a newer build restored onto an older one. Room destroys a downgrade
     * just as thoroughly as it destroys a stale file, so the sweep has to refuse both.
     */
    @Test
    fun aWorkerWokenOverANewerDatabaseOnDiskAlsoDoesNothing() {
        val newer = databaseFile(userVersion = BUNNY_SCHEMA_VERSION + 1)
        val before = newer.readBytes()

        val context =
            StaleDatabaseContext(ApplicationProvider.getApplicationContext(), newer)
        val worker = TestListenableWorkerBuilder<ReminderSweepWorker>(context).build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("the database was modified", before.contentEquals(newer.readBytes()))
    }
}
