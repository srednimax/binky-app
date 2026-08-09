package app.binky.tracker.ui.observations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.ActivityLevel
import app.binky.tracker.data.Appetite
import app.binky.tracker.data.Cecotropes
import app.binky.tracker.data.DroppingsAmount
import app.binky.tracker.data.DroppingsForm
import app.binky.tracker.data.DroppingsSize
import app.binky.tracker.data.Mood
import app.binky.tracker.data.ParticipantExclusion
import app.binky.tracker.data.WaterIntake
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.FormChip
import app.binky.tracker.ui.common.FormSection
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.NoteField
import app.binky.tracker.ui.common.PickerOption
import app.binky.tracker.ui.common.RecordedAtField
import app.binky.tracker.ui.common.SearchablePickerDialog

/**
 * Add or edit one observation — the screen behind the global "+" (ADR-0015), and the durable review
 * path behind the healthy day's snackbar (ADR-0008).
 *
 * A **detail** screen like the weight and bunny editors: pushed onto the back stack with its own app
 * bar, so the shell's switcher and bottom bar step aside while it is open.
 *
 * **Every field is optional.** The graded ones start at "not checked" and stay there unless the owner
 * says otherwise, because a "fine" nobody verified is a false reassurance (ADR-0001). That is why
 * there is no Reset and no defaulting to "normal" anywhere on this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationEntryScreen(
    bunnyId: String,
    observationId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ObservationEntryViewModel =
        viewModel(
            key = "observation-entry-${observationId ?: "new"}",
            factory = ObservationEntryViewModel.factory(bunnyId, observationId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.observation_add_title else R.string.observation_edit_title,
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
            // A section is a card now, and the gap between cards is what separates them — the
            // hairline rules this screen used to draw across the background are gone with them.
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            if (state.offersParticipants) {
                ParticipantsField(
                    state = state,
                    onToggle = viewModel::toggleParticipant,
                )
            }

            RecordedAtField(
                label = stringResource(R.string.observation_recorded_at_label),
                helpText = stringResource(R.string.observation_backdating_help),
                futureRejectedText = stringResource(R.string.observation_future_rejected),
                date = state.date,
                time = state.time,
                inFuture = state.inFuture,
                onDateChanged = viewModel::onDateChanged,
                onTimeChanged = viewModel::onTimeChanged,
            )

            TraySection(state = state, viewModel = viewModel)
            IndividualSection(state = state, viewModel = viewModel)
        }
    }
}

/**
 * Who this observation covers (ADR-0008).
 *
 * Pre-ticked from the current fluffle's active members and **editable**, because pre-selection is a
 * good guess and never a claim. Anybody the pre-selection deliberately left out is named underneath
 * with the reason — the shape Phase 4's Watch exclusion drops straight into.
 */
@Composable
private fun ParticipantsField(
    state: ObservationEntryUiState,
    onToggle: (String) -> Unit,
) {
    // Everything under the chips is a footnote on them, so the card is spaced tight throughout
    // rather than at the between-questions step the other two use.
    FormSection(
        title = stringResource(R.string.observation_participants_label),
        spacing = Spacing.tight,
    ) {
        ChipRow {
            state.candidates.forEach { candidate ->
                val selected = candidate.bunnyId in state.selectedParticipants
                FormChip(
                    selected = selected,
                    onClick = { onToggle(candidate.bunnyId) },
                    label = candidate.name,
                    // A name is not self-evidently a state the way "Few" or "Normal" is, so the
                    // tick says which way this one is set without the owner comparing two fills.
                    leadingIcon =
                        if (!selected) {
                            null
                        } else {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    // Decorative: the chip's own selected state is what a screen
                                    // reader announces.
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        },
                )
            }
        }
        HelpText(stringResource(R.string.observation_participants_help))
        state.excluded.forEach { excluded ->
            HelpText(
                stringResource(
                    when (excluded.reason) {
                        ParticipantExclusion.ARCHIVED -> R.string.observation_excluded_archived
                        ParticipantExclusion.UNDER_WATCH -> R.string.observation_excluded_watch
                    },
                    excluded.name,
                ),
            )
        }
        if (state.noParticipants) {
            ErrorText(stringResource(R.string.observation_participants_required))
        }
    }
}

/**
 * The tray-level facts — the reason a shared observation is shared at all. One tray, one real-world
 * fact, so these land identically on every participant and editing one moves them all (ADR-0008).
 */
@Composable
private fun TraySection(
    state: ObservationEntryUiState,
    viewModel: ObservationEntryViewModel,
) {
    FormSection(title = stringResource(R.string.observation_tray_heading)) {
        if (state.selectedParticipants.size > 1) {
            HelpText(stringResource(R.string.observation_tray_shared_help))
        }
        NullableChoiceField(
            label = stringResource(R.string.observation_droppings_amount_label),
            options = DroppingsAmount.entries,
            selected = state.tray.droppingsAmount,
            optionLabel = { label(it) },
            onSelect = viewModel::onDroppingsAmountChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_droppings_size_label),
            options = DroppingsSize.entries,
            selected = state.tray.droppingsSize,
            optionLabel = { label(it) },
            onSelect = viewModel::onDroppingsSizeChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_droppings_form_label),
            options = DroppingsForm.entries,
            selected = state.tray.droppingsForm,
            optionLabel = { label(it) },
            onSelect = viewModel::onDroppingsFormChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_cecotropes_label),
            options = Cecotropes.entries,
            selected = state.tray.cecotropes,
            optionLabel = { label(it) },
            onSelect = viewModel::onCecotropesChanged,
        )
    }
}

/**
 * The facts that belong to one bunny — one hunched and lethargic while the other is bouncing around.
 * Editing these never touches another participant's row, however shared the observation is.
 */
@Composable
private fun IndividualSection(
    state: ObservationEntryUiState,
    viewModel: ObservationEntryViewModel,
) {
    FormSection(
        // Named when it could be ambiguous, so an owner editing a shared observation can see whose
        // mood they are about to change.
        title =
            if (state.selectedParticipants.size > 1) {
                stringResource(R.string.observation_individual_heading_named, state.subjectName)
            } else {
                stringResource(R.string.observation_individual_heading)
            },
    ) {
        NullableChoiceField(
            label = stringResource(R.string.observation_appetite_label),
            options = Appetite.entries,
            selected = state.individual.appetite,
            optionLabel = { label(it) },
            onSelect = viewModel::onAppetiteChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_mood_label),
            options = Mood.entries,
            selected = state.individual.mood,
            optionLabel = { label(it) },
            onSelect = viewModel::onMoodChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_activity_label),
            options = ActivityLevel.entries,
            selected = state.individual.activity,
            optionLabel = { label(it) },
            onSelect = viewModel::onActivityChanged,
        )
        NullableChoiceField(
            label = stringResource(R.string.observation_water_label),
            options = WaterIntake.entries,
            selected = state.individual.water,
            optionLabel = { label(it) },
            onSelect = viewModel::onWaterChanged,
        )

        SymptomsField(state = state, viewModel = viewModel)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.observation_note_label))
            NoteField(
                value = state.individual.note.orEmpty(),
                onValueChange = viewModel::onNoteChanged,
                placeholder = stringResource(R.string.observation_note_placeholder),
            )
        }
    }
}

/**
 * Symptoms, with the **"none seen"** tick that makes *looked, nothing wrong* a recordable fact
 * rather than an absence indistinguishable from never having opened the picker (ADR-0010).
 */
@Composable
private fun SymptomsField(
    state: ObservationEntryUiState,
    viewModel: ObservationEntryViewModel,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val builtInLabels = builtInSymptomLabels()
    val options =
        state.pickableSymptoms
            .map { PickerOption(id = it.id, label = symptomLabel(it)) }
            .sortedBy { it.label.lowercase() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        FieldLabel(stringResource(R.string.observation_symptoms_label))

        val ticked = options.filter { it.id in state.individual.symptomIds }
        if (ticked.isEmpty()) {
            Text(
                text = stringResource(R.string.observation_symptoms_none_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChipRow {
                ticked.forEach { option ->
                    FormChip(
                        selected = true,
                        onClick = { viewModel.toggleSymptom(option.id) },
                        label = option.label,
                    )
                }
            }
        }
        // A text button carries 12dp of horizontal padding of its own, which would push this one
        // label out of the column every other row in the card lines up on. Dropping it keeps the
        // alignment; the 48dp touch target is the button's minimum size and survives.
        TextButton(
            onClick = { picking = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.tight),
        ) {
            Text(stringResource(R.string.observation_symptoms_pick))
        }

        // Ticking a symptom already means the owner looked, so the claim cannot be withdrawn while
        // any is ticked.
        val canToggleChecked = state.individual.symptomIds.isEmpty()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // The whole row is the target, not just the box. `toggleable` on the Row with
            // `onCheckedChange = null` on the Checkbox is the Compose idiom for that: one clickable
            // node carrying the semantics, rather than two that a screen reader announces twice.
            // Worth the ceremony here of all places — this is the one field that records "I looked
            // and saw nothing", and a label that silently does nothing is how that goes unrecorded
            // (ADR-0010).
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.individual.symptomsChecked,
                        enabled = canToggleChecked,
                        role = Role.Checkbox,
                        onValueChange = viewModel::onSymptomsCheckedChanged,
                    ).padding(vertical = Spacing.hair),
        ) {
            Checkbox(
                checked = state.individual.symptomsChecked,
                enabled = canToggleChecked,
                onCheckedChange = null,
            )
            Text(
                text = stringResource(R.string.observation_symptoms_checked),
                modifier = Modifier.padding(start = Spacing.tight),
            )
        }
        HelpText(stringResource(R.string.observation_symptoms_checked_help))
    }

    if (picking) {
        SearchablePickerDialog(
            title = stringResource(R.string.observation_symptoms_pick),
            options = options,
            selectedIds = state.individual.symptomIds,
            multiSelect = true,
            addLabelRes = R.string.picker_add_symptom,
            onToggle = { viewModel.toggleSymptom(it.id) },
            onAddTyped = { viewModel.addSymptom(it, builtInLabels) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * A closed vocabulary where **not answering is itself an answer**.
 *
 * The leading chip is "Not checked" and it is selected by default. It is a real, tappable option
 * rather than the absence of a selection, because that is what the column stores: `null` *is* "not
 * checked" (ADR-0001), and a row of chips with nothing lit reads as a field the owner forgot rather
 * than one they deliberately left alone.
 *
 * The options **wrap** rather than scrolling sideways. The before set cut "More than usual" and
 * "Strung together" off the right edge of the screen, which made the two answers that most deserve
 * recording the two hardest to find.
 */
@Composable
private fun <T> NullableChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        FieldLabel(label)
        ChipRow {
            FormChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = notCheckedLabel(),
            )
            options.forEach { option ->
                FormChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = optionLabel(option),
                )
            }
        }
    }
}
