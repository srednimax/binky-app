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
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.work.ReminderChannel
import app.binky.tracker.work.ReminderDelivery
import app.binky.tracker.work.canScheduleExactAlarms
import app.binky.tracker.work.isIgnoringBatteryOptimisations
import app.binky.tracker.work.openAppNotificationSettings
import app.binky.tracker.work.openAutostartSettings
import app.binky.tracker.work.openBatteryOptimisationSettings
import app.binky.tracker.work.openChannelNotificationSettings
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
    // Same shape as `exactAlarms`: assumed good until the first read, because `care` is null until
    // then and nothing renders off it anyway.
    var exempt by remember { mutableStateOf(true) }

    LifecycleResumeEffect(doses) {
        care = context.reminderDelivery(ReminderChannel.Care)
        // Reading the doses state is also the `doses` channel's first use, which is exactly when it
        // should come into existence — an owner with no medications never sees the row in their
        // phone's notification settings.
        dose = if (doses) context.reminderDelivery(ReminderChannel.Doses) else null
        exactAlarms = context.canScheduleExactAlarms()
        exempt = context.isIgnoringBatteryOptimisations()
        onPauseOrDispose {}
    }

    // Nothing arrives at all, and this is the **point-of-use ask** (ADR-0006) rather than a caveat:
    // it explains before it requests, and it knows the difference between a refusal and Android
    // refusing to ask again. Never a second opt-in written here — Android permits two
    // `POST_NOTIFICATIONS` denials before it stops asking for good, and two separately-written asks
    // are two places to spend them from.
    val ask = care == ReminderDelivery.Blocked
    val caveat = if (ask) null else caveatFor(care, dose, exactAlarms, exempt, context)

    // Before anything has been read, and when everything is armed, this composable emits no layout
    // node — so the gap above it must be its own rather than the caller's. A Spacer emitted next to
    // a card that renders nothing is a hole at the bottom of the screen for every owner whose phone
    // is set up correctly, which is most of them.
    if (!ask && caveat == null) return

    Column(modifier = modifier.padding(top = Spacing.section)) {
        if (ask) {
            // `10h` draws the opt-in under a heading, because its first line is a paragraph about
            // what reminders are for and a block that opens mid-explanation cannot say what it is.
            // A `SectionHeader` rather than the drawing's sheet title: here it is the last section of
            // a scrolling screen, not the top of its own surface, and it sits on the same rhythm as
            // every other header on Care & Meds. No new string — the wizard's step title already
            // says "Reminders" (ADR-0013).
            SectionHeader(text = stringResource(R.string.reminders_title))
            Column(modifier = Modifier.padding(top = Spacing.tight)) {
                RemindersOptIn()
            }
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
 * 2. The `doses` channel *lowered* rather than muted, so a dose arrives with no sound (9b). Above
 *    every background limit below it because it is certain rather than probable, and because the
 *    owner may never have chosen it: HyperOS hands a channel back at `IMPORTANCE_LOW` after an
 *    off-and-on in system settings, whatever it was created at. Only the channel's own page can put
 *    it back, so that is where this one points — one screen deeper than every other notification
 *    caveat here.
 * 3. Exact alarms not permitted. Denied by default on Android 14+, so this is a state real users
 *    genuinely sit in without ever having chosen it — there is no ask to have declined and no dialog
 *    that will ever appear again.
 * 4. Battery optimisation, which delays rather than blocks, and whose fix is one readable toggle.
 * 5. The OEM autostart list, last because it is the only one of the five the app cannot read back.
 *    It is reached only once 4 is satisfied, so an owner is never shown two background-limit cards
 *    in a row — and reaching it is not the app running out of things to blame: 9a watched a 03:00
 *    dose land at 06:50 on this exact fact.
 */
private fun caveatFor(
    care: ReminderDelivery?,
    dose: ReminderDelivery?,
    exactAlarms: Boolean,
    exempt: Boolean,
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

        dose == ReminderDelivery.Silent ->
            Caveat(
                title = R.string.reminders_caveat_silent_title,
                body = R.string.doses_state_silent,
                action = R.string.reminders_open_settings_action,
                onAction = { context.openChannelNotificationSettings(ReminderChannel.Doses) },
            )

        dose == ReminderDelivery.BestEffort && !exactAlarms ->
            Caveat(
                title = R.string.reminders_caveat_exact_title,
                body = R.string.doses_state_best_effort_exact,
                action = R.string.doses_exact_alarm_action,
                onAction = { context.openExactAlarmSettings() },
            )

        dose == ReminderDelivery.BestEffort && !exempt ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.doses_state_best_effort_battery,
                action = R.string.reminders_battery_action,
                onAction = { context.openBatteryOptimisationSettings() },
            )

        // Exact alarms permitted and the exemption held, and still best-effort: the autostart list
        // is the only input left that can have put it here, so the branch needs no fourth fact.
        dose == ReminderDelivery.BestEffort ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.doses_state_best_effort_autostart,
                action = R.string.reminders_autostart_action,
                onAction = { context.openAutostartSettings() },
            )

        care == ReminderDelivery.Silent ->
            Caveat(
                title = R.string.reminders_caveat_silent_title,
                body = R.string.reminders_state_silent,
                action = R.string.reminders_open_settings_action,
                onAction = { context.openChannelNotificationSettings(ReminderChannel.Care) },
            )

        care == ReminderDelivery.BestEffort && !exempt ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.reminders_state_best_effort,
                action = R.string.reminders_battery_action,
                onAction = { context.openBatteryOptimisationSettings() },
            )

        care == ReminderDelivery.BestEffort ->
            Caveat(
                title = R.string.reminders_caveat_battery_title,
                body = R.string.reminders_state_best_effort_autostart,
                action = R.string.reminders_autostart_action,
                onAction = { context.openAutostartSettings() },
            )

        else -> null
    }
