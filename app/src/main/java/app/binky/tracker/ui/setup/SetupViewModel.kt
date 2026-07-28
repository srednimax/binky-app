package app.binky.tracker.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.backup.BackupScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    /** Defaults to [BackupScope.Records], the same default Backup settings shows (ADR-0005). */
    val scope: BackupScope = BackupScope.Records,
)

/**
 * First-run setup's one ViewModel (ADR-0006), belonging to the backup step — the only step with a
 * choice to store. The bunny step has none: it opens the ordinary bunny editor, which has its own.
 *
 * The scope written here is not wizard state. It is the same preference Backup settings edits and
 * the same one an export defaults to, so it is stored as it is picked rather than at the end: an
 * owner who taps "Everything", then leaves through Back and never returns, has still said what they
 * wanted, and the app has no business forgetting it because they did not reach a Finish button.
 */
class SetupViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val uiState: StateFlow<SetupUiState> =
        container.preferences.backupScope
            .map { scope -> SetupUiState(scope = scope) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun setScope(scope: BackupScope) {
        viewModelScope.launch { container.preferences.setBackupScope(scope) }
    }

    /**
     * Ends the wizard, and is the **only** thing that does. Writing the flag flips
     * `AppContainer.setupState` to `Complete`, and the navigation gate swaps the shell in — so
     * there is no separate "wizard finished" signal that could disagree with what is stored.
     */
    fun finish() {
        viewModelScope.launch { container.preferences.markSetupComplete() }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    SetupViewModel(app.container)
                }
            }
    }
}
