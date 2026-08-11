package app.binky.tracker.ui.observations

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.shell.BunnySummary

/**
 * **Which bunny?** — the step both write paths take under "All bunnies" (ADR-0008).
 *
 * ADR-0015's pre-selection rule takes the selected bunny's fluffle as its input, and in that scope
 * there is no selected bunny. Pre-selecting *every* active bunny instead would write one identical
 * tray-level fact across bunnies that share no tray — this model's central prohibition, arriving from
 * the direction it is not usually stated in. So the scope asks first, and then the ordinary rule runs
 * unchanged.
 *
 * The single-bunny scope never sees this dialog, and the healthy day stays one tap there.
 *
 * The names are [ListRow]s in a [GroupedCard], which is what every other list in the app is now made
 * of — [BinkyDialog] provides the nested level, so the card reads as raised off the dialog the way it
 * reads as raised off the background outside one. **No [app.binky.tracker.ui.common.Chevron] on them**:
 * a chevron marks a row that is only telling you something and opens its own screen, and these rows
 * are the *answer* to the question in the title.
 */
@Composable
fun ChooseBunnyDialog(
    title: String,
    bunnies: List<BunnySummary>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BinkyDialog(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        // The only way out that is not a choice, so it takes the trailing slot on its own.
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        GroupedCard {
            bunnies.forEachIndexed { index, bunny ->
                // Two bunnies are independent of each other, which is the rule a divider states.
                if (index > 0) RowDivider()
                ListRow(title = bunny.name, onClick = { onPick(bunny.id) })
            }
        }
    }
}
