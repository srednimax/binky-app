package app.binky.tracker.ui.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.FabClearance
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.SectionHeader
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
    onEditBunny: (String) -> Unit,
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
                onAcknowledge = viewModel::acknowledge,
                onAskAge = { state.bunnyId?.let(onEditBunny) },
                onStartWatch = viewModel::startWatch,
                onRangeChange = viewModel::setChartRange,
                modifier = modifier,
            )
    }
}

/**
 * The flag's secondary slot: *Start a watch*, or nothing (ADR-0001).
 *
 * Nothing while a watch is already running — a button offering to start one on top of a running one
 * says nothing true — and nothing in the read-only scope, which writes nothing at all (ADR-0004).
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
    onAcknowledge: () -> Unit,
    onAskAge: () -> Unit,
    onStartWatch: (WatchDuration) -> Unit,
    onRangeChange: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = state.rows
    // The spacing *below* the flag has to be decided out here: the banner draws nothing at all for
    // most bunnies, and a `Spacer` emitted next to it would be a hole that only appears when there
    // is no flag. Same rule as Home's.
    val hasFlag = state.flag.showsBanner()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // No `verticalArrangement`: the history rows draw one grouped card between them, and an
        // even gap between every item would slice it into strips.
        contentPadding =
            PaddingValues(
                start = Spacing.base,
                end = Spacing.base,
                top = Spacing.tight,
                bottom = FabClearance,
            ),
    ) {
        if (hasFlag) {
            item {
                TrendFlagBanner(
                    bunnyName = state.bunnyName,
                    flag = state.flag,
                    unit = state.unit,
                    onAcknowledge = onAcknowledge,
                    // ADR-0028's age question, on a gain raised with no birthday on file. It leads
                    // to the bunny editor, which is not on this tab — the one place in the app
                    // where the flag's own action leaves the screen it is drawn on.
                    onAskAge = onAskAge,
                    // Absent while a watch is already running. **Home is where a running watch is
                    // shown and closed** (ADR-0001) — one place for that, so the owner learns where
                    // it lives rather than finding it wherever they happen to be.
                    secondaryAction = watchAction(state, onStartWatch),
                )
                Spacer(Modifier.height(Spacing.section))
            }
        }

        // The chart renders in the archived scope too — reading a history back is exactly what an
        // archived bunny's screen is for, and a chart has nothing to gate: it is already read-only.
        item {
            SectionHeader(stringResource(R.string.weight_chart_section))
            Spacer(Modifier.height(Spacing.tight))
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
                Spacer(Modifier.height(Spacing.section))
                // Full width here, unlike Home's hug-width one: this is the action the tab exists
                // for, and it sits between two sections rather than under a sentence.
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                    shape = RoundedCornerShape(RecordButtonRadius),
                ) {
                    Text(stringResource(R.string.weight_add))
                }
            }
        }

        item {
            Spacer(Modifier.height(Spacing.section))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SectionHeader(stringResource(R.string.weight_history_section), modifier = Modifier.weight(1f))
                if (rows.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(R.plurals.weight_history_count, rows.size, rows.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.tight))
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.weight_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Kotlin note: `itemsIndexed` is `items` with the position handed in, which is what
        // `GroupedCardItem` needs to know whether it is drawing the top or the bottom of the card.
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            val onOpen: (() -> Unit)? =
                when {
                    state.readOnly -> null
                    row.visitId != null -> ({ onOpenVisit(row) })
                    else -> ({ onEdit(row) })
                }
            GroupedCardItem(index = index, count = rows.size) {
                WeighingRow(row = row, unit = state.unit, onOpen = onOpen)
            }
        }
    }
}

/**
 * One weighing, and **where it came from** (ADR-0017).
 *
 * The whole row is the affordance now, and where it leads depends on who owns the number: a typed
 * weighing opens the editor, a visit-recorded one opens the visit. That is ADR-0017's rule drawn
 * rather than written — the visit owns its number, its date re-derives the timestamp, and clearing
 * its field deletes the row, so a second way to change it here would be a second path to a fact the
 * ADR keeps in one place. The chart above plots it identically either way: a weight is a weight
 * (ADR-0022).
 *
 * **Deleting moved to the editor** with this redraw. The design's row carries a value, a timestamp,
 * a change and a chevron and nothing else, and the two buttons it used to end with were the loudest
 * thing in a list meant to be read down.
 *
 * The change stays **neutral in colour** whichever way it points: the flag above is what raises
 * alarm, and colouring every −80 g in the history would leave nothing to escalate with.
 */
@Composable
private fun WeighingRow(
    row: WeightRow,
    unit: WeightUnit,
    onOpen: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onOpen == null) Modifier else Modifier.clickable(onClick = onOpen))
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(text = weightLabel(row.grams, unit), style = MaterialTheme.typography.titleMedium)
            Text(
                // A visit says so **inside its timestamp line** rather than taking a third line of
                // its own — it is a fact about where the number came from, not a second fact.
                text =
                    if (row.visitId == null) {
                        dateTimeLabel(row.recordedAt)
                    } else {
                        stringResource(R.string.weight_row_from_visit, dateTimeLabel(row.recordedAt))
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The change is in grams whatever the display unit says, because "−0.04 kg" hides the signal
        // that "−40 g" makes obvious (house rule). The oldest row says "first" rather than leaving a
        // hole where every other row has a number.
        Text(
            text = row.changeGrams?.let { weightChangeLabel(it) } ?: stringResource(R.string.weight_change_first),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (row.changeGrams == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        if (onOpen != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                // Decorative: the row itself is the target, and its own text is what a screen
                // reader announces.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
