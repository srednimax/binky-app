package app.binky.tracker.work

import app.binky.tracker.data.EventEntity
import java.time.LocalDate

/*
 * **What the daily sweep decides about events** (ADR-0024, ADR-0031), with nothing Android in it.
 *
 * The same split as `CareSweep.kt`, and for the same reason: the part that can be wrong in a way
 * nobody notices for a week is the derivation, not the posting. Keeping it pure makes "an archived
 * bunny is never notified about" and "an event announces itself once" case-table assertions.
 *
 * **The sweep, never an exact alarm.** ADR-0003 reserves the exact-alarm path for doses because a
 * late dose has consequences; an anniversary that arrives at 09:00 instead of midnight has none, and
 * spending the app's second delivery mechanism on one would be paying an OS-level cost for nothing.
 */

/** A bunny as the event sweep sees it: who they are, whether they are archived, and today's events. */
data class SweepEvents(
    val id: String,
    val name: String,
    val archived: Boolean,
    /** Everything dated today for this bunny — already notified or not; the filter is below. */
    val events: List<EventEntity>,
)

/** One event that has come round for a bunny who should hear about it. */
data class DueEvent(
    val bunnyId: String,
    val bunnyName: String,
    val event: EventEntity,
)

/**
 * Everything the sweep should post about today, across every bunny.
 *
 * **Archived bunnies are excluded here**, in the derivation rather than by the caller happening to
 * ask only about active ones — the same rule, in the same place, as [careDueForNotifying]. An
 * archived bunny has died or been rehomed, and a notification about the anniversary of their
 * adoption is exactly the failure ADR-0001 names for a trend flag on a memorial page.
 *
 * The second filter is [EventEntity.notifiedAt]: an event announces itself **once**, and the stamp is
 * what makes a second sweep on the same day silent. Unlike a care reminder there is no due date to
 * compare against, because an event has exactly one date in its life — so "was it announced" is the
 * whole question, and a null is the whole answer.
 */
fun eventsDueForNotifying(
    bunnies: List<SweepEvents>,
    today: LocalDate,
): List<DueEvent> =
    bunnies
        .filterNot { it.archived }
        .flatMap { bunny ->
            bunny.events
                .filter { it.occursOn == today && it.notifiedAt == null }
                .map { DueEvent(bunnyId = bunny.id, bunnyName = bunny.name, event = it) }
        }

/**
 * The event group's summary — one per sweep, not one per event.
 *
 * Inside [RESERVED_NOTIFICATION_IDS]'s block, which is what that block exists for: 1 is the debug
 * reminder, 2 care's summary, 3 the export prompt, 4 the debug dose, 5 the excluded-documents notice,
 * and this is 6.
 */
const val EVENT_SUMMARY_NOTIFICATION_ID = 6

/**
 * A notification id **derived from the event id**, and therefore stable across sweeps — the same
 * requirement, and the same reasoning, as [careNotificationId].
 *
 * The salt is what keeps this id space apart from care's and the watch's. Without it an event and a
 * care reminder whose ids happened to hash alike would replace each other's notification, and all
 * three are posted by the same sweep on the same morning.
 */
fun eventNotificationId(eventId: String): Int {
    // `and Int.MAX_VALUE` clears the sign bit, which `abs` cannot do for Int.MIN_VALUE.
    val positive = (eventId.hashCode() xor EVENT_ID_SALT) and Int.MAX_VALUE
    return if (positive < RESERVED_NOTIFICATION_IDS) positive + RESERVED_NOTIFICATION_IDS else positive
}

/** Arbitrary, fixed forever: changing it would orphan every event notice currently in a shade. */
private const val EVENT_ID_SALT = 0x4576_6E74
