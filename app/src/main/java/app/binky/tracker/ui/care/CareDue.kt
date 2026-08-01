package app.binky.tracker.ui.care

import app.binky.tracker.ui.bunny.Age
import app.binky.tracker.ui.bunny.ageOn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A due date **in words**, which is what the Care list shows instead of a bare date.
 *
 * "Due in 3 days" is a fact an owner can act on; "14 March" is a fact they have to subtract today
 * from first. The bare date is not lost — it stays on the reminder's own screen, where the question
 * is *which* day rather than *how soon*.
 *
 * Pure and JVM-tested, because the awkward cases are all arithmetic: the boundary between "tomorrow"
 * and "in 2 days", a reminder overdue by a year, and the month lengths [careGap] inherits from
 * [ageOn].
 *
 * Kotlin note: a `sealed interface` is a discriminated union — a `when` over one needs no `else`, and
 * stops compiling the day a case is added. That is what keeps the composable that renders these and
 * this file from drifting apart.
 */
sealed interface CareDue {
    data object Today : CareDue

    data object Tomorrow : CareDue

    /** Overdue by exactly one day, which reads better as a word than as "1 day overdue". */
    data object Yesterday : CareDue

    data class In(
        val gap: CareGap,
    ) : CareDue

    data class Overdue(
        val gap: CareGap,
    ) : CareDue
}

/**
 * How far off a date is, in the one unit worth saying out loud — the same single-unit rule
 * [app.binky.tracker.ui.bunny.Age] follows, with days added.
 *
 * Days are the addition, and they are the point: an age is context on a profile, but a nail trim due
 * in four days is something the owner is deciding about this week. Above a fortnight the day count
 * stops meaning anything and weeks, months and years take over.
 */
sealed interface CareGap {
    data class Days(
        val days: Int,
    ) : CareGap

    data class Weeks(
        val weeks: Int,
    ) : CareGap

    data class Months(
        val months: Int,
    ) : CareGap

    data class Years(
        val years: Int,
    ) : CareGap
}

/** Below this many days apart, the answer is a number of days. */
private const val DAYS_UNTIL_WEEKS = 14

/**
 * The gap between two dates, [from] being the earlier one.
 *
 * **Delegates to [ageOn] above a fortnight** rather than dividing days by 30, which is the same
 * calendar arithmetic and already has its own tests: a vaccination due on 29 February counts a year
 * on 28 February in a non-leap year rather than drifting.
 *
 * A gap of zero comes back as `Days(0)`; the callers above all special-case today before asking.
 */
fun careGap(
    from: LocalDate,
    to: LocalDate,
): CareGap {
    val days = ChronoUnit.DAYS.between(from, to)
    if (days < DAYS_UNTIL_WEEKS) return CareGap.Days(days.toInt())
    return when (val age = ageOn(from, to)) {
        is Age.Years -> CareGap.Years(age.years)
        is Age.Months -> CareGap.Months(age.months)
        // `ageOn` returns weeks under a month and null only for a future birthdate, which cannot
        // happen here — `days` is already known to be positive.
        is Age.Weeks, null -> CareGap.Weeks((days / DAYS_IN_WEEK).toInt())
    }
}

private const val DAYS_IN_WEEK = 7

/**
 * When this care is next due, said in words.
 *
 * **Overdue is carried indefinitely and without escalation.** A reminder three months late says so
 * plainly and says it in the same voice as one due tomorrow — the Care screen is where overdue state
 * lives precisely because it can hold it without shouting, which is what lets the notification fire
 * once and never again (ADR-0001).
 */
fun careDue(
    dueOn: LocalDate,
    today: LocalDate,
): CareDue =
    when {
        dueOn == today -> CareDue.Today
        dueOn == today.plusDays(1) -> CareDue.Tomorrow
        dueOn == today.minusDays(1) -> CareDue.Yesterday
        dueOn.isAfter(today) -> CareDue.In(careGap(today, dueOn))
        else -> CareDue.Overdue(careGap(dueOn, today))
    }
