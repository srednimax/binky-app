package app.binky.tracker.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.binky.tracker.R
import app.binky.tracker.data.ArmedDose
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.MedicationRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/*
 * **What a dose reminder is, once one is due** (ADR-0025, ADR-0026, PLAN 5f).
 *
 * `Context` extensions rather than a notifier class like `CareNotifier`, and the difference is that
 * only one end needs this. Care is posted by the sweep and *cancelled* by a `ViewModel`, which is why
 * it lives on `AppContainer` where a screen can reach it without holding a `Context`; a dose is
 * posted by the alarm receiver and cancelled by the action receiver, and both of those have one.
 */

/** Which course an action or a tap is about. Carried by both intents, and meaning the same thing. */
const val EXTRA_DOSE_COURSE_ID = "app.binky.tracker.extra.DOSE_COURSE_ID"

/**
 * Whose dose a **tap** is about.
 *
 * A separate extra from the course id, for the reason `EXTRA_WATCH_BUNNY_ID` is separate from
 * `EXTRA_CARE_BUNNY_ID`: `MedicationCourse` is keyed by the course alone, so the bunny is not
 * recoverable from the destination, and landing on the right course under the wrong selected bunny
 * would be the app lying about whose data is on screen (ADR-0015).
 */
const val EXTRA_DOSE_BUNNY_ID = "app.binky.tracker.extra.DOSE_BUNNY_ID"

/** The slot's local day, as an epoch day — the first half of the key an answer is written against. */
private const val EXTRA_DOSE_SCHEDULED_ON = "app.binky.tracker.extra.DOSE_SCHEDULED_ON"

/** The slot's local time, as a second of day. The other half of the key (ADR-0002). */
private const val EXTRA_DOSE_SCHEDULED_TIME = "app.binky.tracker.extra.DOSE_SCHEDULED_TIME"

/**
 * The two shade answers, as intent actions.
 *
 * An action rather than a boolean extra, because `PendingIntent` equality ignores extras entirely:
 * two intents differing only in "given or skipped" would be the *same* pending intent, and
 * `FLAG_UPDATE_CURRENT` would quietly make both buttons do whichever was built last.
 */
private const val ACTION_DOSE_GIVEN = "app.binky.tracker.action.DOSE_GIVEN"
private const val ACTION_DOSE_SKIPPED = "app.binky.tracker.action.DOSE_SKIPPED"

/**
 * Posts whatever is due at [now], and answers **the latest slot it posted** — which is what the
 * caller rebuilds past (see `DoseAlarmReceiver`).
 *
 * **One notification per due course, and no group.** Care bundles because "what needs doing today"
 * is one glance; a dose notification carries the two buttons that are the entire point of it, and
 * Android renders a bundle's children collapsed until the owner expands them. Bundling would put an
 * expand between the owner and the one tap the feature exists for.
 *
 * A course with two slots inside the same grace window — an 08:00 and an 08:15 delivered together —
 * posts the **earlier** one only, and the later one is picked up by the next rebuild. One row per
 * course means one pair of buttons per course, and buttons that answered an ambiguous "the dose"
 * would be worse than a second notification a few seconds later.
 *
 * @return the latest slot instant actually posted, or null when nothing was.
 */
internal suspend fun Context.postDueDoses(
    medications: MedicationRepository,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant? {
    val posted =
        medications
            .armedDosesNow(zone = zone)
            .filter { doseSlotDueNow(it.due.at, now) }
            // Chronological already, so the first per course is the earliest.
            .distinctBy { it.course.id }
    if (posted.isEmpty()) return null

    posted.forEach { armed -> postDoseNotification(armed, now, zone) }
    return posted.maxOf { it.due.at }
}

/** The same, resolving the repository through the container. Null when the schema guard says stop. */
internal suspend fun Context.postDueDoses(now: Instant): Instant? {
    val medications = doseMedications() ?: return null
    return postDueDoses(medications, now)
}

/**
 * One dose, in the shade: the medicine, the amount and the bunny, with **Given** and **Skipped** as
 * one tap each.
 *
 * **It says what is due and nothing else** (ADR-0026, ADR-0001). No "missed", no "overdue", no
 * suggestion — a notification is the app reporting a time the owner set, not the app forming a view
 * about the animal.
 *
 * The two action labels are the screen's own, deliberately: the same tap answered from the shade and
 * from the course must not be called two different things.
 */
private fun Context.postDoseNotification(
    armed: ArmedDose,
    now: Instant,
    zone: ZoneId,
) {
    val amount = armed.course.doseAmount
    postReminderNotification(
        channel = ReminderChannel.Doses,
        id = doseNotificationId(armed.course.id),
        title = armed.course.name,
        text =
            if (amount.isEmpty()) {
                getString(R.string.dose_notification_no_amount, armed.bunnyName)
            } else {
                getString(R.string.dose_notification_amount, amount, armed.bunnyName)
            },
        tap = ReminderTap.Medication(bunnyId = armed.course.bunnyId, courseId = armed.course.id),
        // **The notification expires with its slot** (ADR-0025): ADR-0002 stops deriving a day once
        // it is over, and a shade still offering a one-tap *Given* at 19:00 for the 08:00 dose is the
        // same eleven-hours-late answer the grace window refuses, arriving by a different route.
        // Measured to the slot's own midnight rather than to `now`'s — the same day in every case
        // that can reach here, since a posted slot is by definition today's, but the slot is what the
        // rule is about.
        timeoutAfter =
            Duration.between(
                now,
                armed.due.scheduledOn
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant(),
            ),
        // A re-post of a dose already in the shade must not buzz again. It is reachable without
        // anything being wrong: any rebuild inside the grace window — a write on another course, the
        // 09:00 sweep, opening the app — can arm a slot that was already posted, which fires
        // immediately and replaces this notification with itself.
        alertOnce = true,
        actions =
            listOf(
                ReminderAction(
                    title = getString(R.string.dose_status_given),
                    intent = doseActionIntent(armed, DoseStatus.GIVEN),
                ),
                ReminderAction(
                    title = getString(R.string.dose_status_skipped),
                    intent = doseActionIntent(armed, DoseStatus.SKIPPED),
                ),
            ),
    )
}

/**
 * A notification id **derived from the course id**, and therefore stable across firings.
 *
 * Same shape and same reasoning as [careNotificationId]: a firing that repeats — a rebuild inside the
 * grace window, a process death between posting and re-arming — must replace its own notification
 * rather than stack a second copy, and it must be the id the action receiver cancels.
 */
fun doseNotificationId(courseId: String): Int {
    val positive = courseId.hashCode() and Int.MAX_VALUE
    return if (positive < RESERVED_NOTIFICATION_IDS) positive + RESERVED_NOTIFICATION_IDS else positive
}

/**
 * The pending intent behind one shade button.
 *
 * The slot travels as its **local key** — an epoch day and a second of day — never as an instant
 * (ADR-0002). A notification posted in Warsaw and answered after landing in London must write the
 * 08:00 slot it named, not the instant that used to be 08:00.
 *
 * The request code varies with the course *and* the answer for the reason [ACTION_DOSE_GIVEN]
 * explains: extras do not distinguish pending intents, so without it Bijou's *Given* and Nugget's
 * would be one object.
 */
private fun Context.doseActionIntent(
    armed: ArmedDose,
    status: DoseStatus,
): PendingIntent {
    val intent =
        Intent(this, DoseActionReceiver::class.java)
            .setAction(if (status == DoseStatus.GIVEN) ACTION_DOSE_GIVEN else ACTION_DOSE_SKIPPED)
            .putExtra(EXTRA_DOSE_COURSE_ID, armed.course.id)
            .putExtra(EXTRA_DOSE_SCHEDULED_ON, armed.due.scheduledOn.toEpochDay())
            .putExtra(EXTRA_DOSE_SCHEDULED_TIME, armed.due.scheduledTime.toSecondOfDay())
    return PendingIntent.getBroadcast(
        this,
        doseNotificationId(armed.course.id) xor
            if (status == DoseStatus.GIVEN) ACTION_GIVEN_SALT else ACTION_SKIPPED_SALT,
        intent,
        // IMMUTABLE: the system delivers this untouched, and nothing outside this app has any
        // business filling in which dose it answers.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Arbitrary and fixed; they only have to keep the two per-course request-code spaces apart. */
private const val ACTION_GIVEN_SALT = 0x4769_7665
private const val ACTION_SKIPPED_SALT = 0x536B_6970

/**
 * Reads the slot out of an action intent, or null if it is not one of ours.
 *
 * Every field is checked rather than defaulted: a broadcast reaching here without them is not a dose
 * this app posted, and inventing a day for it would write an answer against a slot nobody named.
 */
internal fun Intent.doseAction(): DoseAction? {
    val status =
        when (action) {
            ACTION_DOSE_GIVEN -> DoseStatus.GIVEN
            ACTION_DOSE_SKIPPED -> DoseStatus.SKIPPED
            else -> return null
        }
    val courseId = getStringExtra(EXTRA_DOSE_COURSE_ID) ?: return null
    val day = getLongExtra(EXTRA_DOSE_SCHEDULED_ON, Long.MIN_VALUE)
    val second = getIntExtra(EXTRA_DOSE_SCHEDULED_TIME, -1)
    if (day == Long.MIN_VALUE || second < 0) return null
    return DoseAction(courseId = courseId, epochDay = day, secondOfDay = second, status = status)
}

/** One shade answer, as read back off its intent. */
internal data class DoseAction(
    val courseId: String,
    val epochDay: Long,
    val secondOfDay: Int,
    val status: DoseStatus,
)
