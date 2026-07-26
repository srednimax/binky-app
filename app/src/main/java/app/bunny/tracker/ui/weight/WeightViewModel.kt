package app.bunny.tracker.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bunny.tracker.AppContainer
import app.bunny.tracker.BunnyTrackerApplication
import app.bunny.tracker.data.BunnyEntity
import app.bunny.tracker.data.BunnySelection
import app.bunny.tracker.data.TrendDrop
import app.bunny.tracker.data.TrendFlag
import app.bunny.tracker.data.WeightEntity
import app.bunny.tracker.data.WeightUnit
import app.bunny.tracker.data.bunnyId
import app.bunny.tracker.data.evaluateTrend
import app.bunny.tracker.data.readOnlyScope
import app.bunny.tracker.data.toAcknowledgment
import app.bunny.tracker.data.toWeighing
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
    val pendingDelete: WeightRow? = null,
    /** Set straight after a delete that leaves a visible, unacknowledged flag: the dialog host. */
    val writeFlag: TrendDrop? = null,
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

    private val pendingDelete = MutableStateFlow<WeightRow?>(null)
    private val writeFlag = MutableStateFlow<TrendDrop?>(null)

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
                        pendingDelete,
                        writeFlag,
                    ) { series, acknowledgment, pending, raised ->
                        WeightUiState(
                            selection = scope.selection,
                            bunnyName =
                                scope.bunnies
                                    .firstOrNull { it.id == bunnyId }
                                    ?.name
                                    .orEmpty(),
                            unit = scope.unit,
                            rows = series.toRows(),
                            flag =
                                if (scope.selection.readOnlyScope) {
                                    null
                                } else {
                                    evaluateTrend(
                                        series.map { it.toWeighing() },
                                        acknowledgment?.toAcknowledgment(),
                                    ).flag
                                },
                            pendingDelete = pending,
                            writeFlag = raised,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

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
                    val app = this[APPLICATION_KEY] as BunnyTrackerApplication
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
