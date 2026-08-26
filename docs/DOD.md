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

⚠️ **It fired in this phase, and it is discharged.** Phase 10 takes the schema to **8** — §4 and §5
share one `MIGRATION_7_8` — and rule 5 carried an extra clause, that the upgrade be watched on a
**minified** build, because §3 turns R8 on in the same release. ✅ **Done 2026-08-25**: a real schema-7
fixture seeded through the schema-7 tag's own build, the release-shaped build installed over it, the app
opened with no refusal, `user_version` 7 → 8, and `upgrade-diff.py` reporting nothing lost across all 20
tables with both tray photo paths landed in `observation_photos`. The record is `phase-10.md` §4.

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
| **10b** | The ML Kit delegate stops being portrait-locked | ✅ **done**, rotated mid-scan 2026-08-25 |
| **10c** | R8 on | ✅ **done** — found and fixed a silent ML Kit break; export *and* restore proven on the minified build 2026-08-25 |
| **10d** | Several photos on a tray — **schema 8** | ✅ **done**, upgrade watched on the phone 2026-08-25 |
| **10e** | Events: a timeline, and dated events an owner writes — **same schema 8** | ✅ **done**, driven on the phone 2026-08-25 |
| **10f** | A light/dark override in Settings | ✅ **done**, proved on the phone 2026-08-25 |
| **10g** | Weight entry gains a kg/g toggle — **both** weight fields | ✅ **done**, driven on the phone 2026-08-25 |

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
- [x] ⚠️ **API 26–28 was argued from theme XML and never observed** ✅ recorded 2026-08-25 — the phone
      is the only physical device and it is modern, and a local emulator needs `usermod -aG kvm` and a
      re-login. **This is the limit, stated:** every claim about how the app draws under the system bars
      below API 29 rested on the four qualified `colors.xml` files and `SystemBarsTest`, not on a
      screenshot. It is the reason the CI answer below is *yes* — the nightly matrix observes it on an
      API 26 emulator. Until a nightly has run green, the limit stands as written.
- [x] **The full 4-configuration matrix** ✅ 2026-08-25 — **276 cells (69 scenes × 4), 0 skipped,
      no defect.** Evidence in `~/binky-screenshots/1.9.0-e2e/` (report + every PNG). 7 `drawn`
      findings were raised and **all seven are explained, none is a bug**:

  - `reminders-sheet` (3 cells) and `care-reminder-editor` (1 cell) are the **opening frame of a
    scroll taller than the screen** — the case `edge-to-edge.py` already calls "a list scrolling, not
    a defect". Both `-bottom` companions came back clean, which is the proof that design asks for.
  - `document-viewer` (3 cells) is **13 px of line leading** inside the TextView's node box. ⚠️ The
    number that settles it: the overlap is **constant at 13 px while the navigation bar varies 48 →
    142 px**, and content sits exactly 94 px higher in three-button than in gesture — the difference
    between the two bars. An unpadded screen's overlap tracks the bar; this one does not, so the
    shell's `Scaffold` was padding correctly all along. Confirmed against the PNG: the glyphs clear
    the pill.
  - ⚠️ **A `drawn` finding is arithmetic, not a verdict.** A fix was written for `document-viewer`
    and reverted: `navigationBarsPadding()` under a `Scaffold` that already calls
    `consumeWindowInsets(insets)` adds nothing, and the re-shot bounds came back byte-identical.
    Read the screenshot and check whether the overlap scales with the inset **before** changing code.
  - Worth doing sometime: the `drawn` tier could ignore sub-4dp overlaps, which cannot be a real
    collision — and `document-viewer` has no `-bottom` companion, which is why it alone lacked the
    disambiguating evidence the other two had.

### 10b — The ML Kit delegate ✅ built 2026-08-24

`tools:remove` on `android:screenOrientation`, plus `configChanges` so the invisible delegate survives
a rotation instead of being recreated — which is the concern the library's own manifest comment states.
`tools:remove` rather than `tools:replace` with a value because lint's `DiscouragedApi` flags any fixed
`screenOrientation` without reading it.

- [x] Verified against the artifact: `aapt2 dump xmltree` over the APK shows **zero** `screenOrientation`
      attributes and `configChanges=0x0fa0` on the delegate. ⚠️ Read the **compiled** manifest, not the
      text one — the text merged manifest keeps XML comments, so a grep there hits our own explanation.
- [x] **Rotated mid-scan** ✅ 2026-08-25, and the second run on the **minified** artifact: landscape
      mid-scan, a page captured, rotated back — same `ActivityRecord` id throughout, page intact, and the
      finished scan saved into the app. Nothing lost, so the override stays out.
- [x] **`scripts/aab-permissions.py` now asserts it** ✅ 2026-08-26, so a dependency bump cannot quietly
      re-lock the screen. ⚠️ **The primitive decoder this box was written around turned out not to be
      needed, and that was checked rather than assumed**: a fixture manifest compiled with `aapt2 link
      --proto-format` shows `screenOrientation` keeping its source string (`portrait`) *alongside* the
      compiled int, unlike `android:required`, which has no text form at all. The int path is still
      there — for a value set by resource reference, which does lose its literal — but detection never
      reaches it. Proven both ways: green on the real bundle, and exit 1 naming both locked activities
      on the fixture. The delegate's `configChanges` prints as context beside the permission guards.

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
- [x] **Proved by behaviour** ✅ 2026-08-25 on the minified build — and it found a defect every static
      check had passed. **Enums round-trip by name**: an observation written *by the minified build*
      stored `MANY`, `EATEN`, `BRIGHT`, `MORE`, `LARGE`, `ROUND`, none of them an ordinal. **The daily
      sweep fires**: `WM-WorkerWrapper: Starting work for …ReminderSweepWorker`, SUCCESS — WorkManager
      resolved the worker from its persisted class name under R8. **Export writes** a zip, so
      kotlinx.serialization's write path survives.
- [x] ✅ **The restore half is done** 2026-08-25, on the minified build, and the roots drawer turned out
      not to be on the path at all. **Choosing an export folder is what unblocks it**: `OpenDocumentTree`
      opens on DocumentsUI's last-used location with a *USE THIS FOLDER* button already on screen, and
      once a folder is set, `OpenDocument` reopens in that same location with the export sitting in it.
      The drawer only ever needed opening because nothing had put a file somewhere the picker already
      looked. Round trip: an *Everything* export written straight to the folder (4.48 MB), a 1234 g
      weighing recorded **after** it so a no-op restore could not pass, then the restore — manifest
      parsed (*"Everything backup from Aug 25, 2026, 6:21 PM"*, so kotlinx.serialization's read path
      survives R8), *13 images came from the backup*, and a pre-restore snapshot preserved. Verified by
      installing a plain `assembleDebug` over the top and reading the database: **all 22 tables back to
      their baseline counts, the 1234 g row gone**, 5 photo files / 5 rows and 8 document pages / 8 rows
      on disk. ⚠️ The intermediate state was read from the **UI**, not a pulled database — the
      release-shaped build is not debuggable, so `run-as` is unavailable while it is the installed one.
- [x] 🔴 **R8 silently disabled the guided document scanner, and it is fixed** ✅ 2026-08-25. It did not
      crash: `MlKitDocumentScanner` catches everything and falls back to the plain camera by design, so a
      feature owners have simply stopped existing behind one log line. ML Kit's registrar is named inside
      a manifest **meta-data key**, which `aapt_rules.txt` does not read, so R8 kept the class and shrank
      away its no-arg constructor — `NoSuchMethodException: CommonComponentRegistrar.<init> []`, then an
      NPE building the client. Fixed by one evidence-backed rule in `proguard-rules.pro`
      (`-keepclassmembers class * implements …ComponentRegistrar { <init>(); }`); the minified build now
      opens `DocumentScanningActivity` with zero fallback lines. **`proguard-rules.pro` no longer holds
      zero keep rules**, and §3 of `phase-10.md` says why that changed.

- [x] **`scripts/aab-reflection.py`** ✅ 2026-08-26 — the gate that was missing when this broke. Every
      class named in an `<meta-data>` for reflection is looked up in the artifact's dex and must have a
      public no-arg constructor. It reads the class names **out of the manifest** (any `<meta-data>`
      whose value is one of three markers: Firebase's registrar sentinel, `androidx.startup`, and
      datatransport's `backend:` namespace) rather than from a list that would go stale, and finds five.
      ⚠️ **`mapping.txt` cannot answer this** — R8 writes a bare `CommonComponentRegistrar ->
      CommonComponentRegistrar:` line with **no members under it** for a class it kept unrenamed, so
      absence from the mapping is not absence from the artifact. The dex is what gets read: class_defs
      for "this artifact *defines* it", then the class's own direct methods, because a method_id alone
      could be satisfied by a caller elsewhere. **Proven by removing the keep rule and rebuilding**: the
      check reports `GONE CommonComponentRegistrar()` and exits 1 on that bundle, and green on the one
      with the rule. Wired into both publish workflows, which now run four `aab-*.py` checks.

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
- [x] **The other three configurations** ✅ in 10a's matrix. `observations-tray-photos` is clean in all
      four; `observation-entry-tray-photos` reports **no `drawn` finding in any of them** and only
      `touch`-tier hit areas, tracking the unmodified `observation-entry` baseline scene cell for cell.

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
- [x] **Device proof by hand** ✅ 2026-08-25, all three on the minified build: the timeline on a real
      database (a care schedule and a vet visit, two derived sources, upcoming above past, grouped by
      month); an event dated today notified by the forced sweep on `channel=events` — *Nail trim ·
      Today, for Sznycel.*, sitting in the shade beside the seed's own **care** reminder of the same
      name, which is the fifth channel's argument observed rather than asserted; and the hand-off opening
      Google Calendar with *New event: Nail trim, August 25* prefilled.

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
- [x] **Device proof by hand** ✅ 2026-08-25, by pinning `cmd uimode night` and sampling pixels rather
      than eyeballing: phone Light + app Dark → background and both bar strips `(22,19,13)`; phone Dark +
      app Light → `(255,248,239)`, **across a cold start**; phone Dark + app System → dark. Both
      divergent directions, so neither can be the phone leaking through, and all three chips repaint in
      place with no restart. ⚠️ *Same as this phone* needs ~2.5 s to settle where the other two take
      0.6 s — a screenshot taken too early reads exactly like "it did not repaint", and briefly did.

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

## Decided at the end of the phase

- **Can the four configurations run in CI instead of serially on the one phone?** **Yes — decided
  2026-08-25, and built.** The full four-config matrix runs on emulators at **API 26 / 34 / 36**, the
  same three levels `instrumented` already covers. `ci.yml` was already running emulators with KVM, so
  this extended an existing pattern rather than starting a project.

  - **Nightly and `workflow_dispatch`, never `pull_request`**, and deliberately *not* wired into
    `instrumented-gate`. `edge-to-edge.py` walks ~75 scenes a cell through uiautomator taps whose
    `settle()` timings are tuned to real hardware, and an emulator is where those go flaky rather than
    fail. A flaky required check is one people learn to re-run without reading. Promote it to
    `pull_request` after a few weeks of steady nightlies, not before.
  - **The device-family seam landed where the research said it would**: `set_nav_mode` in
    `edge-to-edge.py`. HyperOS drives navigation mode from `force_fsg_nav_bar` *because* the AOSP
    `com.android.internal.systemui.navbar.*` overlays are present-and-disabled on it; everything else
    takes `cmd overlay enable-exclusive --category`. ⚠️ **The overlay path is a no-op that reports
    success on the phone**, which is why the family is *detected* and never passed in — a cell driven
    the wrong way still captures, still checks and still says "clean" against an inset that never moved.
  - ⚠️ **API 26–28 has no gesture navigation at all**, so that leg runs **two** configurations, not
    four. `usable_configs` drops the gesture cells **by name** into the report rather than capturing
    them under a label the device never matched — the same class of lie the `_PINNED` rotation guard
    exists to prevent, and worse in CI where nobody is watching the screen.
  - `--assert-clean` is what makes it a check: non-zero on any `drawn`-tier finding **or any SKIPPED
    scene**, because a driver that could not reach a screen has not shown it to be clean. `touch`-tier
    findings stay advisory.
  - **What still cannot move**, unchanged: the **field upgrade proof** (it crosses a Play-signed 1.0.0
    install that refuses a locally-signed APK), and anything about HyperOS itself — autostart, Doze, the
    battery-optimisation exemption.
  - ⚠️ **Written against emulators that have never run it.** There is no local KVM, so the seam, the
    two-config API 26 leg and the job itself are unproven until the first nightly. Read that run before
    trusting this box.

### 10g — Weight entry gains a kg/g toggle ✅ built 2026-08-25

Owner request, made while the 10c restore proof was being driven: *"you can only specify in grams, so a
simple switch gram/kg when providing new / updating old data"*. Folded into Phase 10 rather than deferred,
because Phase 10 is explicitly the phase that takes whatever owners report — 10d and 10e arrived the same
way.

⚠️ **It reverses a stated house rule, so it is a decision and not a tweak.** `CLAUDE.md` said *"entry is
in grams"*, and the app said so to the owner in two places. All three moved together.

- [x] **Two preferences, not one.** `weightEntryUnit` defaults to **grams**; the display preference
      defaults to **kilograms** and is untouched. Reusing the display one would have moved every existing
      owner's field to kilograms at a stroke — and `2495` typed into a kilogram field is exactly the
      fat-fingered reading the *recent weighings* line exists to catch. Making it form-only instead would
      make an owner who thinks in kilograms re-choose on every weighing.
- [x] **Storage does not move.** `Int` grams on disk, verified on the phone: `1,2` typed as kilograms
      landed as `(1200, 'integer')`.
- [x] **Both separators, both directions.** `.` and `,` are accepted on input and the locale's own is used
      on output, because which one arrives is decided by the keyboard rather than the app's locale. A
      Polish phone offers a comma; refusing it would fail the ordinary case.
- [x] **The echo became the safety net and is now unconditional** — whichever unit the field is in, the
      other is spelled out underneath. `2495` entered as kilograms reads back *"That is 2 495 000 g."*,
      which is unmissable in a way a silently-accepted number is not.
- [x] The field caps kilograms at three decimals rather than rounding a fourth away silently, and
      *recent weighings* renders in the **entry** unit so the magnitude comparison stays like-for-like.
- [x] Entry text carries **no grouping separator** — it goes back into the box, and "2 495" re-parses as
      a different number. That is the one place `weightEntryText` must differ from `gramsNumber`.
- [x] Copy ×9 — 2 new strings, and `settings_weight_unit_help` reworded because it asserted the old rule.
      Gate green at **720 × 8**.
- [x] 13 JVM tests in `WeightFormatTest`. One pinned a behaviour worth keeping: `"1."` parses as 1000 g
      rather than null, so the echo holds steady mid-typing instead of blinking out and back.
- [x] **Shared, not copied.** `ui/weight/WeightAmount.kt` holds the field's whole state machine —
      the text, its unit, and the transitions — plus the `WeightUnitChips` composable. Both weight
      fields in the app use it: *Record a weighing* and the **visit editor**, which is the one that
      needed it most, since that number is usually copied off a vet's note. What is deliberately
      *not* shared is the field itself: the weighing form's box is `6e`'s oversized hero and the
      visit editor's is an ordinary optional row, and one composable forced to be both would be
      worse than either.
- [x] **The two preferences are proven not to collide** ✅ on the phone: with display set to
      **Grams** and entry to **Kilograms**, the flag banner read *"2,380 g then, 1,200 g now."*
      while the form still read *"Weight in kilograms"* with its recent-weighings row in kilograms.
      Neither preference moves the other, and Settings' own help line now says so.
- [x] **Driven on the phone**: chips default to Grams, `2495` → toggle → `2.495` with the help line,
      echo and recent-weighings row all following; `1,2` saved and stored as 1200. The visit editor
      does the same, opening in the unit the weighing form was left in — one preference, two
      screens.

## Closing the phase

When every box above is ticked: write the results into `PLAN.md`'s Phase 10 entry, tick **Phase 10**,
and empty this file back down to the standing schema gate — moving the detail into
[`phase-10.md`](phase-10.md) rather than deleting it, which is how Phase 9's record was preserved.
