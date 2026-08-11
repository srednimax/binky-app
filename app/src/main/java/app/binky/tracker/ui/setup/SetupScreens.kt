package app.binky.tracker.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.backup.BackupScopePicker
import app.binky.tracker.ui.backup.PhotosNotProtectedNote
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.common.openSystemBackupSettings
import app.binky.tracker.ui.reminders.RemindersOptIn
import app.binky.tracker.ui.shell.BunnySummary

/** How many steps the wizard has, so the counter and the last step's button agree on the end. */
private const val SETUP_STEPS = 3

/**
 * Step one: add your first bunny, or don't (ADR-0006).
 *
 * **Skippable, and it says so on the button rather than in the small print.** An owner who came to
 * look around before committing a real animal's records is not a failure state, and a wizard that
 * cannot be got past is the fastest way to an uninstall.
 *
 * @param bunnies the live list of active bunnies. Read rather than passed a flag, so the step
 *   reports what actually happened in the editor — the editor's own callback fires identically on
 *   save and on cancel, and this is the source that cannot be wrong about which it was.
 */
@Composable
fun SetupBunnyStep(
    bunnies: List<BunnySummary>,
    onAddBunny: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val added = bunnies.isNotEmpty()

    SetupStep(
        step = 1,
        title = stringResource(R.string.setup_bunny_title),
        // The one step with no card on it, so it keeps the wide text inset rather than lining up
        // with cards it does not have.
        gutter = Spacing.section,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Text(text = stringResource(R.string.setup_bunny_body), style = MaterialTheme.typography.bodyMedium)

            if (added) {
                Text(
                    // Two strings rather than a `<plurals>`: the singular names the bunny and the
                    // plural cannot, so they are different sentences, not one sentence with a count in
                    // it. Pluralising a string whose *argument* changes shape is how a translation ends
                    // up with a name where a number should be (ADR-0013).
                    text =
                        if (bunnies.size == 1) {
                            stringResource(R.string.setup_bunny_added_one, bunnies.first().name)
                        } else {
                            stringResource(R.string.setup_bunny_added_many)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // `Spacing.block` above the actions, which the step's own 16dp makes out of 16 more.
            StepActions(modifier = Modifier.padding(top = Spacing.base)) {
                if (added) {
                    PrimaryStepButton(onClick = onContinue, label = stringResource(R.string.setup_continue))
                    OutlinedButton(onClick = onAddBunny, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_bunny_add_another))
                    }
                } else {
                    PrimaryStepButton(onClick = onAddBunny, label = stringResource(R.string.setup_bunny_action))
                    TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_bunny_skip))
                    }
                }
            }
        }
    }
}

/**
 * Step two: what protects these records, and what does not (ADR-0005, ADR-0006).
 *
 * The scope choice is here rather than in Settings because **a backup buried in settings never gets
 * made**, which is the whole of ADR-0006's argument for a first-run step at all.
 *
 * The Android backup question is asked, not answered: the app genuinely cannot read that switch, so
 * the honest move is to say so and open the screen where it lives. This is the one moment the owner
 * is already thinking about backup, and every alternative amounts to assuming.
 *
 * There is deliberately **no last-backup status line** here, unlike Backup settings. On a first run
 * it would always read "No automatic backup has been recorded on this phone" — true, and useless as
 * a signal, because nothing has had a chance to record one yet. A line that can only say one thing
 * is not a status.
 */
@Composable
fun SetupBackupStep(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SetupStep(
        step = 2,
        title = stringResource(R.string.setup_backup_title),
        modifier = modifier,
    ) {
        // `10f`: the two horizontal rules that used to divide this step were doing the work of
        // structure, and a line through a screen is the weakest way to say "a new subject starts
        // here". They become section headers on the app's 24-up / 8-down rhythm — which is why this
        // column sits at 8dp and every header adds 16 of its own on top.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(text = stringResource(R.string.setup_backup_body), style = MaterialTheme.typography.bodyMedium)

            SectionHeader(
                text = stringResource(R.string.backup_auto_title),
                modifier = Modifier.padding(top = Spacing.base),
            )
            // What Android's own backup is, what it leaves out, and the one screen that owns the
            // switch — one subject, so one card, rather than four loose paragraphs the eye has to
            // group for itself.
            GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
                    Text(
                        text = stringResource(R.string.backup_auto_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.setup_backup_auto_ask),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.backup_auto_photos),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { context.openSystemBackupSettings() },
                        modifier = Modifier.padding(top = Spacing.hair),
                    ) {
                        Text(stringResource(R.string.backup_auto_settings_action))
                    }
                }
            }

            SectionHeader(
                text = stringResource(R.string.backup_scope_title),
                modifier = Modifier.padding(top = Spacing.base),
            )
            // The picker draws rows, not a card — Backup shares its card with the photo warning and the
            // export button, so the caller supplies one. Zero content padding because a selected row is
            // a full-bleed fill that the card's own corners clip at the ends.
            GroupedCard(contentPadding = PaddingValues(0.dp)) {
                BackupScopePicker(scope = state.scope, onSelect = viewModel::setScope)
            }
            // Apricot here and nowhere else on the step: it is the one sentence that describes
            // something the owner can actually lose. On Backup settings the same note stays plain,
            // because there it sits inside the export block that already qualifies it (`6c`).
            PhotosNotProtectedNote(caution = true, modifier = Modifier.padding(top = Spacing.hair))

            Text(
                text = stringResource(R.string.setup_backup_changeable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.hair),
            )

            StepActions(modifier = Modifier.padding(top = Spacing.base)) {
                // Continue, not Finish: the reminders step is now the last one, and it is the only
                // place the wizard ends. Two steps that both wrote the completion flag would be two
                // answers to one question.
                PrimaryStepButton(onClick = onContinue, label = stringResource(R.string.setup_continue))
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_back))
                }
            }
        }
    }
}

/**
 * Step three: turn reminders on, or don't (ADR-0006).
 *
 * **Our own screen, with the system dialog behind a button on it** — never the bare dialog on
 * launch. Android permits two `POST_NOTIFICATIONS` denials before it stops asking for good, and a
 * prompt shown before the owner knows what it is for is the most likely to be dismissed. This phase
 * spends the first denial, so it spends it on a screen that has said what it wants and why.
 *
 * The body is [RemindersOptIn] verbatim — the same composable the point-of-use sheet hosts. A
 * wizard-shaped copy would be a second place for two denials to be spent from, which is precisely
 * the arithmetic ADR-0006 exists to protect.
 *
 * **Skippable, and skipping is re-asked at the point of use and nowhere else.** Upgraders are not
 * re-onboarded: an upgrader is indistinguishable from a skipper, and `resolveSetupState` already
 * answers that case.
 */
@Composable
fun SetupRemindersStep(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory, extras = appViewModelExtras())

    SetupStep(
        step = 3,
        title = stringResource(R.string.reminders_title),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            RemindersOptIn()

            StepActions(modifier = Modifier.padding(top = Spacing.tight)) {
                PrimaryStepButton(onClick = viewModel::finish, label = stringResource(R.string.setup_finish))
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_back))
                }
            }
        }
    }
}

/**
 * The frame both steps share: where they are, what they are about, and room to scroll.
 *
 * The step counter is not decoration — it is the promise that this ends, and how soon. Three steps
 * is short enough that saying so is the difference between "answer these" and "how long is this
 * going to be".
 */
@Composable
private fun SetupStep(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    gutter: Dp = Spacing.base,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // `Spacing.block` top and bottom: the wizard has no app bar to sit under and no FAB
                // to clear, so its own breathing room is all there is. The horizontal inset defaults
                // to the app's card gutter, so the steps that host cards line them up with every
                // other card in the app rather than indenting them by a wizard-only amount.
                .padding(start = gutter, end = gutter, top = Spacing.block, bottom = Spacing.block),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        // The counter and the title are one block — where you are, and what this is — so they sit at
        // `tight` while the outer column keeps `base` between that block and the step's content.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(
                text = stringResource(R.string.setup_step, step, SETUP_STEPS),
                // Tracked out slightly, which is the whole of the difference between a position and a
                // heading. This is the only orientation on screen: no app bar, no switcher, no nav.
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.6.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            // The app's display face, and the wizard is the only place that spends it (`10e`). This
            // is the first thing anyone sees, and the one moment the product gets to have a voice.
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

/**
 * The pair of buttons every step ends with: the way on, filled, and the way back or past, quiet.
 *
 * 8dp apart, because they are two answers to one question rather than two sections.
 */
@Composable
private fun StepActions(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * A step's one filled action, at the app's primary-button size rather than M3's 40dp default.
 *
 * The same shape *Record a weighing* takes: the single action its screen exists for, standing alone
 * rather than in a row of peers.
 */
@Composable
private fun PrimaryStepButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(RecordButtonHeight),
        shape = RoundedCornerShape(RecordButtonRadius),
    ) {
        Text(label)
    }
}
