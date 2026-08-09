package app.binky.tracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
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

/**
 * A text box's radius, one step tighter than a card's, so it reads as a control inside one.
 *
 * Public for the one box this file does not draw: `6e`'s grams field is 72dp tall at `headlineMedium`
 * — deliberately the only oversized input in the app — and it still has to be the same *shape* as
 * every other box, or it reads as a different kind of control rather than a bigger one.
 */
val FieldRadius = 14.dp

/** A row carrying a control the owner reads a value off — a date beside its *Change*, a switch. */
private val ControlRowHeight = 56.dp

/** [SwitchRow]'s: taller, because it carries a title and a line of help rather than one value. */
private val SwitchRowHeight = 64.dp

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
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
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
 *
 * [placeholder] is an **example** of what to type, so it is left out where there is no useful one to
 * give: a vet's notes are whatever that owner wants to remember about that clinic, and inventing a
 * specimen would suggest the field expects a particular kind of answer.
 */
@Composable
fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder?.let { { Text(it) } },
        shape = RoundedCornerShape(FieldRadius),
        minLines = 3,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * One line of text — a name, an amount — in the same box [NoteField] uses, one line tall.
 *
 * No floating label here either, for [NoteField]'s reason: the [FieldLabel] above it is the label,
 * and one that flies away on focus takes the question with it exactly when the owner starts
 * answering. The error *text* is [ErrorText]'s job below the field; [isError] only colours the box,
 * so the two cannot say different things.
 *
 * [keyboardOptions] is what the field asks the system for. It matters more than it looks: a clinic's
 * phone number wants the *phone* keypad rather than the numeric one, because a number can carry a
 * `+`, spaces and parentheses and is stored exactly as it was typed.
 */
@Composable
fun SingleLineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder?.let { { Text(it) } },
        shape = RoundedCornerShape(FieldRadius),
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A recorded value with the button that changes it — a start date, the time of a weighing.
 *
 * [description] is what a screen reader hears instead of the button's own label. Compose reads a
 * merged node's `contentDescription` in preference to the text inside it, which is what lets three
 * buttons on one card all read *Change* on screen and still announce which one they are.
 *
 * [label] names the value when a card holds more than one of them — *Starts* and *Ends* are two
 * dates and the second is only meaningful against the first. [RecordedAtField] leaves it out: its
 * two rows are a date and a time, which say what they are by their own shape.
 *
 * [enabled] false renders the same row with **no way to change it** — the button is absent rather
 * than present-and-refusing (ADR-0004's shape).
 *
 * [actionLabel] overrides *Change* for a value that has never been set: `4e` writes *"Set a
 * birthday"* and *"Choose a breed"* beside *not known* and *not set*, and it is right to, because
 * "change" is the wrong verb for a field that holds nothing yet.
 *
 * [onClear] adds the second, quieter button that takes the value **back** to absent. It reads a bare
 * *Clear* on screen and announces [clearDescription] — the same trick [description] plays for the
 * first button, and for the same reason: two rows in one card would otherwise both announce *Clear*
 * with no way to tell which field they empty.
 */
@Composable
fun ChangeableValueRow(
    value: String,
    description: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    actionLabel: String? = null,
    onClear: (() -> Unit)? = null,
    clearDescription: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ControlRowHeight)
                // The button carries its own padding, so it stops short of the card's edge on its
                // own; a read-only row has nothing there and takes the full inset instead.
                .padding(start = Spacing.base, end = if (enabled) Spacing.tight else Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
        if (enabled) {
            TextButton(
                onClick = onChange,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(actionLabel ?: stringResource(R.string.action_change))
            }
            // Only when there is something to clear: a permanently visible *Clear* beside an empty
            // field is a control that does nothing four times out of five.
            if (onClear != null) {
                TextButton(
                    onClick = onClear,
                    modifier =
                        Modifier.semantics {
                            if (clearDescription != null) contentDescription = clearDescription
                        },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }
    }
}

/**
 * A switch with its name and, usually, the line of help that says what turning it off means.
 *
 * The **whole row** is the target, not the switch: `toggleable` merges the title, the help and the
 * state into one semantics node, so a screen reader announces the setting once instead of reading a
 * paragraph and then finding an unlabelled switch after it. That is also why the [Switch] itself
 * takes `onCheckedChange = null` — two handlers would be two tap targets stacked on one row.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helpText: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
                .heightIn(min = SwitchRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
        horizontalArrangement = Arrangement.spacedBy(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (helpText != null) HelpText(helpText)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A value the owner **added** and can take away again — one time of day on a course's schedule.
 *
 * The third chip in the language, and the three divide by what they are for: [FormChip] is an
 * answer you *choose*, [app.binky.tracker.ui.common.TagChip] is a fact you *read*, and this is an
 * entry you *remove*. Hay-filled like the tag rather than outlined like an unchosen option, because
 * a time on this list is something the owner put there — apricot and the outline both belong
 * elsewhere.
 *
 * The whole chip removes, not just the ✕: the mark says what tapping does, and a 12dp target inside
 * a 36dp one is a miss waiting to happen.
 */
@Composable
fun RemovableChip(
    label: String,
    removeDescription: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(imageVector = Icons.Filled.Close, contentDescription = removeDescription)
        },
        colors =
            InputChipDefaults.inputChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        // The fill is what says "recorded"; an outline round it would say "not chosen" as well.
        border = null,
        modifier = modifier.height(ChipHeight),
    )
}
