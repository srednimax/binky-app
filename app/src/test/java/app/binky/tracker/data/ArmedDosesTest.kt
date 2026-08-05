package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * **What the single pending dose alarm is armed from**, as a table of cases (ADR-0025, PLAN 5f).
 *
 * The exclusions live in [armedDoses] rather than in the DAO's `WHERE` clause precisely so they can
 * be asserted here — the same choice `careDueForNotifying` made, for the same reason: a rule in SQL
 * is a rule no JVM test can read. Each case pins one sentence ADR-0025 commits to, and the failure
 * mode of every one of them is **silence** — an alarm not armed looks exactly like an app correctly
 * quiet, on the feature where being quiet is worst.
 *
 * Plain `java.time` and plain data classes: no Room, no Android, no coroutines.
 */
class ArmedDosesTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")

    /** Fixed, so "today" in a test is never the machine's actual date. */
    private val today = LocalDate.of(2026, 5, 20)
    private val eightAm = LocalTime.of(8, 0)
    private val eightPm = LocalTime.of(20, 0)

    private fun course(
        id: String = "course",
        bunnyId: String = "bijou",
        name: String = "Metacam",
        startOn: LocalDate = today,
        endOn: LocalDate? = null,
        remindersEnabled: Boolean = true,
    ) = MedicationCourseEntity(
        id = id,
        bunnyId = bunnyId,
        name = name,
        doseAmount = "0.3 ml",
        startOn = startOn,
        endOn = endOn,
        remindersEnabled = remindersEnabled,
    )

    private fun armed(
        course: MedicationCourseEntity = course(),
        vararg at: LocalTime,
        bunnyName: String = "Bijou",
        bunnyArchivedAt: Instant? = null,
    ) = ArmedCourse(
        course = course,
        bunnyName = bunnyName,
        bunnyArchivedAt = bunnyArchivedAt,
        times = at.map { MedicationTimeEntity(courseId = course.id, time = it) },
    )

    private fun answer(
        courseId: String = "course",
        on: LocalDate = today,
        at: LocalTime = eightAm,
        status: DoseStatus = DoseStatus.GIVEN,
    ) = DoseEntity(courseId = courseId, scheduledOn = on, scheduledTime = at, status = status)

    private fun derive(
        courses: List<ArmedCourse>,
        answers: List<DoseEntity> = emptyList(),
        days: Long = DOSE_HORIZON_DAYS,
        zone: ZoneId = warsaw,
    ) = armedDoses(courses, answers, DoseWindow.of(today, days), zone)

    /** The slot's key — the local pair that identifies it, never its instant (ADR-0002). */
    private fun ArmedDose.key() = due.scheduledOn to due.scheduledTime

    // ---- The earliest slot -----------------------------------------------------------------------

    @Test
    fun `slots come back chronological, so the earliest is simply the first`() {
        // The whole reason `rescheduleDoseAlarm` can say "the first one the grace predicate accepts"
        // rather than searching: the order is this function's promise, not the caller's job.
        val slots = derive(listOf(armed(at = arrayOf(eightPm, eightAm)))).map { it.due.at }

        assertEquals(slots.sorted(), slots)
        assertEquals(today.atTime(eightAm).atZone(warsaw).toInstant(), slots.first())
    }

    @Test
    fun `an answered slot is skipped and the next one becomes the earliest`() {
        // What moves the alarm on after a tap in the shade.
        val slots = derive(listOf(armed(at = arrayOf(eightAm, eightPm))), answers = listOf(answer()))

        assertEquals(today to eightPm, slots.first().key())
    }

    @Test
    fun `a skipped dose answers its slot as firmly as a given one`() {
        // There is no third status and no "missed" (ADR-0026): the owner has spoken about this slot,
        // and which way they answered is not the alarm's business. Tomorrow's 08:00 is still armed,
        // because skipping one dose is not ending the course.
        val slots = derive(listOf(armed(at = arrayOf(eightAm))), answers = listOf(answer(status = DoseStatus.SKIPPED)))

        assertTrue(slots.none { it.due.scheduledOn == today })
        assertEquals(today.plusDays(1) to eightAm, slots.first().key())
    }

    @Test
    fun `an ad-hoc dose answers nothing, so the slot stays armed`() {
        // Both halves of the key null is what makes a dose ad hoc (ADR-0002) — a dose that happened,
        // not an answer to a slot. Treating it as one would silence a real reminder.
        val adHoc = DoseEntity(courseId = "course", status = DoseStatus.GIVEN)
        val slots = derive(listOf(armed(at = arrayOf(eightAm))), answers = listOf(adHoc))

        assertEquals(today to eightAm, slots.first().key())
    }

    @Test
    fun `another course's answer does not silence this one's slot`() {
        val slots =
            derive(
                listOf(armed(at = arrayOf(eightAm))),
                answers = listOf(answer(courseId = "somebody-else")),
            )

        assertEquals(today to eightAm, slots.first().key())
    }

    // ---- The exclusions --------------------------------------------------------------------------

    @Test
    fun `a course with reminders off arms nothing`() {
        // ADR-0003's per-course switch, and the only thing that makes it mean anything.
        assertTrue(derive(listOf(armed(course(remindersEnabled = false), eightAm))).isEmpty())
    }

    @Test
    fun `an archived bunny's course arms nothing, however live the course looks`() {
        // The rule `careDueForNotifying` applies to reminders (ADR-0004, ADR-0001), applied to doses.
        // The course here is open, on schedule and reminders-on: the bunny is the whole of the
        // reason, and an 08:00 alarm on a memorial is the worst notification this app could post.
        val slots =
            derive(
                listOf(armed(at = arrayOf(eightAm), bunnyArchivedAt = Instant.parse("2026-05-01T10:00:00Z"))),
            )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `every course having ended leaves no alarm at all`() {
        // Nothing armed is a real state rather than a failure — it is what a finished treatment looks
        // like, and it is what makes `rescheduleDoseAlarm` cancel instead of leaving a stale alarm.
        val slots =
            derive(
                listOf(
                    armed(course(id = "a", endOn = today.minusDays(1)), eightAm),
                    armed(course(id = "b", endOn = today.minusDays(7)), eightPm),
                ),
            )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `a course ending today still arms today's remaining slots`() {
        // The clamp is inclusive of `endOn`, and this is the difference between "end course instead"
        // keeping tonight's dose and quietly dropping it.
        val slots = derive(listOf(armed(course(endOn = today), eightAm, eightPm)))

        assertEquals(listOf(today to eightAm, today to eightPm), slots.map { it.key() })
    }

    @Test
    fun `a course with no times has no slots, so its reminder switch changes nothing`() {
        assertTrue(derive(listOf(armed())).isEmpty())
    }

    // ---- The one that is deliberately not an exclusion --------------------------------------------

    @Test
    fun `a course starting tomorrow arms tomorrow's first slot today`() {
        // **The assertion that `startOn` never became a filter** (ADR-0025), and the most important
        // case in this file. "Starts tomorrow morning" is how most courses are created; if this
        // regresses, the evening a course is added arms nothing, and the next rebuild is the 09:00
        // care sweep — an hour after the 08:00 dose it was meant to announce. Silence, on the first
        // dose of nearly every course.
        val tomorrow = today.plusDays(1)
        val slots = derive(listOf(armed(course(startOn = tomorrow), eightAm, eightPm)))

        assertEquals(tomorrow to eightAm, slots.first().key())
    }

    @Test
    fun `a course starting tomorrow derives nothing for today`() {
        // The other half of the clamp: forward to the start date, and never before it.
        val slots = derive(listOf(armed(course(startOn = today.plusDays(1)), eightAm)))

        assertTrue(slots.none { it.due.scheduledOn == today })
    }

    @Test
    fun `today's slots being all answered leaves tomorrow's first as the earliest`() {
        // The other way the answer lands on a later day, and why the window is a horizon rather than
        // a single day.
        val slots =
            derive(
                listOf(armed(at = arrayOf(eightAm, eightPm))),
                answers = listOf(answer(at = eightAm), answer(at = eightPm)),
            )

        assertEquals(today.plusDays(1) to eightAm, slots.first().key())
    }

    // ---- One alarm, every bunny ------------------------------------------------------------------

    @Test
    fun `one alarm covers every bunny, so the earliest slot can belong to either of them`() {
        // There is one pending alarm for the whole app (ADR-0025), which only works if the derivation
        // is app-wide. A per-bunny read would arm whoever happened to be selected.
        val slots =
            derive(
                listOf(
                    armed(course(id = "bijou-course"), eightPm),
                    armed(
                        course(id = "nugget-course", bunnyId = "nugget", name = "Baytril"),
                        eightAm,
                        bunnyName = "Nugget",
                    ),
                ),
            )

        assertEquals("nugget-course", slots.first().course.id)
        assertEquals("Nugget", slots.first().bunnyName)
    }

    @Test
    fun `two courses due at the same minute are both armed, in a stable order`() {
        // Firing posts one notification per due course, so both have to survive the derivation. The
        // order has to be the same on every rebuild, or "the earliest slot" would flip between them
        // and the alarm would look like it was moving when nothing had changed.
        val slots =
            derive(
                listOf(
                    armed(course(id = "z", name = "Zantac"), eightAm),
                    armed(course(id = "a", name = "Amoxicillin"), eightAm),
                ),
            )

        assertEquals(listOf("a", "z"), slots.take(2).map { it.course.id })
    }

    @Test
    fun `the bunny's name rides along, because the notification says whose dose it is`() {
        val slot = derive(listOf(armed(at = arrayOf(eightAm), bunnyName = "Bijou"))).first()

        assertEquals("Bijou", slot.bunnyName)
        assertEquals("bijou", slot.course.bunnyId)
        // And the amount, exactly as the vet wrote it — the notification never parses it (ADR-0002).
        assertEquals("0.3 ml", slot.course.doseAmount)
    }

    // ---- The horizon -----------------------------------------------------------------------------

    @Test
    fun `the alarm and the screens derive over the same horizon`() {
        // Not a style assertion. A course row reading "Next dose Sunday" while the alarm had armed
        // nothing would be the app disagreeing with itself about one sentence, and the only thing
        // stopping that is these two being one number.
        assertEquals(8L, DOSE_HORIZON_DAYS)
    }

    @Test
    fun `a course beginning beyond the horizon arms nothing yet, and comes into range on its own`() {
        // Honest about the limit rather than pretending there is none: a rebuild happens at the daily
        // sweep and on every launch, so a course starting next month arms once it is in view.
        val far = armed(course(startOn = today.plusDays(30)), eightAm)

        assertTrue(derive(listOf(far)).isEmpty())
        assertEquals(today.plusDays(30) to eightAm, derive(listOf(far), days = 31).first().key())
    }

    // ---- Zones -----------------------------------------------------------------------------------

    @Test
    fun `the instant follows the zone the rebuild ran in, while the key stays local`() {
        // Why a timezone change is one of the occasions that rebuilds (ADR-0003): the same 08:00 slot
        // is a different instant after the flight. The alarm is placed on the instant; the answer is
        // written against the key.
        val course = armed(at = arrayOf(eightAm))
        val inWarsaw = derive(listOf(course), days = 1).first()
        val inLondon = derive(listOf(course), days = 1, zone = ZoneId.of("Europe/London")).first()

        assertEquals(inWarsaw.key(), inLondon.key())
        assertTrue(inLondon.due.at.isAfter(inWarsaw.due.at))
    }

    @Test
    fun `an answer written in one zone still answers its slot in another`() {
        // The whole reason the slot's key is local and never an `Instant` (ADR-0002): an
        // instant-keyed answer would stop matching the moment the owner travelled, and a dose they
        // had already given would re-arm its own alarm.
        val slots =
            derive(
                listOf(armed(at = arrayOf(eightAm))),
                answers = listOf(answer()),
                days = 1,
                zone = ZoneId.of("America/New_York"),
            )

        assertTrue(slots.isEmpty())
    }
}
