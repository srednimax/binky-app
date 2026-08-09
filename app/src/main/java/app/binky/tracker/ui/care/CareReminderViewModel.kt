package app.binky.tracker.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.AppPreferences
import app.binky.tracker.data.CareEventEntity
import app.binky.tracker.data.CareReminderEntity
import app.binky.tracker.data.CareRepository
import app.binky.tracker.data.CareType
import app.binky.tracker.data.WeightRepository
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.lastCompletedOn
import app.binky.tracker.data.scheduleFor
import app.binky.tracker.work.CareNotifier
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * One line of the completion history.
 *
 * **Two sources, one list** (ADR-0018's amendment). A care event is the owner ticking something off;
 * a weight is the app noticing that a weigh-in plainly happened, since it is holding the record that
 * proves it. The second kind is read-only here and editable on the Weight screen, so the history can
 * never show a completion with no visible row behind it.
 *
 * Kotlin note: a nullable [id] is what distinguishes the two — a weight-derived row has no
 * `care_events` row to edit or delete, and the absence is the fact rather than a flag beside it.
 */
data class CareEventRow(
    val id: String?,
    val completedOn: LocalDate,
    val note: String? = null,
    /** Set on a weight-derived row: what the bunny weighed, in grams (house rule). */
    val weightGrams: Int? = null,
) {
    val editable: Boolean get() = id != null

    /** Stable across both kinds — a weight-derived row is keyed by its day and its reading. */
    val key: String get() = id ?: "weight-$completedOn-$weightGrams"
}

data class CareReminderUiState(
    val loading: Boolean = true,
    val reminder: CareReminderEntity? = null,
    val dueOn: LocalDate? = null,
    val due: CareDue? = null,
    val events: List<CareEventRow> = emptyList(),
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val pendingEventDelete: CareEventRow? = null,
    /** Set while one completion's date and note are being corrected. Never a weight-derived row. */
    val editingEvent: CareEventRow? = null,
    /** Set while the confirmation for deleting the **reminder itself** is up. */
    val confirmingDelete: Boolean = false,
    /**
     * Flipped when the reminder is no longer there — deleted from the list behind this screen, or
     * with its bunny. The screen's cue to leave rather than render an empty shell.
     */
    val gone: Boolean = false,
) {
    val calendarHandedOff: Boolean get() = reminder?.calendarHandedOffAt != null

    val completedByWeighing: Boolean get() = reminder?.type == CareType.WEIGH_IN
}

/**
 * One care reminder: its schedule, its history, and the one-way hand-off to the owner's calendar.
 *
 * The due date is **derived here from the same pure functions the list and the sweep use**
 * (ADR-0002) rather than read from a second place — `scheduleFor` over the reminder and whichever
 * completion counts, which for a weigh-in is the later of a care event and a weighing.
 */
class CareReminderViewModel(
    private val reminderId: String,
    private val care: CareRepository,
    private val weights: WeightRepository,
    private val notifier: CareNotifier,
    preferences: AppPreferences,
) : ViewModel() {
    private val pendingEventDelete = MutableStateFlow<String?>(null)
    private val editingEvent = MutableStateFlow<String?>(null)

    /**
     * Deleting the **reminder itself**, which arrived here in Phase 7. `3a` drew the list behind
     * this screen as 64dp rows with a chevron and nowhere for a button, so the list navigates and
     * this screen destroys — the same split `Weight` made at `1d`.
     */
    private val confirmingDelete = MutableStateFlow(false)

    /**
     * The three dialog flags as one flow, so the state combine below stays inside `combine`'s
     * five-argument overload — it already spends four on real data.
     */
    private val dialogs: Flow<Dialogs> =
        combine(pendingEventDelete, editingEvent, confirmingDelete) { deleting, editing, deletingReminder ->
            Dialogs(deleting, editing, deletingReminder)
        }

    /**
     * Kotlin note: `flatMapLatest` re-subscribes when the reminder itself changes, which is what
     * lets a weigh-in start watching the weight series only once it is known to be one — the bunny
     * id is on the reminder, and there is nothing to read before it arrives.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CareReminderUiState> =
        care
            .reminder(reminderId)
            .flatMapLatest { reminder ->
                if (reminder == null) {
                    flowOf(CareReminderUiState(loading = false, gone = true))
                } else {
                    combine(
                        care.events(reminderId),
                        // A weigh-in's history has a second source; anything else has one, and an
                        // empty flow costs nothing.
                        if (reminder.type == CareType.WEIGH_IN) {
                            weights.series(reminder.bunnyId)
                        } else {
                            flowOf(emptyList())
                        },
                        preferences.weightUnit,
                        dialogs,
                    ) { events, series, unit, open ->
                        val zone = ZoneId.systemDefault()
                        val weighings = series.map { it.recordedAt.atZone(zone).toLocalDate() to it.grams }
                        val rows = historyRows(events = events, weighings = weighings)
                        val scheduled =
                            scheduleFor(
                                reminder = reminder,
                                lastCompletedOn =
                                    lastCompletedOn(
                                        type = reminder.type,
                                        latestEventOn = events.maxOfOrNull { it.completedOn },
                                        latestWeightOn = weighings.maxOfOrNull { it.first },
                                    ),
                            )
                        CareReminderUiState(
                            loading = false,
                            reminder = reminder,
                            dueOn = scheduled.dueOn,
                            due = careDue(scheduled.dueOn, LocalDate.now()),
                            events = rows,
                            unit = unit,
                            pendingEventDelete = rows.firstOrNull { it.id == open.event },
                            editingEvent = rows.firstOrNull { it.id == open.editing },
                            confirmingDelete = open.reminder,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareReminderUiState())

    /** Recording a completion is what moves the next date; cancelling the stale notification follows. */
    fun complete(
        completedOn: LocalDate,
        note: String?,
    ) {
        viewModelScope.launch {
            care.complete(reminderId = reminderId, completedOn = completedOn, note = note)
            notifier.cancel(reminderId)
        }
    }

    fun startEventEdit(row: CareEventRow) {
        editingEvent.value = row.id
    }

    fun cancelEventEdit() {
        editingEvent.value = null
    }

    /**
     * Corrects one completion's date or note.
     *
     * A completion on the wrong day is a wrong schedule until it is fixed — the next occurrence is
     * derived from it — which is the whole reason this history is editable rather than a log.
     */
    fun updateEvent(
        completedOn: LocalDate,
        note: String?,
    ) {
        val id = editingEvent.value ?: return
        viewModelScope.launch {
            val existing = care.eventNow(id) ?: return@launch
            care.updateEvent(existing.copy(completedOn = completedOn, note = note))
            editingEvent.value = null
        }
    }

    fun requestEventDelete(row: CareEventRow) {
        pendingEventDelete.value = row.id
    }

    fun cancelEventDelete() {
        pendingEventDelete.value = null
    }

    /**
     * Deletes one completion — which **moves every occurrence after it**, and is exactly why the
     * history is editable at all: a completion recorded on the wrong day is a wrong schedule until
     * it is corrected.
     */
    fun confirmEventDelete() {
        val id = pendingEventDelete.value ?: return
        viewModelScope.launch {
            care.deleteEvent(id)
            pendingEventDelete.value = null
        }
    }

    /**
     * Records that the calendar hand-off happened (ADR-0014) — **after** the calendar app has
     * actually been opened, which is why the screen calls this and not the button.
     *
     * The app does not own the event, so this is not a link: it exists only so the button can read
     * "Added to your calendar" instead of silently minting a second one on a second tap.
     */
    fun markCalendarHandedOff() {
        viewModelScope.launch { care.markCalendarHandedOff(reminderId) }
    }

    fun requestDelete() {
        confirmingDelete.value = true
    }

    fun cancelDelete() {
        confirmingDelete.value = false
    }

    /**
     * Deletes the reminder and every completion recorded against it, by cascade.
     *
     * **One** confirmation, not ADR-0004's two-stage ceremony — that is calibrated to destroying a
     * bunny's whole history; a reminder is a schedule. Nothing navigates here: `care.reminder(id)`
     * emits null once the row is gone, which sets `gone`, and the screen leaves on that.
     */
    fun confirmDelete() {
        viewModelScope.launch {
            care.delete(reminderId)
            confirmingDelete.value = false
            // The row is gone, so anything it posted is now a notification about nothing.
            notifier.cancel(reminderId)
        }
    }

    /** The three dialog flags, grouped so the state combine stays inside its five-argument overload. */
    private data class Dialogs(
        val event: String?,
        val editing: String?,
        val reminder: Boolean,
    )

    companion object {
        /** A factory *function*, because the navigation key carries an argument. */
        fun factory(reminderId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    CareReminderViewModel(
                        reminderId = reminderId,
                        care = app.container.careRepository,
                        weights = app.container.weightRepository,
                        notifier = app.container.careNotifier,
                        preferences = app.container.preferences,
                    )
                }
            }
    }
}

/**
 * The two sources merged, newest first.
 *
 * **One row per weighing, not one per day**: two weighings on one day are two records the owner can
 * see on the Weight screen, and collapsing them here would make this list disagree with that one.
 * Care events sort above weighings on the same day, because a typed completion is the more
 * deliberate of the two.
 */
private fun historyRows(
    events: List<CareEventEntity>,
    weighings: List<Pair<LocalDate, Int>>,
): List<CareEventRow> =
    (
        events.map { CareEventRow(id = it.id, completedOn = it.completedOn, note = it.note) } +
            weighings.map { (day, grams) -> CareEventRow(id = null, completedOn = day, weightGrams = grams) }
    ).sortedWith(compareByDescending<CareEventRow> { it.completedOn }.thenBy { it.weightGrams != null })
