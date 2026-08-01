package app.binky.tracker.ui.care

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A due date said in words, as a case table.
 *
 * The interesting cases are all boundaries — the day "tomorrow" becomes "in 2 days", the fortnight
 * where days give way to weeks, and the month arithmetic [careGap] inherits from `ageOn`. None of
 * them needs Android, and every one of them is the kind of off-by-one that reads as correct until
 * somebody checks it against a calendar.
 */
class CareDueTest {
    private val today = LocalDate.of(2026, 3, 14)

    @Test
    fun `due today says so`() {
        assertEquals(CareDue.Today, careDue(dueOn = today, today = today))
    }

    @Test
    fun `the day either side of today is a word, not a count`() {
        assertEquals(CareDue.Tomorrow, careDue(dueOn = today.plusDays(1), today = today))
        assertEquals(CareDue.Yesterday, careDue(dueOn = today.minusDays(1), today = today))
    }

    @Test
    fun `two days out is where counting starts`() {
        // The boundary "tomorrow" would otherwise swallow: 2 days must not render as "in 0 weeks".
        assertEquals(CareDue.In(CareGap.Days(2)), careDue(dueOn = today.plusDays(2), today = today))
        assertEquals(CareDue.Overdue(CareGap.Days(2)), careDue(dueOn = today.minusDays(2), today = today))
    }

    @Test
    fun `days give way to weeks at a fortnight`() {
        assertEquals(CareDue.In(CareGap.Days(13)), careDue(dueOn = today.plusDays(13), today = today))
        assertEquals(CareDue.In(CareGap.Weeks(2)), careDue(dueOn = today.plusDays(14), today = today))
    }

    @Test
    fun `weeks give way to months at a month`() {
        // 30 days from 14 March is 13 April — one month has not quite passed, so it stays weeks.
        assertEquals(CareDue.In(CareGap.Weeks(4)), careDue(dueOn = today.plusDays(30), today = today))
        assertEquals(CareDue.In(CareGap.Months(1)), careDue(dueOn = today.plusMonths(1), today = today))
    }

    @Test
    fun `a yearly reminder counts in years`() {
        assertEquals(CareDue.In(CareGap.Years(1)), careDue(dueOn = today.plusYears(1), today = today))
    }

    @Test
    fun `an overdue reminder is measured the same way in the other direction`() {
        // A vaccination nobody has renewed for over a year. Overdue state is carried indefinitely,
        // so this has to keep saying something sensible however long it goes on (ADR-0001).
        assertEquals(CareDue.Overdue(CareGap.Years(1)), careDue(dueOn = today.minusYears(1), today = today))
        assertEquals(CareDue.Overdue(CareGap.Months(3)), careDue(dueOn = today.minusMonths(3), today = today))
    }

    @Test
    fun `month arithmetic follows the calendar rather than a thirty-day approximation`() {
        // February is short, so 14 February to 14 March is 28 days — under the fortnight threshold's
        // successor, and a "days divided by 30" implementation would call it 0 months.
        val leapYearFebruary = LocalDate.of(2026, 2, 14)
        assertEquals(CareGap.Months(1), careGap(from = leapYearFebruary, to = today))
    }
}
