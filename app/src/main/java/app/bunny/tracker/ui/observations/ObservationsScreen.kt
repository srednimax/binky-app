package app.bunny.tracker.ui.observations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.data.SymptomEntity
import app.bunny.tracker.data.TrayFacts
import app.bunny.tracker.ui.appViewModelExtras
import app.bunny.tracker.ui.bunny.dateLabel
import app.bunny.tracker.ui.bunny.joinNames
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.weight.timeLabel
import java.time.ZoneId

/**
 * Observations — the day-grouped timeline, and the one-tap healthy day.
 *
 * Under "All bunnies" this is the **combined** timeline across every active bunny, with rows sharing
 * a group id collapsed into one entry; selecting a bunny *filters* it. Because an observation can
 * cover several bunnies at once (ADR-0008), the single-bunny view is the special case here — the
 * opposite way round from Weight (ADR-0015).
 *
 * In the archived scope it renders read-only: no "+", no healthy day, no per-row edit or delete
 * (ADR-0004).
 *
 * No Compose UI tests (ADR-0012, as in 1c); the collapse and the day grouping beneath are covered by
 * `ObservationTimelineTest`.
 */
@Composable
fun ObservationsScreen(
    shell: ShellUiState,
    /**
     * The **shell's** host, not one of this screen's own: it has to be the same Scaffold that owns
     * the "+" FAB, or the FAB lands on top of the Undo action. See `Navigation.kt`.
     */
    snackbarHostState: SnackbarHostState,
    onEditObservation: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ObservationsViewModel =
        viewModel(factory = ObservationsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Under "All bunnies" there is no bunny to pre-select a fluffle from, so both write paths ask
    // which bunny first rather than sweeping one tray fact across bunnies that share no tray
    // (ADR-0008). The single-bunny path is untouched and stays one tap.
    var choosingForHealthyDay by rememberSaveable { mutableStateOf(false) }

    HealthyDaySnackbar(
        receipt = state.receipt,
        hostState = snackbarHostState,
        onUndo = viewModel::undoHealthyDay,
        onDismiss = viewModel::dismissReceipt,
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (state.selection) {
            // Momentary, before the first database and preferences emissions arrive.
            BunnySelection.Loading -> Unit
            BunnySelection.Empty ->
                Text(
                    text = stringResource(R.string.add_a_bunny_first),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            else ->
                Timeline(
                    state = state,
                    onHealthyDay = {
                        val bunnyId = state.bunnyId
                        if (bunnyId != null) viewModel.logHealthyDay(bunnyId) else choosingForHealthyDay = true
                    },
                    onEdit = { entry ->
                        val participant = entry.participants.first { it.observationId == entry.id }
                        onEditObservation(participant.bunnyId, entry.id)
                    },
                    onDelete = viewModel::requestDelete,
                )
        }
    }

    if (choosingForHealthyDay) {
        ChooseBunnyDialog(
            title = stringResource(R.string.healthy_day_which_bunny),
            bunnies = shell.activeBunnies,
            onPick = { bunnyId ->
                choosingForHealthyDay = false
                viewModel.logHealthyDay(bunnyId)
            },
            onDismiss = { choosingForHealthyDay = false },
        )
    }

    state.pendingDelete?.let { entry ->
        DeleteObservationDialog(
            entry = entry,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * The snackbar that names who the healthy day covered, with **Undo** (ADR-0008).
 *
 * A `LaunchedEffect` keyed on the receipt, because `showSnackbar` suspends until the snackbar is
 * dismissed or its action is tapped — the Compose way of saying "show this, then tell me what
 * happened". There is no promise equivalent; the nearest is an effect that awaits a user event.
 */
@Composable
private fun HealthyDaySnackbar(
    receipt: HealthyDayReceipt?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val message =
        receipt?.let {
            // The flag is named beside the bunny it belongs to rather than in a trailing clause, so
            // "Bijou (weight flag) & Nugget" cannot be misread as covering both.
            val names =
                it.names.map { name ->
                    if (name in it.flaggedNames) resources.getString(R.string.healthy_day_name_flagged, name) else name
                }
            resources.getString(R.string.healthy_day_logged, joinNames(resources, names))
        }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(receipt) {
        if (message == null) return@LaunchedEffect
        val result = hostState.showSnackbar(message = message, actionLabel = undoLabel)
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onDismiss()
        }
    }
}

@Composable
private fun Timeline(
    state: ObservationsUiState,
    onHealthyDay: () -> Unit,
    onEdit: (TimelineEntry) -> Unit,
    onDelete: (TimelineEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val symptomsById = remember(state.symptoms) { state.symptoms.associateBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.readOnly) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onHealthyDay, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.healthy_day_action))
                    }
                    // The button names what it records, because one tap commits facts on the owner's
                    // behalf and they are entitled to know which (ADR-0001).
                    Text(
                        text = stringResource(R.string.healthy_day_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.isEmpty) {
            item {
                Text(
                    // A statement about the record, never about the bunny (ADR-0001).
                    text = stringResource(R.string.observations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Text(text = dateLabel(day.date), style = MaterialTheme.typography.titleSmall)
            }
            items(day.entries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    symptomsById = symptomsById,
                    focusBunnyId = state.bunnyId,
                    readOnly = state.readOnly,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

/**
 * One entry — **one real-world moment**, however many bunnies it covered.
 *
 * The tray facts are rendered once and the individual facts per named bunny, which is the storage
 * model's tray/individual split showing through: rendering the tray per participant would produce
 * two apparently duplicate cards for the same litter tray and inflate the apparent observation count
 * on the one screen meant to give a fluffle-wide read (ADR-0008).
 */
@Composable
private fun EntryCard(
    entry: TimelineEntry,
    symptomsById: Map<String, SymptomEntity>,
    /** Whose timeline this is, or null under "All bunnies". Only affects who the card names. */
    focusBunnyId: String?,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val resources = LocalResources.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = timeLabel(entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }

            // Who to name: everyone under "All bunnies", and only the *others* on one bunny's own
            // timeline — "observed together with Bijou" on Bijou's page names her back to herself.
            val others = entry.participants.filterNot { it.bunnyId == focusBunnyId }.map { it.name }
            Text(
                text =
                    when {
                        !entry.shared -> joinNames(resources, entry.participants.map { it.name })
                        // "Observed together" comes from the group id, so a lone survivor still says
                        // it — never silently downgraded to an individual observation (ADR-0008).
                        entry.participants.size > 1 ->
                            stringResource(R.string.observation_observed_together, joinNames(resources, others))
                        // ...and says it **un-named**: the housemate was deleted, or is archived and
                        // out of this scope. ADR-0008 wants no tombstone of them — the marker alone
                        // keeps the record honest, and inventing a name for the gap would not.
                        else -> stringResource(R.string.observation_observed_together_alone)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TrayFactLines(entry.tray)

            entry.participants.forEach { participant ->
                IndividualFactLines(
                    participant = participant,
                    named = entry.participants.size > 1,
                    symptomsById = symptomsById,
                )
            }

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
 * The tray facts, **only the ones actually recorded**.
 *
 * A field nobody checked prints no line at all. Printing "Droppings: not checked" for every untouched
 * field would bury the recorded ones, and printing a default would be the manufactured "fine"
 * ADR-0001 forbids — so absence is shown by absence, and every line on screen is something the owner
 * really answered.
 */
@Composable
private fun TrayFactLines(tray: TrayFacts) {
    tray.droppingsAmount?.let { FactLine(stringResource(R.string.observation_droppings_amount_label), label(it)) }
    tray.droppingsSize?.let { FactLine(stringResource(R.string.observation_droppings_size_label), label(it)) }
    tray.droppingsForm?.let { FactLine(stringResource(R.string.observation_droppings_form_label), label(it)) }
    tray.cecotropes?.let { FactLine(stringResource(R.string.observation_cecotropes_label), label(it)) }
}

@Composable
private fun IndividualFactLines(
    participant: TimelineParticipant,
    named: Boolean,
    symptomsById: Map<String, SymptomEntity>,
) {
    val facts = participant.facts
    val ticked = facts.symptomIds.mapNotNull { symptomsById[it] }
    val hasAnything =
        facts.appetite != null ||
            facts.mood != null ||
            facts.activity != null ||
            facts.water != null ||
            !facts.note.isNullOrBlank() ||
            facts.symptomsChecked
    if (!hasAnything) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (named) {
            Text(text = participant.name, style = MaterialTheme.typography.titleSmall)
        }
        facts.appetite?.let { FactLine(stringResource(R.string.observation_appetite_label), label(it)) }
        facts.mood?.let { FactLine(stringResource(R.string.observation_mood_label), label(it)) }
        facts.activity?.let { FactLine(stringResource(R.string.observation_activity_label), label(it)) }
        facts.water?.let { FactLine(stringResource(R.string.observation_water_label), label(it)) }

        if (ticked.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ticked.forEach { symptom ->
                    Text(text = symptomLabel(symptom), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (facts.symptomsChecked) {
            // The affirmative claim, and the whole reason `symptomsChecked` is a column: *looked,
            // none seen*, which no count of links could express (ADR-0010).
            Text(
                text = stringResource(R.string.observation_no_symptoms_seen),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        facts.note?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FactLine(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * **One** confirmation, naming who it affects.
 *
 * This deletes every participant's row — "that observation was wrong", one event about one moment.
 * Removing a single bunny from it is the form's participant edit, a different claim entirely
 * (ADR-0008).
 */
@Composable
private fun DeleteObservationDialog(
    entry: TimelineEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val names = joinNames(resources, entry.participants.map { it.name })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.observation_delete_title)) },
        text = {
            Text(
                if (entry.shared) {
                    stringResource(R.string.observation_delete_body_shared, names)
                } else {
                    stringResource(R.string.observation_delete_body, names)
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
