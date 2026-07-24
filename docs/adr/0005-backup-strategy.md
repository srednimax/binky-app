# Backup: automatic for the database, manual and scoped for media

All data lives on the device, so losing the phone means losing the bunny's history unless the owner acts.
The app therefore backs up on two levels, and neither requires a server or any running cost — exports go
to storage the owner already owns.

**Android Auto Backup** is enabled and covers the database, preferences, **avatars, and scanned
documents** — the evidential core. The **photo gallery is excluded**: the per-app quota (~25 MB) is small,
and backing up photos would silently exhaust it, leaving the owner believing they had a backup when they
had none.

Documents are kept *in* precisely because they are evidence the owner may need again — a record, not a
sentimental memory — and are individually small; but they share the same quota. The blunt fact that shapes
the design: **Android rejects the *entire* over-quota dataset — it does not back up partially.** So a pile
of documents doesn't cost "just the documents"; it silently takes the database and avatars down with it,
inverting this ADR's own promise that the evidential core is safe. The size guard therefore exists first to
**keep the evidential core under quota**, and only second to preserve as many documents as fit.

A static `include`/`exclude` XML rule cannot make that decision, so backup runs through a **custom
`BackupAgent`**: it checkpoints the WAL into a consistent copy, includes database, preferences and avatars
unconditionally, then admits documents **newest-first up to a ceiling *below* 25 MB** (headroom for
database growth between the OS-scheduled backups, which the app does not control). Because Auto Backup runs
unattended with no UI, "surface honestly" cannot happen at backup time: the agent **persists a marker**
(last-backup timestamp + excluded-document count) into **preferences** — not the database, which restore
replaces — and the app surfaces it later as a permanent status line in Backup settings ("Last automatic
backup: 3 days ago — 12 documents were too large to include; use manual export to keep them"), plus a
single low-key notification the first time exclusion kicks in. It is never dropped silently. This content
is fixed at build time and is not user-configurable.

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

## Restore replaces the database but merges media

The export's counterpart, restore, is a **full database replace** — the incoming db becomes the app's db
outright (which is why the exclusion marker lives in preferences, not the database). Media directories,
though, are **merged, not wiped**: the backup's files are overlaid onto whatever is on disk, keyed by their
relative `<kind>/<uuid>.jpg` path. This is safe precisely because the split relative paths make each uuid a
**stable global identity** — a file with a given uuid is always that exact image, so an overlay can never
mismatch. It is strictly better than wipe-and-replace in the case that matters: restoring an **Essential**
backup (db + avatars, no photos) onto a phone that still holds its photo files keeps those irreplaceable
photos instead of turning them all into placeholders. The database is always the *full* database regardless
of scope, so its photo rows line up with the surviving files; any file the restored db does not reference
is an invisible orphan, never rendered, cleanable later — it never resurrects stale data into the UI.

Because a restore is an irreversible destructive replace, it is gated behind an **explicit confirmation**
stating what it will replace ("[scope] backup from [date]"), and it **snapshots the current database aside**
(timestamped, next to the media) first — the same recovery-artifact move ADR-0007 makes for a schema wipe.
Restore is the same class of event and gets the same net.

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
