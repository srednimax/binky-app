package app.binky.tracker.work

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three honest states, as a case table (ADR-0003's Phase 4a amendment).
 *
 * A case table is the whole reason `resolveReminderDelivery` is pure. The alternative is three
 * phones in three configurations, one of which — a muted channel on a device that also lacks the
 * exemption — nobody would think to set up by hand, and which is exactly where the two failure kinds
 * could be confused for each other.
 *
 * Runs on the JVM with no Android framework under it. `NotificationManager.IMPORTANCE_NONE` is a
 * Java compile-time constant, so the reference below is the literal `0` by the time this is
 * bytecode — no framework class is loaded.
 */
class ReminderDeliveryTest {
    private val muted = NotificationManager.IMPORTANCE_NONE
    private val audible = NotificationManager.IMPORTANCE_DEFAULT

    @Test
    fun `no permission is blocked, whatever else is true`() {
        // Both rows matter: "blocked" has to win over "best-effort" *and* over everything being
        // otherwise perfect, because it is the only certain state of the three.
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = false,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
            ),
        )
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = false,
                channelImportance = muted,
                batteryExemptionConfirmed = false,
            ),
        )
    }

    @Test
    fun `a muted channel is blocked even with the permission granted`() {
        // The owner muting this kind of notification specifically is a different decision from
        // turning the app's notifications off, and it is honoured the same way. Saying "may not
        // arrive reliably" here would hedge about something that definitely will not arrive.
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = muted,
                batteryExemptionConfirmed = true,
            ),
        )
    }

    @Test
    fun `permitted and audible but unexempted is best-effort`() {
        assertEquals(
            ReminderDelivery.BestEffort,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = false,
            ),
        )
    }

    @Test
    fun `armed needs all three, and is reachable on a phone with no autostart list`() {
        // The reachability is the point of the assertion, not the value — and since 9a it is
        // conditional. 4a made armed reachable everywhere by leaving autostart out, on the argument
        // that a permanent hedge is wallpaper; 9a found the hedge was true, and true by nearly four
        // hours. So armed survives exactly where nothing unreadable stands behind it, which is what
        // the default `oemAutostartUnreadable = false` says.
        assertEquals(
            ReminderDelivery.Armed,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
            ),
        )
    }

    @Test
    fun `an unreadable autostart list is best-effort, however well set up the rest is`() {
        // **9a, as a case.** Permission granted, channel audible, exemption held, exact alarms
        // permitted — every fact the app can read is good, and the alarm still did not arrive for
        // 3h50m because HyperOS had frozen the process. Armed here would be the app promising
        // something it watched its own phone not do.
        assertEquals(
            ReminderDelivery.BestEffort,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
                oemAutostartUnreadable = true,
            ),
        )
    }

    @Test
    fun `an autostart list does not soften a certain failure`() {
        // Same ordering rule as everything else: blocked is knowable, best-effort is a hedge, and
        // the newest hedge does not get to outrank the certainty either.
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = false,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
                oemAutostartUnreadable = true,
            ),
        )
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = muted,
                batteryExemptionConfirmed = true,
                oemAutostartUnreadable = true,
            ),
        )
    }

    @Test
    fun `three missing facts are still one best-effort`() {
        // The state stays three-valued as the inputs grow. Which of the three decides the sentence
        // and the tap target, and that ranking lives in the composables that hold all three —
        // adding a fourth enum entry per reason is how a delivery line stops being one sentence.
        assertEquals(
            ReminderDelivery.BestEffort,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = false,
                exactAlarmsPermitted = false,
                oemAutostartUnreadable = true,
            ),
        )
    }

    @Test
    fun `a dose without exact alarms is best-effort, not blocked`() {
        // **The distinction the whole fourth input exists for.** Without SCHEDULE_EXACT_ALARM the
        // alarm still goes in, via setAndAllowWhileIdle, which pierces Doze inside a window the OS
        // picks. Something arrives; it is merely imprecise. Calling that blocked would tell an owner
        // mid-treatment that nothing is coming when something is.
        assertEquals(
            ReminderDelivery.BestEffort,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
                exactAlarmsPermitted = false,
            ),
        )
    }

    @Test
    fun `no permission still beats a missing exact alarm`() {
        // Order matters between a certain failure and an imprecise one, and only in that direction:
        // blocked is knowable, best-effort is a hedge, and hedging about something that definitely
        // will not arrive is how a delivery line stops being read.
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = false,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
                exactAlarmsPermitted = false,
            ),
        )
        assertEquals(
            ReminderDelivery.Blocked,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = muted,
                batteryExemptionConfirmed = true,
                exactAlarmsPermitted = false,
            ),
        )
    }

    @Test
    fun `both mechanisms missing is still one best-effort`() {
        // Two hedges do not stack into a third state. The resolver stays three-valued; which of the
        // two facts is missing decides the sentence and the tap target, and that choice lives in the
        // composable that has both facts in hand rather than in a fourth enum entry nobody could
        // render honestly.
        assertEquals(
            ReminderDelivery.BestEffort,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = false,
                exactAlarmsPermitted = false,
            ),
        )
    }

    @Test
    fun `the exact-alarm input defaults to true, so the sweep's channels are unaffected`() {
        // Care, watch and backup are delivered by the daily sweep, where this permission changes
        // nothing. The default is what keeps 4a's three call sites honest without each of them
        // having to pass a fact about a mechanism they do not use. The autostart default rides the
        // same call: a caller who says nothing about either is a stock phone, and stock phones are
        // not hedged about.
        assertEquals(
            ReminderDelivery.Armed,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = audible,
                batteryExemptionConfirmed = true,
            ),
        )
    }

    @Test
    fun `a lowered but unmuted channel still delivers`() {
        // IMPORTANCE_LOW is a silent notification, not a suppressed one. An owner who took the sound
        // off a daily nag has not asked to stop being told.
        assertEquals(
            ReminderDelivery.Armed,
            resolveReminderDelivery(
                notificationsPermitted = true,
                channelImportance = NotificationManager.IMPORTANCE_LOW,
                batteryExemptionConfirmed = true,
            ),
        )
    }
}
