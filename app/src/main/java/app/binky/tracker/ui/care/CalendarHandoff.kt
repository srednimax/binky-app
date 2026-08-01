package app.binky.tracker.ui.care

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import app.binky.tracker.data.CareInterval
import app.binky.tracker.data.CareIntervalUnit
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * **Add to calendar** — a one-way hand-off, no permission, and the app does not own what it hands
 * over (ADR-0014).
 *
 * A yearly vaccination is where in-app scheduling is weakest: a WorkManager job is being asked to
 * survive a year of reboots, an OS upgrade and possibly a new phone, and ADR-0003 already concedes
 * that neither mechanism fires reliably on an aggressive skin. The owner's calendar is built for that
 * horizon and syncs to their account.
 *
 * What "does not own" means concretely: no event id is stored, so editing the reminder here changes
 * nothing out there and completing it ticks nothing off. `calendarHandedOffAt` records only that the
 * hand-off happened, so the button can read "Added to calendar" instead of silently minting a second
 * event on a second tap.
 */

/**
 * The repeat rule, as iCalendar spells it.
 *
 * **`INTERVAL=1` is omitted**, which is not cosmetic: ADR-0014 names `FREQ=YEARLY` as what a yearly
 * vaccination hands over, and that is exactly what a calendar app shows back as "Annually" rather
 * than "Every 1 year".
 *
 * The mapping is one-to-one because the interval is a *calendar* interval and not a day count — the
 * reason 4b chose `{count, unit}` in the first place. A 42-day nail trim would have to be
 * approximated here; six weeks does not.
 */
fun careRrule(interval: CareInterval): String {
    val frequency =
        when (interval.unit) {
            CareIntervalUnit.DAY -> "DAILY"
            CareIntervalUnit.WEEK -> "WEEKLY"
            CareIntervalUnit.MONTH -> "MONTHLY"
            CareIntervalUnit.YEAR -> "YEARLY"
        }
    return if (interval.count == 1) "FREQ=$frequency" else "FREQ=$frequency;INTERVAL=${interval.count}"
}

/**
 * The start of an all-day event, in the milliseconds `CalendarContract` wants.
 *
 * **Midnight UTC, not midnight here.** The contract defines an all-day event's times that way, and
 * handing over a local midnight is what makes an event land on the day before in a western timezone.
 * The same convention the date pickers in this app already use for a bare `LocalDate`.
 */
fun careCalendarBeginMillis(dueOn: LocalDate): Long = dueOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Opens the owner's calendar, prefilled. Returns false if there is nothing to open it with.
 *
 * **No calendar permission is requested and none is needed** — `ACTION_INSERT` opens the calendar
 * app's own editor and the owner saves it themselves, which is the whole reason ADR-0014 prefers this
 * to writing the event directly.
 *
 * Guarded rather than trusted: a phone with no calendar app installed is unusual but entirely legal,
 * and ADR-0014 asks for a message rather than a crash. The caller reports the false.
 */
fun Context.addCareToCalendar(
    title: String,
    dueOn: LocalDate,
    interval: CareInterval,
): Boolean {
    val begin = careCalendarBeginMillis(dueOn)
    val intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            // One whole day: an all-day event ends at the start of the next one.
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + MILLIS_IN_DAY)
            .putExtra(CalendarContract.Events.RRULE, careRrule(interval))
            // Launched from a screen, but the receiver is another app's task.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

private const val MILLIS_IN_DAY = 24L * 60 * 60 * 1000
