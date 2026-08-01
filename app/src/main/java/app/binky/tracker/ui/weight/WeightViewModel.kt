package app.binky.tracker.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.TrendDrop
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightEntity
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.bunnyId
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.readOnlyScope
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import app.binky.tracker.data.watchState
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
 * One weighing as the history list draws it.
 *
 * [changeGrams] is **always grams** (house rule) and is measured against the next *older* row in
 * ADR-0021's total order — not against the baseline, which is the trend's business and would make
 * the same number mean two different things on one screen.
 */
data class WeightRow(
    val id: String,
    val grams: Int,
    val recordedAt: Instant,
    /** Null on the oldest row, which has nothing to have changed from. */
    val changeGrams: Int?,
)

data class WeightUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    val bunnyName: String = "",
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val rows: List<WeightRow> = emptyList(),
    /**
     * Null means **not evaluated**, which is the archived scope — the flag is not evaluated at all
     * there, not merely hidden (ADR-0001, ADR-0004). It is also null before the first emission.
     */
    val flag: TrendFlag? = null,
    /**
     * The watch, if one is running — read here so the flag's *Start a watch* action is absent when
     * there is already one to start (ADR-0001). [WatchState.None] in the archived scope.
     */
    val watch: WatchState = WatchState.None,
    val pendingDelete: WeightRow? = null,
    /** Set straight after a delete that leaves a visible, unacknowledged flag: the dialog host. */
    val writeFlag: TrendDrop? = null,
    val chartRange: WeightChartRange = WeightChartRange.DAYS_90,
    val chart: WeightChartContent = WeightChartContent.NoWeighings,
) {
    val bunnyId: String? get() = selection.bunnyId

    val readOnly: Boolean get() = selection.readOnlyScope
}

/**
 * Weight: the per-bunny history, the trend flag, and the one confirmation guarding a delete.
 *
 * It **refuses "All bunnies"** (ADR-0015) — weight is individual, and overlaying unrelated animals
 * of different sizes on one axis would say nothing true.
 */
class WeightViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val weights = container.weightRepository
    private val watches = container.watchRepository

    private val pendingDelete = MutableStateFlow<WeightRow?>(null)
    private val writeFlag = MutableStateFlow<TrendDrop?>(null)

    /** What is on top of the screen right now. */
    private data class Dialogs(
        val pendingDelete: WeightRow?,
        val writeFlag: TrendDrop?,
    )

    /**
     * The two dialog flags as one flow, purely so the inner `combine` below stays inside Kotlin's
     * five-flow typed overload — past that the only `combine` left takes an untyped array, and
     * trading five named parameters for `it[3] as TrendDrop?` is not a trade worth making.
     */
    private val dialogs =
        combine(pendingDelete, writeFlag) { pending, raised -> Dialogs(pending, raised) }

    /**
     * The chart's window — held here and **not persisted** (ADR-0022). Living in the `ViewModel`
     * means it survives a rotation and dies with the process, which is the point: an owner who once
     * tapped *All* should not be left permanently in the view that flattens the signal, having
     * opted in with a tap they have long forgotten. It resets to 90 days each session on purpose.
     */
    private val chartRange = MutableStateFlow(WeightChartRange.DAYS_90)

    /** The scope this screen is in, before any of the bunny's own records are read. */
    private data class Scope(
        val selection: BunnySelection,
        val unit: WeightUnit,
        val bunnies: List<BunnyEntity>,
    )

    /**
     * Kotlin note: `flatMapLatest` swaps to a **new inner Flow** whenever the outer one emits,
     * cancelling the previous subscription — switching bunny therefore stops collecting the old
     * bunny's series rather than leaving it running. There is no promise equivalent; the nearest
     * thing is an effect that aborts its in-flight request when its dependencies change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeightUiState> =
        combine(
            container.selectedBunny,
            container.preferences.weightUnit,
            container.bunnyRepository.activeBunnies,
            container.bunnyRepository.archivedBunnies,
        ) { selection, unit, active, archived -> Scope(selection, unit, active + archived) }
            .flatMapLatest { scope ->
                val bunnyId = scope.selection.bunnyId
                if (bunnyId == null) {
                    flowOf(WeightUiState(selection = scope.selection, unit = scope.unit))
                } else {
                    combine(
                        weights.series(bunnyId),
                        weights.acknowledgment(bunnyId),
                        watches.watch(bunnyId),
                        dialogs,
                        chartRange,
                    ) { series, acknowledgment, watch, open, range ->
                        val rows = series.toRows()
                        WeightUiState(
                            selection = scope.selection,
                            bunnyName =
                                scope.bunnies
                                    .firstOrNull { it.id == bunnyId }
                                    ?.name
                                    .orEmpty(),
                            unit = scope.unit,
                            rows = rows,
                            // The chart is filtered; the flag below is **not**. Range never reaches
                            // `evaluateTrend`, which is what stops the two from drifting apart —
                            // and is why the flag can legitimately sit above an empty chart
                            // (ADR-0022). That composition is correct, not a bug to fix.
                            chartRange = range,
                            chart = weightChartContentFor(rows, range, Instant.now()),
                            flag =
                                if (scope.selection.readOnlyScope) {
                                    null
                                } else {
                                    evaluateTrend(
                                        series.map { it.toWeighing() },
                                        acknowledgment?.toAcknowledgment(),
                                    ).flag
                                },
                            // Resolved against the clock on every emission, never stored — the same
                            // shape as the flag, and for the same reason: a watch runs out with
                            // nothing being written to it.
                            watch =
                                if (scope.selection.readOnlyScope) {
                                    WatchState.None
                                } else {
                                    watchState(watch, Instant.now())
                                },
                            pendingDelete = open.pendingDelete,
                            writeFlag = open.writeFlag,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    /** Display-only, and deliberately never written to `AppPreferences` (ADR-0022). */
    fun setChartRange(range: WeightChartRange) {
        chartRange.value = range
    }

    fun requestDelete(row: WeightRow) {
        pendingDelete.value = row
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * **One** confirmation, not two: ADR-0004's two-stage ceremony is calibrated to destroying a
     * bunny's whole history, and one weighing is a correction.
     */
    fun confirmDelete() {
        val row = pendingDelete.value ?: return
        val bunnyId = uiState.value.bunnyId ?: return
        viewModelScope.launch {
            weights.delete(row.id)
            pendingDelete.value = null
            // A delete can *deepen* a drop — removing a low reading moves the baseline — so the
            // flag is re-checked here exactly as it is after an insert or an edit (ADR-0001).
            raiseFlagIfUnacknowledged(bunnyId)
        }
    }

    fun acknowledge() {
        val bunnyId = uiState.value.bunnyId ?: return
        writeFlag.value = null
        viewModelScope.launch { weights.acknowledgeTrend(bunnyId) }
    }

    /** Dismissing is explicitly **not** acknowledging: the watermark is only ever set deliberately. */
    fun dismissWriteFlag() {
        writeFlag.value = null
    }

    /**
     * *Start a watch*, from the flag's secondary action — **offered, never automatic** (ADR-0001).
     *
     * It also closes the write dialog, if that is where the tap came from: the owner has answered
     * the flag with an action, and leaving the dialog up would ask them to answer it again.
     */
    fun startWatch(duration: WatchDuration) {
        val bunnyId = uiState.value.bunnyId ?: return
        writeFlag.value = null
        viewModelScope.launch { watches.start(bunnyId, duration) }
    }

    private suspend fun raiseFlagIfUnacknowledged(bunnyId: String) {
        val evaluation =
            evaluateTrend(
                series = weights.series(bunnyId).first().map { it.toWeighing() },
                acknowledgment = weights.acknowledgment(bunnyId).first()?.toAcknowledgment(),
            )
        writeFlag.value = (evaluation.flag as? TrendFlag.WorthACloserLook)?.drop
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    WeightViewModel(app.container)
                }
            }
    }
}

/** The series arrives newest-first, so the next *older* reading is the following element. */
private fun List<WeightEntity>.toRows(): List<WeightRow> =
    mapIndexed { index, weight ->
        WeightRow(
            id = weight.id,
            grams = weight.grams,
            recordedAt = weight.recordedAt,
            changeGrams = getOrNull(index + 1)?.let { older -> weight.grams - older.grams },
        )
    }
