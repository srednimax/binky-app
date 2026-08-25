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
   A release-shaped debug build is how to do this without touching the Play install (phase-7.5.md §7).

`scripts/schema-gate.py` enforces 1–3 in CI on every pull request. 4 and 5 are judgement, and 5 is the one
that caught the refusal screen 1.5 would otherwise have shipped to every existing owner.

⚠️ **It fires in this phase.** Phase 10 takes the schema to **8** — §4 and §5 below share one
`MIGRATION_7_8`. Rule 5 has an extra clause this time: the upgrade must be watched on a **minified**
build, because §3 turns R8 on in the same release. A proof over an unminified artifact does not cover
the artifact owners install.

---

## Where the project stands

**Every phase through 9 is closed, and 1.8.0 is live in production in nine languages.** That is the
first time both sentences have been true at once, and it is the sentence the whole plan was pointed at.
Phase 9's record — including the full run narratives that used to live in this file — is in
[`phase-9.md`](phase-9.md); its appendix holds them verbatim.

So what is open stops being a release checklist and starts being **whatever owners report**, plus what
Play's Console says about the artifact. That is Phase 10, record in
[`phase-10.md`](phase-10.md), **ships as 1.9.0** — one minor version carrying all of it.

Six inputs, none of them from the original plan: two feature requests from an owner (2026-08-23), three
Play quality notices against release 386, and one settings request.

---

## Phase 10 — the boxes

| | What | State |
| --- | --- | --- |
| **10a** | Edge-to-edge off the deprecated bar setters | ✅ **built 2026-08-24** |
| **10b** | The ML Kit delegate stops being portrait-locked | ✅ **built 2026-08-24**, device check owed |
| **10c** | R8 on | ✅ **built 2026-08-24**, device proof owed |
| **10d** | Several photos on a tray — **schema 8** | ✅ **built 2026-08-24**, device proof owed |
| **10e** | Events: a timeline, and dated events an owner writes — **same schema 8** | ✅ **built 2026-08-25**, device proof owed |
| **10f** | A light/dark override in Settings | ✅ **built 2026-08-25**, device proof owed |

**One edge must not be reordered**: **10c before 10d/10e**, so every artifact check after R8 goes on
runs against a minified build rather than proving something about a build nobody ships. Everything else
is free.

### 10a — Edge-to-edge ✅ built 2026-08-24

`enableEdgeToEdge()` is gone. Verified in the AAR's bytecode that every path in androidx.activity
1.13.0 reaches `Window.setStatusBarColor`/`setNavigationBarColor`, so no version of the call avoids the
deprecation — the fix could not be a dependency bump. `WindowCompat.setDecorFitsSystemWindows` keeps the
half Compose needs; the colours are theme attributes across four qualified `colors.xml` files, and
`SystemBarsTest` holds those four to agreement.

- [x] Built, `spotless`/`assembleDebug`/`test`/`lint` green — lint 0 errors, and the 2 remaining
      `IconXmlAndPng` warnings are pre-existing on the launcher icon.
- [x] Smoke-checked on the phone: 8/8 matrix cells clean, dark mode → light icons, light mode → dark.
- [ ] ⚠️ **API 26–28 is argued from theme XML and never observed** — the phone is the only device, and a
      local emulator needs `usermod -aG kvm` and a re-login. This is the strongest argument for the CI
      question below; record it as a limit either way.
- [ ] The full 75-scene × 4-configuration matrix, against 9c's 300-cell baseline.

### 10b — The ML Kit delegate ✅ built 2026-08-24

`tools:remove` on `android:screenOrientation`, plus `configChanges` so the invisible delegate survives
a rotation instead of being recreated — which is the concern the library's own manifest comment states.
`tools:remove` rather than `tools:replace` with a value because lint's `DiscouragedApi` flags any fixed
`screenOrientation` without reading it.

- [x] Verified against the artifact: `aapt2 dump xmltree` over the APK shows **zero** `screenOrientation`
      attributes and `configChanges=0x0fa0` on the delegate. ⚠️ Read the **compiled** manifest, not the
      text one — the text merged manifest keeps XML comments, so a grep there hits our own explanation.
- [ ] **Rotate the phone mid-scan.** If the page is lost, the override comes back out and the notice is
      recorded as accepted — Android 16 ignores the restriction on large screens anyway.
- [ ] Optional: teach `scripts/aab-permissions.py` to assert no `screenOrientation` survives into the
      AAB. Needs a new primitive decoder — in the protobuf manifest the value compiles to an int with no
      source string, so the existing string-reading path cannot see it.

### 10c — R8 ✅ built 2026-08-24

The comment in `app/build.gradle.kts` set the condition: *"turn it on when there is a shipped build to
turn it on against, and watch that build run."* **1.8.0 is live in production.** The condition is met.

- [x] `isMinifyEnabled = true`; the comment **rewritten**, not deleted — it now records the condition
      being met, and why it took from 3a to here to meet it.
- [x] `app/proguard-rules.pro` created. It holds **no keep rules**, and that is the finding: every
      reflection-shaped thing here is already covered by a rule a dependency ships. The file records
      what was checked against `mapping.txt` / `usage.txt` / `configuration.txt`, so the next person
      adds a keep with evidence rather than on suspicion.
- [x] `isShrinkResources` left **false**. One variable at a time; `aab-locale.py` still counts 737
      base strings and all 8 shipped locales in the minified bundle.
- [x] `-keep` for `BinkyBackupAgent` — **not needed, and that was checked rather than assumed.** AGP
      generates `aapt_rules.txt` from the merged manifest and `android:backupAgent` is one of the
      attributes it reads, so the agent survives under its own name with `onFullBackup` and
      `onRestoreFinished` intact. A rule here would have been a no-op nobody could later prove was one.
- [x] `mapping.txt` rides inside the AAB — `BUNDLE-METADATA/com.android.tools.build.obfuscation/
      proguard.map`, 65 MB uncompressed. Play deobfuscates crashes without an upload step.
- [x] **The artifact checks re-run on the minified bundle**, which is the whole reason 10c goes first:
      `aab-permissions.py` still reads 8 permissions and 0 `<uses-feature>`, and 10b's compiled-manifest
      check still finds zero `screenOrientation` with `configChanges` intact.
- [ ] **Prove by behaviour, not by reading rules** — the device half, batched with the rest:
      **enums round-trip by name** (the house rule, and the one that can silently rewrite history),
      an export→restore under kotlinx.serialization, and the daily sweep actually firing.

**Size:** the AAB goes **12.3 MB → 8.1 MB**, a third off, with resource shrinking still switched off.

**What was verified, and what it rules out.** The static half is genuinely done, because R8 writes down
what it did and the answers were read out of that rather than guessed:

- **Enum names survive.** R8 renames the constant *fields* (`DoseStatus.GIVEN -> e`) but never the name
  string passed to the enum constructor, because `Enum.valueOf` reads it — so `.name`, which is what the
  converters write to the database, is unchanged. Confirmed by grepping the compiled dex: `WITHDRAWN`,
  `LEFT_UNEATEN`, `KILOGRAMS` and the rest are all present verbatim. ⚠️ **No rule pins this** — the usual
  `-keepclassmembers enum *` keeps *field* names, which is not what `.name` returns. That is why the
  behaviour proof above stays open rather than being closed by the dex reading.
- **Worker class names survive**, which is the cross-version one: WorkManager persists the worker's class
  name in its own database, so a sweep enqueued by 1.9.0 has to still resolve after the update to 1.10.
  `androidx.work` ships `-keepnames class * extends androidx.work.ListenableWorker` for exactly this, and
  `ReminderSweepWorker` and `UpdateCatchUpWorker` are both unrenamed in `mapping.txt`.
- **`BunnyDatabase_Impl` is unrenamed**; the DAOs are renamed, which is fine — nothing looks those up by
  name.
- **`@Serializable` survives**: `Companion -> Companion` and the `$$serializer` INSTANCE fields are kept
  by kotlinx.serialization's own rules. Renaming the classes is harmless — a `serialName` is a compile-time
  string literal, so an owner's archive does not change shape when R8 renames the class that reads it.
- **`WeightSource` was removed entirely** and that is correct, not a loss: it is derived from
  `visitId != null` and never stored, and nothing in the release variant reads it.

### 10d — Several photos on a tray (schema 8) ✅ built 2026-08-24

Owner request, 2026-08-23. `observations.trayPhotoPath` became `observation_photos`, following ADR-0029's
own shape for the multi-valued droppings fields. Amendment on **ADR-0029**, not a new ADR.

- [x] Entity + join table, tray-level, denormalised per participant, **replaced not merged**. It carries a
      `position` the droppings tables do not — order is part of *this* fact and not of theirs — and no
      `createdAt`, because nothing would read one.
- [x] `MIGRATION_7_8` — the create-copy-drop-rename rebuild, with **all three** cascade-carrying children
      staged and restored, and the old path read into the new table before anything drops.
- [x] The refcount rule survives, its wording intact and its query moved to `observation_photos`. Every
      path that leaves an edit is diffed against what was there and each orphan checked on its own.
- [x] Cap at **6**, checked against `AutoBackup`'s budget rather than picked: ~0.5 MB a frame against a
      20 MB newest-first queue shared with documents makes six about 3 MB for one thorough tray.
- [x] Copy ×9 — three new keys and one reworded; `translation-gate.py` reports 683 × 8 complete.
- [x] **Proven on the phone**: `Migration7To8Test` (6 tests) and the full instrumented suite, **226 tests**,
      all passing on the device. The migration test counts rows for all three children with values spread
      across three observations, so a recipe that staged two out of three fails.
- [x] **The screens seen running**, on a new `tray_photos` seed variant — the default seed records no
      tray photo at all, so both states were unreachable without one. The timeline draws the first photo
      with a **+3** badge; the form wraps four thumbnails onto two lines with their remove controls and
      leaves both add buttons on screen. `observations-tray-photos` reports **clean**;
      `observation-entry-tray-photos` reports `drawn=0 touch=3`, which is **not** a regression — the
      unmodified `observation-entry` scene already reports two such findings with a *larger* overlap, and
      phase-7.5.md's rule is that an unlabelled `touch` hit area says nothing on its own.
- [ ] The other three configurations, with the rest of the phase's device work.

**The SQL was verified against `schemas/8.json` mechanically, not by eye**: every `CREATE TABLE` and
`CREATE INDEX` in `MIGRATION_7_8` is a byte-for-byte transcription of the exported shape. That is the
house rule stated in the migration's own doc, and now the way it was actually checked.

### 10e — Events ✅ built 2026-08-25 (same schema 8)

Owner request, 2026-08-23: *"a calendar or event list — when was the last vet visit, the last nail trim,
or other events the user would like to remember."* An agenda derived from records that already exist,
plus a new dated record an owner writes. **[ADR-0031](adr/0031-an-event-is-a-dated-label-and-the-timeline-is-derived.md)**
carries it; the build record is [`phase-10.md`](phase-10.md) §5.

- [x] `events` table — per bunny, free label, **no type enum and no recurrence** (care reminders own
      repetition; two spellings of one fact is what this codebase keeps refusing). Folded into the same
      `MIGRATION_7_8`, so **no second migration and no `BUNNY_SCHEMA_VERSION` change**.
- [x] The timeline **stores nothing** — `ui/events/Timeline.kt` merges `EventDao`, `VisitRepository`,
      a new `CareDao.completionsForBunny` join and `CareSchedule`. Weighings, observations and doses
      stay out of the default set; each already owns a screen.
- [x] Entry points: the **first** row in `MoreScreen` and a compact card on Home from
      `timelineHighlights(sections, 1, 2)`. **No sixth bottom tab** (ADR-0015).
- [x] Reminding via the one daily sweep (ADR-0024), never an exact alarm (ADR-0003) — a **fifth
      notification channel**, because muting weekly care nagging must not mute next Thursday's
      neutering.
- [x] ADR-0014's calendar hand-off extends to an event for free — same `ACTION_INSERT`, no `RRULE`.
- [x] Copy ×9 — 31 new resources, gate green.
- [x] JVM: `TimelineTest`, `EventSweepTest`. Instrumented: `EventRepositoryTest` (round-trip, the day
      query, both stamps, the cascade on bunny delete) — **9 green on the phone, 2026-08-25**, and the
      whole instrumented suite green at 235.
- [ ] Device proof by hand, with the rest of the phase's device work: the timeline on a real database,
      an event notification landing on the day, and the calendar hand-off opening something.

### 10f — A light/dark override in Settings ✅ built 2026-08-25

Default stays *follow the phone*; Settings gains *System / Light / Dark*. The build record is
[`phase-10.md`](phase-10.md) §6.

- [x] ⚠️ **`AppCompatDelegate.setDefaultNightMode`, not a Compose flag alone.** A Compose-only override
      leaves the window background (painted before Compose composes) and the `values-night/` scrim from
      10a following the *system* while the app follows the override — the exact mismatch 10a exists to
      prevent, and it shows on API 26–28. `theme/NightMode.kt` is the one call site, reached from
      `BinkyApplication.onCreate` and from `SettingsViewModel.setThemeMode`.
- [x] 10a's `SystemBarAppearance` needs no change: it already keys off `BinkyTheme`'s `darkTheme`.
      It needed none.
- [x] Amendment to **ADR-0027** — that decision gaining a lever, not a new one.
- [x] Copy ×9 (section label + three options) — 4 new resources, gate green at 718.
- [ ] Device proof by hand, with the rest of the phase's device work: the override held across a cold
      start, and the window background and system-bar scrim moving with it rather than with the phone.

---

## Standing decisions changed this phase

- **Play screenshots are light-only** (2026-08-24). This reverses the old rule that *dark is the set to
  upload*. The app keeps both themes and `screenshots.py` keeps both cells — it is a decision about what
  goes in the Console, nothing else. Correct it in `store-listing.md` where that file names the set.
- **A screenshot filename carries its locale** — `home-pl.png`, not `home.png` ✅ done 2026-08-24. Nine
  locales of one screen were otherwise nine files distinguished only by their folder, and a PNG loses
  its folder the moment anyone moves it.
- **The fixture bunnies are Lily and Sznycel** ✅ done 2026-08-24. ⚠️ The drivers tap the bunny **by
  name**, so `edge-to-edge.py` and `alarm-gate.py` moved with the seeder. **Reseed before the next
  driver run** — a phone still holding the old seed will fail on its first tap.

## Deferred to the end of the phase

- **Can the four configurations run in CI instead of serially on the one phone?** Raised 2026-08-24, to
  be decided when the code is ready. The real prize is not parallelism but the **API 26–28 coverage the
  phone cannot give** (10a's stated limit). What cannot move: the **field upgrade proof**, which crosses
  a Play-signed 1.0.0 install that refuses a locally-signed APK, and anything about HyperOS itself. The
  hidden cost: `apply_config` flips navigation mode through MIUI's `force_fsg_nav_bar` *because* the
  AOSP overlays are all present-and-disabled on this phone, so an emulator needs a device-family seam in
  `edge-to-edge.py` rather than a CI config file.

## Closing the phase

When every box above is ticked: write the results into `PLAN.md`'s Phase 10 entry, tick **Phase 10**,
and empty this file back down to the standing schema gate — moving the detail into
[`phase-10.md`](phase-10.md) rather than deleting it, which is how Phase 9's record was preserved.
