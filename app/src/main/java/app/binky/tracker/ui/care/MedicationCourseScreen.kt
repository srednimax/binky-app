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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.DoseEntity
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRowHeight
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.weight.timeLabel
import java.time.ZoneId

/**
 * One medication course: what it is, what is next, and **everything recorded against it**.
 *
 * The tab behind this screen answers "what do I give today". This one answers "what has actually
 * been given", which is the question a vet asks at the follow-up and the reason the history is
 * editable rather than a log.
 *
 * The ADR-0026 line is repeated at the foot of this screen as well as under the course list. It is
 * one sentence, and this is the screen most likely to be looked at by somebody deciding what the app
 * claims to be — an owner reading their own record, or a Play reviewer reading a screenshot.
 *
 * **Phase 7 (`10k`).** Two things changed and neither is this screen's own decision. The facts and
 * the three actions became **one card**, because the buttons act on the thing above them — and
 * ending is separated inside that card by a hairline rather than by a gap, since it changes the
 * course rather than recording against it. The dose history went from three stacked cards to one
 * grouped card of rows, which is what the app does everywhere else with a list of same-shaped
 * things; three floating cards for three doses was the heaviest thing on the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCourseScreen(
    courseId: String,
    readOnly: Boolean,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MedicationCourseViewModel =
        viewModel(factory = MedicationCourseViewModel.factory(courseId), extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted from the list behind this screen, or with its bunny. Leaving is the honest response;
    // rendering an empty shell with a working Edit button is not.
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    val course = state.course

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(course?.name.orEmpty()) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                // The shell's Scaffold already owns the insets; applying them twice would pad the
                // status bar in and then again.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { insets ->
        if (course == null) return@Scaffold

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
                CourseCard(
                    doseAmount = course.doseAmount,
                    schedule = courseScheduleLabel(state.times),
                    next = nextDoseLabel(state.next),
                    range = courseRangeLabel(course.startOn, course.endOn),
                    notes = course.notes,
                    readOnly = readOnly,
                    open = state.open,
                    onRecord = viewModel::startRecording,
                    onEdit = { onEdit(course.bunnyId, course.id) },
                    onEnd = viewModel::endCourse,
                    onDelete = viewModel::requestDelete,
                )
            }

            item {
                Spacer(Modifier.height(Spacing.section))
                SectionHeader(stringResource(R.string.med_history_title))
                Spacer(Modifier.height(Spacing.tight))
            }

            if (state.doses.isEmpty()) {
                item { EmptySection(stringResource(R.string.med_history_empty)) }
            }

            // `GroupedCardItem` rather than one `GroupedCard` around the lot: a long course is a
            // long history — twice a day for a fortnight is 28 rows — and a card wrapping them all
            // would be a single lazy item, so every row composes whether or not it is on screen.
            itemsIndexed(state.doses, key = { _, dose -> dose.id }) { index, dose ->
                GroupedCardItem(index = index, count = state.doses.size) {
                    DoseHistoryRow(
                        dose = dose,
                        readOnly = readOnly,
                        onEdit = { viewModel.startDoseEdit(dose) },
                        onDelete = { viewModel.requestDoseDelete(dose) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(Spacing.base))
                MedicationDisclaimer(Modifier.padding(start = Spacing.hair))
            }
        }
    }

    if (state.recording) {
        RecordDoseDialog(
            courseName = course?.name.orEmpty(),
            doseAmount = course?.doseAmount.orEmpty(),
            onConfirm = { status, at, note -> viewModel.recordAdHoc(status, at, note) },
            onDismiss = viewModel::cancelRecording,
        )
    }

    state.editingDose?.let { dose ->
        RecordDoseDialog(
            courseName = course?.name.orEmpty(),
            doseAmount = course?.doseAmount.orEmpty(),
            title = stringResource(R.string.med_record_edit_title),
            slotTime = dose.scheduledTime,
            initialStatus = dose.status,
            initialAt = dose.recordedAt,
            initialNote = dose.note.orEmpty(),
            onConfirm = { status, at, note -> viewModel.updateDose(status, at, note) },
            onDismiss = viewModel::cancelDoseEdit,
        )
    }

    if (state.confirmingDelete && course != null) {
        DeleteCourseDialog(
            courseName = course.name,
            // The screen is already holding every dose recorded against this course, so the number
            // the dialog names is the list on screen rather than a second `COUNT(*)` that could
            // disagree with it.
            doseCount = state.doses.size,
            open = state.open,
            onConfirm = viewModel::confirmDelete,
            onEndInstead = viewModel::endCourse,
            onDismiss = viewModel::cancelDelete,
        )
    }

    state.pendingDoseDelete?.let {
        BinkyDialog(
            title = stringResource(R.string.med_dose_delete_title),
            onDismiss = viewModel::cancelDoseDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDoseDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDoseDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            Text(stringResource(R.string.med_dose_delete_body))
        }
    }
}

/**
 * The course itself: what it is, and everything that can be done to it.
 *
 * One card rather than a column of loose text and three button rows, and the ordering inside it is
 * the whole of `10k`'s argument. *Record a dose* and *Edit* sit directly under the facts because
 * they act on the thing above them. *End the course* is below a **hairline** rather than a gap:
 * ending changes the course, where recording only adds to it, and a rule is what separates two kinds
 * of action rather than two subjects. *Delete* is last and quiet — `onSurfaceVariant`, the file's
 * treatment for the one action on a screen that destroys a health record.
 *
 * The drawing gives the filled button 52dp and the outlined one 44dp. Neither is taken: 52dp is
 * [app.binky.tracker.ui.common.RecordButtonHeight], which is for the action a *tab* exists for
 * standing alone between two sections, and 44dp is a step the system does not have (`9c` declined it
 * once already). What carries the weight here is the fill, which is `9c`'s app-wide rule — one
 * filled button per screen, and it is the one that matters most.
 */
@Composable
private fun CourseCard(
    doseAmount: String,
    schedule: String,
    next: String?,
    range: String,
    notes: String?,
    readOnly: Boolean,
    open: Boolean,
    onRecord: () -> Unit,
    onEdit: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        if (doseAmount.isNotEmpty()) {
            Text(text = doseAmount, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.hair))
        }
        // The three schedule lines are one thought in three parts — what the times are, which one is
        // next, over what span — so they stack with no gap and the line height carries the rhythm.
        Text(text = schedule, style = MaterialTheme.typography.bodyMedium)
        if (next != null) {
            Text(
                text = next,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The bare dates live here and nowhere else: the list answers "what is next", this screen
        // answers "over what span".
        Text(
            text = range,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (notes != null) {
            Spacer(Modifier.height(Spacing.tight))
            Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        }

        if (readOnly) return@GroupedCard

        Spacer(Modifier.height(Spacing.base))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Button(onClick = onRecord) { Text(stringResource(R.string.med_record_title)) }
            OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }

        Spacer(Modifier.height(Spacing.base))
        // Not `RowDivider`: that one is inset to a row's text inside a card that has not padded its
        // contents. This card has, so a plain divider already stops at the text edge.
        HorizontalDivider()
        Spacer(Modifier.height(Spacing.base))

        // Offered only while there is something to close. Ending keeps every dose, which is what
        // separates it from deleting the course below it.
        if (open) {
            OutlinedButton(onClick = onEnd) { Text(stringResource(R.string.med_end_action)) }
            Spacer(Modifier.height(Spacing.tight))
            HelpText(stringResource(R.string.med_end_help))
            Spacer(Modifier.height(Spacing.tight))
        }

        // **Deleting the course lives here from Phase 7.** The list behind this screen draws 64dp
        // rows with a chevron and nowhere to put a button (`3a`), which is the finding `Weight` made
        // at `1d`. Pulled back to the card's text edge, because a text button carries its own
        // padding and one laid out flush looks indented against the paragraph above it.
        Row(modifier = Modifier.offset(x = -Spacing.snug)) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One recorded dose: what was done, when, and against which slot if any.
 *
 * **An ad-hoc dose says so** rather than showing a blank where a slot would be, because "no slot" is
 * a fact about the dose and not missing information (ADR-0002): a rescue dose at 03:00 is exactly as
 * real as the 08:00 one.
 *
 * A row of the history card rather than a card of its own, and it keeps *Edit* and *Delete* where
 * every other list in the sweep gave them up — because there is nowhere for them to move to. A dose
 * has no screen of its own; this row **is** the record.
 */
@Composable
private fun DoseHistoryRow(
    dose: DoseEntity,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val recordedOn = dose.recordedAt.atZone(zone)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
        horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
        // Top, not centre: a dose with a note is three lines tall and its buttons belong beside the
        // first of them.
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.med_history_row,
                        doseStatusLabel(dose.status),
                        dateLabel(recordedOn.toLocalDate()),
                        timeLabel(recordedOn.toLocalTime()),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (dose.scheduledTime == null) {
                        stringResource(R.string.med_history_ad_hoc)
                    } else {
                        stringResource(R.string.med_history_for_slot, timeLabel(dose.scheduledTime))
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dose.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!readOnly) {
            // The mirror of the quiet-action pull: these two are pushed *outward* by a text button's
            // own padding, so the last one's text edge lines up with the row's inset rather than
            // sitting a button's worth of air short of it.
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
