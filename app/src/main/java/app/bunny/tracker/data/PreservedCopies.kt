package app.bunny.tracker.data

import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * One copy taken aside before a destructive wipe (ADR-0007), as Settings lists it.
 *
 * [files] is the `.db` **plus whichever sidecars exist**, because that is the unit that has to move
 * together: sharing the `.db` alone can hand over a file missing the last session's writes, and
 * deleting the `.db` alone leaves orphaned sidecars that look like a copy but are not one.
 */
data class PreservedCopy(
    val file: File,
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
 * the first time a wipe is prepared.
 */
fun listPreservedCopies(preservedDir: File): List<PreservedCopy> {
    val entries = preservedDir.listFiles() ?: return emptyList()
    return entries
        .filter { it.isFile && it.name.startsWith(PRESERVED_PREFIX) && it.name.endsWith(PRESERVED_SUFFIX) }
        .map { file ->
            PreservedCopy(
                file = file,
                savedAt = savedAtFromName(file.name),
                files = listOf(file) + PRESERVED_SIDECAR_SUFFIXES.map { File(file.path + it) }.filter { it.isFile },
            )
        }
        // The name sorts chronologically by construction, so this needs no date parsing to be right
        // — which also means a copy with an unparseable name still lands somewhere sensible.
        .sortedByDescending { it.name }
}

/** Deletes a copy and its sidecars together. Silent about files already gone. */
fun deletePreservedCopy(copy: PreservedCopy) {
    copy.files.forEach { it.delete() }
}

private fun savedAtFromName(name: String): Instant? =
    try {
        Instant.from(
            PRESERVED_TIMESTAMP_FORMAT.parse(name.removePrefix(PRESERVED_PREFIX).removeSuffix(PRESERVED_SUFFIX)),
        )
    } catch (e: DateTimeParseException) {
        null
    }
