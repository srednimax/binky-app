package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The dose derivation as a **table of cases**, in the shape `CareScheduleTest` set: every clause
 * ADR-0002 and the plan commit to has a case here that fails if it is quietly changed.
 *
 * The two awkward days are the reason this is a test and not a paragraph. A dose derived twice across
 * a fall-back hour is a dose an owner is asked for twice, and one lost in a spring-forward gap is one
 * they are never asked for at all — both are one line of `java.time` behaviour that nothing else in
 * the app would notice going wrong.
 *
 * Plain `java.time` — no Room, no Android, no coroutines — which is why `dueDoses` takes a course and
 * its time rows rather than a bunny id.
 */
class DoseScheduleTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val london = ZoneId.of("Europe/London")
    private val utc = ZoneId.of("UTC")

    /** Fixed, so "today" in a test is never the machine's actual date. */
    private val today = LocalDate.of(2026, 5, 20)
    private val eightAm = LocalTime.of(8, 0)
    private val eightPm = LocalTime.of(20, 0)

    private fun course(
        startOn: LocalDate = today,
        endOn: LocalDate? = null,
    ) = MedicationCourseEntity(
        id = "course",
        bunnyId = "bunny",
        name = "Metacam",
        doseAmount = "0.3 ml",
        startOn = startOn,
        endOn = endOn,
    )

    private fun times(vararg at: LocalTime) = at.map { MedicationTimeEntity(courseId = "course", time = it) }

    private fun List<DueDose>.keys() = map { it.scheduledOn to it.scheduledTime }

    // ---- One slot per time per day ---------------------------------------------------------------

    @Test
    fun `a twice-daily course derives both times, in clock order, for every day of the window`() {
        val slots = dueDoses(course(), times(eightPm, eightAm), DoseWindow.of(today, days = 2), warsaw)

        assertEquals(
            listOf(
                today to eightAm,
                today to eightPm,
                today.plusDays(1) to eightAm,
                today.plusDays(1) to eightPm,
            ),
            slots.keys(),
        )
    }

    @Test
    fun `a window is one day by default, which is what a screen asks for`() {
        val slots = dueDoses(course(), times(eightAm), DoseWindow.of(today), warsaw)
        assertEquals(listOf(today to eightAm), slots.keys())
    }

    @Test
    fun `the instant is the wall-clock time resolved in the given zone`() {
        // The same 08:00 slot, two zones, two instants — and the slot's identity is unchanged by that
        // (ADR-0002), which is the case two tests below.
        val inWarsaw = dueDoses(course(), times(eightAm), DoseWindow.of(today), warsaw).single()
        val inLondon = dueDoses(course(), times(eightAm), DoseWindow.of(today), london).single()

        assertEquals(Instant.parse("2026-05-20T06:00:00Z"), inWarsaw.at)
        assertEquals(Instant.parse("2026-05-20T07:00:00Z"), inLondon.at)
    }

    // ---- A course with no schedule ---------------------------------------------------------------

    @Test
    fun `a course with no times has no slots, ever`() {
        // Scheduling is optional per course (ADR-0002): this is a course the owner records doses
        // against by hand, not one due at some default hour this function invented. A year's window
        // rather than a day's, because "ever" is the claim.
        val slots = dueDoses(course(), emptyList(), DoseWindow.of(today, days = 365), warsaw)
        assertTrue(slots.isEmpty())
    }

    // ---- The clamp: [max(startOn, today), endOn ?: ∞] ---------------------------------------------

    @Test
    fun `a course that started weeks ago derives nothing for the days already gone`() {
        // The window cannot open before today — that is DoseWindow's constructor — so a course
        // running since April contributes today's slot and no arrears. A past day is what was
        // *recorded*, never a slot nothing answered (ADR-0002, ADR-0001).
        val slots = dueDoses(course(startOn = today.minusWeeks(3)), times(eightAm), DoseWindow.of(today), warsaw)

        assertEquals(listOf(today to eightAm), slots.keys())
    }

    @Test
    fun `a course starting in the future derives nothing until it starts`() {
        val startsIn3Days = course(startOn = today.plusDays(3))

        assertTrue(dueDoses(startsIn3Days, times(eightAm), DoseWindow.of(today, days = 3), warsaw).isEmpty())
        assertEquals(
            listOf(today.plusDays(3) to eightAm),
            dueDoses(startsIn3Days, times(eightAm), DoseWindow.of(today, days = 4), warsaw).keys(),
        )
    }

    @Test
    fun `a course ending today derives today and stops`() {
        val slots =
            dueDoses(
                course(startOn = today.minusDays(5), endOn = today),
                times(eightAm),
                DoseWindow.of(today, days = 7),
                warsaw,
            )

        assertEquals(listOf(today to eightAm), slots.keys())
    }

    @Test
    fun `a course that ended yesterday derives nothing at all`() {
        val slots =
            dueDoses(
                course(startOn = today.minusMonths(1), endOn = today.minusDays(1)),
                times(eightAm),
                DoseWindow.of(today, days = 30),
                warsaw,
            )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `an open course is bounded only by the window`() {
        val slots = dueDoses(course(endOn = null), times(eightAm), DoseWindow.of(today, days = 10), warsaw)
        assertEquals(10, slots.size)
    }

    @Test
    fun `shortening a course drops its future slots`() {
        // Not a code path — a consequence of deriving. The recorded rows are untouched because
        // nothing ever wrote a future one; that half is asserted in MedicationRepositoryTest.
        val window = DoseWindow.of(today, days = 5)
        val open = course(startOn = today.minusDays(2))
        assertEquals(5, dueDoses(open, times(eightAm), window, warsaw).size)

        val closedToday = open.copy(endOn = today)
        assertEquals(1, dueDoses(closedToday, times(eightAm), window, warsaw).size)
    }

    // ---- The two awkward days --------------------------------------------------------------------

    @Test
    fun `a slot inside the spring-forward gap happens once, pushed past the gap`() {
        // Warsaw, 29 March 2026: 02:00 to 03:00 does not exist. A 02:30 dose is derived exactly once
        // — java.time shifts it by the length of the gap, to 03:30 local. Never zero times: an owner
        // is not asked for a dose that never comes, and the alarm has an instant to be placed at.
        val springForward = LocalDate.of(2026, 3, 29)
        val slots =
            dueDoses(
                course(startOn = springForward),
                times(LocalTime.of(2, 30)),
                DoseWindow.of(springForward),
                warsaw,
            )

        assertEquals(1, slots.size)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), slots.single().at)
        assertEquals(
            "the stored key is the wall-clock time the owner set, not the one it resolved to",
            LocalTime.of(2, 30),
            slots.single().scheduledTime,
        )
    }

    @Test
    fun `a slot inside the fall-back overlap happens once, at the earlier offset`() {
        // Warsaw, 25 October 2026: 02:00 to 03:00 happens twice. A 02:30 dose is derived once, at
        // +02:00 — never twice, which would be the app asking for the same dose an hour apart.
        val fallBack = LocalDate.of(2026, 10, 25)
        val slots =
            dueDoses(
                course(startOn = fallBack),
                times(LocalTime.of(2, 30)),
                DoseWindow.of(fallBack),
                warsaw,
            )

        assertEquals(1, slots.size)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), slots.single().at)
    }

    @Test
    fun `a course spanning a DST change keeps one slot a day`() {
        val fallBack = LocalDate.of(2026, 10, 25)
        val slots =
            dueDoses(
                course(startOn = fallBack.minusDays(1)),
                times(eightAm),
                DoseWindow.of(fallBack.minusDays(1), days = 3),
                warsaw,
            )

        assertEquals(3, slots.size)
        // 08:00 stays 08:00 either side of the change; only the offset behind it moves.
        assertEquals(Instant.parse("2026-10-24T06:00:00Z"), slots[0].at)
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), slots[1].at)
    }

    // ---- The local key, which is the whole reason the key is local -------------------------------

    @Test
    fun `a zone change leaves an answered slot answered`() {
        // The owner answers the 08:00 dose in Warsaw and flies to London. The slot re-derives at a
        // different *instant* and the same local key, so the row still answers it — an instant-keyed
        // row would read as unanswered here and re-arm its own alarm (ADR-0002).
        val inWarsaw = dueDoses(course(), times(eightAm), DoseWindow.of(today), warsaw).single()
        val given =
            DoseEntity(
                courseId = inWarsaw.courseId,
                scheduledOn = inWarsaw.scheduledOn,
                scheduledTime = inWarsaw.scheduledTime,
                status = DoseStatus.GIVEN,
            )

        val inLondon = dueDoses(course(), times(eightAm), DoseWindow.of(today), london).single()

        assertTrue("the same slot", given.answers(inLondon))
        assertTrue("at a different instant", inWarsaw.at != inLondon.at)
    }

    @Test
    fun `an ad-hoc dose answers no slot`() {
        val slot = dueDoses(course(), times(eightAm), DoseWindow.of(today), utc).single()
        val adHoc = DoseEntity(courseId = slot.courseId, status = DoseStatus.GIVEN)

        assertTrue(!adHoc.answers(slot))
    }

    @Test
    fun `another course's row does not answer this course's slot`() {
        val slot = dueDoses(course(), times(eightAm), DoseWindow.of(today), utc).single()
        val elsewhere =
            DoseEntity(
                courseId = "another-course",
                scheduledOn = slot.scheduledOn,
                scheduledTime = slot.scheduledTime,
                status = DoseStatus.GIVEN,
            )

        assertTrue(!elsewhere.answers(slot))
    }

    // ---- Slots paired with what was said about them ----------------------------------------------

    @Test
    fun `an unanswered slot carries no row and is not a miss`() {
        val course = course()
        val slots = dueDoses(course, times(eightAm, eightPm), DoseWindow.of(today), utc)
        val morning =
            DoseEntity(
                courseId = course.id,
                scheduledOn = today,
                scheduledTime = eightAm,
                status = DoseStatus.SKIPPED,
            )

        val paired = scheduledDoses(course, slots, listOf(morning))

        assertEquals(DoseStatus.SKIPPED, paired[0].recorded?.status)
        assertTrue(paired[0].isAnswered)
        assertNull("nobody has said anything about the evening yet (ADR-0026)", paired[1].recorded)
        assertTrue(!paired[1].isAnswered)
    }

    // ---- The window itself -----------------------------------------------------------------------

    @Test
    fun `a window of zero days is refused rather than silently empty`() {
        val refusal = runCatching { DoseWindow.of(today, days = 0) }
        assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
    }
}
