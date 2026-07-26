package app.bunny.tracker.ui.weight

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bunny.tracker.R
import app.bunny.tracker.data.WeightUnit
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Placeholders share the chart's height so switching range never makes the list below jump. */
private val CHART_HEIGHT = 220.dp

/**
 * Blank x-axis domain either side of the data, as a fraction of the tick step, so the first and last
 * date labels have room to be drawn rather than being clipped by the plot's edges.
 */
private const val X_DOMAIN_PADDING_STEPS = 0.35

private const val MILLIS_PER_DAY = 86_400_000.0

/**
 * The weight chart: a range selector over either a line or one of ADR-0022's three empty states.
 *
 * The selector is **display-only**. It never reaches the trend flag, which is why this composable
 * takes an already-decided [WeightChartContent] and has no access to the series at all.
 */
@Composable
fun WeightChart(
    content: WeightChartContent,
    range: WeightChartRange,
    unit: WeightUnit,
    onRangeChange: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RangeSelector(range = range, onRangeChange = onRangeChange)

        when (content) {
            // Nothing has ever been recorded — the one case where there is no data to point at.
            WeightChartContent.NoWeighings ->
                ChartPlaceholder(stringResource(R.string.weight_chart_no_weighings))

            is WeightChartContent.SinglePoint ->
                if (content.moreOutsideRange) {
                    ChartPlaceholder(
                        text =
                            stringResource(
                                R.string.weight_chart_single_in_range,
                                stringResource(range.windowRes()),
                            ),
                        onShowAll = { onRangeChange(WeightChartRange.ALL) },
                    )
                } else {
                    ChartPlaceholder(stringResource(R.string.weight_chart_single_point))
                }

            // Names the date rather than claiming nothing was recorded — saying "no weight recorded
            // yet" here would be the app disowning data it is holding (ADR-0001, ADR-0022).
            is WeightChartContent.NoneInRange ->
                ChartPlaceholder(
                    text =
                        stringResource(
                            R.string.weight_chart_none_in_range,
                            stringResource(range.windowRes()),
                            instantDateLabel(content.lastWeighingAt),
                        ),
                    onShowAll = { onRangeChange(WeightChartRange.ALL) },
                )

            is WeightChartContent.Line -> WeightLineChart(points = content.points, unit = unit)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    range: WeightChartRange,
    onRangeChange: (WeightChartRange) -> Unit,
) {
    val options = WeightChartRange.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == range,
                onClick = { onRangeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(option.labelRes()))
            }
        }
    }
}

/**
 * An empty state, optionally offering the one-tap escape to *All*.
 *
 * The escape is **offered, never taken** on the owner's behalf: auto-widening would silently
 * override a choice they made and leave the selector showing a range it is not drawing (ADR-0022).
 */
@Composable
private fun ChartPlaceholder(
    text: String,
    onShowAll: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        onShowAll?.let { showAll ->
            TextButton(onClick = showAll) { Text(stringResource(R.string.weight_chart_show_all)) }
        }
    }
}

@Composable
private fun WeightLineChart(
    points: List<WeightChartPoint>,
    unit: WeightUnit,
) {
    // x is **days elapsed since the oldest plotted weighing**, not a raw epoch and emphatically not
    // the list index (house rule): weighings are irregular, and an index axis would draw a monthly
    // gap and an overnight re-weigh the same width. Shifting the origin to zero is a translation,
    // not a rescale, so the shape is untouched — but it keeps the numbers in the tens-to-hundreds,
    // where the Float arithmetic a renderer does internally still resolves an hour. Raw epoch
    // seconds need ten significant digits and a Float carries about seven.
    val origin = points.first().recordedAt
    val xs = remember(points) { points.map { daysBetween(origin, it.recordedAt) } }
    val ys = remember(points) { points.map { it.grams } }
    val xStep = remember(xs) { tickStepDays(spanDays = xs.last() - xs.first()) }

    val modelProducer = remember { CartesianChartModelProducer() }
    // Kotlin note: `LaunchedEffect` runs a coroutine tied to this composable, restarting it when a
    // key changes and cancelling it on removal — roughly useEffect with an async body and automatic
    // cleanup. `runTransaction` is `suspend`, so it needs one; the lists compare by content, so a
    // recomposition that changes nothing does not re-push the model.
    LaunchedEffect(xs, ys) {
        modelProducer.runTransaction { lineSeries { series(xs, ys) } }
    }

    val dateLabel = rememberAxisDateFormatter()
    val weightLabel = rememberAxisWeightFormatter(unit)
    val band = remember(points) { visibleBandGrams(points) }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart =
                rememberCartesianChart(
                    rememberLineCartesianLayer(
                        rangeProvider =
                            remember(band, xStep, xs) {
                                CartesianLayerRangeProvider.fixed(
                                    // A margin either side of the data, in x. Ticks are placed at
                                    // `minX + k × step`, and without this the last one lands on the
                                    // newest weighing — - which sits exactly on the plot's right
                                    // edge, so its date label is drawn half outside the chart and
                                    // clipped. Widening the domain moves every tick clear of the
                                    // edges. Vico's own `addExtremeLabelPadding` does not cover
                                    // this: it thins labels so they do not collide with each other,
                                    // and reserves margin for tick marks, not for label overhang.
                                    minX = -xStep * X_DOMAIN_PADDING_STEPS,
                                    maxX = xs.last() + xStep * X_DOMAIN_PADDING_STEPS,
                                    minY = band.start,
                                    maxY = band.endInclusive,
                                )
                            },
                    ),
                    startAxis =
                        VerticalAxis.rememberStart(
                            valueFormatter = CartesianValueFormatter { _, value, _ -> weightLabel(value) },
                        ),
                    bottomAxis =
                        HorizontalAxis.rememberBottom(
                            valueFormatter =
                                CartesianValueFormatter { _, value, _ ->
                                    dateLabel(origin.plusMillis((value * MILLIS_PER_DAY).roundToLong()))
                                },
                        ),
                    // Vico's default step is the GCD of the gaps between x values, which suits evenly
                    // spaced data and falls apart here: irregular timestamps drive it to something
                    // tiny, and two weighings sharing a `recordedAt` contribute a gap of zero. Real
                    // dates therefore need the step stated, or the axis asks for thousands of ticks.
                    getXStep = { xStep },
                ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
            // A fixed window that fits the width: scrolling or zooming it would let the drawn range
            // drift away from the range the selector claims is showing.
            scrollState = rememberVicoScrollState(scrollEnabled = false),
        )
    }
}

/**
 * The band of weights the y-axis covers, in grams — **fitted to the window, never anchored at zero.**
 *
 * Vico anchors at zero by default, and on a 1.8 kg rabbit that spends the whole axis on weights the
 * animal has never had: the −90 g slide the flag exists to catch draws the same flat line as a −2 g
 * wobble. That is the gram-versus-kilogram house rule failing in geometry instead of arithmetic,
 * and it is the y-axis twin of the compression ADR-0022 gives the x-axis a range selector to avoid.
 *
 * Fitting alone has the opposite failure, though — with nothing but a 2 g wobble in the window it
 * magnifies noise into a cliff, which for a health app is the worse error of the two. So the band
 * never narrows past [MIN_VISIBLE_SPAN_FRACTION], which pins the geometry to the same constants the
 * arithmetic already uses: the trigger is 5 % below baseline (ADR-0001), so a drop that fires
 * crosses about half the chart's height, while a wobble inside ADR-0021's 2 % noise floor stays a
 * ripple near the middle. Neither reading is left to the accident of what else is in the window.
 */
private fun visibleBandGrams(points: List<WeightChartPoint>): ClosedFloatingPointRange<Double> {
    val lightest = points.minOf { it.grams }.toDouble()
    val heaviest = points.maxOf { it.grams }.toDouble()

    val floor = maxOf(heaviest * MIN_VISIBLE_SPAN_FRACTION, MIN_VISIBLE_SPAN_GRAMS)
    val span = maxOf((heaviest - lightest) * (1 + 2 * SPAN_PADDING_FRACTION), floor)
    val middle = (lightest + heaviest) / 2

    // Rounded outward to a tidy number of grams so the axis labels read as round values, and never
    // below zero, which would be a weight no animal has.
    val bottom = roundDownTo(maxOf(0.0, middle - span / 2), GRID_ROUNDING_GRAMS)
    val top = roundUpTo(middle + span / 2, GRID_ROUNDING_GRAMS)
    return bottom..top
}

/**
 * A tenth of the heaviest reading in the window. See [visibleBandGrams] for why this number is tied
 * to the trigger's 5 % rather than chosen for looks.
 */
private const val MIN_VISIBLE_SPAN_FRACTION = 0.10

/** A floor for very small animals, where a tenth of the weight is a handful of grams. */
private const val MIN_VISIBLE_SPAN_GRAMS = 100.0

/** Breathing room above and below, so the line never runs along the frame. */
private const val SPAN_PADDING_FRACTION = 0.15

private const val GRID_ROUNDING_GRAMS = 50.0

private fun roundDownTo(
    value: Double,
    step: Double,
): Double = floor(value / step) * step

private fun roundUpTo(
    value: Double,
    step: Double,
): Double = ceil(value / step) * step

/** Roughly this many gridlines across the plot — enough to date the shape, few enough to read. */
private const val TARGET_GRIDLINES = 5.0

/**
 * Tick spacings worth landing on, in days: a few days, a week, a fortnight, then months and a year.
 * Never below a day — an axis labelled by the hour is noise on a chart of a rabbit's weight, and the
 * floor is also what keeps the step non-zero when every plotted point falls on the same day.
 */
private val TICK_STEPS_DAYS = listOf(1.0, 2.0, 7.0, 14.0, 30.0, 61.0, 91.0, 182.0, 365.0)

private fun tickStepDays(spanDays: Double): Double {
    val target = spanDays / TARGET_GRIDLINES
    return TICK_STEPS_DAYS.firstOrNull { it >= target } ?: TICK_STEPS_DAYS.last()
}

private fun daysBetween(
    from: Instant,
    to: Instant,
): Double = Duration.between(from, to).toMillis() / MILLIS_PER_DAY

@StringRes
private fun WeightChartRange.labelRes(): Int =
    when (this) {
        WeightChartRange.DAYS_30 -> R.string.weight_chart_range_30d
        WeightChartRange.DAYS_90 -> R.string.weight_chart_range_90d
        WeightChartRange.YEAR -> R.string.weight_chart_range_1y
        WeightChartRange.ALL -> R.string.weight_chart_range_all
    }

/**
 * The window as it reads inside a sentence — "no weighings in **the last 90 days**".
 *
 * [WeightChartRange.ALL] cannot reach the copy that uses this: with no cutoff every weighing is in
 * the window, so neither the empty nor the outside-the-range state is reachable. It is spelled out
 * anyway to keep the `when` exhaustive rather than leaning on an `else` that would quietly absorb a
 * fifth range later.
 */
@StringRes
private fun WeightChartRange.windowRes(): Int =
    when (this) {
        WeightChartRange.DAYS_30 -> R.string.weight_chart_window_30d
        WeightChartRange.DAYS_90 -> R.string.weight_chart_window_90d
        WeightChartRange.YEAR -> R.string.weight_chart_window_1y
        WeightChartRange.ALL -> R.string.weight_chart_window_all
    }
