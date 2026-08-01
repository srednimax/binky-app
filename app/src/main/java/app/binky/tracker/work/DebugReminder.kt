package app.binky.tracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.binky.tracker.R
import java.util.concurrent.TimeUnit

/** How long "in two minutes" is. Short enough to sit and wait for, long enough to lock the phone. */
private const val DEBUG_REMINDER_MINUTES = 2L

/**
 * Its own unique name, deliberately separate from [SWEEP_WORK_NAME] — this is the one-shot path, and
 * it must not disturb the sweep whose "exactly one enqueued item" invariant is the thing being
 * proven around it.
 */
private const val DEBUG_REMINDER_WORK_NAME = "debug-reminder"

/** Fixed, so tapping the action twice replaces the pending notification instead of stacking two. */
private const val DEBUG_REMINDER_NOTIFICATION_ID = 1

/**
 * **The debug build's "remind me in two minutes"** — what makes 4a provable with no reminders in
 * existence.
 *
 * On its own one-shot path rather than through the sweep (ADR-0024), because a *daily* sweep cannot
 * demonstrate a channel, a permission and a delivery in the time it takes to look at a phone. It
 * stays after this checkpoint as the fastest way to re-prove delivery after any change to it, which
 * is why it lives in `main/` behind `BuildConfig.DEBUG` at the call site, the same way the
 * sample-data fixture does.
 */
class DebugReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        applicationContext.postReminderNotification(
            channel = ReminderChannel.Care,
            id = DEBUG_REMINDER_NOTIFICATION_ID,
            title = applicationContext.getString(R.string.debug_reminder_notification_title),
            text = applicationContext.getString(R.string.debug_reminder_notification_text),
        )
        return Result.success()
    }
}

/** Arms the two-minute test notification, replacing any previous one still pending. */
fun scheduleDebugReminder(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        DEBUG_REMINDER_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<DebugReminderWorker>()
            .setInitialDelay(DEBUG_REMINDER_MINUTES, TimeUnit.MINUTES)
            .build(),
    )
}
