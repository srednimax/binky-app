package app.bunny.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.bunny.tracker.AppContainer
import app.bunny.tracker.BunnyTrackerApplication
import app.bunny.tracker.data.PreservedCopy
import app.bunny.tracker.data.WeightUnit
import app.bunny.tracker.data.deletePreservedCopy
import app.bunny.tracker.data.listPreservedCopies
import app.bunny.tracker.data.seedSampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the sample-data action did, for a one-line report. Debug builds only. */
enum class SampleDataOutcome { SEEDED, ALREADY_PRESENT }

data class SettingsUiState(
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    /** ADR-0007's pre-wipe copies, newest first. Empty is the ordinary case. */
    val preserved: List<PreservedCopy> = emptyList(),
    val pendingDelete: PreservedCopy? = null,
    val sampleData: SampleDataOutcome? = null,
)

/**
 * Settings — the minimum that has to exist before the weight screens make sense, plus the one place
 * ADR-0007's preserved copies are reachable from.
 *
 * A copy the owner cannot get off the phone is only marginally better than no copy at all, so
 * sharing is the point of listing them; deleting is here because a preserved database is the app's
 * largest file and the owner should not have to use a file manager to reclaim the space.
 *
 * ADR-0013's language switcher lands here too, in Phase 3.
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    /**
     * The preserved directory is a **filesystem** read, not a `Flow` from Room, so it has no change
     * notification of its own — this ticks it after every write that could alter the listing.
     */
    private val refresh = MutableStateFlow(0)
    private val pendingDelete = MutableStateFlow<PreservedCopy?>(null)
    private val sampleData = MutableStateFlow<SampleDataOutcome?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            container.preferences.weightUnit,
            refresh,
            pendingDelete,
            sampleData,
        ) { unit, _, pending, seeded ->
            SettingsUiState(
                unit = unit,
                preserved = listPreservedCopies(container.preservedDir),
                pendingDelete = pending,
                sampleData = seeded,
            )
            // flowOn moves the *upstream* onto the IO dispatcher, which is where the directory
            // listing above belongs. Compose collects the result on the main thread as usual.
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Display only: entry stays in grams either way, and changes are always shown in grams. */
    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { container.preferences.setWeightUnit(unit) }
    }

    fun requestDelete(copy: PreservedCopy) {
        pendingDelete.value = copy
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val copy = pendingDelete.value ?: return
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { deletePreservedCopy(copy) }
            pendingDelete.value = null
            refresh.value++
        }
    }

    /** Debug builds only — the screen renders the row behind `BuildConfig.DEBUG`. */
    fun seedSampleData() {
        viewModelScope.launch {
            val seeded =
                seedSampleData(
                    bunnies = container.bunnyRepository,
                    fluffles = container.fluffleRepository,
                    weights = container.weightRepository,
                    observations = container.observationRepository,
                    symptoms = container.symptomRepository,
                )
            sampleData.value = if (seeded) SampleDataOutcome.SEEDED else SampleDataOutcome.ALREADY_PRESENT
        }
    }

    fun clearSampleDataOutcome() {
        sampleData.value = null
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BunnyTrackerApplication
                    SettingsViewModel(app.container)
                }
            }
    }
}
