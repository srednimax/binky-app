# Symptoms are a seeded table, not an enum

Droppings, mood and care type are closed vocabularies and stay Kotlin enums. Symptoms are not: bunnies
develop quirks no built-in list anticipates, and the ones an owner bothers to write down are usually the
recurring ones. A symptom that can only be typed into a note can never answer "how often has this
happened?", which is the reason symptoms are a field at all rather than prose.

Symptoms therefore live in a table, seeded on first run with the built-in list — head tilt, drooling or wet
chin, sneezing or nasal discharge, eye discharge, dirty bottom, loud teeth grinding, hunched posture,
laboured breathing, not drinking, limping, ear scratching, blood in urine, hiding more than usual — with
owner-added rows alongside, indistinguishable in use.

*Loud* teeth grinding is named deliberately: soft tooth purring means a content bunny and loud grinding
means pain, so an unqualified "teeth grinding" would invert the meaning.

## Consequences

Built-in symptoms carry a **stable key**, not an English display string, and are rendered through
`strings.xml` so they translate like all other UI text (ADR-0013). Owner-added symptoms store their
**literal text**; those are untranslatable, as expected. There is **no `ownerCreated` flag** — this ADR
originally called for one, and it is dropped: `key == null` already says it, since a built-in has a key and no
stored label while an owner row has a label and no key, and a second column could only drift out of step with
the first. Both label columns do earn their place, for opposite reasons — built-in labels **must not** be
stored, so they stay translatable, and owner labels **must** be. An
observation references a symptom's **stable id**, and the "how often has this happened?" count keys off
that id — never the display text — so a symptom can be renamed or translated without orphaning its
history. (The English strings the seed list uses above are the *labels* behind those keys, not the stored
identity.)

Removing a symptom hides it from the picker and never deletes it from historical observations.

A symptom attaches to an observation as a **binary tick** — present at that noticed moment — and nothing
more. **Severity is carried by the symptom's identity, not a field:** "*loud* teeth grinding" is already a
distinct symptom from soft tooth-purring, so grading is chosen by picking the right symptom. There is no
duration or "resolved" state: an observation is a snapshot ("noticed at a specific moment", CONTEXT.md), and
a persistent symptom is expressed by re-ticking it on later observations — each tick honestly "one time I
noticed it", which is exactly what the count claims to be. The accepted limitation is that a chronic symptom
logged once undercounts its persistence; ADR-0007 leaves room to add a stateful model later if that ever
proves necessary.

The free-text note on an observation remains, for one-off detail that is not a symptom.

## Seeding, and keeping the list free of duplicates

The built-in set is seeded on create and **reconciled on open** with an `INSERT OR IGNORE` keyed on `key`, so
the list in code stays identical to the list in the database once wipes stop being free after Phase 3. That
requires a **unique index on `key`** to have any conflict to ignore — without it the reconciliation inserts
the entire built-in list on **every launch**. The index is safe on a nullable column because SQLite treats
NULLs as distinct: it enforces uniqueness across built-ins while permitting unlimited owner rows. Matching on
`key` leaves a hidden symptom's `hiddenAt` untouched; built-ins are retired by hiding, never by deleting.

Nothing in the schema can stop an owner adding a symptom whose label duplicates a built-in's, because built-in
labels are deliberately not stored. So the check is made **once, in the application, at add time**: trim,
compare case-insensitively against existing owner labels *and* the built-in labels as currently resolved, and
on a match select the existing symptom rather than creating a row. A match on a **hidden** symptom unhides it —
an owner typing in a symptom they previously retired is asking for it back, and the alternative is a duplicate
shadow of a symptom that already exists. No index on `label`: it would need a collation Room's `@Index` cannot
express and still could not catch the built-in case, and two mechanisms where only one covers the hard case is
worse than one that covers both. Accepted limitation: the check reads labels resolved in the *current* locale,
so a later language switch can surface a duplicate-looking pair.

## Zero ticks is ambiguous, and one column resolves it

An observation with no symptom links means either *"I looked, nothing wrong"* or *"I never opened the
picker"* — indistinguishable, which is ADR-0001's silence failure applied to the one field this ADR exists to
make queryable. It also makes the one-tap healthy day's central claim unrepresentable, since that shortcut
records *no symptoms* affirmatively.

`Observation.symptomsChecked` is therefore a **non-nullable** boolean. Any link implies `true`, enforced in
the repository rather than merely expected; the healthy day sets it `true` with no links; the full form sets
it from an explicit *"none seen"* tick, mutually exclusive with having selections. Non-nullable deliberately —
a `Boolean?` would make `null` and `false` two spellings of "didn't look", which is precisely why the graded
vocabularies use `null` for absence and this does not. It is not a second spelling of "count of links > 0"
either: it carries the one state the join table cannot express. Nor is it a sentinel "no symptoms" row in the
symptom table, which would make *none* a member of the vocabulary it negates and force every query to
special-case it forever.
