package app.binky.tracker.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemindersUiState(
    /**
     * Defaults to **true**, which is the opposite of the stored default and deliberate: this gates a
     * prompt that appears on its own, and the honest wrong answer for one frame is "already asked".
     * Defaulting to false would flash a battery-optimisation card at every owner who has already
     * dismissed one, for as long as DataStore takes to answer.
     */
    val batteryExemptionAsked: Boolean = true,
)

/**
 * The reminders opt-in's one piece of stored state: whether the battery-optimisation exemption has
 * been offered (ADR-0003's Phase 4a amendment).
 *
 * Everything else the screen shows is read straight off the phone on each resume — the permission,
 * the channel's importance and the exemption itself all live outside this app and can change while
 * the owner is in Android's settings, so a copy held in a ViewModel would be a second answer that
 * goes stale the moment it matters.
 */
class RemindersViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val uiState: StateFlow<RemindersUiState> =
        container.preferences.batteryExemptionAsked
            .map { asked -> RemindersUiState(batteryExemptionAsked = asked) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    /** Recorded on the way into the system screen *and* on "Not now": both are having been asked. */
    fun markBatteryExemptionAsked() {
        viewModelScope.launch { container.preferences.markBatteryExemptionAsked() }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    RemindersViewModel(app.container)
                }
            }
    }
}
