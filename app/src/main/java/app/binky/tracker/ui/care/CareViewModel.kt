package app.binky.tracker.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.CareType
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.MedicationCourseEntity
import app.binky.tracker.data.ScheduledCare
import app.binky.tracker.data.ScheduledDose
import app.binky.tracker.data.bunnyId
import app.binky.tracker.data.readOnlyScope
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

/**
 * One reminder as the Care list draws it: the schedule, and the same date said in words.
 *
 * [due] is computed against the day the flow last emitted rather than held as a date the row
 * renders. A row that said "Due tomorrow" when the screen opened at 23:58 is wrong two minutes
 * later, and recomputing on the next emission is what an owner who reopens the app gets — which is
 * every path that matters, since nothing else about the row can change without one.
 */
data class CareRow(
    val scheduled: ScheduledCare,
    val due: CareDue,
) {
    val id: String get() = scheduled.reminder.id

    /**
     * Whether *Done* opens the weight form instead of the completion dialog.
     *
     * A weigh-in marked done with no weight behind it is the one outcome that makes the reminder
     * pointless, and it is reachable by accident the moment the button is a generic "Done".
     */
    val completedByWeighing: Boolean get() = scheduled.reminder.type == CareType.WEIGH_IN
}

/**
 * One vet visit as the Care list draws it (ADR-0017).
 *
 * [vetName] is null both when the visit named no vet and when the vet it named has since been
 * deleted — the two are indistinguishable on screen and deliberately so: a clinic closing does not
 * make last year's visit a different record, and the row still says what it was for.
 *
 * [weightGrams] is the weighing taken at the visit, if there was one. **One row, never a copy**: it
 * is read back through the join rather than stored on the visit (ADR-0017).
 */
data class VisitRow(
    val id: String,
    val visitedOn: LocalDate,
    val reason: String,
    val vetName: String?,
    val weightGrams: Int?,
)

/**
 * One medication course as the list draws it: what it is, when it is taken, and what is next.
 *
 * [next] is derived per emission for the reason [CareRow.due] is — a row that said "Next dose at
 * 20:00" is a different row once 20:00 has been answered, and the answer is what emits.
 */
data class CourseRow(
    val course: MedicationCourseEntity,
    val times: List<LocalTime>,
    val next: DoseNext,
) {
    val id: String get() = course.id

    /** Whether this course will actually arm anything: a switch without times reminds of nothing. */
    val schedules: Boolean get() = times.isNotEmpty() && course.remindersEnabled && next !is DoseNext.Ended
}

/**
 * A course the owner has asked to delete, **and the size of what that would take**.
 *
 * The count is carried rather than looked up while the dialog draws, because it is one `COUNT(*)`
 * and a composable is the wrong place to suspend. It is read once, at the tap — a dose recorded in
 * the second between the tap and the confirmation would make it stale by one, which is a price
 * worth paying to keep the number out of the render path.
 */
data class PendingCourseDelete(
    val row: CourseRow,
    val doseCount: Int,
)

data class CareUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    val rows: List<CareRow> = emptyList(),
    /** This bunny's vet visits, newest first — the tab's second list (ADR-0017, PLAN 5c). */
    val visits: List<VisitRow> = emptyList(),
    /** This bunny's medication courses, active first then ended (PLAN 5e). */
    val courses: List<CourseRow> = emptyList(),
    /** Every slot derived for **today**, answered or not — the question the tab opens on. */
    val todaysDoses: List<ScheduledDose> = emptyList(),
    /** Set while the one delete confirmation is up (ADR-0004 reserves its ceremony for a bunny). */
    val pendingDelete: CareRow? = null,
    /** Set while the completion sheet is up. Never a weigh-in — see [CareRow.completedByWeighing]. */
    val completing: CareRow? = null,
    /** Set while the visit's delete dialog is up, which is where its weighing's fate is chosen. */
    val pendingVisitDelete: VisitRow? = null,
    /** Set while a course's delete dialog is up, carrying the number of doses it would destroy. */
    val pendingCourseDelete: PendingCourseDelete? = null,
) {
    val bunnyId: String? get() = selection.bunnyId

    /** An archived bunny's reminders are readable and nothing more (ADR-0004, ADR-0015). */
    val readOnly: Boolean get() = selection.readOnlyScope

    /**
     * Whether anything on this screen will actually place an alarm.
     *
     * What gates the delivery line. A bunny whose only course has no times has nothing to deliver,
     * and four sentences about how reliably Android wakes the app would be describing a mechanism
     * that is not going to run.
     */
    val anyDoseReminders: Boolean get() = courses.any { it.schedules }
}

/**
 * The Care tab: what is due, what is overdue, what is merely scheduled — **this bunny's vet
 * visits** (PLAN 5c), **and the medication courses they are on** (PLAN 5e).
 *
 * **Nothing here writes a due date**, because none is stored (ADR-0002) — the care rows arrive
 * already resolved from `CareRepository.schedule`, the doses from `MedicationRepository.schedule`,
 * and recording against either is what moves them. That is also why completing needs no follow-up
 * write: the next occurrence is derived from the completion the moment it lands.
 *
 * **One `ViewModel` for the whole tab** (house rule), which is why visits and medications arrive
 * here rather than through two more of their own: the tab is a hub over one bunny's ongoing care,
 * and three `ViewModel`s would be three answers to "which bunny is this?".
 */
class CareViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val care = container.careRepository
    private val visits = container.visitRepository
    private val medications = container.medicationRepository

    private val pendingDelete = MutableStateFlow<String?>(null)
    private val completing = MutableStateFlow<String?>(null)
    private val pendingVisitDelete = MutableStateFlow<String?>(null)
    private val pendingCourseDelete = MutableStateFlow<CourseDeleteRequest?>(null)

    /**
     * The four dialog flags as one flow.
     *
     * Purely so the combine below stays within `combine`'s five-argument overload — grouping them
     * costs one private type and keeps the alternative, an array-typed vararg combine with
     * positional casts, out of the file.
     */
    private val dialogs: Flow<Dialogs> =
        combine(
            pendingDelete,
            completing,
            pendingVisitDelete,
            pendingCourseDelete,
        ) { deleting, completingId, deletingVisit, deletingCourse ->
            Dialogs(deleting, completingId, deletingVisit, deletingCourse)
        }

    /**
     * Kotlin note: `flatMapLatest` swaps to a new inner Flow whenever the selection changes and
     * cancels the previous subscription, so switching bunny stops collecting the old bunny's
     * schedule rather than leaving it running.
     *
     * The dialog flags are held as **ids** rather than as rows, so a row that moves underneath an
     * open dialog — a weight logged in another tab moves a weigh-in's date — re-resolves to the
     * current row instead of pinning a stale copy.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CareUiState> =
        container.selectedBunny
            .flatMapLatest { selection ->
                val bunnyId = selection.bunnyId
                if (bunnyId == null) {
                    flowOf(CareUiState(selection = selection))
                } else {
                    combine(
                        care.schedule(bunnyId),
                        visits.visits(bunnyId),
                        medications(bunnyId),
                        dialogs,
                    ) { schedule, visitList, meds, open ->
                        val today = LocalDate.now()
                        val rows = schedule.map { CareRow(scheduled = it, due = careDue(it.dueOn, today)) }
                        val visitRows =
                            visitList.map { details ->
                                VisitRow(
                                    id = details.visit.id,
                                    visitedOn = details.visit.visitedOn,
                                    reason = details.visit.reason,
                                    vetName = details.vetName,
                                    weightGrams = details.weightGrams,
                                )
                            }
                        CareUiState(
                            selection = selection,
                            rows = rows,
                            visits = visitRows,
                            courses = meds.courses,
                            todaysDoses = meds.today,
                            pendingDelete = rows.firstOrNull { it.id == open.reminder },
                            completing = rows.firstOrNull { it.id == open.completing },
                            pendingVisitDelete = visitRows.firstOrNull { it.id == open.visit },
                            pendingCourseDelete =
                                open.course?.let { request ->
                                    meds.courses
                                        .firstOrNull { it.id == request.courseId }
                                        ?.let { PendingCourseDelete(it, request.doseCount) }
                                },
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUiState())

    /**
     * This bunny's courses and today's slots, as one flow.
     *
     * Two reads of the same table rather than one, because the two questions genuinely differ: a
     * course with no times produces no slots at all and would vanish from a list built out of the
     * derivation, and it is precisely the course an owner records doses against by hand.
     *
     * **[today] is fixed when this flow is built**, which is what `DoseWindow` needs and what the
     * `BETWEEN` in the answers query is bounded by — unlike `careDue` above, it cannot re-resolve
     * itself on the next emission. `WhileSubscribed(5_000)` is what makes that harmless: leaving
     * the app for more than five seconds tears the flow down, and coming back rebuilds it against
     * whatever day it now is.
     */
    private fun medications(bunnyId: String): Flow<Medications> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        return combine(
            medications.courses(bunnyId, today),
            medications.schedule(bunnyId, days = NEXT_DOSE_DAYS, zone = zone, today = today),
        ) { courses, slots ->
            val now = Instant.now()
            Medications(
                courses =
                    courses.map { withTimes ->
                        CourseRow(
                            course = withTimes.course,
                            times = withTimes.clockTimes,
                            next =
                                doseNext(
                                    course = withTimes.course,
                                    hasTimes = withTimes.times.isNotEmpty(),
                                    slots = slots,
                                    now = now,
                                    today = today,
                                ),
                        )
                    },
                today = slots.filter { it.due.scheduledOn == today },
            )
        }
    }

    fun requestDelete(row: CareRow) {
        pendingDelete.value = row.id
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * **One** confirmation. ADR-0004's two-stage ceremony is calibrated to destroying a bunny's whole
     * history; a reminder is a schedule, and its completions go with it by cascade.
     */
    fun confirmDelete() {
        val id = pendingDelete.value ?: return
        viewModelScope.launch {
            care.delete(id)
            pendingDelete.value = null
            // The row is gone, so anything it posted is now a notification about nothing.
            container.careNotifier.cancel(id)
        }
    }

    fun startCompleting(row: CareRow) {
        completing.value = row.id
    }

    fun cancelCompleting() {
        completing.value = null
    }

    /**
     * Records a completion, which is the only thing that schedules the next occurrence.
     *
     * **And cancels the notification this reminder posted.** With "notifies once and never again",
     * a notification still in the shade for a task already done is the only copy of that lie left —
     * the derived due date has already moved on, and so has the screen.
     */
    fun complete(
        completedOn: LocalDate,
        note: String?,
    ) {
        val id = completing.value ?: return
        viewModelScope.launch {
            care.complete(reminderId = id, completedOn = completedOn, note = note)
            completing.value = null
            container.careNotifier.cancel(id)
        }
    }

    fun requestVisitDelete(row: VisitRow) {
        pendingVisitDelete.value = row.id
    }

    fun cancelVisitDelete() {
        pendingVisitDelete.value = null
    }

    /**
     * Deletes a visit, having **stated** what happens to the weighing recorded at it (PLAN 5c).
     *
     * [keepWeighing] is the owner's answer rather than a default this class picks: keeping it leaves
     * a standalone weighing in the chart, removing it takes the vet's number out of the series, and
     * guessing either way would be the app deciding what a health record is worth. The `SET NULL`
     * foreign key is what makes *keep* correct without a second write (ADR-0017).
     */
    fun confirmVisitDelete(keepWeighing: Boolean) {
        val id = pendingVisitDelete.value ?: return
        viewModelScope.launch {
            visits.delete(id, keepWeighing = keepWeighing)
            pendingVisitDelete.value = null
        }
    }

    /**
     * Answers one derived slot — **given or skipped, one tap either way** (PLAN 5e).
     *
     * No dialog, and tapping the other button afterwards corrects it rather than failing: an owner
     * who tapped *Skipped* and then gave the dose has changed their mind, which the repository
     * already treats as a correction rather than a constraint violation.
     */
    fun answer(
        dose: ScheduledDose,
        status: DoseStatus,
    ) {
        viewModelScope.launch { medications.answer(dose.due, status) }
    }

    /**
     * Asks to delete a course, **counting what that would destroy first**.
     *
     * The count is read here rather than in the dialog because it suspends. Until it arrives there
     * is no dialog, which is the right order: a confirmation that appears and then changes the
     * number under the owner is worse than one that appears a frame later.
     */
    fun requestCourseDelete(row: CourseRow) {
        viewModelScope.launch {
            pendingCourseDelete.value =
                CourseDeleteRequest(courseId = row.id, doseCount = medications.doseCount(row.id))
        }
    }

    fun cancelCourseDelete() {
        pendingCourseDelete.value = null
    }

    /** Destroys the course, its schedule and every dose recorded against it, by cascade. */
    fun confirmCourseDelete() {
        val id = pendingCourseDelete.value?.courseId ?: return
        viewModelScope.launch {
            medications.delete(id)
            pendingCourseDelete.value = null
        }
    }

    /**
     * The offer the delete dialog makes instead: **end the course and keep every dose**.
     *
     * Ending is setting `endOn` to today (ADR-0002), so the future slots simply stop being derived
     * and the history is untouched. It is the answer to what an owner usually means by "we have
     * finished with this one", and it exists in the dialog because that is where they are standing
     * when they mean it.
     */
    fun endCourse() {
        val id = pendingCourseDelete.value?.courseId ?: return
        viewModelScope.launch {
            medications.endCourse(id)
            pendingCourseDelete.value = null
        }
    }

    /** The four dialog ids, grouped so the state combine stays inside its five-argument overload. */
    private data class Dialogs(
        val reminder: String?,
        val completing: String?,
        val visit: String?,
        val course: CourseDeleteRequest?,
    )

    /** A pending course delete before its row has been resolved from the current list. */
    private data class CourseDeleteRequest(
        val courseId: String,
        val doseCount: Int,
    )

    /** The medication half of the tab, assembled off the main combine. */
    private data class Medications(
        val courses: List<CourseRow>,
        val today: List<ScheduledDose>,
    )

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    CareViewModel(app.container)
                }
            }
    }
}
