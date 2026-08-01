package app.binky.tracker.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.R

/**
 * Posts and cancels watch nags — `CareNotifier`'s twin, and on its own channel for the reason
 * `ReminderChannel` gives: an owner who mutes a daily nag must not thereby mute an annual
 * vaccination.
 *
 * **No group and no summary**, unlike care. A nag is strictly one per bunny and two at once is
 * already the unusual case, but the real reason is that each one is individually actionable — it
 * opens *that* bunny's observation form — and a summary spanning two would collapse two different
 * destinations into one tap that cannot choose between them. Care's summary can honestly open the
 * app and let the owner pick; this one would be throwing away the only thing the nag is for.
 */
class WatchNotifier(
    private val context: Context,
) {
    /**
     * One nag per bunny, framed as a question about the owner's checking.
     *
     * The copy is **never a claim about the bunny** (ADR-0001). The app knows only that nothing has
     * been logged; "Bijou may be unwell" would be inventing a fact from silence, which is the one
     * inference this project refuses to make.
     */
    fun post(nags: List<DueNag>) {
        val resources = context.resources
        nags.forEach { nag ->
            context.postReminderNotification(
                channel = ReminderChannel.Watch,
                id = watchNotificationId(nag.bunnyId),
                title = resources.getString(R.string.watch_notification_title, nag.bunnyName),
                text = resources.getString(R.string.watch_notification_text),
                // Straight to the form, pre-filled for this bunny: the nag asks a question and this
                // is where the answer goes.
                tap = ReminderTap.LogObservation(nag.bunnyId),
            )
        }
    }

    /**
     * Drops the nag posted about this bunny.
     *
     * Called when an observation lands for them and when the watch is closed or extended — the same
     * argument `CareNotifier.cancel` makes: with one nag a day, a question still sitting in the
     * shade after it has been answered is the only copy of that staleness left anywhere. Tapping the
     * nag itself needs no help; the notification is built `setAutoCancel(true)`.
     */
    fun cancel(bunnyId: String) {
        NotificationManagerCompat.from(context).cancel(watchNotificationId(bunnyId))
    }
}
