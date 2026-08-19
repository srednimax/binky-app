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
     *
     * Since 5a it also covers a *dose* without `SCHEDULE_EXACT_ALARM`, and that case is different in
     * kind: the alarm still goes in, via `setAndAllowWhileIdle`, which pierces Doze but inside a
     * window the OS chooses. So the reminder is real and merely imprecise — the one place the app
     * degrades a **mechanism** rather than a promise. Same state, different sentence and different
     * fix; the copy and the tap target are chosen by whichever fact is missing.
     *
     * Since 9a it is also where an unreadable **OEM autostart list** lands, and that is the reason
     * this state is now the ceiling on a Xiaomi rather than a step below one. Three facts, three
     * sentences, one state.
     */
    BestEffort,

    /**
     * Permission granted, channel audible, exemption held — and no OEM autostart list standing
     * behind all three. As close to a promise as this gets, which is why 9a took it away from the
     * phones that cannot keep it.
     */
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
 * @param batteryExemptionConfirmed `PowerManager.isIgnoringBatteryOptimizations`.
 * @param oemAutostartUnreadable whether this phone keeps an OEM autostart list — a list this app can
 *   be off without being able to tell. **An input since 9a, having deliberately not been one since
 *   4a**, and the reversal is evidence rather than a change of mind: on HyperOS, with autostart
 *   denied, an exact alarm armed `window=0` did not fire in deep Doze at all. The vendor froze the
 *   process and held the alarm for **3h50m**, until the phone was plugged in. The same alarm with
 *   autostart granted fired 779 ms late, 50 minutes deep into Doze (ADR-0003's Phase 9 amendment).
 *   So where such a list exists, [Armed] is not a promise the app can keep, and the honest ceiling
 *   is [BestEffort] with the reason named. Defaults to false — a phone with no such list is not
 *   hedged about.
 * @param exactAlarmsPermitted `SCHEDULE_EXACT_ALARM`. **Doses only** — the fourth input, and it
 *   defaults to true because it is a fact about the *exact-alarm* mechanism and nothing else rides
 *   it. Care, watch and backup are delivered by the daily sweep, where this permission changes
 *   nothing, and reporting on it there would be hedging about a mechanism that is not in use.
 *   Absent, it is [ReminderDelivery.BestEffort] and **not** [ReminderDelivery.Blocked]: the alarm
 *   still goes in inexactly, so something arrives.
 */
fun resolveReminderDelivery(
    notificationsPermitted: Boolean,
    channelImportance: Int,
    batteryExemptionConfirmed: Boolean,
    exactAlarmsPermitted: Boolean = true,
    oemAutostartUnreadable: Boolean = false,
): ReminderDelivery =
    when {
        !notificationsPermitted -> ReminderDelivery.Blocked
        channelImportance == NotificationManager.IMPORTANCE_NONE -> ReminderDelivery.Blocked
        // Before the exemption, and it only matters for the sentence the caller then writes: an
        // owner missing both is told about the one whose fix is a single toggle and whose absence
        // is the app's own doing, rather than about the OEM screen underneath it.
        !exactAlarmsPermitted -> ReminderDelivery.BestEffort
        !batteryExemptionConfirmed -> ReminderDelivery.BestEffort
        // Last of the three, and only because the other two have fixes the app can verify
        // afterwards. This one it can only offer. Which of the three is missing decides the
        // sentence and the tap target, and that choice lives in the composable holding all three.
        oemAutostartUnreadable -> ReminderDelivery.BestEffort
        else -> ReminderDelivery.Armed
    }

/**
 * The impure half: reads the facts off this phone and hands them to [resolveReminderDelivery].
 *
 * Ensures the channel exists first, because "the channel does not exist yet" and "the owner muted
 * the channel" are different answers and only the second one is [ReminderDelivery.Blocked]. This is
 * one of the two first-use sites [ensureReminderChannel] describes.
 */
fun Context.reminderDelivery(channel: ReminderChannel): ReminderDelivery {
    ensureReminderChannel(channel)
    return resolveReminderDelivery(
        notificationsPermitted = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        channelImportance = reminderChannelImportance(channel),
        batteryExemptionConfirmed = isIgnoringBatteryOptimisations(),
        // The one channel that goes out as an alarm rather than through the sweep. Written as a
        // property of the channel rather than left to each caller to remember, so a fifth channel
        // added later cannot accidentally start reporting on a permission it does not use.
        exactAlarmsPermitted = channel != ReminderChannel.Doses || canScheduleExactAlarms(),
        // Every channel, not just doses. The freezer holds the *process*, so nothing this app
        // schedules is above it, and a promise it cannot keep is worth less than a hedge that
        // names the way in.
        oemAutostartUnreadable = hasAutostartSettings(),
    )
}
