package app.binky.tracker.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.DoseEntity
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.MedicationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class MedicationCourseUiState(
    val loading: Boolean = true,
    val course: MedicationCourseEntity? = null,
    val times: List<LocalTime> = emptyList(),
    val next: DoseNext = DoseNext.NoSchedule,
    /** Every dose recorded against this course, newest first — given, skipped and ad hoc alike. */
    val doses: List<DoseEntity> = emptyList(),
    /** Set while the record-a-dose dialog is up. */
    val recording: Boolean = false,
    /** Set while one recorded dose is being corrected. */
    val editingDose: DoseEntity? = null,
    val pendingDoseDelete: DoseEntity? = null,
    /** Set while the confirmation for deleting the **whole course** is up. */
    val confirmingDelete: Boolean = false,
    /**
     * Flipped when the course is no longer there — deleted from the list behind this screen, or with
     * its bunny. The screen's cue to leave rather than render an empty shell.
     */
    val gone: Boolean = false,
) {
    /** Whether *End course* is worth offering: an already-ended course has nothing to close. */
    val open: Boolean get() = course != null && next !is DoseNext.Ended
}

/**
 * One medication course: its schedule, everything recorded against it, and the two things only this
 * screen offers — recording a dose by hand, and correcting one.
 *
 * **The history is editable because a dose recorded against the wrong slot is a wrong record**
 * (PLAN 5e), and an owner notices that an hour later rather than at the tap. The tab behind this
 * screen shows only today, which is the symptom; the whole record lives here.
 *
 * **A past day lists what was recorded and never a gap** (ADR-0002, ADR-0001). There is no
 * derivation in this screen's history and no empty slot rendered as an unanswered one: today and
 * later derive slots, and yesterday is exactly the rows in `doses`. Deriving backwards would use
 * *today's* times to decide what last week should have contained, which is an edit rewriting
 * history it never touched.
 */
class MedicationCourseViewModel(
    private val courseId: String,
    private val medications: MedicationRepository,
) : ViewModel() {
    private val recording = MutableStateFlow(false)
    private val editingDose = MutableStateFlow<String?>(null)
    private val pendingDoseDelete = MutableStateFlow<String?>(null)

    /**
     * Deleting the **course**, which arrived here in Phase 7 when `3a` gave the list behind this
     * screen 64dp rows with nowhere to put a button. It is the same decision `Weight` made at `1d`:
     * the list navigates, the thing's own screen destroys.
     */
    private val confirmingDelete = MutableStateFlow(false)

    /** Grouped so the state combine below stays inside `combine`'s five-argument overload. */
    private val dialogs: Flow<Dialogs> =
        combine(
            recording,
            editingDose,
            pendingDoseDelete,
            confirmingDelete,
        ) { open, editing, deleting, deletingCourse ->
            Dialogs(open, editing, deleting, deletingCourse)
        }

    /**
     * Kotlin note: `flatMapLatest` re-subscribes when the course itself changes, which is what lets
     * the derivation start only once the bunny is known — the id is on the course, and there is
     * nothing to derive before it arrives.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MedicationCourseUiState> =
        medications
            .course(courseId)
            .flatMapLatest { withTimes ->
                if (withTimes == null) {
                    flowOf(MedicationCourseUiState(loading = false, gone = true))
                } else {
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    combine(
                        medications.doses(courseId),
                        medications.schedule(
                            bunnyId = withTimes.course.bunnyId,
                            days = NEXT_DOSE_DAYS,
                            zone = zone,
                            today = today,
                        ),
                        dialogs,
                    ) { doses, slots, open ->
                        MedicationCourseUiState(
                            loading = false,
                            course = withTimes.course,
                            times = withTimes.clockTimes,
                            next =
                                doseNext(
                                    course = withTimes.course,
                                    hasTimes = withTimes.times.isNotEmpty(),
                                    slots = slots,
                                    now = Instant.now(),
                                    today = today,
                                ),
                            doses = doses,
                            recording = open.recording,
                            editingDose = doses.firstOrNull { it.id == open.editing },
                            pendingDoseDelete = doses.firstOrNull { it.id == open.deleting },
                            confirmingDelete = open.deletingCourse,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationCourseUiState())

    fun startRecording() {
        recording.value = true
    }

    fun cancelRecording() {
        recording.value = false
    }

    /**
     * Records a dose that answers no derived slot — **normal, not an error** (ADR-0002).
     *
     * Both halves of the slot key stay null, which is also what leaves the row out of the unique
     * index, so there can be any number of them on one day.
     */
    fun recordAdHoc(
        status: DoseStatus,
        recordedAt: Instant,
        note: String?,
    ) {
        viewModelScope.launch {
            medications.recordAdHoc(
                courseId = courseId,
                status = status,
                note = note,
                recordedAt = recordedAt,
            )
            recording.value = false
        }
    }

    fun startDoseEdit(dose: DoseEntity) {
        editingDose.value = dose.id
    }

    fun cancelDoseEdit() {
        editingDose.value = null
    }

    /**
     * Corrects one recorded dose.
     *
     * **The slot it answered is not changed**, only what was said about it: moving a dose from one
     * slot to another is two operations on two slots and the unique index has an opinion about both,
     * where deleting the row and recording again is a path the owner already has. Status, time and
     * note are what a correction actually is an hour later.
     */
    fun updateDose(
        status: DoseStatus,
        recordedAt: Instant,
        note: String?,
    ) {
        val id = editingDose.value ?: return
        viewModelScope.launch {
            val existing = medications.doseNow(id) ?: return@launch
            medications.updateDose(existing.copy(status = status, recordedAt = recordedAt, note = note))
            editingDose.value = null
        }
    }

    fun requestDoseDelete(dose: DoseEntity) {
        pendingDoseDelete.value = dose.id
    }

    fun cancelDoseDelete() {
        pendingDoseDelete.value = null
    }

    /** Deletes one recorded dose. The slot it answered goes back to **unanswered**, never to missed. */
    fun confirmDoseDelete() {
        val id = pendingDoseDelete.value ?: return
        viewModelScope.launch {
            medications.deleteDose(id)
            pendingDoseDelete.value = null
        }
    }

    /**
     * Closes an open course, which is setting its end to today (ADR-0002).
     *
     * Every dose already recorded stays; only the slots after today stop being derived. There is no
     * second "active" flag that could disagree with the date.
     */
    fun endCourse() {
        viewModelScope.launch { medications.endCourse(courseId) }
        // Closing from inside the delete confirmation is the whole point of offering it there.
        confirmingDelete.value = false
    }

    fun requestDelete() {
        confirmingDelete.value = true
    }

    fun cancelDelete() {
        confirmingDelete.value = false
    }

    /**
     * Destroys the course, its schedule and every dose recorded against it, by cascade.
     *
     * Nothing navigates here. `medications.course(courseId)` emits null the moment the row is gone,
     * which sets `gone`, and the screen leaves on that — one exit path whether the course was
     * deleted here, deleted with its bunny, or wiped by a restore.
     */
    fun confirmDelete() {
        viewModelScope.launch {
            medications.delete(courseId)
            confirmingDelete.value = false
        }
    }

    private data class Dialogs(
        val recording: Boolean,
        val editing: String?,
        val deleting: String?,
        val deletingCourse: Boolean,
    )

    companion object {
        /** A factory *function*, because the navigation key carries an argument. */
        fun factory(courseId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    MedicationCourseViewModel(
                        courseId = courseId,
                        medications = app.container.medicationRepository,
                    )
                }
            }
    }
}
