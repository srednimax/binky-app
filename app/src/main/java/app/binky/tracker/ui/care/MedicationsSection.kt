package app.binky.tracker.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.ScheduledDose
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.weight.timeLabel
import java.time.ZoneId

/**
 * The medication half of the Care & Meds tab (PLAN 5e) — **today's doses, then the courses that
 * generate them**, which is `3a`'s title for the whole route.
 *
 * **It goes first on the tab**, and that is a decision about time rather than about importance. A
 * dose has a clock time *today*; a nail trim has a week either side of its date. A bunny on nothing
 * pays a header, a sentence and a button for it, which is the same rent the Visits section has paid
 * since 1.2 — and the fixed order is worth more than the three lines, because a screen that
 * rearranges itself depending on what the owner has is a screen they have to re-read every time.
 *
 * Kotlin/Compose note: this is an extension on `LazyListScope`, not a composable — it *adds items*
 * to the tab's one `LazyColumn` rather than nesting a second scrolling list inside it. Nested lazy
 * lists in the same direction are the one thing Compose genuinely cannot lay out.
 */
fun LazyListScope.medicationsSection(
    state: CareUiState,
    onAddCourse: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onAnswer: (ScheduledDose, DoseStatus) -> Unit,
) {
    // Only when there is a day to show. "Nothing due today" under a bunny on no medication is a
    // sentence about the absence of a feature they are not using.
    if (state.todaysDoses.isNotEmpty()) {
        sectionHeading("today-heading", R.string.med_today_heading, first = true)

        itemsIndexed(
            state.todaysDoses,
            key = { _, dose -> "dose-${dose.course.id}-${dose.due.scheduledTime}" },
        ) { index, dose ->
            GroupedCardItem(index = index, count = state.todaysDoses.size) {
                DoseRow(
                    dose = dose,
                    readOnly = state.readOnly,
                    onOpenCourse = { onOpenCourse(dose.course.id) },
                    onAnswer = { status -> onAnswer(dose, status) },
                )
            }
        }
    }

    sectionHeading("courses-heading", R.string.med_heading, first = state.todaysDoses.isEmpty())

    if (state.courses.isEmpty()) {
        item(key = "courses-empty") { EmptySection(stringResource(R.string.med_empty)) }
    }

    // Prefixed like the visits are: `LazyColumn` keys are unique across the whole list, and a course
    // and a visit can be two rows sharing one UUID space.
    itemsIndexed(state.courses, key = { _, row -> "course-${row.id}" }) { index, row ->
        GroupedCardItem(index = index, count = state.courses.size) {
            CourseRow(row = row, onOpen = { onOpenCourse(row.id) })
        }
    }

    // No add / answer affordances at all in the archived scope, rather than affordances that refuse
    // when tapped (ADR-0004).
    if (!state.readOnly) {
        item(key = "courses-add") {
            Spacer(Modifier.height(Spacing.tight))
            // **Filled, and directly under the list it appends to** (`3a`). This is the one action
            // this half of the tab exists for; *Add a reminder* below it is a text button because
            // routine care is the quieter of the two.
            Button(
                onClick = onAddCourse,
                modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                shape = RoundedCornerShape(RecordButtonRadius),
            ) {
                Text(stringResource(R.string.med_add_course))
            }
        }
    }

    item(key = "med-disclaimer") {
        Spacer(Modifier.height(Spacing.tight))
        MedicationDisclaimer(modifier = Modifier.padding(horizontal = Spacing.hair))
    }
}

/**
 * **What the record is**, stated once and permanently under the course list (ADR-0026).
 *
 * Not a dialog — dismissed once and then never seen again, and ADR-0006 keeps that path for
 * permissions — and not a warning. It is one quiet line in the app's own voice (ADR-0012) saying
 * what this screen holds and what it does not do, because the owner cannot read an ADR and the rule
 * is worth nothing if it only binds our copy.
 *
 * It sits with the courses rather than at the foot of the route, because it is a footnote to *them*:
 * the sections below it are nail trims and vet visits, which it says nothing about.
 *
 * It is also the cheapest answer to a Play reviewer looking at medication screenshots on a
 * Lifestyle app: the disclaimer is *in* the screenshot.
 */
@Composable
fun MedicationDisclaimer(modifier: Modifier = Modifier) {
    HelpText(text = stringResource(R.string.med_disclaimer), modifier = modifier)
}

/**
 * One of today's derived slots: **what it is on one line, and what to say about it on the right**.
 *
 * `3a`'s first change, and the one that paid for the rest — a dose was a whole card with an internal
 * divider and two text buttons, so three of them filled the screen. As a 64dp row with the answers
 * inline, eight do. The name and the amount share the title line ("Metacam · 0.3 ml"), which is what
 * let the row come down to two lines at all.
 *
 * **An answered slot collapses to a marker**, so due and done are distinguishable at a glance rather
 * than by reading. It stays correctable: the row opens the course, where the dose history and its
 * edit and delete live — `MedicationRepository.answer` has always treated a second answer as the
 * owner changing their mind rather than as a constraint violation.
 *
 * **An unanswered slot renders no state at all** — not "missed", not "overdue", not a colour. That
 * is the whole of ADR-0026 on this row: nobody has said anything about this dose yet, and for a slot
 * later today that is the ordinary condition of every dose in the app.
 */
@Composable
private fun DoseRow(
    dose: ScheduledDose,
    readOnly: Boolean,
    onOpenCourse: () -> Unit,
    onAnswer: (DoseStatus) -> Unit,
) {
    val resources = LocalResources.current
    val recorded = dose.recorded
    val due = stringResource(R.string.med_dose_due, timeLabel(dose.due.scheduledTime))
    // Kotlin note: `let` is an *inline* function, so composable calls are legal inside its lambda —
    // the body is compiled into this function rather than into a separate object.
    val answered =
        recorded?.let {
            stringResource(
                R.string.med_dose_answered,
                doseStatusLabel(it.status),
                timeLabel(it.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()),
            )
        }

    ListRow(
        title = courseTitle(dose.course.name, dose.course.doseAmount),
        subtitle = if (answered == null) due else resources.getString(R.string.row_pair, due, answered),
        // An answered row opens the course so the answer can be corrected; an unanswered one has its
        // two buttons and must not also be a target, or a mis-tap either side of them navigates away
        // from the thing the owner was about to answer.
        onClick = if (recorded != null) onOpenCourse else null,
        trailing = {
            when {
                recorded != null -> DoseMarker(recorded.status)
                readOnly -> Unit
                else -> {
                    TextButton(onClick = { onAnswer(DoseStatus.GIVEN) }) {
                        Text(stringResource(R.string.dose_status_given))
                    }
                    TextButton(onClick = { onAnswer(DoseStatus.SKIPPED) }) {
                        Text(
                            text = stringResource(R.string.dose_status_skipped),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

/**
 * The hay marker on an answered dose: **somebody said something about this one**.
 *
 * Hay rather than apricot, for [app.binky.tracker.ui.common.TagChip]'s reason — apricot is what the
 * *app* raises, and an answer is something the owner recorded.
 *
 * Two glyphs, because a tick beside *Skipped* would read as "yes, given". A skip is a recorded
 * decision and not a failure (ADR-0026) — an owner told to stop after four days has recorded a fact
 * — so it takes the neutral bar rather than a cross, and both sit in the same hay circle at the same
 * weight. The word is in the line beside it either way; the marker only says *answered*.
 */
@Composable
private fun DoseMarker(status: DoseStatus) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(DoseMarkerSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (status) {
                DoseStatus.GIVEN ->
                    Icon(
                        imageVector = Icons.Filled.Check,
                        // The row's own subtitle says "Given at 6:47 PM" in words, so describing the
                        // glyph would only make a screen reader say the answer twice.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(DoseMarkerGlyph),
                    )
                // Drawn rather than taken from the icon set, and both halves of that matter. There
                // is no minus in Compose's *core* icons — `material-icons-extended` is deliberately
                // not a dependency — and the nearest one that is, a cross, would read as "no".
                DoseStatus.SKIPPED ->
                    Box(
                        modifier =
                            Modifier
                                .size(width = DoseMarkerBar, height = DoseMarkerBarWeight)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSecondaryContainer),
                    )
            }
        }
    }
}

private val DoseMarkerSize = 24.dp

private val DoseMarkerGlyph = 16.dp

/** The skipped marker's neutral bar — the same width the tick occupies, two dp thick. */
private val DoseMarkerBar = 10.dp

private val DoseMarkerBarWeight = 2.dp

/**
 * One course: what it is and how much, then its schedule and what is next on the line beneath.
 *
 * The row opens the course, where the dose history, the ad-hoc path, *End the course* and — since
 * Phase 7 — *Delete* all live. Nothing destructive is reachable from this list any more; a 64dp row
 * has nowhere to put a button, and the chevron already goes to the screen that owns the decision.
 *
 * The next dose is stated in the same voice whether it is four hours away or five weeks past. An
 * ended course is a finished treatment, not a failure to be coloured (ADR-0026).
 */
@Composable
private fun CourseRow(
    row: CourseRow,
    onOpen: () -> Unit,
) {
    val resources = LocalResources.current
    val schedule = courseScheduleLabel(row.times)
    val next = nextDoseLabel(row.next)

    ListRow(
        title = courseTitle(row.course.name, row.course.doseAmount),
        subtitle = if (next == null) schedule else resources.getString(R.string.row_pair, schedule, next),
        onClick = onOpen,
        trailing = { Chevron() },
    )
}

/**
 * "Metacam · 0.3 ml" — the name and the amount on **one** line.
 *
 * `3a`'s fifth change, and the one that made the 64dp row possible: stacking the name and the dose
 * cost a third line on every row for a fact that is two words long. The amount is free text the
 * owner typed exactly as the vet said it, so it is appended rather than formatted (ADR-0026 — Binky
 * never reads it).
 */
@Composable
private fun courseTitle(
    name: String,
    doseAmount: String,
): String = if (doseAmount.isBlank()) name else stringResource(R.string.row_pair, name, doseAmount)

/**
 * **Deleting a course counts what it destroys** (PLAN 5e).
 *
 * `DoseEntity` is `CASCADE`, so one tap can take forty rows saying what was actually given to a sick
 * rabbit — after weights, the most clinically meaningful history this app holds. One confirmation
 * rather than ADR-0004's two-stage ceremony, which is calibrated to a bunny's whole life; but it
 * names the number with `<plurals>` exactly as the destroyed bucket does.
 *
 * And an open course offers **end course instead** in the same dialog, because that is usually what
 * an owner means by "we have finished with this one": the operation already exists (`endOn = today`)
 * and it keeps every dose.
 *
 * **Hosted by `MedicationCourseScreen` since Phase 7**, where the list row that used to raise it now
 * only chevrons to.
 */
@Composable
fun DeleteCourseDialog(
    courseName: String,
    doseCount: Int,
    open: Boolean,
    onConfirm: () -> Unit,
    onEndInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(stringResource(R.string.med_delete_body, courseName))
                if (doseCount > 0) {
                    Text(pluralStringResource(R.plurals.med_delete_doses, doseCount, doseCount))
                }
                if (open) {
                    HelpText(stringResource(R.string.med_delete_end_help))
                    OutlinedButton(onClick = onEndInstead, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.med_delete_end_action))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
