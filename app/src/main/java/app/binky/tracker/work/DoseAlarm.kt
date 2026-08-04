package app.binky.tracker.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant

/**
 * **How late a dose slot can still be answered** (ADR-0025).
 *
 * Thirty minutes, named, and shared by the two decisions that would otherwise each invent their own.
 * The obvious rule — fire what is due *now*, skip what is past — is wrong in the app's **default**
 * configuration rather than in a corner of it. Without `SCHEDULE_EXACT_ALARM`, denied by default
 * from Android 14, the alarm goes in via `setAndAllowWhileIdle` and the OS chooses the moment; in
 * Doze that is a window of minutes. An alarm placed for 08:00 and delivered at 08:04 would find its
 * own slot already in the past, post nothing, and re-arm for the next one — so the path built to
 * degrade honestly would deliver **no dose reminders at all**, looking exactly like the correct
 * quiet of nothing being armed.
 *
 * Thirty clears the Doze window comfortably and stays far short of the eleven-hours-late shade
 * answer that would be a lie about when the app knew. Note what cannot catch a wrong value here: the
 * debug two-minute action on a screen-on phone fires promptly, and so does every emulator.
 */
val DOSE_GRACE: Duration = Duration.ofMinutes(30)

/**
 * Whether [slot] is still worth doing anything about at [now] — **the one predicate**, used by both
 * firing and rescheduling.
 *
 * True for a slot in the future (nothing has aged), true for one up to [grace] in the past, false
 * beyond that. Rescheduling arms the earliest slot that is still true here, which is what lets a
 * rebuild at 08:04 after a force-stop still deliver the 08:00 dose; firing adds the one extra
 * condition below.
 *
 * Kotlin note: `Duration` implements `Comparable`, so `<=` is the operator form of `compareTo` —
 * there is no unit arithmetic hiding in it.
 */
fun doseSlotAnswerable(
    slot: Instant,
    now: Instant,
    grace: Duration = DOSE_GRACE,
): Boolean = Duration.between(slot, now) <= grace

/**
 * Whether [slot] should be **posted** at [now]: answerable, and actually reached.
 *
 * The second half is what stops an alarm delivered early — the OS is permitted to be imprecise in
 * both directions — from announcing a dose before its time.
 */
fun doseSlotDueNow(
    slot: Instant,
    now: Instant,
    grace: Duration = DOSE_GRACE,
): Boolean = !slot.isAfter(now) && doseSlotAnswerable(slot, now, grace)

/**
 * **One request code for the whole app** (ADR-0025), which is what makes "at most one pending dose
 * alarm exists, and none when no course is armed" a one-line check in `dumpsys alarm` rather than an
 * audit. Distinctive rather than `1` so it reads as itself in a dump.
 */
private const val DOSE_ALARM_REQUEST = 0x446F_7365

/**
 * The single pending dose alarm's target.
 *
 * Addressed by component and carrying no extras: everything it needs is re-derived when it runs, so
 * an alarm placed hours ago cannot deliver a stale idea of what was due. That is the same reason the
 * boot receiver reads no persisted schedule — the OS schedule was never the source of truth.
 */
class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        rebuildInBackground(context) { appContext ->
            // ADR-0007's guard first, exactly as the sweep does. A receiver woken by the OS over a
            // schema this build must not open does nothing at all — including not re-arming, which
            // is correct: the next launch goes through the consent screen and rebuilds after it.
            if (appContext.schemaWipePending()) return@rebuildInBackground

            val now = Instant.now()
            // Post, then mark, then rebuild — in that order, and the order is what stops a loop.
            // Rescheduling arms the earliest *answerable* slot, and a slot just posted is still
            // answerable for another half hour; marking it is what takes it out of the derivation
            // so the rebuild below looks forward instead of arming the same instant again.
            appContext.postDueDoses(now)
            appContext.rescheduleDoseAlarm(now)
        }
    }
}

/**
 * Recomputes the one pending dose alarm from truth, and arms it, moves it, or cancels it.
 *
 * **Idempotent by construction** (ADR-0025): same request code, `FLAG_UPDATE_CURRENT`, never
 * appended to. That is what lets the list of occasions to call it grow without bound — process
 * start, boot, a clock change, a timezone change, the exact-alarm grant, the daily care sweep, and
 * from 5d every write that could change the answer. Calling it twice costs one query and leaves the
 * same single alarm behind.
 *
 * @param now injectable so the arithmetic is testable; every caller passes the real clock.
 */
fun Context.rescheduleDoseAlarm(now: Instant = Instant.now()) {
    if (schemaWipePending()) return
    val alarms = getSystemService(AlarmManager::class.java) ?: return
    val pending = doseAlarmPendingIntent()

    val slot = nextAnswerableDoseSlot(now)
    if (slot == null) {
        // Nothing armed is a real state, not a failure — and it is also what a cancelled course, an
        // archived bunny and a finished treatment all look like. Cancelling rather than leaving a
        // stale alarm is what keeps the `dumpsys` invariant checkable.
        alarms.cancel(pending)
        return
    }

    // A slot already past but inside grace is passed through unchanged: AlarmManager fires a trigger
    // in the past immediately, which is precisely what a rebuild that found an undelivered dose
    // should do.
    val triggerAt = slot.toEpochMilli()
    if (canScheduleExactAlarms()) {
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            return
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call. Vanishingly narrow — a
            // revocation force-stops the app — but the fallback below is the honest answer to it,
            // and throwing here would take down a receiver instead.
        }
    }
    // **The degradation, and it is a mechanism rather than a promise.** `setAndAllowWhileIdle`
    // pierces Doze but inside a window the OS picks, so the dose is real and merely imprecise. This
    // is the path DOSE_GRACE exists for.
    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
}

private fun Context.doseAlarmPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        DOSE_ALARM_REQUEST,
        Intent(this, DoseAlarmReceiver::class.java),
        // UPDATE_CURRENT so there is one, ever. IMMUTABLE is required from API 31 and correct
        // everywhere: nothing outside this app has any business filling in fields on it.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
