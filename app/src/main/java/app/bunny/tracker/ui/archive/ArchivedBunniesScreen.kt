package app.bunny.tracker.ui.archive

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bunny.tracker.R
import app.bunny.tracker.ui.appViewModelExtras
import app.bunny.tracker.ui.bunny.BunnyAvatar
import app.bunny.tracker.ui.bunny.BunnyDialogHost
import app.bunny.tracker.ui.bunny.BunnyProfile
import app.bunny.tracker.ui.bunny.dateLabel
import app.bunny.tracker.ui.bunny.housematesLabel
import java.time.ZoneId

/**
 * Archived bunnies, reached from More (ADR-0004): unarchive, delete, or open the bunny read-only.
 *
 * A detail screen, so it carries its own app bar while the shell's switcher and bottom bar step
 * aside — the switcher deliberately does not list archived bunnies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedBunniesScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArchivedBunniesViewModel =
        viewModel(factory = ArchivedBunniesViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.archived_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold already padded everything NavDisplay renders past the status
            // bar; a TopAppBar that pads for it again leaves a status-bar-sized gap.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        if (state.profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.archived_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                ArchivedBunny(
                    profile = profile,
                    onOpen = { onOpen(profile.id) },
                    onUnarchive = { viewModel.unarchive(profile.id) },
                    onDelete = { viewModel.requestDelete(profile) },
                )
            }
        }
    }

    BunnyDialogHost(
        dialog = state.dialog,
        onConfirm = viewModel::confirmDialog,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun ArchivedBunny(
    profile: BunnyProfile,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BunnyAvatar(avatar = profile.avatar, name = profile.name)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                    profile.archivedAt?.let { archivedAt ->
                        Text(
                            text =
                                stringResource(
                                    R.string.archived_on,
                                    dateLabel(archivedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    housematesLabel(profile.housemates)?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.archived_open)) }
                // Unarchiving asks nothing; it only ever restores (ADR-0004).
                TextButton(onClick = onUnarchive) { Text(stringResource(R.string.action_unarchive)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}
