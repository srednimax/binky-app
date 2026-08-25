package app.binky.tracker.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.CompletedCare
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.care.CareDue
import app.binky.tracker.ui.care.careDue
import app.binky.tracker.ui.care.careDueLabel
import app.binky.tracker.ui.care.careGapLabel
import app.binky.tracker.ui.care.careReminderLabel
import app.binky.tracker.ui.care.careTypeLabelRes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/*
 * How a timeline row says what it is.
 *
 * Kept out of `EventsScreen.kt` for the reason `CareLabels.kt` is kept out of the Care screen: the
 * four kinds each resolve their name a different way, and a `when` over the union is easier to read
 * — and to keep exhaustive — with nothing else in the frame.
 *
 * **The row never names its own kind in so many words.** "Event · 14 March" would spend a line
 * telling the owner which table a row came out of, which is the app's business rather than theirs.
 * What distinguishes the kinds instead is the shape of the second line: a due date says *Due*, a
 * completion says *Done*, a visit names the vet, and an event — the only row the owner typed
 * themselves — just says when.
 */

/** The row's first line: the thing's own name. */
@Composable
fun timelineTitle(entry: TimelineEntry): String =
    when (entry) {
        is TimelineEntry.Event -> entry.event.label
        is TimelineEntry.VetVisit -> entry.details.visit.reason
        is TimelineEntry.CareDone -> completedCareLabel(entry.completion)
        is TimelineEntry.CareDue -> careReminderLabel(entry.scheduled.reminder)
    }

/**
 * The row's second line: when, and — for everything but an event — what kind of *when* it is.
 *
 * The upcoming half speaks in relative terms and the past half in dates, which is the split
 * [CareDue] already makes for one reminder, applied to the whole agenda: something still owed is a
 * decision about this week, and something that happened is a date to look up. [today] is passed in
 * rather than read here, so a scrolling list cannot get two answers from two recompositions.
 */
@Composable
fun timelineSubtitle(
    entry: TimelineEntry,
    today: LocalDate,
): String =
    when (entry) {
        is TimelineEntry.Event ->
            if (entry.outstanding(today)) relativeDayLabel(entry.on, today) else dateLabel(entry.on)
        is TimelineEntry.VetVisit ->
            stringResource(
                R.string.row_pair,
                // The clinic when the visit named one, and the plain word when it did not — a visit
                // may legitimately name no vet, and "Vet visit · 14 March" still reads as one.
                entry.details.vetName ?: stringResource(R.string.timeline_vet_visit),
                dateLabel(entry.on),
            )
        is TimelineEntry.CareDone ->
            stringResource(R.string.row_pair, stringResource(R.string.timeline_care_done), dateLabel(entry.on))
        // Reuses the Care screen's own wording verbatim, including "3 weeks overdue" — two screens
        // saying the same thing about one reminder in two different phrasings would read as two
        // different claims.
        is TimelineEntry.CareDue -> careDueLabel(careDue(entry.on, today))
    }

/** A completion's name. [CompletedCare] carries the reminder's label and type rather than the row. */
@Composable
private fun completedCareLabel(completion: CompletedCare): String =
    completion.label ?: completion.type?.let { stringResource(careTypeLabelRes(it)) } ?: ""

/**
 * "Today", "Tomorrow", "In 3 days" — an event's own voice, without care's *Due*.
 *
 * Borrows [careGapLabel] rather than minting a parallel set of plurals: "3 days" is the same phrase
 * whether it stands between today and a nail trim or between today and a rabbit's adoption day, and
 * one copy is one thing to translate.
 */
@Composable
private fun relativeDayLabel(
    on: LocalDate,
    today: LocalDate,
): String =
    when (val due = careDue(on, today)) {
        CareDue.Today -> stringResource(R.string.event_when_today)
        CareDue.Tomorrow -> stringResource(R.string.event_when_tomorrow)
        is CareDue.In -> stringResource(R.string.event_when_in, careGapLabel(due.gap))
        // Unreachable: an event only reaches this function from the upcoming half, which it joins by
        // being today or later. Rendering the date beats an empty line on a hand-edited database.
        CareDue.Yesterday, is CareDue.Overdue -> dateLabel(on)
    }

/**
 * A month heading: "March 2026".
 *
 * Kotlin note: the pattern letter is **`LLLL`, not `MMMM`** — the *standalone* month name. Several
 * languages inflect a month differently depending on whether it stands alone or sits in a date;
 * Polish writes "marzec" for the heading and "marca" inside "14 marca 2026". `MMMM` would give the
 * second one, which is the wrong word for a heading with no day in it.
 *
 * The year is always shown. A timeline that reaches back years is the normal case here, and a bare
 * "March" would make the reader work out which one from the rows.
 */
@Composable
fun timelineMonthLabel(month: YearMonth): String {
    // LocalConfiguration rather than LocalContext.resources: only the former recomposes when the
    // system locale changes (see `dateLabel`, which makes the same choice for the same reason).
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    return remember(month, formatter) { month.atDay(1).format(formatter) }
}
