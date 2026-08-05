package app.binky.tracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A **date-only** picker over a nullable [Instant].
 *
 * Written for the date printed on a scanned document, which is unlike every other date in the app:
 * it may genuinely be unknown, so it starts on today without claiming today is the answer, and
 * *clearing* it is a menu action beside the one that opens this rather than a third button in here
 * — a dialog with OK, Cancel and Clear makes the owner read three words to find out which two mean
 * "stop".
 *
 * **The chosen day is stored as local midnight, not UTC midnight.** The pickers elsewhere work in
 * UTC because they feed `LocalDate` columns, which carry no zone at all; this feeds an `Instant`
 * rendered back through a system-zone formatter, and UTC midnight renders as the *previous* day
 * everywhere west of Greenwich. What the owner picked is what they have to read back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantDatePickerDialog(
    initial: Instant?,
    @StringRes titleRes: Int,
    onPicked: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val todayUtc = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val initialDay = remember(initial, zone) { initial?.atZone(zone)?.toLocalDate() ?: today }

    val pickerState =
        rememberDatePickerState(
            // The picker speaks UTC milliseconds whatever the value means, so the day goes in as UTC
            // and comes back out as UTC; the conversion to a real instant happens once, on confirm,
            // and in the system zone.
            initialSelectedDateMillis = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    // A document dated in the future is a typo, not a record.
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc

                    override fun isSelectableYear(year: Int) = year <= today.year
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = pickerState.selectedDateMillis
                    // Nothing selected closes rather than sticking: the picker opens with a day
                    // already chosen, so null means the owner cleared it inside the calendar.
                    if (picked == null) {
                        onDismiss()
                    } else {
                        val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        onPicked(day.atStartOfDay(zone).toInstant())
                    }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState, title = { Text(stringResource(titleRes)) })
    }
}
