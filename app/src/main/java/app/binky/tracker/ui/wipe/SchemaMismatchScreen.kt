package app.binky.tracker.ui.wipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.sharePreservedCopy

/**
 * The path chip's corner — smaller than any card's, because it is a fragment of text with a
 * background rather than a surface of its own.
 */
private val PathRadius = 10.dp

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
                    .padding(horizontal = Spacing.section, vertical = Spacing.block),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            // No app bar, no nav and one way out, so the heading carries the whole orientation:
            // `headlineSmall`, the app's largest non-display size, standing on the background rather
            // than in a card.
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
                // The two format numbers are corroboration for the sentence above, not news of their
                // own — so they drop to `onSurfaceVariant` rather than sitting at the same weight.
                text = stringResource(R.string.wipe_versions, mismatch.fromVersion, mismatch.toVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // **One card, because the copy is one subject**: where it is, and what can be done with
            // it. What separates it from the news above is the card itself (`10i`).
            GroupedCard(
                contentPadding = PaddingValues(Spacing.base),
                modifier = Modifier.padding(top = Spacing.tight),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
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
                    Surface(
                        shape = RoundedCornerShape(PathRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            // The path as the owner would find it, not the absolute one, which
                            // changes across installs and means nothing on screen. It gets a
                            // container of its own because a file path wrapping mid-name across a
                            // plain background is unreadable, and this one has to be typed out
                            // somewhere else.
                            text = "files/$PRESERVED_DIRECTORY/${mismatch.preservedCopy.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = Spacing.snug, vertical = Spacing.tight),
                        )
                    }
                    Text(
                        text =
                            stringResource(
                                if (mismatch.wipeOnConsent) {
                                    R.string.wipe_copy_reach
                                } else {
                                    R.string.schema_refused_reach
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Sits with the copy it acts on, inside the card, in both variants — and its
                    // *emphasis* is the one deliberate difference between them. In release it is the
                    // only action on the screen, so it takes the filled shape the app reserves for
                    // the thing that matters most; in debug the destructive continue outranks it and
                    // it stays outlined.
                    val share = { context.sharePreservedCopy(preservedCopyOf(mismatch.preservedCopy)) }
                    if (mismatch.wipeOnConsent) {
                        OutlinedButton(
                            onClick = share,
                            modifier = Modifier.padding(top = Spacing.hair),
                        ) {
                            Text(stringResource(R.string.preserved_share))
                        }
                    } else {
                        Button(
                            onClick = share,
                            modifier =
                                Modifier
                                    .padding(top = Spacing.hair)
                                    .fillMaxWidth()
                                    .height(RecordButtonHeight),
                            shape = RoundedCornerShape(RecordButtonRadius),
                        ) {
                            Text(stringResource(R.string.preserved_share))
                        }
                    }
                }
            }

            if (mismatch.wipeOnConsent) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    // The screen's filled button, and it says what it does rather than "OK".
                    Button(
                        onClick = {
                            working = true
                            onContinue()
                        },
                        modifier =
                            Modifier
                                .padding(top = Spacing.tight)
                                .fillMaxWidth()
                                .height(RecordButtonHeight),
                        shape = RoundedCornerShape(RecordButtonRadius),
                    ) {
                        Text(stringResource(R.string.wipe_continue))
                    }
                }
            }
        }
    }
}
