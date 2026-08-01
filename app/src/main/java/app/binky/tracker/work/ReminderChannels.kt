package app.binky.tracker.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import app.binky.tracker.R

/**
 * The notification channels this app owns, and the **only** ones it ever will at 1.1.
 *
 * **Two, not one.** A channel is the owner's one per-kind control, and someone who mutes a daily
 * watch nag must not thereby mute an annual vaccination. **Two, not three.** Medication doses are
 * Phase 5's, and a channel with nothing behind it is a settings row describing a lie.
 *
 * **Both at `IMPORTANCE_DEFAULT`, with no sound, vibration or light overrides**, and that is chosen
 * once and permanently: Android lets the owner lower a channel and never lets the app raise one
 * again. Creating `watch` at `IMPORTANCE_LOW` would be making the mute decision on their behalf, in
 * the one direction that cannot be undone. `IMPORTANCE_HIGH` is spent nowhere in this phase, so
 * Phase 5 can escalate doses to it as a real signal rather than as the level everything already
 * sits at.
 *
 * Kotlin note: enum entries carrying constructor arguments make this a small lookup table rather
 * than the bare constants a JS enum gives you — the same shape as `TopLevelDestination`.
 *
 * @param id what Android stores the channel under. **Never change one**: a renamed id is a new
 *   channel, and the owner's mute goes back to unmuted without anyone touching it.
 * @param nameRes the channel's name in the phone's notification settings. `ReminderChannelsTest`
 *   holds the `channel_<id>_name` / `channel_<id>_description` convention these follow.
 */
enum class ReminderChannel(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    Care("care", R.string.channel_care_name, R.string.channel_care_description),
    Watch("watch", R.string.channel_watch_name, R.string.channel_watch_description),
}

/**
 * Creates both channels, or updates the names of two that already exist.
 *
 * **At first use, not at process start.** Called from the one place that posts a notification and
 * from the one that reports delivery state — an owner who has never opened anything to do with
 * reminders has no reason to find two channels waiting in their notification settings.
 *
 * Calling it again is not just harmless but load-bearing: `createNotificationChannel` on an existing
 * id updates the name and description and **cannot** raise the importance back up, so re-running it
 * is how a channel's name follows an in-app language change (ADR-0013) without ever undoing a mute.
 *
 * `minSdk` is 26, so there is no pre-channel branch to write — channels are the only way to post.
 */
fun Context.ensureReminderChannels() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    ReminderChannel.entries.forEach { channel ->
        manager.createNotificationChannel(
            NotificationChannel(
                channel.id,
                getString(channel.nameRes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(channel.descriptionRes) },
        )
    }
}

/**
 * What importance [channel] currently sits at, after the owner has had their say.
 *
 * Falls back to `IMPORTANCE_DEFAULT` for a channel that does not exist yet, which is what it will be
 * created at — reporting a not-yet-created channel as muted would put a **blocked** warning in front
 * of an owner who has done nothing wrong.
 */
fun Context.reminderChannelImportance(channel: ReminderChannel): Int {
    val manager = getSystemService(NotificationManager::class.java) ?: return NotificationManager.IMPORTANCE_DEFAULT
    return manager.getNotificationChannel(channel.id)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
}
