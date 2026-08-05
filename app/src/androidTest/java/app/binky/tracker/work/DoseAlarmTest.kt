package app.binky.tracker.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.data.BunnyDatabase
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.BunnyRepository
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.DueDose
import app.binky.tracker.data.FluffleRepository
import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.MedicationRepository
import app.binky.tracker.data.MedicationTimeEntity
import app.binky.tracker.data.inMemoryDatabase
import app.binky.tracker.data.temporaryMedia
import app.binky.tracker.data.temporaryPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * **The alarm invariant across the write paths** (ADR-0025, PLAN 5f): *at most one pending dose
 * alarm exists, and none when no course is armed.*
 *
 * Instrumented because none of it is arithmetic. Whether a `PendingIntent` exists is the platform's
 * answer and not Kotlin's; whether Room's join really carries the bunny's name and archived-at into
 * `ArmedCourse` is the database's; and whether a *bunny* write reaches the alarm at all is the
 * question ADR-0025 says a rebuild hung off the medication tables would get wrong. The derivation
 * behind it is `ArmedDosesTest`'s, on the JVM.
 *
 * **"At most one" is asserted nowhere, because it is not expressible.** There is a single request
 * code, so a second pending dose alarm cannot be created — which is the point of the design, and is
 * why every case below asks only whether the one exists.
 *
 * The repositories are wired to a real rebuild over this test's own in-memory database, which is the
 * only way the write paths prove anything: a scheduler that did nothing would pass every assertion
 * about the database and none of these.
 *
 * The slots are all **tomorrow's**, deliberately. A time later today is in the future when the suite
 * starts and in the past when it runs at 23:50, and a flaky alarm test would be worse than none.
 */
@RunWith(AndroidJUnit4::class)
class DoseAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var database: BunnyDatabase
    private lateinit var medications: MedicationRepository
    private lateinit var bunnies: BunnyRepository

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val eightAm: LocalTime = LocalTime.of(8, 0)

    @Before
    fun setUp() =
        runTest {
            database = inMemoryDatabase()
            // Kotlin note: the lambda reads `medications` when it *runs*, not when it is built, so a
            // repository can be handed a scheduler that rebuilds from itself. This is the seam
            // `AppContainer` fills with the container-resolving version.
            medications = MedicationRepository(database) { rebuild() }
            bunnies =
                BunnyRepository(
                    database,
                    FluffleRepository(database),
                    temporaryPreferences(),
                    temporaryMedia(),
                ) { rebuild() }

            // `PendingIntent`s are process-global and outlive a test, so the suite starts from a
            // known state — and an empty database rebuilding to "nothing armed" is itself the first
            // assertion this file makes.
            rebuild()
        }

    @After
    fun tearDown() =
        runTest {
            database.close()
        }

    /** The rebuild, against this test's database rather than the app's. */
    private suspend fun rebuild() = context.rescheduleDoseAlarm(medications, Instant.now())

    private fun armed() = context.hasPendingDoseAlarm()

    private suspend fun addBunny(name: String = "Bijou"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    /**
     * A course with exactly one slot in existence — tomorrow at 08:00 — so "answered" and "nothing
     * left to arm" are the same state and neither needs a clock.
     */
    private suspend fun addOneSlotCourse(
        bunnyId: String,
        times: List<LocalTime> = listOf(eightAm),
    ): String =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bunnyId,
                name = "Metacam",
                doseAmount = "0.3 ml",
                startOn = tomorrow,
                endOn = tomorrow,
            ),
            times = times,
        )

    /** A course already running with no end in sight — what `endCourse` is for. */
    private suspend fun addRunningCourse(bunnyId: String): String =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bunnyId,
                name = "Metacam",
                doseAmount = "0.3 ml",
                startOn = today,
            ),
            times = listOf(eightAm),
        )

    private fun slot(
        courseId: String,
        on: LocalDate = tomorrow,
    ) = DueDose(
        courseId = courseId,
        scheduledOn = on,
        scheduledTime = eightAm,
        at = on.atTime(eightAm).atZone(zone).toInstant(),
    )

    // ---- Nothing armed is a real state -----------------------------------------------------------

    @Test
    fun anEmptyDatabaseArmsNothing() =
        runTest {
            assertFalse("an app with no courses must leave no pending alarm", armed())
        }

    @Test
    fun aCourseWithNoTimesArmsNothing() =
        runTest {
            addOneSlotCourse(addBunny(), times = emptyList())

            assertFalse(armed())
        }

    // ---- The medication write paths ---------------------------------------------------------------

    @Test
    fun addingACourseArmsTheAlarm() =
        runTest {
            addOneSlotCourse(addBunny())

            assertTrue(armed())
        }

    @Test
    fun givingACourseItsTimesArmsTheAlarm() =
        runTest {
            val course = addOneSlotCourse(addBunny(), times = emptyList())
            assertFalse(armed())

            medications.setTimes(course, listOf(MedicationTimeEntity(courseId = course, time = eightAm)))

            assertTrue(armed())
        }

    @Test
    fun switchingRemindersOffCancelsTheAlarm() =
        runTest {
            val bunny = addBunny()
            val course = addOneSlotCourse(bunny)
            assertTrue(armed())

            val row = requireNotNull(medications.courseNow(course))
            medications.update(row.copy(remindersEnabled = false))

            assertFalse("a course with reminders off must arm nothing", armed())
        }

    @Test
    fun answeringTheLastSlotCancelsTheAlarm() =
        runTest {
            val course = addOneSlotCourse(addBunny())
            assertTrue(armed())

            medications.answer(slot(course), DoseStatus.GIVEN)

            assertFalse("an answered slot leaves the derivation", armed())
        }

    @Test
    fun deletingTheAnswerArmsTheSlotAgain() =
        runTest {
            val course = addOneSlotCourse(addBunny())
            val dose = medications.answer(slot(course), DoseStatus.GIVEN)
            assertFalse(armed())

            medications.deleteDose(dose)

            assertTrue("the slot goes back to unanswered, never to missed", armed())
        }

    @Test
    fun anAdHocDoseAnswersNoSlotAndLeavesTheAlarmStanding() =
        runTest {
            val course = addOneSlotCourse(addBunny())

            medications.recordAdHoc(course, DoseStatus.GIVEN)

            assertTrue(armed())
        }

    @Test
    fun endingACourseCancelsTheAlarm() =
        runTest {
            // A running course, with today's slot answered first so the assertion does not depend on
            // what time the suite happens to run at: whatever the clock says, the only thing left to
            // arm is tomorrow's 08:00, and ending the course today is what takes it away.
            val course = addRunningCourse(addBunny())
            medications.answer(slot(course, on = today), DoseStatus.GIVEN)
            assertTrue(armed())

            medications.endCourse(course, on = today)

            assertFalse("ending a course stops the schedule from today (ADR-0002)", armed())
        }

    @Test
    fun deletingACourseCancelsTheAlarm() =
        runTest {
            val course = addOneSlotCourse(addBunny())
            assertTrue(armed())

            medications.delete(course)

            assertFalse(armed())
        }

    // ---- The three that reach the alarm sideways --------------------------------------------------

    @Test
    fun archivingTheBunnyCancelsTheAlarm() =
        runTest {
            // No medication table is touched here at all, which is exactly why ADR-0025 puts the
            // rebuild on the repository rather than on the call sites.
            val bunny = addBunny()
            addOneSlotCourse(bunny)
            assertTrue(armed())

            bunnies.archive(bunny)

            assertFalse("an archived bunny's course arms nothing (ADR-0004)", armed())
        }

    @Test
    fun unArchivingTheBunnyArmsItAgain() =
        runTest {
            val bunny = addBunny()
            addOneSlotCourse(bunny)
            bunnies.archive(bunny)
            assertFalse(armed())

            bunnies.unarchive(bunny)

            assertTrue(armed())
        }

    @Test
    fun deletingTheBunnyCancelsTheAlarm() =
        runTest {
            // The cascade takes the courses with no course write happening anywhere — the path a
            // rebuild hung off the medication tables alone would miss entirely.
            val bunny = addBunny()
            addOneSlotCourse(bunny)
            assertTrue(armed())

            bunnies.delete(bunny)

            assertFalse(armed())
        }

    @Test
    fun anotherBunnysCourseKeepsTheAlarmAfterOneIsArchived() =
        runTest {
            // One alarm covers the whole app, so archiving one rabbit must move it rather than
            // cancel it while somebody else is still on medication.
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            addOneSlotCourse(bijou)
            addOneSlotCourse(nugget)

            bunnies.archive(bijou)

            assertTrue(armed())
        }

    // ---- The heartbeat ----------------------------------------------------------------------------

    @Test
    fun aRebuildWithTheAlarmAlreadyCorrectLeavesItArmed() =
        runTest {
            // What the daily care sweep and process start do, and the assertion that the heartbeat is
            // idempotent rather than additive (ADR-0025). "Additive" is not expressible with one
            // request code, which is the design; what could still go wrong is a rebuild *cancelling*
            // what it just found, and that is what this catches.
            addOneSlotCourse(addBunny())
            assertTrue(armed())

            repeat(3) { rebuild() }

            assertTrue(armed())
        }

    // ---- The join Room has to get right -----------------------------------------------------------

    @Test
    fun theArmedReadCarriesTheBunnysNameAndArchivedState() =
        runTest {
            // `ArmedCourse` is a projection with two aliased join columns and a relation, none of
            // which the type checker proves. The name is what the notification says; `archivedAt` is
            // what the exclusion reads.
            val bunny = addBunny("Bijou")
            addOneSlotCourse(bunny)

            val armedDoses = medications.armedDosesNow(zone = zone, today = today)

            assertEquals(1, armedDoses.size)
            assertEquals("Bijou", armedDoses.first().bunnyName)
            assertEquals("Metacam", armedDoses.first().course.name)
            assertEquals("0.3 ml", armedDoses.first().course.doseAmount)
            assertEquals(tomorrow to eightAm, armedDoses.first().due.let { it.scheduledOn to it.scheduledTime })
        }
}
