package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareType
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import java.time.Instant
import java.time.ZoneOffset

/**
 * Add or edit a care reminder.
 *
 * **Three presets and "something else"** (ADR-0018). The presets carry a type, an icon, a translated
 * name and a default interval; "something else" is the free-text path, and a reminder that takes it
 * is not a lesser reminder — a hay reorder and a nail trim are the same shape to everything
 * downstream.
 *
 * The date field asks **when it is next due**, not when it was last done. That is the question an
 * owner can answer off a vet card; "when did you last vaccinate?" is a subtraction they often cannot
 * do, and a field that quietly moved the schedule would be the second fact ADR-0002 warns about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareReminderEditorScreen(
    bunnyId: String,
    reminderId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CareReminderEditorViewModel =
        viewModel(
            factory = CareReminderEditorViewModel.factory(bunnyId, reminderId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }

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
                            if (state.isNew) R.string.care_editor_add_title else R.string.care_editor_edit_title,
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
            KindPicker(selected = state.type, onSelect = viewModel::setType)

            // Offered for every kind, required only for "something else": a preset the owner renamed
            // keeps its icon and its calendar rule, and a preset they did not rename keeps a name
            // that translates.
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::setLabel,
                label = { Text(stringResource(R.string.care_editor_label)) },
                isError = state.labelInvalid,
                supportingText = {
                    Text(
                        stringResource(
                            if (state.labelInvalid) {
                                R.string.care_editor_label_required
                            } else {
                                R.string.care_editor_label_help
                            },
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            IntervalPicker(
                count = state.intervalCount,
                unit = state.intervalUnit,
                invalid = state.intervalInvalid,
                onCountChange = viewModel::setIntervalCount,
                onUnitChange = viewModel::setIntervalUnit,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.care_editor_first_due),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = dateLabel(state.firstDueOn),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickingDate = true }) {
                        Text(stringResource(R.string.recorded_at_pick_date))
                    }
                }
                Text(
                    text = stringResource(R.string.care_editor_first_due_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The form read back in the same words the list will use, so what was typed and what
            // will be shown can be compared before saving.
            state.previewInterval?.let { interval ->
                Text(text = careIntervalLabel(interval), style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingDate) {
        // A due date may be in the past — an overdue vaccination is exactly the reminder worth
        // creating — so, unlike every other date picker in this app, this one refuses nothing.
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.firstDueOn
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
                            viewModel.setFirstDueOn(
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
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
}

/**
 * The three known kinds, and the rest of the world.
 *
 * Kotlin note: `CareType.entries` is the enum's `Object.values()` in declaration order, and `+ null`
 * appends the free-text path as a fourth chip — one list rather than a loop and a special case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindPicker(
    selected: CareType?,
    onSelect: (CareType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.care_editor_kind), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (CareType.entries + null).forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = {
                        Text(
                            if (type == null) {
                                stringResource(R.string.care_type_custom)
                            } else {
                                stringResource(careTypeLabelRes(type))
                            },
                        )
                    },
                )
            }
        }
    }
}

/** How often it comes round: a number and one of the four calendar units. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalPicker(
    count: String,
    unit: CareIntervalUnit,
    invalid: Boolean,
    onCountChange: (String) -> Unit,
    onUnitChange: (CareIntervalUnit) -> Unit,
) {
    val parsed = remember(count) { count.trim().toIntOrNull() ?: 1 }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.care_editor_interval), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = count,
            onValueChange = onCountChange,
            isError = invalid,
            supportingText =
                if (invalid) {
                    { Text(stringResource(R.string.care_editor_interval_required)) }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareIntervalUnit.entries.forEach { entry ->
                FilterChip(
                    selected = entry == unit,
                    onClick = { onUnitChange(entry) },
                    // The unit agrees with the number beside it: "2 weeks", never "2 week".
                    label = { Text(careIntervalUnitLabel(entry, parsed)) },
                )
            }
        }
    }
}
