package app.binky.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
    val materialYou: Boolean = false,
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
    val uiState: StateFlow<SettingsUiState> =
        combine(
            container.preferences.weightUnit,
            container.preferences.materialYou,
        ) { unit, materialYou ->
            SettingsUiState(unit = unit, materialYou = materialYou)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Display only: entry stays in grams either way, and changes are always shown in grams. */
    fun setUnit(unit: WeightUnit) {
        viewModelScope.launch { container.preferences.setWeightUnit(unit) }
    }

    /**
     * ADR-0027's opt-in, and the half of it the theme commit could not ship.
     *
     * Writing it re-themes the app on the spot: `MainActivity` collects the same preference in front
     * of `BinkyTheme`, so the switch moving and the app repainting are one recomposition — unlike
     * the language row above it, which can only take effect by recreating the Activity.
     */
    fun setMaterialYou(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setMaterialYou(enabled) }
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
