package app.binky.tracker.ui.backup

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.DEFAULT_EXPORT_INTERVAL
import app.binky.tracker.data.ExportInterval
import app.binky.tracker.data.ExportReminder
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.PreservedKind
import app.binky.tracker.data.backup.AutoBackupStatus
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.data.backup.ExportFolderState
import app.binky.tracker.data.backup.RestoreOutcome
import app.binky.tracker.data.backup.RestoreRefusal
import app.binky.tracker.data.dueOn
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.openSystemBackupSettings
import app.binky.tracker.ui.common.shareBackupArchive
import app.binky.tracker.ui.common.sharePreservedCopy
import app.binky.tracker.ui.weight.dateTimeLabel
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.reminderDelivery
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

    // The folder picker. `OpenDocumentTree` returns a *tree* URI standing for a whole directory,
    // which is the only kind of grant that lets an app write a file it has not been handed — and
    // the grant has to be persisted straight away or it dies with this process.
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.rememberFolder(uri)
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
            ExportFolderSection(
                folder = state.folder,
                notice = state.notice,
                working = state.working,
                onChoose = {
                    // A phone with no document provider at all is rare and is not a crash: the
                    // share sheet is still there, and this screen says so rather than dying on the
                    // way to a picker that does not exist (ADR-0005).
                    try {
                        folderPicker.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        viewModel.folderPickerUnavailable()
                    }
                },
                onForget = viewModel::forgetFolder,
                onExportToFolder = viewModel::exportToFolder,
                onDismissNotice = viewModel::dismissNotice,
            )
            HorizontalDivider()
            ExportReminderSection(
                reminder = state.reminder,
                onSet = viewModel::setReminder,
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
            text = { Text(refusal.message()) },
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

/**
 * The remembered export destination (ADR-0005, PLAN 4e).
 *
 * **A saved destination, not a new export mechanism.** The button above still goes to the share
 * sheet and always will — a chooser cannot fail for provider reasons, where writing into a cloud
 * provider's document tree was the plan's longest-standing unverified assumption. What a remembered
 * folder buys is the two taps between making a backup and having it where the owner keeps things,
 * which is what turns "I should export" into something they actually do.
 *
 * The three states are the three the app can honestly be in, and [ExportFolderState.Unavailable] is
 * the one that earns its keep: a grant revoked in Android's settings, or a preference restored from
 * another phone, leaves a folder name that would fail at the moment it was counted on.
 */
@Composable
private fun ExportFolderSection(
    folder: ExportFolderState,
    notice: FolderNotice?,
    working: BackupWork?,
    onChoose: () -> Unit,
    onForget: () -> Unit,
    onExportToFolder: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backup_folder_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_folder_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val line =
            when (folder) {
                ExportFolderState.None -> stringResource(R.string.backup_folder_none)
                is ExportFolderState.Remembered -> stringResource(R.string.backup_folder_chosen, folder.label)
                ExportFolderState.Unavailable -> stringResource(R.string.backup_folder_unavailable)
            }
        Text(text = line, style = MaterialTheme.typography.bodyMedium)

        if (folder is ExportFolderState.Remembered) {
            Button(onClick = onExportToFolder, enabled = working == null) {
                Text(stringResource(R.string.backup_folder_export))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose) {
                Text(
                    stringResource(
                        if (folder is ExportFolderState.None) {
                            R.string.backup_folder_choose
                        } else {
                            R.string.backup_folder_change
                        },
                    ),
                )
            }
            // Offered for an unavailable folder too: forgetting is exactly how an owner clears a
            // destination that has gone, and refusing them that would leave the warning on screen
            // with no way to act on it.
            if (folder !is ExportFolderState.None) {
                TextButton(onClick = onForget) { Text(stringResource(R.string.backup_folder_forget)) }
            }
        }

        notice?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.text(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
}

/** One sentence per outcome; none of them is an error the owner has to resolve. */
@Composable
private fun FolderNotice.text(): String =
    when (this) {
        is FolderNotice.Saved -> stringResource(R.string.backup_folder_saved, name)
        FolderNotice.Refused -> stringResource(R.string.backup_folder_refused)
        FolderNotice.Unavailable -> stringResource(R.string.backup_folder_unavailable)
        FolderNotice.NotRemembered -> stringResource(R.string.backup_folder_not_remembered)
        FolderNotice.NoPicker -> stringResource(R.string.backup_folder_no_picker)
    }

/**
 * The recurring export prompt (ADR-0005, PLAN 4e) — the piece that turns a remembered folder from a
 * two-tap saving into a habit the owner does not have to hold.
 *
 * **A switch and four presets, off by default.** Off by default because an app that starts nagging
 * about backups uninvited is one an owner learns to swipe past, and this project spends its
 * notification budget on animals first.
 *
 * The copy says what it is: a prompt about the owner's *export*, never a claim that their data is
 * unsafe. What is and is not protected is the automatic-backup line's job at the top of this screen,
 * including the case where the honest answer is that nobody knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportReminderSection(
    reminder: ExportReminder,
    onSet: (ExportInterval?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backup_reminder_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_reminder_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.backup_reminder_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = reminder.every != null,
                // Switching on takes the default interval rather than the last one used: the chips
                // below are right there, and remembering a choice the owner turned off is how a
                // reminder comes back on a schedule they no longer recognise.
                onCheckedChange = { on -> onSet(if (on) DEFAULT_EXPORT_INTERVAL else null) },
            )
        }

        if (reminder.every == null) {
            Text(text = stringResource(R.string.backup_reminder_off), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        // **The certain failure, stated** (ADR-0003's three honest states). A switch that promises a
        // monthly prompt while notifications are off — or this channel is muted — is the app
        // claiming something it can already tell will not happen. Only `Blocked` earns a line here:
        // best-effort means "may arrive late", and for a backup prompt late is fine, so saying it
        // would be the hedge that teaches an owner to stop reading these.
        val context = LocalContext.current
        var delivery by remember { mutableStateOf<ReminderDelivery?>(null) }
        // Re-read on every resume, not remembered once: the owner can change either fact by walking
        // into Android's settings and back, and this line has to redraw when they do.
        LifecycleResumeEffect(Unit) {
            delivery = context.reminderDelivery(ReminderChannel.Backup)
            onPauseOrDispose {}
        }
        if (delivery == ReminderDelivery.Blocked) {
            Text(
                text = stringResource(R.string.backup_reminder_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { context.openAppNotificationSettings() }) {
                // The reminders_ string, reused rather than copied: it is the same button doing the
                // same thing, and a second translation of "Open notification settings" is a second
                // thing to keep in step.
                Text(stringResource(R.string.reminders_open_settings_action))
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportInterval.entries.forEach { entry ->
                FilterChip(
                    selected = entry == reminder.every,
                    onClick = { onSet(entry) },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        }

        // The derived due date, shown rather than described: "every month" is the setting, and
        // "next: 1 September" is what the owner can check against their own memory of the last one.
        reminder.dueOn()?.let { due ->
            Text(
                text = stringResource(R.string.backup_reminder_next, dateLabel(due)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val ExportInterval.labelRes: Int
    get() =
        when (this) {
            ExportInterval.WEEKLY -> R.string.backup_reminder_weekly
            ExportInterval.FORTNIGHTLY -> R.string.backup_reminder_fortnightly
            ExportInterval.MONTHLY -> R.string.backup_reminder_monthly
            ExportInterval.QUARTERLY -> R.string.backup_reminder_quarterly
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

/**
 * One sentence per refusal, each of which ends by saying nothing on the phone was changed.
 *
 * A `@Composable` returning a `String` rather than a `@StringRes Int`, because the newer-backup case
 * names both versions and so needs its arguments formatted in — and a resource id cannot carry them.
 */
@Composable
private fun RestoreRefusal.message(): String =
    when (this) {
        RestoreRefusal.NotABinkyBackup -> stringResource(R.string.backup_refused_not_a_backup)
        is RestoreRefusal.MadeByANewerBinky ->
            stringResource(R.string.backup_refused_newer, fileVersion, readableVersion)
        RestoreRefusal.TooLarge -> stringResource(R.string.backup_refused_too_large)
        RestoreRefusal.Unreadable -> stringResource(R.string.backup_refused_unreadable)
    }
