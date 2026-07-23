# Backup: automatic for the database, manual and scoped for media

All data lives on the device, so losing the phone means losing the bunny's history unless the owner acts.
The app therefore backs up on two levels, and neither requires a server or any running cost — exports go
to storage the owner already owns.

**Android Auto Backup** is enabled and covers the database, preferences, **avatars, and scanned
documents** — the evidential core. The **photo gallery is excluded**: the per-app quota (~25 MB) is small,
and backing up photos would silently exhaust it, leaving the owner believing they had a backup when they
had none. Documents are kept *in* precisely because they are evidence the owner may need again — a record,
not a sentimental memory — and are individually small; but they share the same quota, so a **size guard**
stops including them once the backup set approaches the limit, and that must **surface honestly** rather
than silently dropping documents while the owner believes they are covered. This content is fixed at build
time and is not user-configurable.

**Manual export** writes a zip to a destination the owner picks, at one of three scopes:

- **Essential** — database and bunny avatars.
- **Records** *(default)* — Essential plus scanned documents; everything the owner may need again.
- **Everything** — Records plus the photo gallery; large and occasional.

The export shares out through the system share sheet first (which cannot fail for provider reasons), with
a remembered folder destination added afterwards, once writing to that provider has been verified on a
real device. If the folder is a cloud provider's, the backup lands in the owner's own cloud.

The scope is chosen during first-run setup with a plain explanation of the trade-off — not hidden in
settings — and can be changed in settings later. The scope is recorded in the export filename so a
restore can state what the file actually contains.

## Consequences

A restore may legitimately arrive without media, and the app must show missing images as a placeholder
rather than failing. SQLite `-wal`/`-shm` files must be excluded from Auto Backup or checkpointed, or a
live database will be captured mid-write and restore corrupt.

Because the evidential core (database, avatars, documents) is now covered by Auto Backup, the
manual-export folder destination — the plan's biggest unverified assumption (Google Drive provider
writes) — gates only the **sentimental photo gallery**, not vet evidence. That lowers the cost of that
assumption failing. Documents appearing in *both* Auto Backup and the manual Records/Everything scopes is
deliberate, not redundant: Auto Backup is the effortless net, manual export is the portable copy the owner
controls.
