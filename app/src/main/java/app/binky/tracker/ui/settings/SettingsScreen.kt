package app.binky.tracker.ui.settings

import android.text.format.Formatter
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
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.sharePreservedCopy
import app.binky.tracker.ui.weight.dateTimeLabel

/**
 * Settings, reached from More. A detail screen, the same shape as the archived bunnies list.
 *
 * Deliberately minimal for 2c: the weight display unit, and ADR-0007's preserved copies. Backup
 * settings (Phase 3) and ADR-0013's language switcher land here later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            PreservedCopies(
                copies = state.preserved,
                onShare = { copy -> context.sharePreservedCopy(copy) },
                onDelete = viewModel::requestDelete,
            )

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

    state.pendingDelete?.let { copy ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.settings_preserved_delete_title)) },
            text = { Text(stringResource(R.string.settings_preserved_delete_body, copy.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
 * ADR-0007's copies. Listing them is what makes the wipe screen's promise checkable: it says a copy
 * was taken, and this is where the owner sees that it exists and gets it off the phone.
 */
@Composable
private fun PreservedCopies(
    copies: List<PreservedCopy>,
    onShare: (PreservedCopy) -> Unit,
    onDelete: (PreservedCopy) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.settings_preserved_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_preserved_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (copies.isEmpty()) {
            // Stated in words rather than left blank: an empty list here means no wipe has happened,
            // which is good news, and a bare gap reads as something that failed to load.
            Text(
                text = stringResource(R.string.settings_preserved_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        copies.forEach { copy ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = copy.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text =
                            stringResource(
                                R.string.settings_preserved_detail,
                                copy.savedAt?.let { dateTimeLabel(it) }
                                    ?: stringResource(R.string.settings_preserved_undated),
                                Formatter.formatShortFileSize(context, copy.totalBytes),
                                pluralStringResource(
                                    R.plurals.settings_preserved_files,
                                    copy.files.size,
                                    copy.files.size,
                                ),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onShare(copy) }) {
                            Text(stringResource(R.string.settings_preserved_share))
                        }
                        TextButton(onClick = { onDelete(copy) }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
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
