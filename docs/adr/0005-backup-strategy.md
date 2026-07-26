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
(last-backup timestamp + excluded-document count) **in a plain file under `filesDir`, outside the
database** — which restore replaces — and the app surfaces it later as a permanent status line in Backup
settings ("Last automatic backup: 3 days ago — 12 documents were too large to include; use manual export to
keep them"), plus a single low-key notification the first time exclusion kicks in. It is never dropped
silently. This content is fixed at build time and is not user-configurable.

A file rather than the app's DataStore, because **the agent cannot assume the app exists around it**. When
the system starts the process *for* backup it binds the base `android.app.Application` rather than this
app's subclass, so `AppContainer` is absent — and reaching for it would in any case force the `lazy` that
ADR-0007 makes the structural guard standing in front of a wipe. The agent therefore depends on **paths,
not a `Context`**: the file set, the quota admission and the marker are all functions over `File`, which is
also what makes the one piece of arithmetic in here testable on the JVM rather than only on a phone that
happens to be idle and charging.

### The marker must not lie in either direction

The marker is honest when the agent runs. Two cases where it isn't, both of which the design has to handle
explicitly, because each one produces a *reassuring* falsehood:

- **Absence must never render as fine.** Auto Backup runs only if the device has backup enabled with an
  account signed in, and then only when idle, charging and on a network. An owner who has that switched
  off — or a Xiaomi that never gets round to it — produces **no marker at all**, and Android exposes no
  reliable public API to ask whether the app's data is actually being included. A blank status line then
  reads as a working net. That is precisely ADR-0001's failure applied to backup: silence meaning nobody
  looked. Backup settings therefore states the unknown case in words — *"No automatic backup has been
  recorded on this phone"* — with a button into system backup settings. Unknown is a state and gets its own
  copy.
- **The marker must not survive onto a different phone.** It lives outside the database so that a *restore*
  does not wipe it — but anything inside the backup set is carried onto the new device by the very event it
  describes, and would then report a recent successful backup having never made one. Because the agent
  names its own file set, the marker is simply **never included**, and cannot travel at all.
  **`onRestoreFinished()` clears it regardless**, for a second and different reason: after a restore the
  phone no longer holds the data the old marker vouched for, so even a locally-earned marker is now
  reassuring about something that is gone. Two mechanisms failing differently — the exclusion is a static
  claim about a file set that a later edit could silently break, the clear is a runtime guarantee at the
  exact event.

A marker also **ages out**: past 14 days — against Auto Backup's roughly daily cadence — the status stops
showing a bare date and says it is stale. A technically true timestamp from two months ago is a worse
signal than an admission.

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
stating what it will replace ("[scope] backup from [date]"), and it takes an **automatic Essential-scope
export of the current state** first, timestamped into the same directory as ADR-0007's wipe copies — the
same recovery-artifact move, and a zip rather than a bare file so that undoing a bad restore is the
ordinary restore path instead of a recovery procedure. Restore is the same class of event, gets the same
net, and additionally gets a way back.

The replace itself **stages, migrates, then swaps** rather than overwriting the live file and hoping
(ADR-0023): a backup at an older released schema is migrated on the copy, one at a newer schema is refused
outright since no migration runs backwards, and a failure lands before anything on the phone is touched.

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
