package app.binky.tracker.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the next sweep lands, resolved in the device's current zone rather than stored (ADR-0003).
 *
 * PLAN's Verification section names DST-boundary arithmetic as a JVM test target, and this is the
 * first place it bites: the sweep is the only clock in the app until doses arrive, and a daily
 * boundary crossing a DST change is exactly the case where "add 24 hours" and "the same wall clock
 * tomorrow" stop agreeing.
 *
 * Warsaw, because that is where this app's owner and their rabbits are, and because it has both
 * transitions at civilised hours.
 */
class ReminderSweepTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val nine = LocalTime.of(9, 0)

    private fun at(
        date: String,
        time: String,
    ) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), warsaw).toInstant()

    @Test
    fun `before the reminder time, it is today`() {
        assertEquals(
            at("2026-06-15", "09:00"),
            nextSweepAt(at("2026-06-15", "07:30"), nine, warsaw),
        )
    }

    @Test
    fun `after the reminder time, it is tomorrow`() {
        assertEquals(
            at("2026-06-16", "09:00"),
            nextSweepAt(at("2026-06-15", "09:01"), nine, warsaw),
        )
    }

    @Test
    fun `at exactly the reminder time, it is tomorrow`() {
        // Strictly-after, not at-or-after. A sweep that ran at 09:00:00.000 and scheduled its
        // successor for the same instant would re-enqueue itself with a zero delay and spin.
        assertEquals(
            at("2026-06-16", "09:00"),
            nextSweepAt(at("2026-06-15", "09:00"), nine, warsaw),
        )
    }

    @Test
    fun `spring forward shortens the gap without moving the wall clock`() {
        // Warsaw jumps 02:00 to 03:00 on 2026-03-29. The sweep still runs at 09:00 local — that is
        // the whole point of resolving in the zone rather than adding 24 hours — so the *interval*
        // is 23 hours, and an owner notices nothing.
        val next = nextSweepAt(at("2026-03-28", "09:30"), nine, warsaw)
        assertEquals(at("2026-03-29", "09:00"), next)
        assertEquals(Duration.ofHours(23), Duration.between(at("2026-03-28", "09:00"), next))
    }

    @Test
    fun `fall back lengthens the gap without moving the wall clock`() {
        // 2026-10-25, Warsaw repeats 02:00-03:00. Same wall clock, 25 hours later.
        val next = nextSweepAt(at("2026-10-24", "09:30"), nine, warsaw)
        assertEquals(at("2026-10-25", "09:00"), next)
        assertEquals(Duration.ofHours(25), Duration.between(at("2026-10-24", "09:00"), next))
    }

    @Test
    fun `a reminder time inside a spring-forward gap resolves once, forward`() {
        // Nobody would set 02:30 as a reminder time on purpose, but a phone carried into a zone
        // where their chosen time does not exist that morning would. `java.time`'s default
        // resolution shifts it to the first valid instant: fires once, never zero times, never twice.
        val halfPastTwo = LocalTime.of(2, 30)
        val next = nextSweepAt(at("2026-03-29", "00:15"), halfPastTwo, warsaw)
        assertEquals(ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(3, 30), warsaw).toInstant(), next)
    }

    @Test
    fun `the next sweep is always ahead of now, never behind it`() {
        // The property behind every case above, asserted as one: the delay handed to WorkManager is
        // computed as `Duration.between(now, this)`, and a negative delay is a request to run in the
        // past — which WorkManager runs immediately, turning a daily sweep into a tight loop.
        val probes =
            listOf(
                at("2026-01-01", "00:00"),
                at("2026-03-29", "01:59"),
                at("2026-10-25", "02:30"),
                at("2026-12-31", "23:59"),
            )
        probes.forEach { now ->
            assertTrue("next sweep must be after $now", nextSweepAt(now, nine, warsaw) > now)
        }
    }
}
