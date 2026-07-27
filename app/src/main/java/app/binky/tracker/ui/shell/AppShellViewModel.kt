package app.binky.tracker.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the app-wide bunny selection for the shell: which bunny the per-bunny screens are scoped
 * to, and what the switcher offers.
 *
 * The selection itself lives on [AppContainer] rather than here, because it outlives any one screen
 * (ADR-0015). This ViewModel only joins it to the names the UI has to draw.
 */
class AppShellViewModel(
    private val container: AppContainer,
) : ViewModel() {
    /**
     * Kotlin note: `combine` re-runs its block whenever *any* source emits — a reactive
     * `combineLatest`, not a `Promise.all` that settles once. `stateIn` then turns the cold `Flow`
     * into a hot `StateFlow` with an always-readable current value, which is what Compose needs;
     * `WhileSubscribed` stops the database queries a few seconds after the last screen goes away.
     */
    val uiState: StateFlow<ShellUiState> =
        combine(
            container.selectedBunny,
            container.bunnyRepository.activeBunnies,
            container.bunnyRepository.archivedBunnies,
        ) { selection, active, archived ->
            val scopedId =
                when (selection) {
                    is BunnySelection.Single -> selection.id
                    is BunnySelection.Archived -> selection.id
                    else -> null
                }
            ShellUiState(
                selection = selection,
                activeBunnies = active.map { it.toSummary(container.mediaFiles) },
                // Archived bunnies are looked up too: the read-only scope names a bunny that is
                // deliberately absent from the switcher's list.
                scopedBunny = (active + archived).find { it.id == scopedId }?.toSummary(container.mediaFiles),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    /**
     * Kotlin note: the container's setters are `suspend` (they write to DataStore), so they need a
     * coroutine to run in. `viewModelScope.launch` is fire-and-forget, cancelled with the ViewModel.
     */
    fun selectBunny(bunnyId: String) {
        viewModelScope.launch { container.select(bunnyId) }
    }

    fun selectAllBunnies() {
        viewModelScope.launch { container.selectAllBunnies() }
    }

    /**
     * Enters the read-only scope onto an archived bunny, from the archived list under More. In
     * memory only — a background kill must not reopen the app into a memorial (ADR-0015).
     */
    fun openArchivedScope(bunnyId: String) = container.openArchived(bunnyId)

    /** Leaves the read-only scope onto an archived bunny. In memory only — never persisted. */
    fun closeArchivedScope() = container.closeArchived()

    companion object {
        /**
         * Manual DI, not Hilt (house rule): the factory reaches the one [AppContainer] through the
         * Application. `viewModel(factory = AppShellViewModel.Factory)` is the call site.
         */
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    AppShellViewModel(app.container)
                }
            }
    }
}

/**
 * The relative path stored on the row is resolved here, at read time (house rule), so no composable
 * has to know where `filesDir` is. The file may be missing; the avatar renders a placeholder.
 */
private fun BunnyEntity.toSummary(media: MediaFiles) =
    BunnySummary(id = id, name = name, avatar = avatarPath?.let(media::resolve))
