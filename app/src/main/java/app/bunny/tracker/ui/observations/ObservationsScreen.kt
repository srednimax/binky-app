package app.bunny.tracker.ui.observations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.shell.StubScreen

/**
 * Observations — the day-grouped timeline. Under "All bunnies" it is the **combined** timeline
 * across every active bunny, and selecting one *filters* it: observations can cover several bunnies
 * at once (ADR-0008), so here the single-bunny view is the special case (ADR-0015).
 *
 * The timeline itself lands in Phase 2.
 */
@Composable
fun ObservationsScreen(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val body =
        when (state.selection) {
            BunnySelection.Loading -> return
            BunnySelection.Empty -> stringResource(R.string.add_a_bunny_first)
            BunnySelection.All -> stringResource(R.string.observations_all_stub)
            else -> stringResource(R.string.observations_stub)
        }
    StubScreen(state = state, body = body, modifier = modifier)
}

/**
 * The global "+" observation entry (ADR-0015). The route exists from Phase 1 so the structure is
 * settled, but nothing navigates here yet: **no FAB renders** until Phase 2 gives it something to
 * write.
 */
@Composable
fun LogObservationScreen(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    StubScreen(state = state, body = stringResource(R.string.log_observation_stub), modifier = modifier)
}
