package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind

/**
 * What a restore will do to the media already on the phone, decided before a single byte moves.
 *
 * Nothing here is ever deleted. The three buckets exist so the terminal screen can say what
 * happened, and so the difference between [kept] and [orphaned] — which look identical on disk — is
 * a decision the code makes explicitly rather than a coincidence.
 */
data class MediaMergePlan(
    /**
     * Paths the archive carries. Each is written to disk, replacing whatever sits at that path.
     *
     * Overlaying can never mismatch: the split relative paths make each uuid a **stable global
     * identity** (ADR-0005), so a file with a given uuid is always that exact image.
     */
    val overlaid: List<String>,
    /**
     * On disk, in a kind this scope does **not** carry — so they survive untouched.
     *
     * This is the whole point of merging rather than replacing: restoring an Essential backup onto a
     * phone that still holds its photo files keeps those irreplaceable photos instead of turning
     * them all into placeholders.
     */
    val kept: List<String>,
    /**
     * On disk, in a kind this scope **does** carry, and absent from the archive.
     *
     * The database is always the *full* database regardless of scope, so after the replace nothing
     * in it points at these: they are invisible orphans, never rendered, cleanable later. They are
     * left alone anyway — silently deleting a recovery artifact is the one thing this project has
     * consistently refused to do on the owner's behalf.
     */
    val orphaned: List<String>,
)

/**
 * Work out the merge from the two file lists and the scope the manifest declared.
 *
 * A pure function over relative `<kind>/<uuid>.jpg` paths — no disk, no Room — because this is the
 * part of restore that is worth being certain about and the part a phone makes awkward to watch.
 *
 * The **scope is what splits disk-only files in two**, and it has to come from the manifest rather
 * than be inferred from what the archive happens to contain: an Everything export of a phone with no
 * photos yet is authoritative about `photos/` and carries none, which is a different claim from an
 * Essential export that never looked.
 */
fun planMediaMerge(
    archivePaths: Collection<String>,
    diskPaths: Collection<String>,
    scope: BackupScope,
): MediaMergePlan {
    val fromArchive = archivePaths.toSet()
    val (covered, uncovered) = diskPaths.filterNot { it in fromArchive }.partition { scope.coversPath(it) }

    return MediaMergePlan(
        overlaid = fromArchive.sorted(),
        kept = uncovered.sorted(),
        orphaned = covered.sorted(),
    )
}

/** Whether [relativePath]'s kind is one this scope's archive is authoritative about. */
private fun BackupScope.coversPath(relativePath: String): Boolean {
    val directory = relativePath.substringBefore('/')
    return mediaKinds.any { kind: MediaKind -> kind.directory == directory }
}
