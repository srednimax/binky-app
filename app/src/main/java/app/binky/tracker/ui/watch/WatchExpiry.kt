package app.binky.tracker.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.R
import app.binky.tracker.data.TrendFlag
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.data.evaluateTrend
import app.binky.tracker.data.toAcknowledgment
import app.binky.tracker.data.toWeighing
import app.binky.tracker.data.watchState
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.weight.TrendFlagBanner
import app.binky.tracker.ui.weight.instantDateLabel
import app.binky.tracker.ui.weight.weightLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * One expired watch, with the answer to the question the owner is actually being asked.
 *
 * **The current trend travels with it** (ADR-0001): "is it still dropping" is exactly what "extend
 * or close?" comes down to, and a prompt that made the owner leave it to go and look would be a
 * prompt they answer by guessing.
 */
data class ExpiredWatchPrompt(
    val bunnyId: String,
    val bunnyName: String,
    /** Null when the series says nothing worth flagging — **never** rendered as reassurance. */
    val flag: TrendFlag?,
    val lastGrams: Int? = null,
    val lastRecordedAt: Instant? = null,
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
)

/**
 * The auto-expiry prompt, which is app-wide rather than any screen's (ADR-0001).
 *
 * **The nagging has already stopped by the time this appears.** Expiry is what ends the chasing —
 * `watchesDueForNagging` skips an expired row — so this dialog is never the urgent thing. It is only
 * about re-arming, and an unanswered prompt is not an active watch.
 *
 * **Queued one at a time, with no queue.** The flow emits the soonest-ended expired watch; answering
 * it disposes of that row, the flow re-emits, and the next one appears. Two expired watches are two
 * dialogs in sequence and nothing has to remember that.
 *
 * Its own `ViewModel` rather than fields on `AppShellViewModel`, which is deliberately the switcher,
 * the bar and the scope line — the same separation `setupState` already draws there.
 */
class WatchExpiryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val watches = container.watchRepository
    private val weights = container.weightRepository

    /** Just enough to decide *whether* to prompt, before the weight series is read beneath it. */
    private data class Expired(
        val bunnyId: String,
        val bunnyName: String,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val prompt: StateFlow<ExpiredWatchPrompt?> =
        combine(
            watches.watches,
            // Active bunnies only. Archiving deletes the watch, so a row for an archived bunny
            // should not exist — and if one ever did, prompting about a memorial is the failure
            // ADR-0001 names, not something to hedge (ADR-0004).
            container.bunnyRepository.activeBunnies,
        ) { rows, active ->
            // The clock, read on every emission rather than stored: a watch expires without
            // anything being written, so the row that prompts is the row that did not, unchanged.
            val now = Instant.now()
            rows.firstNotNullOfOrNull { row ->
                if (watchState(row, now) !is WatchState.Expired) return@firstNotNullOfOrNull null
                active.firstOrNull { it.id == row.bunnyId }?.let { Expired(it.id, it.name) }
            }
        }.distinctUntilChanged()
            .flatMapLatest { expired ->
                if (expired == null) {
                    flowOf(null)
                } else {
                    combine(
                        weights.series(expired.bunnyId),
                        weights.acknowledgment(expired.bunnyId),
                        container.preferences.weightUnit,
                    ) { series, acknowledgment, unit ->
                        val latest = series.firstOrNull()
                        ExpiredWatchPrompt(
                            bunnyId = expired.bunnyId,
                            bunnyName = expired.bunnyName,
                            flag =
                                evaluateTrend(
                                    series.map { it.toWeighing() },
                                    acknowledgment?.toAcknowledgment(),
                                ).flag,
                            lastGrams = latest?.grams,
                            lastRecordedAt = latest?.recordedAt,
                            unit = unit,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Re-arms for a full duration from now, and clears the nag watermark so tomorrow morning asks.
     *
     * Cancelling the posted nag too: the owner has just said they are still watching, and yesterday
     * morning's unanswered question in the shade is about a watch that no longer exists in that
     * shape.
     */
    fun extend(duration: WatchDuration) {
        val bunnyId = prompt.value?.bunnyId ?: return
        viewModelScope.launch {
            watches.extend(bunnyId, duration)
            container.watchNotifier.cancel(bunnyId)
        }
    }

    /**
     * **Close, dismiss and swipe-away are one action**: the row is deleted.
     *
     * That is what makes "prompts once" true without a column recording it, and nothing is lost —
     * starting a new watch is the same single tap as extending.
     */
    fun close() {
        val bunnyId = prompt.value?.bunnyId ?: return
        viewModelScope.launch {
            watches.close(bunnyId)
            container.watchNotifier.cancel(bunnyId)
        }
    }

    /** The flag's own action, reachable because the prompt renders the flag rather than a copy. */
    fun acknowledge() {
        val bunnyId = prompt.value?.bunnyId ?: return
        viewModelScope.launch { weights.acknowledgeTrend(bunnyId) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    WatchExpiryViewModel(app.container)
                }
            }
    }
}

/**
 * Hosts the expiry prompt from the app shell, so it appears over whatever the owner opened onto.
 *
 * Renders nothing when no watch has expired, which is almost always.
 */
@Composable
fun WatchExpiryHost() {
    val viewModel: WatchExpiryViewModel = viewModel(factory = WatchExpiryViewModel.Factory)
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()

    prompt?.let {
        WatchExpiredDialog(
            prompt = it,
            onExtend = viewModel::extend,
            onClose = viewModel::close,
            onAcknowledge = viewModel::acknowledge,
        )
    }
}

/**
 * Extend or close, with the current trend in front of the owner.
 *
 * `onDismiss` is [onClose] and not a no-op: swiping the dialog away **is** an answer, and
 * treating it as anything else would leave an unanswered row occupying the only watch slot that
 * bunny has, forever, with nothing on screen ever mentioning it again.
 */
@Composable
private fun WatchExpiredDialog(
    prompt: ExpiredWatchPrompt,
    onExtend: (WatchDuration) -> Unit,
    onClose: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    var duration by remember { mutableStateOf(WatchDuration.Default) }

    BinkyDialog(
        title = stringResource(R.string.watch_expired_title, prompt.bunnyName),
        onDismiss = onClose,
        confirmButton = {
            // *Extend it* is the confirming action and sits last (`8c`).
            TextButton(onClick = { onExtend(duration) }) {
                Text(stringResource(R.string.watch_expired_extend))
            }
        },
        dismissButton = {
            // **Quiet, not destructive-red.** Closing a watch is an ordinary answer to the question,
            // not a deletion: the row it removes is a present-tense state rather than a record. The
            // same treatment *Delete document* takes inside its menu (`10b`).
            TextButton(
                onClick = onClose,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(stringResource(R.string.watch_close))
            }
        },
    ) {
        // One child, so `BinkyDialog`'s own 16dp rhythm never applies — `8c` sets the prompt at
        // 12dp, tighter, because the flag it nests is already a card with its own padding.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
            Text(
                text = stringResource(R.string.watch_expired_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            // The flag itself, not a second telling of it — `TrendFlagUi` is where that
            // sentence is written, and a prompt that paraphrased it could drift from the banner
            // the owner reads everywhere else. It renders nothing when there is no live flag.
            //
            // `nested = true` is what `8c` calls "it steps up one surface, the same move a nested
            // flag makes on the all-bunnies list". The colour comes from the dialog's
            // `LocalCardSurface` either way; what `nested` buys here is the 16dp radius, because a
            // card inside a dialog is a card inside a card. This and the flag on Home's list are
            // the only two-level nesting in the app, and the ceiling of it.
            TrendFlagBanner(
                bunnyName = prompt.bunnyName,
                flag = prompt.flag,
                unit = prompt.unit,
                onAcknowledge = onAcknowledge,
                nested = true,
            )
            // And when there is none, the record rather than a verdict: absence of a flag is
            // never evidence of health (ADR-0001), so this says what was weighed and when, and
            // makes no claim at all about the bunny. `8d` draws it with no container of its own,
            // so it reads as a fact rather than as a card competing with the flag it replaces.
            if (prompt.flag !is TrendFlag.WorthACloserLook && prompt.flag !is TrendFlag.Acknowledged) {
                LastWeighingLine(prompt)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(
                    text = stringResource(R.string.watch_duration_question),
                    style = MaterialTheme.typography.labelLarge,
                    // The dialog's text slot is `onSurfaceVariant`, which is right for the body and
                    // the last-weighing line but not for a question expecting an answer below it.
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WatchDurationChoice(selected = duration, onSelect = { duration = it })
            }
        }
    }
}

@Composable
private fun LastWeighingLine(prompt: ExpiredWatchPrompt) {
    val grams = prompt.lastGrams
    val recordedAt = prompt.lastRecordedAt
    Text(
        text =
            if (grams == null || recordedAt == null) {
                stringResource(R.string.watch_expired_no_weight)
            } else {
                stringResource(
                    R.string.watch_expired_weight,
                    weightLabel(grams, prompt.unit),
                    instantDateLabel(recordedAt),
                )
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
