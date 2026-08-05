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
    private val secondDocument = "3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg"

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
    fun `a cloud backup carries the database, the preferences, the avatars and the documents`() {
        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false)

        assertEquals(
            setOf("bunny.db", "bunny_preferences.preferences_pb", avatar, document),
            set.files.map { it.name }.toSet(),
        )
        assertEquals(0, set.excludedDocuments)
    }

    @Test
    fun `the gallery, the preserved copies and the marker are left out of a cloud backup`() {
        writeAutoBackupMarker(filesDir, Instant.now())

        val paths = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false).files.map { it.path }

        // photos/: an unbounded directory inside an all-or-nothing quota would one day take the
        // database down with it. preserved/: unbounded and never pruned, for the same reason.
        assertTrue(paths.none { it.contains("/${MediaKind.Photo.directory}/") })
        assertTrue(paths.none { it.contains("/preserved/") })
        // The marker cannot travel to another phone and vouch there for a backup it never made.
        assertTrue(paths.none { it.endsWith(AUTO_BACKUP_MARKER_FILE) })
    }

    @Test
    fun `documents are admitted last, so the core never loses its place to them`() {
        // A budget with room for the core and one document only. The core is unconditional, and the
        // document that does not fit is reported rather than dropped in silence.
        val coreBytes =
            autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false)
                .files
                .filterNot { it.path.contains("/${MediaKind.Document.directory}/") }
                .sumOf { it.length() }
        write(File(filesDir, "${MediaKind.Document.directory}/$secondDocument"), "x".repeat(4096))

        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false, budget = coreBytes)

        assertEquals(2, set.excludedDocuments)
        assertTrue(set.files.none { it.path.contains("/${MediaKind.Document.directory}/") })
        // Every unconditional file is still there: the ceiling takes documents, never the core.
        assertTrue(set.files.map { it.name }.containsAll(listOf("bunny.db", avatar)))
    }

    @Test
    fun `a device-to-device transfer carries the gallery and every document`() {
        write(File(filesDir, "${MediaKind.Document.directory}/$secondDocument"), "x".repeat(4096))

        // A budget nothing would fit under, to prove the ceiling is not consulted at all here.
        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = true, budget = 1)

        assertTrue(set.files.map { it.name }.contains(photo))
        assertTrue(set.files.map { it.name }.containsAll(listOf(document, secondDocument)))
        assertEquals(0, set.excludedDocuments)
        // No cloud account and no quota there, but preserved/ is still not something to carry onto
        // a new phone unasked.
        assertTrue(set.files.none { it.path.contains("/preserved/") })
    }

    @Test
    fun `a stray file in a media directory is not shipped`() {
        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = true)

        // The same uuid allowlist the export uses: a file this app could not have written is one a
        // restore would refuse anyway.
        assertTrue(set.files.none { it.name == "notes.txt" })
    }

    @Test
    fun `a phone whose database has never been opened backs up what it does have`() {
        staged.delete()

        val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = false)

        assertTrue(set.files.none { it.name == "bunny.db" })
        assertTrue(set.files.map { it.name }.contains(avatar))
    }

    @Test
    fun `everything fits, and nothing is reported as excluded`() {
        val documents = listOf(sized("a", 100), sized("b", 100))

        val admission = admitDocuments(coreBytes = 100, documentsNewestFirst = documents, budget = 1_000)

        assertEquals(documents, admission.files)
        assertEquals(0, admission.excludedDocuments)
    }

    @Test
    fun `nothing fits, and every document is counted`() {
        val documents = listOf(sized("a", 100), sized("b", 100))

        val admission = admitDocuments(coreBytes = 950, documentsNewestFirst = documents, budget = 1_000)

        assertTrue(admission.files.isEmpty())
        assertEquals(2, admission.excludedDocuments)
    }

    @Test
    fun `a core already over budget admits nothing rather than going negative`() {
        // The evidential core is the first duty (ADR-0005). A database that has outgrown the ceiling
        // on its own is a real state, and the arithmetic must not wrap into admitting documents.
        val admission = admitDocuments(coreBytes = 5_000, documentsNewestFirst = listOf(sized("a", 1)), budget = 1_000)

        assertTrue(admission.files.isEmpty())
        assertEquals(1, admission.excludedDocuments)
    }

    @Test
    fun `newest first is honoured at the boundary`() {
        // Room for exactly one of the two. The newest one takes it, and the older is the one left
        // behind — the priority the caller's ordering exists to express.
        val newest = sized("newest", 500)
        val older = sized("older", 500)

        val admission = admitDocuments(coreBytes = 0, documentsNewestFirst = listOf(newest, older), budget = 500)

        assertEquals(listOf(newest), admission.files)
        assertEquals(1, admission.excludedDocuments)
    }

    @Test
    fun `one oversized scan does not exclude the smaller history behind it`() {
        // Skips rather than stops: preserving as many documents as fit is the second duty, and a
        // single huge page would otherwise take every later one down with it.
        val huge = sized("huge", 900)
        val small = sized("small", 100)

        val admission = admitDocuments(coreBytes = 0, documentsNewestFirst = listOf(huge, small), budget = 500)

        assertEquals(listOf(small), admission.files)
        assertEquals(1, admission.excludedDocuments)
    }

    @Test
    fun `an exclusion is announced once, and again only after it has cleared`() {
        // The agent runs roughly daily and cannot remember anything, so this is what keeps a
        // one-time notice from becoming a nightly one.
        assertEquals(ExclusionNotice.Post, exclusionNotice(excludedDocuments = 12, alreadyNotified = false))
        assertEquals(ExclusionNotice.Nothing, exclusionNotice(excludedDocuments = 12, alreadyNotified = true))
        // Still excluding, just fewer: already said, and a second notice would add nothing the
        // status line does not already carry.
        assertEquals(ExclusionNotice.Nothing, exclusionNotice(excludedDocuments = 3, alreadyNotified = true))
        // Resolved — forget it happened, so a future episode is allowed to speak up.
        assertEquals(ExclusionNotice.Clear, exclusionNotice(excludedDocuments = 0, alreadyNotified = true))
        assertEquals(ExclusionNotice.Nothing, exclusionNotice(excludedDocuments = 0, alreadyNotified = false))
    }

    @Test
    fun `the marker survives a round trip and leaves no half-written file behind`() {
        val at = Instant.parse("2026-07-28T06:15:00Z")

        writeAutoBackupMarker(filesDir, at, excludedDocuments = 12)

        assertEquals(AutoBackupMarker(at, excludedDocuments = 12), readAutoBackupMarker(filesDir))
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
    fun `a marker written by another version is still readable in both directions`() {
        // Forward: a key this build has never heard of is ignored rather than treated as garbage,
        // which is what let Phase 5 add the count to a file 1.0 had already shipped.
        write(
            File(filesDir, AUTO_BACKUP_MARKER_FILE),
            "lastBackupAtEpochMilli=1785000000000\nexcludedDocuments=12\nsomethingLater=yes\n",
        )

        assertEquals(
            AutoBackupMarker(Instant.ofEpochMilli(1785000000000L), excludedDocuments = 12),
            readAutoBackupMarker(filesDir),
        )

        // Backward: a marker written by 1.0 or 1.1 has no count at all, and reads as zero rather
        // than as unknown. This build must not claim documents were dropped by a build that had
        // none to drop.
        write(File(filesDir, AUTO_BACKUP_MARKER_FILE), "lastBackupAtEpochMilli=1785000000000\n")

        assertEquals(
            AutoBackupMarker(Instant.ofEpochMilli(1785000000000L), excludedDocuments = 0),
            readAutoBackupMarker(filesDir),
        )
    }

    @Test
    fun `the status line carries the excluded count through to the screen`() {
        val now = Instant.parse("2026-07-28T06:15:00Z")

        val status = autoBackupStatus(AutoBackupMarker(now, excludedDocuments = 12), now)

        // One number behind both the sentence on the Backup screen and the one-time notification,
        // so the two cannot disagree about how much is missing.
        assertEquals(12, (status as AutoBackupStatus.Recorded).excludedDocuments)
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

    /** A file of an exact size, for the admission table — its bytes are never read, only counted. */
    private fun sized(
        name: String,
        bytes: Int,
    ): File = File(temp.root, name).also { write(it, "x".repeat(bytes)) }

    private fun write(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
