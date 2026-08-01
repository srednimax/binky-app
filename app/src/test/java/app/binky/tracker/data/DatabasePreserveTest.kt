package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * ADR-0007's preserve half. The point of reading the header by hand is that it happens before Room
 * opens the file, so these tests hand it files rather than databases.
 */
class DatabasePreserveTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** A file long enough to have a SQLite header, carrying [userVersion] at byte 60. */
    private fun databaseFile(
        userVersion: Int,
        payload: String = "database contents",
    ): File {
        val file = temporaryFolder.newFile("bunny.db")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(100))
            raf.seek(60)
            raf.writeInt(userVersion)
            raf.seek(100)
            raf.write(payload.toByteArray())
        }
        return file
    }

    @Test
    fun `reads the version out of the header`() {
        assertEquals(7, readUserVersion(databaseFile(userVersion = 7)))
    }

    @Test
    fun `a missing file has nothing to lose`() {
        assertEquals(0, readUserVersion(File(temporaryFolder.root, "absent.db")))
    }

    @Test
    fun `a file too short to hold a header has nothing to lose`() {
        val truncated = temporaryFolder.newFile("truncated.db")
        truncated.writeBytes(ByteArray(40))
        assertEquals(0, readUserVersion(truncated))

        val empty = temporaryFolder.newFile("empty.db")
        assertEquals(0, readUserVersion(empty))
    }

    /**
     * The predicate the daily sweep asks before it touches anything (ADR-0024, ADR-0007).
     *
     * Its two "safe" answers are opposites and both matter. A **fresh install** has nothing to lose,
     * so the sweep may run — refusing there would mean a phone that never sweeps until it happens to
     * hold data. A **matching version** is the ordinary case. Everything else is the sweep forcing a
     * container that destroys a database with nobody looking, which is the exact future ADR-0007
     * named when it made its guard structural.
     */
    @Test
    fun `a pending schema mismatch is anything that is neither absent nor current`() {
        assertFalse("a fresh install", schemaMismatchPending(onDiskVersion = 0, appSchemaVersion = 5))
        assertFalse("already at this version", schemaMismatchPending(onDiskVersion = 5, appSchemaVersion = 5))
        assertTrue("an upgrade this build has not migrated", schemaMismatchPending(4, appSchemaVersion = 5))
        // A downgrade counts: Room destroys one just as thoroughly as it destroys an upgrade, so a
        // sweep running under a rolled-back build is the same hazard from the other direction.
        assertTrue("a downgrade", schemaMismatchPending(6, appSchemaVersion = 5))
    }

    @Test
    fun `preserves a database this build would wipe`() {
        val database = databaseFile(userVersion = 1, payload = "a year of weighings")
        val preservedDir = File(temporaryFolder.root, PRESERVED_DIRECTORY)

        val preserved = preserveBeforeWipe(database, preservedDir, appSchemaVersion = 2)

        assertNotNull(preserved)
        assertTrue(preserved!!.name.startsWith("bunny-"))
        assertTrue(preserved.name.endsWith(".db"))
        assertEquals(database.readBytes().toList(), preserved.readBytes().toList())
        // The original is untouched — this is a copy, not a move.
        assertTrue(database.isFile)
    }

    @Test
    fun `preserves a downgrade too, which Room destroys just as thoroughly`() {
        val database = databaseFile(userVersion = 3)
        val preserved =
            preserveBeforeWipe(database, File(temporaryFolder.root, PRESERVED_DIRECTORY), appSchemaVersion = 2)
        assertNotNull(preserved)
    }

    @Test
    fun `preserves nothing when the schema already matches`() {
        val database = databaseFile(userVersion = 2)
        val preservedDir = File(temporaryFolder.root, PRESERVED_DIRECTORY)

        assertNull(preserveBeforeWipe(database, preservedDir, appSchemaVersion = 2))
        assertTrue(!preservedDir.exists())
    }

    @Test
    fun `preserves nothing on a fresh install`() {
        val absent = File(temporaryFolder.root, "absent.db")
        assertNull(preserveBeforeWipe(absent, File(temporaryFolder.root, PRESERVED_DIRECTORY), appSchemaVersion = 2))
    }

    /**
     * The consent screen has no cancel, but an owner can still relaunch instead of pressing the one
     * button. Nothing writes to the database in between, so its `lastModified()` has not moved and
     * the copy takes the same name — one copy, overwritten, rather than a new one per bout of
     * hesitation (ADR-0007).
     */
    @Test
    fun `relaunching before consent overwrites one copy rather than minting another`() {
        val database = databaseFile(userVersion = 1, payload = "a year of weighings")
        val preservedDir = File(temporaryFolder.root, PRESERVED_DIRECTORY)

        val first = preserveBeforeWipe(database, preservedDir, appSchemaVersion = 2)!!
        val second = preserveBeforeWipe(database, preservedDir, appSchemaVersion = 2)!!

        assertEquals(first.name, second.name)
        assertEquals(1, preservedDir.listFiles()!!.size)
        assertEquals(database.readBytes().toList(), second.readBytes().toList())
    }

    @Test
    fun `carries the WAL sidecar, where the most recent writes may be`() {
        val database = databaseFile(userVersion = 1)
        File(database.path + "-wal").writeText("the last three weighings")
        File(database.path + "-shm").writeText("shared memory index")

        val preserved =
            preserveBeforeWipe(
                database,
                File(temporaryFolder.root, PRESERVED_DIRECTORY),
                appSchemaVersion = 2,
            )!!

        assertEquals("the last three weighings", File(preserved.path + "-wal").readText())
        assertEquals("shared memory index", File(preserved.path + "-shm").readText())
    }
}
