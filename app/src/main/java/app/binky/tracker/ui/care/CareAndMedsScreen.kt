package app.binky.tracker.ui.care

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.interval
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.observations.ChooseBunnyDialog
import app.binky.tracker.ui.reminders.RemindersOptIn
import app.binky.tracker.ui.shell.ShellUiState
import app.binky.tracker.ui.weight.gramsLabel
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.openBatteryOptimisationSettings
import app.binky.tracker.work.reminderDelivery

/**
 * Care — one bunny's recurring care, due first (ADR-0018), **and its vet visits** (ADR-0017).
 *
 * **The tab is a hub from 1.2**, and that is a decision about where things live rather than a
 * layout: care reminders, vet visits and — at 5e — medication courses are all *this bunny's ongoing
 * care*, so they share the bunny-scoped tab. The **vet directory is not here**: a vet is app-wide,
 * so it lives in More (ADR-0015), and only the visits are per bunny.
 *
 * **The tab is live from 1.1**, which is the one-value flip 3f left in place: this screen stopped
 * being a stub the moment there was something real behind it, and `StubScreen` lost its last caller
 * on the same commit. The nav key kept its name, because a back stack saved by 1.0 has to stay
 * resolvable (ADR-0015).
 *
 * It is **per bunny, like weight and photos**: under "All bunnies" the tab asks which one and then
 * selects them app-wide, because [app.binky.tracker.CareAndMeds] takes no arguments and selecting is
 * the only thing that can decide whose reminders these are.
 *
 * The delivery line at the top is 4a's three honest states, not decoration. A reminder whose
 * notification will never arrive is still worth having — the list carries overdue state on its own —
 * but the screen must not let it read as an armed alarm (ADR-0003).
 */
@Composable
fun CareAndMedsScreen(
    state: ShellUiState,
    onSelectBunny: (String) -> Unit,
    onAddReminder: (String) -> Unit,
    onOpenReminder: (String) -> Unit,
    onRecordWeight: (String) -> Unit,
    onAddVisit: (String) -> Unit,
    onOpenVisit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CareViewModel = viewModel(factory = CareViewModel.Factory, extras = appViewModelExtras())
    val careState by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingBunny by remember { mutableStateOf(false) }

    when (careState.selection) {
        // Momentary, before the first database and preferences emissions arrive.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> Message(stringResource(R.string.add_a_bunny_first), modifier)
        BunnySelection.All ->
            PickABunny(
                onPick = { choosingBunny = true },
                modifier = modifier,
            )
        else ->
            CareList(
                state = careState,
                onAdd = { careState.bunnyId?.let(onAddReminder) },
                onOpen = { row -> onOpenReminder(row.id) },
                onComplete = { row ->
                    if (row.completedByWeighing) {
                        careState.bunnyId?.let(onRecordWeight)
                    } else {
                        viewModel.startCompleting(row)
                    }
                },
                onDelete = viewModel::requestDelete,
                onAddVisit = { careState.bunnyId?.let(onAddVisit) },
                onOpenVisit = { row -> careState.bunnyId?.let { onOpenVisit(it, row.id) } },
                onDeleteVisit = viewModel::requestVisitDelete,
                modifier = modifier,
            )
    }

    if (choosingBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.care_pick_a_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingBunny = false
                onSelectBunny(bunnyId)
            },
            onDismiss = { choosingBunny = false },
        )
    }

    careState.completing?.let { row ->
        CompleteCareDialog(
            reminderLabel = careReminderLabel(row.scheduled.reminder),
            onConfirm = viewModel::complete,
            onDismiss = viewModel::cancelCompleting,
        )
    }

    careState.pendingDelete?.let { row ->
        DeleteReminderDialog(
            reminderLabel = careReminderLabel(row.scheduled.reminder),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    careState.pendingVisitDelete?.let { row ->
        DeleteVisitDialog(
            row = row,
            onConfirm = viewModel::confirmVisitDelete,
            onDismiss = viewModel::cancelVisitDelete,
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
private fun PickABunny(
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.care_pick_a_bunny), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPick) { Text(stringResource(R.string.care_choose_bunny)) }
    }
}

@Composable
private fun CareList(
    state: CareUiState,
    onAdd: () -> Unit,
    onOpen: (CareRow) -> Unit,
    onComplete: (CareRow) -> Unit,
    onDelete: (CareRow) -> Unit,
    onAddVisit: () -> Unit,
    onOpenVisit: (VisitRow) -> Unit,
    onDeleteVisit: (VisitRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Not in the archived scope: an archived bunny is never swept, so a line about how reliably
        // its notifications arrive would be describing something that will not happen either way.
        if (!state.readOnly) {
            item { DeliveryLine() }
        }

        // Headed from 1.2, and not before: one list needs no header, and two unlabelled ones read
        // as a single list whose rows have stopped making sense.
        item { SectionHeader(stringResource(R.string.care_reminders_heading)) }

        // No add / complete / edit affordances at all in the archived scope, rather than
        // affordances that refuse when tapped (ADR-0004).
        if (!state.readOnly) {
            item {
                Button(onClick = onAdd) { Text(stringResource(R.string.care_add)) }
            }
        }

        if (state.rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.care_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.rows, key = { it.id }) { row ->
            ReminderRow(
                row = row,
                readOnly = state.readOnly,
                onOpen = { onOpen(row) },
                onComplete = { onComplete(row) },
                onDelete = { onDelete(row) },
            )
        }

        item { SectionHeader(stringResource(R.string.visits_heading)) }

        if (!state.readOnly) {
            item {
                Button(onClick = onAddVisit) { Text(stringResource(R.string.visit_add)) }
            }
        }

        if (state.visits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.visits_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Prefixed, because a visit and a reminder can be two different rows sharing one UUID space
        // and `LazyColumn` keys have to be unique across the *whole* list rather than per section.
        items(state.visits, key = { "visit-${it.id}" }) { row ->
            VisitCard(
                row = row,
                readOnly = state.readOnly,
                onOpen = { onOpenVisit(row) },
                onDelete = { onDeleteVisit(row) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/**
 * One visit: the day, what it was for, who was seen, and the weighing taken at it.
 *
 * The weighing is shown because it is the visit's own record of it (ADR-0017) — **the same row** the
 * Weight screen draws, read back through the join rather than copied here.
 */
@Composable
private fun VisitCard(
    row: VisitRow,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = row.reason, style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    row.vetName?.let { stringResource(R.string.visit_row_with_vet, dateLabel(row.visitedOn), it) }
                        ?: dateLabel(row.visitedOn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.weightGrams?.let { grams ->
                Text(
                    text = stringResource(R.string.visit_row_weighed, gramsLabel(grams)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

/**
 * Deleting a visit **states the choice about its weighing** rather than guessing (PLAN 5c).
 *
 * One confirmation, not ADR-0004's two-stage ceremony — that is calibrated to a bunny's whole
 * history. But the weighing is a second record with a life of its own: keeping it leaves a
 * standalone number in the chart, and removing it takes the vet's reading out of the series. The
 * default is **keep**, because it is the recoverable one.
 *
 * With no weighing at the visit there is nothing to choose, and the dialog says so in one line.
 */
@Composable
private fun DeleteVisitDialog(
    row: VisitRow,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var keepWeighing by remember(row.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.visit_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.visit_delete_body, dateLabel(row.visitedOn)))
                row.weightGrams?.let { grams ->
                    Text(stringResource(R.string.visit_delete_weighing, gramsLabel(grams)))
                    WeighingChoice(
                        label = stringResource(R.string.visit_delete_keep_weighing),
                        selected = keepWeighing,
                        onSelect = { keepWeighing = true },
                    )
                    WeighingChoice(
                        label = stringResource(R.string.visit_delete_remove_weighing),
                        selected = !keepWeighing,
                        onSelect = { keepWeighing = false },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(row.weightGrams == null || keepWeighing) }) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun WeighingChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * One reminder: what it is, when it is next due **in words**, and how often it comes round.
 *
 * The whole card opens the reminder, where its history and the calendar hand-off live. *Done* stays
 * on the row, because completing is the thing an owner came here to do and a screen away is a screen
 * too far.
 */
@Composable
private fun ReminderRow(
    row: CareRow,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = careTypeIcon(row.scheduled.reminder.type),
                    // The label beside it carries the name; describing the icon too would only make
                    // a screen reader say it twice.
                    contentDescription = null,
                )
                Text(
                    text = careReminderLabel(row.scheduled.reminder),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.care_reminder_of,
                        careDueLabel(row.due),
                        careIntervalLabel(row.scheduled.reminder.interval),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                // Overdue is stated, not shouted: the same voice as "due tomorrow", because a
                // notification that escalates daily is the wallpaper ADR-0001 rejects and the
                // screen is what carries this state indefinitely.
                color =
                    if (row.due is CareDue.Overdue || row.due is CareDue.Yesterday) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onComplete) {
                        Text(
                            stringResource(
                                if (row.completedByWeighing) R.string.care_weigh_in_action else R.string.action_done,
                            ),
                        )
                    }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

/**
 * What will actually happen when something comes due — blocked, best-effort or armed (ADR-0003).
 *
 * Every fact behind it belongs to Android and the owner can change any of them by walking into
 * system settings and back, so it is re-read on each resume rather than remembered. The **armed**
 * case renders nothing: a line confirming that a working app works is the kind of reassurance an
 * owner learns to stop reading, and then does not read the one that matters.
 */
@Composable
private fun DeliveryLine() {
    val context = LocalContext.current
    var delivery by remember { mutableStateOf<ReminderDelivery?>(null) }
    LifecycleResumeEffect(Unit) {
        delivery = context.reminderDelivery(ReminderChannel.Care)
        onPauseOrDispose {}
    }

    when (delivery) {
        null, ReminderDelivery.Armed -> Unit
        // **The point-of-use ask** (ADR-0006), and the one composable the setup wizard also hosts —
        // never a second opt-in written here. Android permits two `POST_NOTIFICATIONS` denials
        // before it stops asking for good, and two separately-written asks are two places to spend
        // them from. It explains before it requests, and it knows the difference between a refusal
        // and Android refusing to ask again; a bare "open settings" here would send an owner who
        // has never been asked the long way round.
        ReminderDelivery.Blocked -> RemindersOptIn()
        ReminderDelivery.BestEffort ->
            DeliveryState(
                text = stringResource(R.string.reminders_state_best_effort),
                actionLabel = stringResource(R.string.reminders_battery_action),
                onAction = { context.openBatteryOptimisationSettings() },
            )
    }
}

@Composable
private fun DeliveryState(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

/**
 * **One** confirmation, not two. ADR-0004's two-stage ceremony is calibrated to a bunny's whole
 * history; a reminder is a schedule, and the dialog names it so the owner can see which one.
 */
@Composable
private fun DeleteReminderDialog(
    reminderLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.care_delete_title)) },
        text = { Text(stringResource(R.string.care_delete_body, reminderLabel)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
