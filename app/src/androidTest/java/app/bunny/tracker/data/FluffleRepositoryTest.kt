package app.bunny.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The fluffle logic, proven before any UI exists — ADR-0008's one dissolve predicate, shared by
 * editing, deleting and archiving.
 */
@RunWith(AndroidJUnit4::class)
class FluffleRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var fluffles: FluffleRepository
    private lateinit var bunnies: BunnyRepository

    @Before
    fun open() {
        database = inMemoryDatabase()
        preferences = temporaryPreferences()
        fluffles = FluffleRepository(database)
        bunnies = BunnyRepository(database, fluffles, preferences, temporaryMedia())
    }

    @After
    fun close() = database.close()

    private suspend fun addBunny(name: String) = bunnies.add(BunnyEntity(name = name))

    private suspend fun fluffleIdOf(bunnyId: String) = database.bunnyDao().bunnyNow(bunnyId)?.fluffleId

    @Test
    fun livesWithIsASymmetricJoin() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")

            fluffles.livesWith(thumper, clover)

            val fluffleId = fluffleIdOf(thumper)
            assertNotNull(fluffleId)
            // Both members, one row — not "Thumper points at Clover".
            assertEquals(fluffleId, fluffleIdOf(clover))
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun joiningSomeoneWhoAlreadyLivesWithAThirdBunnyJoinsTheExistingFluffle() =
        runTest {
            val clover = addBunny("Clover")
            val hazel = addBunny("Hazel")
            fluffles.livesWith(clover, hazel)
            val existing = fluffleIdOf(clover)

            val thumper = addBunny("Thumper")
            fluffles.livesWith(thumper, clover)

            assertEquals(existing, fluffleIdOf(thumper))
            assertEquals(existing, fluffleIdOf(hazel))
            // A rival pair would be the failure: one bunny, one bonded group, one shared tray.
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun livingTogetherTwiceChangesNothing() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)
            val fluffleId = fluffleIdOf(thumper)

            fluffles.livesWith(thumper, clover)

            assertEquals(fluffleId, fluffleIdOf(thumper))
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun editingAMemberOutOfAPairRevertsTheSurvivorToSoloAndRemovesTheRow() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)

            fluffles.leaveFluffle(thumper)

            // Both are solo: neither shares a tray any more, and both are active.
            assertNull(fluffleIdOf(thumper))
            assertNull(fluffleIdOf(clover))
            assertEquals(0, database.countRows("fluffles"))
        }

    @Test
    fun editingAMemberOutOfATrioLeavesTheRemainingTwoAFluffle() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            val hazel = addBunny("Hazel")
            fluffles.livesWith(thumper, clover)
            fluffles.livesWith(hazel, clover)
            val fluffleId = fluffleIdOf(clover)

            fluffles.leaveFluffle(thumper)

            assertNull(fluffleIdOf(thumper))
            assertEquals(fluffleId, fluffleIdOf(clover))
            assertEquals(fluffleId, fluffleIdOf(hazel))
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun archivingAMemberChangesNothing() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)
            val fluffleId = fluffleIdOf(thumper)

            bunnies.archive(thumper, at = Instant.ofEpochMilli(1_700_000_000_000))

            // The archived bunny is still a member, so the count does not move — the survivor of a
            // bonded pair genuinely did live with her.
            assertEquals(fluffleId, fluffleIdOf(thumper))
            assertEquals(fluffleId, fluffleIdOf(clover))
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun deletingOneOfAPairDissolvesIt() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)

            bunnies.delete(thumper)

            assertNull(fluffleIdOf(clover))
            assertEquals(0, database.countRows("fluffles"))
        }

    @Test
    fun deletingFromATrioWithAnArchivedMemberLeavesTheRowStanding() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            val hazel = addBunny("Hazel")
            fluffles.livesWith(thumper, clover)
            fluffles.livesWith(hazel, clover)
            val fluffleId = fluffleIdOf(clover)
            bunnies.archive(hazel, at = Instant.ofEpochMilli(1_700_000_000_000))

            bunnies.delete(thumper)

            // Two members left, counting the archived one — so Clover keeps having lived with
            // Hazel. An active-only rule would have erased exactly the fact archiving protects.
            assertEquals(fluffleId, fluffleIdOf(clover))
            assertEquals(fluffleId, fluffleIdOf(hazel))
            assertEquals(1, database.countRows("fluffles"))
        }

    @Test
    fun deletingAFluffleNeverDeletesABunny() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)

            database.fluffleDao().deleteById(fluffleIdOf(thumper)!!)

            // ON DELETE SET NULL, not CASCADE — ending a living arrangement is not a bereavement.
            assertEquals(2, database.countRows("bunnies"))
            assertNull(fluffleIdOf(thumper))
            assertNull(fluffleIdOf(clover))
        }

    @Test
    fun aBlankFluffleNameIsStoredAsNoneSoTheMembersLabelIt() =
        runTest {
            val thumper = addBunny("Thumper")
            val clover = addBunny("Clover")
            fluffles.livesWith(thumper, clover)
            val fluffleId = fluffleIdOf(thumper)!!

            fluffles.rename(fluffleId, "  The Girls  ")
            assertEquals("The Girls", fluffles.fluffle(fluffleId).first()?.name)

            fluffles.rename(fluffleId, "   ")
            assertNull(fluffles.fluffle(fluffleId).first()?.name)
        }

    @Test
    fun deletingTheSelectedBunnyClearsItFromTheStoredSelection() =
        runTest {
            val thumper = addBunny("Thumper")
            preferences.setSelection(StoredSelection.Bunny(thumper))

            bunnies.delete(thumper)

            // Nothing left to dangle: that bunny is never coming back.
            assertEquals(StoredSelection.None, preferences.selection.first())
        }

    @Test
    fun archivingTheSelectedBunnyLeavesTheStoredSelectionAlone() =
        runTest {
            val thumper = addBunny("Thumper")
            preferences.setSelection(StoredSelection.Bunny(thumper))

            bunnies.archive(thumper, at = Instant.ofEpochMilli(1_700_000_000_000))

            // The resolver heals on read, so unarchiving her a week later restores the choice
            // (ADR-0015). Writing over it here is what would make that impossible.
            assertEquals(StoredSelection.Bunny(thumper), preferences.selection.first())
        }

    @Test
    fun aNameIsTrimmedAndAnEmptyOneRejected() =
        runTest {
            assertEquals("Thumper", database.bunnyDao().bunnyNow(addBunny("  Thumper  "))?.name)
            runCatching { addBunny("   ") }.onSuccess { error("a blank name should be rejected") }
        }
}
