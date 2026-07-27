package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
