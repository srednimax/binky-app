package app.binky.tracker.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.ui.bunny.BunnyActions
import app.binky.tracker.ui.bunny.BunnyDialog
import app.binky.tracker.ui.bunny.BunnyProfile
import app.binky.tracker.ui.bunny.toProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ArchivedBunniesUiState(
    val loading: Boolean = true,
    val profiles: List<BunnyProfile> = emptyList(),
    val dialog: BunnyDialog? = null,
)

/**
 * The archived list (ADR-0004). Archiving that keeps records nobody can reach is indistinguishable
 * from deleting them, so this screen is what makes the "every record is kept" claim true: unarchive,
 * delete, and a way into the read-only scope over the bunny's records (ADR-0015).
 */
class ArchivedBunniesViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val actions = BunnyActions(container.bunnyRepository, viewModelScope)

    val uiState: StateFlow<ArchivedBunniesUiState> =
        combine(
            container.bunnyRepository.archivedBunnies,
            container.bunnyRepository.activeBunnies,
            actions.dialog,
        ) { archived, active, dialog ->
            ArchivedBunniesUiState(
                loading = false,
                profiles = archived.map { it.toProfile(active + archived, container.mediaFiles) },
                dialog = dialog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchivedBunniesUiState())

    /**
     * Enters the read-only scope onto an archived bunny. In memory only — a background kill must
     * not reopen the app into a memorial (ADR-0015).
     */
    fun open(bunnyId: String) = container.openArchived(bunnyId)

    fun unarchive(bunnyId: String) = actions.unarchive(bunnyId)

    fun requestDelete(profile: BunnyProfile) = actions.requestDelete(profile)

    fun confirmDialog() = actions.confirm()

    fun dismissDialog() = actions.dismiss()

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    ArchivedBunniesViewModel(app.container)
                }
            }
    }
}
