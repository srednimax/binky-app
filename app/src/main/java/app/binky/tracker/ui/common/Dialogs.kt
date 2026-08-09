package app.binky.tracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.binky.tracker.theme.Spacing

// Phase 7's dialog rules, decided once for all of them on `3f`/`3g` and written here rather than
// per dialog: 28dp radius, 24dp padding, the title at `headlineSmall` with the subject beneath it,
// and the actions bottom-right as text buttons with the confirming one last. Most of that is M3's
// own default for `AlertDialog`, which is why this is a thin wrapper and not a re-implementation.
//
// What is *not* M3's default is the level, and that is the whole reason this file exists.

/**
 * Where a dialog sits, which is the one thing about it that differs between the themes.
 *
 * In **light** it goes *down* to `surfaceContainerLow`: it is already separated from the screen by
 * a scrim, so it does not need to shout as well. In **dark** it goes *up* to
 * `surfaceContainerHigh` — it has to lift off a 50%-black scrim, and lighter is the only direction
 * available. Same intent, opposite direction, which is why one constant cannot express it.
 *
 * Read off the scheme in force rather than from [app.binky.tracker.theme.BinkyTheme]'s `darkTheme`
 * flag, because Material You can supply either scheme and this has to be right under both.
 */
@Composable
private fun dialogSurfaces(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return if (dark) {
        scheme.surfaceContainerHigh to scheme.surfaceContainerHighest
    } else {
        scheme.surfaceContainerLow to scheme.surfaceContainer
    }
}

/** Halfway. A scheme's `surface` is near-white or near-black; nothing in either lands near this. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * Binky's dialog: a question, what it is about, the fields that answer it, and two actions.
 *
 * [subject] is the line under the title that says *which* thing this is about — "Metacam · 0.3 ml".
 * `3f` added it because the title alone stopped being enough: a course with two doses a day makes
 * "Record a dose" ambiguous about which one, and the answer is a fact the dialog already holds.
 *
 * The content is scrollable, because a dialog on a short phone in landscape is the one place a
 * form can run out of room with no way to reach the button.
 *
 * **The nested level is provided, not passed.** [LocalCardSurface] steps any [GroupedCard] inside
 * one step above the dialog itself, so a date field in here reads as raised off the dialog the way
 * it reads as raised off the background outside it. `3g` calls this "the only two-level nesting in
 * the app", and it is what stops a card going *darker* than the dialog holding it.
 *
 * Not for the pickers: [androidx.compose.material3.DatePickerDialog] and the time picker are M3
 * components with their own container contract, and re-colouring the frame round a picker that
 * still paints itself would only make the two disagree.
 */
@Composable
fun BinkyDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subject: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (container, nested) = dialogSurfaces()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = container,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                // The slot already provides `headlineSmall`, which Type.kt renders in Nunito.
                Text(title)
                if (subject != null) {
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            CompositionLocalProvider(LocalCardSurface provides nested) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.base),
                    content = content,
                )
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}
