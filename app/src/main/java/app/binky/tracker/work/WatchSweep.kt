package app.binky.tracker.work

import app.binky.tracker.data.WatchEntity
import app.binky.tracker.data.isActive
import app.binky.tracker.data.today
import app.binky.tracker.data.watchState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/*
 * **What the daily sweep decides about watches**, with nothing Android in it — `CareSweep.kt`'s
 * shape, for the same reason: the part in between reading rows and posting notifications is the part
 * that can be wrong in a way nobody notices for a week.
 *
 * The nag rides the **morning** sweep, and that is a medical choice rather than an architectural
 * one. A watch is running because something may be wrong, and for a rabbit that is most likely GI
 * stasis — a same-day emergency. A nag at 19:00 would surface "nobody has looked at Bijou today" at
 * the hour every vet is closed.
 */

/**
 * How recently an observation has to have been made for the nag to consider the question already
 * answered.
 *
 * **A rolling window, while [WatchEntity.lastNaggedOn] is a calendar day.** The two are deliberately
 * different shapes because they answer different questions — "have they looked recently" and "have I
 * already chased today" — and an owner who logged at 20:00 should not be chased at 09:00 the next
 * morning over a calendar-day boundary they had no reason to care about.
 */
val WATCH_SATISFIED_WITHIN: Duration = Duration.ofHours(24)

/** A watched bunny as the sweep sees them: the row, and the two facts that can silence it. */
data class WatchedBunny(
    val id: String,
    val name: String,
    val archived: Boolean,
    val watch: WatchEntity,
    /** When this bunny was last observed, or null if never. */
    val lastObservationAt: Instant?,
)

/** One bunny the owner should be asked about this morning. */
data class DueNag(
    val bunnyId: String,
    val bunnyName: String,
)

/**
 * Everyone the sweep should nag about right now.
 *
 * Four filters, each of which is a rule somewhere else in the app made checkable here:
 *
 * 1. **Archived bunnies are excluded in the derivation**, exactly as care reminders are — and
 *    belt-and-braces, since archiving deletes the row (ADR-0004). A rule enforced in one place is a
 *    rule that survives the day someone adds a second way to archive.
 * 2. **Only an active watch nags.** Past `endsAt` the nagging stops immediately, before any prompt
 *    is answered: expiry is what ends the chasing, and the prompt is only about re-arming.
 * 3. **[WatchEntity.lastNaggedOn] makes "once daily" true.** The sweep can run more than once a day
 *    — a retry, a reboot, a Doze window closing — so this is a recorded fact rather than an
 *    assumption about the scheduler.
 * 4. **Any observation inside [WATCH_SATISFIED_WITHIN] settles it.** The nag asks whether the owner
 *    has looked; if the record says they have, asking again is the wallpaper ADR-0001 rejects.
 */
fun watchesDueForNagging(
    bunnies: List<WatchedBunny>,
    now: Instant,
    zone: ZoneId,
): List<DueNag> {
    val today = today(now, zone)
    return bunnies
        .filterNot { it.archived }
        .filter { watchState(it.watch, now).isActive() }
        .filterNot { it.watch.lastNaggedOn == today }
        .filterNot { it.observedRecently(now) }
        .map { DueNag(bunnyId = it.id, bunnyName = it.name) }
}

private fun WatchedBunny.observedRecently(now: Instant): Boolean {
    val at = lastObservationAt ?: return false
    // Strictly *before* now as well: an observation dated in the future is not evidence anybody
    // looked, and the forms reject one anyway — this is the sweep declining to trust that.
    return !at.isAfter(now) && Duration.between(at, now) < WATCH_SATISFIED_WITHIN
}

/**
 * A notification id **derived from the bunny id**, and therefore stable across sweeps — the same
 * requirement, and the same reasoning, as [careNotificationId].
 *
 * The salt is what keeps the two id spaces apart. Without it a bunny and a care reminder whose ids
 * happened to hash alike would replace each other's notification, and the two are posted by the same
 * sweep on the same morning.
 */
fun watchNotificationId(bunnyId: String): Int {
    // `and Int.MAX_VALUE` clears the sign bit, which `abs` cannot do for Int.MIN_VALUE.
    val positive = (bunnyId.hashCode() xor WATCH_ID_SALT) and Int.MAX_VALUE
    return if (positive < RESERVED_NOTIFICATION_IDS) positive + RESERVED_NOTIFICATION_IDS else positive
}

/** Arbitrary, fixed forever: changing it would orphan every nag currently in a shade. */
private const val WATCH_ID_SALT = 0x5761_7463
