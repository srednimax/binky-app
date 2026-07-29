package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry-name allowlist (ADR-0005) — the thing standing between a zip somebody was sent by
 * message and an arbitrary write into app-private storage.
 *
 * These cases are the *design*, not defensive extras: the claim is that a traversal is defeated **by
 * construction** rather than by sanitising, so each rejected shape has to fail for a structural
 * reason and not because some check happened to catch it.
 */
class ArchiveEntriesTest {
    private val uuid = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    @Test
    fun theThreeFixedMembersAreRecognised() {
        assertEquals(ArchiveEntry.Manifest, archiveEntryFor(BACKUP_MANIFEST_ENTRY))
        assertEquals(ArchiveEntry.Database, archiveEntryFor(BACKUP_DATABASE_ENTRY))
        assertEquals(ArchiveEntry.Preferences, archiveEntryFor(BACKUP_PREFERENCES_ENTRY))
    }

    @Test
    fun eachMediaKindResolvesToItsOwnDirectory() {
        MediaKind.entries.forEach { kind ->
            val name = "${kind.directory}/$uuid.jpg"
            assertEquals(ArchiveEntry.Media(kind, name), archiveEntryFor(name))
        }
    }

    /** The traversal. Two segments, and ".." is not a media directory, so there is nothing to sanitise. */
    @Test
    fun aParentDirectoryEntryIsNotAnEntry() {
        assertNull(archiveEntryFor("../secrets.txt"))
        assertNull(archiveEntryFor("../../data/data/app.binky.tracker/x.jpg"))
        assertNull(archiveEntryFor("photos/../../x.jpg"))
    }

    /**
     * The second answer, and the reason it is separate from the first: these names are not merely
     * unrecognised, they are **hostile**, and the archive carrying one is refused whole rather than
     * restored with the entry skipped.
     *
     * Below Android 14 the platform hands these through without complaint, so this predicate — not
     * `getNextEntry` — is what refuses them across the supported range.
     */
    @Test
    fun aTraversalIsRefusedRatherThanSkipped() {
        assertTrue(isPathTraversal("../escaped.txt"))
        assertTrue(isPathTraversal("../../data/data/app.binky.tracker/x.jpg"))
        assertTrue(isPathTraversal("photos/../../x.jpg"))
        assertTrue(isPathTraversal("/photos/$uuid.jpg"))
        // Harmless on Android, which does not treat a backslash as a separator — refused anyway, so
        // the rule stays "no `..` anywhere" rather than "no `..` on the platforms where it bites".
        assertTrue(isPathTraversal("..\\escaped.txt"))
        // A directory entry makes the same claim as a file and gets the same answer.
        assertTrue(isPathTraversal("../"))
    }

    /**
     * The other half, and the one a too-eager rule would break: an archive from a **later** version
     * carrying entries this build has never heard of must still restore. Unknown is skipped; only
     * hostile is refused.
     */
    @Test
    fun anOrdinaryUnknownEntryIsNotATraversal() {
        assertFalse(isPathTraversal(BACKUP_MANIFEST_ENTRY))
        assertFalse(isPathTraversal(BACKUP_DATABASE_ENTRY))
        assertFalse(isPathTraversal("photos/$uuid.jpg"))
        assertFalse(isPathTraversal("videos/$uuid.mp4"))
        assertFalse(isPathTraversal("notes/..hidden.txt"))
        assertFalse(isPathTraversal("photos/2026/$uuid.jpg"))
    }

    @Test
    fun anUnknownDirectoryIsNotAnEntry() {
        assertNull(archiveEntryFor("preserved/$uuid.jpg"))
        assertNull(archiveEntryFor("databases/$uuid.jpg"))
        assertNull(archiveEntryFor("$uuid.jpg"))
    }

    /**
     * An absolute path splits with an empty first segment, so it fails the directory lookup rather
     * than being stripped of its slash and quietly accepted.
     */
    @Test
    fun anAbsolutePathIsNotAnEntry() {
        assertNull(archiveEntryFor("/photos/$uuid.jpg"))
    }

    /**
     * Stricter than `UUID.fromString`, which parses `1-1-1-1-1` happily — a filename this app would
     * never have written is not one it will write back.
     */
    @Test
    fun aFilenameThatIsNotACanonicalUuidIsNotAnEntry() {
        assertNull(archiveEntryFor("photos/1-1-1-1-1.jpg"))
        assertNull(archiveEntryFor("photos/holiday.jpg"))
        assertNull(archiveEntryFor("photos/$uuid.png"))
        assertNull(archiveEntryFor("photos/$uuid.jpg.exe"))
    }

    @Test
    fun aNestedPathUnderAKnownDirectoryIsNotAnEntry() {
        assertNull(archiveEntryFor("photos/2026/$uuid.jpg"))
    }
}
