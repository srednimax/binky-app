package app.binky.tracker.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.VetEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class VetsUiState(
    val vets: List<VetEntity> = emptyList(),
)

/**
 * The vet directory — **app-wide**, so this `ViewModel` reads no selection at all (ADR-0017).
 *
 * That absence is the whole difference between this screen and every other list in the app, and it
 * is why the directory lives in More rather than on the bunny-scoped Care tab.
 *
 * **It holds nothing but the list from Phase 7.** Deleting a vet moved onto the editor with the
 * redraw (`5a`, following `1d`), and the confirmation went with it — so the pending-delete id, the
 * three methods that drove it and the `combine` that folded it into the state are all in
 * [VetEditorViewModel] now.
 */
class VetsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val uiState: StateFlow<VetsUiState> =
        container.vetRepository.vets
            .map { VetsUiState(vets = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VetsUiState())

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    VetsViewModel(app.container)
                }
            }
    }
}
