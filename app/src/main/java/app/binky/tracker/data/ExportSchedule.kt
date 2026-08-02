package app.binky.tracker.data

import java.time.LocalDate

/*
 * **When the app should prompt the owner to make an export** — the recurring backup reminder
 * (ADR-0005, PLAN 4e), derived on every read and stored nowhere but its own four preference keys.
 *
 * A backup reminder is not a care reminder. Care reminders hang off a bunny, live in the database
 * and are `{label, interval, optional type}` rows (ADR-0018); this one hangs off the *app*, so it is
 * a preference, and there is exactly one of it. What the two do share is the arithmetic, and that is
 * reused rather than rewritten: [careDueOn] and `LocalDate.plus(CareInterval)` decide when it comes
 * round, exactly as they do for a nail trim, so the two cannot drift over a month boundary.
 *
 * Pure, and for `CareSchedule.kt`'s reason: "an interval switched on today does not fire tomorrow"
 * and "a due prompt notifies once" are case tables, and a case table is a JVM test rather than
 * something proven by leaving a phone alone for a fortnight.
 */

/**
 * How often the prompt comes round — **four presets, not a free `{count, unit}` field**.
 *
 * The care editor offers any number and any unit because a vet card says what it says. Nobody has a
 * vet card for their own backups: this is a habit the owner is choosing the shape of, and four
 * chips make that one tap where a number field and four unit chips make it three. The values behind
 * them are ordinary [CareInterval]s, so everything downstream is the care path unchanged.
 *
 * Stored by name, never ordinal (house rule) — inserting a fifth entry must not rewrite anybody's
 * setting.
 */
enum class ExportInterval(
    val interval: CareInterval,
) {
    WEEKLY(CareInterval(1, CareIntervalUnit.WEEK)),
    FORTNIGHTLY(CareInterval(2, CareIntervalUnit.WEEK)),
    MONTHLY(CareInterval(1, CareIntervalUnit.MONTH)),
    QUARTERLY(CareInterval(3, CareIntervalUnit.MONTH)),
}

/** What the switch defaults to when the owner turns it on, and what first-run would pick. */
val DEFAULT_EXPORT_INTERVAL = ExportInterval.MONTHLY

/**
 * Everything the export reminder is, read out of preferences in one shot.
 *
 * Four facts and not one, because they are written by four different events and only their
 * *combination* answers "should the sweep say something this morning":
 *
 * @param every the chosen interval, or `null` for **off** — the switch's whole state. Nothing else
 *   here means anything while this is `null`, and the derivation says so by returning `null` too.
 * @param enabledOn the day the switch was last turned on. The anchor: the first prompt lands one
 *   interval after it, so switching a monthly reminder on today does not produce a notification
 *   tomorrow morning.
 * @param lastExportedOn the day the owner last exported, by **either** path — share sheet or
 *   remembered folder. This is the reminder's "completion", and it is what makes the prompt a
 *   reminder rather than a calendar: an owner who exported yesterday hears nothing today.
 * @param notifiedForDueOn the due date a prompt was last posted *for* — ADR-0024's "notifies once"
 *   recorded the same way `CareReminderEntity.notifiedForDueOn` records it, and for the same reason:
 *   the sweep can run twice in a day, and comparing against derived truth needs no clearing on any
 *   of the paths that move the due date.
 */
data class ExportReminder(
    val every: ExportInterval? = null,
    val enabledOn: LocalDate? = null,
    val lastExportedOn: LocalDate? = null,
    val notifiedForDueOn: LocalDate? = null,
)

/**
 * When the next prompt is due, or `null` when the reminder is off.
 *
 * **One interval after the later of "switched on" and "last exported".** Both halves matter and they
 * fail in opposite directions:
 *
 * - Anchoring on [ExportReminder.enabledOn] alone would ignore an export made since, and prompt an
 *   owner who has just backed up.
 * - Anchoring on [ExportReminder.lastExportedOn] alone would make a monthly reminder switched on by
 *   someone who last exported six months ago fire the very next morning — a nag as the immediate
 *   reward for opting in, which is how an owner learns to switch it back off.
 *
 * A missing [ExportReminder.enabledOn] is treated as off rather than as "long ago". The two are
 * written together, so that pairing cannot arise from this app; if it ever does — a hand-edited
 * preferences file, a truncated restore — silence is the safe direction, and the switch is one tap
 * away from writing both again.
 */
fun ExportReminder.dueOn(): LocalDate? {
    val interval = every?.interval ?: return null
    val enabled = enabledOn ?: return null
    return careDueOn(
        anchor = enabled.plus(interval),
        // Only an export *since* the switch was turned on counts as a completion of this reminder;
        // an older one is history the anchor already accounts for.
        lastCompletedOn = lastExportedOn?.takeIf { it.isAfter(enabled) },
        interval = interval,
    )
}

/**
 * Whether the sweep should post the prompt today: due, and not already posted **for this due date**.
 *
 * The same predicate as `ScheduledCare.needsNotifying`, deliberately — an export prompt that
 * re-fired every morning until the owner exported would be the wallpaper failure ADR-0001 rejects,
 * and this one has a lower claim on the owner's attention than an overdue vaccination, not a higher
 * one.
 */
fun ExportReminder.needsNotifying(today: LocalDate): Boolean {
    val due = dueOn() ?: return false
    return !today.isBefore(due) && notifiedForDueOn != due
}
