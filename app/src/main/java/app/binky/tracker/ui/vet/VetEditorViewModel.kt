package app.binky.tracker.ui.vet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.VetEntity
import app.binky.tracker.data.VetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The vet form, as one immutable data class (house rule).
 *
 * Every field but the name is optional and held as a plain `String` rather than a `String?`, because
 * that is what a text field has: "" *is* the empty state, and the repository is what turns it back
 * into a null column.
 */
data class VetEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameInvalid: Boolean = false,
    val clinic: String = "",
    val phone: String = "",
    val notes: String = "",
    /** Flipped once the write has landed, which is the screen's cue to leave. */
    val saved: Boolean = false,
)

/**
 * Add or edit one vet.
 *
 * **Only the name is required**, deliberately: the useful moment to add a vet is mid-visit-entry
 * with a rabbit on your lap, and a form that demands a clinic address then is a form that gets
 * skipped (ADR-0017).
 */
class VetEditorViewModel(
    private val vetId: String?,
    private val vets: VetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VetEditorUiState(isNew = vetId == null))
    val uiState: StateFlow<VetEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding — [createdAt] has to survive an edit. */
    private var existing: VetEntity? = null

    init {
        viewModelScope.launch {
            // Read once rather than collecting: a form fed by a Flow would overwrite the owner's
            // half-typed phone number every time the row it is editing emitted again.
            val vet = vetId?.let { vets.vetNow(it) }
            existing = vet
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    name = vet?.name.orEmpty(),
                    clinic = vet?.clinic.orEmpty(),
                    phone = vet?.phone.orEmpty(),
                    notes = vet?.notes.orEmpty(),
                )
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameInvalid = false) }
    }

    fun onClinicChanged(clinic: String) {
        _uiState.update { it.copy(clinic = clinic) }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameInvalid = true) }
            return
        }

        viewModelScope.launch {
            val row =
                (existing ?: VetEntity(name = state.name)).copy(
                    name = state.name,
                    clinic = state.clinic,
                    phone = state.phone,
                    notes = state.notes,
                )
            if (existing == null) vets.add(row) else vets.update(row)
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries an argument (as in the bunny editor). */
        fun factory(vetId: String?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    VetEditorViewModel(vetId = vetId, vets = app.container.vetRepository)
                }
            }
    }
}
