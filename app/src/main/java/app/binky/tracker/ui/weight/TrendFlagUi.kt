package app.binky.tracker.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
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
import app.binky.tracker.data.TrendChange
import app.binky.tracker.data.TrendDirection
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.Age
import app.binky.tracker.ui.bunny.ageOn
import app.binky.tracker.ui.common.BinkyDialog
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
 *
 * **Both directions read from here** (ADR-0028), which is the point of one composable: a gain swaps
 * two sentences and keeps the rest verbatim, so the register cannot drift between them. What it does
 * *not* swap is the framing — a gain gets no verdict either, only the numbers and their dates.
 */
@Composable
private fun TrendFlagBody(
    bunnyName: String,
    change: TrendChange,
    unit: WeightUnit,
    acknowledgedAt: Instant?,
) {
    val rose = change.direction == TrendDirection.Gain
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
                stringResource(
                    if (rose) R.string.trend_flag_rise else R.string.trend_flag_drop,
                    bunnyName,
                    gramsLabel(change.changeGrams),
                    instantDateLabel(change.baselineAt),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.trend_flag_readings,
                    weightLabel(change.baselineGrams, unit),
                    weightLabel(change.currentGrams, unit),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        // **Never on a gain** (ADR-0028): the caveat earns its place on a loss because a loss is
        // usually sudden, whereas a gain is *always* measured over four to eight months — and a
        // caveat that always fires is wallpaper.
        if (!rose && isLongGap(change.baselineAt, change.currentAt)) {
            gapLabel(change.baselineAt, change.currentAt)?.let { gap ->
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
            text = stringResource(if (rose) R.string.trend_flag_vet_gain else R.string.trend_flag_vet_diet),
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
 *
 * [onAskAge] is ADR-0028's age question, and it draws only when the card is a gain raised with no
 * usable birthday on file. Null where there is nowhere to send the owner — the watch-expiry prompt
 * is a dialog over whatever screen they were on, and the same card on Home carries the question a
 * moment later.
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
    onAskAge: (() -> Unit)? = null,
) {
    val change =
        when (flag) {
            is TrendFlag.WorthACloserLook -> flag.change
            is TrendFlag.Acknowledged -> flag.change
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
            TrendFlagBody(bunnyName, change, unit, acknowledgedAt)
            // Pulled back to the card's text edge: a text button carries its own padding, so a row
            // of them laid out flush looks indented against everything above it.
            //
            // **FlowRow and not Row**, which the first capture of the gain card settled: three
            // actions — acknowledge, the age question, *Start a watch* — do not fit one line on a
            // phone, and a `Row` does not clip the third, it crushes it to one character wide and
            // spells it down the card. Every locale after English makes this worse.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.offset(x = -Spacing.snug),
            ) {
                if (acknowledgedAt == null) {
                    TextButton(onClick = onAcknowledge) {
                        Text(stringResource(R.string.trend_flag_acknowledge))
                    }
                }
                AskAgeAction(bunnyName, change, onAskAge)
                secondaryAction?.invoke()
            }
        }
    }
}

/**
 * ADR-0028's age question — *"How old is Bijou?"* — and the half of the gain rule that stops an
 * unknown-age kit re-raising a caution dot after every weighing for months.
 *
 * It is **not** the app claiming to know anything: the flag has already fired on what it can see, and
 * this is the one tap that would let it judge properly. Answering once switches the growth gate on
 * permanently, which is why it leads to the bunny editor rather than to a prompt of its own.
 */
@Composable
private fun AskAgeAction(
    bunnyName: String,
    change: TrendChange,
    onAskAge: (() -> Unit)?,
) {
    if (!change.ageUnknown || onAskAge == null) return
    TextButton(onClick = onAskAge) {
        Text(stringResource(R.string.trend_flag_ask_age, bunnyName))
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
    change: TrendChange,
    unit: WeightUnit,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null,
    onAskAge: (() -> Unit)? = null,
) {
    BinkyDialog(
        title = stringResource(R.string.trend_flag_title),
        onDismiss = onDismiss,
        // FlowRow inside each slot for the same reason as the banner's: this dialog can carry four
        // actions at once, and Material's own button row cannot wrap what is inside a slot.
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                secondaryAction?.invoke()
                TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.trend_flag_acknowledge)) }
            }
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                // Beside *Close* rather than beside *I have seen this*: it leaves the screen, and
                // the dismiss side is where this dialog's leaving actions already live.
                AskAgeAction(bunnyName, change, onAskAge)
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.trend_flag_close)) }
            }
        },
    ) {
        TrendFlagBody(bunnyName, change, unit, acknowledgedAt = null)
    }
}
