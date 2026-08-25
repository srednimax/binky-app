package app.binky.tracker.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The timeline as the screen reads it.
 *
 * [sections] is the whole agenda, upcoming above past. [today] rides along because every row needs it
 * to say *Today* or *In 3 days*, and reading the clock inside a composable would give a different
 * answer on every recomposition.
 */
data class EventsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val sections: List<TimelineSection> = emptyList(),
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

/**
 * One bunny's timeline (ADR-0031) — four flows in, nothing stored.
 *
 * Kotlin note: `combine` re-runs its block whenever *any* input emits, so recording a vet visit on
 * the Care tab, completing a nail trim, or logging a weighing that moves a weigh-in's next due date
 * all redraw this screen with nothing telling them to (house rule: DAOs return `Flow`).
 *
 * **The clock is read once, when the flow is built**, and deliberately not per emission: the screen's
 * `ViewModel` is created when the screen opens, so an owner who leaves the app on this tab overnight
 * sees yesterday's *Today* until they come back to it. The alternative is a ticker, which would
 * recompose a scrolling list every minute to change one word — and the sweep, not this screen, is
 * what tells anybody about a day arriving.
 */
class EventsViewModel(
    private val bunnyId: String,
    private val container: AppContainer,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val events = container.eventRepository
    private val today = now.atZone(zone).toLocalDate()

    val uiState: StateFlow<EventsUiState> =
        combine(
            events.events(bunnyId),
            container.visitRepository.visits(bunnyId),
            container.careRepository.completions(bunnyId),
            container.careRepository.schedule(bunnyId, zone),
        ) { ownEvents, visits, completions, schedule ->
            EventsUiState(
                loading = false,
                today = today,
                sections =
                    buildTimeline(
                        events = ownEvents,
                        visits = visits,
                        completions = completions,
                        careDue = schedule,
                        today = today,
                    ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState(today = today))

    /**
     * Deletes one event, and drops the notification it posted.
     *
     * The cancel is not tidiness. An event announces itself once, so a notice still sitting in the
     * shade for a row that no longer exists is the only copy of that lie left anywhere — the same
     * argument [app.binky.tracker.work.CareNotifier.cancel] makes for a completed reminder.
     *
     * **Only the owner's own events can be deleted from here.** A vet visit, a completion and a
     * derived due date are all owned by another screen, and a delete on this one would be a second
     * place to destroy them.
     */
    fun delete(eventId: String) {
        viewModelScope.launch {
            events.delete(eventId)
            container.eventNotifier.cancel(eventId)
        }
    }

    companion object {
        /** A factory *function*, because the navigation key carries the bunny. */
        fun factory(bunnyId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    EventsViewModel(bunnyId = bunnyId, container = app.container)
                }
            }
    }
}
