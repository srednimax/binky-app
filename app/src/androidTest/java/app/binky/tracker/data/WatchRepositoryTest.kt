package app.binky.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.work.WatchedBunny
import app.binky.tracker.work.watchesDueForNagging
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Checkpoint 4d's data layer: the cascade, the two facts that silence a nag, and archiving closing
 * a watch.
 *
 * Instrumented because every claim here is about SQLite — a cascade the ORM is only *asked* for is
 * not a cascade, and `MAX(recordedAt)` across the observations table is not proven by a type
 * checker. The clock arithmetic is `WatchStateTest`'s and `WatchSweepTest`'s, on the JVM, which is
 * why those take rows and instants rather than reaching for a database.
 *
 * The two nag tests below deliberately drive the **real** derivation, `watchesDueForNagging`, over
 * rows this repository wrote. Asserting the suppression against a hand-built fixture would prove the
 * arithmetic (which the JVM tests already do) and say nothing about whether the row the app stores
 * and the read the sweep performs actually meet.
 */
@RunWith(AndroidJUnit4::class)
class WatchRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var watches: WatchRepository
    private lateinit var bunnies: BunnyRepository
    private lateinit var observations: ObservationRepository

    /** Fixed, so "this morning" in a test is never the machine's actual clock. */
    private val morning: Instant = Instant.parse("2026-05-20T09:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 5, 20)
    private val utc: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        watches = WatchRepository(database)
        observations = ObservationRepository(database, temporaryMedia())
        bunnies =
            BunnyRepository(
                database = database,
                fluffles = FluffleRepository(database),
                preferences = temporaryPreferences(),
                media = temporaryMedia(),
            )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addBunny(name: String = "Bijou"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    /** The sweep's own question, asked over whatever this repository currently holds. */
    private suspend fun nagsAt(now: Instant): List<String> {
        val everyBunny = bunnies.activeBunnies.first() + bunnies.archivedBunnies.first()
        val watched =
            watches.watchesNow().mapNotNull { watch ->
                val bunny = everyBunny.firstOrNull { it.id == watch.bunnyId } ?: return@mapNotNull null
                WatchedBunny(
                    id = bunny.id,
                    name = bunny.name,
                    archived = bunny.archivedAt != null,
                    watch = watch,
                    lastObservationAt = watches.latestObservationNow(bunny.id),
                )
            }
        return watchesDueForNagging(watched, now, utc).map { it.bunnyId }
    }

    @Test
    fun aWatchGoesWithItsBunny() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning)
            assertEquals(1, database.countRows("watches"))

            bunnies.delete(bunnyId)

            // The primary-key-as-foreign-key shape (2a's precedent) makes discard-on-delete a database
            // constraint rather than a rule somebody has to remember on the delete path.
            assertEquals(0, database.countRows("watches"))
        }

    @Test
    fun archivingClosesTheWatch() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning)

            bunnies.archive(bunnyId)

            // An archived bunny has died or been rehomed. A daily "have you checked on them today?" is
            // the failure ADR-0001 names for a flag on a memorial page (ADR-0004).
            assertNull(watches.watch(bunnyId).first())
            assertTrue(nagsAt(morning).isEmpty())
        }

    @Test
    fun lastNaggedOnSuppressesASecondNagTheSameDayAndNotTheNext() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning)

            assertEquals(listOf(bunnyId), nagsAt(morning))
            watches.markNagged(bunnyId, today)

            // The sweep can run more than once a day — a retry, a reboot, a Doze window closing — so
            // "once daily" is a recorded fact rather than an assumption about the scheduler.
            assertTrue(nagsAt(morning.plus(Duration.ofHours(6))).isEmpty())
            assertEquals(listOf(bunnyId), nagsAt(morning.plus(Duration.ofDays(1))))
        }

    @Test
    fun anObservationLoggedLastEveningSettlesThisMorningAndOneThirtyHoursAgoDoesNot() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning.minus(Duration.ofDays(1)))

            // 20:00 the previous evening: thirteen hours before the sweep, so nobody is chased.
            val lastEvening = Instant.parse("2026-05-19T20:00:00Z")
            observations.add(listOf(bunnyId), lastEvening, healthyDayFacts())
            assertTrue(nagsAt(morning).isEmpty())

            // And the pair that makes the rolling window real: the same record read a day later is
            // thirty-seven hours old, and thirty-seven hours ago is not "recently".
            assertEquals(listOf(bunnyId), nagsAt(morning.plus(Duration.ofDays(1))))
        }

    @Test
    fun anObservationForAHousemateDoesNotSettleThisBunnysNag() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            watches.start(bijou, WatchDuration.DAYS_7, morning.minus(Duration.ofDays(1)))
            observations.add(listOf(nugget), morning.minus(Duration.ofHours(2)), healthyDayFacts())

            // `forBunny`'s rule, one layer up: what is recorded against a bunny is that bunny's own
            // rows. A shared observation writes one row each, so covering Bijou would have settled it —
            // and one that covered only Nugget says nothing about Bijou at all (ADR-0008).
            assertEquals(listOf(bijou), nagsAt(morning))
        }

    @Test
    fun extendingRewritesTheEndAndClearsTheNagWatermark() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_3, morning)
            watches.markNagged(bunnyId, today)

            val expired = morning.plus(Duration.ofDays(4))
            assertTrue(watchState(watches.watch(bunnyId).first(), expired) is WatchState.Expired)

            watches.extend(bunnyId, WatchDuration.DAYS_7, expired)

            val row = watches.watch(bunnyId).first()
            assertNotNull(row)
            // From *now*, not from the date it ran out: an owner answering three days late is saying
            // they are still watching today.
            assertEquals(expired.plus(Duration.ofDays(7)), row!!.endsAt)
            // Cleared in the same statement, or the first morning of the extended watch — the morning
            // the owner just said they were still worried about — would be silently skipped.
            assertNull(row.lastNaggedOn)
            assertEquals(listOf(bunnyId), nagsAt(expired))
        }

    @Test
    fun closingDeletesTheRowAndStartingAgainIsNotBlockedByAStaleOne() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning)
            watches.close(bunnyId)

            // A watch is a present-tense state, not a record: there is no history to keep.
            assertEquals(0, database.countRows("watches"))

            // And the start path is an upsert, so an unanswered expired row cannot occupy the only slot
            // this bunny has.
            watches.start(bunnyId, WatchDuration.DAYS_3, morning)
            watches.markNagged(bunnyId, today)
            watches.start(bunnyId, WatchDuration.DAYS_14, morning.plus(Duration.ofDays(9)))

            val row = watches.watch(bunnyId).first()!!
            assertEquals(1, database.countRows("watches"))
            assertNull("a restarted watch must not inherit yesterday's watermark", row.lastNaggedOn)
            assertEquals(morning.plus(Duration.ofDays(9 + 14)), row.endsAt)
        }

    @Test
    fun onlyARunningWatchExcludesAHousemateFromTheHealthyDay() =
        runTest {
            val bijou = addBunny("Bijou")
            val nugget = addBunny("Nugget")
            watches.start(nugget, WatchDuration.DAYS_3, morning)

            // Nugget is excluded from anything the owner logs for Bijou; Bijou never is.
            assertEquals(setOf(nugget), watches.activelyWatchedIdsNow(morning))
            val preSelection =
                preSelectParticipants(
                    subject = bunnies.bunnyNow(bijou)!!,
                    fluffleMembers = bunnies.activeBunnies.first(),
                    activelyWatchedIds = watches.activelyWatchedIdsNow(morning),
                )
            assertEquals(listOf(bijou), preSelection.bunnyIds)
            assertEquals(ParticipantExclusion.UNDER_WATCH, preSelection.excluded.single().reason)

            // An unanswered prompt is not an active watch: expiry stops the nagging on its own, and
            // leaving a bunny out of the one-tap path because nobody has answered a dialog yet would be
            // the app acting on a question it has not asked.
            assertEquals(emptySet<String>(), watches.activelyWatchedIdsNow(morning.plus(Duration.ofDays(4))))
        }

    @Test
    fun aLocalDateWatermarkSurvivesTheRoundTrip() =
        runTest {
            val bunnyId = addBunny()
            watches.start(bunnyId, WatchDuration.DAYS_7, morning)
            watches.markNagged(bunnyId, today)

            // The converter, proven rather than assumed — the same claim `CareRepositoryTest` makes for
            // a completion date, and for the same reason: a day is not an instant.
            assertEquals(today, watches.watch(bunnyId).first()!!.lastNaggedOn)
        }
}
