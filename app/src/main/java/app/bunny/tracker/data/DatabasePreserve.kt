package app.bunny.tracker.data

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Where preserved copies land, relative to `filesDir` — alongside the media directories. */
const val PRESERVED_DIRECTORY = "preserved"

/**
 * SQLite writes `user_version` as a big-endian 32-bit integer at byte 60 of the 100-byte file
 * header, and Room uses that field as its schema version. Reading it is therefore a four-byte read
 * that needs neither Room nor SQLite — which is the whole point: it has to happen *before* Room
 * opens the file and wipes it.
 */
private const val USER_VERSION_OFFSET = 60L
private const val SQLITE_HEADER_BYTES = 100

/** Filesystem-safe and sorts chronologically. Colons in a filename are a portability trap. */
private val TIMESTAMP_FORMAT =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/**
 * Reads the schema version recorded in [databaseFile]'s header.
 *
 * Returns 0 for a file that does not exist, is empty, or is too short to hold a header — all of
 * which mean "there is nothing here to lose", which is exactly how a fresh install looks.
 */
fun readUserVersion(databaseFile: File): Int {
    if (!databaseFile.isFile || databaseFile.length() < SQLITE_HEADER_BYTES) return 0
    return RandomAccessFile(databaseFile, "r").use { file ->
        file.seek(USER_VERSION_OFFSET)
        file.readInt() // RandomAccessFile.readInt is big-endian, which matches the header
    }
}

/**
 * Copies the database aside if opening it with this build would destroy it, and returns the copy.
 * Returns null when there is nothing to preserve — no file yet, or a file already at this version.
 *
 * ADR-0007: a destructive wipe never loses the file, in any phase. The **consent** half — the
 * blocking screen that asks first — arrives in Phase 2, when the database holds a weight series
 * that cannot be retyped. In Phase 1 it would fire on every entity added, to guard a bunny name and
 * a birthdate, and the realistic outcome is that it gets disabled before the phase where it counts.
 *
 * The preserved file is a **recovery artifact, not a restore**: reading old data into a new schema
 * *is* a migration, so it cannot be re-imported automatically.
 */
fun preserveBeforeWipe(
    databaseFile: File,
    preservedDir: File,
    appSchemaVersion: Int = BUNNY_SCHEMA_VERSION,
    timestamp: Instant = Instant.now(),
): File? {
    val onDisk = readUserVersion(databaseFile)
    // A *newer* on-disk version is preserved too: Room destroys a downgrade just as thoroughly.
    if (onDisk == 0 || onDisk == appSchemaVersion) return null

    preservedDir.mkdirs()
    val preserved = File(preservedDir, "bunny-${TIMESTAMP_FORMAT.format(timestamp)}.db")
    databaseFile.copyTo(preserved, overwrite = true)

    // In WAL mode the most recent writes may live only in the -wal sidecar, so a copy of the .db
    // alone can be missing the very data worth preserving. Both sidecars travel with it.
    for (suffix in listOf("-wal", "-shm")) {
        val sidecar = File(databaseFile.path + suffix)
        if (sidecar.isFile) sidecar.copyTo(File(preserved.path + suffix), overwrite = true)
    }
    return preserved
}
