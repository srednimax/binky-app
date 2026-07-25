package app.bunny.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Checkpoint 2a's data layer: the cascades that make discard-on-delete a database constraint rather
 * than a rule someone has to remember, and the repository's insert-time discard.
 *
 * Instrumented because these are claims about SQLite's behaviour — a cascade the ORM is only
 * *asked* for is not a cascade. The trend arithmetic itself is checkpoint 2b's, as JVM tests.
 */
@RunWith(AndroidJUnit4::class)
class WeightRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var weights: WeightRepository

    /** A fixed base so `recordedAt` values are deliberate rather than accidentally tied. */
    private val start: Instant = Instant.parse("2026-01-01T09:00:00Z")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        weights = WeightRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addBunny(name: String = "Thumper"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    /** Days after [start], so a series reads as a sequence rather than as a pile of timestamps. */
    private suspend fun weigh(
        bunnyId: String,
        grams: Int,
        day: Long,
    ): String =
        weights.add(
            WeightEntity(
                bunnyId = bunnyId,
                grams = grams,
                recordedAt = start.plus(day, ChronoUnit.DAYS),
            ),
        )

    /** Three steady priors then a 200 g drop: baseline 2500, threshold 125 g, so the trigger holds. */
    private suspend fun seriesThatFlags(bunnyId: String): String {
        weigh(bunnyId, 2500, day = 0)
        weigh(bunnyId, 2500, day = 7)
        weigh(bunnyId, 2500, day = 14)
        return weigh(bunnyId, 2300, day = 21)
    }

    @Test
    fun weightsCascadeWithTheirBunny() =
        runTest {
            val kept = addBunny("Clover")
            val deleted = addBunny("Nugget")
            weigh(kept, 1800, day = 0)
            weigh(deleted, 2000, day = 0)
            weigh(deleted, 1950, day = 7)

            database.bunnyDao().deleteById(deleted)

            assertEquals(1, database.countRows("weights"))
        }

    @Test
    fun anAcknowledgmentGoesWithTheWeightItNames() =
        runTest {
            val bunnyId = addBunny()
            val flagged = seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)
            assertEquals(1, database.countRows("trend_acknowledgments"))

            // Through the DAO rather than the repository: the repository would discard the row by
            // its own rule, and the claim under test is that the *database* would have anyway.
            database.weightDao().deleteById(flagged)

            assertEquals(0, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun anAcknowledgmentGoesWithItsBunny() =
        runTest {
            val bunnyId = addBunny()
            seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)

            // Not via the weight it names — the direct bunnyId foreign key is what makes this a
            // constraint rather than a two-hop cascade accident.
            database.bunnyDao().deleteById(bunnyId)

            assertEquals(0, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun anInsertThatTakesTheTriggerFalseDiscardsTheAcknowledgment() =
        runTest {
            val bunnyId = addBunny()
            seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)

            // Back to 2500: priors are 2300, 2500, 2500 so the baseline is still 2500, and 2500 is
            // not 125 g below it. The episode is over, so the watermark must not survive to silence
            // the next one (ADR-0001).
            weigh(bunnyId, 2500, day = 28)

            assertEquals(0, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun anInsertThatKeepsTheTriggerTrueKeepsTheAcknowledgment() =
        runTest {
            val bunnyId = addBunny()
            seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)

            // Still down: the episode continues, and this is what 2b's re-raise bar judges. An
            // unconditional discard here would re-raise the flag on every weighing of a slow
            // recovery, which is the dismissal failure ADR-0001 exists to prevent.
            weigh(bunnyId, 2250, day = 28)

            assertEquals(1, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun editingAnyWeightDiscardsTheAcknowledgmentUnconditionally() =
        runTest {
            val bunnyId = addBunny()
            seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)

            // The *oldest* prior, nothing to do with the flagged reading — a watermark measured
            // against a baseline that no longer exists is an unagreed suppression (ADR-0001).
            val oldest = database.weightDao().seriesNow(bunnyId).last()
            weights.update(oldest.copy(grams = 2600))

            assertEquals(0, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun deletingAnyWeightDiscardsTheAcknowledgmentUnconditionally() =
        runTest {
            val bunnyId = addBunny()
            seriesThatFlags(bunnyId)
            weights.acknowledgeTrend(bunnyId)

            val oldest = database.weightDao().seriesNow(bunnyId).last()
            weights.delete(oldest.id)

            assertEquals(0, database.countRows("trend_acknowledgments"))
        }

    @Test
    fun weighingsMakeTheDeleteConfirmationTwoStage() =
        runTest {
            val bunnyId = addBunny()
            assertEquals(
                DeleteConfirmation.SINGLE,
                deleteConfirmationFor(database.bunnyDao().recordCounts(bunnyId)!!),
            )

            weigh(bunnyId, 2500, day = 0)

            val counts = database.bunnyDao().recordCounts(bunnyId)!!
            assertEquals(1, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
            assertEquals(DeleteConfirmation.TWO_STAGE, deleteConfirmationFor(counts))
        }
}
