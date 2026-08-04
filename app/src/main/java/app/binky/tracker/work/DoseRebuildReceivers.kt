package app.binky.tracker.work

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/*
 * **Three receivers, one function** (ADR-0025, PLAN 5a). `BootReceiver` is the third and lives in
 * its own file because it also has a sweep to put back; these two do nothing else at all.
 *
 * The single pending dose alarm is one point of failure, and the failure is invisible by
 * construction — zero pending alarms is also the correct state when nothing is armed, so nothing can
 * tell "no course" from "alarm lost". Since the rebuild is idempotent and costs one query, the
 * answer is not more alarms but more occasions to rebuild the same one.
 */

/**
 * An alarm is an absolute instant; a dose slot is a wall-clock time. Change the clock or cross a
 * timezone and the pending alarm points at the wrong moment — 08:00 means 08:00 where the owner is,
 * not where they were when the course was created (ADR-0003).
 *
 * `ACTION_TIME_CHANGED` is the manual clock change, `ACTION_TIMEZONE_CHANGED` the flight. Both are
 * protected broadcasts, which the system may deliver to a receiver no other app can reach — so
 * `exported="false"` in the manifest is correct despite the sender being another process.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // The manifest filters for these already; reading the action here is what stops a re-used
        // receiver acting on a broadcast it was never registered for — and it is also what lint's
        // `UnsafeProtectedBroadcastReceiver` looks for, which is a good reason to keep the check
        // literal rather than behind a helper neither lint nor a reader can follow.
        val action = intent.action
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED) return
        rebuildInBackground(context) { it.rescheduleDoseAlarm() }
    }
}

/**
 * The moment `SCHEDULE_EXACT_ALARM` is granted, an alarm placed inexactly has to be re-placed
 * exactly — otherwise the first precise dose is whichever one happens to follow the next reboot, and
 * the owner who just walked into settings to fix this sees no change from having done so.
 *
 * **Only the grant arrives here.** Revoking the permission force-stops the app on Android 14+ and
 * cancels its pending exact alarms, and a force-stopped app runs no receivers; the rebuild that
 * covers that case is the one at process start, when the owner next opens the app (ADR-0025).
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // The broadcast exists from API 31 only. Below that the manifest filter simply never
        // matches, and the constant is a compile-time String, so nothing here is version-fragile —
        // the check is what documents it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        rebuildInBackground(context) { it.rescheduleDoseAlarm() }
    }
}
