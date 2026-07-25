package app.bunny.tracker.ui.bunny

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * How old a bunny is, in the one unit worth saying out loud.
 *
 * A single unit rather than "1 year, 3 months and 2 weeks": the age is context on a profile, not a
 * measurement, and rabbits arrive as babies (weeks matter), grow up over months, then live years.
 *
 * Kotlin note: a `sealed interface` is a discriminated union — the compiler knows every case, so a
 * `when` over one needs no `else` and stops compiling the day a case is added.
 */
sealed interface Age {
    data class Years(
        val years: Int,
    ) : Age

    data class Months(
        val months: Int,
    ) : Age

    data class Weeks(
        val weeks: Int,
    ) : Age
}

/**
 * The age on [today], or null if [birthDate] is in the future — a typo in a date picker must not
 * render as "-1 years old".
 *
 * Pure and JVM-testable on purpose: the date arithmetic is the part that is easy to get subtly
 * wrong (a leap-year birthday, the day before a birthday), and none of it needs Android.
 */
fun ageOn(
    birthDate: LocalDate,
    today: LocalDate,
): Age? {
    if (birthDate.isAfter(today)) return null
    // Period.between is calendar-aware: born 29 February, it counts a year on 28 February in a
    // non-leap year rather than drifting by a day, which is what dividing days by 365 would do.
    val period = Period.between(birthDate, today)
    return when {
        period.years >= 1 -> Age.Years(period.years)
        period.months >= 1 -> Age.Months(period.months)
        else -> Age.Weeks((ChronoUnit.DAYS.between(birthDate, today) / 7).toInt())
    }
}
