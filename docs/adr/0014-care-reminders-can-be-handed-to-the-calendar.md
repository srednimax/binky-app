# Care reminders can be handed to the owner's calendar

A yearly vaccination reminder is where in-app scheduling is weakest. ADR-0003 already concedes that
neither mechanism fires reliably on aggressive skins without a battery-optimisation exemption, and a
WorkManager job is being asked to survive a year of reboots, an OS upgrade and possibly a new phone. The
owner's calendar is built for that horizon and syncs to their account, which makes it the only thing in
this app besides a manual export (ADR-0005) that survives losing the phone.

Any care reminder therefore offers **Add to calendar**: `Intent.ACTION_INSERT` on
`CalendarContract.Events.CONTENT_URI` with a title, an all-day begin time, and an `RRULE` matching the
reminder's repeat (`FREQ=YEARLY` for vaccination). **No calendar permission is requested** — the owner's
calendar app opens prefilled and they save it themselves.

This is additive, not a replacement. In-app reminders stay primary per ADR-0003, and the hand-off is
offered per reminder, never done automatically. It applies to care reminders only: dose reminders keep
exact alarms, because ADR-0003 gives them that path for a reason — a late dose has consequences, and a
calendar entry is not an alarm.

## Consequences

The app does **not** own the event. No event id is stored, so editing or deleting the reminder in-app
leaves the calendar entry untouched, and completing a reminder ticks nothing off the calendar. The button
must read as a one-way hand-off, not as syncing.

Tapping it twice creates two events. Record that a hand-off happened, so the button can say "Added to
calendar" instead of silently duplicating.

`ACTION_INSERT` needs a calendar app installed. Guard the `startActivity` and fail with a message, not a
crash.
