package app.binky.tracker.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import app.binky.tracker.ui.support.SupportRequest
import app.binky.tracker.ui.support.currentSupportDiagnostics
import app.binky.tracker.ui.support.sendSupportMail
import app.binky.tracker.ui.support.supportBody
import app.binky.tracker.ui.support.supportSubject

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
 *   in its own body. See [DebugSettings]; it is the only place on this screen where a string moved.
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

            // Debug builds only — and a *source set* rather than a runtime flag. `src/release/`'s
            // DebugSettings is an empty composable, so the release build never compiles the seeder
            // or the two-minute reminder, where `if (BuildConfig.DEBUG)` only hid them (9k).
            DebugSettings()
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
