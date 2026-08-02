package app.binky.tracker.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.ExportInterval
import app.binky.tracker.data.ExportReminder
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.backup.AutoBackupStatus
import app.binky.tracker.data.backup.BackupManifest
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.data.backup.ExportFolderState
import app.binky.tracker.data.backup.FolderWrite
import app.binky.tracker.data.backup.RestoreOutcome
import app.binky.tracker.data.backup.RestoreRefusal
import app.binky.tracker.data.backup.autoBackupStatus
import app.binky.tracker.data.backup.exportFileName
import app.binky.tracker.data.backup.exportFolderState
import app.binky.tracker.data.backup.forgetExportFolder
import app.binky.tracker.data.backup.readAutoBackupMarker
import app.binky.tracker.data.backup.rememberExportFolder
import app.binky.tracker.data.backup.writeExportToFolder
import app.binky.tracker.data.deletePreservedCopy
import app.binky.tracker.data.listPreservedCopies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate

/** Where the archive a restore is about to read comes from. */
sealed interface RestoreSource {
    /** A file the owner picked through the system document picker. */
    data class Picked(
        val uri: Uri,
    ) : RestoreSource

    /** A pre-restore snapshot in `preserved/` — the way back out of a bad restore. */
    data class Snapshot(
        val file: File,
    ) : RestoreSource
}

/** Work in flight, so the screen can say which of two slow things it is doing. */
enum class BackupWork { Exporting, Restoring }

/**
 * What happened to the remembered folder, said once and dismissed — the folder half's whole error
 * surface (ADR-0005, PLAN 4e).
 *
 * Every case is recoverable and none of them loses an export: [Refused] and [Unavailable] both end
 * with the archive on its way to the share sheet, which is the path that worked before a folder was
 * ever remembered.
 */
sealed interface FolderNotice {
    /** Written, under the name the provider gave it. */
    data class Saved(
        val name: String,
    ) : FolderNotice

    /** The provider would not take the file. The share sheet has it instead. */
    data object Refused : FolderNotice

    /** The grant is gone — revoked, uninstalled, or restored from another phone. */
    data object Unavailable : FolderNotice

    /** The picked tree could not be persisted, so nothing was remembered. */
    data object NotRemembered : FolderNotice

    /** This phone has no document picker at all, which is rare and not a crash. */
    data object NoPicker : FolderNotice
}

/**
 * The confirmation's contents, **read from the manifest inside the zip**. The filename is never
 * consulted: it is the one part of a file an owner can trivially change, and this dialog is where
 * the promise about what is inside gets made.
 */
data class PendingRestore(
    val source: RestoreSource,
    val manifest: BackupManifest,
)

data class BackupUiState(
    val scope: BackupScope = BackupScope.Records,
    /**
     * What Android's automatic backup has recorded on this phone — **never a blank**, because a
     * blank status line reads as a working net (ADR-0005).
     */
    val autoBackup: AutoBackupStatus = AutoBackupStatus.NeverRecorded,
    /** Both occupants of `preserved/`, newest first — wipe copies and restore snapshots. */
    val preserved: List<PreservedCopy> = emptyList(),
    /**
     * The saved export destination, resolved against the grants this phone actually holds — a
     * stored URI is not a working folder (see [ExportFolderState]).
     */
    val folder: ExportFolderState = ExportFolderState.None,
    /** The recurring export prompt's settings and the three dates behind it. */
    val reminder: ExportReminder = ExportReminder(),
    val notice: FolderNotice? = null,
    val working: BackupWork? = null,
    /** A finished export, waiting to be handed to the share sheet. */
    val exported: File? = null,
    val pendingRestore: PendingRestore? = null,
    val refusal: RestoreRefusal? = null,
    /** Non-null once the restore is done: the screen becomes the terminal report. */
    val restored: RestoreOutcome.Restored? = null,
    val pendingDelete: PreservedCopy? = null,
)

/**
 * Backup and restore (ADR-0005).
 *
 * The two halves are deliberately asymmetric. Export is one tap and a share sheet; restore is a
 * confirmation, a snapshot of what is about to be replaced, and a terminal screen — it is the most
 * destructive thing the app does, and the one operation that gets a way back built out of parts
 * already tested.
 */
class BackupViewModel(
    private val container: AppContainer,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    /**
     * `preserved/` is a **filesystem** read, not a `Flow` from Room, so it has no change
     * notification of its own — this ticks it after every write that could alter the listing.
     */
    private val refresh = MutableStateFlow(0)
    private val transient = MutableStateFlow(TransientState())

    /**
     * The parts of the state that are not derived from disk or preferences.
     *
     * Kotlin note: one `data class` in one `MutableStateFlow` rather than six separate flows —
     * `combine` takes at most five, and more importantly these change together. `copy()` is the
     * object-spread equivalent: everything not named is carried over.
     */
    private data class TransientState(
        val working: BackupWork? = null,
        val exported: File? = null,
        val pendingRestore: PendingRestore? = null,
        val refusal: RestoreRefusal? = null,
        val restored: RestoreOutcome.Restored? = null,
        val pendingDelete: PreservedCopy? = null,
        val notice: FolderNotice? = null,
    )

    val uiState: StateFlow<BackupUiState> =
        combine(
            container.preferences.backupScope,
            container.preferences.exportFolder,
            container.preferences.exportReminder,
            refresh,
            transient,
        ) { scope, folder, reminder, _, now ->
            BackupUiState(
                scope = scope,
                // Resolved on every emission rather than stored: the grant behind a remembered
                // folder can be revoked in Android's settings while this screen is open, and a name
                // read once would keep claiming a destination the app can no longer write to.
                folder = contentResolver.exportFolderState(folder),
                reminder = reminder,
                notice = now.notice,
                // Read on every emission rather than watched: the marker is written by a process
                // that is not this one, at a moment nobody is looking, and re-reading it whenever
                // the screen re-collects is both cheap and enough. `now` is resolved here so the
                // 14-day staleness is judged when the line is drawn, not when the file was written.
                autoBackup = autoBackupStatus(readAutoBackupMarker(container.filesDir), Instant.now()),
                preserved = listPreservedCopies(container.preservedDir),
                working = now.working,
                exported = now.exported,
                pendingRestore = now.pendingRestore,
                refusal = now.refusal,
                restored = now.restored,
                pendingDelete = now.pendingDelete,
            )
            // flowOn moves the *upstream* onto IO, which is where the directory listing belongs.
            // Compose collects the result on the main thread as usual.
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun setScope(scope: BackupScope) {
        viewModelScope.launch { container.preferences.setBackupScope(scope) }
    }

    fun export() {
        if (transient.value.working != null) return
        transient.update { copy(working = BackupWork.Exporting) }
        viewModelScope.launch {
            val archive = buildExport()
            transient.update { copy(working = null, exported = archive) }
        }
    }

    /**
     * Export straight into the remembered folder — the two taps this half of 4e exists to save.
     *
     * **Falls back rather than failing.** A provider can refuse for reasons that have nothing to do
     * with this app, and by then the archive is already built; handing it to the share sheet is the
     * path that worked before any folder was remembered, so that is what a refusal does. The owner
     * still ends up with their backup, and hears why it did not go where they asked.
     */
    fun exportToFolder() {
        if (transient.value.working != null) return
        val destination = uiState.value.folder
        if (destination !is ExportFolderState.Remembered) {
            transient.update { copy(notice = FolderNotice.Unavailable) }
            return
        }

        transient.update { copy(working = BackupWork.Exporting, notice = null) }
        viewModelScope.launch {
            val archive = buildExport()
            when (val written = contentResolver.writeExportToFolder(destination.uri, archive)) {
                is FolderWrite.Written ->
                    transient.update { copy(working = null, notice = FolderNotice.Saved(written.name)) }
                FolderWrite.Refused ->
                    // `exported` is what opens the share sheet, so this line *is* the fallback.
                    transient.update {
                        copy(working = null, exported = archive, notice = FolderNotice.Refused)
                    }
            }
        }
    }

    /**
     * Builds the archive and records that an export was made.
     *
     * **The export counts at the moment the file exists**, not when it reaches a destination. The
     * share sheet returns no result — `ACTION_SEND` never reports whether the owner went through
     * with it — so the alternative to counting it here is a reminder that keeps prompting someone
     * who exports every week. Erring towards "they did it" is the direction that respects the
     * owner's attention; the Backup screen never claims a file is safe anywhere on the strength of
     * this date, and the automatic-backup line is where honesty about *coverage* lives.
     */
    private suspend fun buildExport(): File {
        val now = Instant.now()
        val scope = uiState.value.scope
        val archive =
            container.backupExporter.exportTo(
                target = File(container.exportsDir, exportFileName(scope, now)),
                scope = scope,
                now = now,
            )
        container.preferences.markExported(LocalDate.now())
        // The prompt has been answered; leaving it in the shade is the only copy of that staleness
        // left anywhere.
        container.exportNotifier.cancel()
        return archive
    }

    /** Called once the share sheet has been handed the file, so a rotation cannot open it twice. */
    fun clearExported() {
        transient.update { copy(exported = null) }
    }

    /**
     * Remembers the tree the owner picked, **only if the grant behind it can be persisted**.
     *
     * A URI that works until the process dies is the worst possible shape for a backup destination,
     * so a provider that will not persist one leaves the app with no folder rather than with a
     * setting that quietly stops working.
     */
    fun rememberFolder(uri: Uri) {
        viewModelScope.launch {
            val taken = withContext(Dispatchers.IO) { contentResolver.rememberExportFolder(uri) }
            if (taken) {
                container.preferences.setExportFolder(uri.toString())
                transient.update { copy(notice = null) }
            } else {
                transient.update { copy(notice = FolderNotice.NotRemembered) }
            }
        }
    }

    /** Forgets the folder and hands the grant back, so the app holds nothing it is not using. */
    fun forgetFolder() {
        val current = uiState.value.folder
        viewModelScope.launch {
            if (current is ExportFolderState.Remembered) {
                withContext(Dispatchers.IO) { contentResolver.forgetExportFolder(current.uri) }
            }
            container.preferences.setExportFolder(null)
            transient.update { copy(notice = null) }
        }
    }

    /** Reported by the screen when there is no document picker to launch at all. */
    fun folderPickerUnavailable() {
        transient.update { copy(notice = FolderNotice.NoPicker) }
    }

    fun dismissNotice() {
        transient.update { copy(notice = null) }
    }

    /**
     * Switches the recurring export prompt on at an interval, or off with `null`.
     *
     * `LocalDate.now()` here rather than in the preference: switching it on is an event happening
     * now, and the anchor it writes is what keeps a monthly reminder from firing tomorrow morning.
     */
    fun setReminder(interval: ExportInterval?) {
        viewModelScope.launch {
            container.preferences.setExportReminder(interval, LocalDate.now())
            // A prompt in the shade for a reminder that has just been switched off or rescheduled
            // is asking on behalf of a setting that no longer exists.
            if (interval == null) container.exportNotifier.cancel()
        }
    }

    /**
     * Reads what the picked archive claims to be. A file with no readable manifest is refused here,
     * before any confirmation is offered — there is nothing to confirm about a file that is not a
     * backup.
     */
    fun inspect(source: RestoreSource) {
        viewModelScope.launch {
            val manifest = container.backupRestorer.readManifest { source.open() }
            transient.update {
                if (manifest == null) {
                    copy(refusal = RestoreRefusal.NotABinkyBackup)
                } else {
                    copy(pendingRestore = PendingRestore(source, manifest))
                }
            }
        }
    }

    fun cancelRestore() {
        transient.update { copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val pending = transient.value.pendingRestore ?: return
        transient.update { copy(working = BackupWork.Restoring, pendingRestore = null) }
        viewModelScope.launch {
            // Named, because a trailing lambda would bind to `now` — the last parameter — instead.
            val outcome = container.backupRestorer.restore(open = { pending.source.open() })
            transient.update {
                when (outcome) {
                    is RestoreOutcome.Restored -> copy(working = null, restored = outcome)
                    is RestoreOutcome.Refused -> copy(working = null, refusal = outcome.reason)
                }
            }
            refresh.value++
        }
    }

    fun dismissRefusal() {
        transient.update { copy(refusal = null) }
    }

    fun requestDelete(target: PreservedCopy) {
        transient.update { copy(pendingDelete = target) }
    }

    fun cancelDelete() {
        transient.update { copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val target = transient.value.pendingDelete ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deletePreservedCopy(target) }
            transient.update { copy(pendingDelete = null) }
            refresh.value++
        }
    }

    private fun RestoreSource.open(): InputStream =
        when (this) {
            is RestoreSource.Picked ->
                // Null when the owner has revoked the grant, or the provider is gone. Treated as
                // unreadable rather than crashed: a picked file that vanished is not a bug in Binky.
                contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")

            is RestoreSource.Snapshot -> file.inputStream()
        }

    /** Kotlin note: `update` is `MutableStateFlow`'s compare-and-set loop — a safe read-modify-write. */
    private fun MutableStateFlow<TransientState>.update(block: TransientState.() -> TransientState) {
        value = value.block()
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    BackupViewModel(app.container, app.contentResolver)
                }
            }
    }
}
