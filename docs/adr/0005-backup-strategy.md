# Backup: automatic for the database, manual and scoped for media

All data lives on the device, so losing the phone means losing the bunny's history unless the owner acts.
The app therefore backs up on two levels, and neither requires a server or any running cost — exports go
to storage the owner already owns.

**Android Auto Backup** is enabled but restricted to the database and preferences. Media is excluded: the
per-app quota is small, and backing up the photo gallery would silently exhaust it, leaving the owner
believing they had a backup when they had none. This content is fixed at build time and is not
user-configurable.

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
