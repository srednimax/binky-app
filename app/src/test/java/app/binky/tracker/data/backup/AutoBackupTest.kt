package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Auto Backup's three moving parts, all of which are functions over `File` on purpose (ADR-0005).
 *
 * The agent itself is a shell with no decisions in it, so there is nothing here that needs a phone:
 * what a backup carries, what the marker says, and what happens to a restored database are all
 * settled by arithmetic and directory listings. That is the point of the split — the parts that
 * could be wrong are the parts a test can reach, rather than the parts that only run when a device
 * is idle and charging.
 */
class AutoBackupTest {
    @get:Rule val temp = TemporaryFolder()

    private val avatar = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val photo = "3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"
    private val document = "3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"

    private lateinit var filesDir: File
    private lateinit var staged: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        staged = File(filesDir, AUTO_BACKUP_STAGING_PATH).also { write(it, "a checkpointed database") }

        write(File(filesDir, PREFERENCES_FILE_PATH), "preferences")
        write(File(filesDir, "${MediaKind.Avatar.directory}/$avatar"), "avatar")
        write(File(filesDir, "${MediaKind.Photo.directory}/$photo"), "photo")
        write(File(filesDir, "${MediaKind.Document.directory}/$document"), "document")
        write(File(filesDir, "preserved/bunny-20260726T073547Z.db"), "a copy taken before a wipe")
        write(File(filesDir, "${MediaKind.Avatar.directory}/notes.txt"), "stray")
    }

    @Test
    fun `a cloud backup carries the database, the preferences and the avatars — and nothing else`() {
        val set = autoBackupFileSet(filesDir, staged, includePhotos = false)

        assertEquals(setOf("bunny.db", "bunny_preferences.preferences_pb", avatar), set.map { it.name }.toSet())
    }

    @Test
    fun `the gallery, the preserved copies and the marker are left out of a cloud backup`() {
        writeAutoBackupMarker(filesDir, Instant.now())

        val paths = autoBackupFileSet(filesDir, staged, includePhotos = false).map { it.path }

        // photos/: an unbounded directory inside an all-or-nothing quota would one day take the
        // database down with it. preserved/: unbounded and never pruned, for the same reason.
        assertTrue(paths.none { it.contains("/${MediaKind.Photo.directory}/") })
        assertTrue(paths.none { it.contains("/preserved/") })
        // The marker cannot travel to another phone and vouch there for a backup it never made.
        assertTrue(paths.none { it.endsWith(AUTO_BACKUP_MARKER_FILE) })
        // Phase 5's, with the admission ceiling that makes it a decision rather than a directory.
        assertTrue(paths.none { it.contains("/${MediaKind.Document.directory}/") })
    }

    @Test
    fun `a device-to-device transfer carries the gallery as well`() {
        val set = autoBackupFileSet(filesDir, staged, includePhotos = true)

        assertTrue(set.map { it.name }.contains(photo))
        // No cloud account and no quota there, but preserved/ is still not something to carry onto
        // a new phone unasked.
        assertTrue(set.none { it.path.contains("/preserved/") })
    }

    @Test
    fun `a stray file in a media directory is not shipped`() {
        val set = autoBackupFileSet(filesDir, staged, includePhotos = true)

        // The same uuid allowlist the export uses: a file this app could not have written is one a
        // restore would refuse anyway.
        assertTrue(set.none { it.name == "notes.txt" })
    }

    @Test
    fun `a phone whose database has never been opened backs up what it does have`() {
        staged.delete()

        val set = autoBackupFileSet(filesDir, staged, includePhotos = false)

        assertTrue(set.none { it.name == "bunny.db" })
        assertTrue(set.map { it.name }.contains(avatar))
    }

    @Test
    fun `the marker survives a round trip and leaves no half-written file behind`() {
        val at = Instant.parse("2026-07-28T06:15:00Z")

        writeAutoBackupMarker(filesDir, at)

        assertEquals(AutoBackupMarker(at), readAutoBackupMarker(filesDir))
        assertFalse(File(filesDir, "$AUTO_BACKUP_MARKER_FILE.part").exists())
    }

    @Test
    fun `an absent, unreadable or cleared marker reads as no marker at all`() {
        assertNull(readAutoBackupMarker(filesDir))

        write(File(filesDir, AUTO_BACKUP_MARKER_FILE), "who knows what this is")
        assertNull(readAutoBackupMarker(filesDir))

        writeAutoBackupMarker(filesDir, Instant.now())
        clearAutoBackupMarker(filesDir)
        assertNull(readAutoBackupMarker(filesDir))
    }

    @Test
    fun `a marker written by a later Binky is still readable`() {
        // Phase 5 adds the excluded-document count. A 1.0 reader has to ignore what it does not know
        // rather than treat the whole file as garbage and report a backup that happened as one that
        // did not.
        write(
            File(filesDir, AUTO_BACKUP_MARKER_FILE),
            "lastBackupAtEpochMilli=1785000000000\nexcludedDocuments=12\n",
        )

        assertEquals(AutoBackupMarker(Instant.ofEpochMilli(1785000000000L)), readAutoBackupMarker(filesDir))
    }

    @Test
    fun `no marker is never rendered as a working net`() {
        assertEquals(AutoBackupStatus.NeverRecorded, autoBackupStatus(null, Instant.now()))
    }

    @Test
    fun `a marker goes stale at a fortnight and not before`() {
        val now = Instant.parse("2026-07-28T06:15:00Z")

        fun statusAgedByDays(days: Long) = autoBackupStatus(AutoBackupMarker(now.minus(Duration.ofDays(days))), now)

        assertEquals(false, (statusAgedByDays(13) as AutoBackupStatus.Recorded).stale)
        // Exactly 14 days is still a date rather than an admission: Auto Backup's cadence is
        // roughly daily, and the threshold is what it means to have missed a fortnight of them.
        assertEquals(false, (statusAgedByDays(14) as AutoBackupStatus.Recorded).stale)
        assertEquals(true, (statusAgedByDays(15) as AutoBackupStatus.Recorded).stale)
    }

    @Test
    fun `a marker dated in the future is a clock that moved, not a backup that failed`() {
        val now = Instant.parse("2026-07-28T06:15:00Z")

        val status = autoBackupStatus(AutoBackupMarker(now.plus(Duration.ofDays(30))), now)

        assertEquals(false, (status as AutoBackupStatus.Recorded).stale)
    }

    @Test
    fun `a restored database is moved into place, sidecars and staging directory gone`() {
        val databaseFile = File(temp.newFolder("databases"), "bunny.db")
        write(File("${databaseFile.path}-wal"), "a log describing a file that no longer exists")

        assertTrue(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("a checkpointed database", databaseFile.readText())
        assertFalse(File("${databaseFile.path}-wal").exists())
        assertFalse(staged.exists())
        assertFalse(staged.parentFile!!.exists())
    }

    @Test
    fun `a staged copy beside a live database is discarded, never adopted over it`() {
        // The app has run since the restore, or the copy was never adopted and the owner has been
        // using Binky ever since. Either way the live file is the one with the records in it, and
        // overwriting it with the copy is the more expensive mistake by a long way.
        val databaseFile = File(temp.newFolder("databases"), "bunny.db")
        write(databaseFile, "a year of weighings")

        assertFalse(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("a year of weighings", databaseFile.readText())
        assertFalse(staged.exists())
    }

    @Test
    fun `an ordinary launch with nothing staged does nothing`() {
        staged.delete()
        val databaseFile = File(temp.newFolder("databases"), "bunny.db").also { write(it, "a year of weighings") }

        assertFalse(adoptRestoredDatabase(filesDir, databaseFile))

        assertEquals("a year of weighings", databaseFile.readText())
    }

    private fun write(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
