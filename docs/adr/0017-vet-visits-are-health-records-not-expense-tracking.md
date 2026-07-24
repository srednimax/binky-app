# Vet visits are health records, not an expense tracker

A `Visit` links to a bunny and optionally to a vet, and carries a date, a reason, notes, an optional
origin-tagged weight (stored as one weight row with `source = visit`, per Phase 5 — never a second copy of
the number), and attached documents. It deliberately has **no cost field.**

Rabbit vet bills are real money and some owners would use a cost field, so the omission is a choice, not an
oversight. `CONTEXT.md` frames this app as a trustworthy record of **health**, kept so an owner and their
vet can see changes over time. What a visit cost is not a health signal — no vet reads it, and no chart in
this app is about money. And a cost field is never just a field: it is the first step toward totals,
categories, per-bunny spend and budgets — a second, different app grafted onto this one, which is exactly
the scope creep ADR-0012's "function first" posture exists to resist.

An owner who wants to remember a figure can put it in the free-text notes — untyped, uncounted, and
deliberately not a first-class number.

## Consequences

Promoting cost to a real field later is easy if expense tracking ever becomes an actual goal; walking back
an app that quietly became a budgeting tool is not. Until then, the app stays a health record.
