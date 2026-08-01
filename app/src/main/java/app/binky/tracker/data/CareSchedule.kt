package app.binky.tracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * When care is next due, and whether that has been reached — **derived on every read, stored
 * nowhere** (ADR-0002).
 *
 * Everything here is pure, which is the point: a schedule that survives a completion back-dated
 * across a DST boundary, a completion three weeks overdue, and `31 January + 1 MONTH` is a case
 * table, and a case table is a JVM test rather than something proven by hand on a phone at 09:00.
 *
 * The one constant this file does *not* own is the time of day — that is one app-wide preference
 * (`AppPreferences.reminderTime`), passed in, because it is the sweep's time too and the two must be
 * the same number.
 */

/**
 * The derived due date: the latest completion plus the interval, else the anchor **unmodified**.
 *
 * Both halves are deliberate. Scheduling from the *completion* rather than from the date it was
 * originally due is what makes overdue not drift — a nail trim done three weeks late resets the six
 * weeks, it does not owe them. Returning the anchor untouched is what makes [CareReminderEntity.firstDueOn]
 * a due date rather than a pseudo-completion: a reminder never completed is due when the owner said
 * it was due, however long ago that was.
 */
fun careDueOn(
    anchor: LocalDate,
    lastCompletedOn: LocalDate?,
    interval: CareInterval,
): LocalDate = lastCompletedOn?.plus(interval) ?: anchor

/**
 * The instant the derived due date falls at, in the device's **current** zone.
 *
 * Resolved fresh every call and never stored: a pre-computed absolute trigger points at the wrong
 * wall-clock the moment the owner flies somewhere, and "09:00 on the 14th" means 09:00 where they are
 * (ADR-0003). Day-granularity means DST cannot double or drop an occurrence the way it can a dose,
 * but the zone still decides which instant a date-and-time is — so the zone is a parameter and not a
 * `ZoneId.systemDefault()` buried in the body.
 *
 * `java.time`'s default resolution handles both awkward days: a time falling in a spring-forward
 * *gap* shifts to the first valid instant, one in a fall-back *overlap* takes the earlier offset.
 * Never zero times, never twice.
 */
fun nextOccurrence(
    anchor: LocalDate,
    lastCompletedOn: LocalDate?,
    interval: CareInterval,
    reminderTime: LocalTime,
    zone: ZoneId,
): Instant =
    careDueOn(anchor, lastCompletedOn, interval)
        .atTime(reminderTime)
        .atZone(zone)
        .toInstant()

/**
 * What counts as this reminder's last completion.
 *
 * **A weigh-in reminder's last completion also counts weights** (ADR-0018's amendment): the later of
 * its own care events and the bunny's latest weighing. Without it the app tells the owner a weigh-in
 * is overdue while holding the weight that proves it was done — ADR-0001's principle running in the
 * other direction, and checkable besides.
 *
 * **Read-side only, writing nothing.** Nothing is stored, so back-dating or deleting a weight heals
 * the schedule for free, and [nextOccurrence] stays pure — it simply receives a date the repository
 * computed from two sources.
 */
fun lastCompletedOn(
    type: CareType?,
    latestEventOn: LocalDate?,
    latestWeightOn: LocalDate?,
): LocalDate? =
    when {
        type != CareType.WEIGH_IN -> latestEventOn
        latestEventOn == null -> latestWeightOn
        latestWeightOn == null -> latestEventOn
        else -> maxOf(latestEventOn, latestWeightOn)
    }

/**
 * A reminder with its schedule worked out — what every screen and the sweep read.
 *
 * A separate type rather than fields on the entity, because none of this is in the database and a
 * `dueOn` that looked like a column would eventually be written to one.
 */
data class ScheduledCare(
    val reminder: CareReminderEntity,
    val lastCompletedOn: LocalDate?,
    val dueOn: LocalDate,
) {
    /** Due today or any day before it. */
    fun isDueBy(today: LocalDate): Boolean = !today.isBefore(dueOn)

    /** Due and the day has passed. Overdue state is carried by the screen indefinitely. */
    fun isOverdueOn(today: LocalDate): Boolean = today.isAfter(dueOn)

    /**
     * Whether the sweep should post for this reminder: due, and not already posted **for this due
     * date**.
     *
     * A due reminder notifies once and never again. A notification that re-fires daily for a nail
     * trim is precisely the wallpaper failure ADR-0001 rejects for the watch, and the argument does
     * not change because the subject does — the Care screen is what carries overdue state, and it
     * carries it without shouting.
     */
    fun needsNotifying(today: LocalDate): Boolean = isDueBy(today) && reminder.notifiedForDueOn != dueOn

    fun occurrenceAt(
        reminderTime: LocalTime,
        zone: ZoneId,
    ): Instant = dueOn.atTime(reminderTime).atZone(zone).toInstant()
}

/** Pairs a reminder with a last completion the caller resolved. */
fun scheduleFor(
    reminder: CareReminderEntity,
    lastCompletedOn: LocalDate?,
): ScheduledCare =
    ScheduledCare(
        reminder = reminder,
        lastCompletedOn = lastCompletedOn,
        dueOn = careDueOn(reminder.firstDueOn, lastCompletedOn, reminder.interval),
    )

/** The device's current day, for the predicates above. */
fun today(
    now: Instant,
    zone: ZoneId,
): LocalDate = now.atZone(zone).toLocalDate()
