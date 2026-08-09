package app.binky.tracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.binky.tracker.theme.Spacing

// Phase 7's *form* idiom, the counterpart to Surfaces.kt's row-and-card one.
//
// The design fixes these rules on `2c` (Record an observation) and says so explicitly — it is not
// the prettiest screen in the set, it is the one where the rules get decided, and every other editor
// in the sweep inherits them from here rather than re-deriving them:
//
//   * **Chips wrap; they never scroll sideways.** The before set cut "More than usual" and "Strung
//     together" off the right edge, and an option nobody sees is an option nobody picks.
//   * **A section is a card**, not a hairline rule on the background.
//   * **Help text belongs to the thing above it**, at [Spacing.tight] — close enough to read as its
//     footnote rather than as the next field's heading.
//   * **Free text is an outlined box with a placeholder**, not a floating label: the label above it
//     already says what the field is, and a label that flies away on focus takes the question with
//     it exactly when the owner starts answering.
//
// Colour comes from MaterialTheme only (ADR-0012).

/**
 * Chip height.
 *
 * 36dp rather than M3's 32dp default, which the drawings are consistent about — a wrapping grid of
 * chips is the primary control on every editor here, not a filter bar above a list. Compose still
 * expands the *touch* target to 48dp underneath it, so this is a visual size and not a tappability
 * one.
 */
private val ChipHeight = 36.dp

/** The free-text box: its own radius, one step tighter than a card's, so it reads as a control. */
private val NoteFieldRadius = 14.dp

/**
 * A titled card holding one group of fields — the shape every editor is now built from.
 *
 * [SectionHeader] above, [Spacing.tight] under it, and a [GroupedCard] holding the fields. [spacing]
 * is the gap *between* fields inside the card: [Spacing.base] where each entry is its own question,
 * [Spacing.tight] where what follows is a footnote on what came before.
 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.base),
    spacing: Dp = Spacing.base,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        SectionHeader(title)
        Spacer(Modifier.height(Spacing.tight))
        GroupedCard(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * The name of one field inside a [FormSection] — quieter than the section's own title, because the
 * card has already said what group this belongs to.
 */
@Composable
fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A footnote on the control above it. */
@Composable
fun HelpText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Why a form refuses to save, in the same slot [HelpText] uses.
 *
 * Same size, different role: this is the only place a form spends `error`, and it says what the
 * owner has to change rather than that something is wrong.
 */
@Composable
fun ErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

/**
 * A row of [FormChip]s that **wraps** onto as many lines as it needs.
 *
 * Kotlin note: `FlowRowScope.() -> Unit` is a lambda with a receiver, like [GroupedCard]'s — inside
 * it, `this` is the flow row.
 */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        content = content,
    )
}

/**
 * One option in a [ChipRow]: filled when chosen, a 1dp outline when not.
 *
 * The outline is what keeps dark honest — filling every chip faintly, the way a tint would, makes
 * the whole row look chosen, and these rows are exactly where "chosen" carries the meaning.
 */
@Composable
fun FormChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        modifier = modifier.height(ChipHeight),
    )
}

/**
 * Free text, in the owner's own words.
 *
 * No floating label (the [FieldLabel] above it is the label) and no counter. `minLines` rather than
 * a fixed height, because a height leaves the caret floating in the middle of an empty box where
 * three lines of room start the text at the top and grow from there — the drawing's 88dp minimum,
 * expressed the way the text field can honour it.
 */
@Composable
fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(NoteFieldRadius),
        minLines = 3,
        modifier = modifier.fillMaxWidth(),
    )
}
