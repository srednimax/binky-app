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
