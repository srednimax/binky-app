# Due doses are derived, not stored

A medication course carries a start date, an end date, and an optional daily schedule of clock times.
The doses due today are computed from that schedule on read; only what actually happened — given or
deliberately skipped — is written to the database. Storing future dose rows would mean rewriting or
deleting them every time a course is shortened, paused, or rescheduled, and orphaned future doses are a
reliable source of wrong reminders.

A dose can also be recorded with no schedule at all, since scheduling is optional per course.

## Consequences

Any "did I miss a dose?" question is answered by comparing the derived schedule against recorded doses
for that window, not by looking for rows that are absent.
