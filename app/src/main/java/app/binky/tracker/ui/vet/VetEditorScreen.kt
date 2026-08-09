package app.binky.tracker.ui.vet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldLabel
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.NoteField
import app.binky.tracker.ui.common.SingleLineField

/**
 * Add or edit one vet — a detail screen off the directory, in the shape every other editor in the
 * app takes: its own app bar, *Save* in the actions, and no bottom navigation while it is open.
 *
 * ## Phase 7
 *
 * `5a` and `5b` draw the directory and not this screen, so the language is applied by hand — which
 * is exactly what `Forms.kt` exists for. Four labelled fields in **one card with no header**: the
 * app bar has already said whether this is a new vet or an existing one, and a heading repeating it
 * over the only card on the screen is the furniture ADR-0013 warns about. The labels are the app's
 * existing words, unchanged — *"Clinic, if any"* reads as well above a box as it did inside one, so
 * this route changes no string at all.
 *
 * **Deleting the vet lives here**, moved off the directory row with the redraw. See [VetsScreen] for
 * why, and `1d` for where the rule comes from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetEditorScreen(
    vetId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VetEditorViewModel =
        viewModel(
            key = "vet-editor-${vetId ?: "new"}",
            factory = VetEditorViewModel.factory(vetId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Two ways out of this screen and one mechanism for each, so the form and the stored row cannot
    // disagree about whether the write landed.
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(if (state.isNew) R.string.vet_editor_add_title else R.string.vet_editor_edit_title),
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
            GroupedCard(
                contentPadding = PaddingValues(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                Field(label = stringResource(R.string.vet_name_label)) {
                    SingleLineField(
                        value = state.name,
                        onValueChange = viewModel::onNameChanged,
                        isError = state.nameInvalid,
                    )
                    if (state.nameInvalid) ErrorText(stringResource(R.string.vet_name_required))
                }

                Field(label = stringResource(R.string.vet_clinic_label)) {
                    SingleLineField(value = state.clinic, onValueChange = viewModel::onClinicChanged)
                }

                Field(label = stringResource(R.string.vet_phone_label)) {
                    SingleLineField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChanged,
                        // The phone keyboard, not the number one: a clinic's number can carry a +,
                        // spaces and parentheses, and it is stored as typed rather than parsed.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }

                Field(label = stringResource(R.string.vet_notes_label)) {
                    // No placeholder: whatever an owner wants to remember about a clinic is theirs,
                    // and an example here would suggest the field expects a particular kind of note.
                    NoteField(value = state.notes, onValueChange = viewModel::onNotesChanged)
                }
            }

            // Quieter than *Save*, and one tap behind a confirmation: it is the one action on this
            // screen that destroys a record, and it is not what the owner came for. Only on a vet
            // that exists — there is nothing to delete before the first save, and a button that
            // refuses when tapped is what ADR-0004 rules out.
            if (!state.isNew) {
                TextButton(onClick = viewModel::requestDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.confirmingDelete) {
        DeleteVetDialog(
            name = state.storedName,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * One field: its name, the control, and anything the control has to say afterwards.
 *
 * [Spacing.tight] throughout, which is `Forms.kt`'s rule that help belongs to the thing **above** it
 * — so an error under the name box reads as a footnote on what was typed rather than as a heading
 * for the clinic.
 */
@Composable
private fun Field(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        FieldLabel(label)
        content()
    }
}

/**
 * **One** confirmation, and the body says what survives: the visits keep their rows and lose only
 * the name (ADR-0017). ADR-0004's two-stage ceremony is for destroying a bunny's whole history, and
 * this destroys a phone number.
 *
 * Hosted here from Phase 7; it used to be raised by the row on the directory.
 */
@Composable
private fun DeleteVetDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BinkyDialog(
        title = stringResource(R.string.vet_delete_title),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        Text(stringResource(R.string.vet_delete_body, name))
    }
}
