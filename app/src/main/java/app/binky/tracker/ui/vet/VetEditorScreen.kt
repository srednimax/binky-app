package app.binky.tracker.ui.vet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.ui.appViewModelExtras

/**
 * Add or edit one vet — a detail screen off the directory, in the shape every other editor in the
 * app takes: its own app bar, Save in the actions, and no bottom navigation while it is open.
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

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text(stringResource(R.string.vet_name_label)) },
                isError = state.nameInvalid,
                supportingText = {
                    if (state.nameInvalid) Text(stringResource(R.string.vet_name_required))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.clinic,
                onValueChange = viewModel::onClinicChanged,
                label = { Text(stringResource(R.string.vet_clinic_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChanged,
                label = { Text(stringResource(R.string.vet_phone_label)) },
                // The phone keyboard, not the number one: a clinic's number can carry a +, spaces
                // and parentheses, and it is stored as typed rather than parsed.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text(stringResource(R.string.vet_notes_label)) },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
