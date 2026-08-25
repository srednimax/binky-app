package app.binky.tracker.ui.events

import app.binky.tracker.data.CompletedCare
import app.binky.tracker.data.EventEntity
import app.binky.tracker.data.ScheduledCare
import app.binky.tracker.data.VisitDetails
import java.time.LocalDate
import java.time.YearMonth

/*
 * **The timeline is derived and stores nothing** (ADR-0031).
 *
 * Four sources the app already keeps, merged on read: the owner's own events, vet visits, recorded
 * care completions and the next-due dates `CareSchedule` derives. Nothing here is written anywhere,
 * so back-dating a visit, deleting a completion or changing an interval all move the agenda with
 * nothing being told about it — the same rule ADR-0002 applies to a due date, applied to a feed.
 *
 * Everything in this file is pure, and that is where the risk actually is: "upcoming above past",
 * "an overdue reminder is still outstanding" and "a day holding four kinds sorts the same way twice"
 * are a case table, and a case table is a JVM test rather than something to squint at on a phone.
 *
 * **Weighings, observations and doses are deliberately absent.** Each already owns a screen with its
 * own timeline, and a feed that repeats them is noise rather than history. If they are ever wanted
 * they arrive as filter chips over this function, not as a wider default.
 */

/**
 * One dated thing on the agenda.
 *
 * Kotlin note: a sealed interface is a discriminated union — a `when` over it is exhaustive with no
 * `else` branch, so adding a fifth source becomes a compile error at every place that renders one
 * rather than a row that silently fails to draw.
 */
sealed interface TimelineEntry {
    /** Stable across emissions, so a list can key on it. Unique within a timeline. */
    val id: String

    /** The day this sits under. */
    val on: LocalDate

    /**
     * Whether this is something still owed rather than something that happened.
     *
     * The split the whole screen is built on, and it is **not** simply `on >= today`. An overdue
     * nail trim is outstanding on all twenty-one days it has been overdue — it belongs above the
     * fold with the things still to do, not filed in history under the month it was first due. The
     * Care screen already carries overdue state that way (ADR-0002); this is the same reading.
     */
    fun outstanding(today: LocalDate): Boolean

    /** A dated label the owner wrote down — the keepsake or the one-off appointment. */
    data class Event(
        val event: EventEntity,
    ) : TimelineEntry {
        override val id get() = "event:${event.id}"
        override val on get() = event.occursOn

        override fun outstanding(today: LocalDate) = !on.isBefore(today)
    }

    /** A vet visit, which is always something that happened: `visits.visitedOn` refuses the future. */
    data class VetVisit(
        val details: VisitDetails,
    ) : TimelineEntry {
        override val id get() = "visit:${details.visit.id}"
        override val on get() = details.visit.visitedOn

        override fun outstanding(today: LocalDate) = false
    }

    /** Care that was done, on the day it was done. Also always past — a completion refuses the future. */
    data class CareDone(
        val completion: CompletedCare,
    ) : TimelineEntry {
        override val id get() = "care-done:${completion.id}"
        override val on get() = completion.completedOn

        override fun outstanding(today: LocalDate) = false
    }

    /**
     * Care that is next owed, at the date `CareSchedule` derived for it.
     *
     * **Always outstanding**, including when the date is in the past — see [outstanding]. There is
     * exactly one of these per reminder, because a reminder has exactly one next occurrence.
     */
    data class CareDue(
        val scheduled: ScheduledCare,
    ) : TimelineEntry {
        override val id get() = "care-due:${scheduled.reminder.id}"
        override val on get() = scheduled.dueOn

        override fun outstanding(today: LocalDate) = true
    }
}

/**
 * A month's worth of entries, on one side of the fold.
 *
 * [upcoming] is carried rather than derived from [month], because the two genuinely disagree: an
 * overdue reminder puts a *past* month in the upcoming half, and a screen that recomputed the side
 * from the month would draw it under the wrong heading.
 */
data class TimelineSection(
    val month: YearMonth,
    val upcoming: Boolean,
    val entries: List<TimelineEntry>,
)

/**
 * Merges the four sources into month sections, **upcoming first and then past**.
 *
 * Within upcoming, soonest first — the next thing owed is the first thing read. Within past, most
 * recent first, which is what makes *"when was the last vet visit"* the answer at the top of the
 * lower half rather than something to scroll for.
 *
 * Ties inside one day are broken by [kindOrder] and then by [TimelineEntry.id], so a day carrying a
 * vet visit and two completions renders identically on every recomposition. Without it the order
 * would follow whichever flow emitted last, which is a list that reshuffles while being read.
 */
fun buildTimeline(
    events: List<EventEntity>,
    visits: List<VisitDetails>,
    completions: List<CompletedCare>,
    careDue: List<ScheduledCare>,
    today: LocalDate,
): List<TimelineSection> {
    val entries =
        buildList<TimelineEntry> {
            events.forEach { add(TimelineEntry.Event(it)) }
            visits.forEach { add(TimelineEntry.VetVisit(it)) }
            completions.forEach { add(TimelineEntry.CareDone(it)) }
            careDue.forEach { add(TimelineEntry.CareDue(it)) }
        }

    val (upcoming, past) = entries.partition { it.outstanding(today) }

    return sections(upcoming.sortedWith(byDay(ascending = true)), upcoming = true) +
        sections(past.sortedWith(byDay(ascending = false)), upcoming = false)
}

/**
 * The single next thing owed and the last few that happened — Home's card, and nothing more
 * (ADR-0031).
 *
 * Derived from the same [buildTimeline] output the screen renders, rather than from a second query
 * with its own ordering: a card that disagreed with the screen it links to would be worse than no
 * card. [upcomingCount] and [pastCount] are parameters only so the test can pin the boundary; Home
 * passes 1 and 2.
 */
fun timelineHighlights(
    sections: List<TimelineSection>,
    upcomingCount: Int = 1,
    pastCount: Int = 2,
): List<TimelineEntry> {
    val (upcoming, past) = sections.partition { it.upcoming }
    return upcoming.flatMap { it.entries }.take(upcomingCount) +
        past.flatMap { it.entries }.take(pastCount)
}

/**
 * Which kind wins a tie inside one day.
 *
 * The owner's own record first, because it is the only one on the list they typed; then what is
 * owed, then what happened. Arbitrary in the sense that any fixed order would do — load-bearing only
 * in that it *is* fixed.
 */
private fun kindOrder(entry: TimelineEntry): Int =
    when (entry) {
        is TimelineEntry.Event -> 0
        is TimelineEntry.CareDue -> 1
        is TimelineEntry.VetVisit -> 2
        is TimelineEntry.CareDone -> 3
    }

/**
 * Kotlin note: `compareBy`/`compareByDescending` build a `Comparator` from key selectors, the way a
 * JS `sort((a, b) => …)` would be written by hand — `thenBy` chains the tie-breaks. The day reverses
 * with [ascending]; the two tie-breaks never do, so one day's rows read the same in both halves.
 */
private fun byDay(ascending: Boolean): Comparator<TimelineEntry> {
    val byDate =
        if (ascending) {
            compareBy<TimelineEntry> { it.on }
        } else {
            compareByDescending<TimelineEntry> { it.on }
        }
    return byDate.thenBy { kindOrder(it) }.thenBy { it.id }
}

/**
 * Groups an already-sorted list into months **without re-sorting**.
 *
 * `groupBy` preserves encounter order for both the keys and the values, which is the whole reason
 * the sort happens first: the months come out in the order their entries did, so a descending past
 * half yields descending months for free rather than needing a second comparator that could
 * disagree with the first.
 */
private fun sections(
    sorted: List<TimelineEntry>,
    upcoming: Boolean,
): List<TimelineSection> =
    sorted
        .groupBy { YearMonth.from(it.on) }
        .map { (month, entries) -> TimelineSection(month = month, upcoming = upcoming, entries = entries) }
