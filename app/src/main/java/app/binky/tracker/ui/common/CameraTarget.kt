package app.binky.tracker.ui.common

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * A file for the camera to write into, behind a `content://` Uri — a `file://` one has been illegal
 * to hand another app since Android 7. It lands in the cache: MediaFiles re-encodes the shot into
 * its own directory and this copy is disposable.
 *
 * Shared by every screen that fires `TakePicture`: the bunny editor's avatar and the photo gallery.
 */
fun newCameraTarget(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
