package app.binky.tracker.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.VetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VetsUiState(
    val vets: List<VetEntity> = emptyList(),
    /** Set while the one delete confirmation is up. */
    val pendingDelete: VetEntity? = null,
)

/**
 * The vet directory — **app-wide**, so this `ViewModel` reads no selection at all (ADR-0017).
 *
 * That absence is the whole difference between this screen and every other list in the app, and it
 * is why the directory lives in More rather than on the bunny-scoped Care tab.
 */
class VetsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val vets = container.vetRepository

    /**
     * Held as an **id** rather than as the row, so a vet renamed underneath an open dialog
     * re-resolves to the current row instead of confirming against a stale copy — the same shape
     * the Care list uses for its two dialogs.
     */
    private val pendingDelete = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VetsUiState> =
        combine(vets.vets, pendingDelete) { directory, deleting ->
            VetsUiState(
                vets = directory,
                pendingDelete = directory.firstOrNull { it.id == deleting },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VetsUiState())

    fun requestDelete(vet: VetEntity) {
        pendingDelete.value = vet.id
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * **One** confirmation, and it destroys nothing but the directory entry: every visit that named
     * this vet keeps its row and loses only the name (ADR-0017, ADR-0004).
     */
    fun confirmDelete() {
        val id = pendingDelete.value ?: return
        viewModelScope.launch {
            vets.delete(id)
            pendingDelete.value = null
        }
    }

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
