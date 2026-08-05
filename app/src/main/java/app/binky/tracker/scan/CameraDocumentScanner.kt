package app.binky.tracker.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.binky.tracker.ui.common.newCameraTarget

/**
 * **The fallback** (ADR-0009): one photograph of the page through the system camera.
 *
 * It reuses `newCameraTarget` — the same `FileProvider` `Uri` the avatar and the photo gallery hand
 * to `TakePicture` — so this implementation is wiring rather than a feature. What it loses against
 * the guided scanner is auto-crop and page detection, and nothing else: the image still goes through
 * `MediaFiles.persist(Document)` and lands in the same table.
 *
 * It is also **the reason the app declares no `CAMERA` permission**. Firing the system camera intent
 * needs none; declaring it would make a camera *required at install* and change the store listing.
 *
 * Always available, and deliberately so — it is the answer of last resort, and a scanner that could
 * itself be unavailable would need a third path behind it.
 */
class CameraDocumentScanner(
    context: Context,
) : DocumentScanner {
    private val appContext = context.applicationContext

    override suspend fun start(activity: Activity): ScanStart = ScanStart.Camera(newCameraTarget(appContext))

    /** Never called: a camera result's single page is the target the caller already holds. */
    override fun pagesOf(data: Intent?): List<Uri> = emptyList()
}
