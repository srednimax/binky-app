# Archiving is the normal path; deleting is an explicit, confirmed escape hatch

A bunny that has died or been rehomed is **archived**: hidden from the bunny selector, with every
observation, photo, document and weight kept intact. Deleting is a separate action that destroys the
bunny and all its records via `onDelete = CASCADE`, and exists because the owner may genuinely not want
a dead bunny's records in the app, or may simply have created a duplicate by mistake.

Deletion asks for confirmation, and when the bunny has any records the confirmation is shown twice, the
second time stating what will actually be destroyed (photo, document, observation and weight counts).

## Consequences

Both `archivedAt` and cascading foreign keys exist deliberately — they are not redundant. Archiving must
never be implemented as a delete, and deleting must never be reachable in one tap.

Deletion interacts with **shared observations** (ADR-0008), which are stored one row per bunny. Deleting a
bunny cascades only *its own* rows, so a co-observed bunny's rows survive on their own foreign key — but
the confirmation only stays honest if it counts **two buckets, not one**: observations *solely* this
bunny's (destroyed) versus *shared* observations it merely took part in (the bunny leaves them; the event
survives for the others). Lumping the two together overstates the loss and hides a side effect on a
*different* bunny — the exact dishonesty this confirmation exists to prevent.
