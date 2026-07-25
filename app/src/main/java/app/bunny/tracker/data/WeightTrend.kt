package app.bunny.tracker.data

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

/** See [noiseFloorGrams]; kept as a named function because the re-raise bar in 2b reuses it. */
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
 * [priors] must already be in [inTrendOrder]; only the first [BASELINE_PRIORS] are considered.
 */
internal fun baselineGrams(priors: List<Weighing>): Int? {
    val window = priors.take(BASELINE_PRIORS).map { it.grams }
    return when {
        window.size < MINIMUM_PRIORS -> null
        window.size == MINIMUM_PRIORS -> window.max()
        else -> window.sorted()[1]
    }
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
 * Checkpoint 2b grows the acknowledgment-aware evaluation — the re-raise bar, the auto-clear, the
 * copy the owner sees — around this predicate.
 */
fun trendTriggerHolds(series: List<Weighing>): Boolean {
    val ordered = series.inTrendOrder()
    val current = ordered.firstOrNull() ?: return false
    val baseline = baselineGrams(ordered.drop(1)) ?: return false

    // Written as ADR-0001 writes it, `max(5 % of baseline, noise floor)`, and **the max stays**.
    // 2 % is always less than 5 %, so the floor's inner max can only ever resolve to the 20 g
    // absolute — and 20 g exceeds 5 % of baseline only below a 400 g baseline, i.e. a four-week-old
    // kit. Across the whole 1.1 kg – 6.5 kg range this app sets out to serve the floor never binds
    // here and the 5 % does all the work. It is kept as a deliberate juvenile guard: on a 300 g kit
    // 5 % is 15 g, inside real scale noise. A unit test pins that case, so do not "simplify" this
    // to the 5 % alone.
    val threshold = maxOf(baseline * TRIGGER_FRACTION, noiseFloorGrams(baseline))

    // No rounding, so the boundary is exactly where the arithmetic puts it and the tests can pin it.
    return current.grams <= baseline - threshold
}
