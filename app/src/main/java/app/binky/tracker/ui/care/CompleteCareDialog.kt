package app.binky.tracker.ui.care

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.ChangeableValueRow
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FormSection
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.NoteField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * **When was it done?** — back-datable on the same terms as Phase 2 entry.
 *
 * Defaults to today, the past is allowed, and the future is **refused with the reason stated**
 * rather than quietly greyed out in the picker. That is the one deliberate departure from
 * [app.binky.tracker.ui.common.RecordedAtField], which does grey it out: a weighing carries a time as
 * well as a date and has to re-check the whole instant anyway, where a completion is a bare day and
 * an owner who taps tomorrow deserves the sentence explaining why not.
 *
 * Accepting one would be worse than untidy. The next occurrence is scheduled from the completion, so
 * a date the owner has not reached yet pushes the whole schedule out by the mistake *plus* the
 * interval.
 *
 * Built as `RecordDoseDialog`'s twin, because it asks the same three things: **the reminder's name is
 * the dialog's subject line** rather than a line of body text — "Complete" alone cannot say *which* — the
 * date sits in the section shape `RecordedAtField` draws, and the note takes its placeholder as its
 * label, since one more heading in a dialog this size would out-number the fields.
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

    BinkyDialog(
        title = title,
        subject = reminderLabel,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(completedOn, note.ifBlank { null }) },
                enabled = !inFuture,
            ) { Text(stringResource(R.string.care_complete_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        // The card carries the row's own insets, so it takes none of its own — the same reason
        // `RecordedAtField` is built this way, and this is that field with the time left off.
        FormSection(
            title = stringResource(R.string.care_complete_when),
            contentPadding = PaddingValues(vertical = Spacing.hair),
            spacing = 0.dp,
        ) {
            ChangeableValueRow(
                value = dateLabel(completedOn),
                // "Change" on screen; the old wording stays as what a screen reader hears, where
                // there is no value in view to disambiguate it.
                description = stringResource(R.string.recorded_at_pick_date),
                onChange = { pickingDate = true },
            )
            // The help text explains how to back-date, so it goes with the control that can.
            HelpText(
                text = stringResource(R.string.care_complete_help),
                modifier =
                    Modifier.padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.snug,
                    ),
            )
            if (inFuture) {
                ErrorText(
                    text = stringResource(R.string.care_complete_future),
                    modifier =
                        Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.snug),
                )
            }
        }

        NoteField(
            value = note,
            onValueChange = { note = it },
            placeholder = stringResource(R.string.care_complete_note),
        )
    }

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
