package app.binky.tracker.ui.documents

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.binky.tracker.BinkyApplication
import app.binky.tracker.scan.DocumentScanner
import app.binky.tracker.scan.ScanStart
import kotlinx.coroutines.launch

/**
 * The app's one scanner, out of `AppContainer` (ADR-0009).
 *
 * A composable rather than a ViewModel field because a scan is started from the UI — it needs the
 * `Activity` and two `ActivityResultLauncher`s, neither of which a `ViewModel` has any business
 * holding. What the ViewModel gets back is a list of `Uri`s, which is data.
 */
@Composable
fun rememberDocumentScanner(): DocumentScanner {
    val application = LocalContext.current.applicationContext as BinkyApplication
    return remember(application) { application.container.documentScanner }
}

/**
 * What one finished scan produced.
 *
 * [guided] is false when the fallback engaged (ADR-0009). The fallback engages **silently** — no
 * dialog, no "Play services is missing" — and the screen states the difference afterwards, because
 * an absence the owner cannot act on is not worth a sentence and a missing auto-crop is.
 */
data class ScanResult(
    val pages: List<Uri>,
    val guided: Boolean,
)

/**
 * Registers both scan paths and hands back the one function a screen calls to start one.
 *
 * Both launchers have to be registered unconditionally: `rememberLauncherForActivityResult`
 * registers during composition, so choosing one *after* asking the scanner which path this device
 * takes would mean registering conditionally — which Compose does not allow and which would break
 * the moment the answer changed between compositions. So both exist, and [DocumentScanner.start]
 * decides which one fires.
 *
 * Kotlin note: this is a Compose "hook" — `remember*` calls that must run on every composition in
 * the same order, exactly like React's rules of hooks. The returned lambda is what the button
 * calls.
 */
@Composable
fun rememberDocumentScan(
    scanner: DocumentScanner,
    onScanned: (ScanResult) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    // Kotlin note: `rememberUpdatedState` keeps the *latest* callback visible to a lambda that was
    // captured earlier — without it a launcher registered on the first composition would keep
    // calling the first version of `onScanned` forever.
    val onResult by rememberUpdatedState(onScanned)

    // Saveable: the camera or the scanner UI is in front of us, and a low-memory kill while it is
    // there must not lose the file the photograph is being written into.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val target = cameraTarget
            cameraTarget = null
            if (taken && target != null) {
                onResult(ScanResult(pages = listOf(target), guided = false))
            }
        }

    val guidedScan =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pages = scanner.pagesOf(result.data)
            // A cancelled scan comes back with no pages, which is not a failure worth a message:
            // the owner backed out, and the screen they backed out to is the answer.
            if (pages.isNotEmpty()) onResult(ScanResult(pages = pages, guided = true))
        }

    return remember(scanner, activity) {
        {
            val host = activity
            if (host != null) {
                scope.launch {
                    // Kotlin note: `when` over a sealed interface is exhaustive with no `else`, so a
                    // third scan path could not be added without this failing to compile.
                    when (val start = scanner.start(host)) {
                        is ScanStart.Camera -> {
                            cameraTarget = start.target
                            takePicture.launch(start.target)
                        }
                        is ScanStart.Guided ->
                            guidedScan.launch(IntentSenderRequest.Builder(start.intentSender).build())
                    }
                }
            }
        }
    }
}
