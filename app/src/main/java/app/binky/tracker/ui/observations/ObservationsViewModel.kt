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
import app.binky.tracker.data.ParticipantExclusion
import app.binky.tracker.data.SymptomEntity
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.bunnyId
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.healthyDayFacts
import app.binky.tracker.data.preSelectParticipants
import app.binky.tracker.data.readOnlyScope
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * What the healthy day just recorded, for the snackbar that reports it (ADR-0008).
 *
 * The shortcut is the one write in the app that commits participants unreviewed — not asking *is*
 * the feature — so the attribution has to be visible immediately and a wrong one reversible, without
 * a dialog standing between the owner and the tap.
 */
data class HealthyDayReceipt(
    /** Any row of the observation just written: deleting one deletes the whole thing. */
    val observationId: String,
    val names: List<String>,
    /**
     * Those of [names] carrying a live weight flag.
     *
     * A flagged bunny is deliberately **not excluded** — the flag is about *weight*, and a bunny
     * losing weight with entirely normal droppings is real and useful data. Excluding would put
     * friction on the one-tap path over exactly the stretch that most wants daily observations. So
     * the snackbar names the flag instead (ADR-0001, ADR-0008).
     */
    val flaggedNames: List<String>,
    /**
     * Housemates left **out** because they are under a watch, so the snackbar can say so.
     *
     * ADR-0008 asks for the exclusion *and* the reason, and the reason is the half that was
     * missing: this is the one write path that commits participants unreviewed, so an owner who
     * expected the tap to cover the whole fluffle otherwise learns nothing about why it did not.
     * The full entry screen states it per row; here there are no rows to state it on.
     *
     * Watch exclusions only. An archived housemate is excluded too, but that is a permanent and
     * already-visible fact, and repeating it on every tap would be the daily noise ADR-0001 rejects
     * — where a watch is temporary, and "log for them separately" is something to act on today.
     */
    val watchedOutNames: List<String>,
)

data class ObservationsUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    val days: List<TimelineDay> = emptyList(),
    /** Every symptom, retired ones included — the timeline resolves ticks against this (ADR-0010). */
    val symptoms: List<SymptomEntity> = emptyList(),
    val pendingDelete: TimelineEntry? = null,
    val receipt: HealthyDayReceipt? = null,
) {
    val bunnyId: String? get() = selection.bunnyId

    /** An archived bunny reads its timeline and writes nothing: no "+", no healthy day, no per-row edit. */
    val readOnly: Boolean get() = selection.readOnlyScope

    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * Observations: the day-grouped timeline, and the one-tap healthy day.
 *
 * Unlike Weight this screen **welcomes "All bunnies"** — an observation can cover several at once
 * (ADR-0008), so here the single-bunny view is the special case (ADR-0015). The collapse that makes
 * a shared entry appear once rather than per participant is [buildTimeline]'s, tested on the JVM.
 */
class ObservationsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val observations = container.observationRepository
    private val bunnies = container.bunnyRepository
    private val fluffles = container.fluffleRepository
    private val weights = container.weightRepository
    private val watches = container.watchRepository
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
    private val receipt = MutableStateFlow<HealthyDayReceipt?>(null)

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
                    receipt,
                ) { observationRows, joins, symptoms, pending, healthyDay ->
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
                        receipt = healthyDay,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObservationsUiState())

    /**
     * **One tap.** Records the glance-level facts for the bunny and its active fluffle, then reports
     * who it covered.
     *
     * What it records is [healthyDayFacts]' business and lives in `data/`; who it covers is this
     * function's, and is the ordinary fluffle pre-selection with its stated exclusions (ADR-0008).
     */
    fun logHealthyDay(bunnyId: String) {
        viewModelScope.launch {
            val subject = bunnies.bunnyNow(bunnyId) ?: return@launch
            val members = subject.fluffleId?.let { fluffles.members(it).first() }.orEmpty()
            // **This** is the write path ADR-0008's watch exclusion is written for: the one tap
            // that commits participants unreviewed. A housemate under a running watch has been
            // singled out by the owner, and sweeping them into a shared tray fact would record a
            // claim nobody made.
            val preSelection = preSelectParticipants(subject, members, watches.activelyWatchedIdsNow())

            val ids = observations.add(preSelection.bunnyIds, Instant.now(), healthyDayFacts())
            // Whoever this covered has now been looked at, so the morning's nag is answered — and a
            // question still in the shade after it has been answered is the only copy of that
            // staleness left anywhere (the same argument `CareNotifier.cancel` makes).
            preSelection.bunnyIds.forEach(container.watchNotifier::cancel)
            receipt.value =
                HealthyDayReceipt(
                    observationId = ids.first(),
                    names = preSelection.candidates.map { it.name },
                    flaggedNames = preSelection.candidates.filter { flagged(it.bunnyId) }.map { it.name },
                    watchedOutNames =
                        preSelection.excluded
                            .filter { it.reason == ParticipantExclusion.UNDER_WATCH }
                            .map { it.name },
                )
        }
    }

    /** Undo, straight off the snackbar: the whole observation goes, every participant's row with it. */
    fun undoHealthyDay() {
        val written = receipt.value ?: return
        receipt.value = null
        viewModelScope.launch { observations.delete(written.observationId) }
    }

    fun dismissReceipt() {
        receipt.value = null
    }

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

    /** Whether this bunny's weight series is currently worth a closer look (ADR-0001). */
    private suspend fun flagged(bunnyId: String): Boolean {
        val evaluation =
            evaluateTrend(
                series = weights.series(bunnyId).first().map { it.toWeighing() },
                acknowledgment = weights.acknowledgment(bunnyId).first()?.toAcknowledgment(),
            )
        return evaluation.flag is TrendFlag.WorthACloserLook
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
