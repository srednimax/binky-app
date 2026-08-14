package app.binky.tracker.ui.support

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.BinkyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LicencesUiState(
    val loading: Boolean = true,
    val groups: List<LicenceGroup> = emptyList(),
    /**
     * The SPDX identifiers whose full text is bundled, so a group knows whether it can offer the
     * licence itself or only a link to it. Computed from the assets rather than hardcoded: adding
     * `licences/MIT.txt` is then the only step, and forgetting one degrades to the link rather than
     * to a dead row.
     */
    val bundledTexts: Set<String> = emptySet(),
)

/**
 * The attribution list (Phase 7.5 §3).
 *
 * **A `ViewModel` here where [SupportScreen] has none**, and the difference is the point: Support
 * reads three compile-time constants, this one reads a ~50 KB asset off disk and parses it. That is
 * work worth doing once per screen rather than once per recomposition, and worth surviving a
 * rotation — which is exactly what the house rule's one-ViewModel-per-screen buys.
 *
 * It takes an [AssetManager] rather than `AppContainer`: nothing here is a repository, and the list
 * is a build constant baked into the APK rather than anything an owner can change.
 */
class LicencesViewModel(
    private val assets: AssetManager,
) : ViewModel() {
    private val state = MutableStateFlow(LicencesUiState())
    val uiState: StateFlow<LicencesUiState> = state.asStateFlow()

    init {
        // Kotlin note: `viewModelScope.launch` is the coroutine that dies with this ViewModel, and
        // `withContext(Dispatchers.IO)` moves just the blocking part off the main thread — closer to
        // `await someWorkerPromise()` than to spawning a thread, because the coroutine resumes back
        // on the caller's dispatcher when the block returns.
        viewModelScope.launch {
            val loaded =
                withContext(Dispatchers.IO) {
                    val groups =
                        runCatching {
                            groupLicences(assets.open(LICENCES_ASSET).bufferedReader().use { it.readText() })
                        }.getOrDefault(emptyList())
                    // `list` returns null for a directory that is not there, which is the state a
                    // stripped build would be in.
                    val bundled =
                        assets
                            .list(LICENCE_TEXT_DIRECTORY)
                            .orEmpty()
                            .map { it.removeSuffix(".txt") }
                            .toSet()
                    LicencesUiState(loading = false, groups = groups, bundledTexts = bundled)
                }
            state.value = loaded
        }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    LicencesViewModel(app.assets)
                }
            }
    }
}
