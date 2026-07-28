package app.binky.tracker.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.backup.BackupScopePicker
import app.binky.tracker.ui.backup.PhotosNotProtectedNote
import app.binky.tracker.ui.common.openSystemBackupSettings
import app.binky.tracker.ui.shell.BunnySummary

/** How many steps the wizard has, so the counter and the last step's button agree on the end. */
private const val SETUP_STEPS = 2

/**
 * Step one: add your first bunny, or don't (ADR-0006).
 *
 * **Skippable, and it says so on the button rather than in the small print.** An owner who came to
 * look around before committing a real animal's records is not a failure state, and a wizard that
 * cannot be got past is the fastest way to an uninstall.
 *
 * @param bunnies the live list of active bunnies. Read rather than passed a flag, so the step
 *   reports what actually happened in the editor — the editor's own callback fires identically on
 *   save and on cancel, and this is the source that cannot be wrong about which it was.
 */
@Composable
fun SetupBunnyStep(
    bunnies: List<BunnySummary>,
    onAddBunny: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val added = bunnies.isNotEmpty()

    SetupStep(
        step = 1,
        title = stringResource(R.string.setup_bunny_title),
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.setup_bunny_body), style = MaterialTheme.typography.bodyMedium)

        if (added) {
            Text(
                // Two strings rather than a `<plurals>`: the singular names the bunny and the
                // plural cannot, so they are different sentences, not one sentence with a count in
                // it. Pluralising a string whose *argument* changes shape is how a translation ends
                // up with a name where a number should be (ADR-0013).
                text =
                    if (bunnies.size == 1) {
                        stringResource(R.string.setup_bunny_added_one, bunnies.first().name)
                    } else {
                        stringResource(R.string.setup_bunny_added_many)
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (added) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_continue))
                }
                OutlinedButton(onClick = onAddBunny, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_bunny_add_another))
                }
            } else {
                Button(onClick = onAddBunny, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_bunny_action))
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_bunny_skip))
                }
            }
        }
    }
}

/**
 * Step two: what protects these records, and what does not (ADR-0005, ADR-0006).
 *
 * The scope choice is here rather than in Settings because **a backup buried in settings never gets
 * made**, which is the whole of ADR-0006's argument for a first-run step at all.
 *
 * The Android backup question is asked, not answered: the app genuinely cannot read that switch, so
 * the honest move is to say so and open the screen where it lives. This is the one moment the owner
 * is already thinking about backup, and every alternative amounts to assuming.
 *
 * There is deliberately **no last-backup status line** here, unlike Backup settings. On a first run
 * it would always read "No automatic backup has been recorded on this phone" — true, and useless as
 * a signal, because nothing has had a chance to record one yet. A line that can only say one thing
 * is not a status.
 */
@Composable
fun SetupBackupStep(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SetupStep(
        step = 2,
        title = stringResource(R.string.setup_backup_title),
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.setup_backup_body), style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()

        Text(text = stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_auto_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stringResource(R.string.setup_backup_auto_ask), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.backup_auto_photos),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { context.openSystemBackupSettings() }) {
            Text(stringResource(R.string.backup_auto_settings_action))
        }

        HorizontalDivider()

        Text(text = stringResource(R.string.backup_scope_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.setup_backup_scope_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackupScopePicker(scope = state.scope, onSelect = viewModel::setScope)
        PhotosNotProtectedNote()

        Text(
            text = stringResource(R.string.setup_backup_changeable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_finish))
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_back))
            }
        }
    }
}

/**
 * The frame both steps share: where they are, what they are about, and room to scroll.
 *
 * The step counter is not decoration — it is the promise that this ends, and how soon. Two steps is
 * short enough that saying so is the difference between "answer these" and "how long is this
 * going to be".
 */
@Composable
private fun SetupStep(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_step, step, SETUP_STEPS),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}
