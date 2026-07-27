package app.binky.tracker.ui.observations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
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
 */
@Composable
fun ChooseBunnyDialog(
    title: String,
    bunnies: List<BunnySummary>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column {
                bunnies.forEach { bunny ->
                    Text(
                        text = bunny.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(bunny.id) }
                                .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
