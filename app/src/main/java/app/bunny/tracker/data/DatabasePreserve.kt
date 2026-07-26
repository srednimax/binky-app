package app.bunny.tracker.data

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Where preserved copies land, relative to `filesDir` — alongside the media directories. */
const val PRESERVED_DIRECTORY = "preserved"

/** How a preserved copy is named. Settings matches on these to find them again. */
internal const val PRESERVED_PREFIX = "bunny-"
internal const val PRESERVED_SUFFIX = ".db"

/**
 * In WAL mode the most recent writes may live only in a sidecar, so a copy of the `.db` alone can be
 * missing the very data worth preserving. Both travel with it, and both have to travel again when
 * the owner shares the copy off the phone.
 */
internal val PRESERVED_SIDECAR_SUFFIXES = listOf("-wal", "-shm")

/**
 * SQLite writes `user_version` as a big-endian 32-bit integer at byte 60 of the 100-byte file
 * header, and Room uses that field as its schema version. Reading it is therefore a four-byte read
 * that needs neither Room nor SQLite — which is the whole point: it has to happen *before* Room
 * opens the file and wipes it.
 */
private const val USER_VERSION_OFFSET = 60L
private const val SQLITE_HEADER_BYTES = 100

/**
 * Filesystem-safe and sorts chronologically. Colons in a filename are a portability trap.
 *
 * `internal` rather than private because Settings reads the date back *out* of the filename when it
 * lists the copies — the name dates the data, where the copy's own `lastModified` only dates the
 * moment it was written.
 */
internal val PRESERVED_TIMESTAMP_FORMAT: DateTimeFormatter =
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
 * ADR-0007: a destructive wipe never loses the file, in any phase. Its **consent** half — the
 * blocking screen in front of this — is wired up by `BunnyTrackerApplication`, which is where this
 * runs: before Room exists, let alone opens anything.
 *
 * The copy is named from the database file's own [File.lastModified] rather than the moment of
 * panic, so a hesitating owner who relaunches repeatedly **overwrites one copy instead of minting a
 * new one each time** — nothing has written to the file in between, so its modification time has not
 * moved. The name therefore dates the *data*, not the launch. [timestamp] stays a parameter only so
 * tests can pin it.
 *
 * The preserved file is a **recovery artifact, not a restore**: reading old data into a new schema
 * *is* a migration, so it cannot be re-imported automatically.
 */
fun preserveBeforeWipe(
    databaseFile: File,
    preservedDir: File,
    appSchemaVersion: Int = BUNNY_SCHEMA_VERSION,
    timestamp: Instant = Instant.ofEpochMilli(databaseFile.lastModified()),
): File? {
    val onDisk = readUserVersion(databaseFile)
    // A *newer* on-disk version is preserved too: Room destroys a downgrade just as thoroughly.
    if (onDisk == 0 || onDisk == appSchemaVersion) return null

    preservedDir.mkdirs()
    val preserved =
        File(preservedDir, "$PRESERVED_PREFIX${PRESERVED_TIMESTAMP_FORMAT.format(timestamp)}$PRESERVED_SUFFIX")
    databaseFile.copyTo(preserved, overwrite = true)

    for (suffix in PRESERVED_SIDECAR_SUFFIXES) {
        val sidecar = File(databaseFile.path + suffix)
        if (sidecar.isFile) sidecar.copyTo(File(preserved.path + suffix), overwrite = true)
    }
    return preserved
}
