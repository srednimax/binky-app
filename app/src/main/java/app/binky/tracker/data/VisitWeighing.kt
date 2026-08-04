package app.binky.tracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// The two rules a **visit-recorded weighing** obeys, both pure so they are provable without a
// device. A visit happens on a `LocalDate` and a weighing is an `Instant` (ADR-0017), so something
// has to choose the moment; that choice and the one that protects the row afterwards are the same
// subject, which is why they share a file.

/**
 * **Noon**, and it is not an arbitrary hour.
 *
 * Midnight is the obvious choice and the wrong one: it is the instant a DST spring-forward can
 * delete, so `visitedOn.atStartOfDay(zone)` on the wrong March morning silently lands on the next
 * valid instant — an hour into a *different* rule than the one written here. Noon exists in every
 * zone on every day, and it puts the chart point in the middle of the right day rather than on its
 * edge, where a reader has to know the app's convention to tell which day it belongs to.
 */
private val VISIT_WEIGHING_TIME: LocalTime = LocalTime.NOON

/**
 * When a weighing recorded on a visit is stamped: **`min(noon on visitedOn, now)`**.
 *
 * Clamped to now because a visit dated *today* and typed at 09:00 must not write a weighing three
 * hours into the future — the weight series is the one place the app makes a safety claim, and a
 * reading from the future would sit at the head of it (ADR-0021's total order) while describing
 * something that has not happened.
 *
 * Truncated to the minute for the same reason the entry form is: ADR-0021's collision rule is an
 * **exact** instant match, so the two paths have to agree on what "exact" means or a visit weighing
 * could never collide with a typed one and the rule below would have nothing to protect.
 *
 * [zone] and [now] are parameters rather than reads of the ambient clock, which is what lets the
 * DST case be a test instead of a March morning.
 */
fun visitWeighingAt(
    visitedOn: LocalDate,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): Instant {
    val noon = visitedOn.atTime(VISIT_WEIGHING_TIME).atZone(zone).toInstant()
    return minOf(noon, now).truncatedTo(ChronoUnit.MINUTES)
}

/**
 * The clashing rows the weight entry form is allowed to **overwrite** (ADR-0021's amendment).
 *
 * ADR-0021's same-instant resolver defaults to *replace*, and it was written when a weighing had
 * exactly one owner. A visit-tagged row has two: replacing it would leave the visit displaying a
 * figure the vet never recorded, with the row still carrying its `visitId`. Every visit on a day
 * lands at the same noon, so this is reachable by accident rather than by contrivance.
 *
 * The unique index does not catch it — that stops two rows claiming one visit, not one row being
 * quietly rewritten. So the destructive option is **absent** rather than merely not the default, and
 * this is the one function that decides it.
 */
fun List<WeightEntity>.replaceable(): List<WeightEntity> = filter { it.visitId == null }
