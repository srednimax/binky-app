package app.binky.tracker.ui.observations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.DroppingsAppearance
import app.binky.tracker.data.DroppingsSize
import app.binky.tracker.data.ObservationEntity
import app.binky.tracker.data.SymptomEntity
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

data class ObservationsUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    val days: List<TimelineDay> = emptyList(),
    /** Every symptom, retired ones included — the timeline resolves ticks against this (ADR-0010). */
    val symptoms: List<SymptomEntity> = emptyList(),
    val pendingDelete: TimelineEntry? = null,
) {
    val bunnyId: String? get() = selection.bunnyId

    /** An archived bunny reads its timeline and writes nothing: no "+", no healthy day, no per-row edit. */
    val readOnly: Boolean get() = selection.readOnlyScope

    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * Observations: the day-grouped timeline.
 *
 * Unlike Weight this screen **welcomes "All bunnies"** — an observation can cover several at once
 * (ADR-0008), so here the single-bunny view is the special case (ADR-0015). The collapse that makes
 * a shared entry appear once rather than per participant is [buildTimeline]'s, tested on the JVM.
 *
 * The one-tap healthy day used to live here too. It moved to [HealthyDayViewModel] with the button
 * that started it, which is now the shell's "+" and reachable from Home as well (Phase 7.5 §6).
 */
class ObservationsViewModel(
    container: AppContainer,
) : ViewModel() {
    private val observations = container.observationRepository
    private val bunnies = container.bunnyRepository
    private val media = container.mediaFiles

    /**
     * Turns each entry's relative tray-photo path into a file to draw.
     *
     * Here rather than in [buildTimeline], because that function has no Android in it on purpose —
     * the collapse rule is a JVM test, and only `MediaFiles` knows what a stored path is relative to.
     */
    private fun List<TimelineDay>.withResolvedTrayPhotos(): List<TimelineDay> =
        map { day ->
            day.copy(
                entries =
                    day.entries.map { entry ->
                        entry.copy(trayPhoto = entry.tray.trayPhotoPath?.let(media::resolve))
                    },
            )
        }

    private val pendingDelete = MutableStateFlow<TimelineEntry?>(null)

    private data class Scope(
        val selection: BunnySelection,
        val active: List<BunnyEntity>,
        val archived: List<BunnyEntity>,
    )

    /**
     * The three join-table reads the timeline needs, folded into one flow.
     *
     * Kotlin note: `combine` is typed only up to five flows — past that it degrades to
     * `Array<Any?>` and every field needs a cast. Two of these arrived with schema 7 (ADR-0029),
     * which would have taken the outer combine to seven, so they are combined here instead. Nothing
     * clever: it is the same fan-in, one level down, with the types kept.
     */
    private data class ObservationLinks(
        val symptomIds: Map<String, Set<String>> = emptyMap(),
        val droppingsAppearance: Map<String, Set<DroppingsAppearance>> = emptyMap(),
        val droppingsSizes: Map<String, Set<DroppingsSize>> = emptyMap(),
    )

    private val links: kotlinx.coroutines.flow.Flow<ObservationLinks> =
        combine(
            observations.symptomLinks,
            observations.droppingsAppearance,
            observations.droppingsSizes,
        ) { symptomLinks, appearance, sizes ->
            ObservationLinks(
                symptomIds =
                    symptomLinks.groupBy { it.observationId }.mapValues { (_, l) ->
                        l.map { it.symptomId }.toSet()
                    },
                droppingsAppearance = appearance,
                droppingsSizes = sizes,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ObservationsUiState> =
        combine(
            container.selectedBunny,
            bunnies.activeBunnies,
            bunnies.archivedBunnies,
        ) { selection, active, archived -> Scope(selection, active, archived) }
            .flatMapLatest { scope ->
                val bunnyId = scope.selection.bunnyId
                val rows: kotlinx.coroutines.flow.Flow<List<ObservationEntity>> =
                    when {
                        scope.selection is BunnySelection.All -> observations.forActiveBunnies
                        bunnyId != null -> observations.timelineForBunny(bunnyId)
                        else -> flowOf(emptyList())
                    }
                // Under "All bunnies" the feed is the *current* fluffle's, so only active bunnies
                // are named; under one bunny the whole group is, archived housemates included,
                // because "observed together with Hazel, since archived" is the true answer there.
                val names =
                    when {
                        scope.selection is BunnySelection.All -> scope.active
                        else -> scope.active + scope.archived
                    }.associate { it.id to it.name }

                combine(
                    rows,
                    links,
                    container.symptomRepository.allSymptoms,
                    pendingDelete,
                ) { observationRows, joins, symptoms, pending ->
                    ObservationsUiState(
                        selection = scope.selection,
                        days =
                            buildTimeline(
                                rows = observationRows,
                                names = names,
                                symptomIds = joins.symptomIds,
                                droppingsSizes = joins.droppingsSizes,
                                droppingsAppearance = joins.droppingsAppearance,
                                focusBunnyId = bunnyId,
                            ).withResolvedTrayPhotos(),
                        symptoms = symptoms,
                        pendingDelete = pending,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObservationsUiState())

    fun requestDelete(entry: TimelineEntry) {
        pendingDelete.value = entry
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * **One** confirmation. ADR-0004's two-stage ceremony is calibrated to destroying a bunny's whole
     * history; one observation is a correction, and the dialog names who it affects.
     *
     * This is "that observation was wrong", which deletes every participant's row — deliberately a
     * different event from "this bunny wasn't in it", which is the form's participant edit. Keeping
     * them apart is what stops this dialog having to guess which the owner meant (ADR-0008).
     */
    fun confirmDelete() {
        val entry = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { observations.delete(entry.id) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    ObservationsViewModel(app.container)
                }
            }
    }
}
