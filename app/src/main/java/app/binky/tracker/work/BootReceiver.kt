package app.binky.tracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the daily sweep back after a restart. **That is the whole receiver** (ADR-0024).
 *
 * It reads no persisted schedule, because there is none: every due date in this app is derived from
 * the data, and the OS schedule was only ever a cache of that derivation. With one sweep there is
 * nothing per-reminder to rebuild, which is also why the "re-enqueued on boot, fires again, notifies
 * twice" failure that per-reminder work invites cannot happen here.
 *
 * WorkManager normally restores its own enqueued work across a reboot on its own — this is the
 * backstop for the cases where it does not, which is why the call is [ensureSweepEnqueued] (leave a
 * good one alone) and not a reschedule.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // The manifest filters for this already; the check is what stops a re-used receiver acting
        // on a broadcast it was never registered for.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ensureSweepEnqueued(context)
    }
}
