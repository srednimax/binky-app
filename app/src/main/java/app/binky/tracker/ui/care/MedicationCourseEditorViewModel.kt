package app.binky.tracker.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.MedicationRepository
import app.binky.tracker.data.MedicationTimeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The course form, as one immutable data class (house rule).
 *
 * **[ongoing] is an explicit state, not an empty field** (PLAN 5e). An open course is the normal
 * condition of one an owner is in the middle of, and a blank end date is indistinguishable from a
 * date they meant to fill in and forgot. The switch says which it is, and it defaults to open.
 */
data class MedicationCourseEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameInvalid: Boolean = false,
    /** Free text, exactly as the vet wrote it, and **never required** (ADR-0002). */
    val doseAmount: String = "",
    val startOn: LocalDate = LocalDate.now(),
    val ongoing: Boolean = true,
    val endOn: LocalDate = LocalDate.now(),
    /** Set when the owner tried to save an end before the start — stated, never clamped. */
    val endBeforeStart: Boolean = false,
    val notes: String = "",
    /**
     * The schedule as **rows**, ids and all, because that is what `setTimes` writes back: a chip
     * moved from 08:00 to 09:00 has to arrive as the same row with a new time rather than as a
     * delete and an insert a half-finished edit could leave as neither.
     */
    val times: List<MedicationTimeEntity> = emptyList(),
    val remindersEnabled: Boolean = true,
    val saved: Boolean = false,
) {
    val clockTimes: List<LocalTime> get() = times.map { it.time }.sorted()

    /** Whether the reminder switch is worth showing at all — see [MedicationCourseEditorViewModel]. */
    val hasSchedule: Boolean get() = times.isNotEmpty()
}

/**
 * Add or edit one medication course, **and its daily schedule**.
 *
 * **The reminder switch is absent without times rather than present and inert** (ADR-0003). A course
 * with no [MedicationTimeEntity] rows has no slots to remind about, so a switch offering to remind
 * about them would be a control that does nothing — which is worse than no control, because the
 * owner who turns it on has been told something untrue about what the app will do tonight.
 *
 * The start date **may be in the future**, unlike every other date this app records. "Start her on
 * it tomorrow morning" is how most courses are actually prescribed, and a course that cannot be
 * entered until the morning it begins is a course whose first dose is the one nobody was reminded
 * about.
 */
class MedicationCourseEditorViewModel(
    private val bunnyId: String,
    private val courseId: String?,
    private val medications: MedicationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationCourseEditorUiState(isNew = courseId == null))
    val uiState: StateFlow<MedicationCourseEditorUiState> = _uiState.asStateFlow()

    /** The row as it stands on disk, or null when adding — `createdAt` has to survive an edit. */
    private var existing: MedicationCourseEntity? = null

    init {
        viewModelScope.launch {
            // Read once rather than collecting, so an emission cannot overwrite a half-typed form.
            val loaded = courseId?.let { medications.course(it).first() }
            existing = loaded?.course
            _uiState.update { state ->
                val course = loaded?.course
                state.copy(
                    loading = false,
                    name = course?.name.orEmpty(),
                    doseAmount = course?.doseAmount.orEmpty(),
                    startOn = course?.startOn ?: state.startOn,
                    ongoing = course?.endOn == null,
                    endOn = course?.endOn ?: state.endOn,
                    notes = course?.notes.orEmpty(),
                    times = loaded?.times.orEmpty().sortedBy { it.time },
                    remindersEnabled = course?.remindersEnabled ?: true,
                )
            }
        }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name, nameInvalid = false) }
    }

    fun setDoseAmount(amount: String) {
        _uiState.update { it.copy(doseAmount = amount) }
    }

    fun setStartOn(date: LocalDate) {
        _uiState.update { it.copy(startOn = date, endBeforeStart = false) }
    }

    fun setOngoing(ongoing: Boolean) {
        _uiState.update { it.copy(ongoing = ongoing, endBeforeStart = false) }
    }

    fun setEndOn(date: LocalDate) {
        _uiState.update { it.copy(endOn = date, endBeforeStart = false) }
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    /**
     * Adds a clock time, **silently ignoring one already there**.
     *
     * The unique index on `(courseId, time)` makes "08:00 twice" impossible in the database; this is
     * the same rule one layer up, and it is a no-op rather than an error because tapping *Add* and
     * choosing the time already on screen is a slip, not a request the app should argue with.
     */
    fun addTime(time: LocalTime) {
        _uiState.update { state ->
            if (state.times.any { it.time == time }) {
                state
            } else {
                state.copy(
                    // The placeholder id is replaced by `setTimes`, which copies the real course id
                    // onto every row it writes; a new course goes through `add`, which takes the
                    // bare times and mints its own rows.
                    times =
                        (state.times + MedicationTimeEntity(courseId = courseId.orEmpty(), time = time))
                            .sortedBy { it.time },
                )
            }
        }
    }

    fun removeTime(time: LocalTime) {
        _uiState.update { state -> state.copy(times = state.times.filterNot { it.time == time }) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        _uiState.update { it.copy(remindersEnabled = enabled) }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameInvalid = true) }
            return
        }
        val endOn = if (state.ongoing) null else state.endOn
        // **Rejected with the reason stated, never silently clamped** — the same rule every other
        // entry in this app follows, and the repository refuses it a second time on its own.
        if (endOn != null && endOn.isBefore(state.startOn)) {
            _uiState.update { it.copy(endBeforeStart = true) }
            return
        }

        viewModelScope.launch {
            val row =
                (
                    existing ?: MedicationCourseEntity(
                        bunnyId = bunnyId,
                        name = state.name,
                        doseAmount = state.doseAmount,
                        startOn = state.startOn,
                    )
                ).copy(
                    name = state.name,
                    doseAmount = state.doseAmount,
                    startOn = state.startOn,
                    endOn = endOn,
                    notes = state.notes.ifBlank { null },
                    remindersEnabled = state.remindersEnabled,
                )
            if (existing == null) {
                medications.add(row, times = state.clockTimes)
            } else {
                medications.update(row)
                medications.setTimes(row.id, state.times)
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries arguments (as in the bunny editor). */
        fun factory(
            bunnyId: String,
            courseId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    MedicationCourseEditorViewModel(
                        bunnyId = bunnyId,
                        courseId = courseId,
                        medications = app.container.medicationRepository,
                    )
                }
            }
    }
}
