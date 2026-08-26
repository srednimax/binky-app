# Definition of done — what is still open

The **live checklist**. `PLAN.md` holds the reasoning and the record; this file holds only what is not
yet ticked, so a session can pick up the work without loading 3 000 lines. Keep it short: when an item
closes, tick it here, write the *result* into `PLAN.md`, and delete the detail from this file.

## The standing schema gate — never ticked, checked at every bump

**An update must migrate an existing install without losing anything.** This one does not close with a
phase. Whenever `BUNNY_SCHEMA_VERSION` changes, all five hold before the release goes out:

1. `MIGRATION_x_y` written **and registered in** `BUNNY_MIGRATIONS` for every step. A migration Room
   never runs is not a migration.
2. The exported `app/schemas/*/N.json` committed and git-tagged (ADR-0007) — every later migration is
   written from it.
3. `SchemaGateTest` asserting `appSchemaVersion = N`, so the **launch gate** is proven to let the upgrade
   through. Every migration test opens the database directly and walks past that gate; this is the only
   thing standing in front of it (ADR-0023's Phase 7.5 amendment).
4. A migration test proving the **rows survive** — the committed backup fixtures, restored and counted by
   value, at API 26/34/36.
5. **An actual upgrade watched on the phone**: seed the previous tag, install the new build over it,
   confirm the app opens, `user_version` climbed, and a table-by-table diff on common columns is empty.
   A release-shaped debug build is how to do this without touching the Play install (phase-7.5.md §7) —
   build it with **`./gradlew assembleDebug -PreleaseShapedDebug`**, which is minified *and*
   `BuildConfig.DEBUG == false`. ⚠️ **Both halves are load-bearing**: migrations are registered only when
   `destructiveMigrationAllowed()` says no, so a merely-minified debug build meets the older database with
   the *wipe-consent* screen and migrates nothing — a proof of the wrong code path, and one that looks at a
   glance like the refusal screen 1.5 nearly shipped. It is not debuggable, so read the result by
   installing a plain `assembleDebug` over the top afterwards; an install never touches the data directory.
   And **teach `upgrade-diff.py` any column the migration *moves*** before trusting it — a moved column is
   invisible to its generic diff by definition.

`scripts/schema-gate.py` enforces 1–3 in CI on every pull request. 4 and 5 are judgement, and 5 is the one
that caught the refusal screen 1.5 would otherwise have shipped to every existing owner.

⚠️ **It fired at Phase 10, and it is discharged.** Phase 10 takes the schema to **8** — §4 and §5
share one `MIGRATION_7_8` — and rule 5 carried an extra clause, that the upgrade be watched on a
**minified** build, because §3 turns R8 on in the same release. ✅ **Done 2026-08-25**: a real schema-7
fixture seeded through the schema-7 tag's own build, the release-shaped build installed over it, the app
opened with no refusal, `user_version` 7 → 8, and `upgrade-diff.py` reporting nothing lost across all 20
tables with both tray photo paths landed in `observation_photos`. The record is `phase-10.md` §4.

---
---

## Where the project stands

**Every phase is closed.** Phase 10 was the last one, ticked 2026-08-26; its record is
[`phase-10.md`](phase-10.md), and the box-by-box checklist this file used to hold is that file's
appendix. There is no Phase 11 written, and there is not meant to be — the plan ran out on purpose.
What comes next is whatever owners report, the way Phase 10's own contents arrived.

So this file is back to what its title says: the standing schema gate above, which never ticks, and
the short list below of things that are open without being a phase.

---

## Open, and not a phase

- **1.9.0 has not shipped.** Phase 10 closes on the *build* being done and proven — every box ticked,
  the schema-8 upgrade watched on a minified build, R8 on. The release is the act after it: merge to
  `main`, merge release-please's PR, and the tag's GitHub Release fires `publish-play.yml` onto the
  **internal** track. Production is a second, manual, environment-gated run.
- ⚠️ **Three CI workflows have never run**, and none of them can until this merges — GitHub reads
  `release`, `schedule` and `workflow_dispatch` triggers from the **default branch** only. That is
  `publish-play.yml`, `publish-play-production.yml`, and `ci.yml`'s nightly edge-to-edge matrix, which
  was written against emulators no one here has (no local KVM). **Read the first run of each before
  believing any of them works**; a workflow that has never run is a draft with good syntax.
- ~~**The listing follow-up, deliberately its own PR.**~~ **Folded into the Phase 10 PR and done
  2026-08-26**: nine locales × eight scenes, light, in `art/play-screenshots/`, with
  [`store-listing.md`](store-listing.md) and [`art/README.md`](../art/README.md) rewritten around
  them. The shots are **cropped above the status bar** — the capture driver's Do Not Disturb puts a
  crossed bell in every frame and this ROM ignores SystemUI demo mode, so `pad-screenshot.py
  --crop-status-bar` is the only place it comes off. **What is left of this box is the Console:** the
  production job with `dry_run: true, update_listing: true` as its first real exercise, before a real
  one — and nine locales is nine uploads, not one.
- **Google's developer-verification registration is due 30 Sept 2026.** The owner believes the app is
  already registered and has not confirmed it in the Console; confirming is the whole task. The other
  deadline Play surfaced — the target-API requirement on 31 Aug 2026 — is already satisfied, because
  `targetSdk` is 36.
