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
import app.binky.tracker.data.PreservedCopy
import app.binky.tracker.data.backup.BackupManifest
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.data.backup.RestoreOutcome
import app.binky.tracker.data.backup.RestoreRefusal
import app.binky.tracker.data.backup.exportFileName
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
    /** Both occupants of `preserved/`, newest first — wipe copies and restore snapshots. */
    val preserved: List<PreservedCopy> = emptyList(),
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
    )

    val uiState: StateFlow<BackupUiState> =
        combine(
            container.preferences.backupScope,
            refresh,
            transient,
        ) { scope, _, now ->
            BackupUiState(
                scope = scope,
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
            val now = Instant.now()
            val scope = uiState.value.scope
            val archive =
                container.backupExporter.exportTo(
                    target = File(container.exportsDir, exportFileName(scope, now)),
                    scope = scope,
                    now = now,
                )
            transient.update { copy(working = null, exported = archive) }
        }
    }

    /** Called once the share sheet has been handed the file, so a rotation cannot open it twice. */
    fun clearExported() {
        transient.update { copy(exported = null) }
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
