package app.binky.tracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * Which doses are due, and at what instant — **derived on every read, stored nowhere** (ADR-0002).
 *
 * Everything here is pure, and that is the point: a course crossing both DST boundaries, a course
 * shortened to today, a window asking for days a course had not started yet — that is a case table,
 * and a case table is a JVM test rather than something proven by hand on a phone at 08:00.
 *
 * The one thing this file will not do is look backwards. `CareSchedule.kt`'s derivation is happy in
 * both directions because a care reminder's schedule is one anchor plus one interval; a course's
 * times live in a row each, so re-deriving a past day would use *today's* times to decide what that
 * day should have contained. Moving a fortnight's 08:00 course to 20:00 on day ten would re-derive
 * the nine answered days behind it as unanswered, with nine recorded doses matching no slot at all —
 * an edit rewriting history it never touched. So today and forward is derived; **a past day is what
 * was recorded** (ADR-0002), which is a read of the `doses` table and not a function in this file.
 */

/**
 * The span of days a derivation may cover, and it **cannot begin before today**.
 *
 * A type rather than two `LocalDate` parameters because the forward-only rule is worth more as
 * something the compiler holds than as a sentence someone has to remember at each call site: the only
 * way to make a window is to hand [of] the current day, so there is no window that reaches into the
 * past to hand [dueDoses] in the first place.
 *
 * Kotlin note: the constructor is `private` and the factory lives in `companion object`, which is the
 * idiom for "this type has one legal way to be built" — the JS equivalent of exporting a factory
 * function and keeping the class itself module-private.
 */
class DoseWindow private constructor(
    /** The first day derived — always the caller's today. */
    val from: LocalDate,
    /** The last day derived, inclusive. */
    val through: LocalDate,
) {
    companion object {
        /**
         * A window opening on [today] and running [days] days, today included.
         *
         * The default of one day is the screen's question ("what is due today"); 5f's alarm rebuild
         * asks for more, because the earliest slot still to arm can be tomorrow's when today's are
         * all answered — and a course starting tomorrow has to arm tonight or its first dose is lost.
         */
        fun of(
            today: LocalDate,
            days: Long = 1,
        ): DoseWindow {
            require(days >= 1) { "A dose window covers at least today" }
            return DoseWindow(from = today, through = today.plusDays(days - 1))
        }
    }
}

/**
 * One derived dose slot: a course, a local day, a local time, and the instant that resolves to.
 *
 * **The slot's key is [scheduledOn] and [scheduledTime], never [at]** (ADR-0002). The same 08:00 slot
 * is `06:00Z` in Warsaw and `07:00Z` in London, so an instant-keyed answer stops matching its own slot
 * the moment the owner travels — a dose already given would read as unanswered and re-arm its alarm.
 * [at] exists only to place that alarm, and is recomputed rather than stored.
 */
data class DueDose(
    val courseId: String,
    val scheduledOn: LocalDate,
    val scheduledTime: LocalTime,
    /** When this slot falls, in the zone it was derived in. Computed, never written to a row. */
    val at: Instant,
)

/**
 * The doses [course] has due within [window], resolved wall-clock in [zone].
 *
 * The window is clamped to `[max(startOn, today), endOn ?: ∞]` — the course cannot be due before it
 * starts, [DoseWindow] has already refused to open before today, and an open course (`endOn == null`)
 * simply has no upper bound of its own. A course with **no times has no slots, ever**: scheduling is
 * optional per course (ADR-0002), and an empty schedule is a course the owner records doses against
 * by hand rather than one due at some default hour this function invented.
 *
 * **Shortening a course drops its future due doses and touches no recorded one**, which is not a code
 * path here but a consequence of deriving: `endOn` moves, the clamp moves with it, and the rows in
 * `doses` are untouched because nothing ever wrote a future one.
 *
 * The awkward days are `java.time`'s answer rather than ours, which is why ADR-0003 chose it — and
 * why the test asserts it instead of trusting it. A slot landing in a **spring-forward gap** exists
 * exactly once, pushed forward by the length of the gap (02:30 becomes 03:30 where the hour is
 * skipped); a slot landing in a **fall-back overlap** exists exactly once, at the earlier of the two
 * offsets. Never zero times, never twice — either would be a dose the owner is asked for twice or not
 * at all.
 *
 * @param times the course's own time rows. Order does not matter; the result is chronological.
 */
fun dueDoses(
    course: MedicationCourseEntity,
    times: List<MedicationTimeEntity>,
    window: DoseWindow,
    zone: ZoneId,
): List<DueDose> {
    if (times.isEmpty()) return emptyList()

    val first = maxOf(window.from, course.startOn)
    val last = course.endOn?.let { minOf(window.through, it) } ?: window.through
    if (first.isAfter(last)) return emptyList()

    // Sorted so the day's slots read in clock order, distinct as a belt to the unique index's braces:
    // `(courseId, time)` already makes "08:00 twice" impossible in the database.
    val clockTimes = times.map { it.time }.distinct().sorted()

    // Kotlin note: `generateSequence` is lazy — the days are produced one at a time and `takeWhile`
    // ends it, so an open course does not try to enumerate an unbounded range. The window's own
    // `through` is what bounds an open course; the clamp above only ever shortens it.
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .flatMap { day ->
            clockTimes.asSequence().map { time ->
                DueDose(
                    courseId = course.id,
                    scheduledOn = day,
                    scheduledTime = time,
                    at = day.atTime(time).atZone(zone).toInstant(),
                )
            }
        }.toList()
}

/**
 * Whether this recorded row is the answer to [slot] — **matched on the local key, not on [DueDose.at]**
 * (ADR-0002).
 *
 * An ad-hoc dose has both halves null and so answers nothing, which is right: it is a dose that
 * happened, not an answer to a slot the app derived.
 */
fun DoseEntity.answers(slot: DueDose): Boolean =
    courseId == slot.courseId &&
        scheduledOn == slot.scheduledOn &&
        scheduledTime == slot.scheduledTime

/**
 * A derived slot with whatever the owner said about it — what the medication screen renders.
 *
 * [recorded] being null means **unanswered**, and it is never rendered as a miss (ADR-0026,
 * ADR-0001): nobody has said anything about this slot yet, which for a slot later today is the
 * ordinary state of every dose in the app. The type carries no `missed` property for the same reason
 * `DoseStatus` has no `MISSED` value.
 */
data class ScheduledDose(
    val course: MedicationCourseEntity,
    val due: DueDose,
    val recorded: DoseEntity?,
) {
    val isAnswered: Boolean get() = recorded != null
}

/**
 * Pairs every slot in [slots] with the row that answers it, if any.
 *
 * A plain function over two lists rather than a query, because the slots are not in the database to
 * join against — the "join" between a row that exists and a slot that does not is exactly what
 * ADR-0002 makes the app's own work.
 */
fun scheduledDoses(
    course: MedicationCourseEntity,
    slots: List<DueDose>,
    recorded: List<DoseEntity>,
): List<ScheduledDose> =
    slots.map { slot ->
        ScheduledDose(
            course = course,
            due = slot,
            recorded = recorded.firstOrNull { it.answers(slot) },
        )
    }
