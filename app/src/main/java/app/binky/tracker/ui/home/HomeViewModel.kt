package app.binky.tracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.growthStageNow
import app.binky.tracker.data.readOnlyScope
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import app.binky.tracker.data.watchState
import app.binky.tracker.ui.bunny.BunnyActions
import app.binky.tracker.ui.bunny.BunnyDialog
import app.binky.tracker.ui.bunny.BunnyProfile
import app.binky.tracker.ui.bunny.toProfile
import app.binky.tracker.ui.events.TimelineEntry
import app.binky.tracker.ui.events.buildTimeline
import app.binky.tracker.ui.events.timelineHighlights
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    /**
     * The watch, if one is running. [WatchState.None] in the archived scope for the same reason the
     * flag is null there — an archived bunny has none, because archiving closes it.
     */
    val watch: WatchState = WatchState.None,
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
    /**
     * The next thing owed and the last two that happened, for the one bunny on screen (ADR-0031).
     *
     * **Empty under "All bunnies"**, and not because it is hidden there: a timeline is one rabbit's
     * agenda, and the dashboard already gives each of them a card. Empty also for a bunny with
     * nothing dated at all, which is what makes the card absent rather than an empty box.
     */
    val timeline: List<TimelineEntry> = emptyList(),
    /**
     * The day [timeline] was built against — carried because every row needs it to say *Today* or
     * *In 3 days*, and reading the clock in a composable would answer differently each recomposition.
     */
    val timelineToday: LocalDate = LocalDate.now(),
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
    private val watches = container.watchRepository

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
            combine(
                vitals(shown.profiles, liveState = !shown.selection.readOnlyScope),
                highlights(shown.profiles, shown.selection),
            ) { vitals, timeline ->
                HomeUiState(
                    selection = shown.selection,
                    profiles = shown.profiles,
                    vitals = vitals,
                    unit = shown.unit,
                    dialog = shown.dialog,
                    timeline = timeline.entries,
                    timelineToday = timeline.today,
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
        profiles: List<BunnyProfile>,
        liveState: Boolean,
    ): Flow<Map<String, BunnyVitals>> =
        if (profiles.isEmpty()) {
            // combine() over an empty list never emits, which would leave Home stuck on its initial
            // value for an owner with no bunnies.
            flowOf(emptyMap())
        } else {
            combine(profiles.map { profile -> vitalsFor(profile, liveState) }) { entries -> entries.toMap() }
        }

    /** [highlights]' answer: the rows, and the day they were built against. */
    private data class Highlights(
        val today: LocalDate,
        val entries: List<TimelineEntry>,
    )

    /**
     * Home's slice of the timeline — the next thing owed and the last two that happened.
     *
     * **Four more flows, and only ever four**, because this is built for the single-bunny selection
     * alone. Deliberately *not* folded into [vitalsFor]: that one fans out per profile, so putting
     * the timeline in it would cost four reads per rabbit under "All bunnies" to render a card that
     * screen does not draw — which is exactly the quiet multiplication [vitals] warns about.
     *
     * Derived from the same [buildTimeline] the timeline screen renders rather than from a second
     * query with its own ordering: a card that disagreed with the screen it links to would be worse
     * than no card at all (ADR-0031).
     */
    private fun highlights(
        profiles: List<BunnyProfile>,
        selection: BunnySelection,
    ): Flow<Highlights> {
        val today = LocalDate.now()
        // `singleOrNull` is the whole condition: "All bunnies" hands several profiles and the empty
        // and loading selections hand none, so only a single bunny — active or archived — gets past
        // it. The archived one does too, and should: the record is what an archive keeps.
        val bunnyId =
            profiles.singleOrNull()?.takeIf { selection != BunnySelection.All }?.id
                ?: return flowOf(Highlights(today, emptyList()))
        val zone = ZoneId.systemDefault()
        return combine(
            container.eventRepository.events(bunnyId),
            container.visitRepository.visits(bunnyId),
            container.careRepository.completions(bunnyId),
            container.careRepository.schedule(bunnyId, zone),
        ) { events, visits, completions, schedule ->
            Highlights(
                today = today,
                entries =
                    timelineHighlights(
                        buildTimeline(
                            events = events,
                            visits = visits,
                            completions = completions,
                            careDue = schedule,
                            today = today,
                        ),
                    ),
            )
        }
    }

    /**
     * [liveState] is false in the archived scope, and it governs **both** derived facts — the trend
     * flag and the watch. Neither is merely hidden there: an archived bunny's flag is not evaluated
     * (ADR-0001, ADR-0004), and archiving closes any watch, so reporting one would be reporting a
     * row that should not exist.
     */
    private fun vitalsFor(
        profile: BunnyProfile,
        liveState: Boolean,
    ): Flow<Pair<String, BunnyVitals>> {
        val bunnyId = profile.id
        return combine(
            weights.series(bunnyId),
            weights.acknowledgment(bunnyId),
            // This bunny's own rows, which for a shared observation is its copy — so "last
            // observation" is true of this bunny whether or not it was observed alone (ADR-0008).
            observations.forBunny(bunnyId),
            watches.watch(bunnyId),
        ) { series, acknowledgment, observed, watch ->
            val latest = series.firstOrNull()
            bunnyId to
                BunnyVitals(
                    lastGrams = latest?.grams,
                    lastRecordedAt = latest?.recordedAt,
                    lastObservationAt = observed.firstOrNull()?.recordedAt,
                    flag =
                        if (!liveState) {
                            null
                        } else {
                            evaluateTrend(
                                series.map { it.toWeighing() },
                                acknowledgment?.toAcknowledgment(),
                                // Read on every emission like the watch below, and for the same
                                // reason: a bunny crosses its first birthday without anything being
                                // written to it (ADR-0028).
                                growthStageNow(profile.birthDate),
                            ).flag
                        },
                    // Resolved against the clock on every emission, never stored — a watch runs out
                    // without anything being written to it.
                    watch = if (!liveState) WatchState.None else watchState(watch, Instant.now()),
                )
        }
    }

    fun acknowledge(bunnyId: String) {
        viewModelScope.launch { weights.acknowledgeTrend(bunnyId) }
    }

    /**
     * *Start a watch*, from the flag's secondary action — **offered, never automatic** (ADR-0001).
     *
     * An upsert underneath, so a stale expired row cannot block it.
     */
    fun startWatch(
        bunnyId: String,
        duration: WatchDuration,
    ) {
        viewModelScope.launch { watches.start(bunnyId, duration) }
    }

    /** Close-early, from the card the watch announces itself on. */
    fun closeWatch(bunnyId: String) {
        viewModelScope.launch {
            watches.close(bunnyId)
            container.watchNotifier.cancel(bunnyId)
        }
    }

    fun requestArchive(profile: BunnyProfile) = actions.requestArchive(profile)

    fun requestDelete(profile: BunnyProfile) = actions.requestDelete(profile)

    fun confirmDialog() = actions.confirm()

    fun dismissDialog() = actions.dismiss()

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    HomeViewModel(app.container)
                }
            }
    }
}
