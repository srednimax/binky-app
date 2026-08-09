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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.DoseEntity
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
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
            modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (course.doseAmount.isNotEmpty()) {
                        Text(text = course.doseAmount, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = courseScheduleLabel(state.times),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    nextDoseLabel(state.next)?.let { next ->
                        Text(
                            text = next,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The bare dates live here and nowhere else: the list answers "what is next",
                    // this screen answers "over what span".
                    Text(
                        text = courseRangeLabel(course.startOn, course.endOn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    course.notes?.let { notes ->
                        Text(text = notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!readOnly) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::startRecording) {
                            Text(stringResource(R.string.med_record_title))
                        }
                        OutlinedButton(onClick = { onEdit(course.bunnyId, course.id) }) {
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                }

                // Offered only while there is something to close. Ending keeps every dose, which is
                // what separates it from deleting the course below it.
                if (state.open) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = viewModel::endCourse) {
                                Text(stringResource(R.string.med_end_action))
                            }
                            Text(
                                text = stringResource(R.string.med_end_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // **Deleting the course lives here from Phase 7.** The list behind this screen draws
                // 64dp rows with a chevron and nowhere to put a button (`3a`), which is the finding
                // `Weight` made at `1d`. Quieter than everything above it: it is the one action on
                // this screen that destroys a health record, and it is not what the owner came for.
                item {
                    TextButton(onClick = viewModel::requestDelete) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.med_history_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (state.doses.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.med_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.doses, key = { it.id }) { dose ->
                DoseHistoryRow(
                    dose = dose,
                    readOnly = readOnly,
                    onEdit = { viewModel.startDoseEdit(dose) },
                    onDelete = { viewModel.requestDoseDelete(dose) },
                )
            }

            item { MedicationDisclaimer() }
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

    state.pendingDoseDelete?.let { dose ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDoseDelete,
            title = { Text(stringResource(R.string.med_dose_delete_title)) },
            text = { Text(stringResource(R.string.med_dose_delete_body)) },
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
        )
    }
}

/**
 * One recorded dose: what was done, when, and against which slot if any.
 *
 * **An ad-hoc dose says so** rather than showing a blank where a slot would be, because "no slot" is
 * a fact about the dose and not missing information (ADR-0002): a rescue dose at 03:00 is exactly as
 * real as the 08:00 one.
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            if (!readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
