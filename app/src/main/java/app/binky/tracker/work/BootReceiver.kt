package app.binky.tracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the daily sweep back after a restart, and re-places the one pending dose alarm.
 *
 * **Two mechanisms, two lines, and no schedule read from anywhere** (ADR-0024, ADR-0025): every due
 * date in this app is derived from the data, and the OS schedule was only ever a cache of that
 * derivation. With one sweep there is nothing per-reminder to rebuild, and with one dose alarm there
 * is nothing per-course either — which is why the "re-enqueued on boot, fires again, notifies twice"
 * failure that per-reminder work invites cannot happen here.
 *
 * A reboot **does** lose exact alarms outright, which is why the dose call is a full reschedule
 * where the sweep's is [ensureSweepEnqueued] (leave a good one alone): WorkManager normally restores
 * its own enqueued work and AlarmManager restores nothing.
 *
 * On HyperOS this receiver runs only if autostart is granted — which is off by default and which the
 * app cannot read at all. With it denied, process start is the only rebuild left (ADR-0025).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // The manifest filters for this already; the check is what stops a re-used receiver acting
        // on a broadcast it was never registered for.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Off the main thread, holding the broadcast open: both calls touch disk, and the dose
        // rebuild reads the database header for ADR-0007's guard before it does anything else.
        rebuildInBackground(context) {
            ensureSweepEnqueued(it)
            it.rescheduleDoseAlarm()
        }
    }
}
