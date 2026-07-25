package app.bunny.tracker.ui.bunny

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bunny.tracker.R

/**
 * Add or edit a bunny. A stub until checkpoint 1d — the route exists now because the switcher's
 * "Add a bunny" item is load-bearing (there is no separate bunny-list screen, ADR-0015) and a menu
 * item that goes nowhere is not a wired switcher.
 *
 * Unlike the top-level destinations this is a **detail** screen: it is pushed onto the back stack
 * and carries its own app bar, so the shell's switcher and bottom bar step aside while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunnyEditorScreen(
    bunnyId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A plain Column, not a nested Scaffold: the shell's Scaffold has already applied the window
    // insets to whatever NavDisplay renders, and a second Scaffold would apply them again — a
    // status-bar-sized gap above every detail screen.
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (bunnyId == null) R.string.bunny_editor_add_title else R.string.bunny_editor_edit_title,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold is the one owner of window insets: it has already padded
            // everything NavDisplay renders down past the status bar. A TopAppBar pads for the
            // status bar itself by default, which would do it a second time.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Text(
            text = stringResource(R.string.bunny_editor_stub),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
