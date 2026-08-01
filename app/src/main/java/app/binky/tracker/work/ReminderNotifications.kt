package app.binky.tracker.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.MainActivity
import app.binky.tracker.R

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
}

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
 */
fun Context.postReminderNotification(
    channel: ReminderChannel,
    id: Int,
    title: String,
    text: String,
    tap: ReminderTap = ReminderTap.OpenApp,
    group: String? = null,
    isGroupSummary: Boolean = false,
) {
    ensureReminderChannels()

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
