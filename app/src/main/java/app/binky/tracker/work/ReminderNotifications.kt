package app.binky.tracker.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.MainActivity
import app.binky.tracker.R
import java.time.Duration

/**
 * The extra a care notification carries: **whose** reminder it is.
 *
 * A tap has to write the app-wide selection before navigating, because `CareAndMeds` takes no
 * arguments — selecting that bunny is the only way to show their reminders, and landing on a
 * different bunny's Care screen would be the app lying about whose data is on screen. `MainActivity`
 * reads this and hands it to the shell; see `MainNavigation`.
 */
const val EXTRA_CARE_BUNNY_ID = "app.binky.tracker.extra.CARE_BUNNY_ID"

/**
 * The extra a watch nag carries: whose observation form to open.
 *
 * A **separate** extra rather than a second meaning for the one above, because the two land in
 * different places — Care for a reminder, the observation form for a nag — and one extra carrying a
 * bunny id plus an unwritten convention about which screen wanted it is how the wrong screen
 * eventually gets it.
 */
const val EXTRA_WATCH_BUNNY_ID = "app.binky.tracker.extra.WATCH_BUNNY_ID"

/**
 * The extra an event notice carries: **whose** event it is (ADR-0031).
 *
 * A third per-bunny extra rather than a third meaning for one of the two above, for the reason the
 * watch nag's exists: three extras each landing somewhere different is checkable, and one extra plus
 * an unwritten convention about which screen wanted it is how the wrong screen eventually gets it.
 */
const val EXTRA_EVENT_BUNNY_ID = "app.binky.tracker.extra.EVENT_BUNNY_ID"

/**
 * The flag the export prompt carries. **No bunny**, and that is the point: a backup reminder hangs
 * off the app rather than off any animal (ADR-0005), so there is nothing to select and one
 * destination to open.
 */
const val EXTRA_OPEN_BACKUP = "app.binky.tracker.extra.OPEN_BACKUP"

/**
 * Where tapping a reminder notification lands.
 *
 * Kotlin note: a sealed interface, so the `when` that turns one into an `Intent` is exhaustive and a
 * fourth destination cannot be added without every reader being made to handle it.
 */
sealed interface ReminderTap {
    /** The app as it stands. Right for the debug reminder, and for a summary spanning two bunnies. */
    data object OpenApp : ReminderTap

    /** This bunny's Care screen. */
    data class Care(
        val bunnyId: String,
    ) : ReminderTap

    /**
     * The observation form, pre-filled for this bunny — where a watch nag goes, because the nag asks
     * whether the owner has looked and the form is the answer.
     */
    data class LogObservation(
        val bunnyId: String,
    ) : ReminderTap

    /**
     * This bunny's events, where an event notice lands — the timeline is what an owner opens after
     * being told something is on today, and the event they were told about is on it.
     */
    data class Event(
        val bunnyId: String,
    ) : ReminderTap

    /**
     * Backup & restore, where the export button and the reminder's own switch both are — the export
     * prompt's destination, and the only one carrying no bunny at all.
     */
    data object OpenBackup : ReminderTap

    /**
     * This course's own screen, with [bunnyId] selected first — where a dose reminder's *body* lands
     * when the owner wants the history rather than the one-tap answer (PLAN 5f).
     *
     * The only destination carrying **two** ids, because `MedicationCourse` is keyed by the course
     * alone (the bunny is not recoverable from it) and the app-wide selection has to be written
     * before navigating, exactly as a care tap does.
     */
    data class Medication(
        val bunnyId: String,
        val courseId: String,
    ) : ReminderTap
}

/**
 * One button on a notification: a label and what tapping it does.
 *
 * Only doses have any (PLAN 5f). A care reminder's answer is *doing the thing*, which no button can
 * record, and a watch nag's answer is a form — but a dose is two words and a row, so the shade can
 * take the whole answer.
 */
data class ReminderAction(
    val title: String,
    val intent: PendingIntent,
)

/**
 * Posts one notification on [channel], or does nothing if it cannot.
 *
 * **Never throws on a missing permission.** `NotificationManagerCompat.notify` needs
 * `POST_NOTIFICATIONS` on API 33+, and the caller here is a worker with no owner present and no
 * screen to report to — so the permission is checked and the post skipped. A reminder that could not
 * be delivered is not an error the owner can act on from inside a background sweep; what they act on
 * is the delivery-state line in the app, which is exactly what [ReminderDelivery] is for.
 *
 * @param id must be **stable and derived from what the notification is about** — see
 *   [careNotificationId] and [watchNotificationId]. A stable id means a sweep that runs twice
 *   replaces its own notification rather than stacking a second copy of the same sentence.
 * @param tap where tapping it lands.
 * @param group the bundling key. Three reminders due across two bunnies at 09:00 are one glance at
 *   the shade, not three unrelated notifications.
 * @param isGroupSummary whether this **is** that bundle's summary rather than a member of it.
 * @param timeoutAfter how long the notification should live before Android withdraws it. Null for
 *   everything but doses, which expire with their slot at local midnight (ADR-0025) — a shade that
 *   still offers a one-tap answer for a day the app has stopped deriving is offering to record
 *   something it would otherwise make the owner back-date deliberately.
 * @param alertOnce whether re-posting under the same [id] may sound and vibrate again. True for
 *   doses, which are re-posted by any rebuild inside the grace window with nothing having changed.
 * @param actions buttons, in order. Doses only — see [ReminderAction].
 */
fun Context.postReminderNotification(
    channel: ReminderChannel,
    id: Int,
    title: String,
    text: String,
    tap: ReminderTap = ReminderTap.OpenApp,
    group: String? = null,
    isGroupSummary: Boolean = false,
    timeoutAfter: Duration? = null,
    alertOnce: Boolean = false,
    actions: List<ReminderAction> = emptyList(),
) {
    ensureReminderChannel(channel)

    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return

    val notification =
        NotificationCompat
            .Builder(this, channel.id)
            // A silhouette, not the launcher icon: Android tints every non-transparent pixel of a
            // small icon and throws the rest away.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            // Long text wraps rather than being cut off mid-sentence in the shade.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(tap))
            .setAutoCancel(true)
            .apply {
                if (group != null) setGroup(group)
                if (isGroupSummary) setGroupSummary(true)
                // Non-positive would mean "withdraw immediately", so a slot already past its own
                // expiry posts nothing rather than flashing and vanishing.
                if (timeoutAfter != null) setTimeoutAfter(timeoutAfter.toMillis().coerceAtLeast(1))
                if (alertOnce) setOnlyAlertOnce(true)
                // Icon 0: Android has not drawn action icons in the shade since Nougat, and a
                // silhouette repeated beside both buttons would only be noise on the wearables that
                // still do.
                actions.forEach { addAction(0, it.title, it.intent) }
            }.build()

    try {
        manager.notify(id, notification)
    } catch (e: SecurityException) {
        // `areNotificationsEnabled` is the honest check and it passed, so this is the narrow race
        // where the permission is revoked between the two lines. Nothing to report and nobody to
        // report it to — the next sweep sees the real state.
    }
}

/**
 * The app, carrying whatever the notification wants it to do on arrival.
 *
 * The **request code varies with the tap**, and it has to. `FLAG_UPDATE_CURRENT` rewrites the extras
 * of any equal `PendingIntent`, and two intents differing only in their extras count as equal — so a
 * single request code would have Bijou's notification and Nugget's end up pointing at whichever was
 * built last, which is precisely the "lying about whose data is on screen" these extras exist to
 * prevent. The salt on the observation case is the same defence one level up: a care reminder and a
 * watch nag about the *same* bunny are two different destinations and must not collapse into one.
 */
private fun Context.openAppIntent(tap: ReminderTap): PendingIntent {
    val intent =
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val requestCode =
        when (tap) {
            ReminderTap.OpenApp -> 0
            is ReminderTap.Care -> {
                intent.putExtra(EXTRA_CARE_BUNNY_ID, tap.bunnyId)
                tap.bunnyId.hashCode()
            }
            is ReminderTap.LogObservation -> {
                intent.putExtra(EXTRA_WATCH_BUNNY_ID, tap.bunnyId)
                tap.bunnyId.hashCode() xor TAP_OBSERVATION_SALT
            }
            is ReminderTap.Event -> {
                intent.putExtra(EXTRA_EVENT_BUNNY_ID, tap.bunnyId)
                tap.bunnyId.hashCode() xor TAP_EVENT_SALT
            }
            ReminderTap.OpenBackup -> {
                intent.putExtra(EXTRA_OPEN_BACKUP, true)
                TAP_BACKUP_REQUEST
            }
            is ReminderTap.Medication -> {
                intent.putExtra(EXTRA_DOSE_BUNNY_ID, tap.bunnyId)
                intent.putExtra(EXTRA_DOSE_COURSE_ID, tap.courseId)
                // Keyed on the **course**, not the bunny: two courses on the same rabbit are two
                // destinations, and a bunny-keyed code would collapse them into whichever posted
                // last. Salted for the same reason the observation case is — a dose tap and a care
                // tap about one bunny must not become one pending intent.
                tap.courseId.hashCode() xor TAP_MEDICATION_SALT
            }
        }
    return PendingIntent.getActivity(
        this,
        requestCode,
        intent,
        // IMMUTABLE is required from API 31 and correct everywhere: nothing outside this app has
        // any business filling in fields on an intent we constructed.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Arbitrary and fixed: it only has to keep the two per-bunny request-code spaces apart. */
private const val TAP_OBSERVATION_SALT = 0x4F62_7376

/** The same, for the per-course space a dose tap lives in. */
private const val TAP_MEDICATION_SALT = 0x4D65_6473

/** And again for events, which are per bunny like care and the watch nag and must not collapse into either. */
private const val TAP_EVENT_SALT = 0x4576_5461

/**
 * The export prompt's request code — a constant, because there is one export reminder and it always
 * means the same thing. Distinctive rather than `1` so it cannot plausibly land on a bunny id's hash
 * and hand a care notification the backup screen.
 */
private const val TAP_BACKUP_REQUEST = 0x4261_636B
