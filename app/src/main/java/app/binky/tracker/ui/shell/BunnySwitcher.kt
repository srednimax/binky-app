package app.binky.tracker.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.ui.bunny.BunnyAvatar

/**
 * The persistent bunny switcher (ADR-0015). A **scope indicator first and a picker second**: with
 * one bunny it still says whose data is on screen.
 *
 * It **always opens a menu** — the active bunnies, "All bunnies" once two exist, and "Add a bunny"
 * always. That last item is load-bearing: there is no separate bunny-list screen, so without it a
 * single-bunny owner would have nowhere to add a second.
 */
@Composable
fun BunnySwitcher(
    state: ShellUiState,
    onSelectBunny: (String) -> Unit,
    onSelectAllBunnies: () -> Unit,
    onAddBunny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kotlin note: `var x by rememberSaveable { mutableStateOf(false) }` is Compose's useState —
    // `by` delegates reads and writes to the state object, so `expanded = true` triggers
    // recomposition. `rememberSaveable` additionally survives rotation and process death.
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            // The avatar is what disambiguates two bunnies with near-identical names, which
            // ADR-0016 explicitly allows — so the scope indicator shows it, not just the name.
            state.scopedBunny?.let { bunny ->
                BunnyAvatar(avatar = bunny.avatar, name = bunny.name, size = 28.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text = switcherLabel(state))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.switcher_open),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.activeBunnies.forEach { bunny ->
                DropdownMenuItem(
                    text = { Text(bunny.name) },
                    leadingIcon = { BunnyAvatar(avatar = bunny.avatar, name = bunny.name, size = 32.dp) },
                    onClick = {
                        expanded = false
                        onSelectBunny(bunny.id)
                    },
                )
            }
            if (state.offersAllBunnies) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.switcher_all_bunnies)) },
                    onClick = {
                        expanded = false
                        onSelectAllBunnies()
                    },
                )
            }
            if (state.activeBunnies.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.switcher_add_bunny)) },
                onClick = {
                    expanded = false
                    onAddBunny()
                },
            )
        }
    }
}

@Composable
private fun switcherLabel(state: ShellUiState): String =
    when (state.selection) {
        // Momentary, before the first database and preferences emissions arrive.
        BunnySelection.Loading -> stringResource(R.string.app_name)
        BunnySelection.Empty -> stringResource(R.string.switcher_no_bunnies)
        BunnySelection.All -> stringResource(R.string.switcher_all_bunnies)
        is BunnySelection.Single, is BunnySelection.Archived ->
            state.scopedBunny?.name ?: stringResource(R.string.app_name)
    }
