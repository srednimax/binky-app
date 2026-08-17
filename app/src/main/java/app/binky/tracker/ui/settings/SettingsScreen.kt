package app.binky.tracker.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.BuildConfig
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.FormChip
import app.binky.tracker.ui.common.FormSection
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.common.SwitchRow
import app.binky.tracker.ui.reminders.RemindersOptIn
import app.binky.tracker.ui.support.SupportRequest
import app.binky.tracker.ui.support.currentSupportDiagnostics
import app.binky.tracker.ui.support.sendSupportMail
import app.binky.tracker.ui.support.supportBody
import app.binky.tracker.ui.support.supportSubject
import app.binky.tracker.work.scheduleDebugReminder

/**
 * Settings, reached from More. A detail screen, the same shape as the archived bunnies list.
 *
 * The weight display unit, and the way in to backup and restore. ADR-0013's language switcher lands
 * here too, with the Polish translation.
 *
 * ## Phase 7, against `9a` / `9b`
 *
 * Built by hand first, while this route was still on the *Not yet drawn* list, then checked against
 * the drawing when it arrived. **The hand-built structure held**: five settings separated by five
 * full-width `HorizontalDivider`s — the shape `6c` replaced on Backup & restore — become the header
 * rhythm and grouped cards, with Language and Backup & restore as rows in one card and the unit
 * chips as the standard filled/outlined pair. The rule that produced it generalises to every route
 * left: **a control that cannot name itself gets a [SectionHeader]; a row that names itself does
 * not.** *Show weights in* is a sentence two chips finish, so it is a header over a card, where the
 * three rows each say what they are inside the row.
 *
 * `9a` corrected it in two places, both of which the drawing argues for and both taken:
 *
 * - **The current language belongs in the row's trailing slot**, beside the chevron — not as a line
 *   under the title, where it read as a section heading of its own, and not (as the hand-built
 *   version had it) traded against the help text. The row keeps its help *and* reports its value.
 * - **The two debug blocks group under one header** instead of each repeating "Debug builds only"
 *   in its own body. See [DebugSection]; it is the only place on this screen where a string moved.
 *
 * **The drawing predates the Material You row and does not show it** — `9a` is built from the before
 * captures, and ADR-0027's toggle landed the same day. Kept, in the card the drawing gives it, since
 * removing a shipped setting is not what "new looks" means.
 *
 * Like `6a`, this route spends **none** of the raised budget: there is nothing here the app is
 * raising. And no filled button, which is `9c`'s rule read the other way — a screen whose actions
 * are all secondary has no primary to fill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold has already padded past the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // `section` at the bottom for `6c`'s reason: in a debug build this screen ends
                    // in a button rather than a card, and 8dp puts it under the gesture bar.
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            WeightUnitSetting(unit = state.unit, onSelect = viewModel::setUnit)

            GroupedCard {
                LanguageRow()
                // **Hidden below Android 12**, where there is no wallpaper palette to take.
                // `BinkyTheme` already ignores the preference there, and a switch that provably
                // does nothing is the pointless furniture ADR-0013 warned about. The stored value is
                // untouched either way, so a backup restored onto a newer phone still carries the
                // choice.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RowDivider()
                    // ADR-0027's other half: Binky owns its palette, and this is the way back to
                    // Material You. Until this row existed the decision was only half shipped —
                    // dynamic colour was not "off by default", it was unavailable.
                    SwitchRow(
                        title = stringResource(R.string.settings_material_you),
                        checked = state.materialYou,
                        onCheckedChange = viewModel::setMaterialYou,
                        helpText = stringResource(R.string.settings_material_you_help),
                    )
                }
                RowDivider()
                // A row rather than a section, and that was true before the redraw: backup carries a
                // destructive action and its own terminal screen, and a "Replace everything" button
                // does not belong on the screen the owner opened to change a unit. Now it is
                // literally the row grammar — this one is *telling*, so it chevrons away (ADR-0005).
                ListRow(
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_summary),
                    onClick = onOpenBackup,
                    trailing = { Chevron() },
                )
            }

            if (BuildConfig.DEBUG) {
                DebugSection(
                    outcome = state.sampleData,
                    onSeed = viewModel::seedSampleData,
                    onDismiss = viewModel::clearSampleDataOutcome,
                )
            }
        }
    }
}

/**
 * kg or grams — **display only**. Entry is in grams whatever this says, because that is what a scale
 * reads out, and changes are always shown in grams whichever is picked (house rule).
 *
 * The one section on this screen with a header, because *"Show weights in"* is a sentence the chips
 * finish. `FilterChip`s already, so the redraw is only that they now wrap rather than run off the
 * edge, and that the whole thing sits in a card.
 */
@Composable
private fun WeightUnitSetting(
    unit: WeightUnit,
    onSelect: (WeightUnit) -> Unit,
) {
    FormSection(
        title = stringResource(R.string.settings_weight_unit),
        // `tight`, not `base`: the help line is a footnote on the chips above it, not the next field.
        spacing = Spacing.tight,
    ) {
        ChipRow {
            WeightUnit.entries.forEach { option ->
                FormChip(
                    selected = option == unit,
                    onClick = { onSelect(option) },
                    label =
                        stringResource(
                            when (option) {
                                WeightUnit.KILOGRAMS -> R.string.settings_unit_kilograms
                                WeightUnit.GRAMS -> R.string.settings_unit_grams
                            },
                        ),
                )
            }
        }
        HelpText(stringResource(R.string.settings_weight_unit_help))
    }
}

/**
 * ADR-0013's language switcher — the Settings row that ADR originally hoped the whole thing would
 * be, before checking found the mechanism to be an activity base class and a root theme.
 *
 * **English alone in the list at 1.0**, and that is not the pointless furniture ADR-0013 warned
 * about. The mechanism landed at 3b, months before the translation it exists for; shipping the row
 * on top of it means the switcher is exercised by hand on a real phone in 1.0 rather than for the
 * first time in the week Polish arrives. At 3g the list grows by one entry and this code does not
 * change at all — which is the claim being tested.
 *
 * No ViewModel: the chosen language lives in [androidx.appcompat.app.AppCompatDelegate], not in this
 * app's preferences, and routing it through one would be a second copy of an answer the system also
 * owns.
 *
 * A [ListRow] whose subtitle is the **current** language: the row's job on the way past is to report
 * what is in force, and the help text it used to carry has moved into the dialog where it applies.
 */
@Composable
private fun LanguageRow() {
    // Local state, seeded from the delegate. Applying a language recreates the Activity, so this is
    // thrown away and re-read almost immediately — it exists so the dialog's radio button moves
    // under the finger rather than on the next frame after a recreation.
    var chosen by remember { mutableStateOf(currentAppLanguage()) }
    var picking by remember { mutableStateOf(false) }

    // The translation-report hand-off. Resolved here because the tap happens in an ordinary lambda,
    // where `stringResource` is not callable — the same shape `SupportScreen` uses.
    val context = LocalContext.current
    val diagnostics = currentSupportDiagnostics()
    val translationSubject = stringResource(R.string.support_translation_subject)
    val bugPrompt = stringResource(R.string.support_bug_prompt)
    val noMailApp = stringResource(R.string.support_no_mail_app)
    var mailFailed by remember { mutableStateOf(false) }

    ListRow(
        title = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_help),
        onClick = { picking = true },
        // The current language rides in the **trailing** slot beside the chevron rather than
        // standing as a line under the title, which is `9a`'s one correction to this row: as a line
        // it read as a section heading of its own. A row that both tells and opens carries the
        // answer *and* the arrow.
        trailing = {
            Text(
                text =
                    chosen?.let { stringResource(it.labelRes) }
                        ?: stringResource(R.string.settings_language_system),
                style = MaterialTheme.typography.bodyMedium,
            )
            Chevron()
        },
    )

    if (picking) {
        BinkyDialog(
            title = stringResource(R.string.settings_language),
            onDismiss = { picking = false },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            // Kotlin note: `listOf(null) + entries` builds the offered list with the
            // follow-the-phone case as a first-class member rather than a special row, so the
            // radio group has one shape and one selection rule.
            Column(modifier = Modifier.selectableGroup()) {
                (listOf(null) + AppLanguage.entries).forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == chosen,
                                    role = Role.RadioButton,
                                    onClick = {
                                        chosen = option
                                        picking = false
                                        setAppLanguage(option)
                                    },
                                )
                                // M3's minimum target. The radio button alone is smaller than one,
                                // and the whole row is what takes the tap.
                                .heightIn(min = 48.dp),
                    ) {
                        RadioButton(selected = option == chosen, onClick = null)
                        Text(
                            text =
                                option?.let { stringResource(it.labelRes) }
                                    ?: stringResource(R.string.settings_language_system),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // **The report path lives here and not only on Support**, because this is where an owner
            // is standing at the moment a translation reads wrong. Seven of the nine languages ship
            // without a native read-through, so this row is the mechanism that replaces one — and a
            // channel nobody can find is the same as no channel.
            //
            // It sends an ordinary bug report: `SupportRequest.BUG`, so the one inbox filter that
            // already covers every locale catches it, with a description of its own so the maintainer
            // can still tell the two apart on sight. `supportBody` stamps the resolved locale into the
            // block, which is what makes such a report self-identifying without asking the sender
            // which language they are in.
            HorizontalDivider()
            ListRow(
                title = stringResource(R.string.settings_language_report),
                subtitle = stringResource(R.string.settings_language_report_help),
                onClick = {
                    val sent =
                        context.sendSupportMail(
                            subject = supportSubject(SupportRequest.BUG, translationSubject, diagnostics),
                            body = supportBody(SupportRequest.BUG, bugPrompt, diagnostics),
                        )
                    // The dialog stays open on failure, which is why this screen needs no snackbar
                    // host: a message behind a dismissed dialog is a message nobody reads.
                    if (sent) picking = false else mailFailed = true
                },
            )
            if (mailFailed) {
                Text(
                    text = noMailApp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.base),
                )
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
