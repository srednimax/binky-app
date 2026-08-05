package app.binky.tracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.reminders.RemindersOptIn
import app.binky.tracker.work.scheduleDebugReminder

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
            LanguageSetting()
            HorizontalDivider()
            BackupSetting(onOpen = onOpenBackup)

            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                SampleDataSetting(
                    outcome = state.sampleData,
                    onSeed = viewModel::seedSampleData,
                    onDismiss = viewModel::clearSampleDataOutcome,
                )
                HorizontalDivider()
                DebugReminderSetting()
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
 * ADR-0013's language switcher — the Settings row that ADR originally hoped the whole thing would
 * be, before checking found the mechanism to be an activity base class and a root theme.
 *
 * **English alone in the list at 1.0**, and that is not the pointless furniture ADR-0013 warned
 * about. The mechanism landed at 3b, months before the translation it exists for; shipping the row
 * on top of it means the switcher is exercised by hand on a real phone in 1.0 rather than for the
 * first time in the week Polish arrives. At 3g the list grows by one entry and this code does not
 * change at all — which is the claim being tested.
 *
 * No ViewModel: the chosen language lives in [AppCompatDelegate], not in this app's preferences,
 * and routing it through one would be a second copy of an answer the system also owns.
 */
@Composable
private fun LanguageSetting() {
    // Local state, seeded from the delegate. Applying a language recreates the Activity, so this is
    // thrown away and re-read almost immediately — it exists so the dialog's radio button moves
    // under the finger rather than on the next frame after a recreation.
    var chosen by remember { mutableStateOf(currentAppLanguage()) }
    var picking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clickable { picking = true },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Text(
            text = chosen?.let { stringResource(it.labelRes) } ?: stringResource(R.string.settings_language_system),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.settings_language_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                // Kotlin note: `listOf(null) + entries` builds the offered list with the
                // follow-the-phone case as a first-class member rather than a special row, so the
                // radio group has one shape and one selection rule.
                Column(modifier = Modifier.selectableGroup()) {
                    (listOf(null) + AppLanguage.entries).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.fillMaxWidth().selectable(
                                    selected = option == chosen,
                                    role = Role.RadioButton,
                                    onClick = {
                                        chosen = option
                                        picking = false
                                        setAppLanguage(option)
                                    },
                                ),
                        ) {
                            RadioButton(selected = option == chosen, onClick = null)
                            Text(
                                text =
                                    option?.let { stringResource(it.labelRes) }
                                        ?: stringResource(R.string.settings_language_system),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
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

/**
 * **What makes 4a provable with no reminders in existence** (ADR-0024): a notification two minutes
 * from now, on its own one-shot path rather than through the daily sweep.
 *
 * It was also the point-of-use host for [RemindersOptIn] until 4c gave it a real one on the Care
 * screen — which is what proves ADR-0006's "one composable in two hosts" claim rather than leaving
 * it as an intention. The sheet is the *only* path anyone takes at 1.1, since every install that
 * exists today has already been through first-run setup.
 *
 * Debug builds only; the caller renders it behind `BuildConfig.DEBUG`. It stays after this
 * checkpoint as the fastest way to re-prove delivery after any change to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugReminderSetting() {
    val context = LocalContext.current
    var scheduled by remember { mutableStateOf(false) }
    var optingIn by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.settings_debug_reminder), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_debug_reminder_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { optingIn = true }) {
            Text(stringResource(R.string.settings_debug_reminder_settings_action))
        }
        TextButton(
            onClick = {
                scheduleDebugReminder(context)
                scheduled = true
            },
        ) {
            Text(stringResource(R.string.settings_debug_reminder_action))
        }
    }

    if (optingIn) {
        ModalBottomSheet(onDismissRequest = { optingIn = false }) {
            Column(
                // **A sheet is its own window, so the shell's Scaffold pads none of this** (PLAN 4f).
                // Anchored to the bottom edge, which is exactly where the navigation bar is: without
                // the padding the autostart explanation ran under the three-button bar with the nav
                // icons drawn over the words, and the rest of it off the bottom of the screen. The
                // scroll is the other half — this text does not fit a 1220px-tall landscape screen,
                // and a sheet that cannot scroll simply loses whatever did not fit.
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineSmall)
                RemindersOptIn()
                TextButton(onClick = { optingIn = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }

    if (scheduled) {
        AlertDialog(
            onDismissRequest = { scheduled = false },
            text = { Text(stringResource(R.string.settings_debug_reminder_scheduled)) },
            confirmButton = {
                TextButton(
                    onClick = { scheduled = false },
                ) { Text(stringResource(R.string.action_ok)) }
            },
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
