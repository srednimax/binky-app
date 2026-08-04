package app.binky.tracker.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import app.binky.tracker.R
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.canScheduleExactAlarms
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.openBatteryOptimisationSettings
import app.binky.tracker.work.openExactAlarmSettings
import app.binky.tracker.work.reminderDelivery

/**
 * What will actually happen to a dose reminder, in one sentence — **and the sentence is tappable**.
 *
 * The state is on screen anyway (5d puts it on every course row), so the alternative to a tap target
 * is dead text describing a problem next to no way to fix it. That is not a second ask: ADR-0006
 * still gets exactly one, at the point a course first schedules something. This is the label
 * refusing to be inert, and it earns its place because revoking `SCHEDULE_EXACT_ALARM` on Android
 * 14+ drops the owner into best-effort **without their ever having chosen it** — there is no ask to
 * have declined and no dialog that will ever appear again.
 *
 * Three states, but four sentences: [ReminderDelivery.BestEffort] arrives for two different reasons
 * with two different fixes, and which one is missing decides both the copy and where the button
 * goes. Exact alarms first, because that fix is a single system toggle and the app's own permission,
 * where the battery one is an OEM screen underneath it.
 *
 * Every fact here belongs to Android and the owner can change any of them by walking into settings
 * and back, so all of it is re-read on resume rather than remembered — coming back having granted
 * exact alarms has to redraw as armed, or the app is reporting a state that stopped being true while
 * it was in the background.
 */
@Composable
fun DoseDeliveryLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var delivery by remember { mutableStateOf<ReminderDelivery?>(null) }
    var exactAlarms by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        // Reading the state is also the `doses` channel's first use, which is exactly when it should
        // come into existence — an owner with no medications never sees the row in their settings.
        delivery = context.reminderDelivery(ReminderChannel.Doses)
        exactAlarms = context.canScheduleExactAlarms()
        onPauseOrDispose {}
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        when (delivery) {
            null -> Unit

            ReminderDelivery.Blocked ->
                DoseState(
                    text = stringResource(R.string.doses_state_blocked),
                    actionLabel = stringResource(R.string.reminders_open_settings_action),
                    onAction = { context.openAppNotificationSettings() },
                )

            ReminderDelivery.BestEffort ->
                if (!exactAlarms) {
                    DoseState(
                        text = stringResource(R.string.doses_state_best_effort_exact),
                        actionLabel = stringResource(R.string.doses_exact_alarm_action),
                        onAction = { context.openExactAlarmSettings() },
                    )
                } else {
                    DoseState(
                        text = stringResource(R.string.doses_state_best_effort_battery),
                        actionLabel = stringResource(R.string.reminders_battery_action),
                        onAction = { context.openBatteryOptimisationSettings() },
                    )
                }

            ReminderDelivery.Armed ->
                DoseState(
                    text = stringResource(R.string.doses_state_armed),
                    actionLabel = null,
                    onAction = {},
                )
        }
    }
}

/** One honest sentence about what will happen, and the fix if there is one to offer. */
@Composable
private fun DoseState(
    text: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (actionLabel != null) {
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}
