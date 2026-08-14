package app.binky.tracker.ui.observations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.R
import app.binky.tracker.data.ParticipantExclusion
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.growthStageNow
import app.binky.tracker.data.healthyDayFacts
import app.binky.tracker.data.preSelectParticipants
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.bunny.joinNames
import app.binky.tracker.ui.common.ListRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * **The two ways to record a day, behind the one "+"** (Phase 7.5 §6).
 *
 * The healthy day used to be a full-width button inside the Observations timeline while the "+"
 * opened the long form, so the app had two ways to say "I looked at my rabbit today", no visual
 * relationship between them, and the *discoverable* one was the long one. That is backwards: the
 * healthy day is the path meant to be taken most often, and ADR-0001's whole position is that
 * silence means nobody looked — a shortcut nobody finds does not turn silence into a record.
 *
 * **A sheet rather than a menu, because of what has to travel with the label.** One tap commits
 * four facts plus `symptomsChecked` on the owner's behalf ([healthyDayFacts]), and they are entitled
 * to know which — a menu item is a label with nowhere to put that, where a [ListRow] has a subtitle.
 *
 * It costs the healthy day one tap, taken deliberately: one tap is cheap, being unfindable is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDaySheet(
    onHealthyDay: () -> Unit,
    onObservation: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // **A sheet is its own window, so the shell's Scaffold pads none of this** (PLAN 4f) — the
        // last row would otherwise sit under the navigation bar.
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = Spacing.base)) {
            // The shortcut first: it is the one the report was about, and the one an ordinary day
            // wants. Both labels are the ones these actions already carried — nicer wording was
            // considered and not taken, because two rewrites are two strings in nine languages.
            ListRow(
                title = stringResource(R.string.healthy_day_action),
                subtitle = stringResource(R.string.healthy_day_help),
                onClick = onHealthyDay,
            )
            ListRow(
                title = stringResource(R.string.observation_add_title),
                onClick = onObservation,
            )
        }
    }
}

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

/**
 * The one-tap healthy day, and the receipt it owes.
 *
 * **Shell-scoped, not screen-scoped** (Phase 7.5 §6). The write is reached from the "+" now, and the
 * "+" is the shell's — it is the same button on Home and on Observations, so a ViewModel belonging
 * to either screen would be the wrong owner for it. The snackbar is already the shell's for the same
 * reason: the FAB has to be lifted above the Undo action, and only the Scaffold that owns both can
 * do that.
 */
class HealthyDayViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val observations = container.observationRepository
    private val bunnies = container.bunnyRepository
    private val fluffles = container.fluffleRepository
    private val weights = container.weightRepository
    private val watches = container.watchRepository

    private val _receipt = MutableStateFlow<HealthyDayReceipt?>(null)
    val receipt: StateFlow<HealthyDayReceipt?> = _receipt.asStateFlow()

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
            _receipt.value =
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
        val written = _receipt.value ?: return
        _receipt.value = null
        viewModelScope.launch { observations.delete(written.observationId) }
    }

    fun dismissReceipt() {
        _receipt.value = null
    }

    /** Whether this bunny's weight series is currently worth a closer look (ADR-0001). */
    private suspend fun flagged(bunnyId: String): Boolean {
        val evaluation =
            evaluateTrend(
                series = weights.series(bunnyId).first().map { it.toWeighing() },
                acknowledgment = weights.acknowledgment(bunnyId).first()?.toAcknowledgment(),
                growth = growthStageNow(bunnies.bunnyNow(bunnyId)?.birthDate),
            )
        return evaluation.flag is TrendFlag.WorthACloserLook
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    HealthyDayViewModel(app.container)
                }
            }
    }
}

/**
 * The snackbar that names who the healthy day covered, with **Undo** (ADR-0008).
 *
 * A `LaunchedEffect` keyed on the receipt, because `showSnackbar` suspends until the snackbar is
 * dismissed or its action is tapped — the Compose way of saying "show this, then tell me what
 * happened". There is no promise equivalent; the nearest is an effect that awaits a user event.
 */
@Composable
fun HealthyDaySnackbar(
    receipt: HealthyDayReceipt?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val message =
        receipt?.let {
            // The flag is named beside the bunny it belongs to rather than in a trailing clause, so
            // "Bijou (weight flag) & Nugget" cannot be misread as covering both.
            val names =
                it.names.map { name ->
                    if (name in it.flaggedNames) resources.getString(R.string.healthy_day_name_flagged, name) else name
                }
            val logged = resources.getString(R.string.healthy_day_logged, joinNames(resources, names))
            // ADR-0008 wants the exclusion *and* its reason, and this is the only surface the
            // one-tap path has to put the reason on. Appended rather than shown as a second
            // snackbar: two in a row would make the owner wait to reach Undo, and the whole point
            // of the receipt is that a wrong attribution is reversible immediately.
            if (it.watchedOutNames.isEmpty()) {
                logged
            } else {
                logged + " " +
                    resources.getQuantityString(
                        R.plurals.healthy_day_excluded_watch,
                        it.watchedOutNames.size,
                        joinNames(resources, it.watchedOutNames),
                    )
            }
        }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(receipt) {
        if (message == null) return@LaunchedEffect
        val result = hostState.showSnackbar(message = message, actionLabel = undoLabel)
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onDismiss()
        }
    }
}
