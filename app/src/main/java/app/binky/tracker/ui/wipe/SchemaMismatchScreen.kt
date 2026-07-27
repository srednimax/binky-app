package app.binky.tracker.ui.wipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.SchemaMismatch
import app.binky.tracker.data.PRESERVED_DIRECTORY
import app.binky.tracker.data.preservedCopyOf
import app.binky.tracker.ui.common.sharePreservedCopy

/**
 * The blocking screen that stands between a schema this build does not know and the database
 * written under it. Two variants, and which one renders is not a style choice — it is what this
 * build is actually about to do (ADR-0007's consent half, ADR-0023's release half).
 *
 * **Debug — consent.** The wipe is going to happen anyway and the owner's only choice is whether to
 * look at it first, so there is one forward button and no cancel: what ADR-0007 forbids is the
 * *silent* wipe, not the unavoidable one. Offering a "Keep my data" button that cannot keep the
 * data would be the worse lie.
 *
 * **Release — refusal.** The destruction is no longer going to happen; opening the file throws
 * instead. A forward button here would destroy a bunny's history on a path where nothing was going
 * to destroy it, offered to an owner who is already confused. So this variant states that the build
 * cannot open the records, names the copy, offers **share**, and dead-ends. The way out is a fixed
 * build, not a tap.
 *
 * Both variants say **where the copy is**, because until 3d's restore lands that file is the only
 * copy of a weight series.
 */
@Composable
fun SchemaMismatchScreen(
    mismatch: SchemaMismatch,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local rather than hoisted: the only thing it guards is this screen's own button, and the
    // screen is removed by `schemaMismatch` going null the moment the work finishes.
    var working by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // This screen renders *instead of* `MainNavigation`, so it is outside the
                    // `Scaffold` that insets every other screen — under `enableEdgeToEdge()` it
                    // would otherwise draw its title beneath the status bar.
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (mismatch.wipeOnConsent) R.string.wipe_title else R.string.schema_refused_title,
                    ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text =
                    stringResource(
                        if (mismatch.wipeOnConsent) R.string.wipe_body else R.string.schema_refused_body,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.wipe_versions, mismatch.fromVersion, mismatch.toVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                    stringResource(
                        if (mismatch.wipeOnConsent) {
                            R.string.wipe_copy_taken
                        } else {
                            R.string.schema_refused_copy_taken
                        },
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                // The path as the owner would find it, not the absolute one, which changes across
                // installs and means nothing on screen.
                text = "files/$PRESERVED_DIRECTORY/${mismatch.preservedCopy.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text =
                    stringResource(
                        if (mismatch.wipeOnConsent) R.string.wipe_copy_reach else R.string.schema_refused_reach,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Offered in both variants, and the *only* action in the release one. Settings is
            // unreachable from here — the app behind this screen has not opened.
            OutlinedButton(
                onClick = { context.sharePreservedCopy(preservedCopyOf(mismatch.preservedCopy)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.preserved_share))
            }

            if (mismatch.wipeOnConsent) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Button(
                        onClick = {
                            working = true
                            onContinue()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.wipe_continue))
                    }
                }
            }
        }
    }
}
