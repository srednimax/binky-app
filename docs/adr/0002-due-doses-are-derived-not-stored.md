# Due doses are derived, not stored

A medication course carries a start date, an end date, and an optional daily schedule of clock times.
The doses due today are computed from that schedule on read; only what actually happened — given or
deliberately skipped — is written to the database. Storing future dose rows would mean rewriting or
deleting them every time a course is shortened, paused, or rescheduled, and orphaned future doses are a
reliable source of wrong reminders.

A dose can also be recorded with no schedule at all, since scheduling is optional per course.

## Dose amount is free text; a course's end date is optional

The dose **amount** a course prescribes is a **free-text** field ("0.3 ml", "¼ tablet", "2 drops",
"0.2 mg/kg"). Rabbit prescriptions are too heterogeneous for a `value + unit` enum that would never be
complete, and the app never sums, converts or reasons over amounts — so structure would be false precision.
This is the mirror image of symptoms (ADR-0010): symptoms are structured *because* they are counted; dose
amounts are free text *because* they never are. A recorded dose stays minimal — given or skipped, a
timestamp, an optional note ("only got half in") — and does not re-specify the amount.

A course's **end date is nullable**, meaning an **ongoing** course the owner closes when treatment actually
stops. Forcing an end date would make owners invent a far-future one, which then derives phantom due doses.
An open course simply keeps deriving today's doses (reminders switchable off, ADR-0003), and "shortening a
course drops its future due doses" still holds — closing an open course is setting its end to today.

## Consequences

Any "did I miss a dose?" question is answered by comparing the derived schedule against recorded doses
for that window, not by looking for rows that are absent.
