package app.binky.tracker.ui.bunny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Age arithmetic, as a pure JVM test — the boundaries (the day before a birthday, a leap-year
 * birthday, a date in the future) are exactly the cases a hand check on the phone would miss.
 */
class AgeTest {
    private val today = LocalDate.of(2026, 7, 25)

    @Test
    fun countsWholeYearsOnceTheFirstOneIsComplete() {
        assertEquals(Age.Years(2), ageOn(LocalDate.of(2024, 7, 25), today))
        assertEquals(Age.Years(1), ageOn(LocalDate.of(2025, 7, 25), today))
    }

    @Test
    fun theDayBeforeABirthdayIsStillTheYoungerAge() {
        // Months, not "1 year": a bunny is eleven months old until the day it is not.
        assertEquals(Age.Months(11), ageOn(LocalDate.of(2025, 7, 26), today))
    }

    @Test
    fun countsMonthsBeforeTheFirstYear() {
        assertEquals(Age.Months(6), ageOn(LocalDate.of(2026, 1, 25), today))
        assertEquals(Age.Months(1), ageOn(LocalDate.of(2026, 6, 25), today))
    }

    @Test
    fun countsWeeksForABabyTooYoungForMonths() {
        assertEquals(Age.Weeks(3), ageOn(LocalDate.of(2026, 7, 4), today))
        assertEquals(Age.Weeks(0), ageOn(LocalDate.of(2026, 7, 20), today))
    }

    /**
     * A 29 February bunny turns one on **1 March** in a non-leap year: `Period.between` only counts
     * a whole year once the anniversary date is actually reached, and 29 February 2025 does not
     * exist. Worth pinning down — it is the one date where "a year old" is a judgement call, and
     * the alternative reading would have the age tick over a day early every three years out of
     * four.
     */
    @Test
    fun aLeapDayBirthdayTurnsOneOnTheFirstOfMarch() {
        assertEquals(Age.Months(11), ageOn(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)))
        assertEquals(Age.Years(1), ageOn(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun bornTodayIsZeroWeeksRatherThanNothing() {
        assertEquals(Age.Weeks(0), ageOn(today, today))
    }

    /** A fat-fingered year in the picker must not render as a negative age. */
    @Test
    fun aFutureBirthDateHasNoAge() {
        assertNull(ageOn(LocalDate.of(2026, 7, 26), today))
    }
}
