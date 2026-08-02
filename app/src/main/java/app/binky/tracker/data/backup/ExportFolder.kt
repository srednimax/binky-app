package app.binky.tracker.data.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/*
 * **A remembered destination for exports, not a second export mechanism** (ADR-0005, PLAN 4e).
 *
 * The share sheet is still the primary path and is never replaced: a chooser cannot fail for
 * provider reasons, and every failure in here falls back to it. What this buys is the two taps
 * between "I made a backup" and "it is in my cloud folder" — and, with the recurring reminder beside
 * it, the difference between a saving and a habit.
 *
 * `ContentResolver` extensions rather than `Context` ones, because a `ContentResolver` is the whole
 * of what this needs and it is what a `ViewModel` can hold without holding an Activity.
 */

/** The zip an export is. Repeated from `SharePreservedCopy.kt`'s private one — different layer. */
private const val ZIP_MIME = "application/zip"

/**
 * What the app can say about the remembered folder right now — **three states, not two**, for the
 * same reason the automatic-backup line has three.
 *
 * A stored URI is not a working destination: the grant behind it can be revoked in Android's
 * settings, the provider can be uninstalled, and a restored backup carries the preference onto a
 * phone that never granted anything. Rendering any of those as a folder name would put a button in
 * front of the owner that quietly fails at the moment they are counting on it.
 */
sealed interface ExportFolderState {
    /** No folder chosen. Export goes to the share sheet, which is the path that always works. */
    data object None : ExportFolderState

    /** A folder, still granted, with the name the provider gives it. */
    data class Remembered(
        val uri: Uri,
        val label: String,
    ) : ExportFolderState

    /**
     * A folder was chosen, and this phone can no longer write to it. Named rather than silently
     * cleared: the owner chose a destination and deserves to hear that it has gone, not to discover
     * a settings row that emptied itself.
     */
    data object Unavailable : ExportFolderState
}

/** What happened when an export was written to the remembered folder. */
sealed interface FolderWrite {
    /** Written, under the name the provider actually gave it — which may not be the one asked for. */
    data class Written(
        val name: String,
    ) : FolderWrite

    /**
     * The provider would not take it. **Never an error the owner has to resolve**: the caller falls
     * back to the share sheet, which is what the export did before this feature existed.
     */
    data object Refused : FolderWrite
}

/**
 * Takes the long-lived grant that makes a picked folder usable after this Activity is gone.
 *
 * Without this the URI works until the process dies and then quietly stops, which is the worst
 * possible shape for a backup destination. Returns whether the grant was actually taken — a provider
 * that offered a tree it will not persist is a folder this app must not claim to remember.
 */
fun ContentResolver.rememberExportFolder(uri: Uri): Boolean =
    try {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    } catch (e: SecurityException) {
        // The picker returned a tree whose grant is not persistable, or is already gone. Nothing to
        // recover: the caller keeps no folder and exports keep going to the share sheet.
        false
    }

/** Hands the grant back when the owner forgets the folder, so the app holds nothing it is not using. */
fun ContentResolver.forgetExportFolder(uri: Uri) {
    try {
        releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        // Already released, or never held. Forgetting a folder cannot fail from the owner's side.
    }
}

/**
 * Resolves the stored URI against what this phone will actually allow, and reads the folder's name.
 *
 * The **grant is checked first, from `persistedUriPermissions`**, and only then is the provider
 * asked for a display name. Both are needed: a grant can outlive the folder it points at (a deleted
 * directory answers no query), and a folder can outlive its grant (revoked in Android's settings).
 * Either way the answer is [ExportFolderState.Unavailable] rather than a name the owner would trust.
 */
fun ContentResolver.exportFolderState(stored: String?): ExportFolderState {
    if (stored.isNullOrBlank()) return ExportFolderState.None
    val uri = stored.toUriOrNull() ?: return ExportFolderState.Unavailable

    val granted = persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    if (!granted) return ExportFolderState.Unavailable

    val label = folderLabel(uri) ?: return ExportFolderState.Unavailable
    return ExportFolderState.Remembered(uri = uri, label = label)
}

/**
 * Copies a finished export into the remembered folder.
 *
 * Nothing here throws at the caller. A document provider can refuse for reasons that have nothing to
 * do with this app — no space in the owner's cloud, a folder deleted since it was picked, a provider
 * that has simply stopped — and the export those failures happen to is already built and already
 * shareable. So every failure becomes [FolderWrite.Refused] and the screen falls back.
 *
 * Kotlin note: `withContext(Dispatchers.IO)` is the "run this on the IO pool and suspend until it is
 * done" of a `suspend` function — the copy is file IO over a `ContentProvider`, which can be a
 * network round-trip when the provider is a cloud one.
 */
suspend fun ContentResolver.writeExportToFolder(
    tree: Uri,
    archive: File,
): FolderWrite =
    withContext(Dispatchers.IO) {
        try {
            // A tree URI is not a document URI. Writing needs the *document* the tree stands for,
            // which is what this rebuild produces; passing the tree URI straight to createDocument
            // throws, and it is the one mistake this API practically invites.
            val parent =
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val target =
                DocumentsContract.createDocument(this@writeExportToFolder, parent, ZIP_MIME, archive.name)
                    ?: return@withContext FolderWrite.Refused

            openOutputStream(target).use { output ->
                if (output == null) return@withContext FolderWrite.Refused
                archive.inputStream().use { input -> input.copyTo(output) }
            }
            // The provider decides the final name — a second export in the same minute becomes
            // "bunny-records-….zip (1)" on some, and telling the owner the name Binky asked for
            // would be telling them about a file that is not there.
            FolderWrite.Written(folderLabel(target) ?: archive.name)
        } catch (e: FileNotFoundException) {
            FolderWrite.Refused
        } catch (e: IOException) {
            FolderWrite.Refused
        } catch (e: SecurityException) {
            FolderWrite.Refused
        } catch (e: IllegalArgumentException) {
            // A stored URI that is not a tree URI at all — a hand-edited preferences file, or one
            // restored from a phone whose provider names differ.
            FolderWrite.Refused
        }
    }

/**
 * The provider's own display name for a document or tree, or `null` if it will not say.
 *
 * `null` is a real answer and is treated as one by both callers: a folder the provider will not
 * describe is a folder this app should not promise to write to.
 */
private fun ContentResolver.folderLabel(uri: Uri): String? =
    try {
        val document =
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            } else {
                uri
            }
        query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

/** A stored string that is not a URI is corruption, not a crash. */
private fun String.toUriOrNull(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
