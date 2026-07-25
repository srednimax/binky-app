package app.bunny.tracker.ui.weight

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.shell.StubScreen

/**
 * Weight — always bunny-scoped, and the one screen that **refuses** "All bunnies" (ADR-0015):
 * weight is individual, and overlaying unrelated animals of different sizes on one axis would say
 * nothing true. The chart and entry land in Phase 2.
 */
@Composable
fun WeightScreen(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val body =
        when (state.selection) {
            BunnySelection.Loading -> return
            BunnySelection.Empty -> stringResource(R.string.add_a_bunny_first)
            BunnySelection.All -> stringResource(R.string.weight_pick_a_bunny)
            else -> stringResource(R.string.weight_stub)
        }
    StubScreen(state = state, body = body, modifier = modifier)
}
