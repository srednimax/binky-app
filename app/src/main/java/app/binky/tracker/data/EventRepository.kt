package app.binky.tracker.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Reads and writes the dated labels an owner keeps (ADR-0031).
 *
 * The thinnest repository in the app, and that is the design rather than an omission. An event has
 * no schedule to derive, no second source to reconcile and no completion history — it is a label and
 * a day. Everything interesting about *showing* one is the timeline, which is pure and lives in
 * `ui/events/Timeline.kt`; everything interesting about *delivering* one is the sweep.
 *
 * **A date freely in the past or the future** is what distinguishes this from every other dated write
 * in the app. A weighing, an observation and a vet visit are all things that happened, so all three
 * refuse tomorrow; an event is as often an appointment as a keepsake, and refusing the future here
 * would throw away half of what was asked for.
 */
class EventRepository(
    private val database: BunnyDatabase,
) {
    private val eventDao = database.eventDao()

    /** One bunny's events, soonest first. The screen re-sorts; this is the stored order. */
    fun events(bunnyId: String): Flow<List<EventEntity>> = eventDao.forBunny(bunnyId)

    suspend fun eventsNow(bunnyId: String): List<EventEntity> = eventDao.forBunnyNow(bunnyId)

    /** The sweep's read: everything this bunny has on one day, notified or not. */
    suspend fun onDayNow(
        bunnyId: String,
        day: LocalDate,
    ): List<EventEntity> = eventDao.onDayNow(bunnyId, day)

    /**
     * One event, watched — the `Flow`/`Now` pair every repository here draws. Null once the row is
     * gone, which is how its own screen learns to close.
     */
    fun event(id: String): Flow<EventEntity?> = eventDao.byId(id)

    suspend fun eventNow(id: String): EventEntity? = eventDao.byIdNow(id)

    suspend fun add(event: EventEntity): String {
        eventDao.insert(event.validated())
        return event.id
    }

    suspend fun update(event: EventEntity) {
        eventDao.update(event.validated())
    }

    suspend fun delete(id: String) {
        eventDao.deleteById(id)
    }

    /**
     * Called by the sweep after posting.
     *
     * Takes the instant rather than reading the clock itself, so a test can pin it and so the value
     * stamped is the one the sweep decided with rather than one a few milliseconds later.
     */
    suspend fun markNotified(
        eventId: String,
        notifiedAt: Instant = Instant.now(),
    ) {
        eventDao.setNotifiedAt(eventId, notifiedAt)
    }

    /** Records that ADR-0014's hand-off happened, so the button stops offering it. */
    suspend fun markCalendarHandedOff(
        eventId: String,
        handedOffAt: Instant? = Instant.now(),
    ) {
        eventDao.setCalendarHandedOffAt(eventId, handedOffAt)
    }
}

/**
 * The invariants a row has to satisfy whichever screen wrote it.
 *
 * **The label is the whole record** (ADR-0031), so a blank one leaves nothing to render — there is no
 * type to fall back on the way a care reminder has one. Caught before the database rather than in the
 * composable that would have to shrug at it.
 *
 * A blank note becomes null for the reason every other note field in this app does: "" and "no note"
 * are the same fact, and storing both makes every reader test for two.
 */
private fun EventEntity.validated(): EventEntity {
    val trimmed = label.trim()
    require(trimmed.isNotEmpty()) { "An event needs a label" }
    return copy(label = trimmed, note = note?.trim()?.ifEmpty { null })
}
