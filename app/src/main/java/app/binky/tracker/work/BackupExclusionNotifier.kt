package app.binky.tracker.work

import android.content.Context
import app.binky.tracker.R
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.backup.ExclusionNotice
import app.binky.tracker.data.backup.exclusionNotice
import app.binky.tracker.data.backup.readAutoBackupMarker
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Says, once, that Android's automatic backup could not carry every document (ADR-0005, PLAN 5h).
 *
 * **The agent writes and the app posts**, because the split is forced. `BinkyBackupAgent` runs in a
 * process with no `AppContainer` and its writes are `suspend` inside blocking backup callbacks, so
 * it has nowhere to record that a notice has already fired — and Auto Backup runs roughly daily,
 * which would turn "one-time" into "nightly". It leaves the count in its marker file instead, and
 * this reads it at process start.
 *
 * The cost is honest: the notice arrives when the app is next opened rather than at 3 a.m. That is
 * the right latency for a **standing condition** rather than an event — the documents are still not
 * in the backup tomorrow — and it is still the first thing the owner sees.
 *
 * **No new channel.** It posts on the existing `backup` channel, which 4e created at
 * `IMPORTANCE_DEFAULT` and 1.1 has shipped: an app cannot change an existing channel's importance,
 * only the owner can, so declaring anything lower now would give fresh 1.2 installs one behaviour
 * and upgraded ones another. Lower would also be the wrong instinct — `ReminderChannels.kt` already
 * refuses to make the mute decision on the owner's behalf in the one direction that cannot be
 * undone, and this is the notice that says data is *not* protected.
 */
class BackupExclusionNotifier(
    private val context: Context,
) {
    fun post(excludedDocuments: Int) {
        val resources = context.resources
        context.postReminderNotification(
            channel = ReminderChannel.Backup,
            id = BACKUP_EXCLUSION_NOTIFICATION_ID,
            title = resources.getString(R.string.backup_excluded_notification_title),
            text =
                resources.getQuantityString(
                    R.plurals.backup_excluded_notification_text,
                    excludedDocuments,
                    excludedDocuments,
                ),
            // Backup & restore, where the manual export that *does* carry documents is one tap away.
            // The notice names a gap the owner can close; sending them to the app's front page would
            // leave them to find the door themselves.
            tap = ReminderTap.OpenBackup,
        )
    }
}

/**
 * Decides and posts, at process start, from the marker the agent left behind.
 *
 * Reads the count off disk and the "said already" flag out of DataStore, hands both to the pure
 * [exclusionNotice], and does what it says. Suspending because both reads are — the caller is
 * `BinkyApplication`, after the wipe gate and on IO.
 */
suspend fun Context.postBackupExclusionNoticeIfDue(
    preferences: AppPreferences,
    filesDir: File = this.filesDir,
) {
    val excluded = readAutoBackupMarker(filesDir)?.excludedDocuments ?: 0
    // Kotlin note: `first()` takes one value from the Flow and stops collecting — the read-once
    // equivalent of awaiting a promise, where the screens instead subscribe for good.
    val notified = preferences.excludedDocumentsNotified.first()

    when (exclusionNotice(excluded, notified)) {
        ExclusionNotice.Post -> {
            // **A notice that cannot be posted has not been given**, and must not be marked as
            // given. `postReminderNotification` returns silently when notifications are off, so
            // without this check a blocked phone would burn its one-time notice on a notification
            // nobody ever saw — and an owner who granted the permission a day later would never
            // hear about it. [ReminderDelivery.Blocked] is exactly that case and no other: the
            // best-effort states still deliver something. The condition is standing, so leaving the
            // flag unset simply asks again next launch, and the Backup screen's status line says it
            // in words meanwhile (ADR-0005).
            if (reminderDelivery(ReminderChannel.Backup) == ReminderDelivery.Blocked) return

            BackupExclusionNotifier(this).post(excluded)
            // After the post, not before: a process killed between the two says it again next
            // launch, which is the harmless direction. The other order can lose the notice
            // entirely.
            preferences.setExcludedDocumentsNotified(true)
        }

        ExclusionNotice.Clear -> preferences.setExcludedDocumentsNotified(false)
        ExclusionNotice.Nothing -> Unit
    }
}
