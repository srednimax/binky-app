package app.bunny.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bunny.tracker.AppContainer
import app.bunny.tracker.BunnyTrackerApplication
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.data.TrendFlag
import app.bunny.tracker.data.WeightUnit
import app.bunny.tracker.data.evaluateTrend
import app.bunny.tracker.data.readOnlyScope
import app.bunny.tracker.data.toAcknowledgment
import app.bunny.tracker.data.toWeighing
import app.bunny.tracker.ui.bunny.BunnyActions
import app.bunny.tracker.ui.bunny.BunnyDialog
import app.bunny.tracker.ui.bunny.BunnyProfile
import app.bunny.tracker.ui.bunny.toProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ADR-0015's vitals card: what the record says about this bunny right now — last weight, last
 * observation, and the flag.
 *
 * Every field can be null, and null is always a statement about the **record** rather than about the
 * bunny: nobody has weighed them, nobody has looked. The card must never let that read as
 * reassurance (ADR-0001).
 */
data class BunnyVitals(
    val lastGrams: Int? = null,
    val lastRecordedAt: Instant? = null,
    /** When anything was last noticed about this bunny, shared observations included. */
    val lastObservationAt: Instant? = null,
    /**
     * Null means **not evaluated**, which is the archived scope — the flag is not evaluated at all
     * there, not merely hidden (ADR-0001, ADR-0004).
     */
    val flag: TrendFlag? = null,
)

/**
 * Home, in both of its shapes: one bunny's profile, and under "All bunnies" the dashboard that
 * **is** the bunny list (ADR-0015).
 */
data class HomeUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    /** One profile under a single selection, every active bunny under "All bunnies". */
    val profiles: List<BunnyProfile> = emptyList(),
    val vitals: Map<String, BunnyVitals> = emptyMap(),
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val dialog: BunnyDialog? = null,
) {
    /** An archived bunny is a read-only scope: no write actions (ADR-0015). */
    val readOnly: Boolean get() = selection.readOnlyScope

    fun vitalsFor(bunnyId: String): BunnyVitals = vitals[bunnyId] ?: BunnyVitals()
}

class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val actions = BunnyActions(container.bunnyRepository, viewModelScope)
    private val weights = container.weightRepository
    private val observations = container.observationRepository

    /** Everything the card needs *before* the per-bunny series reads fan out beneath it. */
    private data class Shown(
        val selection: BunnySelection,
        val profiles: List<BunnyProfile>,
        val dialog: BunnyDialog?,
        val unit: WeightUnit,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> =
        combine(
            container.selectedBunny,
            container.bunnyRepository.activeBunnies,
            container.bunnyRepository.archivedBunnies,
            actions.dialog,
            container.preferences.weightUnit,
        ) { selection, active, archived, dialog, unit ->
            // Both lists, so an archived housemate still shows up on a profile as one — the
            // fluffle survives archival, and the survivor genuinely did live with them (ADR-0008).
            val everyBunny = active + archived
            val shown =
                when (selection) {
                    BunnySelection.All -> active
                    is BunnySelection.Single -> everyBunny.filter { it.id == selection.id }
                    is BunnySelection.Archived -> everyBunny.filter { it.id == selection.id }
                    BunnySelection.Loading, BunnySelection.Empty -> emptyList()
                }
            Shown(
                selection = selection,
                profiles = shown.map { it.toProfile(everyBunny, container.mediaFiles) },
                dialog = dialog,
                unit = unit,
            )
        }.flatMapLatest { shown ->
            vitals(shown.profiles.map { it.id }, evaluateFlag = !shown.selection.readOnlyScope)
                .map { vitals ->
                    HomeUiState(
                        selection = shown.selection,
                        profiles = shown.profiles,
                        vitals = vitals,
                        unit = shown.unit,
                        dialog = shown.dialog,
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * **N series reads and N trend evaluations per emission**, under "All bunnies".
     *
     * Stated rather than optimised: at three rabbits it is free, and "derived on read" plus "a card
     * each" is the pairing that stops being free quietly. If a fluffle ever gets large enough for
     * this to matter, the fix is a single query returning each bunny's latest rows — not a cached
     * flag, which would reintroduce the stored-event design ADR-0001 rejects.
     */
    private fun vitals(
        bunnyIds: List<String>,
        evaluateFlag: Boolean,
    ): Flow<Map<String, BunnyVitals>> =
        if (bunnyIds.isEmpty()) {
            // combine() over an empty list never emits, which would leave Home stuck on its initial
            // value for an owner with no bunnies.
            flowOf(emptyMap())
        } else {
            combine(bunnyIds.map { id -> vitalsFor(id, evaluateFlag) }) { entries -> entries.toMap() }
        }

    private fun vitalsFor(
        bunnyId: String,
        evaluateFlag: Boolean,
    ): Flow<Pair<String, BunnyVitals>> =
        combine(
            weights.series(bunnyId),
            weights.acknowledgment(bunnyId),
            // This bunny's own rows, which for a shared observation is its copy — so "last
            // observation" is true of this bunny whether or not it was observed alone (ADR-0008).
            observations.forBunny(bunnyId),
        ) { series, acknowledgment, observed ->
            val latest = series.firstOrNull()
            bunnyId to
                BunnyVitals(
                    lastGrams = latest?.grams,
                    lastRecordedAt = latest?.recordedAt,
                    lastObservationAt = observed.firstOrNull()?.recordedAt,
                    flag =
                        if (!evaluateFlag) {
                            null
                        } else {
                            evaluateTrend(
                                series.map { it.toWeighing() },
                                acknowledgment?.toAcknowledgment(),
                            ).flag
                        },
                )
        }

    fun acknowledge(bunnyId: String) {
        viewModelScope.launch { weights.acknowledgeTrend(bunnyId) }
    }

    fun requestArchive(profile: BunnyProfile) = actions.requestArchive(profile)

    fun requestDelete(profile: BunnyProfile) = actions.requestDelete(profile)

    fun confirmDialog() = actions.confirm()

    fun dismissDialog() = actions.dismiss()

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BunnyTrackerApplication
                    HomeViewModel(app.container)
                }
            }
    }
}
