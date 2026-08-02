package app.binky.tracker.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.R

/**
 * Posts the recurring export prompt — `CareNotifier`'s and `WatchNotifier`'s third sibling, and the
 * only one that is not about a bunny (ADR-0005, PLAN 4e).
 *
 * **One notification, no group, no per-anything id.** There is exactly one export reminder in the
 * app, so [EXPORT_NOTIFICATION_ID] is a constant out of the reserved block rather than a hash of
 * something — and a sweep that runs twice replaces its own prompt for free.
 *
 * The copy is a prompt about the owner's *export*, never a claim that their data is unsafe. Whether
 * anything is actually protecting it is a question the Backup screen's automatic-backup line answers
 * honestly, including the case where the answer is "nobody knows" (ADR-0005); a notification that
 * shouted "your bunnies' history is at risk" once a month would be inventing that answer, and
 * inventing it in the alarming direction.
 */
class ExportNotifier(
    private val context: Context,
) {
    fun post() {
        val resources = context.resources
        context.postReminderNotification(
            channel = ReminderChannel.Backup,
            id = EXPORT_NOTIFICATION_ID,
            title = resources.getString(R.string.export_notification_title),
            text = resources.getString(R.string.export_notification_text),
            // Straight to Backup & restore, where both the export button and the reminder's own
            // switch are: the prompt asks for one export and offers the way to stop asking.
            tap = ReminderTap.OpenBackup,
        )
    }

    /**
     * Drops the prompt once an export has been made.
     *
     * The same argument `CareNotifier.cancel` and `WatchNotifier.cancel` make: with one prompt a
     * month, a request still sitting in the shade after it has been answered is the only copy of
     * that staleness left anywhere.
     */
    fun cancel() {
        NotificationManagerCompat.from(context).cancel(EXPORT_NOTIFICATION_ID)
    }
}
