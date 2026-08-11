package app.binky.tracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
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
 * The surface a [GroupedCard] draws on, when the screen it is standing on is not the background.
 *
 * Compose note: a `CompositionLocal` is React's context — a value provided high up and read far
 * below without threading it through every signature in between. It earns that here because "which
 * level am I nested on" is genuinely ambient. A card cannot know, and the alternative is a colour
 * parameter on [GroupedCard], on [app.binky.tracker.ui.common.FormSection] and on
 * [app.binky.tracker.ui.common.RecordedAtField] — three signatures carrying a value only the
 * innermost one uses.
 *
 * [Color.Unspecified] means *take the default*, which is what every screen wants. Only
 * [BinkyDialog] provides anything else: a dialog is a surface of its own, and a card that kept the
 * background's level would sit **below** its own dialog in dark, which is the one thing `3g` is
 * explicit about.
 */
val LocalCardSurface = compositionLocalOf { Color.Unspecified }

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
 * [raised] steps the surface up one level rather than tinting it, and it says two things at once
 * that turn out to be the same thing. A card *inside* a card needs it to stay legible in **dark**,
 * where a nested card cannot be distinguished by shadow and a tint would just be a lighter grey with
 * no meaning; and the one card on a screen that is **not like the others** — Home's trend flag, Care
 * & Meds' delivery caveat — needs it for the same reason. Light and dark are one rule, not two.
 *
 * Spend it **once per screen**. Two raised cards on one route is two claims to be the exception, and
 * the level stops meaning anything.
 *
 * Kotlin note: `content: @Composable ColumnScope.() -> Unit` is a lambda *with a receiver* — inside
 * it, `this` is the `Column`, so callers can use `Modifier.weight` and friends without naming it.
 * There is no JS equivalent; the nearest thing is a callback invoked with a bound `this`.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.hair),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A provided surface wins over [raised]: inside a dialog the level is already decided by what
    // the dialog itself is drawn on, and nothing raises a card in there.
    val provided = LocalCardSurface.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (raised) NestedCardRadius else CardRadius),
        color =
            when {
                provided.isSpecified -> provided
                raised -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainer
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
 * A **navigable** row of a [GroupedCard]: what the thing is, one line about it, and whatever the row
 * offers on its trailing edge.
 *
 * [FactRow]'s opposite number. A fact row is a label and a value the owner reads; this one is an
 * object they act on — a course, a reminder, a visit, a dose. 64dp so two lines of type sit
 * comfortably, which is what let the drawings put the name and the amount on one line ("Metacam ·
 * 0.3 ml") and bring the row down from a card.
 *
 * **The trailing slot is the whole grammar of these lists.** A row that is only *telling* you
 * something carries a [Chevron] into its own screen; a row that is *asking* you something carries
 * the answer instead, inline. Care & Meds draws both within one screen — a scheduled course chevrons
 * away, a dose due at eight o'clock offers *Given* and *Skipped* where the chevron would be.
 *
 * [enabled] is the third case and it is **not** the same as having no [onClick]: a row that is asking
 * a question is also unclickable, and dimming it would be wrong. This is for a row that would open
 * something if it could — More's *Photos* with no bunny to have photos of — where the title drops to
 * [MaterialTheme.colorScheme.onSurfaceVariant] and the [subtitle] is what says why. The two halves go
 * together: dimmed and mute is ADR-0001's silence.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // `then` rather than a nullable modifier: an unclickable row must not get a ripple
                // or a semantics node at all, which is ADR-0004's rule for the archived scope.
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                // 64dp buys room for two lines; a row with only a title does not need it, and `9c`
                // draws the short one at 56dp so it can sit in the same card as a [FactRow].
                .heightIn(min = if (subtitle == null) SingleLineRowHeight else ListRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * A [ListRow]'s minimum height. Two lines of type and a target, and the drawings are firm on it.
 *
 * Public because the vet directory needs the same floor without being a [ListRow]: its rows carry up
 * to four lines at three different weights, so they are drawn by hand and only borrow the height.
 */
val ListRowHeight = 64.dp

/**
 * A [ListRow] with **no subtitle**, and the floor a [FactRow] takes when it shares a card with one.
 *
 * `9c` is the first card in the app to mix the two — *Privacy policy* opens something so it carries a
 * [Chevron], *Version* does not so it carries a value — and it draws both at 56dp. Neither component's
 * own height fits: 64dp is room for a second line this row has not got, and [FactRow]'s 48dp is the
 * floor for a *column* of facts read as one block, which Home draws and this is not.
 *
 * Public because [FactRow] cannot infer it. A fact row has no way to know it is standing next to a
 * navigable one, so the caller says so — `Modifier.heightIn(min = SingleLineRowHeight)`, which wins
 * because the outer constraint is the larger of the two.
 */
val SingleLineRowHeight = 56.dp

/**
 * The trailing mark of a [ListRow] that opens something.
 *
 * Decorative on purpose: the row itself is the target and its own text is what a screen reader
 * announces, so describing the arrow too would only make it say the row twice.
 */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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
 * The 10dp apricot marker: **the app itself is raising this**, and nothing else in the language says
 * that.
 *
 * A dot rather than a fill, which is the correction the redesign made twice — `errorContainer`, then
 * `tertiaryContainer`, then this. A whole panel of colour asserts an urgency the sentence inside it
 * usually disclaims; a dot marks the card without arguing with its own copy. It stays away from
 * anything the *owner* recorded, which is [TagChip]'s hay (ADR-0001, ADR-0026).
 */
@Composable
fun CautionDot(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(CautionDotSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
    )
}

private val CautionDotSize = 10.dp

/**
 * A quiet card that says something is not going to work as well as it should — and offers the fix.
 *
 * [CautionDot], a title, one honest paragraph, and at most one action. Used for the states that
 * belong to **Android rather than to a rabbit**: notifications switched off, exact alarms not
 * permitted, a battery policy that will hold a reminder back (ADR-0003).
 *
 * It is [GroupedCard]'s `raised` level, which is the one-per-screen budget — so a route that draws
 * one of these draws nothing else at that level.
 *
 * The action is a text button pulled back to the card's text edge: a text button carries its own
 * padding, so one laid out flush looks indented against the paragraph above it.
 */
@Composable
fun CaveatCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    GroupedCard(modifier = modifier, raised = true, contentPadding = PaddingValues(Spacing.base)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CautionDot()
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Row(modifier = Modifier.offset(x = -Spacing.snug)) { action() }
            }
        }
    }
}

/**
 * A route with nothing on it, or a directory with nothing in it: **one sentence, in a card the size
 * of a row**.
 *
 * `3c`'s shape, and it is deliberately that small. What the card buys is that an empty route is the
 * same *class of object* as a full one, rather than loose text floating under the app bar. No
 * illustration — emptiness should not be the most prominent thing on a screen — and the sentence is
 * about the **record**, never about the rabbit (ADR-0001).
 *
 * [title] is `10a`'s addition, and it stays optional because most empty states do not want it. A
 * directory that offers a *way in* has two things to say — that there is nothing here, and what
 * would put something here — and running them together as one paragraph buries the second. The card
 * is still the size of a row; what changes is that its first line is the state and the rest is the
 * invitation. Where there is no way in — the archived scope (ADR-0004) — pass the sentence as [text]
 * alone and the card goes back to `3c`'s shape.
 *
 * [action] is for the case that is a question rather than an emptiness: there is data, it just
 * belongs to somebody the scope has not named yet.
 *
 * Distinct from Care & Meds' `EmptySection`, which is one *section* of a populated screen saying it
 * holds nothing and stays plain text so it cannot be mistaken for a row.
 */
@Composable
fun MessageCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    GroupedCard(modifier = modifier, contentPadding = PaddingValues(Spacing.base)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
            // The two lines are one statement, so they sit at [Spacing.hair] and the card's own
            // spacing only separates them from the action below.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action?.invoke()
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
