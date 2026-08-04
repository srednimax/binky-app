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
import app.binky.tracker.data.ScheduledCare
import app.binky.tracker.data.bunnyId
import app.binky.tracker.data.readOnlyScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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

data class CareUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    val rows: List<CareRow> = emptyList(),
    /** This bunny's vet visits, newest first — the tab's second list (ADR-0017, PLAN 5c). */
    val visits: List<VisitRow> = emptyList(),
    /** Set while the one delete confirmation is up (ADR-0004 reserves its ceremony for a bunny). */
    val pendingDelete: CareRow? = null,
    /** Set while the completion sheet is up. Never a weigh-in — see [CareRow.completedByWeighing]. */
    val completing: CareRow? = null,
    /** Set while the visit's delete dialog is up, which is where its weighing's fate is chosen. */
    val pendingVisitDelete: VisitRow? = null,
) {
    val bunnyId: String? get() = selection.bunnyId

    /** An archived bunny's reminders are readable and nothing more (ADR-0004, ADR-0015). */
    val readOnly: Boolean get() = selection.readOnlyScope
}

/**
 * The Care tab: what is due, what is overdue, what is merely scheduled — **and this bunny's vet
 * visits** (PLAN 5c).
 *
 * **Nothing here writes a due date**, because none is stored (ADR-0002) — the rows arrive already
 * resolved from `CareRepository.schedule`, and recording a completion is what moves them. That is
 * also why completing needs no follow-up write: the next occurrence is derived from the completion
 * the moment it lands.
 *
 * **One `ViewModel` for the whole tab** (house rule), which is why visits arrive here rather than
 * through a second one of their own: the tab is a hub over one bunny's ongoing care, and two
 * `ViewModel`s would be two answers to "which bunny is this?".
 */
class CareViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val care = container.careRepository
    private val visits = container.visitRepository

    private val pendingDelete = MutableStateFlow<String?>(null)
    private val completing = MutableStateFlow<String?>(null)
    private val pendingVisitDelete = MutableStateFlow<String?>(null)

    /**
     * Kotlin note: `flatMapLatest` swaps to a new inner Flow whenever the selection changes and
     * cancels the previous subscription, so switching bunny stops collecting the old bunny's
     * schedule rather than leaving it running.
     *
     * The two dialog flags are held as **ids** rather than as rows, so a row that moves underneath
     * an open dialog — a weight logged in another tab moves a weigh-in's date — re-resolves to the
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
                        pendingDelete,
                        completing,
                        pendingVisitDelete,
                    ) { schedule, visitList, deleting, completingId, deletingVisit ->
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
                            pendingDelete = rows.firstOrNull { it.id == deleting },
                            completing = rows.firstOrNull { it.id == completingId },
                            pendingVisitDelete = visitRows.firstOrNull { it.id == deletingVisit },
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareUiState())

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
