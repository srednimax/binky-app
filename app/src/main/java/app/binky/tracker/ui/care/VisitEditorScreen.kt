package app.binky.tracker.ui.care

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.ChangeableValueRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.ListRowHeight
import app.binky.tracker.ui.common.NoteField
import app.binky.tracker.ui.common.PickerOption
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SearchablePickerDialog
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.common.SingleLineField
import app.binky.tracker.ui.documents.DocumentRow
import app.binky.tracker.ui.documents.ScanNoticeHost
import app.binky.tracker.ui.documents.rememberDocumentScan
import app.binky.tracker.ui.documents.rememberDocumentScanner
import app.binky.tracker.ui.weight.gramsLabel
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
 *
 * **Phase 7 (`10n`).** Six fields and one list, all with the app's own labels and helpers, grouped
 * into three cards by what kind of thing they are. The date and the vet are **value rows** — both
 * are a value plus the control that sets it, and both used to be a heading with a right-aligned
 * link. The three typed fields are a second card. The documents get a section header of their own,
 * because a visit's paperwork is a different subject from the visit's facts, and the two ways in sit
 * under a hairline inside that card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditorScreen(
    bunnyId: String,
    visitId: String?,
    readOnly: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenDocument: (String) -> Unit,
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
    var attachingDocument by rememberSaveable { mutableStateOf(false) }

    val defaultTitle = stringResource(R.string.document_default_title)
    val scan =
        rememberDocumentScan(scanner = rememberDocumentScanner()) { result ->
            viewModel.scanInto(title = defaultTitle, pages = result.pages, guided = result.guided)
        }

    ScanNoticeHost(
        notice = state.scanNotice,
        onShown = viewModel::scanNoticeShown,
        snackbarHostState = snackbarHostState,
    )

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

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
            WhenAndWhoCard(
                date = state.visitedOn,
                inFuture = state.inFuture,
                vetName = state.vetName,
                enabled = !readOnly,
                onPickDate = { pickingDate = true },
                onPickVet = { pickingVet = true },
                onClearVet = { viewModel.onVetChanged(null) },
            )

            Spacer(Modifier.height(Spacing.section))

            WhatHappenedCard(state = state, enabled = !readOnly, viewModel = viewModel)

            Spacer(Modifier.height(Spacing.section))

            DocumentsSection(
                documents = state.documents,
                // A document points at a `visitId`, so there is nothing for one to attach to until
                // the visit row exists. Stated rather than shown as controls that would fail.
                visitSaved = !state.isNew,
                enabled = !readOnly,
                scanning = state.scanning,
                onScan = scan,
                onAttach = {
                    viewModel.loadAttachable()
                    attachingDocument = true
                },
                onOpen = onOpenDocument,
                onDetach = viewModel::detachDocument,
            )

            // **Deleting the visit lives here from Phase 7.** The Care & Meds list draws 64dp rows
            // with a chevron and nowhere to put a button (`3a`), which is the finding `Weight` made
            // at `1d`. Only on a visit that exists: there is nothing to delete before the first
            // save, and a button that refuses when tapped is what ADR-0004 rules out.
            if (!readOnly && !state.isNew) {
                Spacer(Modifier.height(Spacing.base))
                Row(modifier = Modifier.padding(start = Spacing.hair).offset(x = -Spacing.snug)) {
                    TextButton(onClick = viewModel::requestDelete) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (state.confirmingDelete) {
        DeleteVisitDialog(
            visitedOn = state.visitedOn,
            weightGrams = state.storedGrams,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
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

    if (attachingDocument) {
        AttachDocumentDialog(
            choices = state.attachable,
            onPick = {
                attachingDocument = false
                viewModel.attachDocument(it)
            },
            onDismiss = { attachingDocument = false },
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
 * **When it happened, and who saw them** — the two facts that are a value plus the control that sets
 * it, so they are the one card of value rows.
 *
 * A visit is on a day rather than at a moment (ADR-0017): there is no time field here at all. The
 * weighing taken at it needs an instant, and that is derived — `min(noon, now)` — rather than being
 * a second thing to type. The future is refused with the reason stated rather than silently clamped,
 * and that line is [ErrorText] because it is why a save would not go through.
 *
 * Both labels stack above their values. *Vet* would sit beside its own comfortably, but *When was
 * it?* would not, and two rows in one card with the label in two different places reads as a
 * rendering fault rather than as a distinction.
 */
@Composable
private fun WhenAndWhoCard(
    date: LocalDate,
    inFuture: Boolean,
    vetName: String?,
    enabled: Boolean,
    onPickDate: () -> Unit,
    onPickVet: () -> Unit,
    onClearVet: () -> Unit,
) {
    GroupedCard {
        ChangeableValueRow(
            label = stringResource(R.string.visit_date_label),
            value = dateLabel(date),
            // The button says only *Change*: the label stacked above it already says what changes,
            // and the longer wording stays as what a screen reader hears.
            description = stringResource(R.string.recorded_at_pick_date),
            stacked = true,
            enabled = enabled,
            onChange = onPickDate,
        )
        if (inFuture) {
            ErrorText(
                text = stringResource(R.string.visit_future_rejected),
                modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.tight),
            )
        }
        RowDivider()
        ChangeableValueRow(
            label = stringResource(R.string.visit_vet_label),
            value = vetName ?: stringResource(R.string.visit_vet_none),
            description = stringResource(R.string.visit_vet_choose),
            actionLabel = stringResource(R.string.visit_vet_choose),
            stacked = true,
            enabled = enabled,
            onChange = onPickVet,
            // Only when there is something to clear — a permanently visible "clear" beside an empty
            // field is a control that does nothing four times out of five.
            onClear = if (vetName == null) null else onClearVet,
            clearDescription = stringResource(R.string.visit_vet_clear),
        )
    }
}

/**
 * **What it was for, what they weighed and what to remember** — the three typed fields, in one card
 * because all three are things the owner writes rather than picks.
 *
 * The kilogram echo sits directly **under** the grams field rather than replacing what is in it:
 * what an owner types and what they read back are never the same box, which is the rule the weight
 * form set. Empty is the ordinary case for the weight and never an error — most visits are
 * consultations.
 */
@Composable
private fun WhatHappenedCard(
    state: VisitEditorUiState,
    enabled: Boolean,
    viewModel: VisitEditorViewModel,
) {
    GroupedCard(
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.visit_reason_label))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                SingleLineField(
                    value = state.reason,
                    onValueChange = viewModel::onReasonChanged,
                    isError = state.reasonInvalid,
                    enabled = enabled,
                )
                if (state.reasonInvalid) ErrorText(stringResource(R.string.visit_reason_required))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.visit_weight_label))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                SingleLineField(
                    value = state.grams,
                    onValueChange = viewModel::onGramsChanged,
                    isError = state.gramsInvalid,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (state.gramsInvalid) {
                    ErrorText(stringResource(R.string.visit_weight_invalid))
                } else {
                    HelpText(stringResource(R.string.visit_weight_help))
                }
                // Kotlin note: pulled into a local because `parsedGrams` has a custom getter, so
                // the compiler cannot prove it returns the same value twice — the null check and
                // the use would be two separate calls.
                val parsed = state.parsedGrams
                if (parsed != null && state.unit == WeightUnit.KILOGRAMS) {
                    HelpText(
                        stringResource(
                            R.string.weight_grams_as_kilograms,
                            weightLabel(parsed, WeightUnit.KILOGRAMS),
                        ),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            FieldLabel(stringResource(R.string.visit_notes_label))
            NoteField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                enabled = enabled,
            )
        }
    }
}

/**
 * **The paperwork this visit produced** (ADR-0017).
 *
 * Two ways in, because paperwork arrives two ways: scanned there and then, or already on the phone
 * from a scan the owner did before opening the visit. Both write the same `visitId`, and both sit
 * under a hairline inside the card — they are how the list grows rather than entries in it.
 *
 * Detaching is offered per row and is deliberately not called *delete*: the document keeps its bunny
 * and stays in their document list, which is the survival rule a visit itself gets from its vet.
 */
@Composable
private fun DocumentsSection(
    documents: List<DocumentRow>,
    visitSaved: Boolean,
    enabled: Boolean,
    scanning: Boolean,
    onScan: () -> Unit,
    onAttach: () -> Unit,
    onOpen: (String) -> Unit,
    onDetach: (String) -> Unit,
) {
    SectionHeader(stringResource(R.string.visit_documents_label))
    Spacer(Modifier.height(Spacing.tight))

    GroupedCard(contentPadding = PaddingValues(top = Spacing.hair, bottom = Spacing.snug)) {
        if (!visitSaved) {
            HelpText(
                text = stringResource(R.string.visit_documents_save_first),
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
            )
            return@GroupedCard
        }

        if (documents.isEmpty()) {
            Text(
                text = stringResource(R.string.visit_documents_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .heightIn(min = ListRowHeight)
                        .padding(horizontal = Spacing.base, vertical = Spacing.snug),
            )
        } else {
            documents.forEachIndexed { index, document ->
                if (index > 0) RowDivider()
                ListRow(
                    title = document.title,
                    subtitle =
                        pluralStringResource(
                            R.plurals.document_page_count,
                            document.pageCount,
                            document.pageCount,
                        ),
                    onClick = { onOpen(document.id) },
                    trailing = {
                        if (enabled) {
                            TextButton(
                                onClick = { onDetach(document.id) },
                                modifier = Modifier.offset(x = Spacing.tight),
                            ) {
                                Text(
                                    text = stringResource(R.string.visit_documents_detach),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }

        if (enabled) {
            RowDivider()
            Row(
                modifier = Modifier.padding(top = Spacing.tight, start = Spacing.hair),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onScan, enabled = !scanning) {
                    Text(stringResource(R.string.document_scan))
                }
                TextButton(onClick = onAttach, enabled = !scanning) {
                    Text(stringResource(R.string.visit_documents_attach_existing))
                }
            }
        }
    }
}

/**
 * The bunny's documents no visit has claimed.
 *
 * Only the unclaimed ones, because `visitId` is single-valued: offering one that already belongs to
 * another visit would silently move it, and a document quietly leaving last year's dental record is
 * the kind of change nobody notices until they go looking for it.
 *
 * The list is a plain [Column], not a `LazyColumn`: [BinkyDialog] scrolls its own content, and a
 * lazy list inside a scrolling parent measures against an unbounded height and composes every row —
 * losing the only thing it is for. `10a`–`10d` made the same swap twice.
 */
@Composable
private fun AttachDocumentDialog(
    choices: List<DocumentRow>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BinkyDialog(
        title = stringResource(R.string.visit_documents_attach_existing),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        if (choices.isEmpty()) {
            Text(stringResource(R.string.visit_documents_none_free))
        } else {
            Column {
                choices.forEach { document ->
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(document.id) }
                                .padding(vertical = Spacing.snug),
                    )
                }
            }
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

/**
 * Deleting a visit **states the choice about its weighing** rather than guessing (PLAN 5c).
 *
 * One confirmation, not ADR-0004's two-stage ceremony — that is calibrated to a bunny's whole
 * history. But the weighing is a second record with a life of its own: keeping it leaves a
 * standalone number in the chart, and removing it takes the vet's reading out of the series. The
 * default is **keep**, because it is the recoverable one.
 *
 * With no weighing at the visit there is nothing to choose, and the dialog says so in one line.
 *
 * Hosted here from Phase 7; it used to be raised by the row on the Care & Meds list.
 */
@Composable
private fun DeleteVisitDialog(
    visitedOn: LocalDate,
    weightGrams: Int?,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var keepWeighing by remember(visitedOn) { mutableStateOf(true) }

    BinkyDialog(
        title = stringResource(R.string.visit_delete_title),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(weightGrams == null || keepWeighing) }) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        Text(stringResource(R.string.visit_delete_body, dateLabel(visitedOn)))
        weightGrams?.let { grams ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(stringResource(R.string.visit_delete_weighing, gramsLabel(grams)))
                WeighingChoice(
                    label = stringResource(R.string.visit_delete_keep_weighing),
                    selected = keepWeighing,
                    onSelect = { keepWeighing = true },
                )
                WeighingChoice(
                    label = stringResource(R.string.visit_delete_remove_weighing),
                    selected = !keepWeighing,
                    onSelect = { keepWeighing = false },
                )
            }
        }
    }
}

@Composable
private fun WeighingChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.tight),
        )
    }
}
