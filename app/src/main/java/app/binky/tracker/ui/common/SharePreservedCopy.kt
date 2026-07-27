package app.binky.tracker.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.binky.tracker.R
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.PreservedKind
import java.io.File

/**
 * Shares one occupant of `preserved/`: a wipe copy's `.db` **and its sidecars together**, or a
 * pre-restore snapshot's single zip.
 *
 * The sidecars are why this is one function rather than an intent built at each call site. In WAL
 * mode the most recent writes may live only in `-wal`, so handing over the `.db` alone can share a
 * file missing the very data worth keeping — and this is shared by the Backup screen and by the
 * schema-mismatch screen, which in a release build offers it and nothing else (ADR-0023). Two copies
 * of an intent this fiddly would be two chances to forget, in the two places where forgetting costs
 * the same irreplaceable file.
 */
fun Context.sharePreservedCopy(copy: PreservedCopy) {
    val uris = ArrayList(copy.files.map(::backupUri))
    val send =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeTypeFor(copy.kind)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, getString(R.string.preserved_share)))
}

/**
 * Hands a freshly built export to the share sheet (ADR-0005).
 *
 * The share sheet first, and a remembered folder destination at 1.1: a chooser cannot fail for
 * provider reasons, where writing into a cloud provider's document tree is the plan's biggest
 * unverified assumption.
 */
fun Context.shareBackupArchive(archive: File) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME
            putExtra(Intent.EXTRA_STREAM, backupUri(archive))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, getString(R.string.backup_export_share)))
}

private fun Context.backupUri(file: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

/**
 * A zip says what it is; a SQLite database has no MIME type of its own, and `octet-stream` is what
 * keeps mail and cloud-storage targets from helpfully re-encoding it.
 */
private fun mimeTypeFor(kind: PreservedKind): String =
    when (kind) {
        PreservedKind.WipeCopy -> "application/octet-stream"
        PreservedKind.RestoreSnapshot -> ZIP_MIME
    }

private const val ZIP_MIME = "application/zip"
