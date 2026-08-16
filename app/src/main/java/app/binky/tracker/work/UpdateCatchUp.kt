package app.binky.tracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.binky.tracker.BinkyApplication

/** The unique work an update enqueues. One at a time; a second update replaces the first. */
const val UPDATE_CATCH_UP_WORK_NAME = "update-catch-up"

/**
 * **What happens the moment an update lands, rather than whenever the owner next opens the app.**
 *
 * `MY_PACKAGE_REPLACED` is delivered to the app that was just replaced, and it is the only moment
 * the app can be sure an update happened. Two things are worth doing there.
 *
 * The migration is the first. A schema-bumping update leaves a database at the old version until
 * something opens it, and until then every background entry point correctly declines to work
 * (`schemaBlocksBackgroundWork` still blocks a debug wipe and an unreadable file). Waiting for a
 * launch means the first dose after an update goes unposted — measured on the phone at 7.5: the
 * alarm survives the package replace, fires, finds a database at the old version, and the receiver
 * returns before posting *and* before re-arming, so the chain stops.
 *
 * Re-arming is the second, and it is insurance rather than a known need. On the test phone the
 * pending dose alarm **survived** the package replace intact, so nothing was lost — but that is one
 * OEM's answer, alarms are cheap to re-arm, and an app whose job is reminding somebody to medicate a
 * rabbit should not depend on a behaviour it cannot check on every phone.
 *
 * A worker rather than the receiver's own ten seconds: `goAsync` gives a receiver about that long
 * before Android kills the process, and a migration that rebuilds a table row by row over years of
 * history is not a ten-second promise. Expedited so it runs now, with
 * `RUN_AS_NON_EXPEDITED_WORK_REQUEST` as the fallback, because being out of expedited quota is a
 * reason to run later rather than not at all.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // The manifest filters for this already; reading the action here is what stops a re-used
        // receiver acting on a broadcast it was never registered for — same reasoning as
        // `TimeChangeReceiver`, and the same thing lint's `UnsafeProtectedBroadcastReceiver` wants.
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        enqueueUpdateCatchUp(context)
    }
}

/** Enqueues [UpdateCatchUpWorker]. `REPLACE`: only the most recent update is worth catching up to. */
fun enqueueUpdateCatchUp(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        UPDATE_CATCH_UP_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<UpdateCatchUpWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build(),
    )
}

class UpdateCatchUpWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // The same guard every background entry point asks, and for the same reason: a debug build
        // must not wipe without consent, and a file no migration covers must not be forced open in a
        // process with nobody to show the failure to. An upgrade a migration *can* walk falls
        // through — which is the whole point of running here.
        if (applicationContext.schemaBlocksBackgroundWork()) return Result.success()

        // Opening is what migrates: Room applies the registered migrations on the way in, and
        // `BinkyApplication.onCreate` has already copied the old file into `preserved/` before this
        // process reached any worker at all, so the insurance is in place either way.
        //
        // Wrapped, like the sweep's three halves: a migration that throws must not cost the re-arm
        // below, which is the half that matters to an owner tonight.
        val container = (applicationContext as BinkyApplication).container
        runCatching { container.openDatabase() }
        runCatching { applicationContext.rescheduleDoseAlarm() }

        // `KEEP`, so an update does not push a perfectly good pending sweep back by up to a day.
        ensureSweepEnqueued(applicationContext)
        return Result.success()
    }
}
