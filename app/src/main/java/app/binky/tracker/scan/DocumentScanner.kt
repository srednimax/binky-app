package app.binky.tracker.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/**
 * How one scan is started, and — by which variant it is — **which of ADR-0009's two paths it took**.
 *
 * The screen needs to know, because the two do not produce the same thing: the guided scanner finds
 * page edges, deskews and lets the owner add a second sheet, and the camera does none of that. The
 * difference is *stated* rather than explained as an absence, so there is no branch anywhere asking
 * "is ML Kit installed" outside this package.
 *
 * Kotlin note: a `sealed interface` is a discriminated union — the compiler knows both variants, so
 * a `when` over one needs no `else` and a third path would turn every incomplete `when` into a
 * compile error.
 */
sealed interface ScanStart {
    /**
     * **The fallback** (ADR-0009): fire `ActivityResultContracts.TakePicture` at [target], and a
     * `true` result means [target] holds the single page. Already written for photos, so the
     * fallback costs wiring rather than a feature.
     */
    data class Camera(
        val target: Uri,
    ) : ScanStart

    /**
     * **The guided scanner**: fire `StartIntentSenderForResult` with [intentSender] and read the
     * pages back out of the result with [DocumentScanner.pagesOf].
     */
    data class Guided(
        val intentSender: IntentSender,
    ) : ScanStart
}

/**
 * The document scanner, behind the small interface ADR-0009 asked for.
 *
 * Two implementations exist: [CameraDocumentScanner], which is the plain-camera fallback, and the
 * ML Kit one, which is delivered by Play services and therefore **absent on some devices** —
 * including every emulator built without them, which is what makes the API-26 leg of the CI matrix
 * exercise this fallback for real.
 *
 * **Availability is a runtime question, resolved at use and never cached across installs.** There is
 * deliberately no `isAvailable()` for a caller to hold on to: [start] is the single place the
 * question is asked, so a cached answer cannot exist to go stale when Play services is updated,
 * disabled, or restored onto a different phone with the owner's backup.
 *
 * **The contingency this interface exists for** is dropping ML Kit entirely — it brings a merged
 * manifest, AAB size and a Play-services-absent path, and documents as *data* bring none of them.
 * That contingency is one line in `AppContainer`, and it stays one line only because the fallback
 * was built and proven before the ML Kit implementation landed.
 */
interface DocumentScanner {
    /**
     * Prepares one scan, resolving which path this device can take.
     *
     * [activity] because a guided scan is started from one; the fallback ignores it. `suspend`
     * because resolving availability can mean waiting on Play services, and doing that on the main
     * thread is how a scan button becomes a frozen screen.
     */
    suspend fun start(activity: Activity): ScanStart

    /**
     * The pages a [ScanStart.Guided] result carries, in the order the scanner produced them.
     *
     * A [ScanStart.Camera] result never reaches this: its one page is the `Uri` the caller already
     * handed over, which is why that variant carries it.
     */
    fun pagesOf(data: Intent?): List<Uri>
}
