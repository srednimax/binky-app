package app.binky.tracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Restore **merges** media rather than replacing it (ADR-0005), and this is the whole of that
 * decision as a pure function — no disk, no Room, so the case that matters can be stated directly.
 */
class MediaMergeTest {
    private val avatar = "avatars/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val otherAvatar = "avatars/3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"
    private val photo = "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"
    private val otherPhoto = "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg"

    /**
     * The case the merge exists for: an Essential backup onto a phone that still holds its gallery
     * keeps the photos, instead of turning every one of them into a placeholder.
     */
    @Test
    fun anEssentialRestoreKeepsPhotosItNeverLookedAt() {
        val plan =
            planMediaMerge(
                archivePaths = listOf(avatar),
                diskPaths = listOf(avatar, photo, otherPhoto),
                scope = BackupScope.Essential,
            )

        assertEquals(listOf(avatar), plan.overlaid)
        assertEquals(listOf(photo, otherPhoto), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }

    /**
     * The same two disk files, under a scope that *does* cover them, are a different fact: the
     * archive was authoritative about `photos/` and did not contain them, so the restored database
     * has no row pointing at either.
     */
    @Test
    fun anEverythingRestoreOrphansPhotosItsArchiveDidNotCarry() {
        val plan =
            planMediaMerge(
                archivePaths = listOf(avatar),
                diskPaths = listOf(avatar, photo, otherPhoto),
                scope = BackupScope.Everything,
            )

        assertEquals(listOf(avatar), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(listOf(photo, otherPhoto), plan.orphaned)
    }

    /** A file present on both sides is overlaid once, not counted as surviving as well. */
    @Test
    fun aFileInBothPlacesIsOnlyOverlaid() {
        val plan =
            planMediaMerge(
                archivePaths = listOf(avatar, photo),
                diskPaths = listOf(avatar, photo),
                scope = BackupScope.Everything,
            )

        assertEquals(listOf(avatar, photo), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }

    @Test
    fun anArchiveFileMissingFromDiskIsStillOverlaid() {
        val plan =
            planMediaMerge(
                archivePaths = listOf(avatar, otherAvatar),
                diskPaths = listOf(avatar),
                scope = BackupScope.Essential,
            )

        assertEquals(listOf(avatar, otherAvatar), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }

    /** Restoring onto a fresh install: nothing to keep, nothing to orphan. */
    @Test
    fun anEmptyPhoneTakesEverythingTheArchiveHas() {
        val plan =
            planMediaMerge(
                archivePaths = listOf(avatar, photo),
                diskPaths = emptyList(),
                scope = BackupScope.Everything,
            )

        assertEquals(listOf(avatar, photo), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }
}
