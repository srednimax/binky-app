package app.binky.tracker.ui.weight

import java.time.Duration
import java.time.Instant

/**
 * The chart's window — **display-only** (ADR-0022).
 *
 * It changes what is drawn and nothing else. The trend flag is always evaluated over the *whole*
 * series and never over the list this produces, which is what stops the two from drifting apart.
 * The consequence is deliberate and must not be "fixed": the flag can render above an empty chart.
 *
 * Kotlin note: an enum may carry constructor properties, so the window length rides along with the
 * constant instead of living in a separate lookup — the nearest JS equivalent is a frozen object
 * keyed by a string union, except the compiler proves the `when` over it is exhaustive.
 *
 * [days] is null for [ALL], which has no cutoff at all. A year is 365 days rather than a calendar
 * year: this is a viewing window, not an anniversary, and keeping it a [Duration] keeps this file
 * free of time zones and therefore trivial to test.
 */
enum class WeightChartRange(
    val days: Long?,
) {
    DAYS_30(30),
    DAYS_90(90),
    YEAR(365),
    ALL(null),
    ;

    /** The oldest instant still inside the window, or null when the window is unbounded. */
    fun cutoff(now: Instant): Instant? = days?.let { now.minus(Duration.ofDays(it)) }
}

/** One plotted weighing. Deliberately not [WeightRow] — the chart has no use for a row's delta. */
data class WeightChartPoint(
    val recordedAt: Instant,
    val grams: Int,
)

/**
 * What the chart area shows: a line, or one of ADR-0022's **three** empty states.
 *
 * Kotlin note: a sealed interface is a discriminated union — the subtypes are known at compile
 * time, so a `when` over it needs no `else` branch and adding a fourth state breaks every consumer
 * that has not handled it. The states carry different data, which is exactly why this is a union
 * rather than a nullable list plus a flag.
 */
sealed interface WeightChartContent {
    /** Nothing has ever been recorded. The only state where the app truly knows nothing. */
    data object NoWeighings : WeightChartContent

    /**
     * One weighing in the window — a dot, and nothing to join it to.
     *
     * [moreOutsideRange] distinguishes "this bunny has been weighed once" from "the window happens
     * to hold one of many", so the copy never claims ignorance of data the app is holding.
     */
    data class SinglePoint(
        val point: WeightChartPoint,
        val moreOutsideRange: Boolean,
    ) : WeightChartContent

    /**
     * Weighings exist, but none fall in the window — reached by weighing monthly and skipping a
     * quiet winter. It carries [lastWeighingAt] because this state **must name the date** rather
     * than say "nothing recorded yet", which would be ADR-0001's silence failure in miniature.
     */
    data class NoneInRange(
        val lastWeighingAt: Instant,
    ) : WeightChartContent

    /** Two or more points in the window: the chart proper, oldest first. */
    data class Line(
        val points: List<WeightChartPoint>,
    ) : WeightChartContent
}

/**
 * Decides what the chart area shows, given the bunny's whole [series] and the chosen [range].
 *
 * [series] arrives **newest-first**, in ADR-0021's total order, exactly as the history list has it.
 * Only the low end is cut: a weighing cannot be recorded in the future, so there is no upper bound
 * to apply.
 *
 * There is **no auto-widening** here. When the window turns out to be empty this reports the fact
 * and leaves the range alone — a selector that silently overrode the owner's choice would lie about
 * its own state. The one-tap escape to [WeightChartRange.ALL] is offered by the screen instead.
 */
fun weightChartContentFor(
    series: List<WeightRow>,
    range: WeightChartRange,
    now: Instant,
): WeightChartContent {
    val latest = series.firstOrNull() ?: return WeightChartContent.NoWeighings
    val cutoff = range.cutoff(now)
    val inRange = if (cutoff == null) series else series.filter { !it.recordedAt.isBefore(cutoff) }

    return when (inRange.size) {
        0 -> WeightChartContent.NoneInRange(latest.recordedAt)
        1 ->
            WeightChartContent.SinglePoint(
                point = inRange.single().toChartPoint(),
                moreOutsideRange = series.size > 1,
            )
        // `asReversed` is a view rather than a copy, so this is one allocation, not two.
        else -> WeightChartContent.Line(inRange.asReversed().map { it.toChartPoint() })
    }
}

private fun WeightRow.toChartPoint() = WeightChartPoint(recordedAt = recordedAt, grams = grams)
