package app.binky.tracker.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.care.addEventToCalendar
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.ChangeableValueRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.NoteField
import app.binky.tracker.ui.common.SingleLineField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * Add or edit one event, and — because an event has no detail screen of its own — hand it to the
 * calendar and delete it from here too (ADR-0031, ADR-0014).
 *
 * **The date picker refuses nothing**, which makes it the second one in the app after the care
 * reminder's, and for a related reason. Everything else dated in Binky is a record of something that
 * happened; an event is as often *"neutering, next Thursday"* as *"came home, three years ago"*, and
 * a picker that rejected either half would be rejecting the point of the screen.
 *
 * *Save* is in the app bar rather than at the foot of the scroll, which is `3e`'s rule for every
 * editor in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    bunnyId: String,
    eventId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EventEditorViewModel =
        viewModel(
            factory = EventEditorViewModel.factory(bunnyId, eventId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val calendarMissing = stringResource(R.string.care_calendar_none)

    // A save or a delete has landed; the screen's only job now is to leave. One mechanism, so the
    // form and the stored row cannot disagree about whether the write happened.
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.event_editor_add_title else R.string.event_editor_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) } },
                // The shell's Scaffold is the one owner of window insets; padding here would double it.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { insets ->
        if (state.loading) return@Scaffold

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
        ) {
            GroupedCard(
                contentPadding = PaddingValues(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    FieldLabel(stringResource(R.string.event_label))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                        SingleLineField(
                            value = state.label,
                            onValueChange = viewModel::setLabel,
                            isError = state.labelInvalid,
                        )
                        if (state.labelInvalid) {
                            ErrorText(stringResource(R.string.event_label_required))
                        } else {
                            HelpText(stringResource(R.string.event_label_help))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    FieldLabel(stringResource(R.string.event_note_label))
                    NoteField(value = state.note, onValueChange = viewModel::setNote)
                }
            }

            Spacer(Modifier.height(Spacing.section))

            GroupedCard(contentPadding = PaddingValues(top = Spacing.hair, bottom = Spacing.base)) {
                ChangeableValueRow(
                    label = stringResource(R.string.event_date_label),
                    value = dateLabel(state.occursOn),
                    // The button says only *Change*, as every other value row in the app does; the
                    // stacked label above it is what says which date changes, and the description
                    // is what a screen reader hears in its place.
                    description = stringResource(R.string.recorded_at_pick_date),
                    stacked = true,
                    onChange = { pickingDate = true },
                )
                HelpText(
                    text = stringResource(R.string.event_date_help),
                    modifier = Modifier.padding(horizontal = Spacing.base),
                )
            }

            // Both of these act on a **stored** row, so neither is offered until there is one. An
            // owner adding an event saves it and comes straight back into this screen through the
            // timeline, which is one tap and no lost typing — where a hand-off that had to save
            // first would be a button that silently did two things.
            if (!state.isNew) {
                Spacer(Modifier.height(Spacing.section))
                CalendarSection(
                    handedOff = state.calendarHandedOff,
                    onAddToCalendar = {
                        val added = context.addEventToCalendar(title = state.label.trim(), on = state.occursOn)
                        // Recorded only on a hand-off that actually happened — a phone with no
                        // calendar app must not come back reading "Added to your calendar".
                        if (added) {
                            viewModel.markCalendarHandedOff()
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(calendarMissing) }
                        }
                    },
                )

                Spacer(Modifier.height(Spacing.section))
                // The quietest thing on the screen: it is the only one that destroys anything.
                TextButton(onClick = viewModel::requestDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickingDate) {
        // Unlike every dated *record* in this app, and like the care reminder's due date, this one
        // refuses nothing: an event is as legitimately in the future as in the past.
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.occursOn
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            viewModel.setOccursOn(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        pickingDate = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (state.confirmingDelete) {
        BinkyDialog(
            title = stringResource(R.string.event_delete_title),
            // The label rather than a generic "this event": the owner wrote it, and it is the one
            // thing on the dialog that tells them *which* row they are about to lose.
            subject = state.label.trim(),
            onDismiss = viewModel::dismissDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(stringResource(R.string.event_delete_body))
        }
    }
}

/**
 * The one-way hand-off to the owner's calendar, and the button that stops offering itself once it
 * has happened (ADR-0014).
 *
 * "Added to your calendar" is not a claim that the two are in step — the help text below says
 * plainly that they are not. It is only what stops a second tap minting a second calendar entry, and
 * it is the same wording the care reminder uses because it is the same promise.
 */
@Composable
private fun CalendarSection(
    handedOff: Boolean,
    onAddToCalendar: () -> Unit,
) {
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
    HelpText(stringResource(R.string.event_calendar_help))
}
