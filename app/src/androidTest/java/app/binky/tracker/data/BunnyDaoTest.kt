package app.binky.tracker.data

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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BunnyDaoTest {
    private lateinit var database: BunnyDatabase
    private lateinit var dao: BunnyDao

    @Before
    fun open() {
        database = inMemoryDatabase()
        dao = database.bunnyDao()
    }

    @After
    fun close() = database.close()

    private val thumper =
        BunnyEntity(
            id = "thumper",
            name = "Thumper",
            avatarPath = "avatars/9f1c.jpg",
            birthDate = LocalDate.of(2022, 3, 14),
            birthDateApproximate = true,
            sex = Sex.FEMALE,
            neutered = NeuterStatus.YES,
            breed = "Netherland dwarf",
            colour = "Broken black",
            // Whole milliseconds: the converter stores epoch millis, so a nanosecond-precision
            // Instant.now() would not survive the round trip and the test would be about the clock.
            createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        )

    @Test
    fun everyProfileFieldSurvivesTheRoundTrip() =
        runTest {
            dao.insert(thumper)
            assertEquals(thumper, dao.bunnyNow("thumper"))
        }

    @Test
    fun archivingKeepsTheBunnyOutOfTheActiveListButNotOutOfReach() =
        runTest {
            dao.insert(thumper)
            dao.insert(BunnyEntity(id = "clover", name = "Clover"))

            dao.setArchivedAt("thumper", Instant.ofEpochMilli(1_700_000_100_000))

            assertEquals(listOf("clover"), dao.activeBunnies().first().map { it.id })
            assertEquals(listOf("thumper"), dao.archivedBunnies().first().map { it.id })
            // Reachable from More, with its records intact — archiving is not deleting (ADR-0004).
            assertNotNull(dao.bunny("thumper").first())
        }

    @Test
    fun unarchivingPutsHerBack() =
        runTest {
            dao.insert(thumper)
            dao.setArchivedAt("thumper", Instant.ofEpochMilli(1_700_000_100_000))
            dao.setArchivedAt("thumper", null)

            assertEquals(listOf("thumper"), dao.activeBunnies().first().map { it.id })
            assertEquals(emptyList<String>(), dao.archivedBunnies().first().map { it.id })
        }

    @Test
    fun enumsAreStoredByNameNeverByOrdinal() =
        runTest {
            dao.insert(thumper)

            database.openHelper.readableDatabase
                .query("SELECT sex, neutered FROM bunnies WHERE id = 'thumper'")
                .use { cursor ->
                    cursor.moveToFirst()
                    // "FEMALE", not 1 — an ordinal column silently rewrites history the day a
                    // value is inserted into the middle of the enum.
                    assertEquals("FEMALE", cursor.getString(0))
                    assertEquals("YES", cursor.getString(1))
                }
        }

    @Test
    fun activeBunniesAreOrderedByNameCaseInsensitively() =
        runTest {
            dao.insert(BunnyEntity(id = "a", name = "clover"))
            dao.insert(BunnyEntity(id = "b", name = "Bramble"))
            dao.insert(BunnyEntity(id = "c", name = "hazel"))

            assertEquals(listOf("Bramble", "clover", "hazel"), dao.activeBunnies().first().map { it.name })
        }

    @Test
    fun recordCountsAreZeroInPhase1ButComeFromTheRealQuery() =
        runTest {
            dao.insert(thumper)

            assertEquals(RecordCounts(soleOwnedRecords = 0, sharedRecords = 0), dao.recordCounts("thumper"))
            assertNull(dao.recordCounts("no-such-bunny"))
        }
}
