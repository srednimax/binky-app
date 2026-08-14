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
 * The wire name is **frozen at `excludedDocuments`** even though the count now covers tray photos as
 * well (ADR-0029). 1.2 through 1.4 wrote markers with this key, and a renamed key would read back as
 * absent — which this file deliberately treats as *zero excluded*. Renaming it would therefore make
 * an upgraded phone claim nothing was left behind on the very run where something was, which is the
 * unverified reassurance ADR-0001 exists to forbid, arriving through a refactor.
 */
private const val MARKER_EXCLUDED_DOCUMENTS_KEY = "excludedDocuments"

/**
 * The ceiling record-grade images are admitted under, deliberately **below** the ~25 MB Auto Backup
 * quota.
 *
 * The quota is a number Android neither publishes as an API nor promises to keep, and the penalty
 * for crossing it is not a trim but a rejection of the *whole* dataset — the database included. So
 * the ceiling is a fixed figure with headroom underneath the documented one, and every byte of that
 * headroom is buying the same thing: the evidential core arriving even when this file's arithmetic
 * disagrees slightly with the transport's (compression, per-file overhead, a database that grew
 * between the set being computed and the bytes being read).
 */
const val AUTO_BACKUP_BUDGET_BYTES: Long = 20L * 1024 * 1024

/**
 * What a backup carries, and **what it had to leave behind** (ADR-0005).
 *
 * The count travels with the files rather than being recomputed by whoever wants to display it: the
 * only moment the answer is knowable is the moment the set was built, against the directory as it
 * stood then. Everything downstream — the marker, the status line, the one-time notice — reads this
 * one number, so the words and the notification cannot disagree.
 */
data class AutoBackupSet(
    val files: List<File>,
    val excludedRecords: Int,
)

/**
 * Which record-grade images fit under [budget] once the core has taken its share — **a pure function over
 * `File`s**, per ADR-0005, because the agent runs in a process where `AppContainer` does not exist
 * and reaching for one would force the `lazy` ADR-0007 guards.
 *
 * The budget is **dynamic**: they get what is left after the core, so a growing database shrinks the
 * image allowance rather than taking the whole dataset over quota. That ordering is
 * the ADR's, and it is not a preference — Android rejects an over-quota dataset entire, so admitting
 * one image too many does not cost that image, it costs the database.
 *
 * **Skips rather than stops.** One that does not fit is passed over and the walk continues, so a
 * single oversized page cannot exclude the smaller history behind it. What newest-first buys is
 * *priority* — an older image never displaces a newer one that would have fit — and that is the
 * part the order has to be trusted for.
 *
 * @param coreBytes the unconditional part: staged database, preferences, avatars. May already
 *   exceed [budget], in which case nothing is admitted rather than the arithmetic going negative.
 * @param recordsNewestFirst every record-grade image on disk — document pages and tray photos —
 *   newest first. The caller orders them: there is no database in this process to ask, so "newest"
 *   is the file's own timestamp.
 */
fun admitRecords(
    coreBytes: Long,
    recordsNewestFirst: List<File>,
    budget: Long = AUTO_BACKUP_BUDGET_BYTES,
): AutoBackupSet {
    var remaining = (budget - coreBytes).coerceAtLeast(0)
    val admitted = mutableListOf<File>()
    for (record in recordsNewestFirst) {
        val size = record.length()
        if (size <= remaining) {
            admitted += record
            remaining -= size
        }
    }
    return AutoBackupSet(files = admitted, excludedRecords = recordsNewestFirst.size - admitted.size)
}

/**
 * The set of files Auto Backup carries, ADR-0005's evidential core.
 *
 * Unconditional: the database (as [stagedDatabase], already checkpointed), the preferences, and
 * `avatars/`. Absent by construction, because a file that is not returned here is a file the agent
 * never offers:
 *
 * - **`photos/`**, unless [deviceToDeviceTransfer] — the per-app quota is small and Android rejects
 *   the *entire* over-quota dataset rather than trimming it, so a growing gallery would one day take
 *   the database down with it. That gap is stated in words on the Backup screen rather than left to
 *   be discovered.
 * - **`preserved/`**, for a different reason (ADR-0007): it is the app's one unbounded, never-pruned
 *   directory, and admitting an unbounded set into an all-or-nothing quota means one day losing the
 *   database in order to have protected a duplicate. The owner's *share* tap is what makes a
 *   preserved copy safe.
 * - **the marker itself**, so it cannot travel onto another phone and vouch there for a backup that
 *   phone never made.
 *
 * **`documents/` and `observations/` are the conditional set** (PLAN 5h, ADR-0029): admitted
 * newest-first across both by [admitRecords]
 * under what is left of [budget] after the core, and the number left behind is carried out in
 * [AutoBackupSet.excludedRecords] so it can be said in words rather than discovered at a restore.
 * The gallery's flat exclusion would have been the cheaper rule here too, and it is the wrong one —
 * a scanned prescription is the sort of thing an owner keeps precisely because it is hard to
 * reproduce, and most phones will have few enough of them to fit comfortably.
 *
 * @param deviceToDeviceTransfer a transfer straight to another phone, which has **no cloud account
 *   and no quota** — so neither the gallery's exclusion nor the record ceiling applies, and both
 *   travel whole. Silently dropping half an owner's history on a phone upgrade would be the worse
 *   failure by far. This is the distinction the two template XML files used to draw between
 *   `cloud-backup` and `device-transfer`, kept when they were deleted.
 */
fun autoBackupFileSet(
    filesDir: File,
    stagedDatabase: File,
    deviceToDeviceTransfer: Boolean,
    budget: Long = AUTO_BACKUP_BUDGET_BYTES,
): AutoBackupSet {
    // Kotlin note: `buildList` is the idiomatic build-then-freeze — a mutable list inside the
    // lambda, an immutable `List` out of it, so no caller can add `photos/` to a set after the fact.
    val kinds =
        buildList {
            add(MediaKind.Avatar)
            if (deviceToDeviceTransfer) add(MediaKind.Photo)
        }

    val core =
        buildList {
            // Missing on a phone whose database has never been opened. Nothing to back up is an
            // ordinary state, not an error.
            if (stagedDatabase.isFile) add(stagedDatabase)
            val preferences = File(filesDir, PREFERENCES_FILE_PATH)
            if (preferences.isFile) add(preferences)
            // The same uuid allowlist the export uses, so a stray file in a media directory is
            // skipped here rather than shipped and then refused at the far end.
            addAll(mediaFilesFor(kinds, filesDir).map { it.file })
        }

    val records = recordsNewestFirst(filesDir)
    val admission =
        if (deviceToDeviceTransfer) {
            AutoBackupSet(files = records, excludedRecords = 0)
        } else {
            admitRecords(
                coreBytes = core.sumOf { it.length() },
                recordsNewestFirst = records,
                budget = budget,
            )
        }

    return AutoBackupSet(files = core + admission.files, excludedRecords = admission.excludedRecords)
}

/**
 * Every record-grade image on disk, newest first: document pages **and tray photos** (ADR-0029).
 *
 * The two share one queue and one budget because they are the same kind of thing to an owner — a
 * scanned discharge sheet and a photograph of a symptomatic litter tray are both evidence for a vet,
 * and neither can be reproduced later. Ordering across the two by date rather than admitting one
 * kind first is the point: the newest evidence wins, whichever it is.
 *
 * By **file timestamp**, not by the `documents` or `observations` tables: this runs in a process with
 * no database open and ADR-0005 keeps it that way. The two agree in practice — `MediaFiles` writes
 * the file before the row (ADR-0020), so an image's mtime is within milliseconds of its `createdAt` —
 * and where they could drift, the file's own date is the honest answer for a decision about files.
 *
 * The name is the tie-break, so two images written in the same millisecond do not reorder between
 * runs and turn an unchanged phone into a changed backup set.
 */
private fun recordsNewestFirst(filesDir: File): List<File> =
    mediaFilesFor(listOf(MediaKind.Document, MediaKind.Observation), filesDir)
        .map { it.file }
        .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })

/**
 * When Auto Backup last ran on this phone, and how many records it could not carry.
 *
 * @param excludedRecords zero on a marker written by 1.0 or 1.1, which had no documents to
 *   exclude and no key for the count. Absent reads as zero rather than as unknown: the app never
 *   claims anything was dropped on the strength of a field that was not written.
 */
data class AutoBackupMarker(
    val lastBackupAt: Instant,
    val excludedRecords: Int = 0,
)

/**
 * Write the marker, **temp-then-rename**, so a process killed mid-write leaves the previous marker
 * standing rather than a half-written one that parses as garbage and renders as "never".
 */
fun writeAutoBackupMarker(
    filesDir: File,
    at: Instant,
    excludedRecords: Int = 0,
) {
    val marker = File(filesDir, AUTO_BACKUP_MARKER_FILE)
    val part = File(filesDir, "$AUTO_BACKUP_MARKER_FILE.part")
    try {
        filesDir.mkdirs()
        part.writeText(
            "$MARKER_LAST_BACKUP_KEY=${at.toEpochMilli()}\n" +
                "$MARKER_EXCLUDED_DOCUMENTS_KEY=$excludedRecords\n",
        )
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
 * Line-based `key=value` with unknown keys ignored, which is what let Phase 5 add the
 * excluded-document count without a marker written by 1.0 becoming unreadable. The tolerance runs
 * both ways: a *missing* count reads as zero, so a 1.0 marker on a phone that has just taken 1.2
 * describes a backup that happened rather than a file that does not parse. Anything that does not
 * parse at all is treated as absent — the state this app can always describe truthfully.
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
        values[MARKER_LAST_BACKUP_KEY]?.trim()?.toLongOrNull()?.let { millis ->
            AutoBackupMarker(
                lastBackupAt = Instant.ofEpochMilli(millis),
                // A count that is missing, negative or not a number is no count at all. Zero is the
                // reading that cannot invent an exclusion nobody recorded.
                excludedRecords = values[MARKER_EXCLUDED_DOCUMENTS_KEY]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
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
        /**
         * How many documents and tray photos the last run had to leave behind — **said in words on the screen, never
         * dropped silently** (ADR-0005). Read from the marker rather than recomputed, so the status
         * line and the one-time notification are two renderings of one number.
         */
        val excludedRecords: Int = 0,
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
    return AutoBackupStatus.Recorded(
        at = marker.lastBackupAt,
        stale = age > AUTO_BACKUP_STALE_AFTER,
        excludedRecords = marker.excludedRecords,
    )
}

/**
 * What the app should do about an exclusion count when it next starts (PLAN 5h).
 *
 * **The agent writes, the app posts**, and the split is forced rather than chosen: the agent cannot
 * reach the app's DataStore — its writes are `suspend` inside blocking backup callbacks — so it has
 * nowhere to record that a notice has already fired. Auto Backup runs roughly daily, which would
 * turn a "one-time" notice into a nightly one on the channel an owner is most likely to mute. So the
 * agent leaves the count in the marker and this decides, once, on the next launch.
 *
 * [Clear] is what makes it once-*per-episode* rather than once-ever. An exclusion that resolves —
 * documents deleted, the database shrunk, the owner exported and cleared some out — takes the flag
 * with it, so if the condition comes back years later it is allowed to say so again. The alternative
 * is a notice that fires for the first exclusion in the app's life and stays silent through every
 * one after it.
 */
enum class ExclusionNotice {
    /** Post it, then record that it was posted. */
    Post,

    /** Nothing is being excluded any more: forget that the notice fired. */
    Clear,

    /** Either nothing to say, or it has already been said. */
    Nothing,
}

fun exclusionNotice(
    excludedRecords: Int,
    alreadyNotified: Boolean,
): ExclusionNotice =
    when {
        excludedRecords <= 0 -> if (alreadyNotified) ExclusionNotice.Clear else ExclusionNotice.Nothing
        alreadyNotified -> ExclusionNotice.Nothing
        else -> ExclusionNotice.Post
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
