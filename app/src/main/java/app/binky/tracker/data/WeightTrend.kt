package app.binky.tracker.data

import java.time.Instant

/**
 * One weighing as the trend math sees it — deliberately **not** [WeightEntity]. Keeping Room out of
 * this file is what lets the trend tests be plain arithmetic on a table of cases rather than an
 * instrumented test with a database behind it.
 *
 * Carries [createdAt] and [id] because the total order below needs them, not because the arithmetic
 * does.
 */
data class Weighing(
    val id: String,
    val grams: Int,
    val recordedAt: Instant,
    val createdAt: Instant,
)

fun WeightEntity.toWeighing() = Weighing(id, grams, recordedAt, createdAt)

/**
 * **The trigger: 5 % below baseline** (ADR-0001). The figure rabbit-welfare guidance generally
 * treats as significant, and defensible without a specialist in the room.
 */
private const val TRIGGER_FRACTION = 0.05

/**
 * **The noise floor: `max(20 g, 2 % of baseline)`** (ADR-0001) — proportional rather than flat,
 * because the app serves a 1.1 kg Netherland dwarf and a 6.5 kg Flemish giant, a 6× range over
 * which day-to-day gut and bladder variation scales the same way. The 20 g absolute stops the floor
 * collapsing to noise on the very smallest.
 */
private const val NOISE_FLOOR_GRAMS = 20
private const val NOISE_FLOOR_FRACTION = 0.02

/**
 * How many priors the baseline looks at, and the minimum it needs to say anything at all
 * (ADR-0021). Two points do not describe a trend, so a bunny's first two weighings can never raise
 * a flag — accepted deliberately.
 */
private const val BASELINE_PRIORS = 3
private const val MINIMUM_PRIORS = 2

/** Named rather than inlined because [evaluateTrend]'s re-raise bar reuses it — see there. */
fun noiseFloorGrams(baselineGrams: Int): Double =
    maxOf(NOISE_FLOOR_GRAMS.toDouble(), baselineGrams * NOISE_FLOOR_FRACTION)

/**
 * **The stated total order: `recordedAt` desc, then `createdAt` desc, then `id`** (ADR-0021).
 *
 * `recordedAt` alone is not a total order — a minute-granularity picker, two entries in one session
 * and the sample-data seeder all produce ties — and without a stated rule the baseline would depend
 * silently on SQLite's row order. `WeightDao` returns the series already in this order; this sorts
 * anyway, because a pure function that silently required its caller to have sorted correctly would
 * be a stub wearing the tests' name.
 *
 * Kotlin note: `compareByDescending { … }.thenByDescending { … }` builds a `Comparator` by
 * composition — the equivalent of chaining fallbacks inside a JS `sort` callback, but each key is
 * written once and the direction is stated per key.
 */
internal fun List<Weighing>.inTrendOrder(): List<Weighing> =
    sortedWith(
        compareByDescending<Weighing> { it.recordedAt }
            .thenByDescending { it.createdAt }
            .thenBy { it.id },
    )

/**
 * The baseline: **the median of the 3 weighings prior to the current reading**, always excluding
 * the current reading so a real drop cannot dilute its own signal (ADR-0021). Null when there are
 * fewer than [MINIMUM_PRIORS], which is the flag's firing gate.
 *
 * **Median, never mean**: one fat-fingered entry must not drag the baseline, and a `250` typed for
 * `2500` is a normal event that the mean hands a third of the estimator's weight.
 *
 * **At exactly two priors it is the higher of the two, not their median.** The median of an even set
 * *is* the mean of the middle pair, so at two priors the "median" would silently become the most
 * outlier-sensitive estimator in the scheme, in the very window where the flag first switches on —
 * and it fails silent: priors of `2500, 250` average to 1375 g, which puts a healthy bunny
 * permanently "above baseline" and suppresses every later drop.
 *
 * **The obvious fix is forbidden: do not ignore priors older than N days.** A trailing median lags a
 * level shift by one reading, so an unlogged rise followed by a drop is silent once — see the
 * blind-spot test. Discarding stale priors would break interval-independence outright, and if all
 * three are stale and get discarded there is no baseline at all, so the app goes *silent* on an
 * acute drop after a long gap: the single pattern ADR-0001 says must never be dampened into silence.
 * The blind window is one reading and self-heals on the next; that is why it is accepted.
 *
 * Returns the prior **weighing** rather than a bare number, because the flag's copy is dated — "down
 * 240 g since 3 June" — and the baseline is always one of the readings, never a computed average, so
 * that date exists and costs nothing to carry.
 *
 * [priors] must already be in [inTrendOrder]; only the first [BASELINE_PRIORS] are considered.
 */
internal fun baselineWeighing(priors: List<Weighing>): Weighing? {
    val window = priors.take(BASELINE_PRIORS)
    return when {
        window.size < MINIMUM_PRIORS -> null
        // Kotlin note: both `maxBy` and `sortedBy` are stable on equal keys, so two priors of the
        // same weight resolve to whichever comes first in the total order — the more recent reading.
        // Arbitrary but deterministic, which is the property that matters.
        window.size == MINIMUM_PRIORS -> window.maxBy { it.grams }
        else -> window.sortedBy { it.grams }[1]
    }
}

/**
 * The current reading and the baseline it is judged against — null when no claim can be made at all,
 * which is an empty series or fewer than [MINIMUM_PRIORS] priors.
 *
 * Extracted so the raw predicate and the full evaluation below cannot drift apart in how they window
 * the series. ADR-0021 puts that windowing in exactly one place; this is the place.
 */
private data class TrendWindow(
    val current: Weighing,
    val baseline: Weighing,
)

private fun trendWindow(series: List<Weighing>): TrendWindow? {
    val ordered = series.inTrendOrder()
    val current = ordered.firstOrNull() ?: return null
    val baseline = baselineWeighing(ordered.drop(1)) ?: return null
    return TrendWindow(current, baseline)
}

/**
 * Is the raw drop trigger true for [series] right now?
 *
 * This is the **level** trigger — it compares the latest reading against its trailing baseline and
 * asks nothing about rate, so it is **interval-independent**: an acute drop after a long gap fires
 * exactly as an acute drop between weekly weighings does. Damping by elapsed time is the one thing
 * ADR-0001 says must never silence this signal.
 *
 * "Raw" means it knows nothing of acknowledgment. That is what `WeightRepository`'s write paths
 * need: *a stored acknowledgment row implies the raw trigger was true as of the last weight write*
 * is the invariant that lets the read path stay a pure function with no history walk (ADR-0001).
 * [evaluateTrend] wraps this predicate with what the owner actually sees — the re-raise bar, the
 * auto-clear and the drop's numbers.
 */
fun trendTriggerHolds(series: List<Weighing>): Boolean = trendWindow(series)?.triggerHolds() ?: false

private fun TrendWindow.triggerHolds(): Boolean {
    // Written as ADR-0001 writes it, `max(5 % of baseline, noise floor)`, and **the max stays**.
    // 2 % is always less than 5 %, so the floor's inner max can only ever resolve to the 20 g
    // absolute — and 20 g exceeds 5 % of baseline only below a 400 g baseline, i.e. a four-week-old
    // kit. Across the whole 1.1 kg – 6.5 kg range this app sets out to serve the floor never binds
    // here and the 5 % does all the work. It is kept as a deliberate juvenile guard: on a 300 g kit
    // 5 % is 15 g, inside real scale noise. A unit test pins that case, so do not "simplify" this
    // to the 5 % alone.
    val threshold = maxOf(baseline.grams * TRIGGER_FRACTION, noiseFloorGrams(baseline.grams))

    // No rounding, so the boundary is exactly where the arithmetic puts it and the tests can pin it.
    return current.grams <= baseline.grams - threshold
}

/**
 * The acknowledgment watermark as the trend math sees it — the plain-data mirror of
 * [TrendAcknowledgmentEntity], for the same reason [Weighing] mirrors [WeightEntity].
 *
 * It carries the **number**, not the row it came from: the watermark has to remember the weight it
 * was taken against, because an edit may since have moved that row (ADR-0001).
 */
data class TrendAcknowledgment(
    val grams: Int,
    val acknowledgedAt: Instant,
)

fun TrendAcknowledgmentEntity.toAcknowledgment() = TrendAcknowledgment(grams, acknowledgedAt)

/**
 * A drop, in the terms the flag's copy uses. Framed "worth a closer look" and **never** as a
 * diagnosis; [dropGrams] is always grams, because `−0.04 kg` hides the signal `−40 g` makes obvious.
 */
data class TrendDrop(
    val currentGrams: Int,
    val currentAt: Instant,
    val baselineGrams: Int,
    val baselineAt: Instant,
) {
    val dropGrams: Int get() = baselineGrams - currentGrams
}

/**
 * What the trend says about a bunny right now.
 *
 * Kotlin note: a `sealed interface` is a closed set of variants — TypeScript's discriminated union,
 * with the compiler's exhaustiveness check on `when` in place of a `never` fallthrough. `data object`
 * is the singleton case, used where the variant carries no data of its own.
 *
 * There is deliberately **no "was this a re-raise" variant**: a flag that broke back through the
 * watermark says exactly what a first one says, and splitting it would invite copy that reads as a
 * judgement about how bad things are getting.
 */
sealed interface TrendFlag {
    /**
     * Fewer than two priors, so no claim is possible — a bunny's first two weighings can never raise
     * a flag (ADR-0021). **Not** a statement that the bunny is fine: absence of a flag is never
     * evidence of health (ADR-0001).
     */
    data object NotEnoughHistory : TrendFlag

    /** History enough to judge, and the trigger is false. */
    data object Steady : TrendFlag

    /** The trigger holds and nothing is silencing it. This is the flag the owner sees. */
    data class WorthACloserLook(
        val drop: TrendDrop,
    ) : TrendFlag

    /**
     * The trigger still holds, but the owner has seen this episode and it has not deepened past the
     * re-raise bar. The drop is still real, so it is still reported — quietly, as standing
     * information rather than as a fresh signal.
     */
    data class Acknowledged(
        val drop: TrendDrop,
        val acknowledgedAt: Instant,
    ) : TrendFlag
}

/**
 * The flag, plus one thing the caller may need to act on.
 *
 * [watermarkIsStale] is a **report, not an action**: this function is pure and *derived on read*
 * must not come to mean *writes on read* — under "All bunnies" every vitals card evaluates the flag,
 * and N cards would race to delete the same row (ADR-0001). `WeightRepository`'s write paths own the
 * discard, and they already do it with [trendTriggerHolds], which is the same predicate this field
 * is computed from. So on a database written only through the repository this is always `false`; it
 * is here so that an inconsistency is *stated* rather than silently absorbed into the flag.
 */
data class TrendEvaluation(
    val flag: TrendFlag,
    val watermarkIsStale: Boolean,
)

/**
 * Evaluate the trend for a bunny's **whole series** plus its current acknowledgment.
 *
 * Present-tense and derived: it looks only at the latest weighing and its trailing baseline, so a
 * back-dated insert recomputes the *current* flag but can never resurrect a flag for a past moment
 * the bunny has since recovered from — a dip already recovered from is not news (ADR-0001).
 *
 * It never walks history, which is what the repository's invariant buys: *a stored acknowledgment
 * row implies the raw trigger was true as of the last weight write*. Given only `(series,
 * acknowledgment)` this function cannot tell that a trigger went false and came back — that would be
 * exactly the history audit ADR-0001 rejects — so it trusts the invariant and reports staleness when
 * it can see the invariant broken.
 *
 * Callers must **not** evaluate this for an archived bunny: the flag is not evaluated at all in that
 * scope, not merely hidden (ADR-0001, ADR-0004).
 */
fun evaluateTrend(
    series: List<Weighing>,
    acknowledgment: TrendAcknowledgment?,
): TrendEvaluation {
    val window =
        trendWindow(series)
            ?: return TrendEvaluation(TrendFlag.NotEnoughHistory, watermarkIsStale = acknowledgment != null)

    if (!window.triggerHolds()) {
        // The trigger is false, so the episode is over. Because the flag clears exactly when the
        // trigger does, "the trigger is false" *is* "the episode ended" — any watermark still on
        // disk is stale, and keeping it would let a months-old acknowledgment silence a genuinely
        // new drop later.
        return TrendEvaluation(TrendFlag.Steady, watermarkIsStale = acknowledgment != null)
    }

    val drop =
        TrendDrop(
            currentGrams = window.current.grams,
            currentAt = window.current.recordedAt,
            baselineGrams = window.baseline.grams,
            baselineAt = window.baseline.recordedAt,
        )

    if (acknowledgment == null) return TrendEvaluation(TrendFlag.WorthACloserLook(drop), watermarkIsStale = false)

    // **The re-raise bar** (ADR-0001): a later reading breaks back through when it falls below the
    // watermark by *more than* the gram noise floor — strictly more, as "by more than" says. This is
    // a tighter bar than the 5 % trigger on purpose, because a bunny already flagged *and*
    // acknowledged must not be allowed to slide a further 5 % in silence. This is the floor's
    // genuinely load-bearing role; in the trigger above it is almost always inert.
    val reRaises = window.current.grams < acknowledgment.grams - noiseFloorGrams(window.baseline.grams)

    return TrendEvaluation(
        flag =
            if (reRaises) {
                TrendFlag.WorthACloserLook(drop)
            } else {
                TrendFlag.Acknowledged(drop, acknowledgment.acknowledgedAt)
            },
        watermarkIsStale = false,
    )
}
