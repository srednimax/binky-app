package app.binky.tracker.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.binky.tracker.data.DueDose
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * **A dose answered from the shade** — `Given` or `Skipped`, one tap, no app launch (ADR-0025).
 *
 * This is the reminder's whole point rather than a convenience: an owner holding a syringe at 08:00
 * has one hand free, and a reminder that made them unlock the phone, find the bunny and find the
 * course before they could record what they had just done would be recorded late or not at all.
 *
 * **The order is guard, write, cancel** — and the guard is first for the reason every background
 * entry point in this package puts it first (ADR-0007): the OS can deliver this to a process with no
 * UI over a schema this build must not open, and a guarded receiver has to leave the database
 * untouched and the alarm unchanged rather than half-applied.
 *
 * The re-arm is not here, and that is deliberate. `MedicationRepository.answer` rebuilds the alarm
 * itself, because ADR-0025 wires the rebuild at the repository rather than at the call sites that
 * kept forgetting it — the answered slot leaves the derivation and the next one becomes the earliest,
 * whether the answer arrived from this receiver or from the course screen.
 */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // Parsed before `goAsync()`: an intent that is not one of ours is not worth holding a
        // broadcast open for, and `intent` itself must be read on this thread.
        val action = intent.doseAction() ?: return

        rebuildInBackground(context) { appContext ->
            val medications = appContext.doseMedications() ?: return@rebuildInBackground

            val on = LocalDate.ofEpochDay(action.epochDay)
            val time = LocalTime.ofSecondOfDay(action.secondOfDay.toLong())
            val zone = ZoneId.systemDefault()
            medications.answer(
                slot =
                    DueDose(
                        courseId = action.courseId,
                        scheduledOn = on,
                        scheduledTime = time,
                        // Recomputed rather than carried (ADR-0002): the slot's key is the local day
                        // and time, and `at` is only ever an instant derived from them in whatever
                        // zone the phone is in *now*. A notification posted in Warsaw and answered
                        // after landing in London still answers the 08:00 slot it named.
                        at = on.atTime(time).atZone(zone).toInstant(),
                    ),
                status = action.status,
            )

            // Actions do not dismiss their own notification the way a content tap does, and one left
            // in the shade offering *Given* for a dose already answered is the only copy of that
            // wrong idea left anywhere.
            NotificationManagerCompat.from(appContext).cancel(doseNotificationId(action.courseId))
        }
    }
}
