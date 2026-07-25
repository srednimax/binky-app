package app.bunny.tracker.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.shell.StubScreen

/**
 * More — photos (Phase 3), documents (Phase 5), settings, support, and the archived bunnies list
 * (checkpoint 1d). Rows inside here are where ADR-0015 allows a "coming soon", since a dead row
 * costs one line rather than a fifth of primary navigation.
 */
@Composable
fun MoreScreen(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val body =
        when (state.selection) {
            BunnySelection.Loading -> return
            else -> stringResource(R.string.more_stub)
        }
    StubScreen(state = state, body = body, modifier = modifier)
}
