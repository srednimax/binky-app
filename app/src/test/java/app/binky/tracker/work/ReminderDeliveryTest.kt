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
    fun `armed needs all three, and is reachable`() {
        // The reachability is the point of the assertion, not the value. ADR-0003 as written
        // conditioned armed on autostart too, which has no readable state on HyperOS — a strict
        // reading makes this case impossible on the only device this project tests on, and a
        // permanent hedge is wallpaper in the same way a permanent nag is.
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
