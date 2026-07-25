# Archiving is the normal path; deleting is an explicit, confirmed escape hatch

A bunny that has died or been rehomed is **archived**: hidden from the bunny selector, with every
observation, photo, document and weight kept intact. Deleting is a separate action that destroys the
bunny and all its records via `onDelete = CASCADE`, and exists because the owner may genuinely not want
a dead bunny's records in the app, or may simply have created a duplicate by mistake.

Deletion asks for confirmation, and when the bunny has any records the confirmation is shown twice, the
second time stating what will actually be destroyed (photo, document, observation and weight counts).

**Records** means weights, observations, photos, documents, visits and doses — the history that cannot be
reconstructed from memory (ADR-0007). An **avatar and the profile fields do not count.** The avatar is a
photograph the owner still has in their camera roll, and making it trip the same alarm as a year of
weighings teaches the owner to click through both dialogs before reading them — the habit that costs them on
the deletion that is genuinely irreversible. So deleting the empty duplicate this ADR names as a legitimate
case stays a single confirmation, and through Phase 1, where no record type exists yet, every deletion does.

**Archiving asks once**, stating plainly that the records are kept and the bunny can be brought back. It
destroys nothing, so it does not warrant deletion's ceremony — but it removes a bunny from the switcher, and
an owner who has not yet met the archive/delete distinction reads that as loss. One dialog is the cheapest
place to teach the distinction this whole ADR rests on. **Unarchiving asks nothing**; it only ever restores.

Archived bunnies stay **reachable**: a list under More offering unarchive and delete, with their records
readable in a deliberate **read-only scope** (ADR-0015). Records nobody can reach are indistinguishable from
deleted ones, which would hollow out the "every record kept" claim above.

That scope has **three clauses**, stated once here so no screen has to re-decide them: records are
**readable**, nothing is **writable**, and **no signal that calls for action is shown**. The third is the one
that needs saying, and it is not obvious until there are records to show — through Phase 1 the scope was
trivially satisfied by having nothing in it. A trend flag on a bunny that has died is grotesque and its
acknowledge action would write inside a read-only scope (ADR-0001), so it is not evaluated there at all
rather than hidden at the composable. The clause also pre-answers the later phases for free: no watch offer,
no reminder, no dose entry on an archived bunny.

## Consequences

Both `archivedAt` and cascading foreign keys exist deliberately — they are not redundant. Archiving must
never be implemented as a delete, and deleting must never be reachable in one tap.

Deletion interacts with **shared observations** (ADR-0008), which are stored one row per bunny. Deleting a
bunny cascades only *its own* rows, so a co-observed bunny's rows survive on their own foreign key — but
the confirmation only stays honest if it counts **two buckets, not one**: observations *solely* this
bunny's (destroyed) versus *shared* observations it merely took part in (the bunny leaves them; the event
survives for the others). Lumping the two together overstates the loss and hides a side effect on a
*different* bunny — the exact dishonesty this confirmation exists to prevent.

The buckets are counted by **survivorship, not provenance**. A shared observation where this bunny is the
*last remaining participant* is destroyed outright, so it belongs in the **sole-owned** bucket. Counting it as
"shared" would have the second dialog reassure the owner that the record survives for the other bunnies at
the exact moment the loss is total — and stating what is actually destroyed is this dialog's entire
justification. Archived bunnies count as survivors: archiving is not deleting and their rows persist.

Two different questions therefore get two different predicates, deliberately, rather than one column doing
both jobs badly: *"was this observed together?"* is history, immutable, answered by the group id (ADR-0008),
while *"will anything be left of it?"* is present tense, answered by an `EXISTS` over rows belonging to
another bunny.
