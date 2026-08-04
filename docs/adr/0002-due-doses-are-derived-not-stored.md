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

## Derivation looks forward, never back

Deriving is equally cheap in both directions, and that is the trap. A course's times live in one row each,
so changing one changes what *every* day derives — including days already answered. A fortnight's 08:00
course moved to 20:00 on day ten re-derives the nine days behind it at a time no dose was ever recorded
against: an owner who did everything right is shown nine unanswered slots, plus nine recorded doses matching
no slot at all. The edit rewrote history it never touched.

So the derived window is clamped to `[max(start, today), end ?: ∞]`. **Today and forward is derived; the
past is what was recorded.** A past day lists its dose rows — given, skipped, ad hoc — and never a slot that
nothing answered. Giving each time an `effectiveFrom` would keep the past derivable, and is the wrong trade
for one owner and one rabbit: every schedule edit would then have to ask whether it is a correction or a
change, and get it right.

**A recorded dose is keyed by its slot's local date and time, never by an instant.** Slots resolve
wall-clock in the device's current zone (ADR-0003), so the same 08:00 dose is a different instant in Warsaw
and in London; an instant-keyed row would stop matching its own slot the moment the owner travels, and a
dose already given would read as unanswered and re-arm its alarm. `scheduledOn` and `scheduledTime` are null
together for an ad-hoc dose, and SQLite's NULLs-are-distinct rule leaves those unconstrained under
`UNIQUE(courseId, scheduledOn, scheduledTime)` while still answering each real slot exactly once. An instant
is computed only to place an alarm, and never stored.

## Consequences

Any "did I miss a dose?" question is answered by comparing the derived schedule against recorded doses
**for today**, not by looking for rows that are absent — and not at all for days already gone. That is
ADR-0001's sentence one domain over: silence means nobody looked, so the app does not draw it as a gap.

A slot stops existing at local midnight. A dose given at 00:30 for the previous evening is recorded ad hoc
and back-dated, in the app, deliberately — which is also why a dose notification is given `setTimeoutAfter`
its own day rather than sitting in the shade holding a one-tap answer the app would no longer accept
(ADR-0025).
