package app.binky.tracker.ui.care

import app.binky.tracker.data.DoseEntity
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.DoseWindow
import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.MedicationTimeEntity
import app.binky.tracker.data.dueDoses
import app.binky.tracker.data.scheduledDoses
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What a course row says is coming next.
 *
 * The cases are all arithmetic over dates, times and one boolean, which is why they are here rather
 * than proven by hand on a phone at 08:00 — and why the fixture builds its slots through the real
 * `dueDoses` rather than by hand: a test that invented its own slots would pass while the derivation
 * it is standing on was broken.
 */
class DoseNextTest {
    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")
    private val today: LocalDate = LocalDate.of(2026, 8, 5)
    private val morning: LocalTime = LocalTime.of(8, 0)
    private val evening: LocalTime = LocalTime.of(20, 0)

    @Test
    fun `a course with no times is never due`() {
        val course = course()

        assertEquals(
            DoseNext.NoSchedule,
            doseNext(course, hasTimes = false, slots = emptyList(), now = at(9, 0), today = today),
        )
    }

    @Test
    fun `the next slot later today is named by its clock time`() {
        val course = course()
        val slots = slots(course, listOf(morning, evening))

        assertEquals(
            DoseNext.Today(evening),
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `an answered slot is not the next one`() {
        val course = course()
        val slots = slots(course, listOf(morning, evening), answered = setOf(today to evening))

        // Tonight is done, so the next one is tomorrow morning — the same sentence ADR-0025 uses for
        // the alarm, which is the point of sharing one definition with it.
        assertEquals(
            DoseNext.Tomorrow(morning),
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `a slot earlier today that nobody answered is not chased`() {
        val course = course()
        val slots = slots(course, listOf(morning, evening))

        // 08:00 has passed with no answer and it is 09:00. The row says what is *coming*; the
        // unanswered slot is in the day's list with both buttons live, which is the app reporting
        // the record rather than grading it (ADR-0026, ADR-0001).
        assertEquals(
            DoseNext.Today(evening),
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `a course that has not started names its start date rather than a countdown`() {
        val startOn = today.plusDays(3)
        val course = course(startOn = startOn)
        val slots = slots(course, listOf(morning))

        assertEquals(
            DoseNext.NotStarted(startOn),
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `a course that ended is over, not merely quiet`() {
        val endOn = today.minusDays(7)
        val course = course(startOn = today.minusDays(20), endOn = endOn)

        assertEquals(
            DoseNext.Ended(endOn),
            doseNext(course, hasTimes = true, slots = emptyList(), now = at(9, 0), today = today),
        )
    }

    @Test
    fun `a course ending today with every slot answered has nothing further`() {
        val course = course(endOn = today)
        val slots =
            slots(
                course,
                listOf(morning, evening),
                answered = setOf(today to morning, today to evening),
            )

        // Running — `endOn` is today, not behind us — but the clamp derives nothing past tonight and
        // both of tonight's answers are in. That is a different fact from [DoseNext.Ended].
        assertEquals(
            DoseNext.Done,
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `the boundary between today and tomorrow is local midnight`() {
        val course = course()
        val slots = slots(course, listOf(morning))

        // 23:59 today: this morning's slot is behind us, so the next is tomorrow's.
        assertEquals(
            DoseNext.Tomorrow(morning),
            doseNext(course, hasTimes = true, slots = slots, now = at(23, 59), today = today),
        )
    }

    @Test
    fun `a slot beyond tomorrow is named by its date`() {
        val course = course()
        // This morning's 08:00 is behind 09:00 and tomorrow's has already been answered, so the
        // earliest one still owing is the day after — which is too far off to be a word.
        val slots = slots(course, listOf(morning), answered = setOf(today.plusDays(1) to morning))

        assertEquals(
            DoseNext.Later(today.plusDays(2), morning),
            doseNext(course, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    @Test
    fun `another course's slots are ignored`() {
        val mine = course()
        val theirs = course()
        val slots = slots(theirs, listOf(morning, evening))

        // The screen holds one list covering every course this bunny is on, so filtering is this
        // function's job rather than something each caller has to remember.
        assertEquals(
            DoseNext.Done,
            doseNext(mine, hasTimes = true, slots = slots, now = at(9, 0), today = today),
        )
    }

    private fun course(
        startOn: LocalDate = today.minusDays(2),
        endOn: LocalDate? = null,
    ) = MedicationCourseEntity(
        bunnyId = "bunny",
        name = "Metacam",
        doseAmount = "0.3 ml",
        startOn = startOn,
        endOn = endOn,
    )

    /** This course's real derived slots over a week, with the named ones answered. */
    private fun slots(
        course: MedicationCourseEntity,
        times: List<LocalTime>,
        answered: Set<Pair<LocalDate, LocalTime>> = emptySet(),
        from: LocalDate = today,
    ) = scheduledDoses(
        course = course,
        slots =
            dueDoses(
                course = course,
                times = times.map { MedicationTimeEntity(courseId = course.id, time = it) },
                window = DoseWindow.of(from, days = NEXT_DOSE_DAYS),
                zone = zone,
            ),
        recorded =
            answered.map { (day, time) ->
                DoseEntity(
                    courseId = course.id,
                    scheduledOn = day,
                    scheduledTime = time,
                    status = DoseStatus.GIVEN,
                )
            },
    )

    private fun at(
        hour: Int,
        minute: Int,
    ): Instant = today.atTime(hour, minute).atZone(zone).toInstant()
}
