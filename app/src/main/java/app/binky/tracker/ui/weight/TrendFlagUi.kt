package app.binky.tracker.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.TrendDrop
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.Age
import app.binky.tracker.ui.bunny.ageOn
import app.binky.tracker.ui.common.CautionDot
import app.binky.tracker.ui.common.GroupedCard
import java.time.Duration
import java.time.Instant

/**
 * **A month between the two readings being compared.**
 *
 * The trigger is interval-independent by design (ADR-0001) — an acute drop after a long gap fires
 * exactly as one between weekly weighings does, and damping by elapsed time is the one thing that
 * must never silence this signal. But *reporting* it identically would let "down 240 g since 3 June"
 * read in December as though it happened this week. The gap is therefore named in the copy and never
 * used to suppress anything.
 *
 * A month, because the app's own weigh-in preset is weekly (Phase 4): a month between the current
 * reading and its baseline means at least three missed weigh-ins, which is where "since" stops
 * implying "recently".
 */
internal const val LONG_GAP_DAYS = 30L

/**
 * Is the drop being reported across a long stretch? Pure, so the boundary is pinned by a unit test
 * rather than discovered on a phone.
 */
fun isLongGap(
    baselineAt: Instant,
    currentAt: Instant,
): Boolean = Duration.between(baselineAt, currentAt).toDays() >= LONG_GAP_DAYS

/**
 * The trend flag's copy — **the one place it is written**, rendered by all three of its hosts: the
 * dialog straight after a weight write, the banner on the Weight screen, and the banner on Home's
 * vitals card. One composable, so the sentence an owner reads cannot depend on where they read it.
 *
 * Grams, dated, framed *"worth a closer look"* and **never** as a diagnosis; the long-gap framing
 * when the gap warrants it; the vet-diet line, which ADR-0001 names as an accepted limitation the
 * copy owns rather than something to engineer around. **No notification** — this signal never
 * interrupts (ADR-0001).
 */
@Composable
private fun TrendFlagBody(
    bunnyName: String,
    drop: TrendDrop,
    unit: WeightUnit,
    acknowledgedAt: Instant?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
                stringResource(
                    R.string.trend_flag_drop,
                    bunnyName,
                    gramsLabel(drop.dropGrams),
                    instantDateLabel(drop.baselineAt),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.trend_flag_readings,
                    weightLabel(drop.baselineGrams, unit),
                    weightLabel(drop.currentGrams, unit),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isLongGap(drop.baselineAt, drop.currentAt)) {
            gapLabel(drop.baselineAt, drop.currentAt)?.let { gap ->
                Text(
                    text = stringResource(R.string.trend_flag_long_gap, gap),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = stringResource(R.string.trend_flag_not_advice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.trend_flag_vet_diet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (acknowledgedAt != null) {
            Text(
                text = stringResource(R.string.trend_flag_acknowledged, instantDateLabel(acknowledgedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How far apart the two readings are, in the one unit worth saying — reusing [ageOn], which is the
 * same calendar arithmetic and already has its own tests. Null when the two land on the same day,
 * which the long-gap check has already excluded.
 */
@Composable
private fun gapLabel(
    from: Instant,
    to: Instant,
): String? =
    when (val gap = ageOn(from.toLocalDateHere(), to.toLocalDateHere())) {
        null -> null
        is Age.Years -> pluralStringResource(R.plurals.gap_years, gap.years, gap.years)
        is Age.Months -> pluralStringResource(R.plurals.gap_months, gap.months, gap.months)
        is Age.Weeks -> pluralStringResource(R.plurals.gap_weeks, gap.weeks, gap.weeks)
    }

/**
 * Whether [TrendFlagBanner] will draw anything at all.
 *
 * Exported because a host has to know *before* laying out: Home puts 32dp between the hero and the
 * flag, and a spacer emitted for a banner that renders nothing is a hole in the screen that only
 * appears for bunnies with no flag — which is most of them. One rule, read here and applied inside
 * the banner, so the two cannot drift apart.
 */
fun TrendFlag?.showsBanner(): Boolean = this is TrendFlag.WorthACloserLook || this is TrendFlag.Acknowledged

/**
 * The flag as a banner, above the history on the Weight screen and inside Home's vitals card.
 *
 * Renders for both live variants and nothing for the rest: [TrendFlag.Steady] and
 * [TrendFlag.NotEnoughHistory] are **not** rendered as reassurance, because absence of a flag is
 * never evidence of health (ADR-0001).
 *
 * **A quiet card with an apricot marker, not an apricot card.** The fill used to be
 * `tertiaryContainer` — itself a correction of an `errorContainer` that read as an alarm — and the
 * design takes the step the first fix stopped short of: this is the same surface as every other
 * card, and the caution arrives as a 10dp dot beside the title. A whole panel of colour states
 * urgency the sentence underneath it explicitly disclaims, which is what ADR-0026 and ADR-0001 both
 * rule out. The apricot that remains on screen is the active watch directly beneath, where a filled
 * row is telling the owner something *is running* rather than something is wrong.
 *
 * [nested] is for Home's all-bunnies list, where this sits inside a bunny's own card.
 *
 * [secondaryAction] is the slot Phase 4 fills with *Start a watch* (ADR-0001) — built now so that
 * arrives as a caller passing a button, not as a rewrite of the composable every host renders.
 */
@Composable
fun TrendFlagBanner(
    bunnyName: String,
    flag: TrendFlag?,
    unit: WeightUnit,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
    nested: Boolean = false,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val drop =
        when (flag) {
            is TrendFlag.WorthACloserLook -> flag.drop
            is TrendFlag.Acknowledged -> flag.drop
            else -> return
        }
    val acknowledgedAt = (flag as? TrendFlag.Acknowledged)?.acknowledgedAt

    GroupedCard(
        modifier = modifier,
        raised = nested,
        contentPadding = PaddingValues(Spacing.base),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The marker, and the only saturated thing on the card. Absent once the episode has
                // been acknowledged: the drop is still real and still reported, but it has stopped
                // being something the owner has not yet seen, which is all this dot ever said.
                if (acknowledgedAt == null) CautionDot()
                Text(
                    text = stringResource(R.string.trend_flag_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TrendFlagBody(bunnyName, drop, unit, acknowledgedAt)
            // Pulled back to the card's text edge: a text button carries its own padding, so a row
            // of them laid out flush looks indented against everything above it.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.offset(x = -Spacing.snug),
            ) {
                if (acknowledgedAt == null) {
                    TextButton(onClick = onAcknowledge) {
                        Text(stringResource(R.string.trend_flag_acknowledge))
                    }
                }
                secondaryAction?.invoke()
            }
        }
    }
}

/**
 * The same copy as a dialog, shown **straight after a weight write** when the flag is visible and
 * unacknowledged.
 *
 * It fires on edits and deletes as well as inserts, because correcting a *baseline* weight can
 * deepen the drop without the current reading moving at all (ADR-0001).
 *
 * **Dismissing is explicitly not acknowledging**: the acknowledgment watermark is a deliberate act
 * that suppresses the flag until it deepens past the re-raise bar, and inferring it from a tap
 * outside a dialog would suppress the app's one safety signal by accident.
 */
@Composable
fun TrendFlagDialog(
    bunnyName: String,
    drop: TrendDrop,
    unit: WeightUnit,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trend_flag_title)) },
        text = { TrendFlagBody(bunnyName, drop, unit, acknowledgedAt = null) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondaryAction?.invoke()
                TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.trend_flag_acknowledge)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.trend_flag_close)) }
        },
    )
}
