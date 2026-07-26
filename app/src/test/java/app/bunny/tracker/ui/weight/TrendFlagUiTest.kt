package app.bunny.tracker.ui.weight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The long-gap boundary. This only ever *adds a sentence* to the flag's copy — ADR-0001 forbids
 * damping the trigger by elapsed time — so what is being pinned is when the app says "since" out
 * loud, not when it decides to speak.
 */
class TrendFlagUiTest {
    private val baseline = Instant.parse("2026-06-03T09:00:00Z")

    private fun laterBy(duration: Duration) = isLongGap(baseline, baseline.plus(duration))

    @Test
    fun `a day under the threshold is not a long gap`() {
        assertFalse(laterBy(Duration.ofDays(LONG_GAP_DAYS - 1)))
    }

    @Test
    fun `the threshold itself is a long gap`() {
        assertTrue(laterBy(Duration.ofDays(LONG_GAP_DAYS)))
    }

    @Test
    fun `an hour short of the threshold is not yet a long gap`() {
        // toDays() truncates, so the boundary lands on whole elapsed days rather than calendar ones.
        assertFalse(laterBy(Duration.ofDays(LONG_GAP_DAYS).minusHours(1)))
    }

    @Test
    fun `two readings at the same instant are not a long gap`() {
        assertFalse(isLongGap(baseline, baseline))
    }

    @Test
    fun `a year apart is a long gap`() {
        assertTrue(laterBy(Duration.ofDays(365)))
    }
}
