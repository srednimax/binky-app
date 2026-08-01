package app.binky.tracker.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.CareInterval
import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareReminderEntity
import app.binky.tracker.data.CareRepository
import app.binky.tracker.data.CareType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The reminder form, as one immutable data class (house rule).
 *
 * [intervalCount] is a `String` rather than an `Int` for the same reason the weight field is: it is
 * what the owner has typed so far, which includes "" and "1" on the way to "12".
 *
 * **[type] null means "something else"**, which is the free-text path and not a missing value
 * (ADR-0018). A reminder with no type is normal; a reminder with neither a type nor a label is the
 * one combination the repository refuses, and [labelInvalid] is where the owner hears about it.
 */
data class CareReminderEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: CareType? = null,
    val label: String = "",
    val intervalCount: String = "1",
    val intervalUnit: CareIntervalUnit = CareIntervalUnit.WEEK,
    val firstDueOn: LocalDate = LocalDate.now(),
    val labelInvalid: Boolean = false,
    val intervalInvalid: Boolean = false,
    /** Flipped once the write has landed, which is the screen's cue to leave. */
    val saved: Boolean = false,
) {
    val parsedIntervalCount: Int? get() = intervalCount.trim().toIntOrNull()?.takeIf { it > 0 }

    /**
     * Whether the name field is shown at all.
     *
     * A preset resolves its name through `strings.xml`, so the field is offered but not required —
     * an owner who wants *Front claws* on a nail trim gets it, and one who does not gets a
     * translated label rather than an English one stored in their database.
     */
    val needsLabel: Boolean get() = type == null

    /** The interval as one value, for the "Every 6 weeks" line the form shows back. */
    val previewInterval: CareInterval? get() = parsedIntervalCount?.let { CareInterval(it, intervalUnit) }
}

/**
 * Add or edit one care reminder: `{label, interval, optional type}` (ADR-0018).
 *
 * **The three presets are the closed enum, and "something else" is the rest of the world.** Picking
 * a preset fills its default interval — six weeks for a nail trim, yearly for a vaccination, weekly
 * for a weigh-in — because those are facts about the care rather than about this form, which is why
 * they live on [CareType].
 *
 * The anchor is a **due date, not a pseudo-completion**: the form asks when it is next due, which is
 * what a vet card says. An owner who knows when it was last done records that as a real completion,
 * where it is visible and correctable.
 */
class CareReminderEditorViewModel(
    private val bunnyId: String,
    private val reminderId: String?,
    private val care: CareRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CareReminderEditorUiState(isNew = reminderId == null))
    val uiState: StateFlow<CareReminderEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding — the fields an edit must not lose. */
    private var existing: CareReminderEntity? = null

    init {
        viewModelScope.launch {
            val reminder = reminderId?.let { care.reminderNow(it) }
            existing = reminder
            _uiState.update { state ->
                if (reminder == null) {
                    state.copy(loading = false)
                } else {
                    state.copy(
                        loading = false,
                        type = reminder.type,
                        label = reminder.label.orEmpty(),
                        intervalCount = reminder.intervalCount.toString(),
                        intervalUnit = reminder.intervalUnit,
                        firstDueOn = reminder.firstDueOn,
                    )
                }
            }
        }
    }

    /**
     * Picking a kind, including "something else" (`null`).
     *
     * **The interval follows the preset only while the form is new**, and only on the way in: an
     * owner editing a vaccination they had set to two years must not have it silently reset to one
     * because they re-tapped the same chip.
     */
    fun setType(type: CareType?) {
        _uiState.update { state ->
            val defaults = type?.takeIf { state.isNew }
            state.copy(
                type = type,
                intervalCount = defaults?.defaultIntervalCount?.toString() ?: state.intervalCount,
                intervalUnit = defaults?.defaultIntervalUnit ?: state.intervalUnit,
                labelInvalid = false,
            )
        }
    }

    fun setLabel(label: String) {
        _uiState.update { it.copy(label = label, labelInvalid = false) }
    }

    fun setIntervalCount(count: String) {
        _uiState.update { it.copy(intervalCount = count, intervalInvalid = false) }
    }

    fun setIntervalUnit(unit: CareIntervalUnit) {
        _uiState.update { it.copy(intervalUnit = unit) }
    }

    fun setFirstDueOn(date: LocalDate) {
        _uiState.update { it.copy(firstDueOn = date) }
    }

    /**
     * Validates, then writes.
     *
     * The two checks mirror `CareRepository`'s own invariants rather than trusting them: the
     * repository throws, and a form that let the owner reach a throw would have nothing to say
     * afterwards.
     */
    fun save() {
        val state = _uiState.value
        val label = state.label.trim()
        val count = state.parsedIntervalCount

        if (state.needsLabel && label.isEmpty()) {
            _uiState.update { it.copy(labelInvalid = true) }
            return
        }
        if (count == null) {
            _uiState.update { it.copy(intervalInvalid = true) }
            return
        }

        viewModelScope.launch {
            val reminder =
                existing?.copy(
                    // `notifiedForDueOn` and `calendarHandedOffAt` ride along untouched. The
                    // watermark needs no clearing on any path (ADR-0002): it is compared against
                    // the derived due date, and changing the interval moves that date, which makes
                    // the stored value stale on its own.
                    label = label.ifEmpty { null },
                    type = state.type,
                    intervalCount = count,
                    intervalUnit = state.intervalUnit,
                    firstDueOn = state.firstDueOn,
                ) ?: CareReminderEntity(
                    bunnyId = bunnyId,
                    label = label.ifEmpty { null },
                    type = state.type,
                    intervalCount = count,
                    intervalUnit = state.intervalUnit,
                    firstDueOn = state.firstDueOn,
                )

            if (existing == null) care.add(reminder) else care.update(reminder)
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments. */
        fun factory(
            bunnyId: String,
            reminderId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    CareReminderEditorViewModel(
                        bunnyId = bunnyId,
                        reminderId = reminderId,
                        care = app.container.careRepository,
                    )
                }
            }
    }
}
