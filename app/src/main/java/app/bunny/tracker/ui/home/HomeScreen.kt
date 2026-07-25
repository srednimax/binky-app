package app.bunny.tracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.shell.StubScreen

/**
 * Home — the selected bunny's overview, and under "All bunnies" the fluffle dashboard (ADR-0015).
 *
 * The dashboard **is the bunny list**; there is deliberately no separate list screen, because two
 * screens rendering the same rows would diverge the moment one of them gained a field. Checkpoint
 * 1d fills the card in with avatar, age and "Lives with"; Phase 2 turns it into the vitals card.
 */
@Composable
fun HomeScreen(
    state: ShellUiState,
    onAddBunny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.selection) {
        // Momentary, before the first database and preferences emissions arrive. Rendering nothing
        // beats flashing an empty state at an owner who has bunnies.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> NoBunniesYet(onAddBunny, modifier)
        BunnySelection.All -> AllBunnies(state, modifier)
        else -> StubScreen(state = state, body = stringResource(R.string.home_stub), modifier = modifier)
    }
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

@Composable
private fun AllBunnies(
    state: ShellUiState,
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
            )
        }
        // Kotlin note: `items(list) { }` is the LazyColumn equivalent of `list.map(...)` in JSX —
        // except only the visible rows are composed, so a long list stays cheap.
        items(state.activeBunnies, key = { it.id }) { bunny ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = bunny.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
