package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareType
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.ChangeableValueRow
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.FormChip
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.SingleLineField
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
 *
 * **Phase 7 (`10m`).** Four fields, unchanged, in the app's own words — including the long *"not
 * when you last did it"* helper, which is the sentence that stops the whole schedule being entered
 * backwards and is worth its four lines. What changed is grouping: **the kind, the name and the
 * interval are one card**, because they are one answer in three parts and the unit chips belong
 * beside the number they inflect rather than in a card of their own. The date is a value row in a
 * card with its helper under it, and the read-back line sits **outside** both, directly above the
 * fold, where it is the last thing read before saving.
 *
 * *Save* is in the app bar, not at the foot of the scroll. The drawing puts it at the foot "as
 * captured" — but `3e` made this the one editor chrome in the app, for the reason this screen also
 * has: on a form this long the button is below the fold.
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

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.care_editor_add_title else R.string.care_editor_edit_title,
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
        ) {
            WhatSection(state = state, viewModel = viewModel)

            Spacer(Modifier.height(Spacing.section))

            DueSection(firstDueOn = dateLabel(state.firstDueOn), onPick = { pickingDate = true })

            // The form read back in the same words the list will use, so what was typed and what
            // will be shown can be compared before saving. Outside every card on purpose: it is not
            // a field, it is what the fields add up to.
            state.previewInterval?.let { interval ->
                Spacer(Modifier.height(Spacing.base))
                Text(
                    text = careIntervalLabel(interval),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.hair),
                )
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
 * What it is, what to call it and how often — **one card with no header**.
 *
 * Three questions that are one answer, which is why they group. The card has no [FormSection] title
 * because every field inside it already asks its own question out loud; a heading over three
 * questions would have to invent a fourth.
 */
@Composable
private fun WhatSection(
    state: CareReminderEditorUiState,
    viewModel: CareReminderEditorViewModel,
) {
    // The unit agrees with the number beside it: "2 weeks", never "2 week".
    val parsedCount = remember(state.intervalCount) { state.intervalCount.trim().toIntOrNull() ?: 1 }

    GroupedCard(
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.care_editor_kind))
            ChipRow {
                // Kotlin note: `CareType.entries` is the enum's `Object.values()` in declaration
                // order, and `+ null` appends the free-text path as a fourth chip — one list rather
                // than a loop and a special case.
                (CareType.entries + null).forEach { type ->
                    FormChip(
                        selected = type == state.type,
                        onClick = { viewModel.setType(type) },
                        label =
                            if (type == null) {
                                stringResource(R.string.care_type_custom)
                            } else {
                                stringResource(careTypeLabelRes(type))
                            },
                    )
                }
            }
        }

        // Offered for every kind, required only for "something else": a preset the owner renamed
        // keeps its icon and its calendar rule, and a preset they did not rename keeps a name that
        // translates.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.care_editor_label))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                SingleLineField(
                    value = state.label,
                    onValueChange = viewModel::setLabel,
                    isError = state.labelInvalid,
                )
                if (state.labelInvalid) {
                    ErrorText(stringResource(R.string.care_editor_label_required))
                } else {
                    HelpText(stringResource(R.string.care_editor_label_help))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.care_editor_interval))
            SingleLineField(
                value = state.intervalCount,
                onValueChange = viewModel::setIntervalCount,
                isError = state.intervalInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ChipRow {
                CareIntervalUnit.entries.forEach { entry ->
                    FormChip(
                        selected = entry == state.intervalUnit,
                        onClick = { viewModel.setIntervalUnit(entry) },
                        label = careIntervalUnitLabel(entry, parsedCount),
                    )
                }
            }
            if (state.intervalInvalid) {
                ErrorText(stringResource(R.string.care_editor_interval_required))
            }
        }
    }
}

/**
 * When it is next due, and the four lines that stop it being entered backwards.
 *
 * The helper lives **inside** the card with the row it qualifies, not under the card, because it is
 * about this one field and nothing else on the screen (`Forms.kt`: help belongs to the control above
 * it). It is also the reason the label stacks — see [ChangeableValueRow]'s `stacked`.
 */
@Composable
private fun DueSection(
    firstDueOn: String,
    onPick: () -> Unit,
) {
    GroupedCard(contentPadding = PaddingValues(top = Spacing.hair, bottom = Spacing.base)) {
        ChangeableValueRow(
            label = stringResource(R.string.care_editor_first_due),
            value = firstDueOn,
            // The button says only *Change*, as every other value row in the app does: the label
            // stacked above it already says what changes, and "Change the date" stays as what a
            // screen reader hears, where there is no label in view to disambiguate it.
            description = stringResource(R.string.recorded_at_pick_date),
            stacked = true,
            onChange = onPick,
        )
        HelpText(
            text = stringResource(R.string.care_editor_first_due_help),
            modifier = Modifier.padding(horizontal = Spacing.base),
        )
    }
}
