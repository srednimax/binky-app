package app.binky.tracker.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.BunnyAvatar
import app.binky.tracker.ui.common.CardRadius

/**
 * 56dp, up from Material's 48dp default. The rows carry 32dp avatars, and a menu whose job is
 * telling two rabbits apart is worth the extra height.
 */
private val MenuRowHeight = 56.dp

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
        // Drawn as the screen's title, not as a button: this is the top bar's heading and it
        // happens to open a menu. `onSurface` rather than the text button's default `primary`,
        // because a coloured heading on every screen reads as a link to somewhere.
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            // The avatar is what disambiguates two bunnies with near-identical names, which
            // ADR-0016 explicitly allows — so the scope indicator shows it, not just the name.
            state.scopedBunny?.let { bunny ->
                BunnyAvatar(avatar = bunny.avatar, name = bunny.name, size = 40.dp)
                Spacer(Modifier.width(Spacing.snug))
            }
            Text(text = switcherLabel(state), style = MaterialTheme.typography.titleLarge)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.switcher_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The menu is a surface like any other in the app, so it takes the same 20dp corner and
            // the same container colour rather than Material's 4dp menu default, which reads as a
            // leftover from a different app.
            shape = RoundedCornerShape(CardRadius),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            // A minimum rather than a fixed width: the drawing pins it at 212dp, but a bunny may be
            // called something longer than that and truncating a name in the picker whose whole job
            // is telling two bunnies apart would defeat the menu.
            modifier = Modifier.widthIn(min = 212.dp),
        ) {
            state.activeBunnies.forEach { bunny ->
                DropdownMenuItem(
                    text = { Text(bunny.name) },
                    leadingIcon = { BunnyAvatar(avatar = bunny.avatar, name = bunny.name, size = 32.dp) },
                    modifier = Modifier.heightIn(min = MenuRowHeight),
                    onClick = {
                        expanded = false
                        onSelectBunny(bunny.id)
                    },
                )
            }
            if (state.offersAllBunnies) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.switcher_all_bunnies)) },
                    modifier = Modifier.heightIn(min = MenuRowHeight),
                    onClick = {
                        expanded = false
                        onSelectAllBunnies()
                    },
                )
            }
            // Above the last item, not below the rabbits: it separates the destinations from the one
            // item that *creates* something, which is a different kind of thing to tap.
            if (state.activeBunnies.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.tight))
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.switcher_add_bunny)) },
                modifier = Modifier.heightIn(min = MenuRowHeight),
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
