package app.binky.tracker.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.R
import app.binky.tracker.ui.bunny.joinNames

/**
 * The event channel's bundling key. **One group, not one per bunny**, for [CareNotifier]'s reason:
 * the owner's question at 09:00 is "what is on today", and splitting that across two bundles because
 * two rabbits live in the same flat answers a question nobody asked.
 */
private const val EVENT_NOTIFICATION_GROUP = "app.binky.tracker.events"

/**
 * Posts and cancels event notifications (ADR-0031) — the same shape as [CareNotifier], and it exists
 * as a class on [app.binky.tracker.AppContainer] for the same reason: both ends need it and only one
 * of them has a `Context`. The sweep posts from a worker; the Events screen's `ViewModel` cancels
 * when the event it announced is deleted.
 */
class EventNotifier(
    private val context: Context,
) {
    /**
     * One notification per event, bundled under a summary.
     *
     * The summary is posted **only for two or more**, and cancelled otherwise — Android renders a
     * one-child group as a bundle the owner has to expand, which turns a single anniversary into two
     * taps and a heading that says less than the notification under it.
     *
     * **The label is the title and the bunny is the body**, which is care's arrangement rather than
     * the other way round: the shade should read as the thing that is on today, not as the app.
     */
    fun post(due: List<DueEvent>) {
        if (due.isEmpty()) return
        val resources = context.resources

        due.forEach { item ->
            context.postReminderNotification(
                channel = ReminderChannel.Event,
                id = eventNotificationId(item.event.id),
                title = item.event.label,
                text = resources.getString(R.string.event_notification_today, item.bunnyName),
                tap = ReminderTap.Event(item.bunnyId),
                group = EVENT_NOTIFICATION_GROUP,
            )
        }

        if (due.size > 1) {
            context.postReminderNotification(
                channel = ReminderChannel.Event,
                id = EVENT_SUMMARY_NOTIFICATION_ID,
                title = resources.getQuantityString(R.plurals.event_summary_title, due.size, due.size),
                // Which rabbits, once each — two events for one bunny is still one name.
                text = joinNames(resources, due.map { it.bunnyName }.distinct()),
                // No bunny: a summary spanning two of them cannot honestly select either, so it
                // opens the app as it stands and lets the owner choose.
                tap = ReminderTap.OpenApp,
                group = EVENT_NOTIFICATION_GROUP,
                isGroupSummary = true,
            )
        } else {
            cancelSummary()
        }
    }

    /**
     * Drops the notification an event posted.
     *
     * **Deleting the event is what calls this.** With the stamp making it announce once and never
     * again, a notification still sitting in the shade for a row that no longer exists is the only
     * copy of that lie left anywhere.
     */
    fun cancel(eventId: String) {
        NotificationManagerCompat.from(context).cancel(eventNotificationId(eventId))
        // A summary outlives its last child, and an empty bundle in the shade is a notice about
        // nothing. Cheaper to ask Android what is still posted than to track it ourselves.
        if (postedEventNotifications() == 0) cancelSummary()
    }

    private fun cancelSummary() {
        NotificationManagerCompat.from(context).cancel(EVENT_SUMMARY_NOTIFICATION_ID)
    }

    /** How many event notifications are still in the shade, not counting the summary itself. */
    private fun postedEventNotifications(): Int =
        NotificationManagerCompat
            .from(context)
            .activeNotifications
            .count { it.notification.group == EVENT_NOTIFICATION_GROUP && it.id != EVENT_SUMMARY_NOTIFICATION_ID }
}
