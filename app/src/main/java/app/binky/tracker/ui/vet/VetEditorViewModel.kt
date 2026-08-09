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
    /**
     * The name as it stands **on disk**, for the delete confirmation to name.
     *
     * [name] is what is in the box, and the two differ exactly when the owner has retyped it and not
     * saved — where a dialog saying a half-typed name goes from the directory would be naming
     * something that was never in it. Same reason the visit editor keeps its stored weight.
     */
    val storedName: String = "",
    val clinic: String = "",
    val phone: String = "",
    val notes: String = "",
    /** Flipped once the write has landed, which is the screen's cue to leave. */
    val saved: Boolean = false,
    /** Set while the one delete confirmation is up. */
    val confirmingDelete: Boolean = false,
    /** [saved]'s counterpart for the other way out of this screen. */
    val deleted: Boolean = false,
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
                    storedName = vet?.name.orEmpty(),
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

    fun requestDelete() {
        _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    /**
     * **One** confirmation, and it destroys nothing but the directory entry: every visit that named
     * this vet keeps its row and loses only the name (ADR-0017, ADR-0004).
     *
     * Hosted here from Phase 7. The directory list draws rows with a chevron and nowhere to put a
     * button (`5a`), which is the finding `Weight` made at `1d`; the id comes from the screen's own
     * argument now rather than from a pending-delete flag on the list.
     */
    fun confirmDelete() {
        val id = vetId ?: return
        viewModelScope.launch {
            vets.delete(id)
            _uiState.update { it.copy(confirmingDelete = false, deleted = true) }
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
