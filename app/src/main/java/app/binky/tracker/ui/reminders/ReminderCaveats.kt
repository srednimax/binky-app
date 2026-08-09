package app.binky.tracker.ui.reminders

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.CaveatCard
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.canScheduleExactAlarms
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.openBatteryOptimisationSettings
import app.binky.tracker.work.openExactAlarmSettings
import app.binky.tracker.work.reminderDelivery

/**
 * What will actually happen when something on this route comes due — **one card, at the bottom**
 * (ADR-0003).
 *
 * Phase 7 moved it there and gave it the caution marker. It used to be a sentence at the *top* of
 * Care & Meds, above the doses, which put a caveat about Android in front of the thing an owner
 * opened the tab to do at eight in the morning. It is a footnote about the app, so it reads as one.
 *
 * **Exactly one caveat renders, never two**, and the order below is what decides which. The states
 * are not independent — notifications off makes both channels silent, and one battery policy delays
 * both — so stacking every true statement would show the same fix twice under two titles. They are
 * ranked by how much they cost, and taking the fix reveals the next one on resume, because every
 * fact here is re-read then rather than remembered. An owner can walk into system settings and back
 * at any moment, and a card still describing the state they just left would be the app reporting
 * something that stopped being true while it was in the background.
 *
 * **The armed state renders nothing at all.** A line confirming that a working app works is the kind
 * of reassurance an owner learns to skip, and then skips the one that matters.
 *
 * [doses] is whether this route has any dose reminders armed at all. A bunny whose only course has
 * no times has nothing to deliver, and a warning about how reliably Android wakes the app is noise
 * to an owner who has scheduled nothing to wake it for.
 */
@Composable
fun ReminderCaveats(
    doses: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var care by remember { mutableStateOf<ReminderDelivery?>(null) }
    var dose by remember { mutableStateOf<ReminderDelivery?>(null) }
    var exactAlarms by remember { mutableStateOf(true) }

    LifecycleResumeEffect(doses) {
        care = context.reminderDelivery(ReminderChannel.Care)
        // Reading the doses state is also the `doses` channel's first use, which is exactly when it
        // should come into existence — an owner with no medications never sees the row in their
        // phone's notification settings.
        dose = if (doses) context.reminderDelivery(ReminderChannel.Doses) else null
        exactAlarms = context.canScheduleExactAlarms()
        onPauseOrDispose {}
    }

    // Nothing arrives at all, and this is the **point-of-use ask** (ADR-0006) rather than a caveat:
    // it explains before it requests, and it knows the difference between a refusal and Android
    // refusing to ask again. Never a second opt-in written here — Android permits two
    // `POST_NOTIFICATIONS` denials before it stops asking for good, and two separately-written asks
    // are two places to spend them from.
    val ask = care == ReminderDelivery.Blocked
    val caveat = if (ask) null else caveatFor(care, dose, exactAlarms, context)

    // Before anything has been read, and when everything is armed, this composable emits no layout
    // node — so the gap above it must be its own rather than the caller's. A Spacer emitted next to
    // a card that renders nothing is a hole at the bottom of the screen for every owner whose phone
    // is set up correctly, which is most of them.
    if (!ask && caveat == null) return

    Column(modifier = modifier.padding(top = Spacing.section)) {
        if (ask) {
            RemindersOptIn()
        } else if (caveat != null) {
            CaveatCard(
                title = stringResource(caveat.title),
                body = stringResource(caveat.body),
                action = {
                    TextButton(onClick = caveat.onAction) { Text(stringResource(caveat.action)) }
                },
            )
        }
    }
}

/** One caveat: what is wrong, said plainly, and the one screen that fixes it. */
private data class Caveat(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val action: Int,
    val onAction: () -> Unit,
)

/**
 * The ranking, worst first. Not `@Composable` on purpose: it decides *which* caveat, and resolving
 * the strings is the caller's job — that keeps the order readable as the list of rules it is.
 *
 * 1. The `doses` channel muted on its own. The app-wide case is the opt-in above; this is the owner
 *    having switched off one category in system settings, which nothing in the app can ask back.
 * 2. Exact alarms not permitted. Denied by default on Android 14+, so this is a state real users
 *    genuinely sit in without ever having chosen it — there is no ask to have declined and no dialog
 *    that will ever appear again.
 * 3. Battery optimisation, which delays rather than blocks, and whose fix is an OEM screen
 *    underneath the app's own permission.
 */
private fun caveatFor(
    care: ReminderDelivery?,
    dose: ReminderDelivery?,
    exactAlarms: Boolean,
    context: Context,
): Caveat? =
    when {
        care == null -> null

        dose == ReminderDelivery.Blocked ->
            Caveat(
                title = R.string.reminders_caveat_notifications_title,
                body = R.string.doses_state_blocked,
                action = R.string.reminders_open_settings_action,
                onAction = { context.openAppNotificationSettings() },
            )

        dose == ReminderDelivery.BestEffort && !exactAlarms ->
            Caveat(
                title = R.string.reminders_caveat_exact_title,
                body = R.string.doses_state_best_effort_exact,
                action = R.string.doses_exact_alarm_action,
                onAction = { context.openExactAlarmSettings() },
            )

        dose == ReminderDelivery.BestEffort ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.doses_state_best_effort_battery,
                action = R.string.reminders_battery_action,
                onAction = { context.openBatteryOptimisationSettings() },
            )

        care == ReminderDelivery.BestEffort ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.reminders_state_best_effort,
                action = R.string.reminders_battery_action,
                onAction = { context.openBatteryOptimisationSettings() },
            )

        else -> null
    }
