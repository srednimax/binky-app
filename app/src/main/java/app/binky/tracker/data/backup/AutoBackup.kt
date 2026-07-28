package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/*
 * Everything Android's Auto Backup needs, as **functions over `File`** (ADR-0005).
 *
 * Nothing here imports Android, and that is the whole point rather than a happy accident. When the
 * system starts the process *for* backup it binds the base `android.app.Application` instead of
 * `BinkyApplication`, so there is no `AppContainer` to reach for — and reaching for one would force
 * the `lazy` that ADR-0007 makes the structural guard standing in front of a wipe. The failure
 * ordering is what makes this worth building structurally: Auto Backup runs when the device is idle
 * and charging, while `bmgr backupnow` runs with the app on screen, so a container-dependent agent
 * passes every test done by hand and fails only in production, silently.
 *
 * `BinkyBackupAgent` is therefore a thin shell over this file. "Cannot reach the container" is a
 * property of the types here, not a rule someone has to remember — the same move ADR-0007 made when
 * it rejected a guard by discipline.
 */

/**
 * Where the checkpointed database copy waits while Auto Backup reads it, relative to `filesDir`.
 *
 * The live file is **never** handed over, nor its `-wal`/`-shm` sidecars: a `-wal` captured mid-write
 * restores corrupt, and the `.db` on its own is missing whatever the log still holds. Checkpointing
 * into a copy is the only shape that is whole (ADR-0005).
 *
 * It sits under `filesDir` rather than in `cacheDir` for the restore's sake. `fullBackupFile` records
 * the file's domain and relative path, and the far end puts it back at the same relative path — so a
 * copy staged here comes back here, where [adoptRestoredDatabase] can move it into place. A copy
 * staged in the cache would come back into a directory the OS may empty at will.
 */
const val AUTO_BACKUP_STAGING_PATH = "autobackup/bunny.db"

/**
 * The marker file's name under `filesDir`: when Auto Backup last ran, so Backup settings can say so.
 *
 * A plain file rather than the app's DataStore, because the agent cannot reach the app's instance
 * (see above) and its writes are `suspend` inside blocking backup callbacks. ADR-0005 requires the
 * marker to live **outside the database**, which a restore replaces wholesale; this satisfies that
 * and stays readable from both sides.
 */
const val AUTO_BACKUP_MARKER_FILE = "auto-backup-marker.txt"

/**
 * Past this, the status line stops showing a bare date and says it is stale (ADR-0005). Auto Backup
 * aims for roughly daily, so a technically true timestamp from two months ago is a worse signal than
 * an admission.
 */
val AUTO_BACKUP_STALE_AFTER: Duration = Duration.ofDays(14)

private const val MARKER_LAST_BACKUP_KEY = "lastBackupAtEpochMilli"

/**
 * The set of files Auto Backup carries, ADR-0005's evidential core.
 *
 * Unconditional: the database (as [stagedDatabase], already checkpointed), the preferences, and
 * `avatars/`. Absent by construction, because a file that is not returned here is a file the agent
 * never offers:
 *
 * - **`photos/`**, unless [includePhotos] — the per-app quota is small and Android rejects the
 *   *entire* over-quota dataset rather than trimming it, so a growing gallery would one day take the
 *   database down with it. That gap is stated in words on the Backup screen rather than left to be
 *   discovered.
 * - **`preserved/`**, for a different reason (ADR-0007): it is the app's one unbounded, never-pruned
 *   directory, and admitting an unbounded set into an all-or-nothing quota means one day losing the
 *   database in order to have protected a duplicate. The owner's *share* tap is what makes a
 *   preserved copy safe.
 * - **the marker itself**, so it cannot travel onto another phone and vouch there for a backup that
 *   phone never made.
 * - **`documents/`** — Phase 5's, with the newest-first admission ceiling ADR-0005 describes. The
 *   directory is empty until then, so a ceiling built now would admit nothing and be untestable.
 *
 * @param includePhotos true only for a **device-to-device transfer**, which has no cloud account and
 *   no quota, so neither reason above applies and silently dropping a whole gallery on a phone
 *   upgrade would be the worse failure. This is the distinction the two template XML files used to
 *   draw between `cloud-backup` and `device-transfer`, kept when they were deleted.
 */
fun autoBackupFileSet(
    filesDir: File,
    stagedDatabase: File,
    includePhotos: Boolean,
): List<File> {
    // Kotlin note: `buildList` is the idiomatic build-then-freeze — a mutable list inside the
    // lambda, an immutable `List` out of it, so no caller can add `photos/` to a set after the fact.
    val kinds =
        buildList {
            add(MediaKind.Avatar)
            if (includePhotos) add(MediaKind.Photo)
        }

    return buildList {
        // Missing on a phone whose database has never been opened. Nothing to back up is an
        // ordinary state, not an error.
        if (stagedDatabase.isFile) add(stagedDatabase)
        val preferences = File(filesDir, PREFERENCES_FILE_PATH)
        if (preferences.isFile) add(preferences)
        // The same uuid allowlist the export uses, so a stray file in a media directory is skipped
        // here rather than shipped and then refused at the far end.
        addAll(mediaFilesFor(kinds, filesDir).map { it.file })
    }
}

/** When Auto Backup last ran on this phone. Phase 5 adds the excluded-document count beside it. */
data class AutoBackupMarker(
    val lastBackupAt: Instant,
)

/**
 * Write the marker, **temp-then-rename**, so a process killed mid-write leaves the previous marker
 * standing rather than a half-written one that parses as garbage and renders as "never".
 */
fun writeAutoBackupMarker(
    filesDir: File,
    at: Instant,
) {
    val marker = File(filesDir, AUTO_BACKUP_MARKER_FILE)
    val part = File(filesDir, "$AUTO_BACKUP_MARKER_FILE.part")
    try {
        filesDir.mkdirs()
        part.writeText("$MARKER_LAST_BACKUP_KEY=${at.toEpochMilli()}\n")
        if (!part.renameTo(marker)) {
            part.copyTo(marker, overwrite = true)
            part.delete()
        }
    } catch (e: IOException) {
        // A marker that could not be written is not a reason to fail a backup that otherwise
        // worked. The cost is one status line reading "no automatic backup has been recorded",
        // which is the honest failure direction: it understates the net rather than overstating it.
        part.delete()
    }
}

/**
 * Read the marker, or null when there is none.
 *
 * Line-based `key=value` with unknown keys ignored, so Phase 5 can add the excluded-document count
 * without a marker written by 1.0 becoming unreadable. Anything that does not parse is treated as
 * absent — the state this app can always describe truthfully.
 */
fun readAutoBackupMarker(filesDir: File): AutoBackupMarker? {
    val marker = File(filesDir, AUTO_BACKUP_MARKER_FILE)
    if (!marker.isFile) return null
    return try {
        val values =
            marker
                .readLines()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }.toMap()
        values[MARKER_LAST_BACKUP_KEY]?.trim()?.toLongOrNull()?.let { AutoBackupMarker(Instant.ofEpochMilli(it)) }
    } catch (e: IOException) {
        null
    }
}

/** Called from `onRestoreFinished()` — see [AutoBackupStatus] for why a restore has to clear it. */
fun clearAutoBackupMarker(filesDir: File) {
    File(filesDir, AUTO_BACKUP_MARKER_FILE).delete()
}

/**
 * What Backup settings says about the automatic net — **all three states in words** (ADR-0005).
 *
 * [NeverRecorded] is the one that has to be said out loud. Auto Backup runs only with backup enabled,
 * an account signed in, and the phone idle, charging and online, and Android exposes no reliable way
 * to ask whether this app's data is actually being included. A blank status line then reads as a
 * working net, which is ADR-0001's silence failure pointed at backup.
 *
 * The marker is never in the agent's file set, so it cannot travel to another phone at all; and
 * `onRestoreFinished()` clears it anyway, for a second and different reason — after a restore this
 * phone no longer holds the data the old marker vouched for. Two mechanisms failing differently: the
 * exclusion is a static claim a later edit could break, the clear is a runtime guarantee at the exact
 * event.
 */
sealed interface AutoBackupStatus {
    data object NeverRecorded : AutoBackupStatus

    data class Recorded(
        val at: Instant,
        val stale: Boolean,
    ) : AutoBackupStatus
}

fun autoBackupStatus(
    marker: AutoBackupMarker?,
    now: Instant,
): AutoBackupStatus {
    if (marker == null) return AutoBackupStatus.NeverRecorded
    // A marker dated in the future is a clock that moved, not a backup that has not happened. It is
    // reported as fresh: `isNegative` age is below any threshold, and inventing a fourth state for a
    // wrong clock would cost copy in every language to describe something the owner cannot act on.
    val age = Duration.between(marker.lastBackupAt, now)
    return AutoBackupStatus.Recorded(at = marker.lastBackupAt, stale = age > AUTO_BACKUP_STALE_AFTER)
}

/**
 * Move a restored database copy into place, and report whether it was there.
 *
 * `onRestoreFinished()` normally does this the moment the restore ends, before the app has ever been
 * launched. This runs at process start as the backstop: if that callback never fired — it is a
 * platform promise, made by a system that also has to survive an interrupted restore — the restored
 * history would otherwise sit in a staging directory forever while the app opened onto an empty
 * database. Losing a restore *silently* is the failure ADR-0005 exists to prevent, so it gets two
 * mechanisms, in the same shape as the marker.
 *
 * **Adopts only when there is no live database**, which is exactly the post-restore state: a fresh
 * install, restored into, opened for the first time. A staged file found beside a database that
 * already exists is left alone, because overwriting real records with a stale copy would be the more
 * expensive mistake of the two by far.
 */
fun adoptRestoredDatabase(
    filesDir: File,
    databaseFile: File,
): Boolean {
    val staged = File(filesDir, AUTO_BACKUP_STAGING_PATH)
    if (!staged.isFile) return false
    if (databaseFile.isFile) {
        // Not ours to resolve, but not ours to keep either: the copy is stale by definition here.
        staged.delete()
        staged.parentFile?.delete()
        return false
    }

    databaseFile.parentFile?.mkdirs()
    staged.copyTo(databaseFile, overwrite = true)
    // Sidecars left by whatever was here before describe a file that no longer exists. The copy is
    // already checkpointed, so it needs none of its own.
    File("${databaseFile.path}-wal").delete()
    File("${databaseFile.path}-shm").delete()
    staged.delete()
    staged.parentFile?.delete()
    return true
}
