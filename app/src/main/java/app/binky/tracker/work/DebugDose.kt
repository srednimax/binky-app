package app.binky.tracker.work

import android.content.Context
import androidx.core.content.edit
import app.binky.tracker.R
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/*
 * **The 5a stand-in for truth.**
 *
 * Phase 5a ships the exact-alarm path with no schema change at all — the same split 4a used, so a
 * failure in the alarm path and a failure in the migration cannot be confused for each other. That
 * leaves `rescheduleDoseAlarm` with nothing to derive from, and three receivers with nothing
 * observable to do.
 *
 * So the debug dose is stored, in one long in a `SharedPreferences` file of its own, and the two
 * functions below are the derivation the alarm path reads. 5f replaces **their bodies** with
 * `dueDoses` over the courses table and deletes this file; nothing above them changes, which is the
 * point of putting the seam here.
 *
 * A file of its own rather than a key in `AppPreferences`: that store is the owner's settings, it is
 * DataStore (so every read is a `Flow` and a `suspend`), and it survives ADR-0007's wipes on
 * purpose. None of those are properties a debug fixture should be borrowing.
 */

private const val DEBUG_DOSE_PREFS = "debug-dose"
private const val KEY_SLOT_MILLIS = "slot-millis"

/**
 * The debug dose's notification id — **4**, from the block `RESERVED_NOTIFICATION_IDS` holds open.
 *
 * Fixed, so arming twice replaces the pending notification rather than stacking two. 5f derives one
 * id per course from the same block, the way `careNotificationId` already does.
 */
private const val DEBUG_DOSE_NOTIFICATION_ID = 4

/** How long "in two minutes" is. Short enough to sit and wait for, long enough to lock the phone. */
private val DEBUG_DOSE_DELAY: Duration = Duration.ofMinutes(2)

/**
 * Where the overnight run's dose lands: the next **08:00**.
 *
 * An hour before the sweep's default rather than on top of it, and that is the whole reason for the
 * number. One night can carry two mechanisms only while their signatures cannot be confused — a dose
 * at 08:00 on the `doses` channel and a care sweep at 09:00 on `care` are two readings, where two
 * things at 09:00 would be one ambiguous one.
 */
private val DEBUG_DOSE_OVERNIGHT_AT: LocalTime = LocalTime.of(8, 0)

/**
 * The earliest dose slot still worth arming for, or null when nothing is.
 *
 * **5f replaces this body** with the `dueDoses` derivation over the courses table; the grace
 * predicate around it does not change. A stored slot that has aged past [DOSE_GRACE] answers null
 * here, so a rebuild after a long sleep cancels the alarm instead of arming one that would fire and
 * post nothing.
 */
internal fun Context.nextAnswerableDoseSlot(now: Instant): Instant? =
    debugDoseSlot()?.takeIf { doseSlotAnswerable(it, now) }

/**
 * Posts whatever is due at [now], then marks it — **in that order**, the same as the care sweep.
 *
 * A process killed between the two leaves the slot still needing posting, and the next rebuild posts
 * again, replacing its own notification because the id is fixed. The other order loses it for good.
 *
 * The mark is also what breaks the fire-then-rebuild loop: a slot that has just been posted is still
 * *answerable* for another half hour, so without taking it out of the derivation the rebuild would
 * arm the same instant again and fire immediately. **5f replaces this body** with one post per due
 * course and the same shape of mark.
 */
internal fun Context.postDueDoses(now: Instant) {
    val slot = debugDoseSlot() ?: return
    if (!doseSlotDueNow(slot, now)) return

    postReminderNotification(
        channel = ReminderChannel.Doses,
        id = DEBUG_DOSE_NOTIFICATION_ID,
        title = getString(R.string.debug_dose_notification_title),
        text = getString(R.string.debug_dose_notification_text),
        // ADR-0025: a dose notification expires with its slot, because ADR-0002 stops deriving a day
        // once it is over.
        timeoutAfter = Duration.between(now, endOfDay(now)),
    )
    clearDebugDose()
}

/**
 * Arms a debug dose two minutes out — **what makes this checkpoint provable with no medication in
 * existence**, and afterwards the fastest way to re-prove delivery.
 *
 * It goes in through [rescheduleDoseAlarm] like everything else rather than placing its own alarm:
 * the single-alarm bookkeeping is half of what 5a is here to prove, and a debug path that sidestepped
 * it would leave the three receivers with nothing to rebuild.
 */
fun Context.armDebugDose(now: Instant = Instant.now()): Instant = armDebugDoseAt(now.plus(DEBUG_DOSE_DELAY), now)

/**
 * Arms a debug dose at the next 08:00 — the **overnight Doze run** (PLAN 5a), which is the only way
 * to answer the question this checkpoint exists for: does `setExactAndAllowWhileIdle` fire *on time*
 * on HyperOS after twelve hours idle. Arm it in the evening, unplug the phone, and read `dumpsys`
 * before touching the shade.
 *
 * The two-minute action cannot answer it and neither can an emulator: both fire promptly on a
 * screen-on phone, which is the state Doze is defined by not being in.
 */
fun Context.armOvernightDebugDose(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Instant = armDebugDoseAt(nextOvernightDose(now, zone), now)

private fun Context.armDebugDoseAt(
    slot: Instant,
    now: Instant,
): Instant {
    getSharedPreferences(DEBUG_DOSE_PREFS, Context.MODE_PRIVATE)
        .edit { putLong(KEY_SLOT_MILLIS, slot.toEpochMilli()) }
    rescheduleDoseAlarm(now)
    return slot
}

private fun Context.debugDoseSlot(): Instant? {
    val millis = getSharedPreferences(DEBUG_DOSE_PREFS, Context.MODE_PRIVATE).getLong(KEY_SLOT_MILLIS, 0L)
    return if (millis == 0L) null else Instant.ofEpochMilli(millis)
}

private fun Context.clearDebugDose() {
    getSharedPreferences(DEBUG_DOSE_PREFS, Context.MODE_PRIVATE).edit { remove(KEY_SLOT_MILLIS) }
}

/**
 * The next [DEBUG_DOSE_OVERNIGHT_AT] in the device's current zone — today's if it is still ahead,
 * otherwise tomorrow's. Same strictly-after rule as `nextSweepAt`, and resolved fresh rather than
 * stored, so the slot means 08:00 where the phone is.
 */
private fun nextOvernightDose(
    now: Instant,
    zone: ZoneId,
): Instant {
    val today = now.atZone(zone).toLocalDate()
    val todays = today.atTime(DEBUG_DOSE_OVERNIGHT_AT).atZone(zone).toInstant()
    return if (todays > now) {
        todays
    } else {
        today
            .plusDays(1)
            .atTime(DEBUG_DOSE_OVERNIGHT_AT)
            .atZone(zone)
            .toInstant()
    }
}

/** Local midnight after [now] — where a dose notification's life ends (ADR-0025). */
private fun endOfDay(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant =
    now
        .atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
