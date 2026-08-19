package app.binky.tracker.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.CardRadius
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.work.NotificationPermissionOutcome
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.hasAutostartSettings
import app.binky.tracker.work.isIgnoringBatteryOptimisations
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.openAutostartSettings
import app.binky.tracker.work.openBatteryOptimisationSettings
import app.binky.tracker.work.openChannelNotificationSettings
import app.binky.tracker.work.rememberNotificationPermissionAsk
import app.binky.tracker.work.reminderDelivery

/**
 * **One composable in two hosts** (ADR-0006, PLAN 4a): first-run setup's third step and the
 * point-of-use sheet are the same screen, not two asks that could both fire.
 *
 * That matters more than it looks. Android permits two `POST_NOTIFICATIONS` denials before the
 * dialog stops appearing for good, and two separately-written opt-ins are two places to spend them
 * from. Note who actually sees which: every install that exists today has already finished setup, so
 * for 1.1 the point-of-use path is not the fallback — it is the only path anyone takes.
 *
 * The screen explains what reminders are for **before** anything is requested, which is the whole of
 * ADR-0006's objection to a bare system dialog on launch. It then reports honestly what will
 * actually happen (ADR-0003): blocked, best-effort or armed, each with the fix that state deserves.
 *
 * On a phone with an OEM autostart list, *armed* is unreachable on purpose (9a), so best-effort is
 * what this screen settles on and the autostart way-in is what it settles on offering.
 */
@Composable
fun RemindersOptIn(modifier: Modifier = Modifier) {
    val viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Every fact on this screen belongs to Android, and the owner can change any of them by walking
    // into system settings and back. Re-read on each resume rather than remembered: coming back from
    // the battery screen having granted the exemption has to redraw as "armed", or the app is
    // reporting a state that stopped being true while it was in the background.
    var delivery by remember { mutableStateOf<ReminderDelivery?>(null) }
    var exempt by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        delivery = context.reminderDelivery(ReminderChannel.Care)
        exempt = context.isIgnoringBatteryOptimisations()
        onPauseOrDispose {}
    }

    // Null until the owner has been asked in this composition. It is the only way to tell an
    // ordinary refusal from Android refusing to ask again, and it is deliberately not persisted:
    // the question is about the dialog's availability now, not about history.
    var outcome by remember { mutableStateOf<NotificationPermissionOutcome?>(null) }
    val ask =
        rememberNotificationPermissionAsk { result ->
            outcome = result
            delivery = context.reminderDelivery(ReminderChannel.Care)
        }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.reminders_body), style = MaterialTheme.typography.bodyMedium)

        when (delivery) {
            null -> Unit
            ReminderDelivery.Blocked ->
                BlockedState(
                    permanentlyDenied = outcome == NotificationPermissionOutcome.PermanentlyDenied,
                    deniedOnce = outcome == NotificationPermissionOutcome.Denied,
                    onAsk = ask,
                    onOpenSettings = { context.openAppNotificationSettings() },
                )

            // Nothing to ask for and nothing to grant: the level is the owner's, and Android will
            // not let the app raise it back. So this line does the one thing left — say what will
            // happen, and open the page where it can be undone by hand.
            ReminderDelivery.Silent ->
                DeliveryLine(
                    text = stringResource(R.string.reminders_state_silent),
                    actionLabel = stringResource(R.string.reminders_open_settings_action),
                    onAction = { context.openChannelNotificationSettings(ReminderChannel.Care) },
                )

            // Two reasons to be here, ranked the way the resolver ranks them: the exemption first,
            // because its state is readable and its fix is one toggle, and the OEM autostart list
            // only once that is out of the way. Holding the exemption is therefore what *reveals*
            // the autostart line — which is the hole 9a found, since the block further down this
            // screen stops rendering at the same moment.
            ReminderDelivery.BestEffort ->
                if (exempt) {
                    DeliveryLine(
                        text = stringResource(R.string.reminders_state_best_effort_autostart),
                        actionLabel = stringResource(R.string.reminders_autostart_action),
                        onAction = { context.openAutostartSettings() },
                    )
                } else {
                    DeliveryLine(
                        text = stringResource(R.string.reminders_state_best_effort),
                        actionLabel = stringResource(R.string.reminders_battery_action),
                        onAction = {
                            // Recorded here too: taking the fix from the delivery line is still
                            // having been asked, and the unprompted card must not reappear behind it.
                            viewModel.markBatteryExemptionAsked()
                            context.openBatteryOptimisationSettings()
                        },
                    )
                }

            ReminderDelivery.Armed ->
                DeliveryLine(text = stringResource(R.string.reminders_state_armed), actionLabel = null, onAction = {})
        }

        // **Asked once, at the point something is first scheduled, where the reason is visible**
        // (ADR-0006, ADR-0003's amendment). The moment is exact: the owner has just turned reminders
        // on, so this is the app saying what stands between that and one arriving. If it is declined
        // it never auto-appears again — the fix stays on the best-effort line above, which is where
        // an owner who changes their mind will look.
        val offerExemption = delivery != null && delivery != ReminderDelivery.Blocked && !exempt
        if (offerExemption && !state.batteryExemptionAsked) {
            BatteryExemptionCard(
                onAllow = {
                    viewModel.markBatteryExemptionAsked()
                    context.openBatteryOptimisationSettings()
                },
                onDismiss = viewModel::markBatteryExemptionAsked,
            )
        }

        // Offered beside the exemption and **claimed never**: HyperOS exposes no readable state for
        // autostart, so the app cannot say afterwards whether it is on, and asking the owner to
        // confirm would have it repeating their guess back as its own assurance (ADR-0003).
        if (offerExemption && context.hasAutostartSettings()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.reminders_autostart_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { context.openAutostartSettings() }) {
                    Text(stringResource(R.string.reminders_autostart_action))
                }
            }
        }
    }
}

/**
 * Nothing will arrive, and the app is certain of it — so the copy says exactly that, and the button
 * depends on whether Android will still show its dialog.
 *
 * A blocked state does **not** stop reminders being created. The Care screen carries overdue state on
 * its own, so the reminder is still worth having; it just must not claim it will notify.
 *
 * **It takes [AndroidStateCard], which the device pass is what found.** `10g` draws the wizard's third
 * step *armed*, so it never arranged the state a genuine first run is actually in: a fresh install has
 * no notification permission, and this ask rendered as a bare filled button directly above *Finish
 * setup* — two filled buttons on one surface, which is the exact rule `10g` exists to answer. Its
 * answer is containment rather than demotion, so the ask gets a surface of its own and its button
 * becomes the filled button *of that surface*. The two asks are the same class of thing anyway: an
 * Android state that stops delivery, and the one screen that changes it.
 *
 * `10h` draws this state uncarded, and that is the one place the drawing is not followed — it draws
 * the sheet, where the ask is the only filled button on screen and a card buys nothing. One
 * composable serves both hosts (ADR-0006's arithmetic about denials), so the containment has to hold
 * in the host where it is load-bearing.
 */
@Composable
private fun BlockedState(
    permanentlyDenied: Boolean,
    deniedOnce: Boolean,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AndroidStateCard {
        Text(
            text = stringResource(R.string.reminders_state_blocked),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (permanentlyDenied) {
            Text(
                text = stringResource(R.string.reminders_permanently_denied),
                style = MaterialTheme.typography.bodySmall,
            )
            CardPrimaryButton(
                onClick = onOpenSettings,
                label = stringResource(R.string.reminders_open_settings_action),
            )
        } else {
            if (deniedOnce) {
                Text(
                    text = stringResource(R.string.reminders_denied_once),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            CardPrimaryButton(onClick = onAsk, label = stringResource(R.string.reminders_enable_action))
        }
    }
}

/**
 * The surface the two Android-state asks share: apricot, and containing its own filled button.
 *
 * Apricot is `Color.kt`'s caution role, and caution is exactly the register — nothing is broken and
 * nothing the owner did is wrong. `onTertiaryContainer` carries every glyph on it, because that
 * pairing is contrast-checked by construction where a stray `onSurfaceVariant` on it is not; the
 * quieting these lines used to do with colour is done by the card itself now.
 */
@Composable
private fun AndroidStateCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            content = content,
        )
    }
}

/** A card's own filled action, at the app's primary-button size. */
@Composable
private fun CardPrimaryButton(
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

/** One honest sentence about what will happen, and the fix if there is one to offer. */
@Composable
private fun DeliveryLine(
    text: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The one thing standing between an armed reminder and one that actually arrives — **apricot, and
 * containing its own filled button** (`10g`).
 *
 * Two filled buttons on one screen breaks the app's one-filled-button rule, and setup's third step
 * would have exactly that: this ask and *Finish setup*. The fix is containment rather than demotion.
 * The card is a surface of its own, so its button is the filled button *of the card* while Finish is
 * the filled button of the screen — the same nesting the trend flag uses inside a bunny card. A
 * demoted ask would be the wrong answer, because this is the difference between reminders working
 * and reminders silently not.
 *
 * Apricot is `Color.kt`'s caution role, and this is caution rather than alarm: nothing is broken and
 * nothing the owner did is wrong. `onTertiaryContainer` carries the text *and* the dismissal, because
 * that pairing is contrast-checked by construction where a stray `primary` on apricot is not.
 */
@Composable
private fun BatteryExemptionCard(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AndroidStateCard {
        Text(
            text = stringResource(R.string.reminders_battery_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.reminders_battery_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        // The pair sits `hair` apart and `base` below the paragraph, so the two buttons read as
        // one choice rather than two more items in the card's list.
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
            modifier = Modifier.padding(top = Spacing.tight),
        ) {
            CardPrimaryButton(
                onClick = onAllow,
                label = stringResource(R.string.reminders_battery_action),
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            ) {
                Text(stringResource(R.string.reminders_battery_dismiss))
            }
        }
    }
}
