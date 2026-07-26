package app.bunny.tracker.ui.weight

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bunny.tracker.R
import app.bunny.tracker.data.WeightUnit
import app.bunny.tracker.ui.appViewModelExtras
import app.bunny.tracker.ui.bunny.dateLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Add or edit one weighing — the route `NavigationKeys.kt` promised from Phase 1 and never built.
 *
 * A **detail** screen like the bunny editor: pushed onto the back stack with its own app bar, so the
 * shell's switcher and bottom bar step aside while it is open. The global "+" is deliberately not a
 * way in here — it stays observation-only (ADR-0015).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntryScreen(
    bunnyId: String,
    weightId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeightEntryViewModel =
        viewModel(
            key = "weight-entry-${weightId ?: "new"}",
            factory = WeightEntryViewModel.factory(bunnyId, weightId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.weight_entry_add_title else R.string.weight_entry_edit_title,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) } },
            // The shell's Scaffold is the one owner of window insets; padding here would double it.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GramsField(state = state, onGramsChanged = viewModel::onGramsChanged)
            RecordedAtField(
                state = state,
                onDateChanged = viewModel::onDateChanged,
                onTimeChanged = viewModel::onTimeChanged,
            )
        }
    }

    if (state.collision.isNotEmpty()) {
        CollisionDialog(
            count = state.collision.size,
            onReplace = viewModel::replaceExisting,
            onAddSecond = viewModel::addSecond,
            onDismiss = viewModel::cancelCollision,
        )
    }

    state.flagDrop?.let { drop ->
        TrendFlagDialog(
            bunnyName = state.bunnyName,
            drop = drop,
            unit = state.unit,
            onAcknowledge = viewModel::acknowledge,
            onDismiss = viewModel::dismissFlag,
        )
    }
}

/**
 * **Entry is always in grams**, whatever the display preference is set to — that is what a scale
 * reads out (house rule). When the owner reads kilograms, the conversion is echoed underneath rather
 * than the field switching unit, so what they type and what they see are never the same box.
 */
@Composable
private fun GramsField(
    state: WeightEntryUiState,
    onGramsChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.grams,
            onValueChange = onGramsChanged,
            label = { Text(stringResource(R.string.weight_grams_label)) },
            isError = state.gramsInvalid,
            supportingText = {
                when {
                    state.gramsInvalid -> Text(stringResource(R.string.weight_grams_required))
                    else -> Text(stringResource(R.string.weight_grams_help))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val grams = state.parsedGrams
        if (grams != null && state.unit == WeightUnit.KILOGRAMS) {
            Text(
                text = stringResource(R.string.weight_grams_as_kilograms, weightLabel(grams, WeightUnit.KILOGRAMS)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * When the bunny was on the scale. Defaults to now; **back-dating is allowed** and future timestamps
 * are refused with the reason stated rather than silently clamped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordedAtField(
    state: WeightEntryUiState,
    onDateChanged: (LocalDate) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.weight_recorded_at_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = dateLabel(state.date),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.weight_pick_date)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = timeLabel(state.time),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingTime = true }) { Text(stringResource(R.string.weight_pick_time)) }
        }
        Text(
            text = stringResource(R.string.weight_backdating_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.inFuture) {
            Text(
                text = stringResource(R.string.weight_future_rejected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (pickingDate) {
        val today = remember { LocalDate.now() }
        val todayUtc = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.date
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
                // A weighing that has not happened yet is not a record. The time picker cannot
                // express the same rule, so the form re-checks the whole instant on save.
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
                initialHour = state.time.hour,
                initialMinute = state.time.minute,
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

/**
 * The exact-timestamp collision prompt (ADR-0021), **defaulting to replace** — which is the
 * confirm button here, since that is the commonest correction by far: type `250`, watch the flag
 * fire, retype `2500` for the same minute.
 */
@Composable
private fun CollisionDialog(
    count: Int,
    onReplace: () -> Unit,
    onAddSecond: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_collision_title)) },
        text = { Text(pluralStringResource(R.plurals.weight_collision_body, count, count)) },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.weight_collision_replace)) } },
        dismissButton = {
            TextButton(onClick = onAddSecond) { Text(stringResource(R.string.weight_collision_add_second)) }
        },
    )
}
