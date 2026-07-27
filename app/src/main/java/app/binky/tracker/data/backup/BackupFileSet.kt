package app.binky.tracker.data.backup

import java.io.File

/** One file as it will appear inside an archive: where it is now, and what it is called in there. */
data class ArchiveFile(
    val entryName: String,
    val file: File,
)

/**
 * The media a [scope] carries, resolved against [mediaRoot] (normally `filesDir`).
 *
 * A **function over `File` with no `Context` and no Android dependency**, because 3e's `BackupAgent`
 * will call this too and cannot assume the app exists around it (ADR-0005): when the system starts
 * the process *for* backup it binds the base `Application`, so `AppContainer` is absent, and
 * reaching for it would force the `lazy` that ADR-0007 makes the guard in front of a wipe. That
 * failure only ever shows up in production — Auto Backup runs idle and charging, while `bmgr
 * backupnow` runs with the app on screen — so the way to not have it is for the type to make it
 * impossible.
 *
 * The **same allowlist decides what leaves and what comes back**: a file the export would not name
 * is one the restore would refuse anyway, so stray content in a media directory is skipped here
 * rather than shipped and then silently dropped at the far end.
 *
 * Sorted, so two exports of an unchanged phone produce the same entry order.
 */
fun mediaFilesFor(
    scope: BackupScope,
    mediaRoot: File,
): List<ArchiveFile> =
    scope.mediaKinds
        .flatMap { kind ->
            val directory = File(mediaRoot, kind.directory)
            (directory.listFiles() ?: emptyArray())
                .filter { it.isFile }
                .mapNotNull { file ->
                    val entryName = "${kind.directory}/${file.name}"
                    if (archiveEntryFor(entryName) is ArchiveEntry.Media) ArchiveFile(entryName, file) else null
                }
        }.sortedBy { it.entryName }

/**
 * The relative paths of every media file on disk, across **every** kind — not just the ones a scope
 * carries, because deciding what a restore keeps means knowing about the photos it is not being
 * asked to touch.
 */
fun mediaPathsOnDisk(mediaRoot: File): List<String> =
    mediaFilesFor(BackupScope.Everything, mediaRoot).map { it.entryName }
