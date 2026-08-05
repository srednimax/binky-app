package app.binky.tracker.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Short enough for logcat's tag column, specific enough to filter on. */
private const val LOG_TAG = "BinkyScanner"

/**
 * How many sheets one scan may take in. Generous, because the thing this exists for is a multi-page
 * discharge note; bounded, because every page is a full-resolution bitmap on its way through the
 * media pipeline.
 */
private const val MAX_PAGES = 20

/**
 * ML Kit's guided scanner: page detection, auto-crop, deskew, and a second sheet without leaving
 * the flow.
 *
 * **It is delivered by Google Play services, so it can simply not be there** — no Play services, an
 * old Play services, a device where the module has not downloaded yet. That is not an error state:
 * [start] falls through to [fallback] and the scan happens anyway, with the difference stated
 * afterwards rather than a dialog about a component the owner cannot install (ADR-0009).
 *
 * Nothing outside this file names ML Kit. That is what keeps "drop the dependency" a one-line
 * change in `AppContainer`.
 */
class MlKitDocumentScanner(
    private val fallback: DocumentScanner,
) : DocumentScanner {
    private val options =
        GmsDocumentScannerOptions
            .Builder()
            // The whole point of the dependency: edge detection and the crop-and-rotate editor.
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setPageLimit(MAX_PAGES)
            // JPEG only. A PDF result would be a second media kind with its own storage, viewer and
            // export rules for no gain — `document_pages` already models a document as its images.
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            // Off deliberately. Importing from the gallery is the Photo Picker's job elsewhere in
            // this app, and letting the scanner do it here would be a second, differently-behaved
            // way in — while `setGalleryImportAllowed(true)` is also the option most likely to pull
            // a media permission into the merged manifest, which is the thing this checkpoint is
            // checking rather than assuming.
            .setGalleryImportAllowed(false)
            .build()

    /**
     * Asks Play services for a scan, and hands the question to [fallback] if it cannot answer.
     *
     * **Availability is resolved here, at use, and never cached** (ADR-0009): a stored "this phone
     * has no scanner" would survive a Play services update, and a stored yes would survive a
     * restore onto a phone that has none.
     */
    override suspend fun start(activity: Activity): ScanStart =
        try {
            ScanStart.Guided(GmsDocumentScanning.getClient(options).getStartScanIntent(activity).awaitSender())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unavailable: Exception) {
            // Every failure here means the same thing to the owner — no guided scan on this phone —
            // so they are treated alike and the reason goes to logcat rather than to a dialog.
            Log.i(LOG_TAG, "Guided scanner unavailable; falling back to the camera", unavailable)
            fallback.start(activity)
        }

    override fun pagesOf(data: Intent?): List<Uri> =
        GmsDocumentScanningResult
            .fromActivityResultIntent(data)
            ?.pages
            .orEmpty()
            .map { it.imageUri }
}

/**
 * A Play-services `Task` as a `suspend` call.
 *
 * Hand-rolled rather than pulling in `kotlinx-coroutines-play-services` for one `await()`: that is a
 * whole extra dependency for a single bridge, in a file whose entire point is that its dependency
 * can be removed.
 *
 * Kotlin note: `suspendCancellableCoroutine` is the bridge from a callback API to `suspend` — the
 * continuation is resumed by whichever listener fires, roughly `new Promise((resolve, reject) => …)`
 * except that cancelling the caller's coroutine also unblocks it.
 *
 * A failed task **resumes with an exception rather than cancelling**: cancellation would propagate
 * as a `CancellationException`, which [MlKitDocumentScanner.start] deliberately rethrows, and the
 * fallback would then never engage on the one path it exists for.
 */
private suspend fun Task<IntentSender>.awaitSender(): IntentSender =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { sender -> continuation.resume(sender) }
        addOnFailureListener { failure -> continuation.resumeWithException(failure) }
        addOnCanceledListener { continuation.resumeWithException(IllegalStateException("scan intent cancelled")) }
    }
