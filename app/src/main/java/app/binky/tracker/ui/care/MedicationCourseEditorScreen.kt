package app.binky.tracker.ui.care

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.ChangeableValueRow
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.FormSection
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.NoteField
import app.binky.tracker.ui.common.RemovableChip
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SingleLineField
import app.binky.tracker.ui.common.SwitchRow
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
 *
 * `3e` keeps all six fields, in the same order, with the app's own words — what changed is that
 * each group became a card and *Save* moved into the app bar. The drawing keeps the filled button at
 * the foot of the scroll and then argues against itself in its own notes: on a form this long the
 * button is below the fold, where the observation editor's bar button never is. One editor chrome,
 * and this is the one.
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

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.med_editor_add_title else R.string.med_editor_edit_title,
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

        if (state.loading) return@Column

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            MedicationSection(state = state, viewModel = viewModel)

            WhenSection(
                state = state,
                viewModel = viewModel,
                onPickStart = { pickingStart = true },
                onPickEnd = { pickingEnd = true },
            )

            ScheduleSection(state = state, viewModel = viewModel, onAddTime = { pickingTime = true })

            // The one card with no header: the placeholder inside it already says what it is, and a
            // heading over a single box would only repeat it.
            GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
                NoteField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    placeholder = stringResource(R.string.med_editor_notes),
                )
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

/**
 * What was prescribed — the name on the box and, if the owner was told one, the amount.
 *
 * The drawing puts each question *under* its own box as help text, leaving only the example inside
 * it. Built the other way round, with [FieldLabel] above: a question below its own answer reads as a
 * footnote about what you just typed, and Forms.kt's "help belongs to the control above it" was
 * written for footnotes. The amount keeps its real footnote underneath, which is what that rule is
 * for, and both examples stay in the boxes as drawn.
 */
@Composable
private fun MedicationSection(
    state: MedicationCourseEditorUiState,
    viewModel: MedicationCourseEditorViewModel,
) {
    FormSection(title = stringResource(R.string.med_editor_section_medication)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.med_editor_name))
            SingleLineField(
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = stringResource(R.string.med_editor_name_placeholder),
                isError = state.nameInvalid,
            )
            if (state.nameInvalid) ErrorText(stringResource(R.string.med_editor_name_required))
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.med_editor_amount))
            SingleLineField(
                value = state.doseAmount,
                onValueChange = viewModel::setDoseAmount,
                placeholder = stringResource(R.string.med_editor_amount_placeholder),
            )
            HelpText(stringResource(R.string.med_editor_amount_help))
        }
    }
}

/**
 * How long the course runs: when it starts, and whether it has a last day.
 *
 * One card of rows rather than three loose controls, which is what makes *Ongoing* legible as a
 * property of the dates above it instead of a setting that happens to sit nearby. The end date
 * appears **inside** the same card when the switch goes off — the drawing never shows that state,
 * and putting it anywhere else would leave the answer to "then when?" outside the question.
 */
@Composable
private fun WhenSection(
    state: MedicationCourseEditorUiState,
    viewModel: MedicationCourseEditorViewModel,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    FormSection(
        title = stringResource(R.string.med_editor_section_when),
        // The rows carry their own insets, so the dividers between them can reach the card's edge.
        contentPadding = PaddingValues(vertical = Spacing.hair),
        spacing = 0.dp,
    ) {
        ChangeableValueRow(
            label = stringResource(R.string.med_editor_start),
            value = dateLabel(state.startOn),
            description = stringResource(R.string.med_editor_pick_start),
            onChange = onPickStart,
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.med_editor_ongoing),
            helpText = stringResource(R.string.med_editor_ongoing_help),
            checked = state.ongoing,
            onCheckedChange = viewModel::setOngoing,
        )
        if (!state.ongoing) {
            RowDivider()
            ChangeableValueRow(
                label = stringResource(R.string.med_editor_end),
                value = dateLabel(state.endOn),
                description = stringResource(R.string.med_editor_pick_end),
                onChange = onPickEnd,
            )
            if (state.endBeforeStart) {
                ErrorText(
                    text = stringResource(R.string.med_editor_end_before_start),
                    modifier =
                        Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.snug),
                )
            }
        }
    }
}

/**
 * The daily schedule as chips the owner adds and takes away, and the reminder switch that belongs
 * to it.
 *
 * Each chip is one `medication_times` row (ADR-0002's child table), which is why removing one is
 * removing a time rather than editing a string: the unique index on `(courseId, time)` is what makes
 * "08:00 twice" impossible, and a comma-separated field would hand that job back to this screen.
 *
 * *Add a time* sits **in** the chip row rather than under it, which is `3e`'s arrangement and reads
 * as one more thing in the same list instead of a separate control acting on it.
 *
 * The drawing omits the reminder switch entirely — it draws a course that already has two times, so
 * the switch should be showing. It lands here, below a divider, because "remind me at these times"
 * is a sentence about the times in this card and nowhere else. **Absent without times, not present
 * and inert** (ADR-0003): a switch that promises a reminder about a schedule that does not exist is
 * worse than no switch at all.
 */
@Composable
private fun ScheduleSection(
    state: MedicationCourseEditorUiState,
    viewModel: MedicationCourseEditorViewModel,
    onAddTime: () -> Unit,
) {
    FormSection(
        title = stringResource(R.string.med_editor_schedule),
        contentPadding = PaddingValues(vertical = Spacing.hair),
        spacing = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.snug),
            verticalArrangement = Arrangement.spacedBy(Spacing.snug),
        ) {
            HelpText(stringResource(R.string.med_editor_schedule_help))
            ChipRow {
                state.clockTimes.forEach { time ->
                    RemovableChip(
                        label = timeLabel(time),
                        removeDescription = stringResource(R.string.med_editor_remove_time, timeLabel(time)),
                        onRemove = { viewModel.removeTime(time) },
                    )
                }
                TextButton(onClick = onAddTime, modifier = Modifier.height(AddTimeHeight)) {
                    Text(stringResource(R.string.med_editor_add_time))
                }
            }
        }

        if (state.hasSchedule) {
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.med_editor_reminders),
                helpText = stringResource(R.string.med_editor_reminders_help),
                checked = state.remindersEnabled,
                onCheckedChange = viewModel::setRemindersEnabled,
            )
        }
    }
}

/** Chip height, so *Add a time* sits on the same line as the chips it adds to rather than above it. */
private val AddTimeHeight = 36.dp

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
