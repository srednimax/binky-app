package app.bunny.tracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bunny.tracker.data.BUNNY_SCHEMA_VERSION
import app.bunny.tracker.data.buildBunnyDatabase
import app.bunny.tracker.data.countRows
import app.bunny.tracker.data.hasTable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/**
 * ADR-0007's structural guard, from the side that actually matters: **constructing [AppContainer]
 * must not touch the database file.** Everything else in the guard — the header read, the copy, the
 * blocking screen — is worth nothing if Room opens the file on the way past.
 *
 * Instrumented rather than JVM because the claim is about Room's real `build()`, not about a fake.
 */
@RunWith(AndroidJUnit4::class)
class WipeGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** A plausible SQLite file at [userVersion] — enough header for the guard, and a payload. */
    private fun staleDatabaseFile(userVersion: Int): File {
        // A throwaway name, never the real one: this test would otherwise scribble over the
        // database on the developer's own phone, which is the data the guard exists to protect.
        val file = context.getDatabasePath("wipe-guard-${UUID.randomUUID()}.db")
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteArray(100))
            raf.seek(60)
            raf.writeInt(userVersion)
            raf.seek(100)
            raf.write("a year of weighings".toByteArray())
        }
        return file
    }

    /**
     * A real SQLite database at [userVersion], carrying a table this build knows nothing about, so
     * "the wipe actually dropped things" is observable rather than assumed.
     */
    private fun realDatabaseAtVersion(userVersion: Int): File {
        val file = context.getDatabasePath("wipe-guard-${UUID.randomUUID()}.db")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE a_table_from_an_older_schema (id TEXT PRIMARY KEY)")
            db.execSQL("INSERT INTO a_table_from_an_older_schema VALUES ('a year of weighings')")
            db.execSQL("PRAGMA user_version = $userVersion")
        }
        return file
    }

    /**
     * **Consenting to the wipe must wipe, not crash.** ADR-0007's whole premise through Phase 2 is
     * that a schema bump destroys the database — and a destructive fallback that has been quietly
     * disarmed does not announce itself, it throws `A migration from N to M was required but not
     * found` from whichever background coroutine touches the database first.
     *
     * This goes through [buildBunnyDatabase] rather than its own `Room.databaseBuilder` chain on
     * purpose: a test that restated the configuration would have passed while the app crashed,
     * which is exactly what happened when `fallbackToDestructiveMigrationOnDowngrade` was chained
     * after `fallbackToDestructiveMigration` and reset `requireMigration` back to true.
     */
    @Test
    fun openingOverAStaleSchemaWipesRatherThanThrowing() =
        runTest {
            val file = realDatabaseAtVersion(userVersion = BUNNY_SCHEMA_VERSION - 1)

            try {
                val database = buildBunnyDatabase(context, file.name)
                try {
                    // Any query forces the open, and with it the migration decision.
                    assertEquals(0, database.countRows("bunnies"))
                    assertEquals(0, database.countRows("weights"))
                    // dropAllTables reaches tables Room never created, so nothing is left behind.
                    assertFalse(database.hasTable("a_table_from_an_older_schema"))
                } finally {
                    database.close()
                }
            } finally {
                file.delete()
                File(file.path + "-wal").delete()
                File(file.path + "-shm").delete()
            }
        }

    @Test
    fun constructingTheContainerLeavesAStaleDatabaseByteIdentical() =
        runTest {
            val file = staleDatabaseFile(userVersion = BUNNY_SCHEMA_VERSION - 1)
            val before = file.readBytes()

            try {
                // `backgroundScope`'s coroutines are queued on the test dispatcher and do not run
                // until this body suspends — so `selectedBunny`'s eager `stateIn`, the very thing
                // that would open the file, provably has not collected by the assertion below.
                // That isolates the claim to construction, which is what the guard relies on.
                AppContainer(context, backgroundScope, databaseName = file.name)

                assertArrayEquals(before, file.readBytes())
            } finally {
                file.delete()
            }
        }
}
