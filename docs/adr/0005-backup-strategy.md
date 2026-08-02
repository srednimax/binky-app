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
database growth between the OS-scheduled backups, which the app does not control). The agent itself, the
WAL checkpoint, the unconditional set and the marker land at 1.0; **the documents ceiling and the exclusion
notification land with Phase 5**, because `documents/` is empty until then, so at 1.0 the admission function
would admit nothing, the notification could not fire, and the app's first notification channel would be
created in a release that deliberately asks for no notification permission. Building them beside the
documents that exercise them costs nothing later — the file set is ordinary app code, and a backup written
by 1.0 restores into 1.2 regardless. Because Auto Backup runs
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

- **Essential** — database, preferences, and bunny avatars.
- **Records** *(default)* — Essential plus scanned documents; everything the owner may need again.
- **Everything** — Records plus the photo gallery; large and occasional.

**Preferences ride in every scope, from Essential upward.** They are a few hundred bytes, Auto Backup
already carries them, and their absence does not read as missing data — it reads as bugs: a restored phone
showing kilograms when the owner chose grams, landing on the wrong bunny, and defaulting its next export to
a scope the owner did not pick. The one asymmetry worth avoiding here is the automatic path promising
something the manual path quietly drops.

The export shares out through the system share sheet first (which cannot fail for provider reasons), with
a remembered folder destination added afterwards, once writing to that provider has been verified on a
real device. If the folder is a cloud provider's, the backup lands in the owner's own cloud. That
destination is **deferred to 1.1**, with the recurring export reminder: remembering a folder saves two taps
and does not make export automatic, so at 1.0 it would buy convenience while carrying the plan's biggest
unverified assumption into the release that exists to make backup trustworthy.

The scope is chosen during first-run setup with a plain explanation of the trade-off — not hidden in
settings — and can be changed in settings later. The scope is recorded in the export filename so a person
can tell two files apart, but the filename is **not** what restore reads: a **manifest inside the zip**
carries the scope, the schema version, the creation instant and the per-kind counts, and that is the
authority. A filename is the one part of a file an owner can trivially change, and the confirmation dialog
makes a promise about what is inside — a promise must not be sourced from the outside of the envelope.

Restore also **never builds a path out of archive input**. It extracts only entries matching known shapes —
the database, the preferences file, and `<kind>/<uuid>.jpg` with both halves validated — and ignores
anything else, which defeats a `../` traversal by construction rather than by sanitising after the fact. An
archive with no manifest or no database is refused by name rather than partially applied. The threat is
mild, since the file is normally the owner's own, but backups travel by mail and messenger, and an
arbitrary write into app-private storage is not a thing to leave open in an app holding an animal's medical
history.

### Phase 4e amendment: the remembered folder, and the reminder that makes it a habit

The deferred destination lands, and the shape it lands in is narrower than "export to a folder".

**It is a saved destination, not a second export mechanism.** The share sheet is still the primary
path and is never replaced: a chooser cannot fail for provider reasons, and it is what the fallback
falls back *to*. Every failure in the folder path — a provider that refuses the write, a grant
revoked in Android's settings, a preferences file restored from a phone that granted nothing — ends
with the finished archive handed to the share sheet and one sentence saying why. The export is
already built by the time any of that can happen, so no failure here can cost a backup.

**A stored tree URI is not a working folder**, which is the same shape as the automatic-backup
marker's three states and exists for the same reason. The grant is checked against
`persistedUriPermissions` and the provider is asked for a display name on every read; if either
declines, the screen says the folder is gone rather than showing a name that would fail at the
moment the owner counted on it. A folder that quietly emptied itself would be ADR-0001's silence
again, one layer down.

**The recurring reminder is a preference, not a care reminder.** Care reminders hang off a bunny and
live in the database (ADR-0018); this one hangs off the app, so it is a switch and an interval in
Backup settings, and it rides the one daily sweep as one more branch (ADR-0024) rather than becoming
a second scheduled thing. Off by default — an app that starts nagging about backups uninvited is one
an owner learns to swipe past — and on its own notification channel, because it is the least urgent
thing this app posts and therefore the likeliest to be muted, which is exactly why muting it must not
cost a vaccination.

**Its copy is a prompt about the owner's export, never a claim that their data is unsafe.** What is
and is not protected is the automatic-backup status line's job, including the case where the honest
answer is that nobody knows.

**An export counts at the moment the file exists**, not when it reaches a destination. `ACTION_SEND`
returns no result, so the alternative is a reminder that keeps prompting someone who exports every
week; erring towards "they did it" is the direction that respects the owner's attention, and no part
of the app claims a file is *safe* anywhere on the strength of that date.

**The Google Drive question is still owed an answer on a real device.** Whether that provider accepts
a write through a persisted tree grant is the plan's longest-standing unverified assumption, and
building the path does not settle it. It is a gate item, and a failure there is a finding to record —
with local storage and the Drive app folder named as the alternatives — not a checkpoint to fail,
because the fallback is the export that already ships.

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
live database will be captured mid-write and restore corrupt. That last one is not hypothetical while
`allowBackup="true"` stands in the manifest with no agent and no rules: the platform is already eligible to
copy `filesDir` wholesale, sidecars included. Either the agent takes control of the file set or
`allowBackup` goes to `false` — what cannot ship is the middle state, which produces a backup that appears
to work and restores corrupt.

**The photo gallery's exclusion has to be said out loud, not merely implemented.** Photos end up the least
protected data in the app — outside Auto Backup, outside Essential and Records, present only in a manual
"Everything" export — and an owner who has never been told that will reasonably assume the net covers
everything. So first-run setup and Backup settings state it in words. This is the same rule as the missing
marker: a gap the owner cannot see is worse than a gap they were told about, which is ADR-0001 pointed at
the one directory the automatic path does not reach. The alternatives were weighed at Phase 3 and rejected —
the shared MediaStore would fork ADR-0020's pipeline, break the uuid identity the media merge depends on
and need a storage permission at `minSdk` 26; admitting photos to the agent's set would put an unbounded
directory inside an all-or-nothing quota, risking the database to protect a copy.

**One exception, because the reason above is about the quota and not about photos:** on a *device-to-device
transfer* there is no cloud account and no quota, so neither the size argument nor the privacy policy's
promise applies — and silently dropping a whole gallery on a phone upgrade would be the worse failure. The
agent therefore admits `photos/` when the transport reports `FLAG_DEVICE_TO_DEVICE_TRANSFER`, and only then.
That flag exists from API 30; below it the answer is no, which is the same line the old `backup_rules.xml`
drew for "API 30 and below".

Because the evidential core (database, avatars, documents) is now covered by Auto Backup, the
manual-export folder destination — the plan's biggest unverified assumption (Google Drive provider
writes) — gates only the **sentimental photo gallery**, not vet evidence. That lowers the cost of that
assumption failing. Documents appearing in *both* Auto Backup and the manual Records/Everything scopes is
deliberate, not redundant: Auto Backup is the effortless net, manual export is the portable copy the owner
controls.
