package app.binky.tracker.ui.care

import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.ScheduledDose
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * When a course's next dose falls, **in the words a row can show** — the medication half of what
 * [CareDue] does for a care reminder.
 *
 * The one deliberate difference from [CareDue] is that the *day* is relative and the *time* is not.
 * A care reminder has no clock time, so "Due in 3 days" is the whole fact; a dose has one, and it is
 * the vet's instruction. "In about four hours" makes an owner who was told *morning and night* do
 * arithmetic to get back to the number they were given, so the day is said relatively (today,
 * tomorrow, then a date) and the time is said exactly.
 *
 * **Nothing here reaches backwards.** There is no case for a slot that has passed unanswered,
 * because a row saying so would be the app chasing a dose after the fact (ADR-0026, ADR-0001). A
 * slot earlier today that nobody has answered is on screen anyway — it is in the day's list, with
 * both buttons still live — and that is the app reporting the record rather than grading it.
 *
 * Kotlin note: a `sealed interface` is a discriminated union — a `when` over one needs no `else`,
 * and stops compiling the day a case is added.
 */
sealed interface DoseNext {
    /** No times at all: doses are recorded by hand and nothing is ever due (ADR-0002). */
    data object NoSchedule : DoseNext

    /** Begins on a day the owner has not reached — "Starts on 12 August", not "in 3 days at 08:00". */
    data class NotStarted(
        val startOn: LocalDate,
    ) : DoseNext

    /** Over: the end date is behind us and the clamp in `dueDoses` derives nothing. */
    data class Ended(
        val endOn: LocalDate,
    ) : DoseNext

    data class Today(
        val at: LocalTime,
    ) : DoseNext

    data class Tomorrow(
        val at: LocalTime,
    ) : DoseNext

    data class Later(
        val on: LocalDate,
        val at: LocalTime,
    ) : DoseNext

    /**
     * Still running, but nothing further is derived — which happens on the last day of a course
     * whose remaining slots have all been answered. Rare, real, and not the same as [Ended].
     */
    data object Done : DoseNext
}

/**
 * How far ahead the medication screens derive, in days including today.
 *
 * One day would be enough for the day's list and wrong for everything above it: a course starting
 * tomorrow, or one whose slots today have all been answered, has a real next dose that a one-day
 * window cannot see, and a row saying nothing is coming would be false. A week is far enough that
 * anything beyond it is better said as a start date than as a countdown.
 */
internal const val NEXT_DOSE_DAYS = 8L

/**
 * The next dose [course] is waiting on, given the slots already derived for it.
 *
 * "Next" is **the earliest unanswered slot at or after [now]**, which is deliberately the same
 * sentence ADR-0025 uses for the one pending alarm: the row and the alarm must not be able to
 * disagree about which dose is coming, and the cheapest way to guarantee that is one definition.
 *
 * @param slots any window's worth of derived slots. Filtered to [course] here rather than trusted,
 *   because the screen holds one list covering every course this bunny is on.
 */
fun doseNext(
    course: MedicationCourseEntity,
    hasTimes: Boolean,
    slots: List<ScheduledDose>,
    now: Instant,
    today: LocalDate,
): DoseNext {
    if (!hasTimes) return DoseNext.NoSchedule
    course.endOn?.let { endOn -> if (endOn.isBefore(today)) return DoseNext.Ended(endOn) }
    if (course.startOn.isAfter(today)) return DoseNext.NotStarted(course.startOn)

    val next =
        slots
            .filter { it.course.id == course.id && !it.isAnswered && !it.due.at.isBefore(now) }
            .minByOrNull { it.due.at }
            ?: return DoseNext.Done

    return when (next.due.scheduledOn) {
        today -> DoseNext.Today(next.due.scheduledTime)
        today.plusDays(1) -> DoseNext.Tomorrow(next.due.scheduledTime)
        else -> DoseNext.Later(next.due.scheduledOn, next.due.scheduledTime)
    }
}
