package app.binky.tracker.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.weight.timeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * **When it happened** — defaults to now, back-dating allowed, the future refused with the reason
 * stated rather than silently clamped.
 *
 * Shared by the weight and observation forms because the rule is the same rule, and it is a rule the
 * app makes a promise about: a clamp would store a timestamp the owner did not choose. Two copies of
 * a date picker would be two places for that promise to drift.
 *
 * The wording differs between the two forms — a weighing is "when the bunny was on the scale", an
 * observation is "when you noticed it" — so the strings are parameters and none of the copy lives
 * here (ADR-0013).
 *
 * @param inFuture set by the form's own save check. The date picker below refuses future *days* on
 *   its own, but a time picker cannot express "not later than now on today's date", so the form
 *   re-checks the whole instant and this renders the answer.
 * @param enabled false renders the same two lines with **no way to change them** — the pickers are
 *   absent rather than present-and-refusing (ADR-0004's shape). Used by the weight form for a
 *   visit-recorded weighing, whose timestamp is derived from the visit's date (ADR-0017).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordedAtField(
    label: String,
    helpText: String,
    futureRejectedText: String,
    date: LocalDate,
    time: LocalTime,
    inFuture: Boolean,
    onDateChanged: (LocalDate) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    // The card carries the rows' own insets, so it takes no horizontal padding of its own — the
    // divider between the two has to run to the card's edge on the right, which it cannot do from
    // inside a padded column.
    FormSection(
        title = label,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = Spacing.hair),
        spacing = 0.dp,
    ) {
        ChangeableValueRow(
            value = dateLabel(date),
            // The button says only "Change": the value it sits beside already says *what* changes,
            // and "Change the date" next to a date is the label reading the value back. The old
            // wording stays as the screen-reader description, where there is no value in view to
            // disambiguate it.
            description = stringResource(R.string.recorded_at_pick_date),
            enabled = enabled,
            onChange = { pickingDate = true },
        )
        RowDivider()
        ChangeableValueRow(
            value = timeLabel(time),
            description = stringResource(R.string.recorded_at_pick_time),
            enabled = enabled,
            onChange = { pickingTime = true },
        )
        // The help text explains how to back-date, so it goes with the controls that can.
        if (enabled) {
            HelpText(
                text = helpText,
                modifier =
                    Modifier.padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.snug,
                    ),
            )
        }
        if (inFuture) {
            ErrorText(
                text = futureRejectedText,
                modifier =
                    Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.snug),
            )
        }
    }

    if (pickingDate) {
        val today = remember { LocalDate.now() }
        val todayUtc = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                // Something that has not happened yet is not a record. See [inFuture] above for the
                // half of this rule a date picker cannot express.
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc

                        override fun isSelectableYear(year: Int) = year <= today.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDateChanged(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
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

    if (pickingTime) {
        val context = LocalContext.current
        val pickerState =
            rememberTimePickerState(
                initialHour = time.hour,
                initialMinute = time.minute,
                is24Hour = DateFormat.is24HourFormat(context),
            )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChanged(LocalTime.of(pickerState.hour, pickerState.minute))
                        pickingTime = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
