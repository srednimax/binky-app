# Schema changes wipe data until Phase 3, then use real migrations

There is no public release until every phase is complete, and the schema churns hardest in Phases 1-2 —
before any backup exists. Through those phases a schema change is allowed to destroy the database rather
than carrying a migration for every field added to a still-unsettled model. Anything entered before
Phase 3 is disposable test data by definition.

From Phase 3 onward — once export and restore exist, and once builds may be handed to alpha testers —
every schema change gets a real Room migration with a test. An alpha tester who loses their rabbit's
history to a routine update does not stay a tester.

Regardless of phase, **a destructive wipe never happens silently.** On startup the database file's schema
version is read *before* Room opens it. If this build would wipe it, a blocking screen appears first, and
the existing database file is preserved with a timestamp alongside the media.

## Consequences

The preserved file is a recovery artifact, not a restore: reading old data into a new schema *is* a
migration, so it cannot be re-imported automatically. It exists so the data can be recovered by hand, or
by a migration written later.

Weight history is the one thing that cannot be reconstructed from memory. Until Phase 3, weights worth
keeping should be written down outside the app.
