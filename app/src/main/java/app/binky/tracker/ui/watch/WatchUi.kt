package app.binky.tracker.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState

/**
 * *Start a watch* — the occupant of the slot `TrendFlagUi` has carried empty since 2c.
 *
 * **Offered, never automatic** (ADR-0001). "Worth a closer look" is already the flag's voice, and a
 * button acting on that sentence by itself would presume more than the sentence does. It is a
 * button, and the owner presses it.
 *
 * It holds its own dialog state rather than raising the question to a `ViewModel`, so the three
 * hosts the flag renders in — the write dialog, the Weight banner and Home's card — pass one lambda
 * instead of each growing a duplicate piece of state that can disagree with the others.
 */
@Composable
fun StartWatchAction(
    bunnyName: String,
    onStart: (WatchDuration) -> Unit,
) {
    // Kotlin note: `remember { mutableStateOf(...) }` is `useState` — a value that survives
    // recomposition and re-runs this composable when it changes. Not `rememberSaveable`: a
    // half-opened dialog is not worth restoring across process death, and the flag underneath it is.
    var choosing by remember { mutableStateOf(false) }

    TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.watch_start)) }

    if (choosing) {
        StartWatchDialog(
            bunnyName = bunnyName,
            onStart = { duration ->
                choosing = false
                onStart(duration)
            },
            onDismiss = { choosing = false },
        )
    }
}

/**
 * The duration question, as **preset chips** (ADR-0001) — see [WatchDuration] for why there is no
 * free-form field.
 */
@Composable
private fun StartWatchDialog(
    bunnyName: String,
    onStart: (WatchDuration) -> Unit,
    onDismiss: () -> Unit,
) {
    var duration by remember { mutableStateOf(WatchDuration.Default) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_start_title, bunnyName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.watch_start_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.watch_duration_question),
                    style = MaterialTheme.typography.labelLarge,
                )
                WatchDurationChoice(selected = duration, onSelect = { duration = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(duration) }) {
                Text(stringResource(R.string.watch_start_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Shared with the expiry prompt: starting and extending ask the same question the same way. */
@Composable
internal fun WatchDurationChoice(
    selected: WatchDuration,
    onSelect: (WatchDuration) -> Unit,
) {
    // FlowRow rather than Row: three chips fit on one line in English and can want two in Polish,
    // and a fixed Row would clip the third rather than wrap it.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchDuration.entries.forEach { duration ->
            FilterChip(
                selected = duration == selected,
                onClick = { onSelect(duration) },
                label = { Text(watchDurationLabel(duration)) },
            )
        }
    }
}

/** "7 days", through `<plurals>` — a day count spliced into a string is what plurals exist for. */
@Composable
fun watchDurationLabel(duration: WatchDuration): String =
    pluralStringResource(R.plurals.watch_duration_days, duration.days.toInt(), duration.days.toInt())

/**
 * *"Watch active · 4 days left"*, with close-early beside it.
 *
 * **A background state has to be visible where it is running** (ADR-0001). One that announced itself
 * only by nagging would be one the owner cannot turn off at the moment it annoys them — and that is
 * how a feature gets muted at the channel instead, taking its channel-mate's reliability with it.
 */
@Composable
fun WatchActiveCard(
    active: WatchState.Active,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.watch_active_line,
                        pluralStringResource(R.plurals.watch_days_left, active.daysLeft, active.daysLeft),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.watch_close)) }
        }
    }
}
