package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The care schedule as a **table of cases**, in the shape `WeightTrendTest` set: every clause the
 * plan and ADR-0018 commit to has a case here that fails if it is quietly changed.
 *
 * These are the arguments that are hard to have twice. "Overdue resets rather than owes", "the
 * day-of-month is allowed to walk" and "the zone is resolved at read time" are each one line of code
 * and one paragraph of reasoning, and the line is the easy half to get wrong later.
 *
 * Plain `java.time` arithmetic — no Room, no Android, no coroutines — which is why `CareSchedule.kt`
 * takes dates and intervals rather than entities where it can.
 */
class CareScheduleTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val nineAm = LocalTime.of(9, 0)

    private fun weeks(count: Int) = CareInterval(count, CareIntervalUnit.WEEK)

    private fun months(count: Int) = CareInterval(count, CareIntervalUnit.MONTH)

    private fun years(count: Int) = CareInterval(count, CareIntervalUnit.YEAR)

    // ---- The anchor path: a reminder never completed --------------------------------------------

    @Test
    fun `a reminder never completed is due on its anchor, unmodified`() {
        // firstDueOn is a *due date*, not a pseudo-completion. The form asks "when is this next
        // due?" — the question an owner can answer off a vet card — so the answer is used as given.
        assertEquals(
            LocalDate.of(2026, 3, 14),
            careDueOn(anchor = LocalDate.of(2026, 3, 14), lastCompletedOn = null, interval = weeks(6)),
        )
    }

    @Test
    fun `an anchor long past stays past rather than rolling forward`() {
        // The tempting alternative is advancing the anchor by whole intervals until it is in the
        // future. That would silently mark a year of missed vaccinations as never having been due,
        // which is the opposite of what an overdue row is for.
        assertEquals(
            LocalDate.of(2024, 1, 5),
            careDueOn(anchor = LocalDate.of(2024, 1, 5), lastCompletedOn = null, interval = years(1)),
        )
    }

    // ---- Overdue does not drift ------------------------------------------------------------------

    @Test
    fun `an overdue completion resets the interval rather than owing it`() {
        // Due 1 January, trimmed on the 22nd, three weeks late. The next trim is six weeks from the
        // trim — 5 March — not six weeks from the date it was owed, which would be 12 February.
        val next =
            careDueOn(
                anchor = LocalDate.of(2026, 1, 1),
                lastCompletedOn = LocalDate.of(2026, 1, 22),
                interval = weeks(6),
            )
        assertEquals(LocalDate.of(2026, 3, 5), next)
    }

    @Test
    fun `a back-dated completion moves the next date backwards`() {
        // "I did the nail trim on Monday, I'm logging it on Friday" schedules from Monday. The
        // recorded completion is the only anchor once one exists.
        assertEquals(
            LocalDate.of(2026, 2, 16),
            careDueOn(
                anchor = LocalDate.of(2026, 1, 1),
                lastCompletedOn = LocalDate.of(2026, 1, 5),
                interval = weeks(6),
            ),
        )
    }

    // ---- Calendar units, and the clamping taken deliberately (ADR-0018) --------------------------

    @Test
    fun `31 January plus one month clamps to 28 February, and then walks`() {
        val february = careDueOn(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 31), months(1))
        assertEquals(LocalDate.of(2026, 2, 28), february)

        // Completing on the clamped date settles the reminder onto the 28th permanently. That is the
        // accepted cost of calendar intervals: preserving an intended day-of-month would mean
        // storing an intention beside the completion history, two facts that can disagree.
        assertEquals(
            LocalDate.of(2026, 3, 28),
            careDueOn(LocalDate.of(2025, 1, 1), february, months(1)),
        )
    }

    @Test
    fun `29 February plus one year clamps to 28 February`() {
        assertEquals(
            LocalDate.of(2029, 2, 28),
            careDueOn(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 2, 29), years(1)),
        )
    }

    @Test
    fun `a yearly interval lands on the anniversary, which 365 days would not`() {
        // A year spanning 29 February is 366 days, so "365 days later" would put the vaccination a
        // day before its anniversary — and a day further out every leap year after that. The
        // calendar unit is what keeps the app and the FREQ=YEARLY event it hands the owner's
        // calendar (ADR-0014) saying the same thing.
        assertEquals(
            LocalDate.of(2028, 3, 14),
            careDueOn(LocalDate.of(2020, 1, 1), LocalDate.of(2027, 3, 14), years(1)),
        )
    }

    // ---- The zone is resolved at read time, never stored (ADR-0003) ------------------------------

    @Test
    fun `a due date crossing into summer time still resolves to nine in the morning`() {
        // Completed 22 March (CET, UTC+1), due a week later on 29 March — the day Warsaw springs
        // forward, so the due date is CEST, UTC+2. An occurrence computed as "completion instant plus
        // seven days" would land at 10:00 local; resolving the date in the current zone lands at 09:00.
        val occurrence =
            nextOccurrence(
                anchor = LocalDate.of(2026, 3, 1),
                lastCompletedOn = LocalDate.of(2026, 3, 22),
                interval = weeks(1),
                reminderTime = nineAm,
                zone = warsaw,
            )

        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), occurrence)
        assertEquals(nineAm, occurrence.atZone(warsaw).toLocalTime())
    }

    @Test
    fun `a reminder time inside the spring-forward gap shifts to the first valid instant`() {
        // 02:30 does not exist on 29 March 2026 in Warsaw. java.time's default resolution moves it
        // forward by the gap rather than throwing or silently skipping the day — never zero times.
        val occurrence =
            nextOccurrence(
                anchor = LocalDate.of(2026, 3, 29),
                lastCompletedOn = null,
                interval = weeks(1),
                reminderTime = LocalTime.of(2, 30),
                zone = warsaw,
            )

        assertEquals(LocalTime.of(3, 30), occurrence.atZone(warsaw).toLocalTime())
    }

    @Test
    fun `a reminder time inside the autumn overlap takes the earlier offset`() {
        // 02:30 happens twice on 25 October 2026. The earlier one is chosen — never twice.
        val occurrence =
            nextOccurrence(
                anchor = LocalDate.of(2026, 10, 25),
                lastCompletedOn = null,
                interval = weeks(1),
                reminderTime = LocalTime.of(2, 30),
                zone = warsaw,
            )

        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), occurrence)
    }

    @Test
    fun `the same due date is a different instant in a different zone`() {
        val date = LocalDate.of(2026, 6, 1)
        val here = nextOccurrence(date, null, weeks(1), nineAm, warsaw)
        val there = nextOccurrence(date, null, weeks(1), nineAm, ZoneId.of("America/New_York"))
        assertTrue("09:00 means 09:00 where the owner is", here < there)
    }

    // ---- A weigh-in's last completion counts weights (ADR-0018's amendment) ----------------------

    @Test
    fun `a weigh-in takes the later of its completions and the weight series`() {
        assertEquals(
            LocalDate.of(2026, 1, 5),
            lastCompletedOn(
                type = CareType.WEIGH_IN,
                latestEventOn = LocalDate.of(2026, 1, 1),
                latestWeightOn = LocalDate.of(2026, 1, 5),
            ),
        )
        assertEquals(
            LocalDate.of(2026, 1, 9),
            lastCompletedOn(
                type = CareType.WEIGH_IN,
                latestEventOn = LocalDate.of(2026, 1, 9),
                latestWeightOn = LocalDate.of(2026, 1, 5),
            ),
        )
    }

    @Test
    fun `a weighing alone satisfies a weigh-in reminder`() {
        // Otherwise the app tells the owner a weigh-in is overdue while holding the weight that
        // proves it was done — ADR-0001's principle running in the other direction.
        assertEquals(
            LocalDate.of(2026, 1, 5),
            lastCompletedOn(CareType.WEIGH_IN, latestEventOn = null, latestWeightOn = LocalDate.of(2026, 1, 5)),
        )
    }

    @Test
    fun `every other kind ignores the weight series`() {
        // A nail trim is not done by weighing the bunny, and neither is a custom reminder.
        assertEquals(
            LocalDate.of(2026, 1, 1),
            lastCompletedOn(CareType.NAIL_TRIM, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1),
            lastCompletedOn(
                type = null,
                latestEventOn = LocalDate.of(2026, 1, 1),
                latestWeightOn = LocalDate.of(2026, 1, 5),
            ),
        )
        assertNull(lastCompletedOn(CareType.NAIL_TRIM, latestEventOn = null, latestWeightOn = LocalDate.of(2026, 1, 5)))
    }

    // ---- Due, overdue, and notifying exactly once ------------------------------------------------

    @Test
    fun `due is inclusive of the day itself and overdue is not`() {
        val schedule = scheduleFor(reminder(firstDueOn = LocalDate.of(2026, 5, 10)), lastCompletedOn = null)

        assertFalse(schedule.isDueBy(LocalDate.of(2026, 5, 9)))
        assertTrue(schedule.isDueBy(LocalDate.of(2026, 5, 10)))
        assertFalse(schedule.isOverdueOn(LocalDate.of(2026, 5, 10)))
        assertTrue(schedule.isOverdueOn(LocalDate.of(2026, 5, 11)))
    }

    @Test
    fun `a due reminder notifies once and never again`() {
        val due = LocalDate.of(2026, 5, 10)
        val schedule = scheduleFor(reminder(firstDueOn = due), lastCompletedOn = null)
        assertTrue(schedule.needsNotifying(due))

        // What the sweep records afterwards: the due date it posted *for*.
        val posted = scheduleFor(reminder(firstDueOn = due, notifiedForDueOn = due), lastCompletedOn = null)
        assertFalse(posted.needsNotifying(due))
        assertFalse("still overdue a week later, and still silent", posted.needsNotifying(due.plusWeeks(1)))
    }

    @Test
    fun `the watermark goes stale the moment a completion moves the date`() {
        // The whole reason the column stores a due date rather than a timestamp: nothing clears it,
        // and every path that moves the schedule invalidates it for free.
        val due = LocalDate.of(2026, 5, 10)
        val posted = reminder(firstDueOn = due, notifiedForDueOn = due)

        val afterCompleting = scheduleFor(posted, lastCompletedOn = LocalDate.of(2026, 5, 12))
        assertEquals(LocalDate.of(2026, 6, 23), afterCompleting.dueOn)
        assertFalse("not due yet", afterCompleting.needsNotifying(LocalDate.of(2026, 6, 1)))
        assertTrue(
            "due again, and the stale watermark does not silence it",
            afterCompleting.needsNotifying(LocalDate.of(2026, 6, 23)),
        )
    }

    @Test
    fun `editing the interval also invalidates the watermark, with nothing cleared`() {
        val due = LocalDate.of(2026, 5, 10)
        val posted = reminder(firstDueOn = due, notifiedForDueOn = due, interval = weeks(6))
        val completedOn = LocalDate.of(2026, 5, 10)

        val asSixWeeks = scheduleFor(posted, completedOn)
        val asFourWeeks = scheduleFor(posted.copy(intervalCount = 4), completedOn)

        assertEquals(LocalDate.of(2026, 6, 21), asSixWeeks.dueOn)
        assertEquals(LocalDate.of(2026, 6, 7), asFourWeeks.dueOn)
        assertTrue(asFourWeeks.needsNotifying(LocalDate.of(2026, 6, 7)))
    }

    @Test
    fun `today is the device's day, not UTC's`() {
        // 00:30 in Warsaw on 2 June is still 1 June in UTC. A sweep that asked UTC would treat a
        // reminder due on the 2nd as not yet due, on the owner's own morning.
        val justAfterMidnight = Instant.parse("2026-06-01T22:30:00Z")
        assertEquals(LocalDate.of(2026, 6, 2), today(justAfterMidnight, warsaw))
        assertEquals(LocalDate.of(2026, 6, 1), today(justAfterMidnight, ZoneId.of("UTC")))
    }

    // ---- The occurrence a schedule reports -------------------------------------------------------

    @Test
    fun `a schedule reports the same instant the pure function does`() {
        val schedule = scheduleFor(reminder(firstDueOn = LocalDate.of(2026, 6, 1)), lastCompletedOn = null)
        assertEquals(
            nextOccurrence(LocalDate.of(2026, 6, 1), null, weeks(6), nineAm, warsaw),
            schedule.occurrenceAt(nineAm, warsaw),
        )
    }

    private fun reminder(
        firstDueOn: LocalDate,
        notifiedForDueOn: LocalDate? = null,
        interval: CareInterval = weeks(6),
    ) = CareReminderEntity(
        id = "reminder",
        bunnyId = "bunny",
        type = CareType.NAIL_TRIM,
        intervalCount = interval.count,
        intervalUnit = interval.unit,
        firstDueOn = firstDueOn,
        notifiedForDueOn = notifiedForDueOn,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
