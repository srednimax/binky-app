package app.bunny.tracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.bunny.BunnyAvatar
import app.bunny.tracker.ui.bunny.BunnyDialogHost
import app.bunny.tracker.ui.bunny.BunnyProfile
import app.bunny.tracker.ui.bunny.ageLabel
import app.bunny.tracker.ui.bunny.dateLabel
import app.bunny.tracker.ui.bunny.housematesLabel
import app.bunny.tracker.ui.bunny.neuterLabel
import app.bunny.tracker.ui.bunny.sexLabel

/**
 * Home — the selected bunny's profile, and under "All bunnies" the fluffle dashboard (ADR-0015).
 *
 * The dashboard **is the bunny list**; there is deliberately no separate list screen, because two
 * screens rendering the same rows would diverge the moment one of them gained a field. Phase 2 grows
 * the card into the vitals card — current weight, trend flag, active watch — above what is here.
 */
@Composable
fun HomeScreen(
    onAddBunny: () -> Unit,
    onEditBunny: (String) -> Unit,
    onSelectBunny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.selection) {
        // Momentary, before the first database and preferences emissions arrive. Rendering nothing
        // beats flashing an empty state at an owner who has bunnies.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> NoBunniesYet(onAddBunny, modifier)
        BunnySelection.All -> AllBunnies(state, onSelectBunny, modifier)
        else ->
            state.profiles.firstOrNull()?.let { profile ->
                OneBunny(
                    profile = profile,
                    readOnly = state.readOnly,
                    onEdit = { onEditBunny(profile.id) },
                    onArchive = { viewModel.requestArchive(profile) },
                    onDelete = { viewModel.requestDelete(profile) },
                    modifier = modifier,
                )
            }
    }

    BunnyDialogHost(
        dialog = state.dialog,
        onConfirm = viewModel::confirmDialog,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun NoBunniesYet(
    onAddBunny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onAddBunny) { Text(stringResource(R.string.switcher_add_bunny)) }
    }
}

/** One bunny's profile: who they are, who they live with, and what can be done to the record. */
@Composable
private fun OneBunny(
    profile: BunnyProfile,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BunnyAvatar(avatar = profile.avatar, name = profile.name, size = 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = profile.name, style = MaterialTheme.typography.headlineSmall)
                ageLabel(profile.birthDate, profile.birthDateApproximate)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                housematesLabel(profile.housemates)?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Fact(stringResource(R.string.bunny_sex_label), sexLabel(profile.sex))
        Fact(stringResource(R.string.bunny_neutered_label), neuterLabel(profile.neutered))
        // Only an *exact* birthdate is ever shown as a date; an approximate one is an age and
        // nothing more, or the app invents a precision the owner never had (ADR-0016).
        if (profile.birthDate != null && !profile.birthDateApproximate) {
            Fact(stringResource(R.string.bunny_birthdate_label), dateLabel(profile.birthDate))
        }
        profile.breed?.let { Fact(stringResource(R.string.bunny_breed_label), it) }
        profile.colour?.let { Fact(stringResource(R.string.bunny_colour_label), it) }

        if (!readOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onArchive) { Text(stringResource(R.string.action_archive)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }

        Text(
            text = stringResource(R.string.home_stub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AllBunnies(
    state: HomeUiState,
    onSelectBunny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_all_bunnies_stub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Kotlin note: `items(list) { }` is the LazyColumn equivalent of `list.map(...)` in JSX —
        // except only the visible rows are composed, so a long list stays cheap.
        items(state.profiles, key = { it.id }) { profile ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelectBunny(profile.id) }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    BunnyAvatar(avatar = profile.avatar, name = profile.name)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                        ageLabel(profile.birthDate, profile.birthDateApproximate)?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall)
                        }
                        housematesLabel(profile.housemates)?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
