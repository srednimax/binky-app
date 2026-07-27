package app.binky.tracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras

/**
 * Settings, reached from More. A detail screen, the same shape as the archived bunnies list.
 *
 * The weight display unit, and the way in to backup and restore. ADR-0013's language switcher lands
 * here too, with the Polish translation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold has already padded past the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeightUnitSetting(unit = state.unit, onSelect = viewModel::setUnit)
            HorizontalDivider()
            BackupSetting(onOpen = onOpenBackup)

            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                SampleDataSetting(
                    outcome = state.sampleData,
                    onSeed = viewModel::seedSampleData,
                    onDismiss = viewModel::clearSampleDataOutcome,
                )
            }
        }
    }
}

/**
 * kg or grams — **display only**. Entry is in grams whatever this says, because that is what a scale
 * reads out, and changes are always shown in grams whichever is picked (house rule).
 */
@Composable
private fun WeightUnitSetting(
    unit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.settings_weight_unit), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    WeightUnit.KILOGRAMS -> R.string.settings_unit_kilograms
                                    WeightUnit.GRAMS -> R.string.settings_unit_grams
                                },
                            ),
                        )
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_weight_unit_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The way in to export, restore, and every recovery copy on the phone (ADR-0005).
 *
 * A row rather than a section: backup carries a destructive action and its own terminal screen, and
 * a "Replace everything" button does not belong on the screen the owner opened to change a unit.
 */
@Composable
private fun BackupSetting(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_backup_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SampleDataSetting(
    outcome: SampleDataOutcome?,
    onSeed: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.settings_sample_data), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_sample_data_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onSeed) { Text(stringResource(R.string.settings_sample_data_action)) }
    }

    if (outcome != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                Text(
                    stringResource(
                        when (outcome) {
                            SampleDataOutcome.SEEDED -> R.string.settings_sample_data_seeded
                            SampleDataOutcome.ALREADY_PRESENT -> R.string.settings_sample_data_present
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}
