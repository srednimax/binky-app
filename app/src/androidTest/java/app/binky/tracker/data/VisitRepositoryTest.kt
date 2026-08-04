package app.binky.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Checkpoint 5c's claim, asserted against the database: **a visit and its weighing are one write**
 * (ADR-0017).
 *
 * `VisitDaoTest` proved what SQLite does when a row goes; this proves what the repository does when
 * the owner types. The two halves that matter here are "there is no path that produces two numbers"
 * and "deleting a visit states the choice about its weighing" — neither is visible in the schema,
 * and both are the kind of thing a screen would otherwise get right once and drift on.
 */
@RunWith(AndroidJUnit4::class)
class VisitRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var visits: VisitRepository
    private lateinit var weights: WeightRepository

    private val zone: ZoneId = ZoneId.of("Europe/Warsaw")
    private val visitedOn: LocalDate = LocalDate.of(2026, 5, 20)
    private val noon: Instant = Instant.parse("2026-05-20T10:00:00Z")
    private val now: Instant = Instant.parse("2026-06-01T07:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        weights = WeightRepository(database)
        visits = VisitRepository(database, weights)
    }

    @After
    fun tearDown() = database.close()

    // ---- One transaction, one number --------------------------------------------------------------

    @Test
    fun aVisitWithAWeightWritesExactlyOneWeighing() =
        runTest {
            val bunnyId = addBunny()

            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)

            assertEquals(1, database.countRows("weights"))
            val weighing = database.weightDao().weightForVisitNow(visitId)
            assertNotNull("the visit's weighing is tagged with it, and is the only copy", weighing)
            assertEquals(2380, weighing!!.grams)
            assertEquals(noon, weighing.recordedAt)
            assertEquals(bunnyId, weighing.bunnyId)
        }

    @Test
    fun aVisitWithNoWeightWritesNone() =
        runTest {
            visits.add(visit(addBunny()), grams = null, now = now, zone = zone)

            assertEquals("a consultation is the ordinary case", 0, database.countRows("weights"))
        }

    @Test
    fun editingTheWeightChangesThatRowRatherThanAddingASecond() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)
            val original = database.weightDao().weightForVisitNow(visitId)!!

            visits.update(
                database.visitDao().byIdNow(visitId)!!,
                grams = 2410,
                now = now,
                zone = zone,
            )

            assertEquals(1, database.countRows("weights"))
            val updated = database.weightDao().weightForVisitNow(visitId)!!
            assertEquals("the same row, corrected", original.id, updated.id)
            assertEquals(2410, updated.grams)
        }

    @Test
    fun clearingTheWeightDeletesTheRowItHad() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)

            visits.update(database.visitDao().byIdNow(visitId)!!, grams = null, now = now, zone = zone)

            assertEquals(0, database.countRows("weights"))
        }

    @Test
    fun addingAWeightToAVisitThatHadNoneInsertsOne() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = null, now = now, zone = zone)

            visits.update(database.visitDao().byIdNow(visitId)!!, grams = 2380, now = now, zone = zone)

            assertEquals(1, database.countRows("weights"))
            assertEquals(2380, database.weightDao().weightForVisitNow(visitId)!!.grams)
        }

    @Test
    fun movingTheVisitsDateRederivesTheWeighingsInstant() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)

            visits.update(
                database.visitDao().byIdNow(visitId)!!.copy(visitedOn = LocalDate.of(2026, 5, 18)),
                grams = 2380,
                now = now,
                zone = zone,
            )

            // Noon on the *new* day: the visit and its weighing are one fact, so the chart point
            // moves with the record rather than being left on the day it was first typed.
            assertEquals(
                Instant.parse("2026-05-18T10:00:00Z"),
                database.weightDao().weightForVisitNow(visitId)!!.recordedAt,
            )
        }

    @Test
    fun aVisitInTheFutureIsRefusedRatherThanClamped() =
        runTest {
            val bunnyId = addBunny()
            val tomorrow = LocalDate.now(zone).plusDays(1)

            // `runCatching` rather than `assertThrows`, because the call is `suspend` and a nested
            // `runTest` inside this one would be a second test scheduler on the same coroutine.
            val refusal = runCatching { visits.add(visit(bunnyId).copy(visitedOn = tomorrow), zone = zone) }

            assertTrue(
                "a day the owner has not reached cannot be something that happened",
                refusal.exceptionOrNull() is IllegalArgumentException,
            )
            assertEquals(0, database.countRows("visits"))
        }

    // ---- Deleting states the choice ---------------------------------------------------------------

    @Test
    fun deletingAVisitCanKeepTheWeighingStandingOnItsOwn() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)

            visits.delete(visitId, keepWeighing = true)

            assertEquals(0, database.countRows("visits"))
            val series = database.weightDao().seriesNow(bunnyId)
            assertEquals(1, series.size)
            assertNull("SET NULL clears the tag; the number stays", series.single().visitId)
            assertEquals(2380, series.single().grams)
        }

    @Test
    fun deletingAVisitCanTakeItsWeighingWithIt() =
        runTest {
            val bunnyId = addBunny()
            val visitId = visits.add(visit(bunnyId), grams = 2380, now = now, zone = zone)

            visits.delete(visitId, keepWeighing = false)

            assertEquals(0, database.countRows("visits"))
            assertEquals(0, database.countRows("weights"))
        }

    // ---- The joined read the screens use ----------------------------------------------------------

    @Test
    fun theVisitListCarriesItsVetsNameAndItsWeighing() =
        runTest {
            val bunnyId = addBunny()
            val vetId = VetRepository(database).add(VetEntity(name = "Dr Kowalska"))
            visits.add(visit(bunnyId).copy(vetId = vetId), grams = 2380, now = now, zone = zone)

            val details = visits.visits(bunnyId).first().single()

            assertEquals("Dr Kowalska", details.vetName)
            assertEquals(2380, details.weightGrams)
        }

    @Test
    fun aDeletedVetLeavesTheVisitReadableWithNoName() =
        runTest {
            val bunnyId = addBunny()
            val vets = VetRepository(database)
            val vetId = vets.add(VetEntity(name = "Dr Kowalska"))
            visits.add(visit(bunnyId).copy(vetId = vetId), now = now, zone = zone)

            vets.delete(vetId)

            val details = visits.visits(bunnyId).first().single()
            assertNull(details.vetName)
            assertEquals("Molar check", details.visit.reason)
        }

    // ---- Free consequences worth pinning down -----------------------------------------------------

    @Test
    fun aVisitWeighingSatisfiesAWeighInReminder() =
        runTest {
            // Nothing implements this: 4b resolves a weigh-in's last completion as
            // `max(care event, latest weight)`, and a visit's weighing **is** a weight. The
            // assertion exists so a later change to either half cannot quietly break it.
            val bunnyId = addBunny()
            val care = CareRepository(database)
            val today = LocalDate.now(zone)
            care.add(
                CareReminderEntity(
                    bunnyId = bunnyId,
                    type = CareType.WEIGH_IN,
                    intervalCount = 7,
                    intervalUnit = CareIntervalUnit.DAY,
                    firstDueOn = today.minusDays(3),
                ),
            )

            visits.add(
                visit(bunnyId).copy(visitedOn = today),
                grams = 2380,
                now = Instant.now(),
                zone = zone,
            )

            val scheduled = care.scheduleNow(bunnyId, zone).single()
            assertEquals("the weighing is the completion", today, scheduled.lastCompletedOn)
            assertEquals(today.plusDays(7), scheduled.dueOn)
        }

    private suspend fun addBunny(): String {
        val bunny = BunnyEntity(name = "Bijou")
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    private fun visit(bunnyId: String) = VisitEntity(bunnyId = bunnyId, visitedOn = visitedOn, reason = "Molar check")
}
