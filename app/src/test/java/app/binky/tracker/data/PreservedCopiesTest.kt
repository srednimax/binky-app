package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * The listing half of ADR-0007 — what Settings shows an owner after a wipe. Filenames are written
 * out in full rather than built from the format constants, so that a change to the naming scheme
 * fails here instead of quietly agreeing with itself.
 */
class PreservedCopiesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val preservedDir: File get() = temporaryFolder.root

    private fun preserved(
        name: String,
        contents: String = "a year of weighings",
    ): File = temporaryFolder.newFile(name).apply { writeText(contents) }

    @Test
    fun `a directory that was never created lists nothing`() {
        assertEquals(emptyList<PreservedCopy>(), listPreservedCopies(File(temporaryFolder.root, "absent")))
    }

    @Test
    fun `lists newest first`() {
        preserved("bunny-20260603T091500Z.db")
        preserved("bunny-20260726T101500Z.db")
        preserved("bunny-20260710T182200Z.db")

        assertEquals(
            listOf("bunny-20260726T101500Z.db", "bunny-20260710T182200Z.db", "bunny-20260603T091500Z.db"),
            listPreservedCopies(preservedDir).map { it.name },
        )
    }

    @Test
    fun `dates the copy from its name, not from when the copy was taken`() {
        preserved("bunny-20260726T101500Z.db")

        assertEquals(Instant.parse("2026-07-26T10:15:00Z"), listPreservedCopies(preservedDir).single().savedAt)
    }

    @Test
    fun `a name that does not parse still lists, undated`() {
        // Better to offer an owner a file they can still share off the phone than to hide it.
        preserved("bunny-whenever.db")

        val copy = listPreservedCopies(preservedDir).single()
        assertEquals("bunny-whenever.db", copy.name)
        assertNull(copy.savedAt)
    }

    @Test
    fun `ignores files this app did not write`() {
        preserved("bunny-20260726T101500Z.db")
        preserved("notes.txt")
        preserved("bunny-20260726T101500Z.db-wal") // a sidecar is part of a copy, never a copy itself
        preserved("elsewhere-20260726T101500Z.db")
        File(preservedDir, "bunny-20260101T000000Z.db").mkdir() // a directory, not a file

        assertEquals(listOf("bunny-20260726T101500Z.db"), listPreservedCopies(preservedDir).map { it.name })
    }

    @Test
    fun `a copy carries whichever sidecars exist`() {
        val db = preserved("bunny-20260726T101500Z.db", contents = "12345")
        File(db.path + "-wal").writeText("678") // the last writes may live only here
        // No -shm: WAL mode does not always leave one, and its absence is not a broken copy.

        val copy = listPreservedCopies(preservedDir).single()

        assertEquals(listOf("bunny-20260726T101500Z.db", "bunny-20260726T101500Z.db-wal"), copy.files.map { it.name })
        assertEquals(8L, copy.totalBytes)
    }

    @Test
    fun `deleting takes the sidecars with it and leaves other copies alone`() {
        val doomed = preserved("bunny-20260726T101500Z.db")
        File(doomed.path + "-wal").writeText("the last three weighings")
        File(doomed.path + "-shm").writeText("shared memory index")
        preserved("bunny-20260603T091500Z.db")

        deletePreservedCopy(listPreservedCopies(preservedDir).first { it.name == doomed.name })

        assertFalse(doomed.exists())
        assertFalse(File(doomed.path + "-wal").exists())
        assertFalse(File(doomed.path + "-shm").exists())
        assertTrue(File(preservedDir, "bunny-20260603T091500Z.db").isFile)
    }
}
