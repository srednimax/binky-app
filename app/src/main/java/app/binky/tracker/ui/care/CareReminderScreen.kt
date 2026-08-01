package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.interval
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.weight.weightLabel
import kotlinx.coroutines.launch

/**
 * One care reminder: when it is next due, everything recorded against it, and the two things only
 * this screen offers — editing it, and handing it to the owner's calendar (ADR-0014).
 *
 * **The history is editable because the schedule depends on it.** A completion recorded on the wrong
 * day moves every future occurrence, so correcting it has to be possible somewhere; the list behind
 * this screen shows only the next date, which is the symptom rather than the cause.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareReminderScreen(
    reminderId: String,
    readOnly: Boolean,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onRecordWeight: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CareReminderViewModel =
        viewModel(factory = CareReminderViewModel.factory(reminderId), extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var completing by remember { mutableStateOf(false) }

    // Deleted from the list behind this screen, or with its bunny. Leaving is the honest response;
    // rendering an empty shell with a working Edit button is not.
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    val reminder = state.reminder
    val label = reminder?.let { careReminderLabel(it) }.orEmpty()
    val calendarMissing = stringResource(R.string.care_calendar_none)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                // The shell's Scaffold already owns the insets; applying them twice would pad the
                // status bar in and then again.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { insets ->
        if (reminder == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.due?.let { due ->
                        Text(text = careDueLabel(due), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = careIntervalLabel(reminder.interval),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The bare date lives here and nowhere else: the list answers "how soon", this
                    // screen answers "which day".
                    state.dueOn?.let { dueOn ->
                        Text(
                            text = dateLabel(dueOn),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!readOnly) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (state.completedByWeighing) {
                                    onRecordWeight(reminder.bunnyId)
                                } else {
                                    completing = true
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (state.completedByWeighing) {
                                        R.string.care_weigh_in_action
                                    } else {
                                        R.string.action_done
                                    },
                                ),
                            )
                        }
                        OutlinedButton(onClick = { onEdit(reminder.bunnyId, reminder.id) }) {
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                }

                if (state.completedByWeighing) {
                    item {
                        Text(
                            text = stringResource(R.string.care_weigh_in_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    CalendarHandoff(
                        handedOff = state.calendarHandedOff,
                        onAdd = {
                            val added =
                                context.addCareToCalendar(
                                    title = label,
                                    dueOn = state.dueOn ?: reminder.firstDueOn,
                                    interval = reminder.interval,
                                )
                            // Recorded only on a hand-off that actually happened — a phone with no
                            // calendar app must not come back reading "Added to your calendar".
                            if (added) {
                                viewModel.markCalendarHandedOff()
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(calendarMissing) }
                            }
                        },
                    )
                }
            }

            item {
                Text(text = stringResource(R.string.care_history_title), style = MaterialTheme.typography.titleSmall)
            }

            if (state.events.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.care_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.events, key = { it.key }) { row ->
                HistoryRow(
                    row = row,
                    unit = state.unit,
                    readOnly = readOnly,
                    onEdit = { viewModel.startEventEdit(row) },
                    onDelete = { viewModel.requestEventDelete(row) },
                )
            }

            if (state.completedByWeighing && state.events.any { it.weightGrams != null }) {
                item {
                    Text(
                        text = stringResource(R.string.care_history_weight_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (completing) {
        CompleteCareDialog(
            reminderLabel = label,
            onConfirm = { completedOn, note ->
                viewModel.complete(completedOn, note)
                completing = false
            },
            onDismiss = { completing = false },
        )
    }

    state.editingEvent?.let { row ->
        CompleteCareDialog(
            reminderLabel = label,
            title = stringResource(R.string.care_event_edit_title),
            initialDate = row.completedOn,
            initialNote = row.note.orEmpty(),
            onConfirm = viewModel::updateEvent,
            onDismiss = viewModel::cancelEventEdit,
        )
    }

    state.pendingEventDelete?.let { row ->
        AlertDialog(
            onDismissRequest = viewModel::cancelEventDelete,
            title = { Text(stringResource(R.string.care_event_delete_title)) },
            text = { Text(stringResource(R.string.care_event_delete_body, dateLabel(row.completedOn))) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEventDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEventDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * The one-way hand-off, and the button that stops offering itself once it has happened.
 *
 * "Added to your calendar" is not a claim that the two are in step — the help text says plainly that
 * they are not. It is only what stops a second tap minting a second event (ADR-0014).
 */
@Composable
private fun CalendarHandoff(
    handedOff: Boolean,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (handedOff) {
            Text(
                text = stringResource(R.string.care_calendar_added),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.care_calendar_add)) }
        }
        Text(
            text = stringResource(R.string.care_calendar_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One completion — the owner's, or a weighing standing in for one.
 *
 * A weight-derived row carries **no delete**, and that is the point: the row it stands for lives on
 * the Weight screen, and offering to remove it from here would either do nothing or delete a
 * weighing from a screen about nail trims.
 */
@Composable
private fun HistoryRow(
    row: CareEventRow,
    unit: WeightUnit,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = dateLabel(row.completedOn), style = MaterialTheme.typography.bodyLarge)
            row.weightGrams?.let { grams ->
                Text(
                    text = stringResource(R.string.care_history_weight, weightLabel(grams, unit)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
            if (!readOnly && row.editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
