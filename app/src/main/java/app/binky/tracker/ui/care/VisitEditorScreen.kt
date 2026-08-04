package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.PickerOption
import app.binky.tracker.ui.common.SearchablePickerDialog
import app.binky.tracker.ui.weight.weightLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Add or edit one vet visit — a detail screen off the Care tab.
 *
 * Three things here are decisions rather than layout (ADR-0017):
 *
 * - **The vet is optional and picked through the same searchable list as symptoms** (2d's
 *   `SearchablePickerDialog`, reused rather than rebuilt), with *add a new vet* inline — because the
 *   moment an owner needs a vet record is the moment they are typing a visit.
 * - **The weight is optional and entered in grams**, using the same rule as the weight form (house
 *   rule): what a scale reads out. It is one row in `weights` tagged with this visit, never a second
 *   copy of the number, and clearing the field deletes that row.
 * - **[readOnly] is the archived scope** (ADR-0004): the fields render with what was recorded and
 *   nothing can be typed or saved, because a memorial's history is for reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditorScreen(
    bunnyId: String,
    visitId: String?,
    readOnly: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VisitEditorViewModel =
        viewModel(
            key = "visit-editor-${visitId ?: "new"}",
            factory = VisitEditorViewModel.factory(bunnyId, visitId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingVet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.visit_editor_add_title else R.string.visit_editor_edit_title,
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
            // No Save at all in the archived scope, rather than one that refuses when tapped.
            actions = {
                if (!readOnly) {
                    TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                }
            },
            // The shell's Scaffold is the one owner of window insets; padding here would double it.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VisitedOnField(
                date = state.visitedOn,
                inFuture = state.inFuture,
                enabled = !readOnly,
                onPick = { pickingDate = true },
            )
            OutlinedTextField(
                value = state.reason,
                onValueChange = viewModel::onReasonChanged,
                label = { Text(stringResource(R.string.visit_reason_label)) },
                isError = state.reasonInvalid,
                supportingText = {
                    if (state.reasonInvalid) Text(stringResource(R.string.visit_reason_required))
                },
                enabled = !readOnly,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VetField(
                vetName = state.vetName,
                enabled = !readOnly,
                onPick = { pickingVet = true },
                onClear = { viewModel.onVetChanged(null) },
            )
            GramsField(
                grams = state.grams,
                invalid = state.gramsInvalid,
                parsedGrams = state.parsedGrams,
                unit = state.unit,
                enabled = !readOnly,
                onGramsChanged = viewModel::onGramsChanged,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.visit_notes_label)) },
                enabled = !readOnly,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickingDate) {
        VisitDatePicker(
            date = state.visitedOn,
            onPicked = {
                viewModel.onVisitedOnChanged(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }

    if (pickingVet) {
        SearchablePickerDialog(
            title = stringResource(R.string.visit_vet_picker_title),
            options = state.vets.map { PickerOption(id = it.id, label = it.name) },
            selectedIds = setOfNotNull(state.vetId),
            multiSelect = false,
            addLabelRes = R.string.visit_vet_add_typed,
            onToggle = { viewModel.onVetChanged(it.id) },
            onAddTyped = viewModel::addVet,
            onDismiss = { pickingVet = false },
        )
    }
}

/**
 * **When it happened** — today by default, back-dating allowed, the future refused with the reason
 * stated rather than silently clamped.
 *
 * A day rather than a moment (ADR-0017): a visit happens *on* a date, and there is no time field
 * here at all. The weighing taken at it needs an instant, and that is derived — `min(noon, now)` —
 * rather than being a second thing to type.
 */
@Composable
private fun VisitedOnField(
    date: LocalDate,
    inFuture: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.visit_date_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = dateLabel(date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (enabled) {
                TextButton(onClick = onPick) { Text(stringResource(R.string.recorded_at_pick_date)) }
            }
        }
        if (inFuture) {
            Text(
                text = stringResource(R.string.visit_future_rejected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The optional vet, and the one control that also *creates* one (ADR-0017). */
@Composable
private fun VetField(
    vetName: String?,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.visit_vet_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = vetName ?: stringResource(R.string.visit_vet_none),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (vetName == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                TextButton(onClick = onPick) { Text(stringResource(R.string.visit_vet_choose)) }
                // Only when there is something to clear — a permanently visible "clear" beside an
                // empty field is a control that does nothing four times out of five.
                if (vetName != null) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.visit_vet_clear)) }
                }
            }
        }
    }
}

/**
 * The weighing the vet took, **in grams and optional**.
 *
 * Empty is the ordinary case and never an error: most visits are consultations. When the owner reads
 * kilograms the conversion is echoed underneath rather than the field switching unit, exactly as the
 * weight form does — what they type and what they see are never the same box.
 */
@Composable
private fun GramsField(
    grams: String,
    invalid: Boolean,
    parsedGrams: Int?,
    unit: WeightUnit,
    enabled: Boolean,
    onGramsChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = grams,
            onValueChange = onGramsChanged,
            label = { Text(stringResource(R.string.visit_weight_label)) },
            isError = invalid,
            supportingText = {
                if (invalid) {
                    Text(stringResource(R.string.visit_weight_invalid))
                } else {
                    Text(stringResource(R.string.visit_weight_help))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (parsedGrams != null && unit == WeightUnit.KILOGRAMS) {
            Text(
                text =
                    stringResource(
                        R.string.weight_grams_as_kilograms,
                        weightLabel(parsedGrams, WeightUnit.KILOGRAMS),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * UTC midnight throughout, which is what the Material date picker works in — the same convention
 * `RecordedAtField`, the completion dialog and the calendar hand-off use for a bare date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitDatePicker(
    date: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val todayUtc = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // A visit that has not happened yet is not a record. The form re-checks on save, because
            // a picker cannot be the only thing enforcing a rule a restored state could skip.
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc

                    override fun isSelectableYear(year: Int) = year <= today.year
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = pickerState.selectedDateMillis
                    // Nothing selected closes rather than sticking: the picker opens with today
                    // already chosen, so a null here means the owner cleared it deliberately.
                    if (picked == null) {
                        onDismiss()
                    } else {
                        onPicked(Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}
