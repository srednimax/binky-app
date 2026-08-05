package app.binky.tracker.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.ScheduledDose
import app.binky.tracker.ui.reminders.DoseDeliveryLine
import app.binky.tracker.ui.weight.timeLabel
import java.time.ZoneId

/**
 * The medication half of the Care & Meds tab (PLAN 5e).
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
    onOpenCourse: (CourseRow) -> Unit,
    onDeleteCourse: (CourseRow) -> Unit,
    onAnswer: (ScheduledDose, DoseStatus) -> Unit,
) {
    item { SectionHeader(stringResource(R.string.med_heading)) }

    // **Once, and only when something is actually armed.** PLAN 5e reads "each row … carrying 5a's
    // delivery state"; taken literally that is the same four-sentence paragraph under every course.
    // The state is a fact about the *phone*, not about a course, so it belongs once — and it stays
    // away entirely when no course has times with reminders on, because a warning about how
    // reliably Android wakes the app is noise to an owner who has scheduled nothing to wake it for.
    if (!state.readOnly && state.anyDoseReminders) {
        item { DoseDeliveryLine() }
    }

    // Only when there is a day to show. "Nothing due today" under a bunny on no medication is a
    // sentence about the absence of a feature they are not using.
    if (state.todaysDoses.isNotEmpty()) {
        item { Text(text = stringResource(R.string.med_today_heading), style = MaterialTheme.typography.titleSmall) }

        items(state.todaysDoses, key = { "dose-${it.course.id}-${it.due.scheduledTime}" }) { dose ->
            DoseSlotCard(
                dose = dose,
                readOnly = state.readOnly,
                onAnswer = { status -> onAnswer(dose, status) },
            )
        }
    }

    // No add / answer / delete affordances at all in the archived scope, rather than affordances
    // that refuse when tapped (ADR-0004).
    if (!state.readOnly) {
        item {
            Button(onClick = onAddCourse) { Text(stringResource(R.string.med_add_course)) }
        }
    }

    if (state.courses.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.med_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Prefixed like the visits are: `LazyColumn` keys are unique across the whole list, and a course
    // and a visit can be two rows sharing one UUID space.
    items(state.courses, key = { "course-${it.id}" }) { row ->
        CourseCard(
            row = row,
            readOnly = state.readOnly,
            onOpen = { onOpenCourse(row) },
            onDelete = { onDeleteCourse(row) },
        )
    }

    item { MedicationDisclaimer() }
}

/**
 * **What the record is**, stated once and permanently under the course list (ADR-0026).
 *
 * Not a dialog — dismissed once and then never seen again, and ADR-0006 keeps that path for
 * permissions — and not a warning. It is one quiet line in the app's own voice (ADR-0012) saying
 * what this screen holds and what it does not do, because the owner cannot read an ADR and the rule
 * is worth nothing if it only binds our copy.
 *
 * It is also the cheapest answer to a Play reviewer looking at medication screenshots on a
 * Lifestyle app: the disclaimer is *in* the screenshot.
 */
@Composable
fun MedicationDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.med_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * One of today's derived slots, with the two answers it can be given.
 *
 * **Both buttons stay after an answer**, which is the correction path: `MedicationRepository.answer`
 * treats a second answer as the owner changing their mind rather than as a constraint violation, so
 * there is nothing to undo first.
 *
 * An unanswered slot renders **no state at all** — not "missed", not "overdue", not a colour. That
 * is the whole of ADR-0026 on this row: nobody has said anything about this dose yet, and for a slot
 * later today that is the ordinary condition of every dose in the app.
 */
@Composable
private fun DoseSlotCard(
    dose: ScheduledDose,
    readOnly: Boolean,
    onAnswer: (DoseStatus) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text =
                    stringResource(
                        R.string.med_dose_row,
                        dose.course.name,
                        timeLabel(dose.due.scheduledTime),
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (dose.course.doseAmount.isNotEmpty()) {
                Text(
                    text = dose.course.doseAmount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dose.recorded?.let { recorded ->
                Text(
                    text =
                        stringResource(
                            R.string.med_dose_answered,
                            doseStatusLabel(recorded.status),
                            timeLabel(recorded.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAnswer(DoseStatus.GIVEN) }) {
                        Text(stringResource(R.string.dose_status_given))
                    }
                    TextButton(onClick = { onAnswer(DoseStatus.SKIPPED) }) {
                        Text(stringResource(R.string.dose_status_skipped))
                    }
                }
            }
        }
    }
}

/**
 * One course: what it is, how much, when it is taken, and what is next.
 *
 * The whole card opens the course, where the dose history and the ad-hoc path live. *Delete* stays
 * on the row because it is the one action that belongs to the list — everything else is about a
 * course the owner has already decided to look at.
 */
@Composable
private fun CourseCard(
    row: CourseRow,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = row.course.name, style = MaterialTheme.typography.titleMedium)
            if (row.course.doseAmount.isNotEmpty()) {
                Text(text = row.course.doseAmount, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = courseScheduleLabel(row.times),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Stated in the same voice whether it is four hours away or five weeks past — an ended
            // course is a finished treatment, not a failure to be coloured (ADR-0026).
            nextDoseLabel(row.next)?.let { next ->
                Text(
                    text = next,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!readOnly) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

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
 */
@Composable
fun DeleteCourseDialog(
    pending: PendingCourseDelete,
    onConfirm: () -> Unit,
    onEndInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val open = pending.row.next !is DoseNext.Ended

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.med_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.med_delete_body, pending.row.course.name))
                if (pending.doseCount > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.med_delete_doses,
                            pending.doseCount,
                            pending.doseCount,
                        ),
                    )
                }
                if (open) {
                    Text(
                        text = stringResource(R.string.med_delete_end_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
