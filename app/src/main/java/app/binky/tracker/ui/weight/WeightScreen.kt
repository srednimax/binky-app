package app.binky.tracker.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.watch.StartWatchAction

/**
 * Weight — always bunny-scoped, and the one screen that **refuses** "All bunnies" (ADR-0015):
 * weight is individual, and overlaying unrelated animals of different sizes on one axis would say
 * nothing true.
 *
 * Top to bottom: the trend flag, then 2d's chart, then the history. The flag sits **above** the
 * chart because it reads the whole series while the chart reads only the selected window — so it
 * stays put and stays true as the range changes underneath it (ADR-0022).
 *
 * No Compose UI tests here (ADR-0012, as in checkpoint 1c); the logic beneath is covered by 2b's
 * trend tests and by `WeightChartContentTest`.
 */
@Composable
fun WeightScreen(
    onAddWeight: (String) -> Unit,
    onEditWeight: (String, String) -> Unit,
    onOpenVisit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeightViewModel = viewModel(factory = WeightViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.selection) {
        // Momentary, before the first database and preferences emissions arrive.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> Message(stringResource(R.string.add_a_bunny_first), modifier)
        BunnySelection.All -> Message(stringResource(R.string.weight_pick_a_bunny), modifier)
        else ->
            History(
                state = state,
                onAdd = { state.bunnyId?.let(onAddWeight) },
                onEdit = { row -> state.bunnyId?.let { onEditWeight(it, row.id) } },
                onOpenVisit = { row ->
                    val bunnyId = state.bunnyId
                    if (bunnyId != null && row.visitId != null) onOpenVisit(bunnyId, row.visitId)
                },
                onDelete = viewModel::requestDelete,
                onAcknowledge = viewModel::acknowledge,
                onStartWatch = viewModel::startWatch,
                onRangeChange = viewModel::setChartRange,
                modifier = modifier,
            )
    }

    state.pendingDelete?.let { row ->
        DeleteWeighingDialog(
            row = row,
            unit = state.unit,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    // The flag straight after a delete — correcting history can deepen a drop as readily as a new
    // weighing can (ADR-0001). Inserts and edits raise it from the entry screen, before it closes.
    state.writeFlag?.let { drop ->
        TrendFlagDialog(
            bunnyName = state.bunnyName,
            drop = drop,
            unit = state.unit,
            onAcknowledge = viewModel::acknowledge,
            onDismiss = viewModel::dismissWriteFlag,
            secondaryAction = watchAction(state, viewModel::startWatch),
        )
    }
}

/**
 * The flag's secondary slot: *Start a watch*, or nothing (ADR-0001).
 *
 * Nothing while a watch is already running — a button offering to start one on top of a running one
 * says nothing true — and nothing in the read-only scope, which writes nothing at all (ADR-0004).
 * Shared by the banner and the dialog, so the two cannot disagree about whether it is on offer.
 */
private fun watchAction(
    state: WeightUiState,
    onStartWatch: (WatchDuration) -> Unit,
): (@Composable () -> Unit)? =
    if (state.readOnly || state.watch is WatchState.Active) {
        null
    } else {
        { StartWatchAction(bunnyName = state.bunnyName, onStart = onStartWatch) }
    }

@Composable
private fun Message(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxSize().padding(16.dp),
    )
}

@Composable
private fun History(
    state: WeightUiState,
    onAdd: () -> Unit,
    onEdit: (WeightRow) -> Unit,
    onOpenVisit: (WeightRow) -> Unit,
    onDelete: (WeightRow) -> Unit,
    onAcknowledge: () -> Unit,
    onStartWatch: (WatchDuration) -> Unit,
    onRangeChange: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TrendFlagBanner(
                bunnyName = state.bunnyName,
                flag = state.flag,
                unit = state.unit,
                onAcknowledge = onAcknowledge,
                // Absent while a watch is already running. **Home is where a running watch is
                // shown and closed** (ADR-0001) — one place for that, so the owner learns where it
                // lives rather than finding it wherever they happen to be.
                secondaryAction = watchAction(state, onStartWatch),
            )
        }

        // The chart renders in the archived scope too — reading a history back is exactly what an
        // archived bunny's screen is for, and a chart has nothing to gate: it is already read-only.
        item {
            WeightChart(
                content = state.chart,
                range = state.chartRange,
                unit = state.unit,
                onRangeChange = onRangeChange,
            )
        }

        // No add / edit / delete affordances at all in the archived scope, rather than affordances
        // that refuse when tapped (ADR-0004).
        if (!state.readOnly) {
            item {
                Button(onClick = onAdd) { Text(stringResource(R.string.weight_add)) }
            }
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.weight_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.rows, key = { it.id }) { row ->
            WeighingRow(
                row = row,
                unit = state.unit,
                readOnly = state.readOnly,
                onEdit = { onEdit(row) },
                onOpenVisit = { onOpenVisit(row) },
                onDelete = { onDelete(row) },
            )
        }
    }
}

/**
 * One weighing, and **where it came from** (ADR-0017).
 *
 * A visit-recorded row says so and offers the visit; it offers neither *Edit* nor *Delete*, and
 * that is the same rule as the entry form's read-only state rather than a second one. The visit owns
 * that number — its date re-derives the timestamp and clearing its field deletes the row — so a
 * second way to change it would be a second path to the fact ADR-0017 keeps in one place. The chart
 * above plots it identically either way: a weight is a weight (ADR-0022).
 */
@Composable
private fun WeighingRow(
    row: WeightRow,
    unit: WeightUnit,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onOpenVisit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = weightLabel(row.grams, unit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // The change is in grams whatever the display unit says, because "−0.04 kg" hides
                // the signal that "−40 g" makes obvious (house rule).
                row.changeGrams?.let { change ->
                    Text(text = weightChangeLabel(change), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                text = dateTimeLabel(row.recordedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.visitId != null) {
                Text(
                    text = stringResource(R.string.weight_from_visit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.visitId == null) {
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                    } else {
                        TextButton(onClick = onOpenVisit) { Text(stringResource(R.string.weight_open_visit)) }
                    }
                }
            }
        }
    }
}

/**
 * **One** confirmation. ADR-0004's two-stage ceremony exists for destroying a bunny's whole history;
 * a single weighing is a correction, and the dialog names the reading so the owner can see which.
 */
@Composable
private fun DeleteWeighingDialog(
    row: WeightRow,
    unit: WeightUnit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.weight_delete_body,
                    weightLabel(row.grams, unit),
                    dateTimeLabel(row.recordedAt),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
