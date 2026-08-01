package app.binky.tracker.data

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
import java.time.ZoneId

/**
 * Checkpoint 4b's data layer: the cascades, the `LocalDate` round-trip, the counts a delete
 * confirmation quotes, and the one read that reaches outside the care tables.
 *
 * Instrumented because every claim here is about SQLite's behaviour — a cascade the ORM is only
 * *asked* for is not a cascade, and a converter is not proven by the type checker. The schedule
 * arithmetic is `CareScheduleTest`'s, on the JVM, which is why the pure functions take dates rather
 * than entities.
 */
@RunWith(AndroidJUnit4::class)
class CareRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var care: CareRepository
    private lateinit var weights: WeightRepository

    /** Fixed, so "today" in a test is never the machine's actual date. */
    private val today = LocalDate.of(2026, 5, 20)
    private val utc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        care = CareRepository(database)
        weights = WeightRepository(database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addBunny(name: String = "Thumper"): String {
        val bunny = BunnyEntity(name = name)
        database.bunnyDao().insert(bunny)
        return bunny.id
    }

    private suspend fun addReminder(
        bunnyId: String,
        type: CareType? = CareType.NAIL_TRIM,
        label: String? = null,
        firstDueOn: LocalDate = today,
        count: Int = 6,
        unit: CareIntervalUnit = CareIntervalUnit.WEEK,
    ): String =
        care.add(
            CareReminderEntity(
                bunnyId = bunnyId,
                label = label,
                type = type,
                intervalCount = count,
                intervalUnit = unit,
                firstDueOn = firstDueOn,
            ),
        )

    // ---- Cascades: two hops, both enforced by the database ---------------------------------------

    @Test
    fun remindersAndTheirEventsGoWithTheBunny() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId)
            care.complete(reminderId, today.minusWeeks(1), today = today)

            database.bunnyDao().deleteById(bunnyId)

            assertEquals(0, database.countRows("care_reminders"))
            assertEquals("the second hop cascades too", 0, database.countRows("care_events"))
        }

    @Test
    fun eventsGoWithTheirReminder() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId)
            care.complete(reminderId, today.minusWeeks(1), today = today)
            care.complete(reminderId, today.minusWeeks(3), today = today)
            val survivor = addReminder(bunnyId, type = CareType.VACCINATION)
            care.complete(survivor, today.minusMonths(2), today = today)

            care.delete(reminderId)

            assertEquals(1, database.countRows("care_reminders"))
            assertEquals("only the deleted reminder's history goes", 1, database.countRows("care_events"))
        }

    // ---- The LocalDate round-trip ----------------------------------------------------------------

    @Test
    fun careDatesSurviveAsDaysRatherThanMoments() =
        runTest {
            // The reason care dates are LocalDate at all: an instant would land on a different day
            // the first time the owner opened the app in another timezone. Stored as an epoch day
            // there is no time of day to shift.
            val bunnyId = addBunny()
            val due = LocalDate.of(2026, 12, 31)
            val reminderId = addReminder(bunnyId, firstDueOn = due)
            care.complete(reminderId, LocalDate.of(2026, 1, 1), note = "  ", today = today)
            care.markNotified(reminderId, due)

            val stored = care.reminder(reminderId)!!
            assertEquals(due, stored.firstDueOn)
            assertEquals(due, stored.notifiedForDueOn)

            val events = care.events(reminderId).first()
            assertEquals(LocalDate.of(2026, 1, 1), events.single().completedOn)
            assertNull("a whitespace-only note is no note", events.single().note)
        }

    @Test
    fun theTypeAndTheIntervalUnitRoundTripByName() =
        runTest {
            val bunnyId = addBunny()
            val id = addReminder(bunnyId, type = CareType.VACCINATION, count = 1, unit = CareIntervalUnit.YEAR)

            val stored = care.reminder(id)!!
            assertEquals(CareType.VACCINATION, stored.type)
            assertEquals(CareIntervalUnit.YEAR, stored.intervalUnit)
            assertEquals(
                "stored by name, never ordinal",
                "VACCINATION",
                database.rawValue("SELECT type FROM care_reminders WHERE id = '$id'"),
            )
            assertEquals(
                "YEAR",
                database.rawValue("SELECT intervalUnit FROM care_reminders WHERE id = '$id'"),
            )
        }

    @Test
    fun aCustomReminderKeepsItsLabelAndHasNoType() =
        runTest {
            val bunnyId = addBunny()
            val id = addReminder(bunnyId, type = null, label = "  Reorder hay  ")

            val stored = care.reminder(id)!!
            assertEquals("trimmed, not stored as typed", "Reorder hay", stored.label)
            assertNull("a reminder with no type is normal, not a data error (ADR-0018)", stored.type)
        }

    @Test
    fun aReminderWithNeitherLabelNorTypeIsRefused() =
        runTest {
            val bunnyId = addBunny()
            val refusal = runCatching { addReminder(bunnyId, type = null, label = "   ") }
            assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
        }

    // ---- What a delete confirmation quotes (ADR-0004) --------------------------------------------

    @Test
    fun recordCountsCountsRemindersAndTheirEvents() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId)
            care.complete(reminderId, today.minusWeeks(1), today = today)
            care.complete(reminderId, today.minusWeeks(7), today = today)

            val counts = database.bunnyDao().recordCounts(bunnyId)!!
            // Sole-owned in both hops: nothing here survives the bunny.
            assertEquals(3, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    // ---- The derived schedule --------------------------------------------------------------------

    @Test
    fun theScheduleIsSortedBySoonestDue() =
        runTest {
            val bunnyId = addBunny()
            val later = addReminder(bunnyId, type = CareType.VACCINATION, firstDueOn = today.plusMonths(6))
            val sooner = addReminder(bunnyId, firstDueOn = today.plusDays(2))

            val schedule = care.scheduleNow(bunnyId, utc)

            // Created second, due first — a sort SQL cannot do, because the key is not a column.
            assertEquals(listOf(sooner, later), schedule.map { it.reminder.id })
            assertEquals(today.plusDays(2), schedule.first().dueOn)
        }

    @Test
    fun aCompletionMovesTheDueDateAndNothingIsStored() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId, firstDueOn = today)
            care.markNotified(reminderId, today)

            care.complete(reminderId, today, today = today)

            val scheduled = care.scheduleNow(bunnyId, utc).single()
            assertEquals(today.plusWeeks(6), scheduled.dueOn)
            assertEquals(
                "the watermark is left alone; it is compared against derived truth, not cleared",
                today,
                scheduled.reminder.notifiedForDueOn,
            )
            assertTrue(scheduled.needsNotifying(today.plusWeeks(6)))
        }

    @Test
    fun deletingAnEventHealsTheScheduleWithNoBookkeeping() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId, firstDueOn = today)
            val eventId = care.complete(reminderId, today, today = today)
            assertEquals(today.plusWeeks(6), care.scheduleNow(bunnyId, utc).single().dueOn)

            care.deleteEvent(eventId)

            assertEquals(
                "back to the anchor, unmodified",
                today,
                care.scheduleNow(bunnyId, utc).single().dueOn,
            )
        }

    @Test
    fun theLatestCompletionWinsWhateverOrderTheyWereEnteredIn() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId, firstDueOn = today.minusMonths(6))
            // Newest first, then a back-dated one typed afterwards: MAX, not "the last row written".
            care.complete(reminderId, today.minusWeeks(2), today = today)
            care.complete(reminderId, today.minusWeeks(9), today = today)

            assertEquals(
                today.minusWeeks(2).plusWeeks(6),
                care.scheduleNow(bunnyId, utc).single().dueOn,
            )
        }

    @Test
    fun aCompletionInTheFutureIsRefused() =
        runTest {
            val bunnyId = addBunny()
            val reminderId = addReminder(bunnyId)
            val refusal = runCatching { care.complete(reminderId, today.plusDays(1), today = today) }
            assertTrue(refusal.exceptionOrNull() is IllegalArgumentException)
        }

    // ---- The one read that reaches outside the care tables (ADR-0018's amendment) ----------------

    @Test
    fun aWeighingSatisfiesAWeighInReminderWithoutWritingAnything() =
        runTest {
            val bunnyId = addBunny()
            addReminder(bunnyId, type = CareType.WEIGH_IN, count = 1, unit = CareIntervalUnit.WEEK)
            weights.add(
                WeightEntity(
                    bunnyId = bunnyId,
                    grams = 2500,
                    recordedAt = Instant.parse("2026-05-18T09:00:00Z"),
                ),
            )

            val scheduled = care.scheduleNow(bunnyId, utc).single()

            assertEquals(LocalDate.of(2026, 5, 18), scheduled.lastCompletedOn)
            assertEquals(LocalDate.of(2026, 5, 25), scheduled.dueOn)
            assertEquals("read-side only", 0, database.countRows("care_events"))
        }

    @Test
    fun aWeighInTakesTheLaterOfItsOwnCompletionsAndTheWeightSeries() =
        runTest {
            val bunnyId = addBunny()
            val reminderId =
                addReminder(bunnyId, type = CareType.WEIGH_IN, count = 1, unit = CareIntervalUnit.WEEK)
            care.complete(reminderId, LocalDate.of(2026, 5, 19), today = today)
            weights.add(
                WeightEntity(
                    bunnyId = bunnyId,
                    grams = 2500,
                    recordedAt = Instant.parse("2026-05-14T09:00:00Z"),
                ),
            )

            assertEquals(
                "the tick is newer than the weighing, so it wins",
                LocalDate.of(2026, 5, 19),
                care.scheduleNow(bunnyId, utc).single().lastCompletedOn,
            )
        }

    @Test
    fun anotherBunnysWeighingDoesNotSatisfyThisOne() =
        runTest {
            val thumper = addBunny("Thumper")
            val bijou = addBunny("Bijou")
            addReminder(thumper, type = CareType.WEIGH_IN, count = 1, unit = CareIntervalUnit.WEEK)
            weights.add(
                WeightEntity(bunnyId = bijou, grams = 1900, recordedAt = Instant.parse("2026-05-18T09:00:00Z")),
            )

            val scheduled = care.scheduleNow(thumper, utc).single()
            assertNull(scheduled.lastCompletedOn)
            assertEquals("still on its anchor", today, scheduled.dueOn)
        }

    @Test
    fun aNailTrimIgnoresTheWeightSeriesEntirely() =
        runTest {
            val bunnyId = addBunny()
            addReminder(bunnyId, type = CareType.NAIL_TRIM, firstDueOn = today)
            weights.add(
                WeightEntity(bunnyId = bunnyId, grams = 2500, recordedAt = Instant.parse("2026-05-18T09:00:00Z")),
            )

            val scheduled = care.scheduleNow(bunnyId, utc).single()
            assertNull("weighing a bunny does not trim its nails", scheduled.lastCompletedOn)
        }

    @Test
    fun theWeighingsDayIsReadInTheGivenZone() =
        runTest {
            // 23:30 UTC is already the next day in Warsaw. The date a weighing counts for is the
            // owner's day, which is why the zone is a parameter rather than a systemDefault() call
            // buried in the repository.
            val bunnyId = addBunny()
            addReminder(bunnyId, type = CareType.WEIGH_IN, count = 1, unit = CareIntervalUnit.WEEK)
            weights.add(
                WeightEntity(bunnyId = bunnyId, grams = 2500, recordedAt = Instant.parse("2026-05-18T23:30:00Z")),
            )

            assertEquals(
                LocalDate.of(2026, 5, 18),
                care.scheduleNow(bunnyId, utc).single().lastCompletedOn,
            )
            assertEquals(
                LocalDate.of(2026, 5, 19),
                care.scheduleNow(bunnyId, ZoneId.of("Europe/Warsaw")).single().lastCompletedOn,
            )
        }

    // ---- The flow the Care screen will actually collect -------------------------------------------

    @Test
    fun theScheduleFlowSeesAWeighingLoggedInAnotherTab() =
        runTest {
            // The one test of `schedule` as a flow rather than a one-shot, and the reason it exists:
            // three DAO flows are combined, and a weigh-in's date moves when a *weight* is written —
            // a table the care screen never touches. If that wiring is wrong, every other test here
            // still passes because they read after the fact.
            val bunnyId = addBunny()
            addReminder(bunnyId, type = CareType.WEIGH_IN, count = 1, unit = CareIntervalUnit.WEEK)
            assertEquals(
                today,
                care
                    .schedule(bunnyId, utc)
                    .first()
                    .single()
                    .dueOn,
            )

            weights.add(
                WeightEntity(bunnyId = bunnyId, grams = 2500, recordedAt = Instant.parse("2026-05-18T09:00:00Z")),
            )

            assertEquals(
                LocalDate.of(2026, 5, 25),
                care
                    .schedule(bunnyId, utc)
                    .first()
                    .single()
                    .dueOn,
            )
        }
}

/** One scalar, straight out of SQLite, for asserting how a value is *stored* rather than read back. */
private fun BunnyDatabase.rawValue(sql: String): String? =
    openHelper.readableDatabase.query(sql).use { cursor ->
        cursor.moveToFirst()
        if (cursor.isNull(0)) null else cursor.getString(0)
    }
