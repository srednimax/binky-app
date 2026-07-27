package app.binky.tracker.data.backup

import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.PRESERVED_TIMESTAMP_FORMAT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where an export lands before the share sheet picks it up, relative to `cacheDir`. */
const val EXPORTS_DIRECTORY = "exports"

/**
 * The filename an export gets. The scope is in it **for humans** — so two files in a downloads
 * folder can be told apart — and is never what a restore reads.
 */
fun exportFileName(
    scope: BackupScope,
    at: Instant,
): String = "bunny-${scope.slug}-${PRESERVED_TIMESTAMP_FORMAT.format(at)}.zip"

/**
 * Writes a backup zip at a chosen [BackupScope] (ADR-0005).
 *
 * Takes paths rather than a `Context`, for the same reason [mediaFilesFor] does: the pieces this is
 * built from are the pieces 3e's backup agent needs, and an agent cannot reach the app's container.
 *
 * @param scratchDir somewhere disposable for the checkpointed database copy. Never the destination's
 *   own directory — an export lands in `cache/exports` and a pre-restore snapshot lands in
 *   `preserved/`, and `preserved/` holds recovery artifacts only.
 * @param checkpoint how the database is made whole before it is zipped. Defaults to the real WAL
 *   checkpoint, and is a parameter so the archive's *layout* can be tested on the JVM, where
 *   `android.database.sqlite` does not exist. The default is the production wiring, so a caller
 *   cannot forget it by omission — the seam is for substituting the checkpoint, never for skipping it.
 */
class BackupExporter(
    private val databaseFile: File,
    private val filesDir: File,
    private val scratchDir: File,
    private val schemaVersion: Int = BUNNY_SCHEMA_VERSION,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val checkpoint: (File, File) -> Unit = ::checkpointDatabaseTo,
) {
    /**
     * Build the archive at [target] and return it.
     *
     * Written to a `.part` alongside and renamed on success, so an export interrupted half way
     * leaves no file that looks shareable and is not. [now] stays a parameter so tests can pin it.
     *
     * Kotlin note: `withContext(io)` moves the zipping onto the IO dispatcher and suspends the
     * caller until it finishes — unlike an `async` function in JS, calling this starts nothing on
     * its own and there is no promise to await.
     */
    suspend fun exportTo(
        target: File,
        scope: BackupScope,
        now: Instant = Instant.now(),
    ): File =
        withContext(io) {
            target.parentFile?.mkdirs()
            scratchDir.mkdirs()

            val staged = File(scratchDir, "export-staged.db")
            val part = File(target.path + ".part")
            try {
                // The checkpoint has to happen before the entry is written, not after: the file
                // being zipped is the copy, and the copy is only whole once the log is folded in.
                checkpoint(databaseFile, staged)

                val media = mediaFilesFor(scope, filesDir)
                val manifest =
                    BackupManifest(
                        scope = scope,
                        schemaVersion = schemaVersion,
                        createdAtEpochMilli = now.toEpochMilli(),
                        mediaCounts =
                            scope.mediaKinds.associate { kind ->
                                kind.name to media.count { it.entryName.startsWith("${kind.directory}/") }
                            },
                    )

                ZipOutputStream(part.outputStream().buffered()).use { zip ->
                    // Most of the bytes here are already-compressed JPEGs, where a slower deflate
                    // buys nothing. The database, which does compress, still compresses well at this
                    // level, and an export the owner is waiting on is the wrong place to spend CPU.
                    zip.setLevel(Deflater.BEST_SPEED)

                    // The manifest goes in first so a reader looking only for it can stop early.
                    zip.write(BACKUP_MANIFEST_ENTRY, encodeManifest(manifest).toByteArray())
                    zip.write(BACKUP_DATABASE_ENTRY, staged)

                    val preferences = File(filesDir, PREFERENCES_FILE_PATH)
                    // Absent on a phone that has never written a preference — an ordinary state, and
                    // a restore that finds no preferences entry simply leaves the defaults alone.
                    if (preferences.isFile) zip.write(BACKUP_PREFERENCES_ENTRY, preferences)

                    media.forEach { zip.write(it.entryName, it.file) }
                }

                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                target
            } catch (e: Throwable) {
                part.delete()
                throw e
            } finally {
                staged.delete()
            }
        }
}

private fun ZipOutputStream.write(
    entryName: String,
    bytes: ByteArray,
) {
    putNextEntry(ZipEntry(entryName))
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.write(
    entryName: String,
    file: File,
) {
    putNextEntry(ZipEntry(entryName))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}
