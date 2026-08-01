package app.binky.tracker.ui.care

import app.binky.tracker.data.CareInterval
import app.binky.tracker.data.CareIntervalUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The calendar hand-off's two pure halves (ADR-0014).
 *
 * Neither is checkable by looking at the app: the `RRULE` is read by somebody else's calendar, and
 * an all-day event landing a day early in a western timezone is exactly the kind of bug that only
 * shows up on a phone that is not the author's.
 */
class CalendarHandoffTest {
    @Test
    fun `a yearly vaccination is the rule ADR-0014 names`() {
        // Spelled out in the ADR, and what a calendar app shows back as "Annually".
        assertEquals("FREQ=YEARLY", careRrule(CareInterval(1, CareIntervalUnit.YEAR)))
    }

    @Test
    fun `a count of one omits INTERVAL entirely`() {
        assertEquals("FREQ=DAILY", careRrule(CareInterval(1, CareIntervalUnit.DAY)))
        assertEquals("FREQ=WEEKLY", careRrule(CareInterval(1, CareIntervalUnit.WEEK)))
        assertEquals("FREQ=MONTHLY", careRrule(CareInterval(1, CareIntervalUnit.MONTH)))
    }

    @Test
    fun `a six-week nail trim maps one for one`() {
        // The whole return on 4b storing a calendar interval rather than a day count: 42 days would
        // have had to be approximated here.
        assertEquals("FREQ=WEEKLY;INTERVAL=6", careRrule(CareInterval(6, CareIntervalUnit.WEEK)))
    }

    @Test
    fun `every unit has a frequency`() {
        // A `when` over the enum is exhaustive, so this fails to compile rather than at runtime if a
        // unit is ever added — but the mapping itself is still worth stating.
        val frequencies = CareIntervalUnit.entries.map { careRrule(CareInterval(2, it)) }
        assertEquals(
            listOf(
                "FREQ=DAILY;INTERVAL=2",
                "FREQ=WEEKLY;INTERVAL=2",
                "FREQ=MONTHLY;INTERVAL=2",
                "FREQ=YEARLY;INTERVAL=2",
            ),
            frequencies,
        )
    }

    @Test
    fun `an all-day event begins at midnight UTC`() {
        // `CalendarContract` defines all-day times that way. Handing over a local midnight is what
        // puts the event on the day before west of Greenwich.
        assertEquals(
            LocalDate.of(2026, 3, 14).toEpochDay() * 24 * 60 * 60 * 1000,
            careCalendarBeginMillis(LocalDate.of(2026, 3, 14)),
        )
    }
}
