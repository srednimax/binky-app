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

## Consequences

The preserved file is a recovery artifact, not a restore: reading old data into a new schema *is* a
migration, so it cannot be re-imported automatically. It exists so the data can be recovered by hand, or
by a migration written later.

Weight history is the one thing that cannot be reconstructed from memory. Until Phase 3, weights worth
keeping should be written down outside the app.
