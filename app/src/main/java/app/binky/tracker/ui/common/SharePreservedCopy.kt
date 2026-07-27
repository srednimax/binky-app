package app.binky.tracker.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.binky.tracker.R
import app.binky.tracker.data.PreservedCopy

/**
 * Shares the `.db` **and its sidecars together**: in WAL mode the most recent writes may live only
 * in `-wal`, so handing over the `.db` alone can share a file missing the very data worth keeping.
 *
 * Shared by Settings and by the schema-mismatch screen, which in a release build offers this and
 * nothing else (ADR-0023). Two copies of an intent this fiddly would be two chances to forget the
 * sidecars, in the two places where forgetting costs the same irreplaceable file.
 */
fun Context.sharePreservedCopy(copy: PreservedCopy) {
    val uris =
        ArrayList(
            copy.files.map { file -> FileProvider.getUriForFile(this, "$packageName.fileprovider", file) },
        )
    val send =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            // No MIME type describes a SQLite database, and octet-stream is what keeps mail and
            // cloud-storage targets from re-encoding it.
            type = "application/octet-stream"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, getString(R.string.settings_preserved_share)))
}
