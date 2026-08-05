package app.binky.tracker.ui.care

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.weight.timeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Add or edit a medication course.
 *
 * **The amount is free text and is not required** (ADR-0002). "0.3 ml", "half a tablet", "one
 * syringe morning and night" — the app never parses, sums or converts it, and an owner who was told
 * the last of those has nothing to type in a number field. Insisting would make them invent a figure
 * the app would then display as if the vet had said it.
 *
 * **The schedule is optional too.** A course with no times is one the owner records doses against by
 * hand, which is a real way to be prescribed something, and it is the only state in which the
 * reminder switch is hidden rather than shown off (ADR-0003).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCourseEditorScreen(
    bunnyId: String,
    courseId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MedicationCourseEditorViewModel =
        viewModel(
            factory = MedicationCourseEditorViewModel.factory(bunnyId, courseId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    // The write has landed; the screen's only job now is to leave. One mechanism, so the form and
    // the stored row cannot disagree about whether it saved.
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.med_editor_add_title else R.string.med_editor_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                // The shell's Scaffold already owns the insets.
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.med_editor_name)) },
                isError = state.nameInvalid,
                supportingText =
                    if (state.nameInvalid) {
                        { Text(stringResource(R.string.med_editor_name_required)) }
                    } else {
                        null
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.doseAmount,
                onValueChange = viewModel::setDoseAmount,
                label = { Text(stringResource(R.string.med_editor_amount)) },
                supportingText = { Text(stringResource(R.string.med_editor_amount_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = stringResource(R.string.med_editor_start),
                date = state.startOn,
                onPick = { pickingStart = true },
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.med_editor_ongoing),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.ongoing, onCheckedChange = viewModel::setOngoing)
                }
                Text(
                    text = stringResource(R.string.med_editor_ongoing_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.ongoing) {
                    DateField(
                        label = stringResource(R.string.med_editor_end),
                        date = state.endOn,
                        onPick = { pickingEnd = true },
                    )
                    if (state.endBeforeStart) {
                        Text(
                            text = stringResource(R.string.med_editor_end_before_start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SchedulePicker(
                times = state.clockTimes,
                onAdd = { pickingTime = true },
                onRemove = viewModel::removeTime,
            )

            // **Absent without times, not present and inert** (ADR-0003): a switch that promises a
            // reminder about a schedule that does not exist is worse than no switch at all.
            if (state.hasSchedule) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.med_editor_reminders),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.remindersEnabled,
                            onCheckedChange = viewModel::setRemindersEnabled,
                        )
                    }
                    Text(
                        text = stringResource(R.string.med_editor_reminders_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.med_editor_notes)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingStart) {
        // A course may start tomorrow — that is how most are prescribed — so, unlike every other
        // date picker in this app bar the care reminder's, this one refuses nothing.
        CourseDatePicker(
            initial = state.startOn,
            onPicked = viewModel::setStartOn,
            onDismiss = { pickingStart = false },
        )
    }

    if (pickingEnd) {
        CourseDatePicker(
            initial = state.endOn,
            onPicked = viewModel::setEndOn,
            onDismiss = { pickingEnd = false },
        )
    }

    if (pickingTime) {
        val context = LocalContext.current
        val pickerState =
            rememberTimePickerState(
                initialHour = LocalTime.now().hour,
                initialMinute = 0,
                is24Hour = DateFormat.is24HourFormat(context),
            )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTime(LocalTime.of(pickerState.hour, pickerState.minute))
                        pickingTime = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** A labelled read-only date with the button that changes it — the shape the care editor uses. */
@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = dateLabel(date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onPick) { Text(stringResource(R.string.recorded_at_pick_date)) }
        }
    }
}

/**
 * The daily schedule as chips the owner adds and takes away.
 *
 * Each chip is one `medication_times` row (ADR-0002's child table), which is why removing one is
 * removing a time rather than editing a string: the unique index on `(courseId, time)` is what makes
 * "08:00 twice" impossible, and a comma-separated field would hand that job back to this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulePicker(
    times: List<LocalTime>,
    onAdd: () -> Unit,
    onRemove: (LocalTime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.med_editor_schedule), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.med_editor_schedule_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (times.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { time ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(time) },
                        label = { Text(timeLabel(time)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.med_editor_remove_time, timeLabel(time)),
                            )
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.med_editor_add_time)) }
    }
}

/** UTC midnight throughout, which is the convention every bare date in this app picks with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDatePicker(
    initial: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}
