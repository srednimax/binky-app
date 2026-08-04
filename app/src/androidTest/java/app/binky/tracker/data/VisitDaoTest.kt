package app.binky.tracker.data

import android.database.sqlite.SQLiteConstraintException
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

/**
 * Checkpoint 5b's schema, asserted where it actually lives: **in SQLite** (ADR-0017).
 *
 * Every claim here is about what the database does when a row goes, and a cascade the ORM was merely
 * *asked* for is not a cascade. Three deletes, three different answers, and the whole point is that
 * they differ:
 *
 * - delete the **bunny** → the visits go with them (`CASCADE`)
 * - delete the **vet** → the visits stand, minus a name (`SET NULL`)
 * - delete the **visit** → the weighing stands, minus its origin tag (`SET NULL`)
 *
 * That last one is the one worth having a test for: the vet read a number off a scale, and no
 * bookkeeping decision about which visit row it belonged to may destroy it.
 *
 * The derivation on top of `visitId` is `WeightSourceTest`'s, on the JVM — nothing in it touches a
 * database, which is exactly the split the one-stored-fact design buys.
 */
@RunWith(AndroidJUnit4::class)
class VisitDaoTest {
    private lateinit var database: BunnyDatabase

    private val visitedOn = LocalDate.of(2026, 5, 20)
    private val recordedAt = Instant.parse("2026-05-20T12:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() = database.close()

    // ---- Cascades and survivals -----------------------------------------------------------------

    @Test
    fun visitsGoWithTheBunny() =
        runTest {
            val bunnyId = addBunny()
            addVisit(bunnyId)

            database.bunnyDao().deleteById(bunnyId)

            assertEquals(0, database.countRows("visits"))
        }

    @Test
    fun aDeletedVetLeavesItsVisitsStandingWithNoVet() =
        runTest {
            val bunnyId = addBunny()
            val vetId = addVet()
            val visitId = addVisit(bunnyId, vetId = vetId)

            database.vetDao().deleteById(vetId)

            val visit = database.visitDao().byIdNow(visitId)
            assertNotNull("the visit is the health record; the vet is a directory entry", visit)
            assertNull(visit!!.vetId)
            assertEquals("Molar check", visit.reason)
        }

    @Test
    fun aDeletedVisitLeavesItsWeighingStandingWithNoOrigin() =
        runTest {
            val bunnyId = addBunny()
            val visitId = addVisit(bunnyId)
            val weightId = addWeighing(bunnyId, grams = 2380, visitId = visitId)

            database.visitDao().deleteById(visitId)

            val weight = database.weightDao().weightNow(weightId)
            assertNotNull("the vet's number outlives the visit row", weight)
            assertEquals(2380, weight!!.grams)
            assertNull(weight.visitId)
            // The derived value follows the column with nothing to remember to clear.
            assertEquals(WeightSource.MANUAL, weight.source)
        }

    // ---- The unique index: one row, never a copy -------------------------------------------------

    @Test
    fun aSecondWeighingCannotClaimTheSameVisit() =
        runTest {
            val bunnyId = addBunny()
            val visitId = addVisit(bunnyId)
            addWeighing(bunnyId, grams = 2380, visitId = visitId)

            // `runCatching` rather than JUnit's `assertThrows`, which takes a non-suspending lambda.
            val failure = runCatching { addWeighing(bunnyId, grams = 2390, visitId = visitId) }.exceptionOrNull()

            assertTrue(
                "the index, not the editor, is what stops a second row claiming one visit: $failure",
                failure is SQLiteConstraintException,
            )
            assertEquals(1, database.countRows("weights"))
        }

    @Test
    fun manualWeighingsAreLeftUnconstrained() =
        runTest {
            val bunnyId = addBunny()
            addWeighing(bunnyId, grams = 2400)
            addWeighing(bunnyId, grams = 2410)
            addWeighing(bunnyId, grams = 2420)

            // NULLs are distinct in SQLite, which is what keeps a unique index off the ordinary path.
            assertEquals(3, database.countRows("weights"))
        }

    // ---- Converters and reads --------------------------------------------------------------------

    @Test
    fun aVisitDateRoundTripsAsACalendarDay() =
        runTest {
            val bunnyId = addBunny()
            // A day the epoch-day converter has to get exactly right, and a day no timezone shift
            // can produce by accident.
            val leapAdjacent = LocalDate.of(2026, 2, 28)
            val visitId = addVisit(bunnyId, visitedOn = leapAdjacent)

            assertEquals(leapAdjacent, database.visitDao().byIdNow(visitId)!!.visitedOn)
        }

    @Test
    fun aBunnysVisitsReadBackNewestFirst() =
        runTest {
            val bunnyId = addBunny()
            addVisit(bunnyId, visitedOn = visitedOn.minusMonths(1), reason = "Vaccination")
            addVisit(bunnyId, visitedOn = visitedOn, reason = "Molar check")
            // A second bunny's visit must not appear in the first bunny's list.
            addVisit(addBunny("Nugget"), reason = "Post-op check")

            val reasons =
                database
                    .visitDao()
                    .forBunny(bunnyId)
                    .first()
                    .map { it.reason }

            assertEquals(listOf("Molar check", "Vaccination"), reasons)
        }

    @Test
    fun theVetDirectoryIsAppWideAndSurvivesEveryBunny() =
        runTest {
            val bunnyId = addBunny()
            addVet()

            database.bunnyDao().deleteById(bunnyId)

            // No bunny FK to cascade through: a household's last rabbit leaving must not take the
            // clinic's phone number with it (ADR-0017).
            assertEquals(1, database.countRows("vets"))
        }

    // ---- What a delete confirmation quotes --------------------------------------------------------

    @Test
    fun recordCountsPutsVisitsInTheDestroyedBucketAndLeavesVetsOut() =
        runTest {
            val bunnyId = addBunny()
            val vetId = addVet()
            addVisit(bunnyId, vetId = vetId)
            addVisit(bunnyId, vetId = vetId, reason = "Vaccination")

            val counts = database.bunnyDao().recordCounts(bunnyId)!!

            // Two visits and nothing else — the vet is app-wide, so it is not something this delete
            // destroys and the confirmation must not claim it is (ADR-0004).
            assertEquals(2, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    @Test
    fun theEmptyPhaseFiveTablesCountHonestlyAsZero() =
        runTest {
            val bunnyId = addBunny()

            // Medications and documents have tables from 5b and no screen until 5d and 5g. A count of
            // zero is a true count, which is why the query can land with the schema.
            assertEquals(0, database.bunnyDao().recordCounts(bunnyId)!!.soleOwnedRecords)
            assertEquals(0, database.countRows("medication_courses"))
            assertEquals(0, database.countRows("doses"))
            assertEquals(0, database.countRows("documents"))
        }

    // ---- Helpers ---------------------------------------------------------------------------------

    private suspend fun addBunny(name: String = "Thumper"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    private suspend fun addVet(name: String = "Dr Kowalska"): String {
        val vet = VetEntity(name = name, clinic = "Klinika Ada", phone = "+48 22 000 00 00")
        database.vetDao().insert(vet)
        return vet.id
    }

    private suspend fun addVisit(
        bunnyId: String,
        vetId: String? = null,
        visitedOn: LocalDate = this.visitedOn,
        reason: String = "Molar check",
    ): String {
        val visit = VisitEntity(bunnyId = bunnyId, vetId = vetId, visitedOn = visitedOn, reason = reason)
        database.visitDao().insert(visit)
        return visit.id
    }

    private suspend fun addWeighing(
        bunnyId: String,
        grams: Int,
        visitId: String? = null,
    ): String {
        val weight =
            WeightEntity(
                bunnyId = bunnyId,
                grams = grams,
                // Distinct instants, so nothing here collides on ADR-0021's same-instant rule.
                recordedAt = recordedAt.plusSeconds(grams.toLong()),
                visitId = visitId,
            )
        database.weightDao().insert(weight)
        return weight.id
    }
}
