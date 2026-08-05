package app.binky.tracker.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.MedicationRepository
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
            // **Post, then rebuild past what was posted** — and the second half is what stops a loop
            // rather than an optimisation. A dose notification does not answer its own slot (only
            // the owner does), so the slot stays in the derivation and is still *answerable* for
            // another half hour; a plain rebuild here would arm the same instant again, fire
            // immediately, and go round. Handing the rebuild the latest slot just posted is what
            // makes it look forward — see [rescheduleDoseAlarm]'s `postedThrough`.
            val postedThrough = appContext.postDueDoses(now)
            appContext.rescheduleDoseAlarm(now = now, postedThrough = postedThrough)
        }
    }
}

/**
 * The medication repository, or null if this process must not touch the database.
 *
 * **ADR-0007's guard is the first half of it**, which is why every entry point into the alarm path
 * goes through here rather than reaching for the container directly: forcing `AppContainer` over a
 * stale schema destroys the database in the background on a phone nobody is looking at.
 *
 * The cast is safe-by-default rather than checked: a `Context` that is not this app's `Application`
 * has no container to offer, and the honest answer there is "no alarm" rather than a crash inside a
 * broadcast.
 */
internal fun Context.doseMedications(): MedicationRepository? {
    if (schemaWipePending()) return null
    return (applicationContext as? BinkyApplication)?.container?.medicationRepository
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
 * @param postedThrough slots at or before this instant are ignored — see the overload below.
 */
suspend fun Context.rescheduleDoseAlarm(
    now: Instant = Instant.now(),
    postedThrough: Instant? = null,
) {
    val medications = doseMedications() ?: return
    rescheduleDoseAlarm(medications, now, postedThrough)
}

/**
 * The same rebuild against an explicit repository — what an instrumented test drives, and what
 * [AppContainer][app.binky.tracker.AppContainer] hands its own writes.
 *
 * @param postedThrough the latest slot a firing has just posted, or null everywhere else. Slots at
 *   or before it are skipped, because a posted slot is still unanswered and still answerable for
 *   another half hour — arming it again would fire immediately and repeat forever. Everything else
 *   passes null and gets the plain "earliest answerable slot", which is what lets a rebuild after a
 *   force-stop deliver a dose the phone slept through the alarm for.
 */
suspend fun Context.rescheduleDoseAlarm(
    medications: MedicationRepository,
    now: Instant = Instant.now(),
    postedThrough: Instant? = null,
) {
    if (schemaWipePending()) return
    val alarms = getSystemService(AlarmManager::class.java) ?: return

    val slot = medications.nextAnswerableDoseSlot(now, postedThrough)
    if (slot == null) {
        // Nothing armed is a real state, not a failure — and it is also what a cancelled course, an
        // archived bunny and a finished treatment all look like. Cancelling rather than leaving a
        // stale alarm is what keeps the `dumpsys` invariant checkable.
        //
        // Asked with `FLAG_NO_CREATE`, so a rebuild that finds nothing to arm does not mint a
        // `PendingIntent` purely in order to cancel an alarm with it — and the object is cancelled
        // too, which is what makes "none when no course is armed" true of the app's state and not
        // only of the alarm list.
        existingDoseAlarmPendingIntent()?.let { pending ->
            alarms.cancel(pending)
            pending.cancel()
        }
        return
    }
    val pending = doseAlarmPendingIntent()

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

/**
 * The earliest dose slot still worth arming for, or null when nothing is.
 *
 * "Earliest unanswered slot at or after now" is ADR-0025's sentence, and this is all of it: the
 * repository returns unanswered armed slots in chronological order, so the answer is the first one
 * the grace predicate accepts. A stale slot — the phone was off for hours — fails that predicate, so
 * a rebuild after a long sleep cancels the alarm rather than arming one that would fire and post
 * nothing.
 *
 * Kotlin note: an extension on the repository rather than a method on it, because the grace window
 * is the alarm path's rule and not the data layer's — `MedicationRepository` should not have to know
 * what an alarm is.
 */
private suspend fun MedicationRepository.nextAnswerableDoseSlot(
    now: Instant,
    postedThrough: Instant?,
): Instant? =
    armedDosesNow()
        .firstOrNull { armed ->
            doseSlotAnswerable(armed.due.at, now) &&
                (postedThrough == null || armed.due.at.isAfter(postedThrough))
        }?.due
        ?.at

/**
 * Whether the one pending dose alarm exists at this moment.
 *
 * The in-process form of the `dumpsys alarm` line ADR-0025 makes the gate's invariant — *at most one
 * pending dose alarm exists, and none when no course is armed*. "At most one" needs no assertion
 * anywhere: there is a single request code, so a second one is not expressible.
 *
 * `FLAG_NO_CREATE` is what makes this a question rather than an answer — it returns the existing
 * `PendingIntent` or null, and never brings one into being.
 */
internal fun Context.hasPendingDoseAlarm(): Boolean = existingDoseAlarmPendingIntent() != null

/** The pending dose alarm's intent if one exists, and never a newly created one. */
private fun Context.existingDoseAlarmPendingIntent(): PendingIntent? =
    PendingIntent.getBroadcast(
        this,
        DOSE_ALARM_REQUEST,
        Intent(this, DoseAlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

private fun Context.doseAlarmPendingIntent(): PendingIntent =
    PendingIntent.getBroadcast(
        this,
        DOSE_ALARM_REQUEST,
        Intent(this, DoseAlarmReceiver::class.java),
        // UPDATE_CURRENT so there is one, ever. IMMUTABLE is required from API 31 and correct
        // everywhere: nothing outside this app has any business filling in fields on it.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
