package app.binky.tracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BUNNY_DATABASE_FILE
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.dueOn
import app.binky.tracker.data.needsNotifying
import app.binky.tracker.data.readUserVersion
import app.binky.tracker.data.schemaMismatchPending
import app.binky.tracker.data.today
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The one name every sweep is enqueued under. **Never two**: the invariant this design is checkable
 * by is "exactly one enqueued work item exists at any time" (ADR-0024), and that is only true while
 * there is one name.
 */
const val SWEEP_WORK_NAME = "reminder-sweep"

/**
 * When the sweep runs, absent an owner's choice — `AppPreferences.reminderTime` is where that choice
 * lives, and this is the value it falls back to.
 *
 * One app-wide time, not one per reminder: per-reminder clock times would promise a precision
 * ADR-0003 deliberately reserves for medication doses, and would need the exact-alarm path to mean
 * anything at all. It lives here rather than beside the preference because it is a fact about the
 * *sweep* — the preference names the sweep's hour, not the other way round.
 */
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * **One worker for the whole app** (ADR-0024), and the reason there is no per-reminder scheduled
 * work to cancel, orphan or lose.
 *
 * At 4a it derives nothing, because there is nothing to derive — no care table exists yet. What it
 * proves is the path: that a worker runs at all, that it survives a reboot, that it re-arms itself,
 * and that it refuses to run over a database it must not touch. Proving that here, on an empty
 * database, rather than underneath the first real reminder, means a missed notification later has
 * one suspect instead of two.
 *
 * Kotlin note: `CoroutineWorker.doWork` is a `suspend` function, so the whole body already runs off
 * the main thread on WorkManager's own dispatcher — there is no `withContext(Dispatchers.IO)` to
 * add, and adding one would only move the work sideways.
 */
class ReminderSweepWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // ADR-0007's guard, from the direction the ADR predicted: the OS can start this process to
        // run a worker with no UI and no owner present, and any worker that touches a repository
        // forces the container — which, at a stale schema, destroys the database in the background
        // on a phone nobody is looking at. So this is asked *before* anything else, and answered
        // out of four bytes of the file header rather than by opening anything.
        val onDisk = readUserVersion(applicationContext.getDatabasePath(BUNNY_DATABASE_FILE))
        if (schemaMismatchPending(onDisk, BUNNY_SCHEMA_VERSION)) {
            // Success, and deliberately no re-enqueue. There is nothing wrong with the *work* — the
            // database is simply not this build's to open yet, and the consent screen is what
            // resolves that. Re-arming happens on the next launch, through the same path the boot
            // receiver uses, once the owner has been through it.
            return Result.success()
        }

        // The three halves. Wrapped, and deliberately: this is the one place where a failure must
        // not cost the *next* sweep. A throw here would return `Result.failure` with the re-enqueue
        // below unreached, and the app would go quiet until the next launch or reboot — a far worse
        // outcome than one missed morning.
        //
        // **One `runCatching` each**, not one around all three: a care reminder that throws must not
        // cost the watch nag its morning, and a watch is running precisely because somebody is
        // worried. The export prompt is last for the same reason it is least urgent — and it is
        // wrapped separately so a preferences file that will not read cannot silence the other two.
        val container = (applicationContext as BinkyApplication).container
        runCatching { sweepCare(container) }
        runCatching { sweepWatch(container) }
        runCatching { sweepExport(container) }

        // Before returning, not after: this is what keeps the sweep permanently enqueued, and a
        // sweep that only re-armed on a successful pass would go quiet the first time anything
        // downstream threw.
        enqueueNextSweep(applicationContext)
        return Result.success()
    }
}

/**
 * Derives what care is due today, posts it, and records what was posted for.
 *
 * The order is not negotiable. `markNotified` runs **after** the post, so a process killed between
 * the two leaves the reminder still needing notifying — the next sweep posts again, replacing its own
 * notification because the id is derived from the reminder id (see [careNotificationId]). The other
 * order loses the notification for good.
 *
 * **Archived bunnies are read and then excluded**, rather than never asked about. The exclusion is
 * the rule ADR-0001 cares about, so it lives in [careDueForNotifying] where it is a case-table
 * assertion; the few extra queries a memorial bunny costs once a day are the price of it being
 * checkable at all.
 */
private suspend fun sweepCare(
    container: AppContainer,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val bunnies = container.bunnyRepository.activeBunnies.first() + container.bunnyRepository.archivedBunnies.first()
    val today = today(now, zone)

    val sweepable =
        bunnies.map { bunny ->
            SweepBunny(
                id = bunny.id,
                name = bunny.name,
                archived = bunny.archivedAt != null,
                schedule = container.careRepository.scheduleNow(bunny.id, zone),
            )
        }

    val due = careDueForNotifying(sweepable, today)
    container.careNotifier.post(due, today)
    due.forEach { container.careRepository.markNotified(it.scheduled.reminder.id, it.scheduled.dueOn) }
}

/**
 * Asks, once a morning, whether anybody has looked at each watched bunny.
 *
 * The same post-then-mark order as [sweepCare], for the same reason: a process killed between the
 * two leaves the bunny still needing nagging, and the next sweep replaces its own notification
 * because the id is derived from the bunny id (see [watchNotificationId]). The other order loses the
 * nag for good.
 *
 * **The rows are read first and filtered afterwards**, in [watchesDueForNagging] — archived,
 * expired, already-nagged and already-observed are all rules ADR-0001 cares about, so they live
 * somewhere they can be read and asserted rather than being implied by which query was run.
 */
private suspend fun sweepWatch(
    container: AppContainer,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val watches = container.watchRepository.watchesNow()
    // The overwhelmingly common case, and worth an early return: no watches means no bunny reads,
    // no observation reads, and a sweep that costs one query on the mornings nobody is worried.
    if (watches.isEmpty()) return

    val bunnies =
        (container.bunnyRepository.activeBunnies.first() + container.bunnyRepository.archivedBunnies.first())
            .associateBy { it.id }

    val watched =
        watches.mapNotNull { watch ->
            val bunny = bunnies[watch.bunnyId] ?: return@mapNotNull null
            WatchedBunny(
                id = bunny.id,
                name = bunny.name,
                archived = bunny.archivedAt != null,
                watch = watch,
                lastObservationAt = container.watchRepository.latestObservationNow(bunny.id),
            )
        }

    val due = watchesDueForNagging(watched, now, zone)
    container.watchNotifier.post(due)
    val today = today(now, zone)
    due.forEach { container.watchRepository.markNagged(it.bunnyId, today) }
}

/**
 * Asks whether it is time the owner made an export (ADR-0005, PLAN 4e).
 *
 * **The whole branch is preferences and arithmetic** — no database, no repositories, no bunnies.
 * That is what makes it one more branch in the one worker (ADR-0024) rather than a second scheduled
 * thing: a backup reminder hangs off the app, so the only per-run cost when it is switched off is a
 * single `DataStore` read that returns a `null` interval and stops.
 *
 * Post-then-mark, the same order as the other two and for the same reason: a process killed between
 * them leaves the prompt still needing posting, and the next sweep replaces its own notification
 * because the id is a constant. The other order loses it for good.
 */
private suspend fun sweepExport(
    container: AppContainer,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val reminder = container.preferences.exportReminder.first()
    val today = today(now, zone)
    if (!reminder.needsNotifying(today)) return

    container.exportNotifier.post()
    // Non-null whenever `needsNotifying` was true — it is derived from the same call — but read
    // again rather than smuggled out of the predicate, which would make the predicate return two
    // things and be honest about neither.
    reminder.dueOn()?.let { due -> container.preferences.markExportReminderNotified(due) }
}

/**
 * Puts the sweep in place if it is not already there, and leaves it alone if it is.
 *
 * Called on every launch and from [BootReceiver] — the two moments the app can be sure the OS has
 * not quietly dropped it. `KEEP` rather than `REPLACE` because both callers fire at times when a
 * perfectly good sweep is usually already pending, and replacing it would push its trigger back by
 * up to a day every time the owner reboots or opens the app.
 */
fun ensureSweepEnqueued(
    context: Context,
    now: Instant = Instant.now(),
) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        SWEEP_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        sweepRequest(now),
    )
}

/**
 * Moves the sweep to a new time, discarding whatever was pending.
 *
 * For 4b's reminder-time preference: changing the time has to move the *next* run, not the one after
 * it. `REPLACE` cancels the pending sweep and inserts a fresh one, which is exactly the intent here
 * and exactly the wrong thing for [ensureSweepEnqueued].
 */
fun rescheduleSweep(
    context: Context,
    now: Instant = Instant.now(),
) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        SWEEP_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        sweepRequest(now),
    )
}

/**
 * Tomorrow's sweep, enqueued by today's from inside [ReminderSweepWorker.doWork].
 *
 * `APPEND_OR_REPLACE`, and the choice is not cosmetic. The worker calling this **is** the pending
 * work under [SWEEP_WORK_NAME] at the moment it calls: `REPLACE` would cancel the very worker doing
 * the enqueuing, and `KEEP` would see itself and drop tomorrow's run on the floor. Appending makes
 * the next sweep a child of the one finishing, which is precisely "enqueues tomorrow's run before
 * returning" (ADR-0024). WorkManager prunes the finished ancestors on its own.
 */
private fun enqueueNextSweep(
    context: Context,
    now: Instant = Instant.now(),
) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        SWEEP_WORK_NAME,
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        sweepRequest(now),
    )
}

private fun sweepRequest(now: Instant) =
    OneTimeWorkRequestBuilder<ReminderSweepWorker>()
        .setInitialDelay(
            Duration.between(now, nextSweepAt(now)).toMillis(),
            TimeUnit.MILLISECONDS,
        ).build()

/**
 * The next instant the sweep should run: [reminderTime] today if that is still ahead, otherwise
 * tomorrow.
 *
 * **Resolved fresh in the device's current zone every time**, never stored. A pre-computed absolute
 * trigger points at the wrong wall-clock the moment the owner changes timezone, and "09:00" means
 * 09:00 where they are, not 09:00 where they were when the reminder was made (ADR-0003).
 *
 * Pure, and the reason it is: a daily boundary crossing a DST change is a case table, and a case
 * table is a JVM test. `java.time`'s default resolution handles both awkward days — a time falling
 * in a spring-forward *gap* shifts to the first valid instant, one in a fall-back *overlap* takes
 * the earlier offset. Never zero times, never twice.
 *
 * Strictly-after, not at-or-after: a sweep that ran at exactly 09:00:00.000 must not schedule its
 * successor for the same instant and spin.
 */
fun nextSweepAt(
    now: Instant,
    reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant {
    val today = now.atZone(zone).toLocalDate()
    val todaysSweep = today.atTime(reminderTime).atZone(zone).toInstant()
    return if (todaysSweep > now) {
        todaysSweep
    } else {
        today
            .plusDays(1)
            .atTime(reminderTime)
            .atZone(zone)
            .toInstant()
    }
}
