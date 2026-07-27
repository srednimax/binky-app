package app.binky.tracker.data

import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * What a file in `preserved/` is, and therefore what can be done with it.
 *
 * The directory holds two occupants with **opposite properties**, and a listing that did not say
 * which was which would offer the owner a "Restore" button that works on one row and cannot work on
 * the other.
 */
enum class PreservedKind {
    /**
     * ADR-0007's copy, taken aside before a destructive wipe. At a **stale schema**, so it is a
     * recovery artifact and not a restore: reading old data into a new schema *is* a migration.
     * Shareable, never restorable in place.
     */
    WipeCopy,

    /**
     * The Essential-scope export a restore takes of the state it is about to replace (ADR-0005). At
     * the **current schema**, so undoing a bad restore is the ordinary restore path rather than a
     * recovery procedure with `adb` in it.
     */
    RestoreSnapshot,
    ;

    /** Whether the app can load this back in, as opposed to only handing it to the owner. */
    val restorable: Boolean get() = this == RestoreSnapshot
}

/**
 * One occupant of `preserved/`, as Settings lists it.
 *
 * [files] is the file **plus whichever sidecars exist**, because that is the unit that has to move
 * together: sharing a `.db` alone can hand over a file missing the last session's writes, and
 * deleting the `.db` alone leaves orphaned sidecars that look like a copy but are not one. A
 * snapshot is a single zip and needs none of that, which the same list expresses at length one.
 */
data class PreservedCopy(
    val file: File,
    val kind: PreservedKind,
    /**
     * When the *data* was last written, read back out of the filename.
     *
     * Deliberately not the copy's own `lastModified`, which only dates the moment the copy was
     * taken — `preserveBeforeWipe` names the file from the database's modification time precisely so
     * that a hesitating owner relaunching repeatedly overwrites one copy rather than minting a new
     * one per launch. Null when the name does not parse, which means a file this app did not write.
     */
    val savedAt: Instant?,
    val files: List<File>,
) {
    val name: String get() = file.name

    val totalBytes: Long get() = files.sumOf { it.length() }
}

/**
 * Every preserved copy on this phone, newest first.
 *
 * Returns empty for a directory that does not exist — the ordinary case, since it is only created
 * the first time a wipe is prepared or a restore is run.
 */
fun listPreservedCopies(preservedDir: File): List<PreservedCopy> {
    val entries = preservedDir.listFiles() ?: return emptyList()
    return entries
        .filter { it.isFile }
        .mapNotNull(::preservedCopyOrNull)
        // Sorted on the parsed date rather than on the name: the two prefixes no longer sort
        // chronologically against each other ("bunny-before-restore-…" and "bunny-2026…" differ in
        // their fourth character), so the name is no longer a stand-in for the date. A file whose
        // name does not parse still lands somewhere sensible — last, with the oldest.
        .sortedWith(compareByDescending<PreservedCopy> { it.savedAt ?: Instant.EPOCH }.thenByDescending { it.name })
}

/**
 * One preserved file described the way the listing describes it — kind resolved, sidecars gathered,
 * date read back out of the name.
 *
 * Used by [listPreservedCopies] and by the schema-mismatch screen, which holds the single copy
 * `preserveBeforeWipe` just took and has to offer the same shareable unit without listing the
 * directory it landed in.
 */
fun preservedCopyOf(file: File): PreservedCopy = preservedCopyOrNull(file) ?: wipeCopy(file)

/** Null for a file this app did not put there — the listing shows only what it can describe. */
private fun preservedCopyOrNull(file: File): PreservedCopy? =
    when {
        file.name.startsWith(SNAPSHOT_PREFIX) && file.name.endsWith(SNAPSHOT_SUFFIX) ->
            PreservedCopy(
                file = file,
                kind = PreservedKind.RestoreSnapshot,
                savedAt = savedAtFromName(file.name, SNAPSHOT_PREFIX, SNAPSHOT_SUFFIX),
                // A zip is already one file. Nothing travels beside it.
                files = listOf(file),
            )

        file.name.startsWith(PRESERVED_PREFIX) && file.name.endsWith(PRESERVED_SUFFIX) -> wipeCopy(file)

        else -> null
    }

private fun wipeCopy(file: File): PreservedCopy =
    PreservedCopy(
        file = file,
        kind = PreservedKind.WipeCopy,
        savedAt = savedAtFromName(file.name, PRESERVED_PREFIX, PRESERVED_SUFFIX),
        files = listOf(file) + PRESERVED_SIDECAR_SUFFIXES.map { File(file.path + it) }.filter { it.isFile },
    )

/** Deletes a copy and its sidecars together. Silent about files already gone. */
fun deletePreservedCopy(copy: PreservedCopy) {
    copy.files.forEach { it.delete() }
}

private fun savedAtFromName(
    name: String,
    prefix: String,
    suffix: String,
): Instant? =
    try {
        Instant.from(PRESERVED_TIMESTAMP_FORMAT.parse(name.removePrefix(prefix).removeSuffix(suffix)))
    } catch (e: DateTimeParseException) {
        null
    }
