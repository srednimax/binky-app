package app.binky.tracker.ui.weight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * ADR-0022: the range changes what is **drawn** and nothing else, and there are three empty states
 * rather than two — the third one has to say it is holding data the window does not reach.
 */
class WeightChartContentTest {
    private val now: Instant = Instant.parse("2026-07-26T10:00:00Z")

    /** Newest-first, as the history list and ADR-0021's total order both have it. */
    private fun series(vararg daysAgoToGrams: Pair<Long, Int>): List<WeightRow> =
        daysAgoToGrams
            .map { (daysAgo, grams) ->
                WeightRow(
                    id = "w-$daysAgo-$grams",
                    grams = grams,
                    recordedAt = now.minus(Duration.ofDays(daysAgo)),
                    changeGrams = null,
                )
            }.sortedByDescending { it.recordedAt }

    @Test
    fun `no weighings at all is its own state`() {
        assertEquals(
            WeightChartContent.NoWeighings,
            weightChartContentFor(emptyList(), WeightChartRange.DAYS_90, now),
        )
    }

    @Test
    fun `two or more in the window draw a line, oldest first`() {
        val content =
            weightChartContentFor(
                series(2L to 2500, 20L to 2400, 60L to 2300),
                WeightChartRange.DAYS_90,
                now,
            )

        // Oldest first is what a chart plots left to right; the input arrived newest-first.
        assertEquals(
            listOf(2300, 2400, 2500),
            (content as WeightChartContent.Line).points.map { it.grams },
        )
    }

    @Test
    fun `the only weighing there has ever been is a single point, with nothing hidden`() {
        val content = weightChartContentFor(series(3L to 2500), WeightChartRange.DAYS_90, now)

        assertEquals(2500, (content as WeightChartContent.SinglePoint).point.grams)
        // Nothing is outside the window, so the copy must not offer to widen it.
        assertEquals(false, content.moreOutsideRange)
    }

    @Test
    fun `one in the window with others behind it says so`() {
        val content =
            weightChartContentFor(
                series(3L to 2500, 200L to 2400, 300L to 2300),
                WeightChartRange.DAYS_90,
                now,
            )

        // The distinction the boolean exists for: the same single dot, but the app is holding more.
        assertEquals(true, (content as WeightChartContent.SinglePoint).moreOutsideRange)
    }

    @Test
    fun `an empty window names the last weighing rather than claiming ignorance`() {
        // Weigh monthly, skip a quiet winter, open to a blank 90-day window — an ordinary path.
        val content =
            weightChartContentFor(
                series(120L to 2500, 150L to 2450),
                WeightChartRange.DAYS_90,
                now,
            )

        assertEquals(
            now.minus(Duration.ofDays(120)),
            (content as WeightChartContent.NoneInRange).lastWeighingAt,
        )
    }

    @Test
    fun `a weighing exactly on the cutoff is inside the window`() {
        // The boundary is inclusive, so a 90-day window shows the reading taken 90 days ago rather
        // than dropping it on a strict comparison.
        val content =
            weightChartContentFor(
                series(0L to 2500, 90L to 2400),
                WeightChartRange.DAYS_90,
                now,
            )

        assertEquals(2, (content as WeightChartContent.Line).points.size)
    }

    @Test
    fun `narrowing the range drops points and nothing else`() {
        // The whole of "display-only" in one assertion: same series, same data, fewer points drawn.
        val series = series(2L to 2500, 45L to 2450, 200L to 2400)

        // One point left in 30 days, so there is no line — but the other two are still held.
        val thirty = weightChartContentFor(series, WeightChartRange.DAYS_30, now)
        assertEquals(2500, (thirty as WeightChartContent.SinglePoint).point.grams)
        assertEquals(true, thirty.moreOutsideRange)

        assertEquals(
            2,
            (weightChartContentFor(series, WeightChartRange.DAYS_90, now) as WeightChartContent.Line).points.size,
        )
        assertEquals(
            3,
            (weightChartContentFor(series, WeightChartRange.YEAR, now) as WeightChartContent.Line).points.size,
        )
        assertEquals(
            3,
            (weightChartContentFor(series, WeightChartRange.ALL, now) as WeightChartContent.Line).points.size,
        )
    }

    @Test
    fun `All has no cutoff, so it can never report an empty window`() {
        // The invariant the copy leans on: `weight_chart_window_all` is unreachable. A decade-old
        // series still draws under ALL, which is what makes the one-tap escape worth offering.
        val ancient = series(4000L to 2500, 4200L to 2450)

        assertTrue(weightChartContentFor(ancient, WeightChartRange.ALL, now) is WeightChartContent.Line)
        assertTrue(
            weightChartContentFor(ancient, WeightChartRange.YEAR, now) is WeightChartContent.NoneInRange,
        )
    }

    @Test
    fun `two weighings sharing an instant both survive to the chart`() {
        // 2c's sample fixture contains a tied `recordedAt` on purpose, and the chart must plot both
        // rather than collapse them — the tie is also what makes an inferred x step degenerate.
        val tied = now.minus(Duration.ofDays(5))
        val series =
            listOf(
                WeightRow(id = "a", grams = 2500, recordedAt = tied, changeGrams = null),
                WeightRow(id = "b", grams = 2480, recordedAt = tied, changeGrams = null),
                WeightRow(id = "c", grams = 2400, recordedAt = now.minus(Duration.ofDays(30)), changeGrams = null),
            )

        val points = (weightChartContentFor(series, WeightChartRange.DAYS_90, now) as WeightChartContent.Line).points

        assertEquals(3, points.size)
        assertEquals(listOf(2400, 2480, 2500), points.map { it.grams })
    }
}
