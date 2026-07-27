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
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras

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
                onDelete = viewModel::requestDelete,
                onAcknowledge = viewModel::acknowledge,
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
        )
    }
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
    onDelete: (WeightRow) -> Unit,
    onAcknowledge: () -> Unit,
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
                onDelete = { onDelete(row) },
            )
        }
    }
}

@Composable
private fun WeighingRow(
    row: WeightRow,
    unit: WeightUnit,
    readOnly: Boolean,
    onEdit: () -> Unit,
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
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
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
