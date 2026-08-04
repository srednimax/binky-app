package app.binky.tracker.ui.vet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.VetEntity
import app.binky.tracker.ui.appViewModelExtras

/**
 * The vet directory: every vet the household uses, in one list (ADR-0017).
 *
 * A **detail screen off More**, not a tab and not per bunny — a clinic's phone number belongs to the
 * household rather than to one rabbit, and a directory per bunny would make the owner type it in
 * twice and then keep two copies in step by hand.
 *
 * Nothing here is scoped by the selected bunny, so it renders the same in the archived scope as
 * anywhere else: the entry outlives the visits (ADR-0017), and it outlives an archive too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetsScreen(
    onBack: () -> Unit,
    onAddVet: () -> Unit,
    onEditVet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VetsViewModel = viewModel(factory = VetsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.vets_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold is the one owner of window insets; padding here would double it.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = onAddVet) { Text(stringResource(R.string.vet_add)) }
            }

            if (state.vets.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.vets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.vets, key = { it.id }) { vet ->
                VetRow(
                    vet = vet,
                    onOpen = { onEditVet(vet.id) },
                    onDelete = { viewModel.requestDelete(vet) },
                )
            }
        }
    }

    state.pendingDelete?.let { vet ->
        DeleteVetDialog(
            name = vet.name,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun VetRow(
    vet: VetEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = vet.name, style = MaterialTheme.typography.titleMedium)
            // Everything but the name is optional, so each line is drawn only when it has something
            // in it — a card of empty labels reads as missing data rather than as a short entry.
            vet.clinic?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vet.phone?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            vet.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

/**
 * **One** confirmation, and the body says what survives: the visits keep their rows and lose only
 * the name (ADR-0017). ADR-0004's two-stage ceremony is for destroying a bunny's whole history, and
 * this destroys a phone number.
 */
@Composable
private fun DeleteVetDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vet_delete_title)) },
        text = { Text(stringResource(R.string.vet_delete_body, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
