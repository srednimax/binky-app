package app.binky.tracker.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.binky.tracker.theme.Spacing

// Phase 7's row-and-card treatment, in one place because every route uses it.
//
// The mockups draw the same three things on screen after screen: a quiet section header, a grouped
// card, and label/value rows inside it separated by inset dividers. Written here once so the
// remaining routes are an adjustment against a drawing rather than twenty re-derivations of it —
// and so that changing the idiom later is one file, which is the same bet ADR-0012 makes about
// colour.
//
// Colour comes from MaterialTheme only (ADR-0012). The mockups' hexes are hand-picked and do not
// sit on the generated tonal grid; the roles do, and the roles are what a device renders.

/** The card radius the language uses on every surface that sits directly on the background. */
val CardRadius = 20.dp

/**
 * The full-width primary action — *Record a weighing*, *Log a healthy day*.
 *
 * Taller than M3's 40dp default and fully rounded, which the drawings are consistent about: this is
 * the one action its tab exists for, and it sits alone between two sections rather than in a row of
 * peers where a default-sized button would be right.
 */
val RecordButtonHeight = 52.dp

/** [RecordButtonHeight]'s radius — half of it, so the ends are true semicircles. */
val RecordButtonRadius = 26.dp

/** A [DenseFactRow]'s minimum height, against [FactRow]'s 48dp. */
private val DenseRowHeight = 28.dp

/** A [TagChip]'s height, and the smaller one a card's header row takes. */
private val TagChipHeight = 32.dp

private val DenseTagChipHeight = 26.dp

/**
 * How much room a scrolling screen has to leave at its bottom so its last row clears the FAB.
 *
 * 56dp of button, 16dp of the margin the Scaffold gives it, and 16dp so the content stops short of
 * touching it rather than ending exactly underneath. Nothing computes this for us: Compose's
 * `Scaffold` pads its content for the bars it owns but not for the FAB floating over it, so a screen
 * that scrolls to its end hides its own last row — which the before set does on Home, where *Delete*
 * sits under the button.
 */
val FabClearance = 88.dp

/**
 * One step down, for a card *inside* a card — Home's all-bunnies list nests the trend flag inside
 * each bunny's card, and a nested corner matching its parent's reads as a rendering mistake.
 */
val NestedCardRadius = 16.dp

/**
 * A section's name, sitting above its content.
 *
 * Deliberately quiet — `titleSmall` in [MaterialTheme.colorScheme.onSurfaceVariant] — because it
 * labels a group the owner is already looking at rather than announcing anything. `Spacing`'s rhythm
 * rule is the other half: [Spacing.section] above this, [Spacing.tight] below it, so it reads as
 * attached downward and detached upward.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.hair),
    )
}

/**
 * The grouped card: one raised surface holding rows that belong together.
 *
 * [nested] steps the surface up one level rather than tinting it, which is what keeps a card inside
 * a card legible in **dark** — where a nested card cannot be distinguished by shadow and a tint
 * would just be a lighter grey with no meaning. Light and dark are the same rule, not two.
 *
 * Kotlin note: `content: @Composable ColumnScope.() -> Unit` is a lambda *with a receiver* — inside
 * it, `this` is the `Column`, so callers can use `Modifier.weight` and friends without naming it.
 * There is no JS equivalent; the nearest thing is a callback invoked with a bound `this`.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.hair),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (nested) NestedCardRadius else CardRadius),
        color =
            if (nested) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * One row of a [GroupedCard] whose rows are **separate lazy items**.
 *
 * A weight history runs to hundreds of rows, and a `GroupedCard` wrapping them all is a single
 * `LazyColumn` item — so every row composes whether or not it is on screen, which is the one thing
 * `LazyColumn` exists to avoid. This draws the same card the other way round: each row carries the
 * card's surface itself, and only the two rows at the ends round their outer corners, so a list that
 * scrolls still reads as one grouped card.
 *
 * [index] and [count] are the row's place in the **whole** list, not in the visible window — a
 * lazy layout never composes the rows either side, so a row cannot work out its own ends.
 */
@Composable
fun GroupedCardItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = if (index == 0) CardRadius else 0.dp
    val bottom = if (index == count - 1) CardRadius else 0.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            // Above every row but the first, which is [RowDivider]'s rule stated per row instead of
            // per card: the card's own edge already separates the two at the ends.
            if (index > 0) RowDivider()
            Column(
                modifier =
                    Modifier.padding(
                        top = if (index == 0) Spacing.hair else 0.dp,
                        bottom = if (index == count - 1) Spacing.hair else 0.dp,
                    ),
                content = content,
            )
        }
    }
}

/**
 * A label and its value, on one row of a [GroupedCard].
 *
 * 48dp minimum so a row stays a comfortable target and a card of them keeps an even rhythm, and the
 * value is right-aligned so a column of them lines up down the card's trailing edge. The label
 * carries [MaterialTheme.colorScheme.onSurfaceVariant] and the value `onSurface`: the value is the
 * thing being read, and the label is only there to say what it is.
 */
@Composable
fun FactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = Spacing.base),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A label and its value inside a block of rows that all describe **one** thing.
 *
 * [FactRow]'s dense twin, and the difference between them is about meaning rather than size. A
 * divider separates rows that are independent of each other, and the four droppings facts of a
 * single observation are not independent — they are one answer in four parts. So these run 28dp
 * tall, carry no divider, and the caller stacks them at [Spacing.hair] as one block the eye reads
 * straight down, where a column of [FactRow]s reads as a list of separate things.
 *
 * No horizontal padding of its own, unlike [FactRow]: a block like this sits inside a card that has
 * already inset its contents, where [FactRow] draws edge to edge in a card that has not.
 */
@Composable
fun DenseFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = DenseRowHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A read-only chip: something the owner **recorded**, stated as a tag rather than a sentence.
 *
 * Hay — `secondaryContainer` — and never apricot. Apricot is what the *app* raises, and a symptom or
 * a housemate's name is something the owner put there themselves; colouring the two alike would make
 * the app look like it had an opinion about a tick the owner entered.
 *
 * Not one of M3's `Chip`s, which are all controls: they carry a click, a ripple and a selected state
 * this has none of. [dense] is the smaller one that rides in a card's header row beside a title.
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    Surface(
        modifier = modifier,
        // A percentage rather than a dp, so the two sizes stay true pill ends without a second
        // constant that has to be kept at half the height.
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = if (dense) DenseTagChipHeight else TagChipHeight)
                    .padding(horizontal = if (dense) Spacing.tight else Spacing.snug),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * The divider *between* rows of a [GroupedCard] — never above the first or below the last, where it
 * would only draw a line next to the card's own edge.
 *
 * Inset to the rows' text, not to the card's edge: a divider that runs the full width cuts the card
 * in two, where an inset one reads as a seam between rows of one thing.
 */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(start = Spacing.base))
}
