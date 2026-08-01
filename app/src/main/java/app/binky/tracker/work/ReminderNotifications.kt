package app.binky.tracker.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.MainActivity
import app.binky.tracker.R

/**
 * Posts one notification on [channel], or does nothing if it cannot.
 *
 * **Never throws on a missing permission.** `NotificationManagerCompat.notify` needs
 * `POST_NOTIFICATIONS` on API 33+, and the caller here is a worker with no owner present and no
 * screen to report to — so the permission is checked and the post skipped. A reminder that could not
 * be delivered is not an error the owner can act on from inside a background sweep; what they act on
 * is the delivery-state line in the app, which is exactly what [ReminderDelivery] is for.
 *
 * @param id must be **stable and derived from what the notification is about** (4c derives care ids
 *   from the reminder id). A stable id means a sweep that runs twice replaces its own notification
 *   rather than stacking a second copy of the same sentence.
 */
fun Context.postReminderNotification(
    channel: ReminderChannel,
    id: Int,
    title: String,
    text: String,
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
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    try {
        manager.notify(id, notification)
    } catch (e: SecurityException) {
        // `areNotificationsEnabled` is the honest check and it passed, so this is the narrow race
        // where the permission is revoked between the two lines. Nothing to report and nobody to
        // report it to — the next sweep sees the real state.
    }
}

/**
 * Where tapping the notification lands, until 4c gives care notifications a real target.
 *
 * 4c decides that properly — a tap has to write the app-wide selection through `AppContainer.select`
 * before handing `NavDisplay` a back stack, because `CareAndMeds` takes no arguments and landing on
 * a different bunny's Care screen would be the app lying about whose data is on screen. Opening the
 * app is the honest placeholder while nothing has a bunny behind it.
 */
private fun Context.openAppIntent(): PendingIntent =
    PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        // IMMUTABLE is required from API 31 and correct everywhere: nothing outside this app has
        // any business filling in fields on an intent we constructed.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
