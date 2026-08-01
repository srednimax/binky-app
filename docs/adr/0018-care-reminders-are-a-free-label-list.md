# A care reminder is a free-label schedule; the care-type enum only tags the known kinds

The house rule lists **care type** among the closed enum vocabularies (droppings, mood, care type). Taken
literally that would cap care reminders at a fixed set — nail trim, vaccination, weigh-in — and an owner
who wants a recurring reminder for syringe-feeding a recovering bunny, a breed-specific grooming task, or
reordering hay would have nowhere to put it. That is the same "owners have their own vocabulary" problem
ADR-0010 already solved for symptoms, and the same answer applies.

A **care reminder is `{label, interval, optional type}`:**

- The closed **`CareType` enum survives, but only tags the *known* kinds.** Its job is to let a known
  reminder map to a calendar `RRULE` (ADR-0014 — `FREQ=YEARLY` for vaccination) and carry a sensible icon.
  Preset labels are `strings.xml` keys, so they translate (ADR-0013).
- A **custom reminder is a free-text label plus an owner-chosen interval, with `type = null`.** Its label
  is literal text, untranslatable — exactly the split ADR-0010 draws between seeded and owner-added
  symptoms.

This is lighter than the symptoms table on purpose: a care reminder is a single scheduled row, not a
shared tag referenced by many records, so it needs no seeded vocabulary table — just an optional enum tag
on each row.

## Consequences

The house rule's "care type — closed vocabulary" line means the enum is closed, **not** that every care
reminder is one of a fixed set. A reminder with no `type` is normal, not a data error.

## Amendment (Phase 4b): the interval is a calendar interval, and one type carries behaviour

**"An owner-chosen interval" means `{count, unit}`, not a day count.** Stored as `intervalCount: Int` plus
`intervalUnit: CareIntervalUnit` (DAY / WEEK / MONTH / YEAR, by name). A day count makes "yearly" mean 365
days, which drifts a day off the anniversary every four years and disagrees with the `FREQ=YEARLY` this
reminder hands to the calendar (ADR-0014) — the app and the event the owner is looking at would slowly
diverge. Calendar units make the hand-off exact and let the presets stop hedging: nail trim is 6 weeks,
vaccination is yearly, weigh-in is weekly, and each is true.

The cost is `java.time`'s clamping, accepted rather than engineered around: `31 January + 1 MONTH` is 28
February, and since the next occurrence is scheduled from the recorded completion, a monthly reminder
anchored late in the month settles onto the 28th and stays there. Preserving an intended day-of-month would
mean storing an intention beside a completion history — two facts that can disagree, which is what ADR-0002
and ADR-0001 push this project away from.

**`WEIGH_IN` is no longer only a display tag.** This ADR says the enum's job is to map a known reminder to
an `RRULE` and an icon. One preset does more: a weigh-in reminder resolves its last completion as the later
of its own care events and the bunny's latest weight, read-side only, storing nothing. Without that the app
tells the owner a weigh-in is overdue while holding the weight that proves it was done — ADR-0001's
principle running in the other direction, and checkable besides. It is the only preset that names a record
the app already keeps, and Phase 5's vaccination-recorded-at-a-visit will want the same seam.
