package app.binky.tracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Checkpoint 5d's data layer: the two cascades, the unique index that makes a slot answerable exactly
 * once, the converters, and the counts a delete confirmation quotes.
 *
 * Instrumented because every claim here is about SQLite's behaviour — a cascade the ORM is only
 * *asked* for is not a cascade, NULLs being distinct is the database's rule and not Kotlin's, and a
 * converter is not proven by the type checker. The derivation itself is `DoseScheduleTest`'s, on the
 * JVM, which is why `dueDoses` takes a course and its times rather than a bunny id.
 */
@RunWith(AndroidJUnit4::class)
class MedicationRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var medications: MedicationRepository

    /** Fixed, so "today" in a test is never the machine's actual date. */
    private val today = LocalDate.of(2026, 5, 20)
    private val utc = ZoneId.of("UTC")
    private val eightAm = LocalTime.of(8, 0)
    private val eightPm = LocalTime.of(20, 0)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        medications = MedicationRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addBunny(name: String = "Bijou"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    private suspend fun addCourse(
        bunnyId: String,
        name: String = "Metacam",
        startOn: LocalDate = today,
        endOn: LocalDate? = null,
        times: List<LocalTime> = listOf(eightAm),
    ): String =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bunnyId,
                name = name,
                doseAmount = "0.3 ml",
                startOn = startOn,
                endOn = endOn,
            ),
            times = times,
        )

    private suspend fun slotsFor(
        bunnyId: String,
        days: Long = 1,
    ) = medications.scheduleNow(bunnyId, days = days, zone = utc, today = today)

    // ---- Cascades: two hops, both enforced by the database ---------------------------------------

    @Test
    fun coursesTimesAndDosesGoWithTheBunny() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, times = listOf(eightAm, eightPm))
            medications.answer(slotsFor(bunnyId).first().due, DoseStatus.GIVEN)
            medications.recordAdHoc(courseId, DoseStatus.GIVEN)

            database.bunnyDao().deleteById(bunnyId)

            assertEquals(0, database.countRows("medication_courses"))
            assertEquals("the schedule cascades too", 0, database.countRows("medication_times"))
            assertEquals("and so does what was actually given", 0, database.countRows("doses"))
        }

    @Test
    fun timesAndDosesGoWithTheirCourse() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, times = listOf(eightAm, eightPm))
            medications.recordAdHoc(courseId, DoseStatus.GIVEN)
            val survivor = addCourse(bunnyId, name = "Baytril", times = listOf(eightAm))
            medications.recordAdHoc(survivor, DoseStatus.GIVEN)

            medications.delete(courseId)

            assertEquals(1, database.countRows("medication_courses"))
            assertEquals("only the deleted course's schedule goes", 1, database.countRows("medication_times"))
            assertEquals("and only its doses", 1, database.countRows("doses"))
        }

    // ---- The unique index: a slot is answerable exactly once, ad hoc is unconstrained -------------

    @Test
    fun theUniqueIndexRejectsASecondAnswerToTheSameSlot() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId)
            val dao = database.medicationDao()
            dao.insertDose(
                DoseEntity(
                    courseId = courseId,
                    scheduledOn = today,
                    scheduledTime = eightAm,
                    status = DoseStatus.GIVEN,
                ),
            )

            val refusal =
                runCatching {
                    dao.insertDose(
                        DoseEntity(
                            courseId = courseId,
                            scheduledOn = today,
                            scheduledTime = eightAm,
                            status = DoseStatus.SKIPPED,
                        ),
                    )
                }

            // The database's rule, not the repository's — this is the guarantee `answer` is written
            // to stay on the right side of.
            assertTrue(refusal.exceptionOrNull() is SQLiteConstraintException)
            assertEquals(1, database.countRows("doses"))
        }

    @Test
    fun anyNumberOfAdHocDosesIsAccepted() =
        runTest {
            // NULLs are distinct in SQLite, so ad-hoc rows fall out of the unique index for free —
            // no partial index and no branch in the code. Three rescue doses in one night is a real
            // thing an owner does (ADR-0002).
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId)

            repeat(3) { medications.recordAdHoc(courseId, DoseStatus.GIVEN) }

            assertEquals(3, database.countRows("doses"))
        }

    @Test
    fun answeringASlotTwiceCorrectsTheAnswerRatherThanThrowing() =
        runTest {
            val bunnyId = addBunny()
            addCourse(bunnyId)
            val slot = slotsFor(bunnyId).single().due

            val first = medications.answer(slot, DoseStatus.SKIPPED, note = "  she spat it out  ")
            val second = medications.answer(slot, DoseStatus.GIVEN, note = "  ")

            assertEquals("the same row, corrected", first, second)
            assertEquals(1, database.countRows("doses"))
            val stored = medications.doseNow(first)!!
            assertEquals(DoseStatus.GIVEN, stored.status)
            assertNull("a whitespace-only note is no note", stored.note)
        }

    // ---- The converters, proven against what is actually stored ----------------------------------

    @Test
    fun clockTimesAndStatusesRoundTripAsThemselves() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, times = listOf(eightPm, eightAm))
            medications.answer(slotsFor(bunnyId).first().due, DoseStatus.SKIPPED)

            val stored = medications.course(courseId).first()!!
            assertEquals(listOf(eightAm, eightPm), stored.clockTimes)
            assertEquals(
                "seconds since local midnight, so ORDER BY and the unique index are real comparisons",
                "28800",
                database.rawColumn("SELECT time FROM medication_times ORDER BY time LIMIT 1"),
            )
            assertEquals(
                "stored by name, never ordinal",
                "SKIPPED",
                database.rawColumn("SELECT status FROM doses"),
            )
        }

    // ---- What a delete confirmation quotes (ADR-0004) --------------------------------------------

    @Test
    fun recordCountsCountsCoursesAndTheirDoses() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId)
            medications.answer(slotsFor(bunnyId).single().due, DoseStatus.GIVEN)
            medications.recordAdHoc(courseId, DoseStatus.GIVEN)

            val counts = database.bunnyDao().recordCounts(bunnyId)!!
            // The course and both doses; the times are parts of the course, not records of their own.
            assertEquals(3, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    // ---- The derived schedule, joined to what was recorded ---------------------------------------

    @Test
    fun theScheduleJoinsAnswersOntoDerivedSlotsAndLeavesTheRestUnanswered() =
        runTest {
            val bunnyId = addBunny()
            addCourse(bunnyId, times = listOf(eightAm, eightPm))

            medications.answer(slotsFor(bunnyId).first().due, DoseStatus.GIVEN)

            val slots = medications.schedule(bunnyId, zone = utc, today = today).first()
            assertEquals(listOf(eightAm, eightPm), slots.map { it.due.scheduledTime })
            assertEquals(DoseStatus.GIVEN, slots[0].recorded?.status)
            assertNull("nobody has said anything about the evening yet", slots[1].recorded)
        }

    @Test
    fun anotherBunnysDosesAreNotThisBunnysAnswers() =
        runTest {
            val bijou = addBunny("Bijou")
            val clover = addBunny("Clover")
            addCourse(bijou)
            val cloversCourse = addCourse(clover)
            medications.answer(slotsFor(clover).single().due, DoseStatus.GIVEN)

            assertNull(slotsFor(bijou).single().recorded)
            assertEquals(cloversCourse, slotsFor(clover).single().recorded!!.courseId)
        }

    @Test
    fun shorteningACourseDropsFutureSlotsAndKeepsEveryRecordedDose() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, startOn = today.minusDays(3))
            medications.answer(slotsFor(bunnyId).single().due, DoseStatus.GIVEN)
            medications.recordAdHoc(courseId, DoseStatus.GIVEN)
            assertEquals(5, slotsFor(bunnyId, days = 5).size)

            medications.endCourse(courseId, on = today)

            assertEquals(
                "today's slot survives; the four after it are simply not derived",
                1,
                slotsFor(bunnyId, days = 5).size,
            )
            assertEquals("nothing recorded is touched — there was never a future row", 2, database.countRows("doses"))
        }

    @Test
    fun activeCoursesComeBeforeEndedOnes() =
        runTest {
            val bunnyId = addBunny()
            addCourse(bunnyId, name = "Zithromax", startOn = today.minusWeeks(2), endOn = today.minusDays(1))
            addCourse(bunnyId, name = "Metacam")

            val names = medications.courses(bunnyId, today).first().map { it.course.name }
            assertEquals(listOf("Metacam", "Zithromax"), names)
        }

    // ---- Writes: the schedule, the close, the corrections ----------------------------------------

    @Test
    fun settingTimesReplacesTheScheduleAndKeepsTheRowsTheEditorIsHolding() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, times = listOf(eightAm, eightPm))
            val held =
                medications
                    .course(courseId)
                    .first()!!
                    .times
                    .sortedBy { it.time }

            // The morning chip is moved to 09:00 and the evening one removed: one row keeps its id
            // and changes its time, the other goes.
            medications.setTimes(courseId, listOf(held.first().copy(time = LocalTime.of(9, 0))))

            val after = medications.course(courseId).first()!!.times
            assertEquals(listOf(LocalTime.of(9, 0)), after.map { it.time })
            assertEquals("the same row, moved", held.first().id, after.single().id)
        }

    @Test
    fun aScheduleWithTheSameTimeTwiceCollapsesToOneRow() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, times = listOf(eightAm, eightAm))

            assertEquals(1, database.countRows("medication_times"))
            assertEquals(1, slotsFor(bunnyId).size)
            assertEquals(courseId, slotsFor(bunnyId).single().course.id)
        }

    @Test
    fun endingACourseKeepsEveryDoseAndStopsTheSlots() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId, startOn = today.minusDays(2))
            medications.recordAdHoc(courseId, DoseStatus.GIVEN)

            medications.endCourse(courseId, on = today.minusDays(1))

            assertEquals(today.minusDays(1), medications.courseNow(courseId)!!.endOn)
            assertTrue("ended yesterday, so nothing is due today", slotsFor(bunnyId).isEmpty())
            assertEquals(1, database.countRows("doses"))
        }

    @Test
    fun deletingARecordedDoseLeavesTheSlotUnansweredRatherThanMissed() =
        runTest {
            val bunnyId = addBunny()
            addCourse(bunnyId)
            val slot = slotsFor(bunnyId).single().due
            val doseId = medications.answer(slot, DoseStatus.GIVEN)

            medications.deleteDose(doseId)

            assertNull(slotsFor(bunnyId).single().recorded)
        }

    // ---- What the database is not allowed to hold ------------------------------------------------

    @Test
    fun aCourseWithNoNameIsRefused() =
        runTest {
            val bunnyId = addBunny()
            val refusal = runCatching { addCourse(bunnyId, name = "   ") }
            assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, database.countRows("medication_courses"))
        }

    @Test
    fun aCourseEndingBeforeItStartsIsRefused() =
        runTest {
            val bunnyId = addBunny()
            val refusal =
                runCatching { addCourse(bunnyId, startOn = today, endOn = today.minusDays(1)) }
            assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
        }

    @Test
    fun aDoseGivenInTheFutureIsRefused() =
        runTest {
            val bunnyId = addBunny()
            val courseId = addCourse(bunnyId)
            val now = Instant.parse("2026-05-20T09:00:00Z")

            val refusal =
                runCatching {
                    medications.recordAdHoc(
                        courseId,
                        DoseStatus.GIVEN,
                        recordedAt = now.plusSeconds(3600),
                        now = now,
                    )
                }

            assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
            assertEquals("back-dating is normal; the future is not", 0, database.countRows("doses"))
        }
}

/** One scalar, straight out of SQLite, for asserting how a value is *stored* rather than read back. */
private fun BunnyDatabase.rawColumn(sql: String): String? =
    openHelper.readableDatabase.query(sql).use { cursor ->
        cursor.moveToFirst()
        if (cursor.isNull(0)) null else cursor.getString(0)
    }
