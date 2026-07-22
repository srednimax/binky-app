# Archiving is the normal path; deleting is an explicit, confirmed escape hatch

A rabbit that has died or been rehomed is **archived**: hidden from the rabbit selector, with every
observation, photo, document and weight kept intact. Deleting is a separate action that destroys the
rabbit and all its records via `onDelete = CASCADE`, and exists because the owner may genuinely not want
a dead rabbit's records in the app, or may simply have created a duplicate by mistake.

Deletion asks for confirmation, and when the rabbit has any records the confirmation is shown twice, the
second time stating what will actually be destroyed (photo, document, observation and weight counts).

## Consequences

Both `archivedAt` and cascading foreign keys exist deliberately — they are not redundant. Archiving must
never be implemented as a delete, and deleting must never be reachable in one tap.
