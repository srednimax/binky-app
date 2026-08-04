# Vet visits are health records, not an expense tracker

A `Visit` links to a bunny and optionally to a vet, and carries a date, a reason, notes, an optional
origin-tagged weight (stored as one weight row carrying the visit's id, per Phase 5 — never a second copy of
the number), and attached documents. It deliberately has **no cost field.**

Rabbit vet bills are real money and some owners would use a cost field, so the omission is a choice, not an
oversight. `CONTEXT.md` frames this app as a trustworthy record of **health**, kept so an owner and their
vet can see changes over time. What a visit cost is not a health signal — no vet reads it, and no chart in
this app is about money. And a cost field is never just a field: it is the first step toward totals,
categories, per-bunny spend and budgets — a second, different app grafted onto this one, which is exactly
the scope creep ADR-0012's "function first" posture exists to resist.

An owner who wants to remember a figure can put it in the free-text notes — untyped, uncounted, and
deliberately not a first-class number.

## `visitId` is the only stored origin fact

A weighing recorded at a visit is **one** row in `weights` carrying `visitId`, and `WeightSource` is
**derived** from it — `if (visitId != null) Visit else Manual`. A stored `source` column beside the id would
be two facts that can disagree, which is the pattern already refused for a care reminder's intended
day-of-month. `ON DELETE SET NULL` then makes "deleting the visit leaves the weighing standing" true by
construction, rather than true because a repository remembered to clear a second field.

The index on `visitId` is **unique**. NULLs are distinct in SQLite, so every manual weighing stays
unconstrained while a visit can never acquire a second number — which makes "one row, never a copy" a
property of the schema instead of a property of the editor being careful. The chart plots a visit weighing
identically to any other; a weight is a weight, and where it was taken is not a different kind of truth.

## A vet outlives its visits

`Vet` is app-wide, with no bunny foreign key: a household's bunnies see the same vet, and a directory per
bunny would make the owner type the clinic in twice. `visits.vetId` is `ON DELETE SET NULL`, so a clinic
closing — or an owner tidying a duplicate — leaves the visit standing with no vet named. Losing a health
record is not an acceptable side effect of editing a directory.

For the same reason vets are **not** counted in a bunny's destroyed bucket (ADR-0004): visits are
sole-owned and go with their bunny, but deleting a bunny must not take a phone number with it.

## Consequences

Promoting cost to a real field later is easy if expense tracking ever becomes an actual goal; walking back
an app that quietly became a budgeting tool is not. Until then, the app stays a health record.
