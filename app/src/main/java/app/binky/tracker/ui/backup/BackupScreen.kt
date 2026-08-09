package app.binky.tracker.ui.backup

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.CautionDot
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.FormChip
import app.binky.tracker.ui.common.FormSection
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.common.SwitchRow
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
 *
 * ## Phase 7, against `6c` / `6d`
 *
 * Every section, sentence and option is the app's own — **nothing added, nothing renamed**. What
 * changed is the shape:
 *
 * - **The section rules are gone.** Six `HorizontalDivider`s between six blocks of loose text become
 *   the standard 24-up / 8-down header rhythm and a card per section, which is `Spacing.kt`'s rule
 *   applied rather than a new one invented.
 * - **The automatic-backup status gets the apricot [CautionDot]** — "no automatic backup has been
 *   recorded on this phone" is exactly the class of thing the marker exists for, and it was
 *   previously indistinguishable from the paragraph above it.
 * - **The scopes become 64dp rows in a grouped card**, the chosen one carrying a fill.
 * - **The photo warning moved inside the export card**, above the button it qualifies.
 *
 * **The screen spends its one high surface on automatic backup** (`6d`), because that is the section
 * about a mechanism the owner cannot control; everything below it is work they *can* do and stays on
 * the ordinary card level. The selected scope row and the photo warning take the same high surface as
 * a *fill inside* a card — one mechanism reused, not a second card claiming to be the exception.
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // `section` at the bottom, not `tight`: this screen ends in a footnote rather
                    // than a card, and 8dp put it under the gesture bar on the first device pass.
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
            // The gap a section header expects above it. No dividers: the header rhythm is what
            // separates sections now, and a rule as well would be the same statement made twice.
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            AutomaticBackup(
                status = state.autoBackup,
                onOpenSystemSettings = { context.openSystemBackupSettings() },
            )
            ExportSection(
                scope = state.scope,
                working = state.working,
                onSelectScope = viewModel::setScope,
                onExport = viewModel::export,
            )
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
            ExportReminderSection(
                reminder = state.reminder,
                onSet = viewModel::setReminder,
            )
            RestoreSection(
                working = state.working,
                onPick = { picker.launch(arrayOf("*/*")) },
            )
            PreservedCopies(
                copies = state.preserved,
                onShare = { copy -> context.sharePreservedCopy(copy) },
                onRestore = { copy -> viewModel.inspect(RestoreSource.Snapshot(copy.file)) },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    state.pendingRestore?.let { pending ->
        BinkyDialog(
            title = stringResource(R.string.backup_restore_confirm_title),
            onDismiss = viewModel::cancelRestore,
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(
                stringResource(
                    R.string.backup_restore_confirm_body,
                    stringResource(pending.manifest.scope.labelRes),
                    dateTimeLabel(pending.manifest.createdAt),
                ),
            )
        }
    }

    state.refusal?.let { refusal ->
        BinkyDialog(
            title = stringResource(R.string.backup_refused_title),
            onDismiss = viewModel::dismissRefusal,
            confirmButton = {
                TextButton(onClick = viewModel::dismissRefusal) { Text(stringResource(R.string.action_ok)) }
            },
        ) {
            Text(refusal.message())
        }
    }

    state.pendingDelete?.let { copy ->
        BinkyDialog(
            title = stringResource(R.string.preserved_delete_title),
            onDismiss = viewModel::cancelDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(stringResource(R.string.preserved_delete_body, copy.name))
        }
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
 *
 * **`6c`'s apricot [CautionDot] marks exactly those same two states**, which is what settles the
 * open question about a stale-backup marker without inventing anything: the staleness rule is
 * already in [AutoBackupStatus] and already the reason the button is there, so the dot marks what the
 * screen was going to act on anyway. A recorded, fresh backup gets no marker — the app has nothing to
 * raise, and a permanent dot beside a working net is the reassurance an owner learns to skip.
 */
@Composable
private fun AutomaticBackup(
    status: AutoBackupStatus,
    onOpenSystemSettings: () -> Unit,
) {
    val actionable = status !is AutoBackupStatus.Recorded || status.stale

    Column {
        SectionHeader(stringResource(R.string.backup_auto_title))
        Spacer(Modifier.height(Spacing.tight))
        // The screen's one raised card (`6d`): this is the section about a mechanism Binky cannot
        // switch on, check, or fix, and the level is what says so before the sentence does.
        GroupedCard(
            raised = true,
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.snug),
        ) {
            HelpText(stringResource(R.string.backup_auto_help))

            val line =
                when (status) {
                    AutoBackupStatus.NeverRecorded -> stringResource(R.string.backup_auto_never)
                    is AutoBackupStatus.Recorded ->
                        stringResource(
                            if (status.stale) R.string.backup_auto_stale else R.string.backup_auto_recorded,
                            dateTimeLabel(status.at),
                        )
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (actionable) CautionDot()
                Text(text = line, style = MaterialTheme.typography.titleMedium)
            }

            // Never dropped silently (ADR-0005). The count comes from the marker the backup agent
            // wrote, which is the same number the one-time notification is posted from — so the two
            // cannot disagree about how much is missing. Its own line rather than folded into the
            // sentence above: the date is about the net working, this is about a hole in it.
            val excluded = (status as? AutoBackupStatus.Recorded)?.excludedDocuments ?: 0
            if (excluded > 0) {
                Text(
                    text = pluralStringResource(R.plurals.backup_auto_excluded, excluded, excluded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HelpText(stringResource(R.string.backup_auto_photos))

            if (actionable) {
                OutlinedButton(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.backup_auto_settings_action))
                }
            }
        }
    }
}

/**
 * What an export includes, and the button that makes one.
 *
 * One card holding all three parts, which is `6c`'s structural change: the three scopes, the photo
 * warning that qualifies them, and the action they configure. The warning used to sit between two
 * sections belonging to neither.
 *
 * `contentPadding` of zero because the scope rows are full-bleed — a selected row is a band of colour
 * across the card, and it is the card's own corner radius that is meant to clip it at the ends.
 * Everything after the rows takes its inset back.
 */
@Composable
private fun ExportSection(
    scope: BackupScope,
    working: BackupWork?,
    onSelectScope: (BackupScope) -> Unit,
    onExport: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.backup_scope_title))
        Spacer(Modifier.height(Spacing.tight))
        GroupedCard(contentPadding = PaddingValues(0.dp)) {
            BackupScopePicker(scope = scope, onSelect = onSelectScope)
            RowDivider()
            Column(
                modifier = Modifier.padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.snug),
            ) {
                PhotosNotProtectedNote()
                // The one action this section exists for, so it takes the full-width primary shape
                // rather than sitting where a default-sized button would.
                Button(
                    onClick = onExport,
                    enabled = working == null,
                    modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                    shape = RoundedCornerShape(RecordButtonRadius),
                ) {
                    Text(stringResource(R.string.backup_export_action))
                }
                if (working == BackupWork.Exporting) Working(R.string.backup_exporting)
            }
        }
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
    FormSection(
        title = stringResource(R.string.backup_folder_title),
        spacing = Spacing.snug,
    ) {
        HelpText(stringResource(R.string.backup_folder_help))

        val line =
            when (folder) {
                ExportFolderState.None -> stringResource(R.string.backup_folder_none)
                is ExportFolderState.Remembered -> stringResource(R.string.backup_folder_chosen, folder.label)
                ExportFolderState.Unavailable -> stringResource(R.string.backup_folder_unavailable)
            }
        Text(text = line, style = MaterialTheme.typography.bodyLarge)

        if (folder is ExportFolderState.Remembered) {
            Button(
                onClick = onExportToFolder,
                enabled = working == null,
                modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                shape = RoundedCornerShape(RecordButtonRadius),
            ) {
                Text(stringResource(R.string.backup_folder_export))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
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
 *
 * Undrawn — `6c` stops at *Where exports go* — so the language is applied by hand: the switch is
 * [SwitchRow], which carries its own insets, and everything else in the card takes them explicitly.
 */
@Composable
private fun ExportReminderSection(
    reminder: ExportReminder,
    onSet: (ExportInterval?) -> Unit,
) {
    FormSection(
        title = stringResource(R.string.backup_reminder_title),
        // The switch row spans the card so its whole width is the target; the text around it takes
        // the inset back a block at a time.
        //
        // `spacing = 0` and every gap written out, because the two mechanisms compound otherwise:
        // [SwitchRow] already carries 12dp of its own inside a 64dp floor, so a section gap on top
        // of that left the paragraph, the switch and the state line floating as three unrelated
        // things. Found on the device, not in the code.
        contentPadding = PaddingValues(vertical = Spacing.hair),
        spacing = 0.dp,
    ) {
        HelpText(
            text = stringResource(R.string.backup_reminder_help),
            modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.tight),
        )

        SwitchRow(
            title = stringResource(R.string.backup_reminder_switch),
            checked = reminder.every != null,
            // Switching on takes the default interval rather than the last one used: the chips
            // below are right there, and remembering a choice the owner turned off is how a
            // reminder comes back on a schedule they no longer recognise.
            onCheckedChange = { on -> onSet(if (on) DEFAULT_EXPORT_INTERVAL else null) },
        )

        if (reminder.every == null) {
            Text(
                text = stringResource(R.string.backup_reminder_off),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.snug),
            )
            return@FormSection
        }

        RowDivider()

        Column(
            modifier =
                Modifier.padding(
                    start = Spacing.base,
                    end = Spacing.base,
                    top = Spacing.snug,
                    bottom = Spacing.snug,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.snug),
        ) {
            // **The certain failure, stated** (ADR-0003's three honest states). A switch that
            // promises a monthly prompt while notifications are off — or this channel is muted — is
            // the app claiming something it can already tell will not happen. Only `Blocked` earns a
            // line here: best-effort means "may arrive late", and for a backup prompt late is fine,
            // so saying it would be the hedge that teaches an owner to stop reading these.
            val context = LocalContext.current
            var delivery by remember { mutableStateOf<ReminderDelivery?>(null) }
            // Re-read on every resume, not remembered once: the owner can change either fact by
            // walking into Android's settings and back, and this line has to redraw when they do.
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
                    // The reminders_ string, reused rather than copied: it is the same button doing
                    // the same thing, and a second translation of "Open notification settings" is a
                    // second thing to keep in step.
                    Text(stringResource(R.string.reminders_open_settings_action))
                }
            }

            ChipRow {
                ExportInterval.entries.forEach { entry ->
                    FormChip(
                        selected = entry == reminder.every,
                        onClick = { onSet(entry) },
                        label = stringResource(entry.labelRes),
                    )
                }
            }

            // The derived due date, shown rather than described: "every month" is the setting, and
            // "next: 1 September" is what the owner can check against their own memory of the last
            // one.
            reminder.dueOn()?.let { due ->
                Text(
                    text = stringResource(R.string.backup_reminder_next, dateLabel(due)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
    FormSection(
        title = stringResource(R.string.backup_restore_title),
        spacing = Spacing.snug,
    ) {
        HelpText(stringResource(R.string.backup_restore_help))
        OutlinedButton(onClick = onPick, enabled = working == null) {
            Text(stringResource(R.string.backup_restore_action))
        }
        if (working == BackupWork.Restoring) Working(R.string.backup_restoring)
    }
}

@Composable
private fun Working(labelRes: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        modifier = Modifier.fillMaxWidth(),
    ) {
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
 *
 * The one list in the app whose rows keep their buttons rather than chevroning into an editor
 * (`1d`'s rule): a preserved copy has no screen of its own, and giving it one so that *Delete* could
 * move off the row would be a new route in a phase that adds none.
 */
@Composable
private fun PreservedCopies(
    copies: List<PreservedCopy>,
    onShare: (PreservedCopy) -> Unit,
    onRestore: (PreservedCopy) -> Unit,
    onDelete: (PreservedCopy) -> Unit,
) {
    val context = LocalContext.current
    Column {
        SectionHeader(stringResource(R.string.preserved_title))
        Spacer(Modifier.height(Spacing.tight))

        if (copies.isEmpty()) {
            // Stated in words rather than left blank: an empty list here means nothing has ever
            // needed replacing, which is good news, and a bare gap reads as something that failed
            // to load.
            MessageCard(stringResource(R.string.preserved_empty))
            Spacer(Modifier.height(Spacing.tight))
            HelpText(stringResource(R.string.preserved_help))
            return@Column
        }

        GroupedCard {
            copies.forEachIndexed { index, copy ->
                if (index > 0) RowDivider()
                Column(
                    modifier = Modifier.padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.hair),
                ) {
                    Text(text = stringResource(copy.kind.labelRes), style = MaterialTheme.typography.titleMedium)
                    Text(text = copy.name, style = MaterialTheme.typography.bodySmall)
                    HelpText(
                        stringResource(
                            R.string.preserved_detail,
                            copy.savedAt?.let { dateTimeLabel(it) }
                                ?: stringResource(R.string.preserved_undated),
                            Formatter.formatShortFileSize(context, copy.totalBytes),
                            pluralStringResource(R.plurals.preserved_files, copy.files.size, copy.files.size),
                        ),
                    )
                    if (!copy.kind.restorable) {
                        HelpText(stringResource(R.string.preserved_not_restorable))
                    }
                    // Pulled back to the text edge: a text button carries its own padding, so a row
                    // of them laid out flush looks indented against the lines above.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                        modifier = Modifier.padding(top = Spacing.hair).offset(x = -Spacing.snug),
                    ) {
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

        Spacer(Modifier.height(Spacing.tight))
        // Below the list rather than above it: it explains what the rows are, and a paragraph
        // between a header and the thing it names pushes the content off the first screenful.
        HelpText(stringResource(R.string.preserved_help))
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
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.section),
        verticalArrangement = Arrangement.spacedBy(Spacing.snug),
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
        HelpText(stringResource(R.string.backup_restored_close_help))
        Button(
            onClick = {
                activity?.finishAffinity()
                exitProcess(0)
            },
            modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
            shape = RoundedCornerShape(RecordButtonRadius),
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
