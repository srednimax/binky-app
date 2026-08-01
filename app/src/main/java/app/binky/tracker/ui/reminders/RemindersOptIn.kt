package app.binky.tracker.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.work.NotificationPermissionOutcome
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.hasAutostartSettings
import app.binky.tracker.work.isIgnoringBatteryOptimisations
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.openAutostartSettings
import app.binky.tracker.work.openBatteryOptimisationSettings
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

            ReminderDelivery.BestEffort ->
                DeliveryLine(
                    text = stringResource(R.string.reminders_state_best_effort),
                    actionLabel = stringResource(R.string.reminders_battery_action),
                    onAction = {
                        // Recorded here too: taking the fix from the delivery line is still having
                        // been asked, and the unprompted card must not reappear behind it.
                        viewModel.markBatteryExemptionAsked()
                        context.openBatteryOptimisationSettings()
                    },
                )

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
 */
@Composable
private fun BlockedState(
    permanentlyDenied: Boolean,
    deniedOnce: Boolean,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reminders_state_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (permanentlyDenied) {
            Text(
                text = stringResource(R.string.reminders_permanently_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminders_open_settings_action))
            }
        } else {
            if (deniedOnce) {
                Text(
                    text = stringResource(R.string.reminders_denied_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminders_enable_action))
            }
        }
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

@Composable
private fun BatteryExemptionCard(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.reminders_battery_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.reminders_battery_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminders_battery_action))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminders_battery_dismiss))
            }
        }
    }
}
