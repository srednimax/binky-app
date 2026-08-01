package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.ui.bunny.dateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * **When was it done?** — back-datable on the same terms as Phase 2 entry.
 *
 * Defaults to today, the past is allowed, and the future is **refused with the reason stated**
 * rather than quietly greyed out in the picker. That is the one deliberate departure from
 * `RecordedAtField`, which does grey it out: a weighing carries a time as well as a date and has to
 * re-check the whole instant anyway, where a completion is a bare day and an owner who taps
 * tomorrow deserves the sentence explaining why not.
 *
 * Accepting one would be worse than untidy. The next occurrence is scheduled from the completion, so
 * a date the owner has not reached yet pushes the whole schedule out by the mistake *plus* the
 * interval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteCareDialog(
    reminderLabel: String,
    onConfirm: (LocalDate, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.care_complete_title),
    initialDate: LocalDate = LocalDate.now(),
    initialNote: String = "",
) {
    // Kotlin note: `LocalDate` is not one of the types `rememberSaveable` can put in a Bundle, so
    // the epoch day is what survives a rotation and the date is derived from it.
    var epochDay by rememberSaveable { mutableLongStateOf(initialDate.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val completedOn = LocalDate.ofEpochDay(epochDay)
    val today = remember { LocalDate.now() }
    val inFuture = completedOn.isAfter(today)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = reminderLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.care_complete_when),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = dateLabel(completedOn),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickingDate = true }) {
                        Text(stringResource(R.string.recorded_at_pick_date))
                    }
                }
                if (inFuture) {
                    Text(
                        text = stringResource(R.string.care_complete_future),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.care_complete_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.care_complete_note)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(completedOn, note.ifBlank { null }) },
                enabled = !inFuture,
            ) { Text(stringResource(R.string.care_complete_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingDate) {
        // UTC midnight throughout, which is what the Material date picker works in — the same
        // convention `RecordedAtField` and the calendar hand-off use for a bare date.
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = completedOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            epochDay =
                                Instant
                                    .ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toEpochDay()
                        }
                        pickingDate = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
