package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.interval
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.CautionDot
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRowHeight
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.weight.weightLabel
import kotlinx.coroutines.launch

/**
 * One care reminder: when it is next due, everything recorded against it, and the two things only
 * this screen offers — editing it, and handing it to the owner's calendar (ADR-0014).
 *
 * **The history is editable because the schedule depends on it.** A completion recorded on the wrong
 * day moves every future occurrence, so correcting it has to be possible somewhere; the list behind
 * this screen shows only the next date, which is the symptom rather than the cause.
 *
 * **Phase 7 (`10l`), and it is deliberately `10k` again.** A course and a reminder are the same
 * shape — what is next, what to do about it, what has happened — so they get the same two cards.
 * The one thing that differs is what sits below the card's hairline: on a course it is *ending*,
 * here it is the **calendar hand-off**, because that is the action on this screen which is not about
 * this occurrence. It stays inside the card rather than taking one of its own: it acts on this
 * reminder, and its help text is long enough that a separate card would read as a second subject.
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
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding =
                PaddingValues(
                    start = Spacing.base,
                    end = Spacing.base,
                    top = Spacing.tight,
                    bottom = Spacing.section,
                ),
        ) {
            item {
                ReminderCard(
                    due = state.due,
                    interval = careIntervalLabel(reminder.interval),
                    dueOn = state.dueOn?.let { dateLabel(it) },
                    readOnly = readOnly,
                    byWeighing = state.completedByWeighing,
                    handedOff = state.calendarHandedOff,
                    onDone = {
                        if (state.completedByWeighing) {
                            onRecordWeight(reminder.bunnyId)
                        } else {
                            completing = true
                        }
                    },
                    onEdit = { onEdit(reminder.bunnyId, reminder.id) },
                    onDelete = viewModel::requestDelete,
                    onAddToCalendar = {
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

            item {
                Spacer(Modifier.height(Spacing.section))
                SectionHeader(stringResource(R.string.care_history_title))
                Spacer(Modifier.height(Spacing.tight))
            }

            if (state.events.isEmpty()) {
                item { EmptySection(stringResource(R.string.care_history_empty)) }
            }

            // `GroupedCardItem` rather than one `GroupedCard` around the lot: a weekly reminder kept
            // for a year is fifty rows, and a card wrapping them all would be a single lazy item.
            itemsIndexed(state.events, key = { _, row -> row.key }) { index, row ->
                GroupedCardItem(index = index, count = state.events.size) {
                    HistoryRow(
                        row = row,
                        unit = state.unit,
                        readOnly = readOnly,
                        onEdit = { viewModel.startEventEdit(row) },
                        onDelete = { viewModel.requestEventDelete(row) },
                    )
                }
            }

            if (state.completedByWeighing && state.events.any { it.weightGrams != null }) {
                item {
                    Spacer(Modifier.height(Spacing.base))
                    HelpText(
                        text = stringResource(R.string.care_history_weight_help),
                        modifier = Modifier.padding(start = Spacing.hair),
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

    if (state.confirmingDelete) {
        // **One** confirmation, not two. ADR-0004's ceremony is calibrated to a bunny's whole
        // history; a reminder is a schedule, and the dialog names it so the owner can see which.
        BinkyDialog(
            title = stringResource(R.string.care_delete_title),
            onDismiss = viewModel::cancelDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(stringResource(R.string.care_delete_body, label))
        }
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
        BinkyDialog(
            title = stringResource(R.string.care_event_delete_title),
            onDismiss = viewModel::cancelEventDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmEventDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEventDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(stringResource(R.string.care_event_delete_body, dateLabel(row.completedOn)))
        }
    }
}

/**
 * The reminder itself: when it is next due, and everything that can be done about it.
 *
 * `10k`'s card with one substitution. *Done*, *Edit* and *Delete* stay on one row here rather than
 * splitting around the hairline, because all three are about **this occurrence**; what goes below
 * the rule is the calendar hand-off, which is about the repeat rather than the next one.
 *
 * **An overdue reminder takes the [CautionDot]**, which is the one thing `10l` adds that the app did
 * not already draw. It is the marker's own definition — *the app itself is raising this* — and a
 * reminder whose day has been and gone is the app raising something. Red is not available and never
 * was (ADR-0026): the copy says how many days, not that anybody failed.
 */
@Composable
private fun ReminderCard(
    due: CareDue?,
    interval: String,
    dueOn: String?,
    readOnly: Boolean,
    byWeighing: Boolean,
    handedOff: Boolean,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToCalendar: () -> Unit,
) {
    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        if (due != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (due is CareDue.Yesterday || due is CareDue.Overdue) CautionDot()
                Text(text = careDueLabel(due), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(Spacing.hair))
        }
        Text(
            text = interval,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The bare date lives here and nowhere else: the list answers "how soon", this screen
        // answers "which day".
        if (dueOn != null) {
            Text(
                text = dueOn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (readOnly) return@GroupedCard

        Spacer(Modifier.height(Spacing.base))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onDone) {
                Text(
                    stringResource(
                        if (byWeighing) R.string.care_weigh_in_action else R.string.action_done,
                    ),
                )
            }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            // **Deleting the reminder lives here from Phase 7.** The list behind this screen draws
            // 64dp rows with a chevron and nowhere to put a button (`3a`), which is the finding
            // `Weight` made at `1d`. Deliberately the quietest of the three: only one of them
            // destroys anything.
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (byWeighing) {
            Spacer(Modifier.height(Spacing.tight))
            HelpText(stringResource(R.string.care_weigh_in_help))
        }

        Spacer(Modifier.height(Spacing.base))
        // Not `RowDivider`: this card has already inset its contents, so a plain divider stops at
        // the text edge on its own.
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.base))

        // The one-way hand-off, and the button that stops offering itself once it has happened.
        // "Added to your calendar" is not a claim that the two are in step — the help text below
        // says plainly that they are not. It is only what stops a second tap minting a second
        // event (ADR-0014).
        if (handedOff) {
            Text(
                text = stringResource(R.string.care_calendar_added),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OutlinedButton(onClick = onAddToCalendar) {
                Text(stringResource(R.string.care_calendar_add))
            }
        }
        Spacer(Modifier.height(Spacing.tight))
        HelpText(stringResource(R.string.care_calendar_help))
    }
}

/**
 * One completion — the owner's, or a weighing standing in for one.
 *
 * A weight-derived row carries **no delete**, and that is the point: the row it stands for lives on
 * the Weight screen, and offering to remove it from here would either do nothing or delete a
 * weighing from a screen about nail trims. `10l` draws exactly that — the weight row with no
 * buttons beside it — so the rule now shows rather than only holding.
 */
@Composable
private fun HistoryRow(
    row: CareEventRow,
    unit: WeightUnit,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
        horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
        // Top, not centre: a completion with a note is three lines tall and its buttons belong
        // beside the first of them.
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(text = dateLabel(row.completedOn), style = MaterialTheme.typography.bodyLarge)
            row.weightGrams?.let { grams ->
                Text(
                    text = stringResource(R.string.care_history_weight, weightLabel(grams, unit)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!readOnly && row.editable) {
            // Pushed outward by a text button's own padding, so the last one's text edge lines up
            // with the row's inset rather than sitting a button's worth of air short of it.
            Row(
                modifier = Modifier.offset(x = Spacing.snug, y = -Spacing.hair),
                horizontalArrangement = Arrangement.spacedBy(Spacing.hair),
            ) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
