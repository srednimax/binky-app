package app.binky.tracker.work

import app.binky.tracker.data.ScheduledCare
import java.time.LocalDate

/*
 * **What the daily sweep decides**, with nothing Android in it (ADR-0024).
 *
 * The sweep itself is a worker that reads repositories and posts notifications; this file is the
 * part in between, which is the part that can be wrong in a way nobody notices for a week. Keeping
 * it pure is what makes "an archived bunny is never notified about" and "a due reminder notifies
 * once" case-table assertions rather than things to watch for on a phone at nine in the morning.
 */

/** A bunny as the sweep sees it: who they are, whether they are archived, and what is scheduled. */
data class SweepBunny(
    val id: String,
    val name: String,
    val archived: Boolean,
    val schedule: List<ScheduledCare>,
)

/** One reminder that has come due for a bunny who should hear about it. */
data class DueCare(
    val bunnyId: String,
    val bunnyName: String,
    val scheduled: ScheduledCare,
)

/**
 * Everything the sweep should post about today, across every bunny.
 *
 * **Archived bunnies are excluded here**, in the derivation, rather than by the caller happening to
 * ask only about active ones. An archived bunny has died or been rehomed, and a notification about
 * its nail trim is the same failure ADR-0001 names for a trend flag on a memorial page — so the rule
 * lives somewhere it can be read and asserted, and the caller passes every bunny it has. With one
 * sweep there is no per-bunny work to cancel either (ADR-0024), which is what makes this a fact
 * about the derivation and nothing else.
 *
 * The second filter is [ScheduledCare.needsNotifying]: due, and not already posted **for this due
 * date**. That is the whole of "notifies once, and never again" — a reminder left overdue for three
 * weeks is due on all twenty-one of them and posts on none but the first.
 */
fun careDueForNotifying(
    bunnies: List<SweepBunny>,
    today: LocalDate,
): List<DueCare> =
    bunnies
        .filterNot { it.archived }
        .flatMap { bunny ->
            bunny.schedule
                .filter { it.needsNotifying(today) }
                .map { DueCare(bunnyId = bunny.id, bunnyName = bunny.name, scheduled = it) }
        }

/**
 * Ids below this are spoken for: 1 is the debug reminder, 2 the care group summary, 3 the export
 * prompt, 4 the debug dose, 5 the excluded-documents notice.
 *
 * A block rather than a handful of constants, so 4d's watch nag, 4e's export reminder and 5a's dose
 * had somewhere to take an id from without revisiting the derivation below.
 */
const val RESERVED_NOTIFICATION_IDS = 16

/** The care group's summary — one per sweep, not one per reminder. */
const val CARE_SUMMARY_NOTIFICATION_ID = 2

/**
 * The recurring export prompt. A **fixed** id, unlike care's and the watch's: there is exactly one
 * export reminder in the app, so there is nothing to derive it from and nothing to collide with.
 */
const val EXPORT_NOTIFICATION_ID = 3

/**
 * The one-time notice that documents were left out of an automatic backup (PLAN 5h). Fixed for the
 * same reason the export prompt's is: there is one of it, describing one standing condition, and a
 * second launch while the condition holds must replace it rather than stack a duplicate.
 */
const val BACKUP_EXCLUSION_NOTIFICATION_ID = 5

/**
 * A notification id **derived from the reminder id**, and therefore stable across sweeps.
 *
 * Stability is the requirement. A sweep that runs twice before `notifiedForDueOn` commits — a
 * process death between the post and the write is enough — must replace its own notification rather
 * than stack a second copy of the same sentence, and replacing is what Android does when the id
 * matches. It is also what lets a completion cancel exactly the notification that reminder posted.
 *
 * `hashCode` collisions are possible in principle and harmless in practice: two reminders that
 * collided would replace each other's notification, on a phone that would have to hold billions of
 * them for it to be likely. Anything unique enough to rule it out would have to be stored, and a
 * stored id is a column that has to be cleaned up on delete.
 */
fun careNotificationId(reminderId: String): Int {
    // `and Int.MAX_VALUE` clears the sign bit, which `abs` cannot do for Int.MIN_VALUE.
    val positive = reminderId.hashCode() and Int.MAX_VALUE
    return if (positive < RESERVED_NOTIFICATION_IDS) positive + RESERVED_NOTIFICATION_IDS else positive
}
