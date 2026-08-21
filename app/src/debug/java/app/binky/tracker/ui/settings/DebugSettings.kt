package app.binky.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import app.binky.tracker.data.seedSampleData
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.reminders.RemindersOptIn
import app.binky.tracker.work.scheduleDebugReminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * **The debug half of Settings, compiled only into the debug build** (Phase 9, `9k`).
 *
 * It used to live in `main/` behind `if (BuildConfig.DEBUG)` at the call site, which is a *runtime*
 * guard: with `isMinifyEnabled = false` nothing removes a branch whose condition is a compile-time
 * `false`, so the seeder, the two-minute reminder and this section were all compiled into the
 * release AAB — unreachable, but present, and their thirteen strings were being translated into
 * nine languages for nobody to read.
 *
 * The seam is a source set instead. `SettingsScreen` calls [DebugSettings] unconditionally; this
 * file answers in a debug build and `src/release/`'s no-op answers in a release one, so the release
 * artifact does not contain the code at all rather than containing code it cannot reach. **Adding a
 * debug affordance means adding it here**, not in `main/` behind a flag.
 *
 * The strings moved with it, to `src/debug/res/values/strings.xml`, which is outside
 * `scripts/translation-gate.py`'s scope (`app/src/main/res`) — that is what takes them off the
 * nine-language bill. `scripts/edge-to-edge.py` still taps two of the labels by name and merges this
 * overlay into its English needle table for exactly that reason.
 */
@Composable
fun DebugSettings() {
    val viewModel: DebugSettingsViewModel =
        viewModel(factory = DebugSettingsViewModel.Factory, extras = appViewModelExtras())
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    DebugSection(outcome = outcome, onSeed = viewModel::seed, onDismiss = viewModel::dismiss)
}

/** What the sample-data action did, for a one-line report. Debug builds only. */
enum class SampleDataOutcome { SEEDED, ALREADY_PRESENT }

/**
 * A **second** ViewModel on the Settings screen, against the one-per-screen house rule, and
 * deliberately.
 *
 * The rule exists so a screen has one place its state comes from; this state belongs to a section
 * that does not exist in the shipped app, and folding it back into [SettingsViewModel] would put
 * `SampleDataOutcome`, `seedSampleData` and their imports right back in `main/` — which is the whole
 * of what `9k` removed.
 *
 * It is a ViewModel rather than a `rememberCoroutineScope` because [seedSampleData] is **not
 * idempotent by merging** — it declines to run twice by checking whether its first bunny is already
 * there. A seed cancelled halfway leaves that bunny behind and every later run reports
 * `ALREADY_PRESENT` over a fixture missing most of its rows, which the capture harness then
 * photographs. `viewModelScope` survives a rotation; a composition-scoped one does not.
 */
class DebugSettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val sampleData = MutableStateFlow<SampleDataOutcome?>(null)

    /**
     * Kotlin note: `asStateFlow()` is the read-only view of the mutable one above — the same idea as
     * returning a getter-only wrapper rather than the store itself, so nothing outside this class
     * can write to it.
     */
    val outcome: StateFlow<SampleDataOutcome?> = sampleData.asStateFlow()

    fun seed() {
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
                    vets = container.vetRepository,
                    visits = container.visitRepository,
                    medications = container.medicationRepository,
                    documents = container.documentRepository,
                    cacheDir = container.cacheDir,
                )
            sampleData.value = if (seeded) SampleDataOutcome.SEEDED else SampleDataOutcome.ALREADY_PRESENT
        }
    }

    fun dismiss() {
        sampleData.value = null
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as BinkyApplication
                    DebugSettingsViewModel(app.container)
                }
            }
    }
}

/**
 * The two things that vanish in a release build, under **one** header that says so once.
 *
 * `9a`'s third structural change, and the reason is accuracy rather than length: both help strings
 * used to open with "Debug builds only", which said it twice and said it in the wrong place — it is
 * a fact about the *section*, not about seeding a fluffle. So the phrase is hoisted to a
 * [app.binky.tracker.ui.common.SectionHeader] and both bodies start at the sentence that describes
 * what the tool does. Three strings touched in both locales, no new words invented.
 *
 * They share a card and a divider because they are one class of thing, where every other section on
 * this screen is its own. The actions stay **text buttons** pulled back to the card's text edge
 * (a text button carries its own padding, so one laid out flush looks indented) — nothing here is
 * the screen's primary action, and `9c`'s one-filled-button-per-screen rule means Settings has none.
 *
 * ## Both blocks in detail
 *
 * *Sample data* is **the tail of the scrolling column on purpose**: `edge-to-edge.py`'s `seed()`
 * reaches *Add the sample data* by letting `tap` scroll until it finds the label.
 *
 * *Reminder delivery* is **what makes 4a provable with no reminders in existence** (ADR-0024): a
 * notification two minutes from now, on its own one-shot path rather than through the daily sweep.
 * It was also the point-of-use host for [RemindersOptIn] until 4c gave it a real one on the Care
 * screen — which is what proves ADR-0006's "one composable in two hosts" claim rather than leaving
 * it as an intention.
 *
 * **Both button labels are load-bearing outside the app**: `edge-to-edge.py`'s `reminders-sheet`
 * scenes reach the sheet by tapping *Reminder settings* by name, and `seed()` taps *Add the sample
 * data*. The redraw changes no label on this screen.
 *
 * The sheet itself is left alone — *Reminders opt-in* is its own entry on the undrawn list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSection(
    outcome: SampleDataOutcome?,
    onSeed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var scheduled by remember { mutableStateOf(false) }
    var optingIn by remember { mutableStateOf(false) }

    Column {
        SectionHeader(stringResource(R.string.settings_debug_header))
        Spacer(Modifier.height(Spacing.tight))
        GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
            DebugBlock(
                title = stringResource(R.string.settings_sample_data),
                help = stringResource(R.string.settings_sample_data_help),
            ) {
                DebugAction(stringResource(R.string.settings_sample_data_action), onSeed)
            }

            Spacer(Modifier.height(Spacing.snug))
            // A plain divider, not a `RowDivider`: the card has already inset its contents, and a
            // second inset would leave the line hanging away from the text either side of it.
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.snug))

            DebugBlock(
                title = stringResource(R.string.settings_debug_reminder),
                help = stringResource(R.string.settings_debug_reminder_help),
            ) {
                DebugAction(
                    stringResource(R.string.settings_debug_reminder_settings_action),
                ) { optingIn = true }
                DebugAction(stringResource(R.string.settings_debug_reminder_action)) {
                    scheduleDebugReminder(context)
                    scheduled = true
                }
            }
        }
    }

    if (outcome != null) {
        BinkyDialog(
            title = stringResource(R.string.settings_sample_data),
            onDismiss = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            },
        ) {
            Text(
                stringResource(
                    when (outcome) {
                        SampleDataOutcome.SEEDED -> R.string.settings_sample_data_seeded
                        SampleDataOutcome.ALREADY_PRESENT -> R.string.settings_sample_data_present
                    },
                ),
            )
        }
    }

    if (optingIn) {
        ModalBottomSheet(onDismissRequest = { optingIn = false }) {
            Column(
                // **A sheet is its own window, so the shell's Scaffold pads none of this** (PLAN 4f).
                // Anchored to the bottom edge, which is exactly where the navigation bar is: without
                // the padding the autostart explanation ran under the three-button bar with the nav
                // icons drawn over the words, and the rest of it off the bottom of the screen. The
                // scroll is the other half — this text does not fit a 1220px-tall landscape screen,
                // and a sheet that cannot scroll simply loses whatever did not fit.
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                Text(text = stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineSmall)
                RemindersOptIn()
                TextButton(onClick = { optingIn = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }

    if (scheduled) {
        // Titled with the section's own name rather than left title-less as it was: [BinkyDialog]
        // is what puts a dialog on the right container level in both themes, and it wants a title.
        // No new string — the section is already called *Reminder delivery*, which is what this is.
        BinkyDialog(
            title = stringResource(R.string.settings_debug_reminder),
            onDismiss = { scheduled = false },
            confirmButton = {
                TextButton(onClick = { scheduled = false }) { Text(stringResource(R.string.action_ok)) }
            },
        ) {
            Text(stringResource(R.string.settings_debug_reminder_scheduled))
        }
    }
}

/**
 * One debug tool: what it is, what it does, and the actions that run it.
 *
 * The title is `bodyLarge` rather than [SectionHeader]'s `titleSmall` — it names a block *inside* a
 * card, and the card's own header has already named the group.
 */
@Composable
private fun DebugBlock(
    title: String,
    help: String,
    actions: @Composable FlowRowScope.() -> Unit,
) {
    Text(text = title, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(Spacing.hair))
    HelpText(help)
    // Wrapping, because "Remind me in two minutes" and "Reminder settings" do not fit one line on a
    // narrow phone and a row that clipped one of them would hide a control entirely.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        // Pulled back to the text edge on the left and bottom: a text button carries its own
        // padding, so a row of them laid out flush looks indented against the paragraph above.
        modifier = Modifier.offset(x = -Spacing.snug),
        content = actions,
    )
}

/** A debug tool's action. A text button: nothing on this screen is its primary action. */
@Composable
private fun DebugAction(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) { Text(label) }
}
