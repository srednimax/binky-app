package app.binky.tracker.ui.events

import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareReminderEntity
import app.binky.tracker.data.CareType
import app.binky.tracker.data.CompletedCare
import app.binky.tracker.data.EventEntity
import app.binky.tracker.data.ScheduledCare
import app.binky.tracker.data.VisitDetails
import app.binky.tracker.data.VisitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The timeline as a case table (ADR-0031).
 *
 * Everything in `Timeline.kt` is pure, and that is where the risk actually is. "Upcoming above
 * past", "an overdue reminder is still outstanding" and "a day holding four kinds sorts the same way
 * twice" are exactly the claims that look right on a phone with three rows and go wrong on a phone
 * with three hundred.
 */
class TimelineTest {
    private val today = LocalDate.of(2026, 3, 14)

    @Test
    fun `upcoming comes above past, soonest first and then most recent first`() {
        val sections =
            buildTimeline(
                events = listOf(event("far", today.plusMonths(2)), event("soon", today.plusDays(2))),
                visits = listOf(visit("old", today.minusMonths(2)), visit("recent", today.minusDays(3))),
                completions = emptyList(),
                careDue = emptyList(),
                today = today,
            )

        assertEquals(listOf(true, true, false, false), sections.map { it.upcoming })
        // The next thing owed is the first thing read; the last thing that happened is the first
        // thing under the fold, which is what makes "when was the last vet visit" a glance.
        assertEquals(
            listOf("soon", "far", "recent", "old"),
            sections.flatMap { section -> section.entries.map { it.title() } },
        )
    }

    @Test
    fun `an event on today is upcoming, not history`() {
        // The boundary. An appointment this afternoon belongs above the fold, and a day is the
        // smallest unit the timeline knows about — so "today" cannot be split by a clock.
        val sections =
            buildTimeline(
                events = listOf(event("today", today)),
                visits = emptyList(),
                completions = emptyList(),
                careDue = emptyList(),
                today = today,
            )

        assertTrue(sections.single().upcoming)
    }

    @Test
    fun `an overdue reminder stays outstanding and drags a past month above the fold`() {
        // The one place `upcoming` and `month` genuinely disagree, and the reason `TimelineSection`
        // carries the side rather than deriving it: a nail trim twenty-one days late belongs with
        // the things still to do, under a heading of the month it was first due.
        val sections =
            buildTimeline(
                events = emptyList(),
                visits = emptyList(),
                completions = emptyList(),
                careDue = listOf(careDue("nail trim", today.minusWeeks(3))),
                today = today,
            )

        val section = sections.single()
        assertTrue(section.upcoming)
        assertEquals(YearMonth.of(2026, 2), section.month)
    }

    @Test
    fun `a day holding all four kinds sorts the same way every time`() {
        // Without a fixed tie-break the order would follow whichever flow emitted last, which is a
        // list that reshuffles while it is being read. The owner's own record first, then what is
        // owed, then what happened.
        val onTheDay = today.minusDays(1)
        val sections =
            buildTimeline(
                // Deliberately handed over in the wrong order, so a pass cannot come from luck.
                events = listOf(event("event", onTheDay)),
                visits = listOf(visit("visit", onTheDay)),
                completions = listOf(completion("done", onTheDay)),
                careDue = listOf(careDue("due", onTheDay)),
                today = today,
            )

        // The due one is outstanding whatever its date, so it leaves the day and goes above the
        // fold on its own — the remaining three are what share the past day.
        val past = sections.single { !it.upcoming }
        assertEquals(listOf("event", "visit", "done"), past.entries.map { it.title() })
    }

    @Test
    fun `entries group into months in the order their entries arrived`() {
        val sections =
            buildTimeline(
                events = emptyList(),
                visits =
                    listOf(
                        visit("january", LocalDate.of(2026, 1, 20)),
                        visit("february", LocalDate.of(2026, 2, 2)),
                        visit("also february", LocalDate.of(2026, 2, 26)),
                    ),
                completions = emptyList(),
                careDue = emptyList(),
                today = today,
            )

        assertEquals(listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 1)), sections.map { it.month })
        assertEquals(listOf("also february", "february"), sections.first().entries.map { it.title() })
    }

    @Test
    fun `every entry id is unique, so a list can key on it`() {
        // Four kinds drawn from four tables, and two of them can legitimately share a raw id — a
        // completion and the reminder it belongs to. The prefix is what keeps them apart.
        val sections =
            buildTimeline(
                events = listOf(event("event", today)),
                visits = listOf(visit("visit", today.minusDays(1))),
                completions = listOf(completion("done", today.minusDays(1))),
                careDue = listOf(careDue("due", today.plusDays(1))),
                today = today,
            )

        val ids = sections.flatMap { section -> section.entries.map { it.id } }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `highlights take the next thing owed and the last two that happened`() {
        val sections =
            buildTimeline(
                events = listOf(event("next", today.plusDays(1)), event("after that", today.plusDays(5))),
                visits =
                    listOf(
                        visit("yesterday", today.minusDays(1)),
                        visit("last week", today.minusWeeks(1)),
                        visit("last year", today.minusYears(1)),
                    ),
                completions = emptyList(),
                careDue = emptyList(),
                today = today,
            )

        val highlights = timelineHighlights(sections, upcomingCount = 1, pastCount = 2)

        assertEquals(listOf("next", "yesterday", "last week"), highlights.map { it.title() })
    }

    @Test
    fun `highlights of an empty timeline are empty, and of a one-sided one are that side`() {
        assertTrue(timelineHighlights(emptyList()).isEmpty())

        val onlyPast =
            buildTimeline(
                events = emptyList(),
                visits = listOf(visit("only", today.minusDays(2))),
                completions = emptyList(),
                careDue = emptyList(),
                today = today,
            )

        assertEquals(listOf("only"), timelineHighlights(onlyPast).map { it.title() })
    }

    /** The one label each kind carries, so a test can name a row without rendering it. */
    private fun TimelineEntry.title(): String =
        when (this) {
            is TimelineEntry.Event -> event.label
            is TimelineEntry.VetVisit -> details.visit.reason
            is TimelineEntry.CareDone -> completion.label.orEmpty()
            is TimelineEntry.CareDue -> scheduled.reminder.label.orEmpty()
        }

    private fun event(
        label: String,
        on: LocalDate,
    ) = EventEntity(id = "event-$label", bunnyId = "bunny", label = label, occursOn = on)

    private fun visit(
        reason: String,
        on: LocalDate,
    ) = VisitDetails(
        visit = VisitEntity(id = "visit-$reason", bunnyId = "bunny", visitedOn = on, reason = reason),
        vetName = null,
        weightId = null,
        weightGrams = null,
    )

    private fun completion(
        label: String,
        on: LocalDate,
    ) = CompletedCare(
        // Deliberately the same raw id as the reminder below would carry, which is what the `id`
        // uniqueness test is about.
        id = "care-$label",
        reminderId = "care-$label",
        completedOn = on,
        note = null,
        label = label,
        type = null,
    )

    private fun careDue(
        label: String,
        dueOn: LocalDate,
    ) = ScheduledCare(
        reminder =
            CareReminderEntity(
                id = "care-$label",
                bunnyId = "bunny",
                label = label,
                type = CareType.NAIL_TRIM,
                intervalCount = 6,
                intervalUnit = CareIntervalUnit.WEEK,
                firstDueOn = dueOn,
            ),
        lastCompletedOn = null,
        dueOn = dueOn,
    )
}
