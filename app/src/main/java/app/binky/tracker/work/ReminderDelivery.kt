package app.binky.tracker.work

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Whether a reminder will actually reach the owner — **three honest states, not two**.
 *
 * ADR-0003 was written with two: an armed alarm, or a best-effort hedge while the phone's background
 * limits are unconfirmed. That covers the *soft* failure, where the app has done everything right
 * and the OS may still drop the work. It does not cover the **certain** one: a denied notification
 * permission or a muted channel means nothing will arrive, the app can detect both without asking
 * anyone, and hedging about it would be the app pretending not to know.
 *
 * Built here, one phase before doses make it critical, so Phase 5 inherits the framing rather than
 * covering one case in three.
 */
enum class ReminderDelivery {
    /**
     * Nothing will be posted, and the app is certain of it. Its copy says so plainly and offers the
     * way to fix it; it does **not** stop reminders being created, because the Care screen carries
     * overdue state on its own and a reminder is still worth having. It just must not claim it will
     * notify.
     */
    Blocked,

    /**
     * It will be posted, and may arrive late or not at all. The exemption is unconfirmed, which on
     * an aggressive skin is the difference between a reminder and a wish (ADR-0003).
     */
    BestEffort,

    /** Permission granted, channel audible, exemption held. As close to a promise as this gets. */
    Armed,
}

/**
 * The resolver, as a **pure function** — the whole point of this file.
 *
 * Every input is a fact somebody else owns: Android holds the permission, the owner holds the
 * channel's importance, the OS holds the exemption. Reading them needs a `Context`; *deciding* what
 * they add up to does not, and keeping the decision here is what makes all three states a case table
 * in a JVM test instead of three phones.
 *
 * Order matters. Blocked is checked first because it is certain and the other two are not: a phone
 * with no permission and no exemption is blocked, not best-effort, and saying "may not arrive
 * reliably" about something that definitely will not arrive is the hedge that teaches an owner to
 * stop reading the line.
 *
 * @param notificationsPermitted `POST_NOTIFICATIONS` on API 33+, and the settings-level switch below
 *   it — `NotificationManagerCompat.areNotificationsEnabled` answers both.
 * @param channelImportance the channel's current importance. `IMPORTANCE_NONE` is the owner having
 *   muted this kind specifically, which is a different decision from muting the app and is honoured
 *   the same way.
 * @param batteryExemptionConfirmed `PowerManager.isIgnoringBatteryOptimizations`. **Autostart is
 *   deliberately not an input**: HyperOS exposes no readable state for it, so requiring it would
 *   make [Armed] permanently unreachable on the only device this project tests on — a permanent
 *   hedge, which is wallpaper in exactly the way a permanent nag is (ADR-0003's Phase 4a amendment).
 */
fun resolveReminderDelivery(
    notificationsPermitted: Boolean,
    channelImportance: Int,
    batteryExemptionConfirmed: Boolean,
): ReminderDelivery =
    when {
        !notificationsPermitted -> ReminderDelivery.Blocked
        channelImportance == NotificationManager.IMPORTANCE_NONE -> ReminderDelivery.Blocked
        !batteryExemptionConfirmed -> ReminderDelivery.BestEffort
        else -> ReminderDelivery.Armed
    }

/**
 * The impure half: reads the three facts off this phone and hands them to [resolveReminderDelivery].
 *
 * Ensures the channels exist first, because "the channel does not exist yet" and "the owner muted
 * the channel" are different answers and only the second one is [ReminderDelivery.Blocked]. This is
 * one of the two first-use sites [ensureReminderChannels] describes.
 */
fun Context.reminderDelivery(channel: ReminderChannel): ReminderDelivery {
    ensureReminderChannels()
    return resolveReminderDelivery(
        notificationsPermitted = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        channelImportance = reminderChannelImportance(channel),
        batteryExemptionConfirmed = isIgnoringBatteryOptimisations(),
    )
}
