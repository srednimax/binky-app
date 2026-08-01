package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The watch resolved from `(row, now)` — active, expired, absent — as a case table (PLAN 4d).
 *
 * Pure JVM, and that is the whole reason `watchState` takes a row and an instant rather than reading
 * the clock: "does a watch stop nagging the moment it runs out" is arithmetic, and arithmetic proven
 * by waiting seven days on a phone is arithmetic proven by nobody.
 */
class WatchStateTest {
    private val now: Instant = Instant.parse("2026-05-20T09:00:00Z")

    private fun watch(
        endsAt: Instant,
        lastNaggedOn: LocalDate? = null,
    ) = WatchEntity(
        bunnyId = "bijou",
        startedAt = endsAt.minus(Duration.ofDays(7)),
        endsAt = endsAt,
        lastNaggedOn = lastNaggedOn,
    )

    @Test
    fun `no row is no watch`() {
        assertEquals(WatchState.None, watchState(null, now))
        assertFalse(watchState(null, now).isActive())
    }

    @Test
    fun `a watch ending later is active`() {
        val state = watchState(watch(now.plus(Duration.ofDays(4))), now)
        assertTrue(state is WatchState.Active)
        assertTrue(state.isActive())
    }

    @Test
    fun `a watch is expired from the instant it ends, not the day after`() {
        // The boundary the nag turns on: `endsAt` exactly is already over. A watch that kept nagging
        // through the whole of its final day would run a day longer than the owner asked for, every
        // time.
        assertTrue(watchState(watch(now), now) is WatchState.Expired)
        assertTrue(watchState(watch(now.minusMillis(1)), now) is WatchState.Expired)
        assertTrue(watchState(watch(now.plusMillis(1)), now) is WatchState.Active)
    }

    @Test
    fun `an expired watch is not an active one`() {
        // An unanswered prompt must not keep excluding a bunny from the healthy day, and must not
        // keep nagging. Both follow from this one line.
        assertFalse(watchState(watch(now.minus(Duration.ofDays(3))), now).isActive())
    }

    @Test
    fun `days left is a whole number of days, rounded up`() {
        fun daysLeft(endsIn: Duration) = (watchState(watch(now.plus(endsIn)), now) as WatchState.Active).daysLeft

        // A fresh 7-day watch reads "7 days left" rather than "6".
        assertEquals(7, daysLeft(Duration.ofDays(7)))
        // Mid-afternoon on the last day still reads "1 day left", never "0 days left" — a watch
        // that claims to have ended while it is still nagging is the one reading that is simply
        // wrong.
        assertEquals(1, daysLeft(Duration.ofHours(3)))
        assertEquals(1, daysLeft(Duration.ofSeconds(1)))
        // And a partial day rounds up rather than truncating.
        assertEquals(5, daysLeft(Duration.ofDays(4).plusHours(12)))
    }

    @Test
    fun `a duration lands a watch a whole number of days out`() {
        WatchDuration.entries.forEach { duration ->
            assertEquals(
                "${duration.name} should end exactly ${duration.days} days out",
                now.plus(Duration.ofDays(duration.days)),
                watchEndsAt(now, duration),
            )
        }
    }

    @Test
    fun `the default duration is seven days`() {
        // ADR-0001 time-boxes the watch precisely so it cannot become wallpaper, and the default is
        // the number almost everyone will accept without reading it.
        assertEquals(WatchDuration.DAYS_7, WatchDuration.Default)
        assertEquals(listOf(3L, 7L, 14L), WatchDuration.entries.map { it.days })
    }
}
