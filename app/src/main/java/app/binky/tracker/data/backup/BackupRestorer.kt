package app.binky.tracker.data.backup

import android.content.Context
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.PRESERVED_SIDECAR_SUFFIXES
import app.binky.tracker.data.PRESERVED_TIMESTAMP_FORMAT
import app.binky.tracker.data.SNAPSHOT_PREFIX
import app.binky.tracker.data.SNAPSHOT_SUFFIX
import app.binky.tracker.data.buildBunnyDatabase
import app.binky.tracker.data.readUserVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/** Where a restore assembles the incoming database before it is allowed anywhere near the live one. */
const val STAGED_DATABASE_FILE = "restore-staged.db"

/**
 * The most an archive is allowed to unpack to.
 *
 * Not a security boundary so much as a floor under the worst case: a malformed or hostile zip must
 * not be able to fill the device before anyone notices. Two gigabytes is far above any real gallery
 * — at ADR-0020's 2048px/q85 that is several thousand photos — and far below "the phone is full".
 */
const val RESTORE_MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024

/** What a restore did, or why it declined to do anything. */
sealed interface RestoreOutcome {
    /**
     * The database was replaced and the media merged.
     *
     * @param snapshot the Essential-scope export of the *previous* state, in `preserved/`. Undoing a
     *   bad restore is then the ordinary restore path rather than a recovery procedure with `adb`
     *   in it.
     */
    data class Restored(
        val manifest: BackupManifest,
        val merge: MediaMergePlan,
        val snapshot: File,
    ) : RestoreOutcome

    /** Nothing on the phone was touched. */
    data class Refused(
        val reason: RestoreRefusal,
    ) : RestoreOutcome
}

/**
 * Why a restore declined. Each maps to one sentence the owner can act on — there is deliberately no
 * "unknown error", because a restore that fails vaguely is a restore nobody trusts again.
 */
enum class RestoreRefusal {
    /** No manifest, or no database inside. Refused **by name**, not partially applied. */
    NotABinkyBackup,

    /** A newer archive format or a newer schema. No migration runs backwards. */
    MadeByANewerBinky,

    /** Over [RESTORE_MAX_TOTAL_BYTES]. */
    TooLarge,

    /** The database inside could not be opened by this build. The live one is untouched. */
    Unreadable,
}

/**
 * Replaces the database from an archive and merges its media back in (ADR-0005).
 *
 * **Stage, migrate, swap** (ADR-0023). The incoming database is unzipped to a staging file beside
 * the live one, opened with the real migrations so it arrives at the current schema, and only then
 * swapped in. Every way this can fail lands on the copy, before anything on the phone has been
 * touched.
 *
 * This deliberately does *not* compare version numbers and stop there. That only asserts a migration
 * exists, never that it survives *this* file — and written as "refuse anything that isn't this
 * build's version" it would make every existing backup unrestorable the day 1.1 bumps the schema,
 * inverting ADR-0005's whole promise.
 */
class BackupRestorer(
    context: Context,
    private val filesDir: File,
    private val preservedDir: File,
    private val scratchDir: File,
    private val exporter: BackupExporter,
    private val databaseName: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    private val liveDatabase: File get() = appContext.getDatabasePath(databaseName)

    private val stagedDatabase: File get() = appContext.getDatabasePath(STAGED_DATABASE_FILE)

    /**
     * What the archive claims to be, for the confirmation dialog — *"[scope] backup from [date]"*.
     *
     * Null for anything that is not a Binky backup. [open] is a factory rather than a stream because
     * the restore reads the archive twice and a `content://` stream cannot be rewound.
     */
    suspend fun readManifest(open: () -> InputStream): BackupManifest? =
        withContext(io) {
            var manifest: BackupManifest? = null
            forEachEntry(open) { entry, stream ->
                if (entry is ArchiveEntry.Manifest) manifest = decodeManifest(stream.readBytes().decodeToString())
                // The manifest is written first, so an archive of ours stops here on entry one.
                manifest == null
            }
            manifest
        }

    suspend fun restore(
        open: () -> InputStream,
        now: Instant = Instant.now(),
    ): RestoreOutcome =
        withContext(io) {
            clearStaging()
            val stagedPreferences = File(scratchDir, "restore-preferences.pb")
            stagedPreferences.delete()

            // Pass one reads the whole archive: it lands the two small members, counts every byte it
            // would ever write, and notes which media entries exist — all before anything on the
            // phone is touched. The media bytes themselves wait for pass two, so a big gallery is
            // not held on disk twice.
            var manifest: BackupManifest? = null
            var sawDatabase = false
            val mediaEntries = mutableListOf<String>()
            var totalBytes = 0L
            var tooLarge = false

            forEachEntry(open) { entry, stream ->
                when (entry) {
                    is ArchiveEntry.Manifest ->
                        stream.readBytes().let { bytes ->
                            totalBytes += bytes.size
                            manifest = decodeManifest(bytes.decodeToString())
                        }

                    is ArchiveEntry.Database -> {
                        sawDatabase = true
                        totalBytes += stream.drainTo(stagedDatabase)
                    }

                    is ArchiveEntry.Preferences -> totalBytes += stream.drainTo(stagedPreferences)

                    is ArchiveEntry.Media -> {
                        mediaEntries += entry.relativePath
                        totalBytes += stream.discard()
                    }
                }
                if (totalBytes > RESTORE_MAX_TOTAL_BYTES) tooLarge = true
                !tooLarge
            }

            if (tooLarge) return@withContext refuse(RestoreRefusal.TooLarge, stagedPreferences)
            // No manifest, or no database, means *"this file is not a Binky backup"* — refused by
            // name rather than partially applied.
            val declared = manifest
            if (declared == null || !sawDatabase) {
                return@withContext refuse(RestoreRefusal.NotABinkyBackup, stagedPreferences)
            }
            if (declared.format > BACKUP_FORMAT_VERSION) {
                return@withContext refuse(RestoreRefusal.MadeByANewerBinky, stagedPreferences)
            }

            // The *file's* header is the authority on its schema, not the manifest's claim about it:
            // the manifest is data an owner could have edited, and Room is about to be pointed at
            // the file either way. A newer version is refused outright — no migration runs backwards.
            if (readUserVersion(stagedDatabase) > BUNNY_SCHEMA_VERSION) {
                return@withContext refuse(RestoreRefusal.MadeByANewerBinky, stagedPreferences)
            }

            if (!migrateStagedDatabase()) return@withContext refuse(RestoreRefusal.Unreadable, stagedPreferences)

            // Only now, with a staged database this build has actually opened, is the restore going
            // to happen — so this is where the way back gets built. Taking it earlier would leave
            // `preserved/` littered with snapshots of restores that never occurred.
            val snapshot =
                exporter.exportTo(
                    target =
                        File(preservedDir, "$SNAPSHOT_PREFIX${PRESERVED_TIMESTAMP_FORMAT.format(now)}$SNAPSHOT_SUFFIX"),
                    scope = BackupScope.Essential,
                    now = now,
                )

            // Listed before the overlay, or every incoming file would look like one that was already
            // here.
            val merge =
                planMediaMerge(
                    archivePaths = mediaEntries,
                    diskPaths = mediaPathsOnDisk(filesDir),
                    scope = declared.scope,
                )

            swapInStagedDatabase()
            extractMedia(open)
            if (stagedPreferences.isFile) {
                val preferences = File(filesDir, PREFERENCES_FILE_PATH)
                preferences.parentFile?.mkdirs()
                stagedPreferences.copyTo(preferences, overwrite = true)
                stagedPreferences.delete()
            }

            RestoreOutcome.Restored(manifest = declared, merge = merge, snapshot = snapshot)
        }

    /**
     * Opens the staged copy with the **real** migrations, so it arrives at this build's schema
     * before it is swapped in. Returns false when this build cannot open it at all.
     *
     * `allowDestructiveMigration = false` is pinned here rather than inherited (ADR-0023). Left to
     * default, a debug build would take `BuildConfig.DEBUG`'s answer and **quietly empty the very
     * file it was asked to restore** — the owner would watch a successful restore produce an empty
     * app. That is the trap the parameter on `buildBunnyDatabase` exists for.
     */
    private fun migrateStagedDatabase(): Boolean =
        try {
            val database =
                buildBunnyDatabase(
                    context = appContext,
                    databaseName = STAGED_DATABASE_FILE,
                    allowDestructiveMigration = false,
                )
            // `RoomDatabase` is not `Closeable`, so there is no `use` to lean on. Closing matters
            // here beyond tidiness: a clean close folds the log back into the file, which is what
            // makes the swap below a single-file move.
            try {
                database.openHelper.writableDatabase
            } finally {
                database.close()
            }
            true
        } catch (e: RuntimeException) {
            // Room reports a missing migration, a corrupt file and a downgrade all as unchecked
            // exceptions of several types. What they have in common is the only thing that matters:
            // the copy did not open, and nothing live has been touched.
            false
        }

    /**
     * Replaces the live database file with the staged one.
     *
     * The sidecars are deleted rather than moved: a closed SQLite database has already folded its
     * log into the main file, so a surviving `-wal` from the *old* database beside the *new* one
     * would be a log belonging to a file that no longer exists.
     */
    private fun swapInStagedDatabase() {
        val live = liveDatabase
        val staged = stagedDatabase
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(live.path + it).delete() }
        live.delete()
        if (!staged.renameTo(live)) {
            staged.copyTo(live, overwrite = true)
            staged.delete()
        }
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(staged.path + it).delete() }
    }

    /**
     * Pass two: the media, straight into place.
     *
     * Written directly under `filesDir` rather than staged first, because the paths were *matched*
     * against the allowlist rather than sanitised — `<kind.directory>/<uuid>.jpg` and nothing else —
     * and staging a gallery would hold it on disk twice for no gain.
     */
    private fun extractMedia(open: () -> InputStream) {
        forEachEntry(open) { entry, stream ->
            if (entry is ArchiveEntry.Media) stream.drainTo(File(filesDir, entry.relativePath))
            true
        }
    }

    private fun refuse(
        reason: RestoreRefusal,
        stagedPreferences: File,
    ): RestoreOutcome {
        clearStaging()
        stagedPreferences.delete()
        return RestoreOutcome.Refused(reason)
    }

    private fun clearStaging() {
        val staged = stagedDatabase
        staged.delete()
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(staged.path + it).delete() }
    }
}

/**
 * Walks an archive, handing the reader every entry the allowlist recognises and silently skipping
 * everything else.
 *
 * [onEntry] returns false to stop early. `ZipInputStream` is sequential and the entry's own stream
 * must be consumed or skipped before the next one is asked for, which is why the body is a callback
 * rather than a sequence the caller could hold on to past its turn.
 */
private inline fun forEachEntry(
    open: () -> InputStream,
    onEntry: (ArchiveEntry, InputStream) -> Boolean,
) {
    ZipInputStream(open().buffered()).use { zip ->
        while (true) {
            val next = zip.nextEntry ?: return
            if (!next.isDirectory) {
                val entry = archiveEntryFor(next.name)
                if (entry != null && !onEntry(entry, zip)) return
            }
            zip.closeEntry()
        }
    }
}

/** Writes the entry to [target], creating its directory, and returns how many bytes that was. */
private fun InputStream.drainTo(target: File): Long {
    target.parentFile?.mkdirs()
    return target.outputStream().use { copyTo(it) }
}

/** Reads the entry without keeping it, and returns how many bytes it would have been. */
private fun InputStream.discard(): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return total
        total += read
    }
}
