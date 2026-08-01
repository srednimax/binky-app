package app.binky.tracker.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.R
import app.binky.tracker.ui.bunny.joinNames
import app.binky.tracker.ui.care.careReminderLabel
import java.time.LocalDate

/**
 * The care channel's bundling key. **One group, not one per bunny**: the owner's question at 09:00
 * is "what needs doing today", and splitting that across two bundles because two rabbits live in the
 * same flat answers a question nobody asked.
 */
private const val CARE_NOTIFICATION_GROUP = "app.binky.tracker.care"

/**
 * Posts and cancels care notifications — the one place in the app that holds a `Context` for that
 * purpose.
 *
 * It exists as a class on [app.binky.tracker.AppContainer] rather than as `Context` extensions
 * because **both ends need it and only one of them has a `Context`**: the sweep posts from a worker,
 * and the Care screen's `ViewModel` cancels on completion. A `ViewModel` reaching for a `Context` is
 * how activity leaks start; reaching for a container it already holds is the shape every other
 * dependency in this app already takes.
 */
class CareNotifier(
    private val context: Context,
) {
    /**
     * One notification per reminder, bundled under a summary.
     *
     * The summary is posted **only for two or more**, and cancelled otherwise. Android renders a
     * one-child group as a bundle with a header the owner has to expand, which turns a single nail
     * trim into two taps and a heading that says less than the notification under it.
     */
    fun post(
        due: List<DueCare>,
        today: LocalDate,
    ) {
        if (due.isEmpty()) return
        val resources = context.resources

        due.forEach { item ->
            context.postReminderNotification(
                channel = ReminderChannel.Care,
                id = careNotificationId(item.scheduled.reminder.id),
                title = careReminderLabel(resources, item.scheduled.reminder),
                text =
                    if (item.scheduled.isOverdueOn(today)) {
                        resources.getString(R.string.care_notification_overdue, item.bunnyName)
                    } else {
                        resources.getString(R.string.care_notification_due, item.bunnyName)
                    },
                tap = ReminderTap.Care(item.bunnyId),
                group = CARE_NOTIFICATION_GROUP,
            )
        }

        if (due.size > 1) {
            context.postReminderNotification(
                channel = ReminderChannel.Care,
                id = CARE_SUMMARY_NOTIFICATION_ID,
                title = resources.getQuantityString(R.plurals.care_summary_title, due.size, due.size),
                // Which rabbits, once each — three reminders for one bunny is still one name.
                text = joinNames(resources, due.map { it.bunnyName }.distinct()),
                // No bunny: a summary spanning two of them cannot honestly select either, so it
                // opens the app as it stands and lets the owner choose.
                tap = ReminderTap.OpenApp,
                group = CARE_NOTIFICATION_GROUP,
                isGroupSummary = true,
            )
        } else {
            cancelSummary()
        }
    }

    /**
     * Drops the notification a reminder posted.
     *
     * **Completing is what calls this**, and it is not tidiness. With "notifies once and never
     * again", a notification still sitting in the shade for a task already done is the only copy of
     * that lie left anywhere — the Care screen has already moved on.
     */
    fun cancel(reminderId: String) {
        NotificationManagerCompat.from(context).cancel(careNotificationId(reminderId))
        // A summary outlives its last child, and an empty bundle in the shade is a reminder about
        // nothing. Cheaper to ask Android what is still posted than to track it ourselves.
        if (postedCareNotifications() == 0) cancelSummary()
    }

    private fun cancelSummary() {
        NotificationManagerCompat.from(context).cancel(CARE_SUMMARY_NOTIFICATION_ID)
    }

    /** How many care notifications are still in the shade, not counting the summary itself. */
    private fun postedCareNotifications(): Int =
        NotificationManagerCompat
            .from(context)
            .activeNotifications
            .count { it.notification.group == CARE_NOTIFICATION_GROUP && it.id != CARE_SUMMARY_NOTIFICATION_ID }
}
