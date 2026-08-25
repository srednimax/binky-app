package app.binky.tracker.work

import app.binky.tracker.data.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What the daily sweep decides about events, as a case table (ADR-0024, ADR-0031).
 *
 * The same two rules `CareSweepTest` pins, because they fail the same way — silently, for as long as
 * nobody looks: a notice about an archived bunny, and an event that starts announcing itself every
 * morning because "once" stopped being true.
 */
class EventSweepTest {
    private val today = LocalDate.of(2026, 3, 14)

    @Test
    fun `an archived bunny is never notified about`() {
        // ADR-0001's memorial-page rule, arriving at the sweep. The date has come round and the
        // event is unannounced — and the bunny has died or been rehomed, so nothing is posted. The
        // anniversary of an adoption is precisely the notice that must not arrive.
        val bunnies =
            listOf(
                sweepBunny("bijou", archived = false, occursOn = today),
                sweepBunny("nugget", archived = true, occursOn = today),
            )

        val due = eventsDueForNotifying(bunnies, today)

        assertEquals(listOf("bijou"), due.map { it.bunnyId })
    }

    @Test
    fun `an event announces itself once and never again`() {
        val bunnies =
            listOf(
                SweepEvents(
                    "bijou",
                    "Bijou",
                    archived = false,
                    events = listOf(event(today).copy(notifiedAt = Instant.parse("2026-03-14T09:00:00Z"))),
                ),
            )

        assertTrue(eventsDueForNotifying(bunnies, today).isEmpty())
    }

    @Test
    fun `only today counts, in either direction`() {
        // Unlike a care reminder, an event has exactly one date in its life and no overdue state: a
        // sweep that missed yesterday's does **not** post it this morning. The timeline is what
        // carries a past event, and it carries it without shouting.
        val bunnies =
            listOf(
                SweepEvents(
                    "bijou",
                    "Bijou",
                    archived = false,
                    events = listOf(event(today.minusDays(1)), event(today.plusDays(1))),
                ),
            )

        assertTrue(eventsDueForNotifying(bunnies, today).isEmpty())
    }

    @Test
    fun `every event across every bunny comes back, carrying whose it is`() {
        // Three events across two bunnies is the case the group summary exists for, and the
        // notification's own text names the bunny — so the id has to travel with the event.
        val bunnies =
            listOf(
                SweepEvents(
                    "bijou",
                    "Bijou",
                    archived = false,
                    events = listOf(event(today), event(today)),
                ),
                sweepBunny("nugget", archived = false, occursOn = today),
            )

        val due = eventsDueForNotifying(bunnies, today)

        assertEquals(3, due.size)
        assertEquals(listOf("Bijou", "Bijou", "Nugget"), due.map { it.bunnyName })
    }

    @Test
    fun `a notification id is stable for an event and clear of the reserved block`() {
        // Stability is what makes a sweep that runs twice replace its own notification rather than
        // stack a second copy, and what lets a delete cancel exactly the right one.
        assertEquals(eventNotificationId("event-1"), eventNotificationId("event-1"))
        assertNotEquals(eventNotificationId("event-1"), eventNotificationId("event-2"))

        val ids = (1..2000).map { eventNotificationId("event-$it") }
        assertTrue(ids.all { it >= RESERVED_NOTIFICATION_IDS })
        assertTrue(EVENT_SUMMARY_NOTIFICATION_ID < RESERVED_NOTIFICATION_IDS)
    }

    @Test
    fun `an event and a care reminder sharing an id do not share a notification`() {
        // The salt's whole job. Both sweeps run on the same morning through the same notification
        // manager, so two ids that agreed would have one notice silently replace the other.
        val shared = "0f8c2b14-collision"
        assertNotEquals(eventNotificationId(shared), careNotificationId(shared))
    }

    private fun sweepBunny(
        id: String,
        archived: Boolean,
        occursOn: LocalDate,
    ) = SweepEvents(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        archived = archived,
        events = listOf(event(occursOn)),
    )

    private fun event(occursOn: LocalDate) =
        EventEntity(
            bunnyId = "bunny",
            label = "Neutering",
            occursOn = occursOn,
        )
}
