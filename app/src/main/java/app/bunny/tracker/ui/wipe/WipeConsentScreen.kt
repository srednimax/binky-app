package app.bunny.tracker.ui.wipe

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.bunny.tracker.PendingWipe
import app.bunny.tracker.R
import app.bunny.tracker.data.PRESERVED_DIRECTORY

/**
 * ADR-0007's consent half: the blocking screen that stands between a schema bump and the database
 * it is about to destroy.
 *
 * It is **honest about having no alternative**. The copy has already been taken by the time this
 * renders, and there is nothing this build can do with the old file — reading old records into a new
 * schema *is* a migration, and one has not been written. So there is one forward button and no
 * cancel: what ADR-0007 forbids is the *silent* wipe, not the unavoidable one. Offering a "Keep my
 * data" button that cannot keep the data would be the worse lie.
 *
 * What it must therefore do properly is say what is being destroyed and **where the copy is**, since
 * through Phase 2 that file is the only copy of a weight series. Settings gains a share action for
 * it in checkpoint 2c; until then `adb` is the way to it, which is a developer's recovery path and
 * exactly why the share action is not optional.
 */
@Composable
fun WipeConsentScreen(
    pendingWipe: PendingWipe,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local rather than hoisted: the only thing it guards is this screen's own button, and the
    // screen is removed by `pendingWipe` going null the moment the work finishes.
    var working by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.wipe_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.wipe_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    stringResource(
                        R.string.wipe_versions,
                        pendingWipe.fromVersion,
                        pendingWipe.toVersion,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.wipe_copy_taken),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                // The path as the owner would find it, not the absolute one, which changes across
                // installs and means nothing on screen.
                text = "files/$PRESERVED_DIRECTORY/${pendingWipe.preservedCopy.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.wipe_copy_reach),
                style = MaterialTheme.typography.bodyMedium,
            )

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
