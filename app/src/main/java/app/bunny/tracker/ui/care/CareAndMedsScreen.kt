package app.bunny.tracker.ui.care

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.shell.ShellUiState
import app.bunny.tracker.ui.shell.StubScreen

/**
 * Care & Meds — care reminders (Phase 4) and medication courses (Phase 5).
 *
 * This is the destination ADR-0015 expects to be `Hidden` when 1.0 ships, rather than opening real
 * users onto a stub. Through Phases 1-2 it stays visible; the flip is one value in
 * [app.bunny.tracker.TopLevelDestination].
 */
@Composable
fun CareAndMedsScreen(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val body =
        when (state.selection) {
            BunnySelection.Loading -> return
            BunnySelection.Empty -> stringResource(R.string.add_a_bunny_first)
            else -> stringResource(R.string.care_stub)
        }
    StubScreen(state = state, body = body, modifier = modifier)
}
