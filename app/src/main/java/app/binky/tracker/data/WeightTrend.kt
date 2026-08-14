package app.binky.tracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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

/**
 * **The gain trigger: 10 % above the anchor** (ADR-0028). Unlike the 5 % above, this figure is not
 * borrowed — there is no published equivalent for gain — but derived: the PFMA/RWAF five-point body
 * condition scale states its bands in percentages (*thin* is 10–20 % below ideal), so **one
 * condition step is about 10 % of body weight**. Ten per cent over six months is one condition step
 * in half a year, which is a unit a vet would recognise.
 */
private const val GAIN_TRIGGER_FRACTION = 0.10

/**
 * **The anchor: the weighing nearest six months back, accepted only inside 4–8 months** (ADR-0028).
 * No reading in that window is **no claim**, which is not the same as saying the bunny is fine
 * (ADR-0001).
 *
 * In days rather than calendar months, deliberately: the window is four months wide, so nothing it
 * decides can turn on a leap year or on the length of February, and days keep this whole file free
 * of a time zone — an `Instant` cannot be given a calendar month without one. 183 is half of 366.
 */
private const val ANCHOR_TARGET_DAYS = 183L
private const val ANCHOR_MIN_DAYS = 122L
private const val ANCHOR_MAX_DAYS = 244L

/**
 * **The growth gate: a bunny under a year old is silent** (ADR-0028). A kit at four months might be
 * 1.8 kg and at ten months 2.6 kg — **+44 % over six months, entirely healthy** — so any gain rule
 * fires continuously on a growing rabbit and is arithmetically correct every time.
 */
private const val GROWTH_GATE_MONTHS = 12L

/**
 * Whether a bunny is old enough for a gain to mean what ADR-0028 says it means.
 *
 * **Three states rather than a `Boolean`, and [Unknown] is the load-bearing one.** `birthDate` is
 * nullable and commonly absent — rescues arrive with no known age, and the app's own sample bunny
 * has none — so the guard is unavailable exactly where it is most needed. Reading "no birthday" as
 * "adult" would raise a health signal on the strength of an absent field, which is the move ADR-0001
 * exists to ban; staying silent for every rescue would delete the feature for a large share of the
 * app's users. So the app does neither: on [Unknown] the flag fires **and the card asks how old the
 * bunny is**.
 *
 * Kotlin note: an `enum class` here rather than a `sealed interface` because no case carries data —
 * the same distinction `TrendFlag` draws below, where the variants do.
 */
enum class GrowthStage {
    /** Under [GROWTH_GATE_MONTHS] months old, so a gain is growth and raises nothing. */
    Growing,

    /** Old enough that a gain is not growth. */
    Grown,

    /** No usable birthday. The flag fires and the card asks — see the class doc. */
    Unknown,
}

/**
 * The growth gate for a birthday, on a given day. Pure and JVM-testable, like `ageOn`'s arithmetic
 * over in `ui/bunny`: the part that is easy to get subtly wrong is the day before a birthday, and
 * none of it needs Android.
 *
 * A birthday **in the future** is a typo in a date picker, and it resolves to [GrowthStage.Unknown]
 * rather than to a silently growing bunny — the app asks, which is also how a typo gets corrected.
 */
fun growthStageOn(
    birthDate: LocalDate?,
    on: LocalDate,
): GrowthStage =
    when {
        birthDate == null || birthDate.isAfter(on) -> GrowthStage.Unknown
        // Calendar-aware, so a bunny born 29 February turns one on 28 February in a non-leap year
        // rather than drifting by a day — the same reason `ageOn` uses `Period`.
        Period.between(birthDate, on).toTotalMonths() < GROWTH_GATE_MONTHS -> GrowthStage.Growing
        else -> GrowthStage.Grown
    }

/** [growthStageOn] against today, which is what every caller outside the tests wants. */
fun growthStageNow(birthDate: LocalDate?): GrowthStage = growthStageOn(birthDate, LocalDate.now())

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
    /**
     * Everything before [current], in the stated total order. The loss baseline reads the first
     * three; the gain anchor reaches much further back, which is why the whole tail is carried.
     */
    val priors: List<Weighing>,
)

private fun trendWindow(series: List<Weighing>): TrendWindow? {
    val ordered = series.inTrendOrder()
    val current = ordered.firstOrNull() ?: return null
    val priors = ordered.drop(1)
    val baseline = baselineWeighing(priors) ?: return null
    return TrendWindow(current, baseline, priors)
}

/**
 * **The loss trigger, and it is a level one** — it compares the latest reading against its trailing
 * baseline and asks nothing about rate, so it is **interval-independent**: an acute drop after a
 * long gap fires exactly as an acute drop between weekly weighings does. Damping by elapsed time is
 * the one thing ADR-0001 says must never silence this signal.
 *
 * The gain trigger below is the deliberate opposite — **time-anchored where this one is
 * count-anchored** — because a gain only means anything per unit time (ADR-0028 makes that argument
 * at length, and scopes ADR-0001's interval-independence to loss rather than contradicting it).
 */
private fun TrendWindow.dropTriggerHolds(): Boolean {
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
 * **The anchor: the single weighing nearest six months before the current reading**, or null when
 * there is none inside the 4–8 month window — which is *no claim*, not a clean bill of health.
 *
 * A single reading rather than a median of a bucket around the anchor, which ADR-0028 considered and
 * rejected: this is the version a person can hold in their head and the card can explain by naming
 * its date. **The cost is a real silent failure** — one high reading at the anchor, the bunny weighed
 * in its carrier or straight after a big meal, suppresses the flag entirely and says nothing about
 * having done so. A unit test pins that case rather than engineering around it, in ADR-0021's own
 * tradition.
 *
 * Kotlin note: `minByOrNull` is stable, so two readings exactly equidistant from the target resolve
 * to whichever comes first in the total order — the more recent one. Arbitrary but deterministic,
 * which is the property that matters here as it does in [baselineWeighing].
 */
private fun TrendWindow.anchorWeighing(): Weighing? =
    priors
        .filter { daysBefore(it) in ANCHOR_MIN_DAYS..ANCHOR_MAX_DAYS }
        .minByOrNull { abs(daysBefore(it) - ANCHOR_TARGET_DAYS) }

private fun TrendWindow.daysBefore(prior: Weighing): Long =
    ChronoUnit.DAYS.between(prior.recordedAt, current.recordedAt)

/** The gain trigger: at least [GAIN_TRIGGER_FRACTION] above the anchor, unrounded as the loss one is. */
private fun TrendWindow.riseTriggerHolds(anchor: Weighing): Boolean =
    current.grams >= anchor.grams + anchor.grams * GAIN_TRIGGER_FRACTION

/**
 * What the series says right now, or null for no claim at all.
 *
 * **Loss takes precedence** (ADR-0028): both triggers can hold at once — a bunny that gained 800 g
 * over five months and then dropped 150 g last week trips both — and the card should say the most
 * urgent true thing. Nothing is lost, only deferred: when the drop resolves, the gain appears if it
 * still holds. That precedence is also what keeps one acknowledgment row enough, and the schema at 6.
 */
private fun TrendWindow.change(growth: GrowthStage): TrendChange? {
    if (dropTriggerHolds()) {
        return TrendChange(
            direction = TrendDirection.Loss,
            currentGrams = current.grams,
            currentAt = current.recordedAt,
            baselineGrams = baseline.grams,
            baselineAt = baseline.recordedAt,
        )
    }
    if (growth == GrowthStage.Growing) return null
    val anchor = anchorWeighing()?.takeIf { riseTriggerHolds(it) } ?: return null
    return TrendChange(
        direction = TrendDirection.Gain,
        currentGrams = current.grams,
        currentAt = current.recordedAt,
        baselineGrams = anchor.grams,
        baselineAt = anchor.recordedAt,
        ageUnknown = growth == GrowthStage.Unknown,
    )
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
 * Which way the number moved. **One flag, two rules** (ADR-0028): the card, the dot and the
 * acknowledgment are the same either way, because a second visual vocabulary for the same fact would
 * be a second thing for the owner to learn about one number.
 */
enum class TrendDirection {
    Loss,
    Gain,
}

/**
 * A change, in the terms the flag's copy uses. Framed "worth a closer look" and **never** as a
 * diagnosis; [changeGrams] is always grams, because `−0.04 kg` hides the signal `−40 g` makes
 * obvious.
 *
 * [baselineGrams] and [baselineAt] are **what the current reading is judged against**, and which
 * reading that is depends on the direction: the trailing median of three priors for a loss
 * (ADR-0021), the single weighing nearest six months back for a gain (ADR-0028). Both are real
 * readings with real dates, which is what lets the card say *"up 500 g since 12 February"* rather
 * than claiming a span it never measured.
 */
data class TrendChange(
    val direction: TrendDirection,
    val currentGrams: Int,
    val currentAt: Instant,
    val baselineGrams: Int,
    val baselineAt: Instant,
    /**
     * A gain judged with no usable birthday on file, so the card asks how old the bunny is. Never
     * true for a loss: the loss rule has never cared how old the bunny is.
     *
     * It matters more than it looks. The watermark re-raises when a later reading breaks through it
     * by more than the noise floor, and a growing kit clears 20 g between almost any two weighings —
     * so without the question an unknown-age kit would raise a caution dot after **every** weighing
     * for months, with no way to silence it. The action is what ends that loop (ADR-0028).
     */
    val ageUnknown: Boolean = false,
) {
    /** Always positive: the direction is carried by [direction], never by the sign of the number. */
    val changeGrams: Int get() = abs(currentGrams - baselineGrams)
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

    /** A trigger holds and nothing is silencing it. This is the flag the owner sees. */
    data class WorthACloserLook(
        val change: TrendChange,
    ) : TrendFlag

    /**
     * The trigger still holds, but the owner has seen this episode and it has not moved past the
     * re-raise bar. The change is still real, so it is still reported — quietly, as standing
     * information rather than as a fresh signal.
     */
    data class Acknowledged(
        val change: TrendChange,
        val acknowledgedAt: Instant,
    ) : TrendFlag
}

/**
 * The flag, plus one thing the caller may need to act on.
 *
 * [watermarkIsStale] is a **report, not an action**: this function is pure and *derived on read*
 * must not come to mean *writes on read* — under "All bunnies" every vitals card evaluates the flag,
 * and N cards would race to delete the same row (ADR-0001). `WeightRepository`'s write paths own the
 * discard, and they act on exactly this field, so the two cannot disagree about what a stale
 * watermark is. On a database written only through the repository it is `false` by the time anything
 * reads it; it is here so that an inconsistency is *stated* rather than silently absorbed into the
 * flag.
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
    growth: GrowthStage,
): TrendEvaluation {
    val window =
        trendWindow(series)
            ?: return TrendEvaluation(TrendFlag.NotEnoughHistory, watermarkIsStale = acknowledgment != null)

    // **A gain is judged inside the loss window's gate, not beside it.** Two readings six months
    // apart are arithmetically enough for the gain rule, which needs only an anchor and a current
    // reading — but ADR-0021's "two points do not describe a trend" is a statement about how thin a
    // record the app is willing to speak from at all, and it is not weaker for a rise. So a bunny
    // with one prior stays `NotEnoughHistory` in both directions. Pinned by a test.
    val change =
        window.change(growth)
            // No trigger holds, so the episode is over. Because the flag clears exactly when the
            // trigger does, "no trigger is true" *is* "the episode ended" — any watermark still on
            // disk is stale, and keeping it would let a months-old acknowledgment silence a
            // genuinely new drop later.
            ?: return TrendEvaluation(TrendFlag.Steady, watermarkIsStale = acknowledgment != null)

    if (acknowledgment == null) return TrendEvaluation(TrendFlag.WorthACloserLook(change), watermarkIsStale = false)

    // **The direction-flip discard** (ADR-0028). The watermark stores grams and no direction, so the
    // direction it was taken for is read back off the number itself: an acknowledged loss sat below
    // its baseline, an acknowledged gain sat above its anchor. A watermark on the wrong side of what
    // this reading is judged against was taken for the other direction, and it is discarded rather
    // than allowed to judge this one — otherwise a loss would be silenced by grams acknowledged for
    // a gain. Direction is re-derivable like this on every read, which is why it costs no column.
    val watermarkFitsDirection =
        when (change.direction) {
            TrendDirection.Loss -> acknowledgment.grams < change.baselineGrams
            TrendDirection.Gain -> acknowledgment.grams > change.baselineGrams
        }
    if (!watermarkFitsDirection) {
        return TrendEvaluation(TrendFlag.WorthACloserLook(change), watermarkIsStale = true)
    }

    // **The re-raise bar** (ADR-0001): a later reading breaks back through when it moves past the
    // watermark by *more than* the gram noise floor — strictly more, as "by more than" says. This is
    // a tighter bar than either trigger on purpose, because a bunny already flagged *and*
    // acknowledged must not be allowed to slide a further 5 % in silence. This is the floor's
    // genuinely load-bearing role; in the triggers above it is almost always inert.
    val floor = noiseFloorGrams(change.baselineGrams)
    val reRaises =
        when (change.direction) {
            TrendDirection.Loss -> window.current.grams < acknowledgment.grams - floor
            TrendDirection.Gain -> window.current.grams > acknowledgment.grams + floor
        }

    return TrendEvaluation(
        flag =
            if (reRaises) {
                TrendFlag.WorthACloserLook(change)
            } else {
                TrendFlag.Acknowledged(change, acknowledgment.acknowledgedAt)
            },
        watermarkIsStale = false,
    )
}
