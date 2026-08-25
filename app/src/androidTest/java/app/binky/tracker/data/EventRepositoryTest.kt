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

/**
 * Events against a real database (ADR-0031).
 *
 * Instrumented rather than JVM because every claim here is about **SQLite** rather than about
 * Kotlin: the day query, the cascade that takes an event with its bunny, and the two stamps the
 * sweep and the calendar hand-off write. The merging and ordering that make a timeline out of these
 * rows are pure and live in `TimelineTest`.
 */
@RunWith(AndroidJUnit4::class)
class EventRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var events: EventRepository
    private lateinit var bunnies: BunnyRepository

    private val day = LocalDate.of(2026, 3, 14)

    @Before
    fun open() {
        database = inMemoryDatabase()
        events = EventRepository(database)
        bunnies = BunnyRepository(database, FluffleRepository(database), temporaryPreferences(), temporaryMedia())
    }

    @After
    fun close() = database.close()

    @Test
    fun anEventSurvivesTheRoundTripWithItsDayAndNote() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))

            val id = events.add(EventEntity(bunnyId = bunnyId, label = "Neutering", occursOn = day, note = "10am"))

            val stored = checkNotNull(events.eventNow(id))
            assertEquals("Neutering", stored.label)
            assertEquals(day, stored.occursOn)
            assertEquals("10am", stored.note)
            // Neither stamp is written by an ordinary save — only the sweep and the hand-off set them.
            assertNull(stored.notifiedAt)
            assertNull(stored.calendarHandedOffAt)
        }

    @Test
    fun aDateInTheFutureIsAcceptedAndSoIsOneInThePast() =
        runTest {
            // The one dated write in the app that refuses neither direction. Every other one is a
            // record of something that happened; an event is as often an appointment as a keepsake.
            val bunnyId = bunnies.add(BunnyEntity(name = "Clover"))

            events.add(EventEntity(bunnyId = bunnyId, label = "Vaccination", occursOn = day.plusYears(1)))
            events.add(EventEntity(bunnyId = bunnyId, label = "Came home", occursOn = day.minusYears(3)))

            assertEquals(2, events.eventsNow(bunnyId).size)
        }

    @Test
    fun aBlankLabelIsRefusedBeforeItReachesTheDatabase() =
        runTest {
            // The label is the whole record, so a blank one leaves nothing to render — and unlike a
            // care reminder there is no type to fall back on.
            val bunnyId = bunnies.add(BunnyEntity(name = "Hazel"))

            // `runCatching` rather than JUnit's `assertThrows`, which takes a non-suspending
            // lambda — the same choice `VisitDaoTest` makes, for the same reason.
            val result = runCatching { events.add(EventEntity(bunnyId = bunnyId, label = "   ", occursOn = day)) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, events.eventsNow(bunnyId).size)
        }

    @Test
    fun aLabelIsTrimmedAndABlankNoteBecomesNull() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Nugget"))

            val id = events.add(EventEntity(bunnyId = bunnyId, label = "  Adoption day  ", occursOn = day, note = " "))

            val stored = checkNotNull(events.eventNow(id))
            assertEquals("Adoption day", stored.label)
            assertNull("\"\" and \"no note\" are the same fact", stored.note)
        }

    @Test
    fun theDayQueryAnswersForOneBunnyAndOneDayOnly() =
        runTest {
            // The sweep's read. It deliberately does **not** filter on notifiedAt — "announces once"
            // lives in `eventsDueForNotifying`, where it is a JVM assertion rather than SQL.
            val mine = bunnies.add(BunnyEntity(name = "Bijou"))
            val theirs = bunnies.add(BunnyEntity(name = "Pippin"))
            events.add(EventEntity(bunnyId = mine, label = "Today", occursOn = day))
            events.add(EventEntity(bunnyId = mine, label = "Tomorrow", occursOn = day.plusDays(1)))
            events.add(EventEntity(bunnyId = theirs, label = "Also today", occursOn = day))

            val onTheDay = events.onDayNow(mine, day)

            assertEquals(listOf("Today"), onTheDay.map { it.label })
        }

    @Test
    fun anAlreadyNotifiedEventStillComesBackFromTheDayQuery() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Bramble"))
            val id = events.add(EventEntity(bunnyId = bunnyId, label = "Check-up", occursOn = day))

            events.markNotified(id, Instant.parse("2026-03-14T09:00:00Z"))

            assertEquals(1, events.onDayNow(bunnyId, day).size)
            assertNotNull(checkNotNull(events.eventNow(id)).notifiedAt)
        }

    @Test
    fun theCalendarStampIsRecordedAndCanBeTakenBackOff() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Willow"))
            val id = events.add(EventEntity(bunnyId = bunnyId, label = "Dental", occursOn = day))

            events.markCalendarHandedOff(id, Instant.parse("2026-03-01T12:00:00Z"))
            assertNotNull(checkNotNull(events.eventNow(id)).calendarHandedOffAt)

            events.markCalendarHandedOff(id, null)
            assertNull(checkNotNull(events.eventNow(id)).calendarHandedOffAt)
        }

    @Test
    fun deletingABunnyTakesTheirEventsWithThem() =
        runTest {
            // The foreign key's cascade, asserted against the table rather than through the DAO: a
            // row left behind would have no bunny to belong to and would never be read again.
            val bunnyId = bunnies.add(BunnyEntity(name = "Marmalade"))
            events.add(EventEntity(bunnyId = bunnyId, label = "Came home", occursOn = day))
            assertEquals(1, database.countRows("events"))

            bunnies.delete(bunnyId)

            assertEquals(0, database.countRows("events"))
        }

    @Test
    fun theWatchedReadGoesNullOnceTheRowIsGone() =
        runTest {
            // How an event's own screen learns to close when the row is deleted behind it.
            val bunnyId = bunnies.add(BunnyEntity(name = "Juniper"))
            val id = events.add(EventEntity(bunnyId = bunnyId, label = "Nail trim", occursOn = day))
            assertNotNull(events.event(id).first())

            events.delete(id)

            assertNull(events.event(id).first())
            assertTrue(events.events(bunnyId).first().isEmpty())
        }
}
