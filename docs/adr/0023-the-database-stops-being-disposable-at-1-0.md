# The database stops being disposable at 1.0

ADR-0007 lets a schema change destroy the database until Phase 3, and attaches the migration obligation the
moment a schema version reaches a device holding real data. ADR-0019 makes that moment **1.0**. Both ADRs
say *when* the rule changes. Neither says what changes **mechanically**, and the answer is not "write
migrations from now on" — it is four things, all of which are still configured for the disposable era.

## The destructive fallback becomes debug-only

`buildBunnyDatabase` is built with `fallbackToDestructiveMigration(dropAllTables = true)`. Through Phases
1-2 that is ADR-0007's entire premise. From 1.0 it silently changes meaning: **a forgotten migration stops
costing test data and starts deleting a real owner's history.**

So the fallback is gated on `BuildConfig.DEBUG`. A release build with no migration path **throws when the
file is opened** — the app fails to launch, loudly, and lands in Play Console crash vitals, which is one of
the three reasons ADR-0009 chose Play over sideloading. A crash on launch is a bad day for the owner. It is
a *recoverable* bad day, and it is the only outcome here that is.

The debug build keeps the fallback, which is exactly the "throwaway debug build/database where destructive
wipes are still fair game" that ADR-0007 already grants Phases 4-5. Nothing about that churn changes; it
simply stops sharing a process with real data.

## In a release build, the consent screen has nothing to consent to

ADR-0007's guard runs before Room, copies the file aside, and shows a blocking screen whose one control is
a forward button that destroys. That is right when the destruction is *going to happen anyway* and the
owner's only choice is whether to look at it first.

In a release build the destruction is no longer going to happen — the open will fail instead. A forward
button there would be a control that destroys a bunny's history **on a path where nothing was going to
destroy it**, offered to an owner who is already confused. So the release variant of the screen states that
this build cannot open the data, names the preserved copy, and offers the **share** action and nothing else.
It is a dead end, correctly: the way out is a fixed build, not a tap.

The copy is still taken first, in both builds. `preserveBeforeWipe` is then preserving before a *failure*
rather than before a wipe, which is a better reason than the one it was written for.

## The debug build becomes a separate app

ADR-0007 offers "a `debug` `applicationId` suffix, **or** a separate DB name". Once 1.0 is installed from
Play, that is not an "or". The Play build is signed with the release key; a locally-signed debug build of
the same `applicationId` can neither sit beside it nor replace it, and the only way through is uninstalling
the Play build — destroying the real data it holds. A separate DB name does not help with any of that.

So the debug build takes `applicationIdSuffix = ".debug"` and a distinct label. The release app on the
author's phone holds real bunny history and is dogfooded, which is the premise ADR-0007's obligation rests
on; the debug app is throwaway and keeps the free churn. `FileProvider`'s authority is already
`${applicationId}.fileprovider`, so it follows the suffix; the instrumentation package does too, which is a
correction owed to CLAUDE.md's Xiaomi fallback command.

## Restore proves the migration instead of asserting it

Because a release build now refuses a file it cannot migrate rather than emptying it, "can this build open
that backup?" has a real answer — and the honest way to get it is to try.

Restore therefore **stages, migrates, then swaps**: it unzips to a staging database, refuses outright
anything at a *newer* schema version than this build (no migration runs backwards), opens the staged file
with the real migrations, and swaps in the result — which is by then already at the current schema. A
failure happens on the copy, before anything on the phone has been touched, and the owner is told what was
found.

The alternative, comparing the incoming `user_version` against this build's, only ever asserts that a
migration *exists*. It never establishes that it survives this particular file, and it needs a list that
drifts. The staged open needs neither. Its one trap: the staged builder must pin its own configuration, or
in a debug build the fallback above would quietly empty the very file it was asked to test.

## Amendment (Phase 4b): a build takes migrations **or** the fallback, never both

This ADR says the debug build keeps the fallback and the release build gets migrations, and reads as though
they are independent settings. They are not. **Room prefers a registered migration over the destructive
fallback**, so registering the migrations unconditionally — the obvious way to write it, and the way that
looks like it exercises them harder — would silently retire the debug wipe this ADR just chose to keep.

That matters most while a phase is in flight, because a pending migration is *rewritten in place* as the
shape churns (ADR-0007). A debug database that had already migrated to an earlier shape of version 5 cannot
be migrated again: same version, different identity hash, so Room throws "cannot verify the data integrity"
rather than wiping. The consent screen would also be promising a wipe that no longer happens — a screen
whose whole value is that its promise is true.

So `buildBunnyDatabase` branches: `allowDestructiveMigration` picks the fallback, and its `else` registers
`BUNNY_MIGRATIONS`. The debug build keeps wiping through the consent screen for the whole of a churning
phase, and the migration is proven **by test rather than by the author's phone** — `MigrationTestHelper`
against the exported schema, plus a release-shaped open through `allowDestructiveMigration = false`, which
is the second thing 3c's parameter buys. CI runs both on every pull request, which is what makes the guard
always-on rather than a thing someone remembers to do before a release.

## Amendment (Phase 7.5): the gate asks whether a migration **exists**, not whether the versions differ

This ADR's release half says the open "will fail instead" — and that is true of a file **no migration
covers**. The guard in front of Room did not make that distinction. `schemaMismatchPending` is
`onDisk != 0 && onDisk != app`, and `BinkyApplication` treated every `true` as a reason to show the
blocking screen, so the release variant — deliberately a dead end, with no forward button — stood in front
of the commonest event in the app's life: an owner updating across a schema bump.

**The consequence was total and had been shipping since 1.1.** A phone holding a 1.4.0 database that opens
1.5 gets *"This version cannot open the records on this phone"*, a share button, and advice to install the
version that wrote them — which a Play owner cannot do. The database is untouched and a copy lands in
`preserved/`, so nothing is deleted; the app is simply unusable, and `MIGRATION_6_7` never runs. Watched on
the phone at 7.5 with a real schema-6 database under a release-shaped build, after being read out of the
code rather than met in the wild.

Every proof this project had of the migrations opened the database **directly** — `MigrationTestHelper`,
the release-shaped open, three committed backup archives at three API levels. All of them true, none of
them touching the gate the owner actually arrives at. That is the general lesson: a guard in front of the
thing under test is not covered by testing the thing.

So the decision moves into `schemaGateDecision` (`SchemaGate.kt`), a pure function over the on-disk
version, this build's version, the migrations **actually registered**, and whether the fallback is armed.
It returns `Open` for a fresh install, a matching version, or a mismatch a registered migration can walk;
`Consent` for a debug build, which by the amendment above has no migrations registered at all; `Refuse`
only for what a release build genuinely cannot read — an unknown version, or a file from a newer Binky,
since no migration runs backwards. Branch order is load-bearing: `destructiveAllowed` is asked **before**
the migrations, or a debug build would sail past the consent screen and let Room empty the file in silence.

The copy is still taken before a migratable upgrade. It is one file copy per schema bump, and a migration
rewriting the only record of a bunny's weight series is the one moment insurance is worth having.

## Consequences

A schema mistake now costs differently per build, and that asymmetry is the point: free in debug, a failed
launch in release, and never a silent deletion in either.

`./gradlew installDebug` keeps working after 1.0, which it would otherwise stop doing on the day the
release lands on the phone — a build-config detail, discovered as a workflow outage.

From schema 4 onward the exported schema JSONs are committed **and git-tagged** (ADR-0007), because they
are what every later migration is written from. `preserved/` gains a second kind of occupant — restore
snapshots alongside wipe copies — which is why Settings has to name what each row is rather than listing
files.

None of this is reachable without ADR-0019 having moved 1.0 forward. Under the old roadmap these four
changes would have landed at the end of Phase 5, on a schema that had already churned through the vet and
medication tables with nothing standing behind it.
