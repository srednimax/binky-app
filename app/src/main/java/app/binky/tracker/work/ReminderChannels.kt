package app.binky.tracker.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import app.binky.tracker.R

/**
 * The notification channels this app owns.
 *
 * **Four, and each is a separate mute.** A channel is the owner's one per-kind control, and every
 * pairing here fails the same test: someone who mutes a daily watch nag must not thereby mute an
 * annual vaccination, someone who has decided a monthly *"make a backup"* prompt is not for them
 * must not lose either, and **muting doses must not follow from any of the three** — which is the
 * whole reason there is a fourth rather than a medication row posted on `care`.
 *
 * **The importance is per entry**, and it has to be. 4a created every channel at a hardcoded
 * `IMPORTANCE_DEFAULT`, which was correct while the three things posting were nudges; a dose during
 * treatment is not a nudge, and it cannot be [NotificationManager.IMPORTANCE_HIGH] without the level
 * being a property of the channel instead of the loop. 4a spent HIGH nowhere on purpose, so this one
 * reads as a real signal rather than as the volume everything already sits at.
 *
 * The direction is what makes the choice permanent: Android lets the owner lower a channel and never
 * lets the app raise one again. Creating `watch` at `IMPORTANCE_LOW` would be making the mute
 * decision on their behalf, in the one direction that cannot be undone.
 *
 * Kotlin note: enum entries carrying constructor arguments make this a small lookup table rather
 * than the bare constants a JS enum gives you — the same shape as `TopLevelDestination`.
 *
 * @param id what Android stores the channel under. **Never change one**: a renamed id is a new
 *   channel, and the owner's mute goes back to unmuted without anyone touching it.
 * @param nameRes the channel's name in the phone's notification settings. `ReminderChannelsTest`
 *   holds the `channel_<id>_name` / `channel_<id>_description` convention these follow.
 * @param importance what the channel is *created* at, and therefore the ceiling the owner can lower
 *   from. Never read back for a decision — [reminderChannelImportance] is what the app acts on.
 */
enum class ReminderChannel(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val importance: Int,
) {
    Care(
        "care",
        R.string.channel_care_name,
        R.string.channel_care_description,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    Watch(
        "watch",
        R.string.channel_watch_name,
        R.string.channel_watch_description,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),

    /**
     * Dated events an owner wrote down (ADR-0031). Its own channel rather than care's, because the
     * two are different promises: care is a job the app is asking for, an event is a day the owner
     * asked to be reminded of, and Android's per-channel switch is the only place that distinction
     * can be acted on.
     */
    Event(
        "events",
        R.string.channel_events_name,
        R.string.channel_events_description,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),
    Backup(
        "backup",
        R.string.channel_backup_name,
        R.string.channel_backup_description,
        NotificationManager.IMPORTANCE_DEFAULT,
    ),

    /**
     * Medication doses (PLAN 5a). The only [NotificationManager.IMPORTANCE_HIGH] channel in the app,
     * because it is the only thing the app posts where being seen late has consequences — and the
     * only one where the app also refuses to say what to do about it (ADR-0026).
     */
    Doses(
        "doses",
        R.string.channel_doses_name,
        R.string.channel_doses_description,
        NotificationManager.IMPORTANCE_HIGH,
    ),
}

/**
 * Creates [channel] if it is not there, or updates the name and description of one that is.
 *
 * **One channel, at its own first use** — not all four at process start, and not all four at any one
 * channel's first use either. The rule this file has always stated is *"a channel exists when
 * something is posting on it"*, and 4a's loop quietly broke it the moment there were channels an
 * owner might never reach: adding `doses` to a `forEach` would put a medication row in the
 * notification settings of every owner who has never opened a medication screen.
 *
 * Calling it again is not just harmless but load-bearing: `createNotificationChannel` on an existing
 * id updates the name and description and **cannot** raise the importance back up, so re-running it
 * is how a channel's name follows an in-app language change (ADR-0013) without ever undoing a mute.
 *
 * `minSdk` is 26, so there is no pre-channel branch to write — channels are the only way to post.
 */
fun Context.ensureReminderChannel(channel: ReminderChannel) {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            channel.id,
            getString(channel.nameRes),
            channel.importance,
        ).apply { description = getString(channel.descriptionRes) },
    )
}

/**
 * What importance [channel] currently sits at, after the owner has had their say.
 *
 * Falls back to the channel's **own** creation importance for one that does not exist yet, which is
 * what it will be created at — reporting a not-yet-created channel as muted would put a **blocked**
 * warning in front of an owner who has done nothing wrong.
 */
fun Context.reminderChannelImportance(channel: ReminderChannel): Int {
    val manager = getSystemService(NotificationManager::class.java) ?: return channel.importance
    return manager.getNotificationChannel(channel.id)?.importance ?: channel.importance
}
