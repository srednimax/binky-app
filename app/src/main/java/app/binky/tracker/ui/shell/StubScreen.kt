package app.binky.tracker.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection

/**
 * The body every not-yet-built destination renders: **who it is scoped to**, then what is missing.
 *
 * The scope line is the point (ADR-0015). A stub scoped to the wrong bunny is otherwise
 * indistinguishable from one scoped to the right one, so rendering the selection is what makes the
 * switcher's wiring falsifiable while it is still cheap to change.
 */
@Composable
fun StubScreen(
    state: ShellUiState,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScopeLine(state)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

/** Names the current scope in words. Blank while loading, and on the add-a-bunny empty state. */
@Composable
fun ScopeLine(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val name = state.scopedBunny?.name
    val text =
        when (state.selection) {
            BunnySelection.Loading, BunnySelection.Empty -> null
            BunnySelection.All -> stringResource(R.string.scope_all_bunnies)
            is BunnySelection.Single -> name?.let { stringResource(R.string.scope_bunny, it) }
            is BunnySelection.Archived -> name?.let { stringResource(R.string.scope_bunny_archived, it) }
        }
    if (text != null) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
    }
}
