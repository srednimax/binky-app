package app.binky.tracker.ui.backup

import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.PreservedKind
import app.binky.tracker.data.backup.AutoBackupStatus
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.data.backup.RestoreOutcome
import app.binky.tracker.data.backup.RestoreRefusal
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.openSystemBackupSettings
import app.binky.tracker.ui.common.shareBackupArchive
import app.binky.tracker.ui.common.sharePreservedCopy
import app.binky.tracker.ui.weight.dateTimeLabel
import kotlin.system.exitProcess

/**
 * Backup and restore (ADR-0005), reached from Settings.
 *
 * Once a restore has finished this screen **is** the terminal report and nothing else: half the app
 * is holding `Flow`s over a database file that no longer exists, so there is nowhere sensible to go
 * back to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    state.restored?.let { restored ->
        RestoreFinished(restored)
        return
    }

    // Any MIME type on purpose. Backups travel by mail and messenger, which relabel a zip as
    // octet-stream, x-zip-compressed or nothing at all, and a filter that hid the owner's own backup
    // would be a worse failure than showing them a file Binky then declines. The manifest check is
    // the real gate, and it runs on whatever comes back.
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.inspect(RestoreSource.Picked(uri))
        }

    // A finished export is handed straight to the share sheet, then cleared — or a rotation would
    // open a second chooser over the first.
    LaunchedEffect(state.exported) {
        state.exported?.let { archive ->
            context.shareBackupArchive(archive)
            viewModel.clearExported()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.backup_title)) },
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
            AutomaticBackup(
                status = state.autoBackup,
                onOpenSystemSettings = { context.openSystemBackupSettings() },
            )
            HorizontalDivider()
            ExportSection(
                scope = state.scope,
                working = state.working,
                onSelectScope = viewModel::setScope,
                onExport = viewModel::export,
            )
            HorizontalDivider()
            RestoreSection(
                working = state.working,
                onPick = { picker.launch(arrayOf("*/*")) },
            )
            HorizontalDivider()
            PreservedCopies(
                copies = state.preserved,
                onShare = { copy -> context.sharePreservedCopy(copy) },
                onRestore = { copy -> viewModel.inspect(RestoreSource.Snapshot(copy.file)) },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_body,
                        stringResource(pending.manifest.scope.labelRes),
                        dateTimeLabel(pending.manifest.createdAt),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.refusal?.let { refusal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRefusal,
            title = { Text(stringResource(R.string.backup_refused_title)) },
            text = { Text(stringResource(refusal.messageRes)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    state.pendingDelete?.let { copy ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.preserved_delete_title)) },
            text = { Text(stringResource(R.string.preserved_delete_body, copy.name)) },
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
 * The status line that **must not lie in either direction** (ADR-0005).
 *
 * Every state is words. Absence is the one that matters: Auto Backup runs only with backup switched
 * on, an account signed in, and the phone idle, charging and online, and there is no reliable way to
 * ask Android whether this app is actually included — so a blank line here would read as a working
 * net, which is ADR-0001's silence failure pointed at backup. Past a fortnight a bare date stops
 * being reassuring and starts being a claim, so it says so.
 *
 * The button appears in both of the states the owner can act on, and not beside a fresh date, where
 * it would only invite fiddling with a setting that is working.
 */
@Composable
private fun AutomaticBackup(
    status: AutoBackupStatus,
    onOpenSystemSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_auto_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val line =
            when (status) {
                AutoBackupStatus.NeverRecorded -> stringResource(R.string.backup_auto_never)
                is AutoBackupStatus.Recorded ->
                    stringResource(
                        if (status.stale) R.string.backup_auto_stale else R.string.backup_auto_recorded,
                        dateTimeLabel(status.at),
                    )
            }
        Text(text = line, style = MaterialTheme.typography.bodyMedium)

        Text(
            text = stringResource(R.string.backup_auto_photos),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val actionable = status !is AutoBackupStatus.Recorded || status.stale
        if (actionable) {
            OutlinedButton(onClick = onOpenSystemSettings) {
                Text(stringResource(R.string.backup_auto_settings_action))
            }
        }
    }
}

@Composable
private fun ExportSection(
    scope: BackupScope,
    working: BackupWork?,
    onSelectScope: (BackupScope) -> Unit,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backup_scope_title), style = MaterialTheme.typography.titleMedium)

        BackupScopePicker(scope = scope, onSelect = onSelectScope)
        PhotosNotProtectedNote()

        Button(onClick = onExport, enabled = working == null) {
            Text(stringResource(R.string.backup_export_action))
        }
        if (working == BackupWork.Exporting) Working(R.string.backup_exporting)
    }
}

@Composable
private fun RestoreSection(
    working: BackupWork?,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_restore_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onPick, enabled = working == null) {
            Text(stringResource(R.string.backup_restore_action))
        }
        if (working == BackupWork.Restoring) Working(R.string.backup_restoring)
    }
}

@Composable
private fun Working(labelRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Everything in `preserved/`, with **what each row is** spelled out (ADR-0005).
 *
 * The two occupants have opposite properties — a wipe copy is at a stale schema and can only be
 * shared, a restore snapshot is at the current schema and loads back in one tap — so a listing that
 * did not say which was which would offer a button that works on one row and cannot work on the
 * other. Nothing here is auto-pruned: every occupant is a recovery artifact, and silently deleting
 * those is the one thing this project has consistently refused to do on the owner's behalf.
 */
@Composable
private fun PreservedCopies(
    copies: List<PreservedCopy>,
    onShare: (PreservedCopy) -> Unit,
    onRestore: (PreservedCopy) -> Unit,
    onDelete: (PreservedCopy) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.preserved_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.preserved_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (copies.isEmpty()) {
            // Stated in words rather than left blank: an empty list here means nothing has ever
            // needed replacing, which is good news, and a bare gap reads as something that failed
            // to load.
            Text(text = stringResource(R.string.preserved_empty), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        copies.forEach { copy ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(copy.kind.labelRes), style = MaterialTheme.typography.titleSmall)
                    Text(text = copy.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text =
                            stringResource(
                                R.string.preserved_detail,
                                copy.savedAt?.let { dateTimeLabel(it) }
                                    ?: stringResource(R.string.preserved_undated),
                                Formatter.formatShortFileSize(context, copy.totalBytes),
                                pluralStringResource(R.plurals.preserved_files, copy.files.size, copy.files.size),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!copy.kind.restorable) {
                        Text(
                            text = stringResource(R.string.preserved_not_restorable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (copy.kind.restorable) {
                            TextButton(onClick = { onRestore(copy) }) {
                                Text(stringResource(R.string.preserved_restore))
                            }
                        }
                        TextButton(onClick = { onShare(copy) }) { Text(stringResource(R.string.preserved_share)) }
                        TextButton(onClick = { onDelete(copy) }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
}

/**
 * The terminal screen, and the whole reason restore ends rather than returns.
 *
 * Half the app is holding `Flow`s over the database file that was just replaced, so the process has
 * to go. The obvious automatic version — schedule a `PendingIntent` and kill — is a **background
 * activity start**, restricted since Android 10 and policed harder by HyperOS: it would work on this
 * desk and silently fail to come back on someone else's phone, immediately after the most
 * destructive operation in the app. One tap instead, on a screen that is the right place to say what
 * happened anyway.
 */
@Composable
private fun RestoreFinished(restored: RestoreOutcome.Restored) {
    val activity = LocalActivity.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.backup_restored_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text =
                stringResource(
                    R.string.backup_restored_scope,
                    stringResource(restored.manifest.scope.labelRes),
                    dateTimeLabel(restored.manifest.createdAt),
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.backup_restored_overlaid,
                    restored.merge.overlaid.size,
                    restored.merge.overlaid.size,
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (restored.merge.kept.isNotEmpty()) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.backup_restored_kept,
                        restored.merge.kept.size,
                        restored.merge.kept.size,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.backup_restored_snapshot, restored.snapshot.name),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.backup_restored_close_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                activity?.finishAffinity()
                exitProcess(0)
            },
        ) {
            Text(stringResource(R.string.backup_restored_close))
        }
    }
}

private val PreservedKind.labelRes: Int
    get() =
        when (this) {
            PreservedKind.WipeCopy -> R.string.preserved_kind_wipe
            PreservedKind.RestoreSnapshot -> R.string.preserved_kind_snapshot
        }

/** One sentence per refusal, each of which ends by saying nothing on the phone was changed. */
private val RestoreRefusal.messageRes: Int
    get() =
        when (this) {
            RestoreRefusal.NotABinkyBackup -> R.string.backup_refused_not_a_backup
            RestoreRefusal.MadeByANewerBinky -> R.string.backup_refused_newer
            RestoreRefusal.TooLarge -> R.string.backup_refused_too_large
            RestoreRefusal.Unreadable -> R.string.backup_refused_unreadable
        }
