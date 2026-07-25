package app.bunny.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bunny.tracker.AppContainer
import app.bunny.tracker.BunnyTrackerApplication
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.ui.bunny.BunnyActions
import app.bunny.tracker.ui.bunny.BunnyDialog
import app.bunny.tracker.ui.bunny.BunnyProfile
import app.bunny.tracker.ui.bunny.toProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Home, in both of its shapes: one bunny's profile, and under "All bunnies" the dashboard that
 * **is** the bunny list (ADR-0015).
 */
data class HomeUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    /** One profile under a single selection, every active bunny under "All bunnies". */
    val profiles: List<BunnyProfile> = emptyList(),
    val dialog: BunnyDialog? = null,
) {
    /** An archived bunny is a read-only scope: no write actions (ADR-0015). */
    val readOnly: Boolean get() = selection is BunnySelection.Archived
}

class HomeViewModel(
    container: AppContainer,
) : ViewModel() {
    private val actions = BunnyActions(container.bunnyRepository, viewModelScope)

    val uiState: StateFlow<HomeUiState> =
        combine(
            container.selectedBunny,
            container.bunnyRepository.activeBunnies,
            container.bunnyRepository.archivedBunnies,
            actions.dialog,
        ) { selection, active, archived, dialog ->
            // Both lists, so an archived housemate still shows up on a profile as one — the
            // fluffle survives archival, and the survivor genuinely did live with them (ADR-0008).
            val everyBunny = active + archived
            val shown =
                when (selection) {
                    BunnySelection.All -> active
                    is BunnySelection.Single -> everyBunny.filter { it.id == selection.id }
                    is BunnySelection.Archived -> everyBunny.filter { it.id == selection.id }
                    BunnySelection.Loading, BunnySelection.Empty -> emptyList()
                }
            HomeUiState(
                selection = selection,
                profiles = shown.map { it.toProfile(everyBunny, container.mediaFiles) },
                dialog = dialog,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun requestArchive(profile: BunnyProfile) = actions.requestArchive(profile)

    fun requestDelete(profile: BunnyProfile) = actions.requestDelete(profile)

    fun confirmDialog() = actions.confirm()

    fun dismissDialog() = actions.dismiss()

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BunnyTrackerApplication
                    HomeViewModel(app.container)
                }
            }
    }
}
