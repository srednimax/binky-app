package app.binky.tracker.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.EventEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The event form, as one immutable data class (house rule).
 *
 * **[label] is the whole record** (ADR-0031), so it is the only required field and [labelInvalid] is
 * the only validation on the screen. The date defaults to today and is never wrong — see
 * [EventEditorViewModel].
 */
data class EventEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val label: String = "",
    val occursOn: LocalDate = LocalDate.now(),
    val note: String = "",
    val labelInvalid: Boolean = false,
    /** Whether ADR-0014's one-way hand-off has already happened, so the button stops offering it. */
    val calendarHandedOff: Boolean = false,
    val confirmingDelete: Boolean = false,
    /**
     * Flipped once a write has landed — a save **or** a delete — which is the screen's cue to leave.
     *
     * One flag rather than two, because the screen does the same thing either way and two would let
     * a delete-then-save race decide it twice.
     */
    val finished: Boolean = false,
)

/**
 * Add or edit one event: a label, a day and an optional note (ADR-0031).
 *
 * **The date refuses nothing.** Every other dated write in this app is a record of something that
 * happened, so a weighing, an observation and a vet visit all reject tomorrow; an event is as often
 * an appointment as a keepsake, and a picker that would not let an owner enter their rabbit's
 * neutering next Thursday would throw away half of what this screen is for.
 *
 * **Delete lives here**, not on the timeline row. That is `1d`'s finding — the same one that moved
 * deleting a weighing, a course, a reminder and a visit onto their own screens — and an event has no
 * detail screen of its own, so the editor *is* that screen. It cancels the notification with the row
 * for the reason [EventsViewModel.delete] does: a notice in the shade for a row that no longer
 * exists is the last copy of that claim left anywhere.
 */
class EventEditorViewModel(
    private val bunnyId: String,
    private val eventId: String?,
    private val container: AppContainer,
) : ViewModel() {
    private val events = container.eventRepository

    private val _uiState = MutableStateFlow(EventEditorUiState(isNew = eventId == null))
    val uiState: StateFlow<EventEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding — the fields an edit must not lose. */
    private var existing: EventEntity? = null

    init {
        viewModelScope.launch {
            val event = eventId?.let { events.eventNow(it) }
            existing = event
            _uiState.update { state ->
                if (event == null) {
                    state.copy(loading = false)
                } else {
                    state.copy(
                        loading = false,
                        label = event.label,
                        occursOn = event.occursOn,
                        note = event.note.orEmpty(),
                        calendarHandedOff = event.calendarHandedOffAt != null,
                    )
                }
            }
        }
    }

    fun setLabel(label: String) {
        _uiState.update { it.copy(label = label, labelInvalid = false) }
    }

    fun setOccursOn(date: LocalDate) {
        _uiState.update { it.copy(occursOn = date) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    /**
     * Validates, then writes.
     *
     * The check mirrors `EventRepository`'s own invariant rather than trusting it: the repository
     * throws on a blank label, and a form that let the owner reach a throw would have nothing to say
     * afterwards.
     */
    fun save() {
        val state = _uiState.value
        val label = state.label.trim()
        if (label.isEmpty()) {
            _uiState.update { it.copy(labelInvalid = true) }
            return
        }

        viewModelScope.launch {
            val event =
                existing?.copy(
                    // `notifiedAt` and `calendarHandedOffAt` ride along untouched. Moving the date
                    // does **not** re-arm the notice: an event announces itself once, and an owner
                    // correcting a typo in the year is not asking to be told about it again
                    // (ADR-0031). Cancelling and re-arming would be a second way to be shouted at.
                    label = label,
                    occursOn = state.occursOn,
                    note = state.note,
                ) ?: EventEntity(
                    bunnyId = bunnyId,
                    label = label,
                    occursOn = state.occursOn,
                    note = state.note,
                )

            if (existing == null) events.add(event) else events.update(event)
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun requestDelete() {
        _uiState.update { it.copy(confirmingDelete = true) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(confirmingDelete = false) }
    }

    fun confirmDelete() {
        val id = eventId ?: return
        viewModelScope.launch {
            events.delete(id)
            container.eventNotifier.cancel(id)
            _uiState.update { it.copy(confirmingDelete = false, finished = true) }
        }
    }

    /**
     * Records that the hand-off happened, so the button stops offering it (ADR-0014).
     *
     * Called only when the calendar actually opened — the screen holds that condition, because
     * whether an `ACTION_INSERT` found anything to open is a fact about the phone rather than about
     * the row.
     */
    fun markCalendarHandedOff() {
        val id = eventId ?: return
        viewModelScope.launch {
            events.markCalendarHandedOff(id)
            _uiState.update { it.copy(calendarHandedOff = true) }
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments. */
        fun factory(
            bunnyId: String,
            eventId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    EventEditorViewModel(bunnyId = bunnyId, eventId = eventId, container = app.container)
                }
            }
    }
}
