package app.binky.tracker.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.MainActivity
import app.binky.tracker.R

/**
 * The extra a reminder notification carries: **whose** reminder it is.
 *
 * A tap has to write the app-wide selection before navigating, because `CareAndMeds` takes no
 * arguments — selecting that bunny is the only way to show their reminders, and landing on a
 * different bunny's Care screen would be the app lying about whose data is on screen. `MainActivity`
 * reads this and hands it to the shell; see `MainNavigation`.
 */
const val EXTRA_CARE_BUNNY_ID = "app.binky.tracker.extra.CARE_BUNNY_ID"

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
 *   [careNotificationId]. A stable id means a sweep that runs twice replaces its own notification
 *   rather than stacking a second copy of the same sentence.
 * @param bunnyId whose reminder this is, for the tap target. Null opens the app as it stands, which
 *   is right for the debug reminder and for a group summary that spans several bunnies.
 * @param group the bundling key. Three reminders due across two bunnies at 09:00 are one glance at
 *   the shade, not three unrelated notifications.
 * @param isGroupSummary whether this **is** that bundle's summary rather than a member of it.
 */
fun Context.postReminderNotification(
    channel: ReminderChannel,
    id: Int,
    title: String,
    text: String,
    bunnyId: String? = null,
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
            .setContentIntent(openAppIntent(bunnyId))
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
 * Where tapping lands: the app, carrying the bunny whose reminder this was.
 *
 * The **request code varies with the bunny**, and it has to. `FLAG_UPDATE_CURRENT` rewrites the
 * extras of any equal `PendingIntent`, and two intents differing only in their extras count as
 * equal — so a single request code would have Bijou's notification and Nugget's end up pointing at
 * whichever was built last, which is precisely the "lying about whose data is on screen" this extra
 * exists to prevent.
 */
private fun Context.openAppIntent(bunnyId: String?): PendingIntent =
    PendingIntent.getActivity(
        this,
        bunnyId?.hashCode() ?: 0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (bunnyId != null) putExtra(EXTRA_CARE_BUNNY_ID, bunnyId) },
        // IMMUTABLE is required from API 31 and correct everywhere: nothing outside this app has
        // any business filling in fields on an intent we constructed.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
