package app.binky.tracker.ui.bunny

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.BinkyDialog

/**
 * Renders whichever confirmation [BunnyActions] currently holds, and nothing when it holds none.
 *
 * One host for both ceremonies, used by Home and the archived list, so the wording an owner sees
 * cannot depend on which screen they started from.
 */
@Composable
fun BunnyDialogHost(
    dialog: BunnyDialog?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        is BunnyDialog.Archive ->
            BinkyDialog(
                title = stringResource(R.string.archive_dialog_title, dialog.name),
                onDismiss = onDismiss,
                confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_archive)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            ) {
                Text(stringResource(R.string.archive_dialog_body, dialog.name))
            }

        is BunnyDialog.Delete ->
            BinkyDialog(
                title =
                    if (dialog.confirmedOnce) {
                        stringResource(R.string.delete_dialog_second_title, dialog.name)
                    } else {
                        stringResource(R.string.delete_dialog_title, dialog.name)
                    },
                onDismiss = onDismiss,
                confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            ) {
                // One child, not three: the dialog spaces its own content at [Spacing.base], which is
                // right between two subjects and too much between a sentence and the counts that
                // qualify it — the same call the medication delete makes.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    if (dialog.confirmedOnce) {
                        // The second stage states what is destroyed, in ADR-0004's two buckets:
                        // records solely this bunny's, and shared entries that survive for the
                        // others. Lumping them together would overstate the loss and hide a
                        // side effect on a different bunny.
                        Text(stringResource(R.string.delete_dialog_second_body))
                        Text(
                            pluralStringResource(
                                R.plurals.delete_records_sole_owned,
                                dialog.counts.soleOwnedRecords,
                                dialog.counts.soleOwnedRecords,
                            ),
                        )
                        if (dialog.counts.sharedRecords > 0) {
                            Text(
                                pluralStringResource(
                                    R.plurals.delete_records_shared,
                                    dialog.counts.sharedRecords,
                                    dialog.counts.sharedRecords,
                                ),
                            )
                        }
                    } else {
                        Text(stringResource(R.string.delete_dialog_body))
                        // Named, not counted: the avatar does not trip the second dialog, but
                        // the owner should not discover afterwards that the photo went too.
                        if (dialog.hasAvatar) Text(stringResource(R.string.delete_dialog_avatar_note))
                    }
                }
            }
    }
}
