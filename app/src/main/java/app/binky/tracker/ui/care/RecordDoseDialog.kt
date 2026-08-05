package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.ui.common.RecordedAtField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Recording a dose by hand: **what happened, when, and anything worth saying about it**.
 *
 * One dialog for both jobs — the ad-hoc record and the correction of one already recorded — because
 * they are the same three fields and two copies would be two places for the future check to drift.
 * The title and the initial values are what differ, and both are parameters (ADR-0013 keeps the copy
 * out of the composable that uses it).
 *
 * **An ad-hoc dose is normal, not an error** (ADR-0002). A rescue dose at 03:00, or last night's
 * 20:00 given at 00:30 after the slot had stopped existing: neither answers a derived slot, both are
 * real. That is why back-dating is the point here rather than a concession — and why the future is
 * refused, on the same terms as every other entry in this app: a moment the owner has not reached
 * cannot be something they did.
 *
 * The two statuses are offered as equals. *Skipped* is a recorded decision, not a failure to record
 * *Given* (ADR-0026).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDoseDialog(
    courseName: String,
    onConfirm: (DoseStatus, Instant, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.med_record_title),
    initialStatus: DoseStatus = DoseStatus.GIVEN,
    initialAt: Instant = Instant.now(),
    initialNote: String = "",
) {
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(initialAt, zone) { initialAt.atZone(zone) }

    // Kotlin note: neither `LocalDate` nor `LocalTime` is a type `rememberSaveable` can put in a
    // Bundle, so the epoch day and the second of the day are what survive a rotation and the two
    // values are derived from them — the same trick `CompleteCareDialog` uses for its bare date.
    var epochDay by rememberSaveable { mutableLongStateOf(initial.toLocalDate().toEpochDay()) }
    var secondOfDay by rememberSaveable { mutableIntStateOf(initial.toLocalTime().toSecondOfDay()) }
    var status by rememberSaveable { mutableStateOf(initialStatus.name) }
    var note by rememberSaveable { mutableStateOf(initialNote) }

    val date = LocalDate.ofEpochDay(epochDay)
    val time = LocalTime.ofSecondOfDay(secondOfDay.toLong())
    val recordedAt = remember(date, time, zone) { date.atTime(time).atZone(zone).toInstant() }
    // Read once per composition rather than per frame: the dialog is not a clock, and a *Record*
    // button that switched itself off as the minute rolled over would be a puzzle, not a guard.
    val now = remember { Instant.now() }
    val inFuture = recordedAt.isAfter(now)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(text = courseName, style = MaterialTheme.typography.titleSmall)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.med_record_status),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoseStatus.entries.forEach { entry ->
                            FilterChip(
                                selected = entry.name == status,
                                onClick = { status = entry.name },
                                label = { Text(doseStatusLabel(entry)) },
                            )
                        }
                    }
                }

                RecordedAtField(
                    label = stringResource(R.string.med_record_when),
                    helpText = stringResource(R.string.med_record_when_help),
                    futureRejectedText = stringResource(R.string.med_record_future),
                    date = date,
                    time = time,
                    inFuture = inFuture,
                    onDateChanged = { epochDay = it.toEpochDay() },
                    onTimeChanged = { secondOfDay = it.toSecondOfDay() },
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.med_record_note)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(DoseStatus.valueOf(status), recordedAt, note.ifBlank { null }) },
                enabled = !inFuture,
            ) { Text(stringResource(R.string.med_record_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
