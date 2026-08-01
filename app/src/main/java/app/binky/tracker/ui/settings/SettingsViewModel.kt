package app.binky.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.seedSampleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the sample-data action did, for a one-line report. Debug builds only. */
enum class SampleDataOutcome { SEEDED, ALREADY_PRESENT }

data class SettingsUiState(
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val sampleData: SampleDataOutcome? = null,
)

/**
 * Settings — the minimum that has to exist before the weight screens make sense, plus the way in to
 * backup and restore.
 *
 * The preserved copies moved to the Backup screen in 3d: `preserved/` now holds pre-restore
 * snapshots as well as ADR-0007's wipe copies, and a snapshot has to sit beside the restore that can
 * load it back in rather than two screens away from it.
 *
 * ADR-0013's language switcher lands here too, in Phase 3.
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val sampleData = MutableStateFlow<SampleDataOutcome?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            container.preferences.weightUnit,
            sampleData,
        ) { unit, seeded ->
            SettingsUiState(unit = unit, sampleData = seeded)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Display only: entry stays in grams either way, and changes are always shown in grams. */
    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { container.preferences.setWeightUnit(unit) }
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
                    photos = container.photoRepository,
                    care = container.careRepository,
                    watches = container.watchRepository,
                    cacheDir = container.cacheDir,
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
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    SettingsViewModel(app.container)
                }
            }
    }
}
