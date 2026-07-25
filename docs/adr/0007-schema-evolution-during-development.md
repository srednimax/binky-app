# Schema changes wipe data until Phase 3, then use real migrations

Version 1.0 ships at the end of Phase 3 (ADR-0019), and the schema churns hardest in Phases 1-2 — before
any backup exists, and before any build has reached a device holding data worth keeping. Through those phases a schema change is allowed to destroy the database rather
than carrying a migration for every field added to a still-unsettled model. Anything entered before
Phase 3 is disposable test data by definition.

The obligation to migrate attaches to a schema version the moment it has been **released to a device that
holds real data** — an alpha tester, or the author's own dogfood app, which from Phase 3 keeps real bunny
history once backup exists. Every such released version must have a **tested forward migration**; the
released schema JSONs are committed and **git-tagged** so it is unambiguous which versions are load-bearing.
It is *not* anchored to the calendar or to every edit: the author develops on the same physical phone that
holds the real data, so the schema still churns during Phases 4-5 while the medication/vet tables are
designed. That churn happens on a **throwaway debug build/database** (a `debug` `applicationId` suffix, or a
separate DB name), where destructive wipes and rewriting pending migrations are still fair game; when a
feature's schema settles, a **single consolidated, tested** migration is written from the last released
version, and only then does it reach the real-data app. Migration count tracks *releases*, not keystrokes,
and no tester — or the author — loses history to a routine update.

**A destructive wipe never loses the file**, in any phase: before a destructive migration the existing
database is copied aside with a timestamp, alongside the media.

The **consent** half arrives in Phase 2, not Phase 1. From then on, startup reads the database file's schema
version *before* Room opens it, and a blocking screen appears first if this build would wipe it. In Phase 1
that screen would fire on every entity added, to guard a bunny name and a birthdate that take twenty seconds
to retype — daily consent from the only user, who already knows they are wiping, is a rubber stamp, and the
likely outcome is that it gets disabled before reaching the phase where it matters. It matters from Phase 2,
when the database first holds a weight series (below).

## The guard is structural: the container does not exist until consent

A blocking screen only blocks if **nothing opens the file before it**. Room's `build()` does not open the
file, so the tempting arrangement is to leave the container constructed and merely stop it from *collecting* —
which works, and is one eager `stateIn` away from silently breaking, in a project that goes on to add reminder
rescheduling at process start. A guard by absence-of-subscription is unwritten, unenforceable, and
load-bearing for the only copy of unretypeable data.

So the pre-Room check and the copy-aside run in `Application.onCreate` — four bytes at offset 60 of the SQLite
header, no Room and no container involved — and `AppContainer` sits behind a `lazy` that is forced only once
the wipe has been consented to. No Room object exists, so no collection can exist, and the property stays
true however the container grows later. (This is not the decorative `database by lazy`, which would be forced
immediately by the eager repositories that take it as a constructor argument. This lazy *is* the gate.)

On consent the database is then opened **explicitly**, so the destruction the screen describes happens while
the owner is looking at the screen rather than whenever some flow first collects.

## The copy has to stay reachable

The screen states where the copy is and offers one forward button. After that tap, `filesDir/preserved/` is
unreachable to the owner without `adb` — and through Phase 2 that file is the only copy of a weight series.
So Settings lists the preserved copies with a **share** action and a per-file **delete**. A share sheet puts
the copy into Drive or an email at the moment the owner is thinking about it, which is the only thing that
actually makes it safe; `adb` instructions on a screen are a developer's recovery path, not an owner's. The
share carries the `-wal` and `-shm` sidecars alongside the `.db`, because the newest writes may live only in
the sidecar and the guard cannot checkpoint them — checkpointing means opening the file, which is the one
thing it must not do.

Nothing prunes automatically: silently deleting the owner's only copy is the failure this whole mechanism
exists to prevent. The copy is named from the database file's own `lastModified()` rather than the moment of
panic, so a hesitating owner relaunching repeatedly overwrites one copy instead of minting a new one each
time, and the name dates the *data*.

Whether `preserved/` belongs inside the Auto Backup set is Phase 3's question (ADR-0005): a second full copy
of the database, inside a quota where scanned documents are already being squeezed out.

## Consequences

The preserved file is a recovery artifact, not a restore: reading old data into a new schema *is* a
migration, so it cannot be re-imported automatically. It exists so the data can be recovered by hand, or
by a migration written later.

Weight history is the one thing that cannot be reconstructed from memory. Until Phase 3, weights worth
keeping should be written down outside the app.
