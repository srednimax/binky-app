# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

**This file is the record, not the worklist.** What is still open — the boxes to tick, with the device
state and the commands to read it — lives in [`DOD.md`](DOD.md), which stays short enough to open every
session. Read the phase you are in here; read `DOD.md` to know what to do next. When something closes,
its *result* is written back into the checkpoint below and its detail deleted from `DOD.md`.

**A phase still being planned or built gets its own file** — `phase-N.md` beside this one — so that
working on it costs the phase rather than the whole history. It moves in here, or simply stops being
read, once it closes. Phases 0–5 are finished and live below; **Phases 6 and 7 are finished and stay in
[`phase-6.md`](phase-6.md) and [`phase-7.md`](phase-7.md)** rather than being copied in, and **Phases 7.5
and 8 are in [`phase-7.5.md`](phase-7.5.md) and [`phase-8.md`](phase-8.md)**.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [x] **Phase 1** — Data layer, bunnies, avatars
- [x] **Phase 2** — Weight and observations
- [x] **Phase 3** — Backup, first-run setup, photo gallery — **ships as 1.0**
- [x] **Phase 4** — Care reminders and watch — **ships as 1.1** *(closed on the build; the Console half and
  one night's evidence are carried into Phase 5)*
- [ ] **Phase 5** — Vet, medications, documents, dose reminders — **ships as 1.2**
- [x] **Phase 6** — Support contact — **ships as 1.3** — record in [`phase-6.md`](phase-6.md), not here
  *(closed on the build and the documents; Play's per-app contact email and the received-mail read are
  carried in [`DOD.md`](DOD.md) §5)*
- [x] **Phase 7** — The redesign — **ships as 1.4** — record in [`phase-7.md`](phase-7.md), not here
  *(closed 2026-08-13 on the sweep, the 244-scene matrix, lint at 0/0 and the before/after comparison; the
  **Polish capture moved to Phase 8**, which owns the locale-aware driver it needs — see
  [`DOD.md`](DOD.md) §7)*
- [ ] **Phase 7.5** — The interlude: the gain signal, licence attribution, the downsample answer and the
  locale-aware capture driver — **ships as 1.5** — planned in [`phase-7.5.md`](phase-7.5.md), not here
  *(opened 2026-08-14; it is an ordering over items already open in [`DOD.md`](DOD.md), collected because
  each is cheaper before nine languages than after)*
- [ ] **Phase 8** — Nine languages — **ships as 1.6** — planned in [`phase-8.md`](phase-8.md), not here
  *(retargeted from 1.5 on 2026-08-14: Phase 7.5 adds functionality, so `release-please` cuts 1.5 there)*

The rule is **no release before the data is safe**, which Phase 3 satisfies (ADR-0019). It replaces the
former blanket ban on shipping before every phase was complete — a rule that held the weight trend flag,
the app's one load-bearing safety signal, hostage to a document scanner. Release work is therefore not a
phase of its own; it happens at the end of each of Phases 3, 4 and 5.

## Phase 0 — Toolchain, project skeleton, docs ✅

JDK 21, Android SDK under `~/Android/Sdk`, `ANDROID_HOME` in `~/.zshrc`, Xiaomi device over USB.
Compose project scaffolded with `android create` (AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Navigation 3),
package `app.binky.tracker` — which remains the `namespace` and the source tree, but **stopped being the
`applicationId` at 3h**, where the install identity became `binky.bunny.and.rabbit.tracker`.

**Gate met:** `assembleDebug`, `test`, `lint`, and `connectedAndroidTest` all pass; the app runs on the phone.

## Phase 1 — Data layer, bunnies, avatars ✅

**Gate met:** `spotlessApply`, `assembleDebug`, `test` and `lint` pass; 29 instrumented tests pass on the
Xiaomi; the on-device checks — avatars surviving a restart and upright from the camera, delete asking once
and removing the file, a broken avatar path rendering the placeholder, the five destinations and the
switcher, archive and the read-only archived scope — were verified by hand.

Scope is deliberately narrow: **`Bunny` and `Fluffle` only.** Weights, observations, symptoms, vets,
medications and documents are not modelled here — ADR-0007 makes pre-Phase-3 schema churn free, so an entity
written before the phase that exercises it buys nothing and locks in a guess (`source`/`visitId` on weights
is already deferred to a Phase-5 migration). The consequence to accept: a Phase-1 bunny has **no records**,
so the delete-confirmation counting is built structurally against a query that returns zeros, and its honest
two-bucket proof (ADR-0004, ADR-0008) moves to Phase 2's gate.

- Room entities, DAOs, database, type converters, `AppContainer`, repositories, and a DataStore preferences
  repository holding **exactly one key** — the bunny selection. The weight display unit arrives in Phase 2,
  with the screens that read it.
- Destructive-wipe handling per ADR-0007, **preserve half only**: the database file is copied aside with a
  timestamp before a destructive migration. The blocking consent screen arrives in Phase 2, when the data
  first cannot be retyped — in Phase 1 it would fire on every entity added, to guard a bunny name and a
  birthdate, and the realistic outcome is that it gets commented out.
- `MediaFiles.kt` — the single path for persisting images, built **kind-aware** and **file-first** per
  ADR-0020: `persist(uri, kind)` with a per-kind directory and downsample spec, avatars as a blind centre
  crop at 512² JPEG q85, EXIF orientation applied to the pixels then all metadata stripped, and no orphan
  sweep. Phase 1 implements the `Avatar` kind only; the shape is fixed now so Phase 3's photos and Phase 5's
  documents extend it rather than fork it.
- Avatars alone exercise the whole pipeline (write through the helper, relative split path, placeholder on a
  broken path); the sentimental photo gallery is deferred to Phase 3 (ADR-0015) so Phase 1 keeps the media
  machinery without the extra surface area.
- Navigation shell per ADR-0015: the bunny-first top-level destinations (Home / Weight / Observations /
  Care & Meds / More) are fixed now — as stubs where a screen doesn't exist yet — because ADR-0012 requires
  the structure decided before the first screen. **One back stack**: switching top-level destination
  replaces rather than pushes, back from any of them returns to Home, back from Home exits. The visibility
  enum (`Hidden` / `ComingSoon` / `Live`) is **defined here**, so Phase 3's promotion is the one-value flip
  ADR-0015 intends rather than an introduction. The global "+" observation entry has its route and nav key
  but **renders no FAB yet** — deciding the structure and rendering it are different claims, and the app's
  primary write action is the worst one to teach the owner is inert. Every stub **renders the selected
  bunny**, so the switcher's wiring is falsifiable in Phase 1 rather than in Phase 2.
- **The switcher always opens a menu**: the active bunnies, "All bunnies" **only once ≥2 exist**, and
  "Add a bunny" always. There is **no separate bunny-list screen** — All-bunnies Home *is* the list, its
  card carrying avatar, name, age and "Lives with" now and growing into ADR-0015's vitals card in Phase 2.
  In "All bunnies" mode Home is that fluffle dashboard and Observations a combined timeline, while Weight
  refuses with a pick-a-bunny prompt (ADR-0015).
- Retire the template placeholders as real screens land: keep `Navigation.kt` / `NavigationKeys.kt` as the
  Nav3 wiring; delete `DataRepository.kt`, `MainScreen.kt`, `MainScreenViewModel.kt` and their two tests.
- Bunny add / edit / archive / delete, per ADR-0004. Avatar from camera or album. Profile fields — `name`
  (the only required one, trimmed, and duplicates allowed), `sex`, `neutered`, `birthdate` (with an
  "approximate" flag, rendered as "~2 years old" and never as a date), `breed`, `colour/markings`, and no
  target weight — per ADR-0016. **Archiving asks once**, stating that records are kept; unarchiving asks
  nothing. Deleting a Phase-1 bunny also asks **once**: the two-stage ceremony is calibrated to destroying
  history, and an avatar the owner still has in their camera roll is not history (ADR-0004).
- **Archived bunnies** are reachable from More, with unarchive and delete, and their records stay
  **readable in a deliberate read-only scope** — archiving that keeps records nobody can reach is
  indistinguishable from deleting them, which would hollow out ADR-0004.
- **Fluffle** as a first-class table with a nullable `bunny.fluffleId` FK (ADR-0008): "Lives with" is set
  when adding or editing a bunny; solo bunnies have none. The observation `groupId` is a *separate* column
  stamped per shared observation (Phase 2) and is never derived from the current fluffle, so re-bonding or
  archival can't rewrite past observations. The fluffle carries an optional custom name, "Lives with" is a
  symmetric join, and a fluffle **dissolves when it would be left with one member counting archived ones** —
  one predicate shared by editing, deleting and archiving (ADR-0008).
- **Selected-bunny state** is app-wide (ADR-0015): a `StateFlow` on `AppContainer`, persisted to DataStore
  so a Xiaomi background-kill lands the owner back on the same bunny, resolved reactively against the live
  list of active bunnies so archiving or deleting the selected bunny self-heals — falling back to the sole
  active bunny, else "All bunnies", else the add-a-bunny empty state. Healing is **resolve-on-read with no
  write-back**: DataStore holds the owner's last explicit choice, so unarchiving a bunny restores it. A
  third state, `Archived(id)`, is entered only from the archived list, is read-only, and is **never
  persisted**.
- Missing-media placeholder, required by ADR-0005.
- All user-facing text in `strings.xml` from the first screen, counts through `<plurals>` (ADR-0013). The
  delete confirmation's record counts are the app's first plurals case and the fluffle's "Thumper & Clover"
  its first list join; neither may be concatenated.

### Checkpoints

Dependencies run one way, and the Xiaomi's split-APK confirmation prompt makes `connectedAndroidTest`
something to run at boundaries rather than per commit.

1. **1a — Data layer.** Entities, converters, DAOs, `AppContainer`, repositories, DataStore, the pre-wipe
   preserve. **The fluffle logic is proven here, before any UI exists**: instrumented Room tests, plus a
   pure JVM unit test for the selection resolver, written as `(persisted selection, active bunnies) →
   resolved selection` so every state is testable with no Android involved.
2. **1b — Media pipeline.** `MediaFiles` and `MediaKind`. Its tests are **instrumented, not JVM** —
   `Bitmap` and `ExifInterface` decoding are framework — with a fixture JPEG carrying orientation tag 6 in
   `androidTest/assets` asserting the written pixels come out upright.
3. **1c — Nav shell.** Destinations, back-stack policy, switcher, All-bunnies behaviour, stubs, visibility
   enum, template deletion. **No Compose UI tests**: this UI churns through Phases 2-3 and ADR-0012 puts
   visual work last, so it is verified by hand on the phone against the gate.
4. **1d — Bunny CRUD.** List, add, edit, avatar picking (needs a `FileProvider`), "Lives with", archive,
   delete, archived list and the read-only scope. Leans on 1a's tests; verified by hand.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 1a and
1b, the two that add instrumented tests.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **1a**: ADR-0004,
0007, 0008, 0016. **1b**: ADR-0020, 0005. **1c**: ADR-0012, 0015. **1d**: ADR-0004, 0008, 0015, 0016.

**Gate:**

- Two bunnies with avatars survive a restart; a camera-taken avatar is **upright**, and matches an
  album-picked one.
- Deleting a bunny asks **once**, names its avatar, and removes the file; the two-stage path passes an
  instrumented test fed fake counts.
- A deliberately broken avatar path (`adb shell run-as app.binky.tracker rm …`) renders the placeholder,
  never a crash.
- All five top-level destinations exist; **switching bunny visibly changes every per-bunny stub**; Weight
  shows the pick-a-bunny prompt under "All bunnies" while Home and Observations do not; "All bunnies" is
  absent while only one bunny exists.
- Fluffle, as instrumented tests: symmetric join writes both members onto one `fluffleId`; joining someone
  who already lives with a third bunny joins the **existing** fluffle rather than forming a rival pair;
  editing a member out of a pair reverts the survivor to solo and removes the row **in one transaction**;
  archiving a member changes nothing; deleting from a trio that includes an archived member leaves the row
  standing.
- Selection resolver unit tests cover heal-on-archive, heal-on-delete, restore-on-unarchive, and the
  `Archived(id)` scope.
- Archiving asks once and says records are kept; the archived bunny is reachable, read-only, from More.
- No user-facing string is hardcoded; counts use `<plurals>`.

## Phase 2 — Weight and observations

Weight entry, the chart and the trend flag; observations with symptoms; ADR-0007's blocking wipe screen; and
the delete confirmation's record counts becoming real. The reasoning behind all of it lives in the ADRs named
per checkpoint below — this section is what lands, in what order.

- **Weight entry** defaults to now, with the date/time editable and **back-dating allowed** (weigh in the
  morning, log in the evening); **future timestamps are rejected** with the reason stated rather than silently
  clamped. Existing entries are editable and deletable — **value as well as timestamp** — behind **one**
  confirmation, since ADR-0004's two-stage ceremony is calibrated to destroying a bunny's whole history.
- The **trend flag**, the app's single load-bearing safety signal: a level trigger at 5 % below baseline,
  **interval-independent**, noise floor `max(20 g, 2 % of baseline)`, delta always in grams, framed "worth a
  closer look" and never as a diagnosis, derived on read, auto-clearing, with an episode-scoped
  acknowledgment and **no push notification** (ADR-0001), over a trailing-median baseline (ADR-0021). It is
  not evaluated at all for an archived bunny (ADR-0004).
- The **chart** plots real timestamps, with a 30 d / 90 d / 1 y / All range selector that is **display-only**
  and three empty states (ADR-0022).
- **Observation entry** with every field optional, droppings amount landing on **"not checked"** rather than a
  silent "normal", and a **"Log a healthy day"** shortcut affirmative on the glance-level facts only
  (ADR-0001). Shared observations across a fluffle, one row per bunny, with participant correction and a
  snackbar naming who was covered (ADR-0008).
- **Symptoms** as a seeded table with owner-added rows and an explicit "checked, none seen" state (ADR-0010).
- **Breed becomes a searchable picker with add-your-own** — a Phase-1 field finishing its UI here, not new
  scope. Deliberately **not** ADR-0010's seeded table: breed is asked no "how often" question, so it stays a
  text column and the suggestion list is a query.
- The **blocking wipe screen** — ADR-0007's consent half, which lands here because the database first holds a
  weight series that cannot be retyped. The preserve half already exists from Phase 1.
- Warnings derive from recorded observations, **never from silence** (ADR-0001).

### Checkpoints

Six rather than Phase 1's four, because this is roughly twice the phase: weight and observations each split
into a data layer proven by tests and a UI verified by hand, and the two pieces easiest to entangle with
everything else — the trend math and the chart — stay separate so they can be reviewed for what they are.
Dependencies run one way, and the Xiaomi's split-APK confirmation prompt still makes `connectedAndroidTest` a
boundary run rather than a per-commit one.

**Two schema bumps, two wipes** — version 2 at 2a, version 3 at 2e. Both are free under ADR-0007 and both are
a chance to exercise the consent screen on a real device. The consequence stands for the whole phase: until
Phase 3 the phone's database is disposable, so weights worth keeping are written down outside the app.

1. **2a — Weight data layer, and the consent half of the wipe guard.** ✅
   - `WeightEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `grams: Int` (house rule — never a float),
     `recordedAt: Instant` (the moment on the scale, back-datable), `createdAt: Instant`. Indexed on
     `(bunnyId, recordedAt)`. No `source` / `visitId`: deferred to a Phase-5 migration.
   - `TrendAcknowledgmentEntity` — the flag's **only** persisted piece: `bunnyId` as primary key **and** an FK
     to `bunnies` `CASCADE` (at most one live episode per bunny), `weightId` FK `CASCADE`, `grams`,
     `acknowledgedAt`. Both value columns earn their place, and the direct `bunnyId` FK replaces a two-hop
     cascade accident (ADR-0001). A table rather than columns on `bunnies` so that discard-on-delete is a
     database constraint rather than a rule someone has to remember.
   - `WeightDao` / `WeightRepository`: the series as a `Flow` in the **stated total order**, insert / update /
     delete. **No "the *n* weighings prior to an instant" query** — windowing belongs to 2b's pure function
     and is defined in exactly one place (ADR-0021).
   - `insert` re-reads the series, evaluates the trigger and **discards a stale acknowledgment**; `update` and
     `delete` discard unconditionally. The invariant to hold: *a stored acknowledgment row implies the raw
     trigger was true as of the last weight write* (ADR-0001). The FK is a backstop, not the mechanism.
   - Schema → **2**, and **the consent screen lands in the same commit as the bump** — this is the first wipe
     ADR-0007's consent half exists for, and shipping the bump a commit earlier would spend it.
   - The screen is **honest about having no alternative**: the copy has already been taken, so it states what
     is about to be destroyed, where the copy is (`files/preserved/bunny-<timestamp>.db`), and offers one
     forward button. What ADR-0007 forbids is the *silent* wipe, not the unavoidable one.
   - It has to block **before Room opens the file**, so the guard becomes **structural**: `preserveBeforeWipe`
     and the pending-wipe state move to `Application.onCreate`, `AppContainer` goes behind a `lazy` forced only
     on consent, and consent then opens the database explicitly (ADR-0007). `selectedBunny` keeps
     `SharingStarted.Eagerly` — the guard no longer depends on nothing collecting.
   - `preserveBeforeWipe` names the copy from **`databaseFile.lastModified()`**, not `Instant.now()` (which
     stays as the injected default for tests), so a hesitating owner relaunching overwrites one copy rather
     than minting another each time.
   - Weight display unit becomes `AppPreferences`' **second key** — kg by default, grams the alternative;
     entry is in grams either way. Its toggle lands in 2c, since a preference with no setter is a constant
     with a DataStore round-trip.
   - `BunnyDao.recordCounts` gets its first real SQL (weights are sole-owned), which makes 1d's structurally
     built **two-stage delete ceremony reachable for the first time**. It reaches final form in 2e.
   - Tests, instrumented: weights cascade with their bunny; an acknowledgment row disappears both with the
     weight it names and with its bunny; a stale-version database file survives `AppContainer` construction
     **byte-identical**, and relaunching before consent adds no second preserved copy. The out-of-order
     windowing test lives in **2b**, as JVM arithmetic.
2. **2b — Trend math.** ✅ Pure JVM, no Room and no Android — `deleteConfirmationFor` is the precedent: a
   decision function in `data/` whose test reads as a table of cases.
   - Input is the bunny's **whole series** as a plain list of `(id, grams, recordedAt)` plus the current
     acknowledgment; output a sealed result that also reports a **stale watermark** for 2a to act on.
     Deliberately **not** Room types, so the tests stay arithmetic.
   - **This function owns the windowing**, not the DAO: it sorts by the total order, takes the latest reading
     as *current* and the priors beneath it (ADR-0021).
   - The constants live in this one file with ADR-0001's reasoning in comments, and the baseline estimator
     with ADR-0021's — including that the floor cannot bind in the trigger above a 400 g baseline, so its real
     job is the re-raise bar, and a comment forbidding the stale-prior "fix".
   - The project's heaviest unit tests, as a case table: a long gap before an acute drop still fires; one
     prior never fires and two do; at exactly two priors a fat-fingered low prior does not suppress
     (`2500, 250` must not yield a 1375 g baseline); the floor behaves at both ends of the 1.1 kg – 6.5 kg
     range and **binds in the trigger only on a ~300 g kit**, so the `max` cannot be simplified away; a
     stabilized-low bunny auto-clears as the baseline catches up; acknowledge → further slide re-raises,
     acknowledge → wobble within the floor stays quiet; a trigger going false discards the watermark so the
     next episode fires from scratch; a back-dated insert into the middle of history changes the current flag
     and never resurrects a past one; ties in `recordedAt` resolve by the stated total order; rows arriving out
     of order window correctly; and the **gap blind spot** as a green test — after a long gap the second
     post-gap reading does not fire and the third does (ADR-0021).
3. **2c — Weight entry, history, the flag surfaced, and Settings.** ✅
   - A **`WeightEntry(bunnyId, weightId: String? = null)` nav key** — null adds, non-null edits, mirroring
     `BunnyEditor`. This **closes a Phase-1 omission rather than adding scope**: `NavigationKeys.kt` promises
     every route exists from Phase 1 and this one didn't. The global "+" stays **observation-only**
     (ADR-0015) and is never the way in.
   - Entry defaulting to now, date/time editable, back-dating allowed, future rejected with the reason stated.
     On an **exact `recordedAt` collision** for that bunny, offer *replace* or *add a second*, defaulting to
     replace, so the commonest correction does not displace a real prior out of the baseline window (ADR-0021).
   - The per-bunny history list, every row editable and deletable — **value as well as timestamp** — behind
     **one** confirmation.
   - One flag composable in **three** hosts: a dialog straight after any weight write when the flag is
     **visible and unacknowledged** — dismissing it is explicitly *not* acknowledging, and it applies to edits
     and deletes as well as inserts, since correcting a baseline weight can deepen the drop — plus the banner
     on the weight screen and on Home's card. Grams, dated, "worth a closer look", the long-gap framing when
     the gap warrants it, the vet-diet line, an acknowledge action, and **no notification**. Built with room
     for a **second action**, since Phase 4 adds *Start a watch* to the same composable.
   - Home under "All bunnies" is **one vitals card per active bunny**, so it is *N* series reads and *N* trend
     evaluations per emission. Stated, not optimised — at three rabbits it is free, and "derived on read" plus
     "a card each" is the pairing that stops being free quietly.
   - A minimal **Settings screen**, flipping More's `more_settings` row from "coming soon" to live: the weight
     display unit, and a **preserved copies** row listing ADR-0007's copies with a share action (the `.db` plus
     its `-wal`/`-shm` sidecars) and a per-file delete. Same shape as `ArchivedBunnies`: a detail route off
     More. Settings has to exist before 1.0 regardless, since ADR-0013's language switcher needs it.
   - **One weight formatter, in one place**, so kg-vs-grams and "changes are always shown in grams" are
     expressed once rather than re-derived at the axis, the row and the card.
   - The **weight half of the `BuildConfig.DEBUG` sample-data action**, writing **through the repositories** so
     it cannot seed rows the app itself could not produce: a year of uneven, back-dated weighings, a
     fat-fingered entry, a long gap before an acute drop, and a tied `recordedAt`. It lands here rather than at
     2f because **2d needs it** — hand-typing a year of back-dated entries through a date picker is the toil
     that gets skimmed, and an identical fixture is what makes the 2d and 2f chart reviews comparable. It also
     exercises 2a's insert-time discard a few hundred times on a real device.
   - In the `Archived(id)` scope the history renders read-only, with no add / edit / delete affordances, and
     the flag is **not evaluated** (ADR-0004).
   - Weight stops being a stub and still refuses "All bunnies". No Compose tests (ADR-0012, as in 1c); the
     logic beneath is covered by 2b.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (64 unit tests) and `lint` pass. Exercised on the
     Xiaomi: a future time refused with the reason stated; an exact-timestamp collision offering *replace* and
     updating the row rather than adding a second; a corrected weight and a deleted row each clearing the flag
     they caused; **editing an unrelated baseline discarding the acknowledgment and raising the dialog on an
     edit**; an archived bunny showing a year of history with no add/edit/delete and **no flag** across a
     −500 g drop; and a preserved copy listed, shared with its `-wal`, then deleted with it.
4. **2d — The chart.** ✅ Vico enters `libs.versions.toml` here and nowhere earlier. Real `recordedAt` on the
   x-axis; range selector 30 d / 90 d / 1 y / All defaulting to 90 d, held in the `ViewModel` and not
   persisted; **three** empty states, the third naming the last weighing's date and offering one tap to *All*;
   no auto-widening; range **display-only**, so the flag can render above an empty chart and that composition
   gets verified by eye (all ADR-0022). Read-only in the `Archived(id)` scope.
   - **One session** is the time box for getting Vico onto the pinned Compose BOM. If it does not build, the
     fallback is a hand-rolled `Canvas` line chart; the Compose BOM is **not** moved to satisfy a chart. Vico
     is accepted only if it renders a real time axis without fighting it — a library that builds but wants an
     index axis is rejected anyway (ADR-0022).
   - Its own checkpoint on purpose: a new charting dependency either drops straight in or eats a day, and
     neither outcome should be tangled up in the review of the entry flow.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (73 unit tests, 9 new) and `lint` pass. Vico 2.1.3
     is **accepted, not fallen back on** — its `series(xs, ys)` takes arbitrary `Number` x values, so this is a
     real value axis and not an index one, and the pinned Compose BOM did **not** move (Vico's POM imports BOM
     2025.05.01, ours outranks it, `compose.ui` still resolves to 1.11.2). Three of Vico's defaults had to be
     overridden, each found by looking at the phone rather than by the compiler and each commented where it
     lives: an explicit `getXStep` (the default GCD-of-gaps collapses on irregular timestamps, and the fixture's
     tied `recordedAt` contributes a gap of 0), a y-axis that is **not** zero-anchored but fitted to the window
     with a floor of 10 % of the heaviest reading (the default drew a real series as a flat line pinned to the
     top, where a −40 g drop and a −2 g wobble looked identical), and x-domain padding so the newest weighing's
     date label is not clipped at the plot edge.
   - Exercised on the Xiaomi against 2c's fixture: at 90 d the acute −500 g drop and the fat-fingered entry are
     both unmistakable, with real weekly date ticks; at *All* that same drop compresses to a wiggle over the
     full year, which is exactly why the selector exists and proves the filtering; the `Archived(id)` scope
     charts with no add / edit / delete affordances and **no flag**; and all three empty states, the third
     naming the last weighing's date and its one tap to *All* switching the selector and redrawing.
5. **2e — Observation data layer.** ✅ Schema → **3**.
   - `ObservationEntity`, one row per bunny (ADR-0008): `id`, `bunnyId` FK `CASCADE`, `groupId: String?`
     (non-null only when shared), `recordedAt`, `createdAt`, the tray-level fields (droppings amount / size /
     form, cecotropes), the individual ones (appetite, mood, activity, water, note) and
     **`symptomsChecked: Boolean`**, non-nullable, so "looked, none seen" is distinguishable from "never
     checked" (ADR-0010).
   - **Sharedness is `groupId IS NOT NULL`, never a count of rows sharing it**, and there is deliberately no
     `observedTogether` column (ADR-0008). Converting a solo observation to shared mints a `groupId` and
     back-fills it onto the existing row, inside the transaction that is already there.
   - Every vocabulary column is a **nullable enum stored by name**, and `null` *is* "not checked" — no
     `NOT_CHECKED` entry, or absence gets two spellings.
   - `SymptomEntity` (ADR-0010): `id`, `key: String?` for built-ins with a **unique index** (which the
     `INSERT OR IGNORE` reconciliation needs to have any conflict to ignore), `label: String?` for owner-added
     rows, `hiddenAt: Instant?`. No `ownerCreated` flag. Seeded on create, reconciled on open, with the
     case-insensitive add-time duplicate check and unhide-on-match. `ObservationSymptomEntity` joins them on a
     composite key, `CASCADE` from the observation and **no cascade from the symptom** — hiding a symptom is
     not deleting it.
   - `ObservationRepository` owns the shared write as **one transaction**: one `groupId`, tray-level facts
     written identically onto every participant, individual fields blank. Editing a tray-level field is an
     `UPDATE … WHERE groupId = :groupId`; editing an individual one touches one row. `addParticipant` and
     `removeParticipant`, where a correction dropping the group to one row **clears the survivor's `groupId`**
     while deleting a *bunny* does not (ADR-0008).
   - `recordCounts` reaches its final form, bucketed by **survivorship, not provenance** (ADR-0004): shared
     means a grouped observation with `EXISTS` at least one row belonging to a *different* bunny; a grouped
     observation where this bunny is the last participant is destroyed, so it counts as sole-owned. Archived
     bunnies count as survivors. `deleteConfirmationFor` is untouched — either bucket being non-zero still
     yields `TWO_STAGE`, so only the numbers get honest.
   - Tests, instrumented: the shared write lands one `groupId` and identical tray facts on every participant;
     editing a tray fact moves every row and editing a mood moves one; deleting one participant leaves the rest
     marked observed-together **while correcting the participants down to one clears the marker** — the paired
     test *is* the distinction; deleting a bunny cascades its observations and symptom links but no symptom;
     the last surviving participant's observations count as sole-owned, and an archived housemate keeps them
     counted as shared; the seed runs once, survives a wipe, and tops up on open **without inserting the whole
     built-in list again** or resurrecting a hidden symptom; a hidden symptom still resolves on an old
     observation. JVM: the healthy-day field set as a pure function, asserting `symptomsChecked`.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (79 unit tests, 6 new) and `lint` pass; **65
     instrumented tests** pass on the Xiaomi, 26 of them new. Two decisions worth naming, because neither is
     forced by the ADRs and both would be easy to get wrong later. First, the **seed hangs on `onOpen`, not
     just `onCreate`**: after ADR-0007's destructive migration Room drops and recreates the tables inside
     `onUpgrade`, so `onCreate` never fires and a seed hung only on it would land the owner on an empty
     picker at exactly the launch a wipe just happened. `onCreate` is kept anyway, as documentation that
     cannot go stale. Second, `add` writes the **individual** facts onto every participant, not just the tray
     ones, because *looked, no symptoms seen* is an individual fact read from the same glance as the tray —
     a shortcut covering a bonded pair has to be able to claim it for both. Anything genuinely per-bunny goes
     through `updateIndividual`, which touches one row.
   - Also decided here rather than deferred: `delete` removes **every** row of a shared observation ("that
     observation was wrong"), which is a different event from `removeParticipant`'s "this bunny wasn't in
     it" — keeping them apart is what stops 2f's confirmation dialog having to guess which the owner meant.
     The built-in symptom **labels stay out of `data/`**: `add` takes the resolved built-ins as a parameter,
     so the duplicate check sees the owner's current locale (ADR-0010) and the data layer stays free of
     `R.string`, as it was before this checkpoint. The labels themselves land in `strings.xml` with the
     picker, at 2f.
   - Exercised on the Xiaomi, on the real bump: the consent screen appeared naming *format 2 → format 3* and
     the copy at `files/preserved/bunny-20260726T073547Z.db`, which was on disk with its `-wal` and `-shm`
     before anything was destroyed. Consenting wiped and reopened the file at `user_version` **3** with all
     six tables — and the symptom table holding **exactly 13 rows**, which is the `onOpen` decision above
     confirmed on the path that actually exercises it. The instrumented test can only stand in for that path
     by emptying the table by hand; this is Room's own destructive migration, where `onCreate` provably never
     fired and the picker came back full anyway. Relaunching left the count at 13 and added no second
     preserved copy.
6. **2f — Observation UI, the "+", and the healthy day.** ✅
   - The global "+" FAB **finally renders** — Phase 1 settled its route and deliberately left it inert. On Home
     and Observations, not on More.
   - The full form: every field optional, droppings amount landing on **not checked**, participants
     pre-selected from the current fluffle's *active* members and editable, the symptom picker with
     add-your-own and an explicit **"none seen"** tick, note, back-dating and future-rejection on the same
     terms as weight. Participant editing on an existing observation, which is the durable review path behind
     the shortcut's snackbar (ADR-0008).
   - Pre-selection is built as a **filter with a stated reason per exclusion**, even though Phase 2 excludes
     nobody — so Phase 4's watch exclusion is one predicate added rather than a rework.
   - Under **"All bunnies"** the "+" and the healthy day **ask which bunny first**, then apply the ordinary
     fluffle pre-selection; the single-bunny path is untouched and stays one tap (ADR-0008).
   - The timeline grouped by day **for display only**, shared entries naming who they covered and rendering
     "no symptoms seen" where that was affirmatively recorded. Under "All bunnies" it is the combined
     timeline, **collapsing rows that share a `groupId`** into one entry — tray facts once, individual fields
     per named bunny — as a pure display function with a JVM test (ADR-0008).
   - Edit and delete per observation behind one confirmation, respecting the tray/individual split.
   - **"Log a healthy day"** — one tap, recording droppings **amount, size and form** as normal plus
     cecotropes eaten and `symptomsChecked`, leaving the graded fields "not checked", with the button naming
     what it records and a snackbar naming who it covered with **Undo**. All three droppings sub-fields
     because they are read from the same glance at the same tray. The Watch-based exclusion is Phase 4's and is
     not stubbed here.
   - A **flagged bunny is not excluded**, but the snackbar **names the flag** — *"Logged a healthy day for
     Bijou (weight flag) and Nugget"*. The flag is about **weight**; a bunny losing weight with entirely
     normal droppings is real and useful data, and excluding would add friction to the one-tap path over
     exactly the stretch that most wants daily observations (ADR-0008, ADR-0001).
   - **Breed gets that same picker**, single-select with a search field — the one bunny-editor item riding
     this checkpoint, because the picker is built here and building it twice is the alternative. The list is
     the built-in breeds from `strings.xml` ∪ `SELECT DISTINCT breed` over **all** bunnies including archived,
     "Mixed / unknown" first since that is most pet rabbits, and an unmatched entry is **accepted as typed**
     rather than refused — then it is in the list for the next bunny, which is the whole of "add your own".
     Search is why the two pickers share code: 13 symptoms do not need it, ~50 breeds do.
   - `bunnies.breed` stays a **text column** — no `BreedEntity`. ADR-0010's reason a vocabulary earns a table
     is that the "how often has this happened?" count must key off a stable id; breed is a profile fact on
     Home's card and is counted by nothing. Two costs accepted in exchange for no schema bump and no new
     table: a breed drops out of the suggestions once no bunny carries it (the reuse that matters — a second
     bunny of the same breed — still works, because the first one carries the string), and a built-in name is
     stored as the literal text picked, so it does not follow a language switch (ADR-0013). `colour` is the
     obvious second user of the picker and is **not** wired to it here.
   - The **observation half of the sample-data action**: the two bunnies it needs, a shared observation across
     them, symptom links. Re-running it after 2e's wipe regenerates 2c's weight fixture identically, which is
     what makes the 2d and 2f chart reviews like-for-like.
   - In the `Archived(id)` scope the timeline renders read-only, with no "+", no healthy day and no per-row
     edit or delete (ADR-0004).
   - Observations stops being a stub, and Home's card completes its growth into ADR-0015's vitals card: last
     weight, last observation, the flag.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (97 unit tests, 18 new) and `lint` pass, lint with
     zero issues. No instrumented tests are added here, so `connectedAndroidTest` is not a gate for 2f; 2e's
     65 still stand, the repository changes being additive. The three new JVM suites are `buildTimeline`'s
     collapse (a shared observation appearing **once** with both bunnies named, tray facts once and moods
     apart, sharedness surviving down to a lone participant, day grouping preserving the query's order),
     `preSelectParticipants` (the subject always a candidate — including when the subject is *itself*
     archived, since the read-only scope is what stops that write, not this function — and an archived
     housemate landing in `excluded` with a reason rather than silently missing), and the built-in symptom
     keys against their labels in both directions.
   - Three decisions worth naming, all three found by driving the app rather than by a test. First, **the
     form writes its individual facts through `updateIndividual` and passes `add` the tray facts only.**
     2e's `add` deliberately spreads individual facts across every participant, which is right for the
     healthy day and wrong for the form: the form only ever showed the *subject's* individual fields, so a
     shared observation was recording a mood for a housemate the owner had said nothing about. It rendered
     perfectly — two bunnies, one mood, no error anywhere — which is exactly why it had to be caught on the
     phone. Second, **the shell owns the snackbar host, not the screen.** With the "+" FAB in the shell
     `Scaffold` and the host in the screen's own `Box`, the two laid out in ignorance of each other and the
     FAB covered the healthy day's **Undo** — visible, unpressable, and the one control ADR-0008 puts behind
     that shortcut. A Scaffold lifts its FAB clear of its own snackbar, which only helps if it owns both.
     Third, **a card names the *other* participants, not everyone.** "Observed together with Bijou" on
     Bijou's own timeline names her back to herself; and when nobody else resolves — the housemate deleted,
     or archived and out of scope — it reads a plain, un-named **"Observed together"**, because ADR-0008
     wants the marker without a tombstone of the bunny who is gone.
   - Exercised on the Xiaomi against the sample fixture: a future-dated observation refused with its reason
     while the form stayed put; an untouched tray recording nothing at all and the timeline printing no line
     for it, rather than "not checked" on every row; the healthy day's snackbar reading *"Healthy day
     recorded for Bijou (weight flag) & Nugget."* with an Undo that removed the whole entry; under "All
     bunnies" the "+" asking which bunny and the seeded shared observation appearing once with the tray read
     once and only Bijou hunched. The pair that has to differ, differs: **correcting** the participants down
     to one left a solo entry reading just "Bijou", while **deleting** a participating bunny left the
     survivor reading "Observed together". The breed picker took an unmatched *"Harlequin lop"* as typed. The
     archived scope showed the banner, the timeline, and no "+", no healthy day and no per-row edit or delete.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 2a and
2e, the two that add instrumented tests; `lint` at the gate.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **2a**: ADR-0007,
0001, 0004. **2b**: ADR-0021, 0001. **2c**: ADR-0001, 0021, 0004, 0012. **2d**: ADR-0022, 0012. **2e**:
ADR-0008, 0010, 0004. **2f**: ADR-0008, 0010, 0001, 0013.

**Gate:**

- Trend-math unit tests pass: interval-independent level trigger, trailing baseline of the 3 prior weighings
  excluding the current one, the ≥ 2-prior firing gate, the noise floor, the gram delta, and the
  auto-clear / acknowledge / re-raise transitions — **including that a long gap before an acute drop still
  fires**, and that after a long gap the second post-gap reading does not fire while the third does.
- At exactly two priors the baseline is the higher of the two: `2500, 250` does not yield a 1375 g baseline
  and does not silence a later drop.
- The noise floor binds in the trigger only below a ~400 g baseline; the kit case is covered, so the `max`
  cannot be dropped without a red test.
- A recovered episode cannot silence a new drop: acknowledge a flag, log a weight that clears the trigger,
  then log the original low weight again — it fires.
- Correcting a mistyped weight clears the flag it caused and restores the baseline; deleting a duplicate
  weighing does the same; either one also discards an acknowledgment taken against it. **Editing an
  unrelated weight discards it too** — including a baseline weight whose correction deepens the drop, which
  raises the flag dialog on an *edit*.
- Re-entering a weight at a timestamp that already has one offers to replace it rather than silently adding a
  second row to the baseline window.
- Constructing `AppContainer` over a database file at a stale schema version leaves that file byte-identical,
  and relaunching before consenting does not add a second preserved copy.
- The blocking wipe screen appears on a real schema bump, names the preserved file, the file is there, and
  Settings can share it off the phone afterwards.
- The chart is time-correct with deliberately uneven and back-dated dates, and switching range never changes
  whether the flag is showing. A range holding no weighings says so and names the last weighing's date rather
  than reporting no data, and the flag still renders above it.
- A future-dated weight is rejected, in both the weight and the observation forms.
- An untouched droppings field records "not checked", not "normal".
- "Log a healthy day" records the glance-level fields, leaves the graded ones "not checked", records
  **no-symptoms as an affirmative fact distinguishable in the database from not having checked**, and names
  the bunnies it covered in a snackbar that can be undone.
- Under "All bunnies" the "+" asks which bunny before opening the form, and a shared observation appears
  **once** in the combined timeline, naming both bunnies.
- Correcting a shared observation's participants down to one leaves a **solo** observation, while deleting a
  participating bunny leaves the survivor still reading "observed together".
- An archived bunny holding a year of weights and a drop that would flag shows its history and chart, offers
  no way to add or edit anything, and shows **no trend flag**.
- Deleting a bunny that has weights and shared observations shows **two** confirmations, with the two buckets
  counted separately and correct pluralisation at 1 and at 3; the shared observations survive for the other
  bunnies, still marked observed-together. Deleting the **last remaining participant** counts those
  observations as destroyed, not as surviving.
- Opening the app twice does not double the built-in symptom list.
- An empty database produces no warnings.
- No user-facing string is hardcoded; counts use `<plurals>`, and the built-in symptom labels resolve through
  `strings.xml` rather than being stored.

**Gate met:** `spotlessApply`, `assembleDebug`, `test` (97 unit tests) and `lint` pass, and the **65
instrumented tests were re-run on the Xiaomi after 2f** rather than inherited from 2e — 2f left the
repository additive, but a phase gate that leans on instrumented proof should not take that on trust.

Two things the checkpoints had left owing were closed by hand here. First, **the two-bucket delete ceremony
was finally driven on a real device** — Phase 1 built it structurally against a query returning zeros and
deferred the honest proof to this gate. Deleting a sample-data bunny showed two dialogs, the first offering
archiving as the alternative and the second naming the buckets separately: *"38 records kept only for this
bunny are destroyed"* beside *"2 shared entries stay, for the other bunnies they covered"*. Both plural forms
were then exercised at the boundaries in one dialog, on a bunny built to have exactly three weighings and one
shared observation: *"3 records … are destroyed"* against *"1 shared entry stays, for the other bunnies **it**
covered"* — the verb and pronoun agreement is why these are `<plurals>` and not a count spliced into one
string. Completing that delete left the observation on the survivors' timeline reading *"Observed together
with Nugget"*: still shared at two participants, and with no tombstone for the bunny that is gone.

Second, **an empty database produces no warnings**, checked on a freshly cleared install: every destination
shows *"No bunnies yet"* and an invitation, and a bunny with no records reads *"None recorded yet"* rather
than anything inferred. The weight screen states the reason it is silent — *"Three are needed before a trend
can be judged at all"* — which is ADR-0001's rule surfacing as copy rather than as an absence.

Incidental confirmations from the same session, each of which had only been claimed at a checkpoint: the
exact-timestamp collision dialog offering *Replace it* or *Add a second*; history deltas rendering as
`+10 g`; the trend flag on Home carrying the vet-diet line and no notification; and a bunny joining a bonded
pair joining the **existing** fluffle rather than forming a rival one.

Lint is honest rather than silent: **0 errors, 19 warnings, 4 of them in project code**, and all four are
standing decisions rather than debt. `OldTargetApi` is `targetSdk` 36 held deliberately (CLAUDE.md);
`ObsoleteSdkInt` on `mipmap-anydpi-v26` is the AGP template's own folder; and `backup_rules.xml` and
`data_extraction_rules.xml` read as unused because `AndroidManifest.xml` sets `allowBackup="true"` without
yet referencing either — **that pair is Phase 3's first piece of wiring** (ADR-0005), so the warning is a
correct description of an unfinished phase and is left standing until it is.

## Phase 3 — Backup, first-run setup, photo gallery — ships as 1.0 ✅

Moved ahead of vet/meds: by the end of Phase 2 the app holds irreplaceable data with no way off the device.
That is also why this phase is 1.0 (ADR-0019) — the data being safe is the whole precondition for having
users, and everything after it is additive.

**1.0 ships to the internal testing track**, not to production. Going public is a later decision that costs
no engineering at all — it costs twelve testers and a fortnight.

**Register the Play developer account at the *start* of this phase** (ADR-0009), and start recruiting
testers then too — but the two buy different things, and only one of them can run in parallel.
Registration and the internal track are engineering unblockers, available the day the $25 clears. The
**closed-testing prerequisite** — 12 testers opted in continuously for 14 days at the time of writing,
which is a number to re-read in the Console rather than trust — gates **production access only**, is
satisfied by a **closed** track and not by the internal one, and **cannot be started early**: a closed
track means real installs on other people's phones, and ADR-0007 attaches the migration obligation the
moment a schema version reaches one. Opening it at 3a would cost either a hand-written 3 → 4 migration on a
schema still moving, or twelve bricked installs the day 1.0 lands. So the clock starts at 1.0.1, and what
genuinely runs in parallel with the engineering is the **recruiting**, which is the long pole regardless.

- Photo gallery, moved here from Phase 1 (ADR-0015): per-bunny lazy grid, full-screen pager, captions. It
  lands alongside backup because photos are the sentimental bulk excluded from Auto Backup and covered only
  by the "Everything" manual scope — and because without it, at 1.0, all three export scopes are
  **byte-identical**: `documents/` stays empty until Phase 5, so photos are the only thing that makes the
  scope design and ADR-0005's media merge falsifiable, in the release whose one job is that backup works.
- **Photos are the least-protected data in the app**, and that is accepted rather than fixed: no automatic
  backup, absent from Essential and Records, present only in "Everything". The alternatives were weighed and
  rejected — the shared MediaStore forks ADR-0020's pipeline, breaks the uuid identity the restore merge
  depends on, and needs a storage permission at `minSdk` 26; admitting photos into the agent's set risks the
  all-or-nothing quota taking the database down with them. What is **not** accepted is the gap being silent:
  first-run setup and Backup settings say so in words, which is ADR-0001's rule pointed at the one directory
  the net does not cover.
- Auto Backup via a **custom `BackupAgent`** (ADR-0005): checkpoints the WAL, includes database, preferences
  and avatars unconditionally, excludes the photo gallery and `preserved/`. **The document admission ceiling
  and the one-time exclusion notification move to Phase 5**, with the documents that make them exercisable —
  at 1.0 `documents/` is empty, so the ceiling admits nothing and the notification cannot fire, and building
  the app's first notification channel to carry it would contradict 3f asking for no notification permission.
- **The backup status line cannot be allowed to lie in either direction** (ADR-0005). Absence of a marker
  is rendered in words — *"No automatic backup has been recorded on this phone"*, with a button into system
  backup settings — never as a blank, which reads as a working net and is ADR-0001's silence failure applied
  to backup. `onRestoreFinished()` **clears the marker**, or a restore carries the old phone's timestamp
  onto a device that has never backed up anything. Past **14 days** the status says it is stale rather than
  showing a bare date.
- Manual export at the three scopes — Essential / Records / Everything — via the share sheet. **Preferences
  travel in all three**, from Essential upward: they are a few hundred bytes, the agent already carries them,
  and a restored phone missing the display unit, the selected bunny and the chosen backup scope is subtly
  wrong in ways that read as bugs rather than as missing data. The **remembered-folder destination is cut to
  1.1** — it saves two taps rather than making export automatic, and it carries the plan's biggest
  unverified assumption (whether Google Drive's provider accepts writes); it belongs beside the recurring
  export reminder that would make it worth something.
- Restore (ADR-0005): a full database replace but a **media merge** (keyed by relative uuid path, so an
  Essential restore keeps photos already on disk), gated behind an explicit confirmation and a pre-restore
  snapshot, stating honestly what the file contains — **read from a manifest inside the zip, not from the
  filename**, which is the one part of a file an owner can trivially change.
- First-run setup, **two steps at 1.0**: add first bunny (skippable) → backup scope. ADR-0006's reminders
  opt-in moves to 1.1 with the reminders themselves — 1.0 has nothing that posts a notification, and Android
  allows only two denials before the permission is refused for good. The backup step also **asks whether
  system backup is switched on**, with a deep link into Android's settings — the app cannot detect it, and
  this is the one moment the owner is already thinking about it.
- Top-level destinations get their **visibility state** (`Hidden` / `ComingSoon` / `Live`) set for real
  before this ships — the enum was defined in Phase 1, so this is the one-value flip, not an introduction —
  since 1.0 is the first build anyone else sees: Care & Meds is hidden rather than opening onto a stub, while
  unbuilt rows inside More may read "coming soon" (ADR-0019, ADR-0015).
- **The language switcher's mechanism is an app-shell change, not a Settings row** (ADR-0013). That ADR left
  the question open and the answer turned out to be the expensive one: AppCompat's pre-13 locale backport
  applies through `AppCompatDelegate`, which exists only inside an `AppCompatActivity`, which in turn needs
  an AppCompat-parented theme — and this app is a single `ComponentActivity` under
  `android:Theme.Material.Light.NoActionBar` with no AppCompat anywhere. It is accepted, as ADR-0013
  pre-authorised, but it takes its **own checkpoint at the front of the phase** rather than sitting beside
  the translation at the end: a base-class and root-theme change is the cheapest thing here to do early and
  among the most expensive to do late.
- **Polish and the release are deliberately separated.** ADR-0013 puts Polish before the Play release, and
  ~400 strings of voice-heavy copy is a multi-session writing task; welding it to the release makes the
  release date a function of how long translating takes, and puts the pressure to rush it exactly where it
  is least recoverable. So 1.0 ships English to the internal track the moment the gate passes, Polish lands
  next, and **1.0.1 is the build that opens the closed track**.

### Checkpoints

**Ten** — seven planned, and the last of those split into four once it was clear that 3g was three releases
and a translation wearing one number. The ordering is deliberate three times over. ADR-0009's registration
and the store paperwork are
calendar costs rather than engineering ones, so they go first and the recruiting starts with them. The
**shell change** goes second, because landing AppCompat early gives every later checkpoint's
hand-verification a free pass over it, while landing it last would put a root-theme reparent underneath the
release with only the polish pass to catch what it broke. And the two backup halves are built
**provable-first** — manual export and restore before the unattended agent — because they share the WAL
checkpoint and the scope-to-file-list, and that machinery is better built where a test can watch it.
ADR-0019 gates 1.0 on *the data being safe*, which export and restore satisfy on their own; the agent is
ADR-0005's effortless net **on top**, so an agent that turns into a swamp costs a release date rather than a
release. Everything else runs one way: photos need the schema bump, restore needs an export to restore,
first-run setup needs a backup scope to offer.

**The tail is four checkpoints rather than one**, for the same reason this phase already separated Polish
from the release: each has a different kind of cost and fails in a different way, and welding them together
makes the release date a function of the slowest. **3g** is engineering with an unknown yield — a gate pass
finds what it finds. **3h** is paperwork plus Play's review latency on a first-time account, which no amount
of working harder shortens. **3i** is a multi-session writing task. **3j** is a calendar. Only 3g can be
estimated, and it is the only one that may change code.

**One schema bump, one wipe** — version 4 at 3c. It is the last *planned* one, not the last *permitted*
one: nothing reaches another device until 1.0.1, so a bump at 3d or 3f would still be free under ADR-0007.
The obligation attaches when **1.0.1 reaches the closed track**, and from there every schema version that
reaches a device carries a tested forward migration, its exported JSON git-tagged and load-bearing. What
that costs mechanically is not "write migrations from now on" — the destructive fallback, the consent
screen, the debug build and restore all have to move, which is **ADR-0023**.

1. **3a — The release path, proven while the payload is boring.**
   - Pay the $25, register the developer account, and **re-read the current closed-testing policy in the
     Console rather than assuming 12 testers over 14 days** (ADR-0009) — that number decides *when* the
     clock can realistically finish, so a stale reading of it is expensive in a way a stale API reading is
     not.
   - **Start recruiting the twelve**, tracked as an explicit non-code item running across the whole phase.
     It is the only dependency here that cannot be solved by working harder, and the only one whose lead
     time is other people's.
   - **Privacy policy** at `docs/privacy-policy.md`, published through GitHub Pages off the already-public
     repo. It needs a hosted URL and the app has no server by design; Pages costs nothing, versions with the
     code, and the content is unusually short — nothing collected, nothing shared, no network requests from
     the app's own code, everything on the device — which is also what the Data safety form will say.
   - Play's **App content** section: data safety, content rating, target audience, ads and news
     declarations. These gate publishing to **any** track, internal included, which is why they are here and
     not at 3g: the pipeline does not move until they are green.

     Every answer is written out in [`docs/play-app-content.md`](play-app-content.md), verified against
     the built release artifact rather than against intent — the app declares **no user-facing
     permission at all**, which is what makes "collects nothing" a checkable claim rather than a
     promise. Play cross-checks that form against the privacy policy, so the two move together or
     neither does. Three answers are judgement calls and are marked as such: the 18+ target age, the
     Health-apps declaration (Play's is written for *human* health), and Android Auto Backup, which is
     disclosed but is not collection by the app.
   - A **minimum-viable store listing** — short and full description, feature graphic, two screenshots of
     whatever exists. It gets revisited at 3g with real 1.0 screenshots; taking them now would photograph an
     app that is about to change. The copy below is decided; only the screenshots are placeholders.

     Play has **no hidden keyword field** — the searchable surface is title, short description and full
     description, in that order of weight. So the title carries the keywords and the brand leads:

     | Field | Value |
     | --- | --- |
     | Launcher label (`app_name`) | `Binky` |
     | Play title (en) | `Binky: Bunny & Rabbit Tracker` (29/30) |
     | Play short description (en) | `Track your rabbit's weight, health and care. Private, offline, no ads.` (70/80) |
     | Play title (pl) | `Binky: Zdrowie Królika` (22/30) |

     The **full descriptions**, in both languages, live in [`docs/store-listing.md`](store-listing.md),
     which is the paste-ready source for every listing field. They are scoped to **1.0** — weight,
     observations, photos, backup — and deliberately describe no reminder, medication, vet or document
     feature, because those ship at 1.1 and 1.2 and Play treats advertising absent features as a
     listing violation rather than a rounding error.

     The **Polish listing needs its own keywords** — `królik`, `waga`, `dzienniczek zdrowia` — not a
     translation of the English ones, because each locale's listing is indexed separately and Polish
     owners search in Polish. English is the default listing language, matching ADR-0013's base language
     for the same reason: it is the fallback for every unmatched locale.

     The **brand name is deliberately not translated**, so `app_name` stays out of `values-pl/strings.xml`
     and falls back. A launcher label resolves against the **system** locale, not ADR-0013's per-app
     switcher, so translating it would put a Polish name under an English UI — or the reverse — for
     anyone whose app language differs from their phone's. Descriptions are what carry the language.

     The name was checked before it was committed to: Play requires unique **package names**, not unique
     titles, so the two unrelated apps already called Binky are no obstacle and neither is in this
     category. `BINKY` is a US trademark for pacifiers and teething rings — Nice class 10/28, not class 9
     — and no class 9 registration was found. **TMview checked 2026-07-27 and the gap is closed:** no
     Polish national mark contains "binky" at all, and the single live EUIPO class 9 registration for the
     bare word (EM 016461519, Shenzhen Binky E-Commerce) is *figurative*, not a word mark, and specifies
     only consumer-electronics hardware — earphones, camcorders, phone cases, baby monitors — with no
     software or downloadable applications in it. Class number alone is not the test; similarity of goods
     is, and hardware accessories are far from a pet-health app. The earlier EUIPO word mark `BINKY`
     (EM 006459119) has **Ended** and covered produce. A register search is not legal clearance and says
     nothing about unregistered rights, but nothing here blocks the name.
   - **A real app icon** — adaptive plus the 512² listing asset. This is ADR-0012's stated exception:
     identity assets are not the visual polish that comes last, because they cannot be deferred past the
     release the way spacing and colour can, and the template's green robot is not a thing to ship.

     **Done, and original.** The stock Noto Emoji rabbit that stood here first carried an obligation
     that gated the upload — the OFL requires the licence notice to reach the user, and there is no
     licences screen — so the art was replaced rather than the screen added, exactly as this
     checkpoint preferred. Flaticon and the other stock libraries whose licences forbid using their
     art as a logo were ruled out on the same basis, not on taste.

     The mark is now six ellipses declared in [`art/rabbit.py`](../art/rabbit.py), from which both
     the adaptive icon (foreground, background, and a monochrome layer for Android 13+) and the
     feature graphic are generated — one declaration, so the two cannot drift apart. The eye is a
     hole wound against the other subpaths rather than a shape painted in the ground colour, which
     is what lets it survive the monochrome layer's flat tint. Verified rendered by the system on
     the Xiaomi, not just as committed XML.

     **No third-party art remains in the repo**, so nothing here obliges a licences screen. That is
     a reason to build one deliberately later if the Apache-2.0 dependencies warrant it, rather than
     under release pressure for a single icon.
   - Keystore generated **once**, kept out of git, backed up off this machine; `signingConfigs` read from
     `local.properties`, and a release build with no key **fails loudly** rather than falling back to the
     debug key. Note what Play App Signing actually means (ADR-0009): Google holds the permanent *app
     signing* key, ours is an *upload* key, and an upload key can be reset — the stakes are hygiene, not
     catastrophe.
   - **`bundleRelease`, not `assembleRelease`.** Play requires an AAB for new apps, and an `.aab` cannot be
     `adb install`ed, so the only artifact ever installed on the phone is the one Play delivers — which is
     also the only one signed the way a real user receives it. `assembleRelease` stays for automated checks
     and is never installed.
   - **`applicationIdSuffix = ".debug"` and a distinct debug label** (ADR-0023). Without it, `installDebug`
     stops working the day this checkpoint succeeds: the Play build carries Google's signature, a locally
     signed build of the same `applicationId` can neither sit beside it nor replace it, and the only way
     through is uninstalling the Play build. ADR-0007 offered "or a separate DB name" as an alternative; it
     is not one, since it does nothing about two builds being unable to coexist. `FileProvider`'s authority
     already interpolates `${applicationId}`, so it follows; the instrumentation package becomes
     `app.binky.tracker.debug.test`, which is a correction owed to **CLAUDE.md**'s Xiaomi fallback command.
   - **R8 stays off** — `isMinifyEnabled = false`, recorded as a decision rather than left as a template
     default. 1.0 already differs from any build that gets tested in five ways (application id, signature,
     ADR-0023's throw-instead-of-wipe, the consent screen's release variant, the `BuildConfig.DEBUG`
     sample-data gate) and is obtainable only through a Play round-trip. A sixth divergence whose failures
     are release-only, runtime, and reflection-shaped is the opposite of what this checkpoint is for.
     Revisit at 1.1, against a known-good 1.0 and with testers already on the track.
   - One signed build on the **internal testing track**, at Phase 2's feature set. Nothing about the payload
     is new, which is the point: upload, track configuration, Play's review and install-from-Play on the
     Xiaomi are each proven while none of them are entangled with a feature under review.
   - That build is then **uninstalled**. It is a pipeline proof, not a dogfood build: leaving a release build
     at schema 3 sitting on the phone would create a migration obligation for a version nobody used, and
     3c's wipe is spent on the debug app instead. The author's real bunny history starts at 1.0.
   - **Neither of those last two bullets happened, and recording that is worth more than quietly dropping
     them.** The developer account, the App content answers, the listing copy, the privacy policy, the icon,
     the keystore and a real `bundleRelease` all landed; **nothing was ever uploaded to a track**. So the one
     property this checkpoint existed to buy — proving upload, track configuration, Play's review and
     install-from-Play while the payload is boring — is **spent**, because by the time it is done the next
     thing there is to upload is 1.0 itself. The unproven half moves to **3h**, which buys back what it still
     can by ordering *within* the checkpoint — a release candidate before the version number that matters —
     rather than by pretending a boring payload still exists.
   - `versionName` / `versionCode` stay automated (release-please) and are never hand-edited. Both halves
     are **verified against a real `bundleRelease`**: the AAB's manifest carries `versionCode` 85, matching
     `git rev-list --count HEAD`, alongside `versionName` `0.4.0`, and the bundle is signed by the upload
     key. Reading that back needs a decoder — an AAB stores `base/manifest/AndroidManifest.xml` as protobuf,
     not the binary XML `aapt2 dump` knows how to read.

     The second half was **not true when it was written**. Built from a source archive with no `.git` — the
     documented fallback case — the release produced a **signed AAB carrying `versionCode` 1**. The build
     did fail, but only after packaging and signing it, and only because the configuration cache could not
     serialise the failed `git` call; `--no-configuration-cache` removes that and leaves a clean success. So
     the fallback shipped, which is exactly what a Play track never forgives. The guard now runs at
     configuration time and leaves no artifact behind, and the exit code is inspected in our own code rather
     than left to throw inside Gradle's value source — which is what deferred the failure past the artifact.
     A debug build with no git history still falls back to 1 and still succeeds, which is the case the
     fallback exists for.
   - There is no test gate here, and nothing in this checkpoint touches app code. The gate is that the build
     is installable from Play on the Xiaomi and that the debug app installs beside it — and discovering both
     now rather than at 3g is the entire reason this is first.
2. **3b — The shell: AppCompat, and the switcher's mechanism.** ✅
   - `androidx.appcompat` enters `libs.versions.toml`; `MainActivity` becomes an `AppCompatActivity`;
     `Theme.Binky` is reparented from `android:Theme.Material.Light.NoActionBar` to
     **`Theme.AppCompat.DayNight.NoActionBar`**. Not a Material Components theme: `AppCompatActivity` only
     requires an AppCompat-descended one, and Compose M3 draws every pixel of actual UI, so pulling in
     `com.google.android.material` would add a second dependency that renders nothing.
   - `locales_config.xml` with **English alone**, and the `AppLocalesMetadataHolderService` metadata with
     `autoStoreLocales="true"` — AppCompat's own persistence rather than a fifth DataStore key, because
     DataStore's asynchronous read would let the app draw a frame in the wrong language before resolving.
   - The Settings switcher **row** is not built here; it lands at 3f. This checkpoint exists to find out what
     the backport costs, a long way before the translation, which is what ADR-0013 asked for.
   - Its own checkpoint for the same reason 2d was: a dependency that either drops straight in or eats a day,
     whose outcome should not be tangled into the review of anything else. The difference from 2d is that
     there is no fallback — ADR-0013 accepted this dependency in advance — so the timebox buys information,
     not a decision.
   - No new tests. The gate is `spotlessApply`, `assembleDebug`, `test`, and **every screen looked at on the
     Xiaomi**: edge-to-edge insets, the status bar, dialogs, the Photo Picker and the camera intent, all of
     which now compose under a theme they have never seen.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` and `lint` pass with no source change beyond the
     four files above. **The information the timebox bought is that it drops straight in** — appcompat 1.7.1,
     one base class, one theme parent, two manifest entries, and no Compose or Nav3 code touched at all. The
     merged manifest carries `localeConfig="@xml/locales_config"`, the holder service and `autoStoreLocales`;
     the platform accepts and reads back a per-app locale for `app.binky.tracker.debug`.
   - Exercised on the Xiaomi: all five destinations, Settings, the bunny switcher's dropdown, the Edit form,
     and the delete confirmation — insets, status bar and the dialog scrim all unchanged. Both media paths
     survive the base-class change with their results intact: the Photo Picker returns a Uri that lands as an
     avatar, and the camera intent's `FileProvider` round-trip still writes, reads back and downsamples.
   - **`DayNight` is a real behaviour change, not just a rename.** The old parent was `Material.Light`, so the
     window Android draws — background, and the frame before Compose composes — was light even while
     `BinkyTheme` had already followed the system into dark. They now agree; verified by flipping the phone
     both ways.
   - **What the phone could not prove:** it runs Android 16, so this exercised the *platform* per-app locale
     path, not AppCompat's pre-13 backport — which is the half `minSdk` 26 exists to serve and the reason the
     dependency was taken at all. The shell cost is now known and paid; the backport's own behaviour stays
     unverified until 3f puts a second language behind the switcher, and wants an API 30-ish emulator or
     device then rather than the Xiaomi.
3. **3c — Photos: the gallery, and the last planned wipe.** ✅ Schema → **4**.
   - `PhotoEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `path` relative (`photos/<uuid>.jpg`), nullable
     `caption`, nullable `capturedAt`, `createdAt`. Indexed on `(bunnyId, createdAt)`.
   - **`capturedAt` is read before the strip, not after** (ADR-0020). `persist` returns `(path, capturedAt)`
     instead of a bare path, taken from the same `ExifInterface` call site that already reads the
     orientation tag; the one avatar caller ignores the second field. Stripping the file is about what a
     file leaving the device carries, not a rule against the pipeline reading it on the way past — and
     there is no going back for this one, since a column added later could never be backfilled from files
     whose metadata this app has already removed. Ordering is `COALESCE(capturedAt, createdAt)`, because
     screenshots and re-shared images routinely carry no EXIF date.
   - The case that forces it: a bulk import from the camera roll lands twenty photos spanning two years
     within the same millisecond, and ordering those by when they were *added* is arbitrary order for a
     gallery whose whole point is a bunny growing up.
   - `PhotoDao` / `PhotoRepository`: a bunny's photos as a `Flow`, newest first; add, edit caption, delete.
     Add goes **file first, then the row**; delete is the mirror — row, commit, then the file best-effort
     (ADR-0020).
   - **`MediaKind.Photo`'s first real caller.** Its 2048 long-edge / q85 numbers were written in Phase 1 as
     an unverified guess for exactly this screen; verify them against the full-screen pager on the phone,
     and adjust the spec table rather than the call site.
   - Multi-select from the Photo Picker plus a single "take a photo" through the existing `TakePicture` +
     `FileProvider` plumbing — no CameraX, no new permission. Selection is **capped at 50**, and `persist`
     runs **sequentially with determinate progress**: twenty full-resolution bitmaps decoded at once is
     precisely the failure the house rule about the media helper exists to prevent.
   - Import is **incremental and forgiving**, which the file-first pipeline already makes natural. Each photo
     is committed as it lands, so a cancel or a navigate-away keeps everything finished and loses nothing
     else; an unreadable file is **skipped, counted, and reported once at the end** — *"18 of 20 added, 2
     could not be read"* — rather than aborting 17 good imports over one bad one. The job lives in the
     `ViewModel` and dies with the screen; no background scheduling arrives a phase before Phase 4.
   - `LazyVerticalGrid` with Coil 3, `HorizontalPager` for full screen, caption edited from the pager. A
     missing file renders the placeholder, never a crash — and an Essential restore is exactly the case
     that produces one (ADR-0005), so this is not a hypothetical branch.
   - Reached from **More → Photos**, flipping that row from "coming soon" to live. Scoped to the selected
     bunny; under **"All bunnies"** it asks which bunny first, reusing 2f's `ChooseBunnyDialog` rather than
     inventing a combined gallery — photos are individual data, like weight (ADR-0015).
   - In the `Archived(id)` scope the gallery renders read-only: no add, no caption edit, no delete
     (ADR-0004).
   - `recordCounts` gains its third contributor. Photos are **sole-owned**, so they land in the destroyed
     bucket, and deleting a bunny must remove its photo **files** — the same best-effort-after-commit the
     avatar already gets, over a list rather than one path.
   - **Exclude `photos/` from Android Auto Backup — the privacy policy already promises this.** It says
     *"Your photo gallery in the app is deliberately excluded from it"*, and that is **not implemented**:
     `allowBackup` is `true` while the manifest references neither `android:dataExtractionRules` nor
     `android:fullBackupContent`, and both files in `res/xml/` are still AGP template stubs with every
     rule commented out. Auto Backup therefore takes all of `filesDir`, `avatars/` included. The claim is
     not false *today* only because the gallery does not exist yet; it becomes false in this checkpoint,
     against a policy that has been **published since 3a**. Either wire the rules up here or change that
     sentence — and wiring them up is the right call, since a gallery is exactly the large, replaceable
     data Auto Backup should skip, while the database that carries the actual history is not.

     **Both attributes are needed, not one.** `fullBackupContent` governs API 30 and below,
     `dataExtractionRules` API 31 and above; `minSdk` is 26, so setting only the modern one silently
     leaves every device below API 31 backing the gallery up anyway. Verify against ADR-0005's export
     scopes rather than reinventing the path list — the same split that makes an export scope a list of
     directories makes this a list of exclusions.
   - **The destructive fallback becomes debug-only here** (ADR-0023), in the same commit as the bump, because
     this is the wipe that makes it the last one. A release build with no migration path throws at open
     instead of deleting a bunny's history, and the release variant of the consent screen loses its forward
     button: it states that this build cannot open the data, names the preserved copy, and offers **share**
     and nothing else. The copy is still taken first in both builds — preserving before a *failure* rather
     than before a wipe, which is a better reason than the one it was written for.
   - Tests, instrumented: photos cascade with their bunny; editing a caption does not touch the path;
     deleting a photo removes both row and file; `recordCounts` counts photos as sole-owned; `capturedAt`
     survives a round trip and a photo with no EXIF date orders by `createdAt`. A JVM test asserts the
     destructive fallback is gated on `BuildConfig.DEBUG` — a property that cannot be checked by hand on a
     release build, since `run-as` does not work on one. The sample-data action gains a handful of photos,
     so 3d and 3e have something real to include and exclude.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` and `lint` clean; **75 instrumented tests pass on
     the Xiaomi**, seven of them the new `PhotoRepositoryTest`. Six commits, with the schema bump and
     ADR-0023's gating in the same one as the plan requires.
   - **`MediaKind.Photo`'s 2048/q85 guess holds — no change to the spec table.** Three real camera photos
     imported through the Photo Picker: 2.3–3.7 MB sources land at 1536×2048 and 310–350 kB each. The phone
     is 1220×2712, so a stored photo still carries 1.26× the pixels the pager can show in portrait and 1.68×
     in landscape. Phase 1 guessed a number that turned out to have headroom rather than one that had to be
     raised, which is the outcome that costs nothing.
   - **`PendingWipe` became `SchemaMismatch`** while implementing ADR-0023: in a release build nothing is
     pending and nothing is wiped, so the name described only half of what the type now carries. It gained a
     `wipeOnConsent` flag and one screen renders both variants. The `ui/wipe` package name was left alone —
     renaming a package for a type rename is churn.
   - The gating is `.apply { if (allow) fallbackToDestructiveMigration(...) }` rather than a builder call
     taking a boolean, so the release path leaves the builder **untouched** instead of configuring it to do
     nothing. The parameter is also 3d's hook: the staged restore database has to pin its own configuration.
   - `PhotoDao` deliberately has **no `@Update`**, only `setCaption`. A blanket `@Update` would let a future
     caller rewrite `path` and point a row at a file it was never written for — the one failure ADR-0020's
     file-first ordering cannot protect against.
   - The import's `catch` rethrows `CancellationException` by hand. `runCatching` would swallow it, and
     leaving the screen mid-import would then be counted and reported as a pile of unreadable files.
   - **Device transfer keeps the photos**, cloud backup does not. The privacy policy's promise names the
     Google-account upload, and ADR-0005's arithmetic is about that quota; a phone-to-phone transfer has
     neither, and silently dropping a bunny's whole gallery on a phone upgrade is the worse failure.
     `preserved/` is excluded from both (ADR-0007).
   - The `-wal`/`-shm` sidecars were left alone deliberately, though ADR-0005 names them: excluding them
     would lose committed transactions that have not been checkpointed, which is worse than the mid-write
     capture it prevents. That wants the WAL checkpoint inside the custom `BackupAgent`, at 1.0.
   - Exercised on the Xiaomi end to end: the schema-4 wipe and its preserved copy; the seeded gallery
     ordering by capture date with the dated photos added *last*; the pager's "Taken" vs "Added" labels; a
     caption edit round-tripping through the `Flow`; a delete taking row and file together and the pager
     carrying on; Back closing the viewer rather than the screen; and the archived scope rendering with no
     add action, no delete and no caption edit.
4. **3d — Manual export, and restore.** ✅
   - Zip at three scopes — Essential (database + preferences + `avatars/`), **Records** (default; plus
     `documents/`), Everything (plus `photos/`). A scope is **a list of `MediaKind`** plus the two fixed
     members, which is what ADR-0020 gave the enum a `directory` for; no magic strings. Preferences ride from
     Essential upward, so no scope produces a restored phone that has forgotten its own settings.
   - The **WAL checkpoint and the scope-to-file-list are built here**, as the shared helper 3e will reuse.
     An export that captures a mid-write database is the same bug as a backup that does, and it is written
     once, in the checkpoint where a test can watch it happen.
   - A **manifest inside the zip** — scope, schema version, created-at, per-kind counts — is the authority
     for what the archive contains. The scope also goes in the filename (`bunny-<scope>-<timestamp>.zip`)
     for humans, but the confirmation dialog reads the manifest: a filename is the one part of a file the
     owner can trivially change, and it must not be what a promise is sourced from. Out through the **share
     sheet**, from a new `cache/exports` FileProvider path: cache, so a share the owner abandons is
     reclaimed by the OS rather than doubling the app's footprint.
   - Restore **never builds a path out of archive input**. It extracts only entries matching known shapes —
     the database filename, the preferences filename, and `<MediaKind.directory>/<uuid>.jpg` with both
     halves validated — and ignores everything else, which defeats zip-slip by construction rather than by
     sanitising after the fact. No manifest, or no database entry, means *"this file is not a Binky
     backup"* by name. A total-bytes ceiling stops a malformed archive filling the device.
   - Restore is gated behind an explicit confirmation naming *"[scope] backup from [date]"*, and first takes
     an **automatic Essential-scope export of the current state** into the existing `preserved/` — a zip
     rather than a bare `.db`, so **undoing a bad restore is the ordinary restore path** rather than a
     recovery procedure with `adb` in it. Restore is the most destructive thing the app does; it is the one
     operation that should have a way back built out of parts already tested.
   - `preserved/` therefore holds two kinds of occupant with opposite properties — wipe copies (stale
     schema, unrestorable by design) and restore snapshots (current schema, restorable in one tap). Settings
     **names what each row is**, says which can be restored and which can only be shared, and shows per-row
     and total size, so the app's one unbounded directory cannot grow invisibly. Three invariants hold it
     together: `preserved/` is in **no export scope**, is **never written by a restore** — or the snapshot
     that undoes a restore would be eaten by the restore itself — and is **never auto-pruned**, because
     every occupant is a recovery artifact and silently deleting those is the one thing this project has
     consistently refused to do on the owner's behalf. Deletion stays an explicit tap.
   - **Stage, migrate, swap** (ADR-0023). Unzip to a staging database; refuse anything at a *newer* schema
     outright, since no migration runs backwards; open the staged file with the real migrations, so it is
     already at the current schema; then swap it in. A failure lands on the copy, before anything on the
     phone has been touched. The staged builder **pins its own configuration**, or 3c's debug fallback would
     quietly empty the very file it was asked to test.
   - This replaces comparing version numbers, which only asserts that a migration exists and never that it
     survives *this* file — and which, written as "refuse anything that isn't this build's version", would
     make every existing backup unrestorable the day 1.1 bumps the schema, inverting ADR-0005's whole
     promise.
   - Database **replaced**, media **merged** by relative `<kind>/<uuid>.jpg` path (ADR-0005): an Essential
     restore onto a phone that still holds its photo files keeps them instead of turning them all into
     placeholders.
   - The restore then ends on a **terminal screen** — what was restored, what the scope contained, where the
     pre-restore snapshot went, and one button, *"Close Binky"*, which calls `finishAffinity()` and
     `exitProcess(0)`. Half the app is holding `Flow`s over the file that was just replaced, so the process
     has to go; and the obvious automatic version — schedule a `PendingIntent` and kill — is a **background
     activity start**, restricted since Android 10 and policed harder by HyperOS, so it would work on this
     desk and silently fail to come back on someone else's phone after the most destructive operation in the
     app. One tap, on a screen that is the right place to tell the owner what happened anyway.
   - Tests, JVM: the zip round-trip; what each scope's manifest contains; merge semantics as a pure function
     over two file lists — kept, overlaid, orphaned; and the entry-name allowlist, including a `../` entry
     and an entry naming an unknown directory.
   - Tests, **instrumented** — because stage-migrate-swap is Room on a device and cannot be reached from the
     JVM: a staged file at the current schema opens and swaps in; a staged file at a *newer* version is
     refused with the live database **byte-identical** afterwards; and a staged file this build cannot open
     is **not emptied in a debug build**, which is the pinned-configuration trap the design names and whose
     failure mode is silently destroying the backup the owner is trying to restore. The older-schema
     migration path is not testable here, because at 1.0 no older released schema exists — it becomes a real
     test at 1.1, and the plan should not claim it before then.
   - **Three buckets, not two, and the scope is what splits them.** The merge was specified as a function
     over two file lists, and two lists cannot tell the case ADR-0005 cares about from its opposite: a photo
     on disk during an *Essential* restore is **kept**, because that archive never claimed to know about
     `photos/`; the same photo during an *Everything* restore is **orphaned**, because that archive was
     authoritative and did not carry it. So `planMediaMerge` takes the manifest's scope as a third argument.
     Nothing is deleted either way — naming them apart is what lets the terminal screen report honestly.
   - The **WAL checkpoint is a constructor parameter on the exporter**, defaulting to the real one.
     `android.database.sqlite` does not exist on the JVM, so the archive's *layout* would otherwise have had
     no test at all. The default is the production wiring, so the seam substitutes the checkpoint and cannot
     skip it; the real one runs in the instrumented tests, which build their archives through the exporter.
   - **The file's header outranks the manifest** on schema version. The manifest is the authority on what an
     archive *contains* — scope, date, counts — but it is also data an owner could have edited, and Room is
     about to be pointed at the file rather than at the claim. `readUserVersion` on the staged copy is what
     actually refuses a newer database; the instrumented test proves it by shipping a manifest that lies.
   - The pre-restore snapshot is taken **after** the staged copy has been opened and migrated, not before.
     Nothing destructive has happened until the swap, and taking it earlier would litter `preserved/` with
     snapshots of restores that never occurred — in the one directory this app never prunes.
   - `preserved/` now holds two occupants, so `PreservedCopy` carries a `PreservedKind` and the listing sorts
     on the parsed date rather than on the name: `bunny-before-restore-…` and `bunny-2026…` no longer sort
     chronologically against each other, which the old name-only ordering had quietly relied on.
   - The section **moved off Settings onto the new Backup screen**, and its strings moved from
     `settings_preserved_*` to `preserved_*`. A restore snapshot has to sit beside the restore that can load
     it back in, and the schema-mismatch screen shares the same share action, so the strings belong to
     neither screen.
   - Restore reads the archive **twice**, not three times: pass one lands the manifest, the database and the
     preferences, counts every byte it would ever write and notes the media entry names; pass two extracts
     the media straight into `filesDir`. The ceiling is therefore enforced before anything is touched, and a
     large gallery is never held on disk twice.
   - `kotlinx-serialization-json` added for the manifest. The serialization *plugin* was already present for
     Nav3's `@Serializable` keys; the JSON format itself was not, and a hand-rolled parser between an owner's
     archive and their bunny's history is not a saving worth making.
   - Verified: `./gradlew test` (127 JVM), the full instrumented suite on the Xiaomi (84, via the plain-APK
     fallback — `connectedAndroidTest`'s split install hit `INSTALL_FAILED_USER_RESTRICTED` again), and
     `lint` clean of anything from this checkpoint.
   - Exercised on the Xiaomi by hand: an Everything export off the seeded fixture, whose zip holds the
     manifest, the database, the preferences and exactly the five seeded photos, with per-kind counts to
     match; the FileProvider grant and the share sheet, showing the scope in the filename; then a restore
     driven from a snapshot row — the confirmation naming *"Everything backup from …"* out of the manifest,
     the terminal report, the pre-restore snapshot landing in `preserved/` at Essential scope (9 kB against
     the archive's 14), and **"Close Binky" actually ending the process**.
   - Two things that hand-verification caught and nothing else would have. *"Restored a Everything backup"* —
     an article chosen at build time cannot agree with a scope name chosen at run time, and two of the three
     start with a vowel, so the copy says "the". And the SAF picker on HyperOS **ignores injected input
     entirely**, so the picker → confirm → restore chain was exercised through the `preserved/` snapshot path
     instead; the picked-file path differs only in where the `InputStream` comes from, but it is the one link
     no test on this device touches, and it is worth a deliberate tap before the release gate.
5. **3e — Auto Backup: the agent, and the marker that must not lie.** ✅
   - `BunnyBackupAgent` registered with `android:backupAgent` **and `android:fullBackupOnly="true"`** —
     declaring an agent without it puts the app on the key/value path, which is not what ADR-0005 describes.
   - The agent **takes paths, not a `Context`** (ADR-0005). When the system starts the process *for* backup
     it binds the base `android.app.Application` rather than this app's subclass, so `AppContainer` is
     absent and a cast to `BinkyApplication` is a `ClassCastException` — and reaching for the
     container would in any case force the `lazy` that ADR-0007 makes the structural guard in front of a
     wipe. The failure ordering is what makes this worth building structurally: Auto Backup runs when the
     device is idle and charging, `bmgr backupnow` runs with the app on screen, so a container-dependent
     agent passes every test done by hand and fails only in production, silently.
   - So the file set and the marker are **functions over `File`**, in their own file with no Android
     dependency, and the agent is a thin shell that calls them. "Cannot reach the container" becomes a
     property of the types — the same move ADR-0007 made when it rejected a guard by discipline.
   - `onFullBackup` checkpoints the WAL into a consistent copy via 3d's shared helper and backs up **that
     copy**, never the live file with its sidecars — ADR-0005 names the alternative as a restore that comes
     back corrupt. This is also why `allowBackup="true"` cannot simply be left as it stands: today the
     manifest enables Auto Backup with no agent and no rules, so the platform is already eligible to copy
     `filesDir` wholesale, live database and `-wal`/`-shm` included. Either the agent takes control of the
     file set here, or `allowBackup` goes to `false`; leaving it as-is ships a backup that appears to work
     and restores corrupt.
   - Unconditional: database, preferences, `avatars/`. Excluded: `photos/` (ADR-0005), and **`preserved/`** —
     ADR-0007 left that question open for this phase and the answer is no. Not on quota grounds, which do
     not hold at 1.0 where the whole set is under a megabyte, but because `preserved/` is the app's one
     unbounded, never-pruned directory, and Android rejects the *entire* over-quota dataset rather than
     trimming it. Admitting an unbounded set means one day losing the database and the avatars in order to
     have protected a duplicate. The owner's **share** tap is what makes a preserved copy safe.
   - **The documents ceiling and the exclusion notification are Phase 5's**, not 1.0's. `documents/` is empty
     until then, so the admission function would admit nothing, the notification could not fire, and the
     app's first notification channel would be created in the release that 3f deliberately keeps free of any
     notification permission. Building them beside the documents that exercise them costs nothing later —
     the agent's file set is ordinary app code, changed in any release, and a backup made by 1.0 restores
     into 1.2 regardless.
   - The marker — last-backup instant — is a **plain file under `filesDir`**, written temp-then-rename,
     behind a `(File) -> Marker?` helper that the agent and Settings both call. Not DataStore: the agent
     cannot reach the app's instance without the container, and its writes are `suspend` inside blocking
     backup callbacks. ADR-0005's requirement is *outside the database*, which restore replaces; this
     satisfies it and stays readable from both sides.
   - Because the agent names its own file set, the marker is **never included**, so it cannot travel to
     another phone at all. `onRestoreFinished()` **clears it regardless**, for a second and different
     reason: after a restore the phone no longer holds the data the old marker vouched for. Two mechanisms
     failing differently — the exclusion is a static claim a later edit could break, the clear is a runtime
     guarantee at the exact event.
   - Settings gains the status line, with all three states **in words**: a date; **stale** past 14 days; and
     *"No automatic backup has been recorded on this phone"* with a button into system backup settings.
     A blank reads as a working net, which is ADR-0001's silence failure pointed at backup. The deep link is
     best-effort with a `resolveActivity` fallback to top-level settings — HyperOS moves that screen — and
     3f reuses it in first-run setup. The same screen states plainly that **photos are not in the automatic
     backup** and need an Everything export.
   - The two template XML files resolve here. An agent that chooses its own file set makes `backup_rules.xml`
     and `data_extraction_rules.xml` dead, so the expected outcome is **deleting both along with their
     manifest attributes**, closing two of Phase 2's four standing lint warnings — but confirmed against a
     real backup run rather than against the documentation.
   - Driven with `adb shell bmgr backupnow app.binky.tracker` and `bmgr restore`; if HyperOS will not drive
     `bmgr`, the fallback evidence is the marker file appearing under `run-as`, plus `dumpsys backup` and
     logcat around the callbacks. **If it cannot be observed at all, 1.0 still ships**: export and restore
     already satisfy ADR-0019, and the marker design means an agent that never ran renders as *"No automatic
     backup has been recorded on this phone"* — which is literally true. An unverifiable agent degrades into
     an honest app rather than a lying one, and Play Console vitals become how it is found out, which is one
     of the three reasons ADR-0009 chose Play.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (141 unit tests, 14 new) and `lint` pass. Lint is
     down to **0 errors and no new warnings**: the two `UnusedResources` warnings Phase 2 left standing —
     `backup_rules.xml` and `data_extraction_rules.xml` read as unused — are gone, because both files are,
     which is the resolution Phase 2 predicted rather than a suppression. No instrumented tests are added;
     the agent's every decision is a function over `File`, so it is proven on the JVM and then on the phone,
     with nothing in between that a device test could reach.
   - `bmgr` **did** drive on HyperOS, so the fallback evidence was not needed. The full loop, on the local
     transport: `bmgr backupnow` moved **145,920 bytes** — the 131 kB database, the preferences and one
     avatar, and demonstrably *not* the five seeded photos — then `pm clear` wiped the app and `bmgr restore`
     brought it back. The app opened on the restored history: Bijou, the fluffle, the year of weighings and
     the trend flag. On disk afterwards: `databases/bunny.db` in place with no `-wal`/`-shm`, `avatars/` and
     the preferences restored, and **no `photos/`, no `preserved/` and no marker** — the exclusions proved by
     what did not arrive. The Google transport was put back afterwards.
   - **A backup is only half a design; the restore path is the other half, and it dictated the staging.**
     `fullBackupFile` records a file's domain and relative path, and the far end puts it back at exactly that
     path — so the checkpointed copy cannot be handed over from `cacheDir` (which the OS may empty, and which
     comes back as cache), and it cannot pretend to be `databases/bunny.db`. It is staged at
     `filesDir/autobackup/bunny.db`, and `onRestoreFinished()` moves it into place. Backing up the live file
     instead would have made all of this go away and walked straight into the trap ADR-0005 names.
   - So the move gets **two mechanisms, like the marker**: `onRestoreFinished()` normally does it before the
     app is ever launched, and `adoptRestoredDatabase` runs again at the head of `BinkyApplication.onCreate`,
     ahead of ADR-0007's version read. It adopts **only when there is no live database**, which is exactly the
     post-restore state; a staged copy found beside a real one is discarded, because overwriting records with
     a stale copy is by far the more expensive mistake. A restore that silently lands nowhere is the failure
     this whole phase exists to prevent, and it is one platform callback away.
   - The **device-to-device carve-out survives the XML deletion**. `data_extraction_rules.xml` deliberately
     let photos ride along on a phone-to-phone transfer — no cloud account, no quota, and losing a gallery on
     an upgrade is the worse failure — and that decision is now `includePhotos`, read off
     `FLAG_DEVICE_TO_DEVICE_TRANSFER`. The flag arrived in API 30, so below that it is false and the gallery
     stays out, which is precisely what `backup_rules.xml` did for "API 30 and below". Deleting the two files
     was meant to close two lint warnings; it would also have quietly dropped a decision.
   - The class is **`BinkyBackupAgent`**, not the plan's `BunnyBackupAgent`: the Android components are named
     for the app (`BinkyApplication`, `MainActivity`) while `Bunny*` names the animal's data.
   - Two things about the deep link that only tapping it could find, both on the Xiaomi. First,
     `ACTION_PRIVACY_SETTINGS` — the obvious choice, and the *only* one of four candidate actions that
     resolves on this phone — opens the Android 12 **Privacy dashboard**, which is permissions and has no
     backup switch anywhere on it. That is worse than the top-level fallback, because it looks like the
     destination; it is now used below API 31 only, where it really was Backup & reset. Second, the intent
     needs **`FLAG_ACTIVITY_NEW_TASK`**: without it Settings is pushed onto Binky's own task, and the owner's
     back stack becomes Binky-then-three-Settings-screens with no way to bring the app forward. With it,
     Settings opens in its own task and one Back returns to the Backup screen.
   - Incidental, and worth having seen: the restored phone renders the photo gallery it no longer has files
     for as **placeholders, with no crash** — the house rule holding on the path Auto Backup's exclusion
     creates, which is the one that will actually happen to someone.
6. **3f — First-run setup, the visibility flip, and the switcher row.** ✅
   - **Two steps at 1.0** (ADR-0006): add first bunny (skippable) → backup scope. The chosen scope becomes
     `AppPreferences`' **third** key, is 3d's export default, and stays editable in Settings.
   - `setupComplete` is **resolved on read, not merely stored**: absent means *complete if any bunny already
     exists*. That is Phase 1's selection-resolver idiom reused, and it settles two cases with one rule —
     the author's existing debug install never meets a wizard it predates, and a phone that has just
     restored a backup is not asked to set the app up again. Preferences travelling in every export scope
     (3d) is the second, independent mechanism covering the same restore case.
   - The reminders step is **not built here**. It ships with 1.1, with the reminders: Android allows two
     denials before the permission is refused permanently, 1.0 has nothing that posts a notification, and an
     opt-in that cannot demonstrate anything is the most likely to be dismissed — which is the failure
     ADR-0006 exists to prevent, arrived at from the other direction. ADR-0006's point-of-use ask becomes
     the *first* ask rather than the second.
   - The backup step **asks whether system backup is switched on**, using the deep link 3e already added,
     and states that photos are outside it. The app cannot detect either (ADR-0005), and this is the one
     moment the owner is already thinking about it.
   - **The visibility flip** (ADR-0015, ADR-0019) — the one-value change Phase 1 defined the enum for, not
     an introduction. `CARE` → `Hidden`; every other tab stays `Live`; More's Photos row went live at 3c,
     Documents and Support stay `ComingSoon`. The bottom bar renders from the non-`Hidden` entries, and a
     `Hidden` key arriving on a **restored back stack** — a Nav3 stack saved by a build where that tab was
     live — resolves to Home rather than to a blank destination.
   - Checked here too: `StubScreen` has **no remaining top-level caller**. If it does, either that screen is
     real or its tab is hidden; there is no third answer before 1.0.
   - **The switcher row** in Settings, with English alone in the list. Its mechanism landed at 3b, so this is
     the Settings row ADR-0013 originally hoped the whole thing would be.
   - **`setupComplete` became `setupProgress`, and that was not a rename — it was the checkpoint's one real
     bug, found by tapping.** As planned, the flag was a `Boolean?` resolved on read: absent meant *complete
     if any bunny already exists*. Run by hand, saving the first bunny **ended the wizard mid-flight** and
     dropped the owner straight into the app, because the wizard's own first step is what makes a bunny
     exist. The backup step — the one thing ADR-0006 puts in setup rather than in settings, on the grounds
     that a backup buried in settings never gets made — was unreachable for anyone who did not skip.
   - The fix is that `hasBunny` answers a question about the **past** (did this install exist before the
     wizard did), so it may only be asked while nothing has been recorded. The record therefore has three
     states, not two: absent, `Started`, `Complete`, stored by name like every other enum here. Showing the
     wizard writes `Started`, which is what makes it survive both its own first step and a process death
     halfway through — the same failure twice, and one written-down fact closes both. Nothing in composition
     could have: a latch held in `remember` dies with the process.
   - Two mechanisms again, and this is the pair: the flag is a preference and preferences travel in every
     export scope (3d), so a restore normally carries the answer; the resolver's absent-branch covers the
     restore that does not — an Auto Backup arriving without it, or an archive written before this release.
   - The screens are **two Nav3 keys on a back stack of their own**, not two more keys on the shell's. The
     shell's stack is rooted at Home, so Back out of step one would land on an app that is not set up and
     take the wizard's key with it. Rooted at `SetupBunny`, Back out of step one exits, which is what a
     first screen should do — and reusing `appEntryDecorators` means `BunnyEditorScreen` is composed
     **verbatim**, arguments and all, with the per-entry `ViewModelStore` it has in the shell. A
     wizard-shaped copy of the bunny form would have been a second place for ADR-0016's fields to drift.
   - There is no `onFinish` callback anywhere: the last step writes the preference, `setupState` flips, the
     gate swaps the shell in. One mechanism, so the screen and the stored answer cannot disagree.
   - The visibility flip was the one value ADR-0015 promised. `CARE` gained
     `DestinationVisibility.Hidden` and the bottom bar went to four tabs; the key, the entry and
     `CareAndMedsScreen` all stay, so 1.1 flips it back with no navigation work. `StubScreen`'s only
     top-level caller is that screen, and its tab is now hidden — which is one of the two answers the
     checkpoint allowed.
   - The restored-back-stack rule is a **pure function over `List<NavKey>`** in `NavigationKeys.kt`, applied
     during composition rather than from an effect: `NavDisplay` is a child, so it reads the repaired list
     and no frame of a hidden destination is ever drawn. Compose lint rejected the first attempt — a
     `remember` returning `Unit` — and was right to; the helper now returns the stack it repaired.
   - The scope picker and the photos warning were **extracted and shared** with the Backup screen rather
     than copied. There are exactly two places an owner picks a scope, and one set of words for them.
   - Hand-verification caught a second thing that only a finger finds: in the scope picker **only the radio
     circle was tappable**, not the two-line description beside it that plainly reads as part of the
     control. Pre-existing from 3d and harmless in settings; on the first-run path it is the first control
     the owner ever touches. The row is now `selectable` with `RadioButton(onClick = null)`, which also
     makes a screen reader announce one radio button rather than two things.
   - The switcher row ships with English alone, and that is not the pointless furniture ADR-0013 warned
     about: it means the switcher is exercised by hand on a real phone at 1.0 rather than for the first time
     in the week Polish lands. `AppLanguage` and `locales_config.xml` are the same claim in two files, so
     `AppLanguageTest` parses the XML and asserts they agree — the drift it prevents is silent, and 3g is
     the worst week to find it.
   - **Gate met:** `spotlessApply`, `assembleDebug`, `test` (153 unit tests, 12 new) and `lint` pass, at
     **0 errors** with no new warnings — the four project-code warnings standing are 3c's and earlier, and
     are 3g's to close. No instrumented tests: the setup rule, the visibility flip and the back-stack repair
     are all pure functions, proven on the JVM and then on the phone.
   - Driven on the Xiaomi, and the full loop was watched: the existing debug install with a year of history
     **never met the wizard** (the absent-record branch doing its job); a cleared install opened on step 1,
     chrome-free and correctly inset; the editor saved Clover and came **back to step 1**, now reading
     "Clover is in"; a force-stop mid-wizard came back to step 1 rather than into the app; the skip path
     finished with no bunny at all and did not re-ask. On disk afterwards, `setup_progress Complete` and
     `backup_scope Everything` — the scope chosen in the wizard, stored as it was picked rather than at the
     Finish button, because an owner who says what they want and then leaves has still said it. The language
     row was taken to English and back: `cmd locale get-app-locales` reported `[en]` and then `[]`, and the
     app came back on the same screen both times.
7. **3g — The gate pass, and what it is allowed to find.** ✅
   - **What landed between 3f and here, unplanned and load-bearing for this checkpoint.** Three things, none
     of them a checkpoint. The four standing lint warnings were **closed rather than restated** — and one was
     a real defect, not a lint opinion: the gallery read strings through `LocalContext.current.resources`, and
     a language switch (ADR-0013) replaces the `Resources` while a cached context keeps the old one, so it
     would have shipped a screen that ignored the switcher 3f had just built. `photo_import_partial` became a
     `<plurals>` keyed on the **unreadable** count, which is the number its trailing clause governs; its
     English items are identical and that is the point, since the structure has to exist *before* 3i and not
     after. The dependency-version notices and `OldTargetApi` dropped to `informational` rather than
     `disable`, so the next bump is still one `./gradlew lint` away but they stop counting against a gate
     about our code. Restore then learned to refuse a traversal entry on **every** API rather than only 14+.
   - And **CI grew an instrumented matrix at API 26 / 34 / 36**, behind one stable required check name, plus
     `BackupRoundTripTest` exporting at all three scopes and restoring onto a cleared phone. That changes this
     gate's mechanics rather than its content: `connectedAndroidTest` stops being a Xiaomi ceremony run at
     boundaries and becomes a per-PR check, and the **`minSdk` floor finally executes** — the pre-S route into
     system backup settings, the pre-R agent branch, the pre-S theme and ADR-0013's pre-13 locale backport are
     code that a single modern emulator never runs at all.
   - **This checkpoint adds no features.** It is Phase 3's gate list driven end to end on the phone, and its
     output is defects plus the fixes they justify. Anything it turns up that is *not* a defect — a rough
     edge, a missing affordance, a screen that looks plain — is written down for 1.1 and left alone
     (ADR-0012), or the gate quietly becomes a scope hole in the release it exists to protect.
   - The list is ~20 bullets, so drive it in **four sittings grouped by machinery, not by screen**, because
     the setup cost is per mechanism: (1) export and restore at all three scopes plus every refusal — a newer
     schema, a `../` entry, a zip that is not a backup, and the pre-restore snapshot **undoing** a restore;
     (2) Auto Backup — the marker's three states including the 14-day stale one, the "photos are not in this"
     copy in both Settings and setup, and **no notification channel and no notification permission** on a
     fresh install, which is a `dumpsys` question rather than a visual one; (3) the media and delete paths —
     bulk import in capture order, cancelled part-way, one unreadable file; deleting a bunny counting photos
     in the destroyed bucket and removing the files; the archived read-only gallery and the "All bunnies"
     chooser; (4) first-run setup on a cleared install, the visibility flip, and debug beside release.
   - **Two links have never been exercised by anything, and they are why this is a checkpoint and not a
     checklist.** First, 3d's **SAF picked-file path**: HyperOS's picker ignores injected input, so every
     restore so far — by test and by hand — has come from a `preserved/` row, and the picked-file path differs
     exactly in where the `InputStream` comes from. It wants a deliberate human tap on a file the share sheet
     put somewhere real. Second, 3b's **pre-13 locale backport**, which the Xiaomi cannot exercise at all
     because it runs Android 16, and which is the half `minSdk` 26 exists to serve.
   - The backport becomes an **instrumented test on the API 26 leg** rather than a claim waiting on hardware
     nobody owns: set the application locales through `AppCompatDelegate` and assert the app resolves strings
     against the configuration that results, below API 33 where the backport rather than the platform is doing
     the work. If it cannot be asserted headlessly — the backport recreates activities and persists through
     its own metadata service — it is recorded as **not observed** and checked by hand at 3i, where there is a
     second language to switch to. What is not acceptable is the release depending on an emulator this machine
     would need a `usermod -aG kvm` and a re-login to run (CLAUDE.md).
   - **Release hygiene that belongs to the code rather than to the store**: `spotlessApply`, `assembleDebug`,
     `test`, the CI matrix green on all three legs, one `bundleRelease` that still succeeds, and the
     **schema-4 JSON git-tagged** (ADR-0007). The JSON is already committed; what is owed is the tag and the
     statement that **4 is the first load-bearing schema** — the file every later migration is written from.
     Lint already reports **0 errors and 0 warnings** after the closure above, so the phase's "must reach
     zero" line is met and the only job here is to hold it there.
   - **A release build cannot destructively wipe** is asserted by a JVM test (3c), because `run-as` does not
     reach a release build. The half a phone *can* show is driven here: force the release variant of the
     schema-mismatch screen in a debug build and confirm it offers **share and no forward button**.
   - **Gate met:** every bullet passed and **nothing was recorded as not observed** — the one
     pre-authorised exception went unused, because `bmgr backupnow` ran on the Xiaomi with **60 MB** of
     files in `files/photos` and still transferred only the database, which is ADR-0005's exclusion
     demonstrated rather than argued. The picked-file restore was driven by hand, through Google Drive
     and back. `spotlessApply`, `assembleDebug`, `test`, `lint` at **0 errors and 0 warnings**, one
     `bundleRelease`, the matrix green at 26/34/36, and schema 4 tagged `schema-4`.
   - **What it found.** Three defects, all fixed here. The **too-new-backup refusal named neither
     version**, so an owner met a file they could not open and no way to say how far ahead it was;
     `RestoreRefusal` became a sealed interface so that one case can carry the pair. The **photo import
     swallowed the reason a file failed**, which turned a corrupt fixture into a half-day's suspicion of
     the media pipeline — the count still goes to the owner, the reason now goes to logcat. And the new
     locale test **left the phone in French**: on 33+ AppCompat drops `setApplicationLocales` when no
     activity is registered while the getter still reads back empty, so its teardown asserted clean over
     a device that was not.
   - **The matrix earned its keep on the first run.** The locale probe passed at API 26 and failed at 34
     and 36: `fr` is not in `locales_config.xml`, and the platform declines an app locale the app does
     not declare, while the backport — and HyperOS — apply it regardless. The probe is now gated below
     33, which is where this checkpoint asked for it. **At 3i that gate should come off**: `pl` is a
     locale the app will declare, and the same assertions should then hold on every leg.
   - **Deferred, and not out of convenience:** *debug beside release, with `installDebug` still working*
     is a bullet about 1.0 being installed from Play, so there is nothing to observe until **3h**.
     Written down for 1.1 (ADR-0012): the missing-photo placeholder is near-invisible in dark theme — it
     renders and does not crash, but a restored Essential gallery reads as a black screen rather than as
     "this photo is not on this phone".
8. **3h — 1.0 to the internal track.** ✅
   - **The `applicationId` moved, and it moved because of an irreversible form field.** Play Console's
     *Create app* screen suggests a package name derived from the app *title*, and the suggestion was
     accepted: the entry was created as `binky.bunny.and.rabbit.tracker` while the app built as
     `app.binky.tracker`. A Console package name cannot be changed afterwards, so the choice was to
     recreate the listing or to move the app. **The app moved** — `applicationId` is now
     `binky.bunny.and.rabbit.tracker` and `namespace` stays `app.binky.tracker`, which is legal because
     the two are different things: one is install identity, the other is where `R` and `BuildConfig` are
     generated. No Kotlin source moved.
   - What that costs, recorded rather than glossed: the Store URL is `?id=binky.bunny.and.rabbit.tracker`
     permanently, a keyword-stuffed *title* is now fossilised in an *identifier* that outlives any title
     change, and it is not reverse-DNS. What it bought: not re-entering App content, Data safety, the
     content rating questionnaire and the listing. The trade was made deliberately with those terms
     stated. It is also the second time this project has moved its `applicationId` — `app.bunny.tracker`
     → `app.binky.tracker` at 0.4.0 — and, unlike that one, this is the last chance: the next move after
     a Play release is not a move, it is a different app with a different listing and stranded users.
   - **The find is the checkpoint working.** Discovered on the upload attempt, at the one moment it was
     still free: nothing published, no tester, no version number anyone keeps. This is precisely the
     class of failure 3a existed to catch and deferred, and the reason 3h orders the RC before 1.0.
   - **3a's deferred half lands here, minus the property it was bought for.** The de-risking is bought back by
     ordering *inside* this checkpoint: the **first upload is a release candidate at whatever version
     release-please has**, not 1.0. It proves the upload key against Play App Signing, the App content answers
     Play cross-checks against the privacy policy, the store listing's completeness, and — the one with a
     calendar cost nobody can shorten — **Play's review of a first-time personal developer account**, all on a
     version number nobody has to keep.
   - Only once that build has **installed from Play on the Xiaomi** is 1.0 cut. The cost of the extra upload
     is one version number; the cost of skipping it is discovering a rejected form or a signing mismatch with
     the 1.0 tag already pushed and the release notes already written.
   - The RC **may stay installed**, which 3a's proof build could not. Its reason for being uninstalled was a
     migration obligation for a schema nobody used, and the schema is now **4** — the same one 1.0 ships — so
     there is no version-specific obligation to avoid. Keeping it is also the only real dogfood of an artifact
     signed the way a user receives it. ADR-0023's obligation attaches when a schema reaches *someone else's*
     device, and the internal track here is this phone.
   - **Real 1.0 screenshots and final copy**, replacing 3a's minimum-viable placeholders, taken from the
     release build **with the debug fixture's data** — screenshots of an empty app photograph no product.
     Five: Home's vitals card with the trend flag, the chart at 90 d, the observation timeline with a shared
     entry, the gallery, and the backup screen. [`docs/store-listing.md`](store-listing.md) stays the
     paste-ready source, and nothing in it may describe a 1.1 or 1.2 feature.
   - **`bundleRelease`, never `assembleRelease`**, with `versionCode` read back out of the AAB's protobuf
     manifest and checked against `git rev-list --count HEAD` — 3a found that trap by falling into it, and the
     failure was a *signed artifact* carrying `versionCode` 1.
   - **Both apps on the phone at once** (ADR-0023): `installDebug` still works with 1.0 from Play installed,
     and the two hold separate data. This is the claim the whole `applicationIdSuffix` decision exists for and
     the first moment it is testable.
   - **Gate:** 1.0 is installable from Play on the Xiaomi, sits beside the debug build with its own data, and
     the listing describes only what 1.0 does. ADR-0019's condition is met at this point — the data is safe
     and it is in someone's hands — and everything after this is additive.
   - **Gate met.** `1.0.0`, `versionCode` **154**, on the Xiaomi with
     `installerPackageName=com.android.vending` — delivered by Play, not sideloaded. It sits beside
     `binky.bunny.and.rabbit.tracker.debug` with its own `dataDir`, and the listing carries four real
     screenshots and describes only what 1.0 does. The `versionCode` was read back out of the AAB's
     protobuf manifest and matched `git rev-list --count HEAD` before upload.
     **ADR-0019's condition is satisfied: the data is safe and it is in someone's hands.**
   - **The update path was proven in the same motion, unplanned.** 1.0 arrived on the phone as an
     **update over the 0.8.0 RC**, not a fresh install, and the restored fixture survived it intact —
     bunnies, weighings, observations, the trend flag. That is the project's first real Play update, and
     it carried a schema-4 database across versions on a build `run-as` cannot reach. It was available
     only because this checkpoint chose to install an RC first; a plan that uploaded 1.0 directly would
     have had to wait until 1.1 to learn anything about updating at all.
   - **What the RC proved, and it is the whole reason for ordering it first.** Every property this gate
     tests was demonstrated on the release candidate before 1.0 had a number anyone keeps: installed
     **from Play** (`installerPackageName=com.android.vending`, not a sideload); `installDebug` succeeding
     **with the Play build present**, the two holding separate `dataDir`s — ADR-0023's central claim, and
     the first moment in the project it could be tested at all; the schema-4 backup **restored into a
     Play-delivered artifact** through the SAF picked-file path, which is 3d's link that nothing had
     exercised; and `BuildConfig.DEBUG`'s sample-data gate holding in release, confirmed by the Sample data
     row being **absent** from the Play build's Settings.
   - **The find that justifies the checkpoint: the `applicationId` was wrong, and it was permanent.**
     Play Console's *Create app* screen suggests a package name derived from the app *title*, the
     suggestion was accepted, and the entry was created as `binky.bunny.and.rabbit.tracker` against an app
     built as `app.binky.tracker`. The upload refused. A Console package name cannot be changed, so the
     choice was recreate the listing or move the app; **the app moved**, with the costs recorded above.
     Discovered on the upload attempt, with nothing published, no tester and a throwaway version number —
     the single cheapest moment it could have surfaced. The same mismatch found after a production release
     is not fixable: a package name change is a different app, a different listing and stranded users.
   - **What else it found.** The welcome screen told a new owner the app keeps "vet visits" and "reminders",
     which are 1.2 and 1.1 — the one surface holding a different standard from the More tab's *Coming soon*
     labels and `care_stub`'s explicit version numbers. The listing claimed **two languages** when 1.0 is
     English-only, Polish being 3i; the Polish listing was held back to 3j with it, rather than put in front
     of an English-only app. And the Data safety answer for the advertising ID turned out to rest on a fact
     with an expiry date — it is safe because **no Play Services SDK is on the release classpath at all**,
     and ML Kit brings GMS in at 1.2, so the answer now carries a re-check trigger instead of being
     inherited.
   - **Four screenshots, not five.** The gallery was dropped: `SampleData.writeSampleJpeg` writes
     solid-colour JPEGs, because the fixture exists to exercise the media pipeline rather than to look like
     anything, so the gallery photographs as four flat rectangles. Shipping that reads as a broken app.
     Play's minimum is two. Captured at the phone's native 1220×2712 and padded to 1526×2713 — Play caps
     screenshot aspect at 2:1 and the raw capture is 2.22:1, so the padding is a requirement rather than a
     style choice.
   - **Deviations from this checkpoint as written, both deliberate.** The RC went to the **closed** track
     rather than internal. Nothing the gate tests depends on which track delivered the build, and the
     12-testers-for-14-days clock does not begin merely because a closed track exists — it counts days with
     twelve testers opted in, so nothing was spent. And the RC's version is **0.8.0, not 0.7.0**: the
     `applicationId` move carried `BREAKING CHANGE`, and while the major is 0 release-please bumps the
     minor. That is the checkpoint working as designed — "whatever version release-please has" is exactly
     the point of not fixing the number in advance.
   - **Two toolchain facts worth not rediscovering.** `aapt2 dump xmltree` cannot read an AAB's manifest —
     it is protobuf, not binary XML — and prints **nothing while exiting 0**, which is how 3a shipped a
     signed artifact carrying `versionCode` 1. `scripts/aab-version.py` now decodes it and asserts against
     `git rev-list --count HEAD`. Separately, the APK Play delivers carries a **v3.2 signature block with
     an ML-DSA hybrid post-quantum signer**, and `apksigner` from build-tools 37.0.0 on JDK 21 cannot
     verify it (`ML-DSA KeyFactory not available`). The device installs it fine; this is a local toolchain
     gap, and any later step that assumes the Play artifact's signature can be checked on this machine will
     fail confusingly. Reading it back over `adb` is the route that works.
   - **Reproduced from 3g, so it is a property rather than a flake:** HyperOS's SAF picker does not accept
     injected input for the roots drawer or search, though it does accept filter-chip taps. The
     picked-file restore needs a human tap, on every invocation, and any plan step assuming otherwise is
     wrong. Separately, a **first** install of a new `applicationId` over USB is refused outright by
     HyperOS — `INSTALL_FAILED_USER_RESTRICTED` returns instantly with no dialog, which reads exactly like
     the documented missed-prompt case and is not it. Updates were always fine, which is why it only
     surfaced here.
9. **3i — Polish.** ✅
   - `values-pl/strings.xml` — **335 strings, 14 plurals** and the breed array, not the ~400 this was
     estimated at and not the 15 plurals counted above either. It lands after 1.0 is on the track,
     deliberately: translating churn twice is the only way to make it more expensive.
   - **"One new file, no code changes" was very nearly true, and the exception is the interesting part.**
     `AppLanguage` gained `POLISH` and `locales_config.xml` gained `pl` — both expected, both the same
     claim in two files that `AppLanguageTest` already guards. The third edit was not expected: `app_name`
     had to be marked **`translatable="false"`**. Leaving the launcher label out of `values-pl` is a
     decision (a launcher label resolves against the *system* locale, so a Polish `app_name` would rename
     the icon on a Polish phone whose owner set Binky to English), but an omission and an oversight look
     identical to lint. The deliberate absence had to be *declared*, not merely left.
   - **The constraint that actually shaped the file was gender, not plurals.** Polish predicate adjectives
     and past-tense verbs inflect, and a bunny's name arrives from the owner in the nominative and cannot be
     declined — so `zdjęcia królicy Zosi` is unreachable when the app can only substitute `Zosia`. Every
     string that interpolates a name now reaches it through a colon, a comma or brackets (`Mieszka z: %1$s`,
     `Przenieść do archiwum: %1$s?`) rather than through a preposition that governs a case, and the same
     trick carries the backup scope names as quoted `„%1$s”`. Predicate adjectives went with it:
     `Mieszka samotnie`, never `Mieszka sam`. This is a deeper version of store-listing.md's rule about
     `co zostało zapisane` — that one is about not addressing half the audience, this one is about not
     guessing at a name the owner chose.
   - **The four plural categories paid off exactly where 3g said they would.** `photo_import_partial` is the
     falsifiable case: its English items are identical and its Polish ones are not — *nie udało się odczytać
     **1 pliku*** against *… **2 plików***, the negated infinitive pulling the count into the genitive. The
     delete ceremony and the participant counts inflect as expected.
   - **Two things came out of the crossing that the English file had already got right for reasons only
     visible here.** The chart's four window strings are in the **locative** in Polish (`ostatnich 30 dniach`)
     and cannot double as the selector's labels — English keeps them as separate resources for a milder
     reason and would have survived merging them; Polish would not. Conversely `gap_*` and `age_*` **collapse
     entirely** — both are `2 lata` — so the distinction English draws between "2 years" and "2 years old"
     has no Polish reflex at all. Kept separate anyway: the English difference is real and the next language
     may reinstate it.
   - **Dates, numbers and weights were a check rather than work, and the check passed.** `WeightFormat` takes
     its locale from `LocalConfiguration` and formats through `NumberFormat`/`DateTimeFormatter`; the chart's
     axis labels go through the same helpers, so `2,45 kg` needs nothing.
   - **3b's locale gate came off, and it came off cleanly.** `LocaleBackportTest`'s probe moved `fr` → `pl`
     and the `assumeTrue` below API 33 is gone, so the same assertions now run on all three CI legs instead
     of one. The fallback claim — an unshipped language resolves to English rather than to nothing — moved to
     a `createConfigurationContext`, because the platform declines an *app locale* it does not declare, and
     that route was never what the fallback depended on. One assertion was added that the old probe could not
     make at all: the app's own strings resolve in Polish, which is what distinguishes a translation in the
     APK from a configuration that merely changed.
   - **Ungating it immediately found a third answer, and the matrix earned its keep twice in two
     checkpoints.** 3f's finding was that 26 applies an undeclared locale and 34 and 36 decline it. With `pl`
     declared, 26 and 36 went green and **34 did not**: the running activity did not pick the locale up
     inside the ten seconds this file allowed, twice — while a later test in the same run resolved Polish in
     1.4 seconds, having *inherited* the override the timed-out test had set. Then **36 went red too**, on a
     run whose only change was a comment — one test, the same ten-second wait, `last seen 'en'`. The
     platform legs are not deterministic here: 34 fails almost always, 36 intermittently, 26 never.
     Applying a per-app locale on 13+ is a request to a system service that recreates the activity when it
     gets to it, and *when it gets to it* is not something a test can wait on honestly.
   - **Two fixes were tried against that and both were reverted, which is the more useful record.** Waiting
     on a freshly launched activity as a second stage did not help. Clearing the override in `@Before`, so
     that no test could start on one, took both platform legs from a slow apply to **no apply at all**
     inside twenty-five seconds: two locale writes in quick succession do not queue, and the clear issued
     moments before the set can be the one that lands last. That is a trap with no symptom other than the
     wrong locale, the same shape as 3f's teardown that asserted clean over a device that was not.
   - **So the gate moved rather than came off, and the file is sharper for it.** The recreate-in-place
     assertions now run **below 13 only** — the backport's own branch, which no hardware here can reach and
     which is the reason the file exists; chasing them on the platform legs was chasing the platform's
     scheduler. What the app actually owes is asserted on **every** leg instead, and directly: that
     `values-pl` is in the APK and resolves, through a configuration context with no app-locale machinery
     in the way. 1.0 could not make that assertion at all, so the platform legs now assert strictly more
     than they did — just not via a wait. Four CI cycles went into learning that, which is the argument for
     writing it down rather than rediscovering it at 1.1.
   - **`PolishTranslationTest`** holds the parity mechanically from here: every translatable resource has a
     counterpart, `values-pl` declares nothing extra, every plural carries all four categories, every format
     argument survives, and the breed lists are the same length. "Read every screen once" is a person's job
     done once; this is the part of it a machine can keep holding afterwards.
   - **The read was done, on the phone, and nothing shipping is in English.** Every screen was walked with
     the app switched through its own switcher — home, weight list and entry, the observation timeline, entry
     form and symptom picker, photos, the archive, settings, backup and restore, both wizard steps, the empty
     states — plus the dialogs and a snackbar, which is the half a screen-by-screen walk usually misses. The
     switcher's claim held on the 13+ half too: the app read `pl` while the phone stayed `en-US`. The pre-13
     half stays mechanical, on the ungated API 26 leg.
   - **Three things the read found are worth keeping.** The seeded symptom table **resolves through
     resources, not through the rows it was seeded with**, so a database seeded in English still lists Polish
     symptoms — the failure this check most expected to find is structurally absent. The **breed list is
     half-untranslated on purpose** (`Angora angielska` and `Zając belgijski` beside `Beveren` and `Blanc de
     Hotot`): registry names are not translated in Polish rabbit keeping either, and `PolishTranslationTest`
     holds the two arrays only to the same length, so this is a decision rather than a gap — written down
     because it looks exactly like one. The only English left anywhere is in **`SampleData.kt`**, a note and
     two photo captions, which is debug-only and reaches no shipped build.
   - **One screen was not reached by hand:** the restore confirmation and the terminal restore screen, which
     need a file chosen through the vendor picker — and the Xiaomi's picker would not take the taps that
     switch to its Downloads root, which is the same class of obstacle as the split-APK prompt. Its strings
     were read out of `values-pl` instead and are complete. It stays owed as a two-minute look the next time
     a restore is driven by hand, which the gate already requires for other reasons.
10. **3j — 1.0.1, and the closed track.** ✅ *(closed on the build; the Console half is carried — last bullet)*
    - **1.0.1 is cut.** Release-please's PR merged, `v1.0.1` tagged, and the bundle checked **against the
      artifact rather than against the config** that was meant to produce it: `versionCode` 164,
      `versionName` 1.0.1, the upload key, and the Polish strings present in `base/resources.pb` — the last
      being the one absence that would make this particular release pointless, and the one 3a's silent
      `aapt2` lesson says to read out of the file.
    - 1.0.1 goes up with Polish, and **that build opens the closed track**. The internal track does not satisfy
      Play's prerequisite; a closed one does, which is the whole reason this is a separate release.
    - **This is where the schema stops being disposable in the sense that matters** (ADR-0023, ADR-0007):
      1.0.1 reaches devices that are not the author's, so schema 4 becomes load-bearing and every later bump
      ships a **tested forward migration** written from the tagged JSON. The machinery is already in place —
      the debug-only destructive fallback, the release consent screen with no forward button, stage-migrate-
      swap on restore — so what lands here is the obligation, not the code. Naming that is the point: the day
      it attaches, nothing in the build changes and everything about a schema edit does.
    - The **12 testers over 14 days** — re-read in the Console rather than trusted from this document — gate
      **production access only**. If twelve have not opted in, 1.0 and 1.0.1 are already released and in use;
      what waits is a button, not the app.
    - The recruiting has been running as a non-code item since 3a. This is where it is either finished or
      honestly re-planned, and it is the one dependency in this phase that working harder cannot move.
    - **Phase 4 can start the day 1.0.1 is up.** The clock is other people's time, not engineering time. What
      the clock does change is that Phase 4's first schema bump is a migration rather than a wipe, and that its
      first notification channel and permission ask will be seen by people who are not the author — which is
      the point of having a track at all.
    - **Closed 2026-08-01 on the build rather than on the upload**, deliberately. Everything that is code,
      tests, artifact and copy is done and verified; what is left is Console work and other people's time,
      which is the exact cost 3j was split out to isolate. Holding the phase open for it would make the phase
      a measure of Play's queue rather than of the work. **Carried, and still owed before 1.0.1 is in anyone
      else's hands:** the bundle uploaded and the closed track opened; the Polish listing entered, with its
      own screenshots, since each locale's are uploaded per listing; **1.0.1 installed from Play on the
      Xiaomi**, which is a gate bullet the artifact check cannot stand in for, because it proves delivery and
      not construction; and the twelve testers. **ADR-0023's obligation attaches on that upload, not on this
      tick** — until the bundle reaches a device that is not the author's, schema 4 is still disposable.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 3c and
3d, the two that add instrumented tests, and again at the gate; `lint` at the gate. This is the phase where
lint must reach **zero project-code warnings that are not a stated standing decision** — two of Phase 2's
four were 3e's to close, and the remaining four were closed between 3f and 3g, so the report now stands at
**0 errors and 0 warnings** and the job from here is to hold it there.

**From 3g on the instrumented suite is CI's**, on emulators at API 26 / 34 / 36 for every pull request,
behind one stable required check name. The Xiaomi run stays at the gate anyway, and not out of ceremony: an
emulator has no HyperOS split-APK prompt, no background killer, no vendor SAF picker and no Play install
path, which is four of the things this phase has actually been bitten by.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **3a**: ADR-0009,
0019, 0023, 0012. **3b**: ADR-0013, 0012. **3c**: ADR-0020, 0015, 0004, 0007, 0023. **3d**: ADR-0005, 0023,
0020. **3e**: ADR-0005, 0001, 0007. **3f**: ADR-0006, 0015, 0019, 0013. **3g**: ADR-0005, 0019, 0007, 0023.
**3h**: ADR-0009, 0023, 0012. **3i**: ADR-0013, 0001. **3j**: ADR-0009, 0007, 0023.

**Gate:**

- Export at **each** of the three scopes, clear app data, and restore each one: what the scope promised is
  present, and what it excluded degrades gracefully — placeholders in the grid, the pager, the switcher and
  Home's card, never a crash. Preferences survive the round trip, so the restored app remembers its display
  unit, its selected bunny and its backup scope.
- An **Essential** restore onto a phone that still holds its photo files **keeps those photos**, rather than
  turning the gallery into placeholders.
- A backup at a **newer** schema version than the build is refused with both versions named, and the
  database on the phone is untouched.
- A zip carrying a `../` entry, and a zip that is not a backup at all, are each **refused by name** with the
  database on the phone untouched — and the confirmation dialog's scope comes from the manifest, so renaming
  an export's file does not change what restore claims it contains.
- A restore leaves a restorable Essential export of the replaced state in `preserved/`, listed in Settings
  as what it is and distinguishable from a wipe copy, and **restoring it undoes the restore**. `preserved/`
  still holds that snapshot afterwards — no restore path writes to it, and no export scope contains it.
- A restore ends on the terminal screen, and the app reopened by hand shows the restored data.
- A device that has never run Auto Backup **says so in words**, with a button into system backup settings;
  a marker older than 14 days reads as **stale** rather than as a bare date.
- A restore does not carry the source phone's backup timestamp onto the target.
- A backup taken with a deliberately large photo gallery still **succeeds**: photos are out of the set, and
  the database and avatars are in — the whole reason ADR-0005 excludes them. If `bmgr` cannot be driven on
  the Xiaomi, this is recorded as **not observed** rather than as passed.
- Settings and first-run setup both state, in words, that **photos are not in the automatic backup**.
- No notification channel exists at 1.0, and **no notification permission is requested**.
- Deleting a bunny counts its photos in the destroyed bucket, with correct pluralisation, and removes the
  files as well as the rows.
- A bulk import of photos spanning years lands in **capture order**, not import order; cancelling one
  part-way keeps the photos already added; and an unreadable file is skipped and reported rather than
  aborting the batch.
- The gallery is read-only in the `Archived(id)` scope, and asks which bunny under "All bunnies".
- The **debug and release apps are both installed on the Xiaomi at once**, holding separate data, and
  `installDebug` still works with 1.0 on the phone.
- A release build **cannot** destructively wipe: asserted by test, since `run-as` does not reach a release
  build, and the release consent screen's no-forward-button variant is exercised by forcing it in a debug
  build.
- First run reaches **both** steps, the skippable one is genuinely skippable, and the backup scope chosen
  there is what the export sheet defaults to afterwards. Setup does **not** appear on an install that
  already has bunnies, nor on a phone that has just restored a backup.
- **No bottom-navigation tab opens onto a stub** — Care & Meds is absent, not "coming soon".
- Every screen in Polish with no English left behind, and the switcher changes the app's language without
  changing the phone's — on a pre-13 device as well as a 13+ one, since the backport is the whole reason 3b
  exists.
- A restore driven from a file chosen through the **system picker**, not only from a `preserved/` row — the
  one link in ADR-0005's chain that no test on this device reaches.
- Schema **4**'s exported JSON is committed and **git-tagged** (ADR-0007), and CI's instrumented matrix is
  green at API 26, 34 and 36 — the floor leg being the only place the pre-S and pre-13 branches run at all.
- Then the releases themselves — a **release candidate** proving the upload path before the version number
  that matters, then **1.0 English on the internal track**, and **1.0.1 with Polish**, all installable from
  Play on the Xiaomi, with the closed track opened on 1.0.1.

**Gate met**, with the three bullets that live in the Console carried into Phase 4 rather than blocking it —
1.0.1 installed from Play, the closed track opened, and the Polish listing's screenshots (3j's last bullet).
Everything else was proved at 3g and re-proved at the close: `spotlessApply`, `assembleDebug`, `test` and
`lint` pass, lint holding at **0 errors and 0 warnings**, the instrumented matrix green at API 26 / 34 / 36,
schema 4's exported JSON tagged, and — the bullet 1.0 could not satisfy at all — **every screen read in
Polish with no English left behind**, on a phone whose own language never changed.

## Phase 4 — Care reminders and watch — ships as 1.1 ✅ *(closed on the build; delivery evidence and the Console half are carried into Phase 5)*

Care reminders depend only on a bunny existing, and use the simpler mechanism. Building them first
establishes the notification channel, permission flow, reboot rescheduling and Xiaomi battery-exemption
prompt on easy ground, so dose reminders later add only the exact-alarm path.

**Three things are different from every phase before this one, and each of them costs something.** The
schema is **load-bearing**: 1.0 is installed from Play on the author's phone and holds real bunny history,
which is exactly the dogfood case ADR-0007 names, so version 5 arrives by a **written, tested migration**
and not by a wipe — the first one this project has ever had to write. The app ships **two locales**, so a
new string is not finished until it exists in both, and `PolishTranslationTest` makes that a red build
rather than a memory. And the app **posts notifications for the first time**, which means the first runtime
permission, the first manifest permission since Phase 1, and the first change to the Play answers 3a
verified against an artifact that declared none.

The other novelty is quieter and is a trap ADR-0007 wrote down in advance: its structural wipe guard exists
because a container forced before consent destroys data with nobody looking, and the ADR names the future
that breaks it — *"a project that goes on to add reminder rescheduling at process start"*. This is that
phase. The OS can now start this process to run a worker, with no UI and no owner present, and any worker
that touches a repository forces the container. The worker therefore checks the pending-schema state first
and does nothing if one is pending; the reminder is rescheduled when the owner next opens the app.

**Everything scheduled in this phase is one worker** (ADR-0024). Care reminders, the watch nag and 4e's
export reminder are all derived at fire time by a single daily sweep, enqueued as unique work under one
name and left enqueued permanently. That is the decision the rest of the phase is shaped by: there is no
per-reminder scheduled work to cancel, orphan or lose, the boot receiver has exactly one job, and the
overnight-Doze gate below is aimed at one target instead of three. Phase 5's dose alarms deliberately do
**not** follow it — ADR-0003 gives them exact alarms for a reason, and ADR-0024 records why the two halves
diverge.

- Care reminders on WorkManager, rescheduled after reboot.
- Repeat handled as "complete → record the care event → schedule the next", not an OS periodic trigger.
  Completion can be **back-dated** (did the nail trim yesterday, log it today) on the same terms as Phase 2
  entry; the next occurrence is scheduled from the recorded completion, not from when it was ticked off.
- A care reminder is `{label, interval, optional type}` (ADR-0018): the closed `CareType` enum tags only
  the known kinds — presets nail trim (6 weeks), vaccination (yearly), weigh-in (weekly), which map to
  calendar RRULEs and icons — while a custom reminder is a free-text label plus an owner-chosen interval.
- **The interval is a calendar interval, `{count, unit}`**, not a day count. "Yearly" then means the same
  date next year rather than 365 days later, and the `RRULE` hand-off maps one-to-one instead of
  approximating. The cost is `java.time`'s clamping, taken deliberately: see 4b.
- Watch: opt-in per bunny and **time-boxed** — the owner sets a duration when starting it (default 7 days)
  and it **auto-expires** with a prompt to extend or close, never silently persisting into wallpaper
  (ADR-0001). Only while active does the app chase for fresh observations: a **once-daily best-effort
  notification** framed about the owner's checking, not the bunny's state, and **satisfied by any
  observation logged for that bunny in the last 24 hours**. A missed watch nag is low-stakes, so
  best-effort delivery is fine — it needs none of the exact-alarm treatment doses get.
- The trend flag and the Watch are **connected in both directions**, now that both exist. The flag carries
  a *Start a watch* action pre-filled with the default duration — **offered, never automatic**, because
  "worth a closer look" is already the flag's voice and a button acting on that sentence presumes less than
  the sentence does (ADR-0001). The auto-expiry prompt shows the **current trend**, since "is it still
  dropping" is exactly what the owner is being asked. And a bunny under an active watch is **excluded from
  "Log a healthy day" pre-selection**, with the reason stated — the one unreviewed write path must not
  sweep a separated, ill bunny into a shared tray fact (ADR-0008).
- **Notification delivery has three honest states, not two** — blocked, best-effort, armed — resolved from
  the notification permission, the channel's importance and the battery exemption. ADR-0003's honest-state
  rule covers the *soft* failure; a denied permission or a muted channel is the **certain** one, and the app
  can detect both. Building all three now means Phase 5 inherits the framing for doses rather than covering
  one case in three.
- Battery-optimisation exemption requested here, at the point something is first scheduled, and asked
  **once**. Autostart is offered alongside it and never claimed either way, because nothing can read it
  (ADR-0003's amendment, written in 4a).
- Care reminders optionally hand off to the owner's calendar, one-way, no permission (ADR-0014).
- The **remembered-folder export destination**, deferred from Phase 3 (ADR-0005): `ACTION_OPEN_DOCUMENT_TREE`
  with a persisted URI permission, and the plan's longest-standing unverified assumption finally tested on
  the device — whether Google Drive's provider accepts writes. It lands here rather than at 1.0 because
  remembering a folder saves two taps and does not make export automatic; what makes it worth something is
  the recurring reminder this phase adds, which is also the thing that turns a manual export into a habit
  the owner does not have to hold. The share sheet remains the path that cannot fail for provider reasons.
- **First-run setup gets its third step** (ADR-0006), which has been waiting for 1.1 by name since Phase 3:
  our own screen explaining what reminders are for, with an opt-in that then triggers `POST_NOTIFICATIONS`
  — never the bare system dialog. Android permits **two denials** before the permission is refused for good,
  and this phase spends the first one, so the wizard step and ADR-0006's point-of-use ask are built as **one
  composable in two hosts** rather than two asks that can both fire. Note who actually sees which: every
  install that exists today has already finished setup, so for 1.1 the point-of-use path is not the fallback
  — it is the only path anyone takes.
- **Care & Meds goes `Live`** — the one-value flip back that 3f left in place deliberately, with the key, the
  entry and the screen all still present. `StubScreen` loses its last caller and is deleted with the
  `care_stub` string, which is the other half of 3f's rule: a top-level tab opens onto something real or it
  is hidden, and there is no third answer.
- **Schema → 5, by migration.** The first hand-written `Migration` in the project, written in the same commit
  as the first care table and rewritten in place as the shape churns, rather than saved up for the release.

- **Edge-to-edge, verified rather than implemented — its own checkpoint.** Play Console raises this against
  every app targeting SDK 35+, and the notice is generic advice rather than a detected defect: `MainActivity`
  already calls `enableEdgeToEdge()`, every screen's `Scaffold` owns the insets with its `TopAppBar` passing
  `WindowInsets(0, 0, 0, 0)` so they are not applied twice, and `SchemaMismatchScreen` — the one screen that
  lives outside a `Scaffold` — uses `safeDrawingPadding()`. The mechanism is in place. What is owed is
  evidence, which is why this is a checkpoint of its own and not a line item inside another.

  What has actually been *looked at* is one device, portrait, gesture navigation — 1.0's screenshots, which
  render correctly. That is evidence for one cell of the matrix. Untested: **landscape**, where a punch-hole
  or notch stops being a top-edge concern and `displayCutout` arrives on a side; and **three-button
  navigation**, whose bottom inset is far taller than the gesture pill's, and therefore the configuration
  most likely to put a nav bar over a button.

  Locking to portrait is not the escape hatch it looks like. Nothing locks orientation today, and adding
  `screenOrientation` would not help where it matters: **Android 16 ignores orientation restrictions on
  large screens**, and `targetSdk` is 36, so landscape is in scope on tablets and unfolded foldables no
  matter what the manifest asks for. Nor can enforcement be deferred behind a compatibility flag — Android
  16 removed `windowOptOutEdgeToEdgeEnforcement`, and it is already live on the test device. The only
  question left is whether every screen survives it.

### Checkpoints

**Eight.** The ordering is deliberate four times over. The **plumbing goes first, on an empty database** —
3a's lesson repeated, proving the path while the payload is boring: the sweep, the channels, the permission
and the boot receiver are all provable with a debug-only "remind me in two minutes" action, and proving them
there rather than underneath the first real reminder means a missed notification later has one suspect
instead of two. The **migration lands with the first table it has to carry**, not at the end, because a
migration written under release pressure is the one that gets written once and looked at never. The **watch
comes after care reminders** even though ADR-0001 makes it the more interesting half: it reuses the
notification plumbing, it needs the trend flag's slot that already exists, and it is the piece whose failure
mode is *nagging a real owner*, which is the last thing to build on ground that is still moving.
**Edge-to-edge is verified after the new screens exist**, since a matrix checked before them is a matrix
that grows afterwards.

**The schema rule for this phase, stated once.** Version 5 is reached by `MIGRATION_4_5`, written in the
same commit as the first care table. As the shape churns across 4b–4d the version does **not** climb —
`5.json` is regenerated in place and the migration rewritten to match, which is exactly the "rewriting
pending migrations is still fair game" ADR-0007 grants the debug build. The debug app keeps wiping through
the consent screen on each churn, and that is fine; what must stay true is that a **release-shaped open of a
schema-4 file succeeds**, asserted by test. That guard is **instrumented** — this project has no Robolectric
and is not adding one to hold a single assertion — so what makes it always-on is **CI's per-pull-request
matrix at API 26 / 34 / 36**, which is the granularity that actually matters: nothing broken reaches `main`.
Version 5 is frozen and its JSON git-tagged at 4g, and only there.

**Polish is no longer a checkpoint.** 3i was a multi-session writing task because ~400 strings arrived at
once; here they arrive a handful at a time, and `PolishTranslationTest` already fails the build on a missing
counterpart, a missing plural category and a dropped format argument. So translation is a per-commit
obligation rather than a phase of its own — which is the whole return on having paid for that test.

1. **4a — Reminder plumbing, proven while the payload is boring.** ✅ No schema change at all, deliberately:
   the plumbing lands before the migration era starts, so a failure in either has only one explanation.
   - WorkManager enters `libs.versions.toml` here and nowhere earlier.
   - **On-demand initialization**, not `androidx.startup`: the default initializer node is removed from the
     manifest and `BinkyApplication` implements `Configuration.Provider`. The default runs WorkManager's
     initializer at process start, ahead of nothing in particular — and this app's `Application.onCreate` is
     where ADR-0007's guard lives. Initialization order between the two should be a decision, not whatever
     the merged manifest happens to produce.
   - **The sweep** (ADR-0024): one worker, enqueued as unique work under one name, which resolves the
     app-wide reminder time, does its pass, and enqueues tomorrow's run before returning. It stays enqueued
     even when nothing is scheduled, and no-ops. The alternative — cancel it when the last reminder goes,
     re-enqueue on the first write — has a silent failure mode of exactly the kind ADR-0003 refuses: one
     write path that forgets to re-arm means no reminders ever again, with no error and nothing on screen.
     The invariant to hold, and to assert at the gate: **exactly one enqueued work item exists at any time.**
   - **The sweep begins by asking whether the container is safe to force**, and returns success having done
     nothing when a schema mismatch is pending. This is the ADR-0007 hazard named in the intro, and with one
     worker it is one helper called from one place; the sweep is re-enqueued on next launch, which is the
     same path the boot receiver already uses.
   - **Two notification channels**, `care` and `watch`, created at first use. Two rather than one because a
     channel is the owner's only per-kind control, and an owner who mutes a daily watch nag must not thereby
     mute an annual vaccination. Two rather than three because doses are Phase 5's and a channel with
     nothing behind it is a settings row that describes a lie. **Both at `IMPORTANCE_DEFAULT`**, with no
     sound, vibration or light overrides. Android lets the owner lower a channel and never lets the app
     raise it, so this is chosen once and permanently: two channels exist to give the owner *separate
     controls*, not because the two things ship at different volumes, and creating `watch` at `LOW` would be
     making the mute decision on their behalf in the one direction that cannot be undone. `HIGH` is spent
     nowhere in this phase, so Phase 5 can escalate doses to it as a real signal rather than as the level
     everything already sits at.
   - **The three-state delivery resolver**: a pure function over `(permission granted, channel importance,
     battery exemption)` returning **blocked** (no permission, or `importance == IMPORTANCE_NONE`),
     **best-effort** (exemption unconfirmed) or **armed**. Blocked is not a hedge — it is certain, and it is
     detectable without any permission — so its copy says reminders will only appear in the app and offers a
     deep link. A blocked state does **not** block creating reminders: the Care screen carries overdue state
     on its own, so the reminder is still worth having. It just must not claim it will notify.
   - `POST_NOTIFICATIONS` (API 33+) and `RECEIVE_BOOT_COMPLETED` enter the manifest — the app's first
     permissions since the `FileProvider`, and the reason 3a's *"declares no user-facing permission at all"*
     stops being true. `docs/play-app-content.md` gains a note here and is re-verified against the artifact
     at 4h.
   - The **permission ask is one function**, called from the wizard's third step and from the point-of-use
     path, and it is the only caller of `requestPermissions` in the app. It also has to recognise the
     **permanent refusal** — not granted, and no rationale to show, means the system dialog will never
     appear again, so the ask deep-links to app settings instead of firing a request that silently does
     nothing. ADR-0006's two-denial arithmetic is about not spending both; this is the case after they are
     spent, and it is the one the owner actually hits.
   - **First-run setup's third step** (ADR-0006), appended to 3f's stack of two, and **the same composable**
     the point-of-use path hosts as a sheet. Skippable; skipping is re-asked at the point of use and nowhere
     else. Existing installs are **not** re-onboarded — an upgrader is indistinguishable from a skipper, and
     ADR-0006 already answers that case.
   - The **boot receiver** re-enqueues the sweep and nothing else. It reads no persisted schedule, because
     there is none: the due date is derived, and the OS schedule was never the source of truth.
   - **Battery-optimisation exemption, without the permission.** `PowerManager.isIgnoringBatteryOptimizations`
     needs none to *read*, so the app detects the state and deep-links to Android's battery-optimisation
     settings and — where the intent resolves — Xiaomi's autostart screen. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     is **not** declared: Play restricts it to apps whose core function is the exemption, and this app's core
     function is a rabbit's weight chart. The ask happens **once**, at first schedule, where the reason is
     visible (ADR-0006), not in onboarding; if declined it is never auto-asked again, and the delivery-state
     line carries a tappable fix instead.
   - **ADR-0003's honest-state rule applies to care reminders too**, one phase before it becomes critical,
     and it gets **narrowed here in writing**. The ADR conditions the honest framing on battery exemption
     *and autostart*; autostart has no readable state on HyperOS, so a strict reading makes "armed"
     permanently unreachable on the only device this project tests on, and a permanent hedge is wallpaper in
     the same way a permanent nag is. So armed depends on the **detectable** exemption, autostart is offered
     once and claimed never, and 4g's overnight-Doze run is the evidence that stands behind it. Recorded as
     an amendment to ADR-0003 rather than left to code, in the style ADR-0006 already carries one.
   - A **debug-only "remind me in two minutes"** action beside the sample-data one, on its own one-shot path
     rather than through the sweep. It is what makes this checkpoint provable with no reminders in existence,
     and it stays afterwards as the fastest way to re-prove delivery after any change to it.
   - Tests, JVM: the pending-schema guard as a pure predicate; the delivery-state resolver as a case table
     across all three states; the channel definitions against `strings.xml` in both directions, the same
     shape as `AppLanguageTest`.
2. **4b — Care reminders: schema 5, the first real migration, and the data layer.** ✅
   - `CareReminderEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `label: String`, `type: CareType?` stored
     by name, `intervalCount: Int`, `intervalUnit: CareIntervalUnit` stored by name, `firstDueOn: LocalDate`,
     `notifiedForDueOn: LocalDate?`, `createdAt`, `calendarHandedOffAt: Instant?`. `type` is nullable because
     ADR-0018 says a reminder with no type is normal and not a data error; the preset labels are `strings.xml`
     keys resolved at read time, while a custom label is literal text — the same seeded/owner-added split
     ADR-0010 draws for symptoms.
   - `CareEventEntity` — `id`, `reminderId` FK `CASCADE` indexed, `completedOn: LocalDate`, `note: String?`,
     `createdAt`.
   - **Care dates are `LocalDate`, not `Instant`** — a new converter, stored ISO. This is the first place in
     the app where the distinction is real: a weighing happens at a moment, a nail trim happens on a *day*,
     and "I did it yesterday" stored as an instant shifts to a different day the first time the owner opens
     the app in another timezone. ADR-0003 already calls care reminders day-granularity; this is that,
     enforced by the column type rather than by everyone remembering.
   - **The due date is derived, never stored** — latest `completedOn` plus the interval, else `firstDueOn`
     **unmodified**. The anchor is a *due date*, not a pseudo-completion: the form asks "when is this next
     due?", which is the question an owner can actually answer from a vet card, where "when did you last
     vaccinate?" is a subtraction they have to do in their head and often cannot. An owner who does know when
     it was last done records that as a real `CareEventEntity` row, where it is visible and correctable,
     rather than as a field that silently moves the schedule.
   - **Overdue does not drift.** Completing a reminder three weeks late schedules the next occurrence from
     the recorded completion, not from the date it was originally due — a nail trim done late resets the six
     weeks, it does not owe them.
   - **The day-of-month is allowed to walk, and that is the decision.** Calendar units bring `java.time`'s
     clamping: `31 January + 1 MONTH` is 28 February, and completing on the 28th then schedules 28 March, so
     one short month relocates a monthly reminder permanently. The alternative is storing an intended
     day-of-month beside the completion history — two facts that can disagree, which is the pattern ADR-0002
     and ADR-0001 push this project away from — to save three days on a hay reorder. Following the completion
     is also the rule the phase already committed to for late completions; this is the same rule meeting a
     short month.
   - `nextOccurrence` as a **pure JVM function** over `(anchor or last completion, interval, LocalTime,
     ZoneId, now)` returning an `Instant`, resolved fresh in the device's current zone every time it is
     called. Day-granularity means DST cannot double or drop an occurrence the way it can a dose, but the
     zone still decides which instant "09:00 on the 14th" is, and PLAN's Verification section already names
     DST-boundary arithmetic as a JVM test target. The constants and the reasoning live in this one file.
   - **A weigh-in reminder's last completion also counts weights.** For `type = WEIGH_IN` it resolves as
     `max(latest CareEvent.completedOn, latest weight.recordedAt as a date)` for that bunny — read-side only,
     writing nothing. Otherwise the app asserts a task was not done while holding the record proving it was,
     which is ADR-0001's principle running in the other direction and checkable besides. `nextOccurrence`
     stays pure; it simply receives a `lastCompletedOn` the repository computed from two sources. Nothing is
     stored, so back-dating or deleting a weight heals the schedule for free.
   - **One app-wide reminder time-of-day**, `AppPreferences`' **fifth** key, defaulting to 09:00 — and it is
     the sweep's time, so changing it re-enqueues the sweep. Per-reminder clock times would be promising a
     precision ADR-0003 deliberately reserves for doses, and would need the exact-alarm path to mean anything.
   - **A due reminder notifies once, and never again** — recorded as `notifiedForDueOn`, the due date the
     notification was posted *for*, compared against the derived due date. Storing *when* it notified would
     have to be cleared on every path that moves a due date — a completion, an edited or deleted care event,
     an edited interval, and for a weigh-in a back-dated weight that writes nothing to the reminder at all —
     and a missed clear fails silently in the worst direction. Comparing against derived truth needs no
     clearing anywhere and heals on every one of those paths. The Care screen carries the overdue state
     indefinitely; the notification does not repeat, because a notification that re-fires daily for a nail
     trim is precisely the wallpaper failure ADR-0001 rejects for the watch, and the argument does not change
     because the subject does.
   - **`MIGRATION_4_5` ships in this commit**, with `room-testing` and a `MigrationTestHelper` instrumented
     test that opens a schema-4 database built from `4.json`, migrates, and reads **every table's rows back**
     — the assertion is data survival, not merely that nothing throws.
   - **And a fixture the app did not synthesise.** `MigrationTestHelper` builds v4 from the app's own
     description of v4, which proves the migration consistent with the JSON, not with a file 1.0.1 wrote. So
     a **schema-4 backup zip produced by the shipped 1.0.1 build**, carrying fabricated bunnies and never
     real history, is committed as a test asset and restored through 3d's staged path in an instrumented
     test — the same trick as `rotated_quadrants.jpg`, for the same reason: the thing under test is exactly
     the discrepancy a synthesised input cannot contain. Together with the always-on
     `buildBunnyDatabase(allowDestructiveMigration = false)` guard, that runs the real migration against a
     real artifact on every pull request, instead of once by hand at 4h. 3c's `allowDestructiveMigration`
     parameter — added for 3d's staged restore — is what makes it expressible without a release build.
   - `recordCounts` gains its fourth contributor: care reminders and their events are **sole-owned**, so they
     land in the destroyed bucket (ADR-0004).
   - Tests, instrumented: reminders and events cascade with their bunny, and events with their reminder;
     `LocalDate` round-trips; `recordCounts` counts both; the migration and fixture tests above. JVM:
     `nextOccurrence` as a case table — a completion back-dated across a DST boundary, an overdue completion
     resetting rather than owing, `31 January + 1 MONTH` and `29 February + 1 YEAR` clamping, the anchor path
     for a reminder never completed, and `notifiedForDueOn` going stale the moment a completion moves the
     date.
3. **4c — Care reminders: the screen, completion, the calendar hand-off, and the tab flip.** ✅
   - The Care list per bunny: due, overdue and scheduled, each row naming its next date in words rather than
     a bare date, and carrying the delivery state from 4a rather than presenting as an armed alarm. Add /
     edit / delete a reminder, delete behind **one** confirmation (ADR-0004's two-stage ceremony is
     calibrated to a bunny's whole history, not to a reminder).
   - The three presets — nail trim 6 weeks, vaccination yearly, weigh-in weekly — as `CareType` entries with
     icons and `strings.xml` labels, plus "something else" for the free-text path (ADR-0018).
   - **Completion, back-datable** on the same terms as Phase 2 entry: defaults to today, past allowed,
     future rejected with the reason stated. Recording the completion is what schedules the next occurrence —
     there is no OS periodic trigger anywhere in this design. Completing a reminder also **cancels its posted
     notification**: with "notifies once and never again", a stale notification in the shade for a task
     already done is the only copy of that lie left.
   - **A weigh-in reminder's completion opens the weight entry form** rather than writing a bare tick. A
     weigh-in marked done with no weight behind it is the one outcome that makes the reminder pointless, and
     it is reachable by accident the moment the button is a generic "Done".
   - A per-reminder history of care events, editable and deletable, since a completion recorded on the wrong
     day moves every future occurrence. For a weigh-in it also lists the **weight-derived** completions —
     *"Weighed 2 380 g"* — read-only there and editable in Weight, so the history cannot show a completion
     with no visible row behind it.
   - **Add to calendar**, per reminder (ADR-0014): `ACTION_INSERT` with title, all-day begin, and an `RRULE`
     from the interval (`FREQ=YEARLY` for vaccination), **no calendar permission**. It lives here rather than
     with 4e's export hand-offs because ADR-0014 is a decision *about care reminders* — it argues the yearly
     vaccination is where in-app scheduling is weakest — and because a button on this screen should be built
     while this screen is open, not two checkpoints later. The app does not own the event — no event id
     stored, editing in-app changes nothing out there — so `calendarHandedOffAt` exists only to let the
     button read "Added to calendar" instead of silently minting a second event on a second tap. Guard the
     `startActivity` and fail with a message, never a crash.
   - **The tab flips to `Live`** and `CareAndMedsScreen` stops calling `StubScreen`; `StubScreen.kt` and
     `care_stub` are deleted. **The nav key's name does not change** — `@Serializable data object CareAndMeds`
     is persisted back-stack state, and renaming it would make a stack saved by 1.0 unresolvable on 1.1. Only
     the tab's *string* moves, to "Care" for 1.1 and back at 1.2, because a tab labelled "Care & Meds" with no
     medications behind it advertises a feature that is not there — the same objection 3f's rule makes to a
     stub, one layer up.
   - Under **"All bunnies"** the tab asks which bunny first, reusing 2f's `ChooseBunnyDialog` — care is
     individual data, like weight and photos (ADR-0015).
   - In the `Archived(id)` scope the list renders read-only, and the sweep skips archived bunnies entirely:
     an archived bunny has died or been rehomed, and a notification about its nail trim is the same failure
     ADR-0001 names for the flag on a memorial page. There is nothing to cancel — with one sweep there is no
     per-bunny work — so this is a fact about the derivation, and asserted as one.
   - Notification tap targets, decided here because this is where the first real one exists: a tap **writes
     the app-wide selection** through the same `AppContainer.select` the switcher uses, then hands
     `NavDisplay` a back stack (3f's repair function). `CareAndMeds` takes no arguments, so selecting that
     bunny is the only way to show their reminders, and landing on a different bunny's Care screen would be
     the app lying about whose data is on screen. A notification naming a since-archived bunny falls back to
     Home without changing the selection, since `Archived(id)` is deliberately never persisted.
   - **One notification per reminder, bundled under a group summary** on the `care` channel, with ids derived
     from the reminder id — three reminders due across two bunnies at 09:00 is otherwise three unrelated
     notifications, and a stable id means a sweep that runs twice before `notifiedForDueOn` commits replaces
     rather than stacks.
   - The **care half of the sample-data action**: an overdue nail trim, a vaccination due in months, and a
     weigh-in with a completion history — so 4f has real rows to render and 4g has something overdue to look
     at.
4. **4d — The watch, and its two connections to the trend flag.** ✅
   - `WatchEntity` — `bunnyId` as primary key **and** FK `CASCADE`, `startedAt`, `endsAt`,
     `lastNaggedOn: LocalDate?`. The primary-key-as-FK shape is `TrendAcknowledgmentEntity`'s precedent
     (2a): at most one live watch per bunny, and discard-on-delete as a database constraint rather than a
     rule someone has to remember. `MIGRATION_4_5` is rewritten to include it — the pending-migration rule
     doing exactly what it is for.
   - **A watch is a present-tense state, not a record.** Closing deletes the row; there is no watch history.
     Accepted rather than overlooked: the same family as the flag being derived on read (ADR-0001), and a
     history nothing reads is a table that has to be migrated forever.
   - **Every resolution disposes of the row.** Extend rewrites `endsAt` and clears `lastNaggedOn`; close,
     dismiss, or swiping the prompt away **deletes** it. That is what makes "prompts once" true without a
     column recording it, and it stops an unanswered expired row from occupying the only slot that bunny has.
     Nothing is lost by treating dismissal as closing, because starting a new watch is the same single tap as
     extending. The start path is an **upsert** regardless, so no stale row can block a new watch.
   - **Duration is preset chips — 3 / 7 / 14 days, defaulting to 7**, not a free-form number. A free-form
     field invites a 90-day watch, which is the silent wallpaper ADR-0001 time-boxed the feature to prevent.
   - **An active watch is visible on that bunny's Home card** — *"Watch active · 4 days left"* — with
     close-early there. A background state that only announces itself by nagging is one the owner cannot turn
     off at the moment it annoys them, and that is how a feature gets muted at the channel instead, taking
     its channel-mate's reliability with it.
   - **`lastNaggedOn` is why "once daily" is true.** The sweep can run more than once a day — a retry, a
     reboot, a doze window closing — so the once-a-day property has to be a recorded fact, not an assumption
     about the scheduler.
   - **The nag rides the morning sweep, and satisfaction is a rolling 24 hours.** Any observation logged for
     that bunny in the last 24 h suppresses it, so an owner who logged at 20:00 is not chased at 09:00. The
     window is rolling while `lastNaggedOn` is calendar-day, deliberately: they answer different questions —
     "have they looked recently" and "have I already chased today" — and 4d asserts them separately. Morning
     rather than evening is a medical choice, not an architectural one: a watch is running because something
     may be wrong, and for a rabbit that is most likely GI stasis, a same-day emergency. A nag at 19:00
     surfaces "nobody has looked at Bijou today" at the hour every vet is closed.
   - Its copy is about the owner's checking — *"Have you checked on Bijou today?"* — and never a claim about
     the bunny (ADR-0001). Tapping it opens the observation form pre-filled for that bunny, which means a
     `PendingIntent` has to resolve to a **back stack** and not just to the Activity; 3f's back-stack repair
     function is the precedent for handing `NavDisplay` a list it did not build itself.
   - **Auto-expiry**: past `endsAt` the watch stops nagging immediately, and the app prompts once — extend or
     close — **showing the current trend**, because "is it still dropping" is the question being asked
     (ADR-0001). The prompt is a dialog on first app open after expiry, queued one at a time if several
     expired; the nagging has already stopped by then, so it is never the urgent thing. An unanswered prompt
     is not an active watch. Expiry is what stops the nagging; the prompt is only about re-arming.
   - **The flag's `secondaryAction` slot gets its occupant.** `TrendFlagUi.kt` has carried it since 2c with a
     comment naming this checkpoint, so *Start a watch* is a parameter passed at three call sites — dialog,
     weight banner, Home card — and not a change to the composable. Offered, never automatic.
   - **`ParticipantExclusion.UNDER_WATCH`**, which 2f built the road for: one enum entry, one `when` branch
     and one string, with the reason shown — *"Clover is under a watch — log for her separately."* The
     unreviewed one-tap write path must not sweep a separated, ill bunny into a shared tray fact (ADR-0008).
     A **flagged** bunny is still covered, as 2f decided; a **watched** one is not. The distinction is that
     the flag is about weight and the watch is about the owner having separated this bunny out.
   - No watch on an archived bunny, and archiving closes any active one (ADR-0004).
   - Tests, instrumented: the watch cascades with its bunny; `lastNaggedOn` suppresses a second nag the same
     day and not the next; an observation logged at 20:00 suppresses the following morning's nag while one
     logged 30 hours earlier does not. JVM: `preSelectParticipants` with a watched housemate excluded by
     reason; watch state resolved from `(row, now)` as a pure function, so active / expired / absent is a
     table.
5. **4e — Backup: a destination worth remembering, and the nudge that makes it one.** ✅ Both halves live in
   Backup settings, and both were deferred from 3d to the same place for the same reason.
   - **The remembered export folder** (ADR-0005): `ACTION_OPEN_DOCUMENT_TREE` with a persisted URI
     permission, `AppPreferences`' sixth key. **The share sheet stays the primary path** and is never
     replaced — this is a saved destination, not a new export mechanism, and the fallback when a provider
     refuses is the path that already works. Guard the `startActivity` and fail with a message, never a
     crash.
   - **The plan's longest-standing unverified assumption gets tested here**: whether Google Drive's document
     provider accepts a write through a persisted tree grant. If it does not, that is a finding to record
     with the local-storage and Drive-app-folder alternatives named, not a checkpoint to fail — the export
     that matters already ships.
   - **The recurring export reminder.** A backup reminder is not a care reminder: care reminders hang off a
     bunny and this one hangs off the app. So it is a switch in Backup settings with an interval,
     `AppPreferences`' seventh key, reusing 4a's sweep and 4b's `nextOccurrence` wholesale — one more branch
     in the one worker, not a second scheduled thing. It is also the piece that turns the remembered folder
     from a two-tap saving into a habit the owner does not have to hold.
   - Its copy says what it is: a prompt about the owner's export, not a claim that the data is unsafe. The
     Backup screen's status line already handles the honest version of that (3e).
6. **4f — Edge-to-edge, verified.** ✅ No feature work; the deliverable is evidence, per the scope bullet
   above. **Done, and it was not clean** — the evidence is [`docs/edge-to-edge.md`](edge-to-edge.md), the
   capture is `scripts/edge-to-edge.py`, and the matrix found two defects, both fixed here and both
   invisible in the one cell that had prior evidence.

   The **keyboard** was the first: `enableEdgeToEdge()` sets `decorFitsSystemWindows = false`, which makes
   the manifest's `adjustResize` inoperative from API 30 and gets it downgraded to a *pan*, so opening the
   keyboard on the observation form slid the top of the form under the status bar and carried the
   `TopAppBar` — *Save* included — off the screen. Fixed with `imePadding()` on the shell's `NavDisplay` and
   `SOFT_INPUT_ADJUST_NOTHING` on API 30+ only, because below 30 `WindowInsets.ime` is not reported at all
   and `adjustResize` is still the only thing that moves. The **bottom sheet** was the second: a sheet is
   its own window, so the one owner of insets does not reach it, and the reminders opt-in could not scroll
   and ran under the navigation bar.

   `displayCutout` in landscape — the case this checkpoint named as untested — **holds**, and the reason is
   recorded because it is not something this app does: Material3's `contentWindowInsets` default is
   `systemBars.union(displayCutout)`, on a value the shell has never overridden. An override added later
   would take it away, in landscape only, on cutout devices only.

   Two notes for whoever runs this again. HyperOS ignores the AOSP `navbar.threebutton` overlay this bullet
   assumed and needs MIUI's `force_fsg_nav_bar` instead; and `input tap` on this phone exits 0 when the
   event is dropped, so the driver taps, looks, and taps again — an unverified tap skips scenes, and a
   skipped scene in a matrix reads exactly like a clean one.
   - **Three cells, not four.** Portrait + gesture is already evidenced by 1.0's screenshots, so what is owed
     is portrait + three-button, landscape + gesture and landscape + three-button, for every screen — with
     the fourth cell captured as well for the screens 4a–4e add, which have no prior evidence.
   - **Capture is scripted; review is by structural family.** Driven with `adb`:
     `settings put system accelerometer_rotation 0` plus `user_rotation`, and
     `cmd overlay enable com.android.internal.systemui.navbar.threebutton` for the nav mode, then
     `exec-out screencap`. This is one of the few checks in the project that genuinely needs a screenshot
     rather than a `uiautomator` dump, because the defect is pixels under a system bar. But the defect is
     also **structural** — does this screen's container own its insets — so screens sharing chrome fail
     identically, and the review groups them: top-level tabs with the bottom bar, detail routes with a
     `TopAppBar` and back, forms with bottom actions, full-bleed content (`PhotoGallery`, the chart), and the
     chrome-free outliers. Capturing all of them is cheap once `adb` is driving it; reviewing dozens of
     unstructured images is what does not happen. A family member that differs from its representative is
     itself the finding.
   - The two screens outside the ordinary `Scaffold` are the ones to look at hardest: `SchemaMismatchScreen`
     (`safeDrawingPadding()`) and the first-run wizard, which is chrome-free by design.
   - Three things a screen-by-screen matrix misses, all likelier to be broken than a plain screen:
     **dialogs and bottom sheets**, drawn over content with their own inset behaviour; **the IME in
     landscape**, where `adjustResize` meets a keyboard that eats the screen and pushes bottom actions into
     the nav bar; and the **empty versus populated** state of any screen whose content only reaches the
     bottom edge once it has rows.
   - **`displayCutout` in landscape** is the specific untested case: in portrait a punch-hole sits behind the
     status bar and costs nothing, and in landscape it arrives on a side edge where nothing is padding for it.
   - A finding here is a fix here. If the matrix is clean, the checkpoint's output is the screenshots and a
     line saying so — which is a real result, since Play's notice is generic advice and "we looked" is
     exactly what was missing.
7. **4g — The gate pass, and freezing schema 5.** ✅ *(schema 5 frozen and tagged, lint at 0/0, CI green at
   26/34/36, overnight Doze observed; the watch's auto-expiry is carried — it needs a watch to run out, and
   Phase 5 re-drives this plumbing anyway)*
   - The gate below, driven by hand on the Xiaomi, including the **overnight Doze** run — which is a calendar
     item, not a task: it has to be started the evening before. With one sweep it is also a single target:
     if the sweep fires, every reminder in the app fires.
   - **The overnight-Doze run: passed on 2026-08-04, and the caveat is written down rather than rounded off.**
     Armed 2026-08-03, read the next morning from `dumpsys` alone — no unlocking, since waking the phone to
     look is the one thing that invalidates the observation. The sweep ran **09:00:00.720 → 09:00:01.411**
     (691 ms) and posted exactly what was predicted the day before: `care` / "Nail trim" / "Due today for
     Nugget.", and `watch` / "Have you checked on Bijou today?". Nugget's *other* nail trim, dated 2026-08-03
     and already notified, did **not** re-notify — the "a second day passing does not re-notify" bullet, got
     for free. Afterwards exactly one binky job remained, due 09:00 the next day, which is 4a's
     single-enqueued-item invariant holding *across* a run and not merely at arming.

     What the run proves is **survival**, and that much is unambiguous: `batterystats --history` puts the
     phone unplugged at 22:25 on 80% with `device_idle=full` stretches of 1 h, 2 h, **4 h unbroken** and 1 h
     again through the night, on HyperOS, with the battery-optimisation exemption deliberately absent. The
     job was neither culled nor deferred. What it does **not** prove is firing *while still dozing*: the
     phone was plugged in at 08:53:00, seven minutes before the sweep, so at the moment it ran the device was
     awake and on power. The mitigating detail, recorded for what it is worth rather than as a substitute —
     it fired at its scheduled 09:00:00, not at 08:53 when Doze lifted, and a job Doze had been suppressing
     flushes the instant its constraints clear.

     Taken as a pass on that basis. ADR-0003's amendment already has the app presenting as **best-effort**
     rather than armed while the exemption is unconfirmed, so the copy on screen does not depend on the
     stronger reading, and the seven-minute gap changes no shipped words.
   - **Schema 5 is frozen**: `5.json` committed and git-tagged (ADR-0007), `MIGRATION_4_5` no longer pending.
     From here a further change in this phase is a 5 → 6 migration, not a rewrite.
   - `lint` back to **0 errors and 0 warnings**, which 3g reached and 3f's note says the job is to hold.
   - The CI instrumented matrix green at API 26 / 34 / 36. The **26 leg is not ceremony here**: it is the only
     place the pre-33 branch runs, where `POST_NOTIFICATIONS` does not exist and notifications post without a
     runtime permission at all.
8. **4h — 1.1 to the tracks.** ✅ *(closed on the build; the Console half is carried — see Phase 5)*
   **Blocked on Play, not on the build, as of 2026-08-04.** 1.0.1's closed-testing
   run is in flight against the 12-testers / 14-day requirement, and shipping 1.1 into that track while it is
   counting is not worth the risk to the run. So the three remaining items — the upgrade proof, both listings'
   screenshots, and the two track uploads — are **deferred until that run completes**, not performed and not
   waived. The build half is done: 1.1.0 is cut, tagged `v1.1.0`, and verified against the artifact.
   - Release-please cuts 1.1.0; the bundle is checked **against the artifact rather than the config** — 3a's
     lesson — for `versionName`, `versionCode`, the upload key, and the Polish strings present in
     `base/resources.pb`.
   - **The upgrade proof, which is this phase's whole point.** 1.0.1 installed from the track, then 1.1 over
     it, and the real bunny history is still there — the end-to-end evidence that `MIGRATION_4_5` works on a
     file this project did not construct for the purpose. 4b's committed 1.0.1 fixture is the cheaper version
     of the same proof that CI has been running on every pull request since; this is the one on real history.
     **Outstanding, and the distinction matters:** `MIGRATION_4_5` is not unproven — the committed schema-4
     zip written by the shipped 1.0.1 build migrates and reads every table back, green in CI at API 26/34/36
     on every pull request. What is missing is only the last mile, that same migration against *this phone's*
     real history rather than a fixture. So the risk being carried is narrow, and it is carried knowingly.
   - `docs/play-app-content.md` **re-verified against the new manifest**: the app no longer declares zero
     user-facing permissions, and the answers 3a wrote against an artifact that did must be re-read rather
     than assumed to still hold.
   - **Screenshots for both listings**, since each locale's are uploaded per listing and 1.1 adds screens
     worth showing.
   - Internal track first, then the closed one — the same order 1.0 and 1.0.1 took, for the same reason.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 4b, 4c
and 4d — the migration tests and the two data layers — and again at the gate; `lint` at the gate, holding at
**0 errors and 0 warnings**. From 3g the instrumented suite is CI's on every pull request at API 26 / 34 / 36,
which is what makes the schema-4 guard always-on, and the Xiaomi run stays at the gate: an emulator has no
HyperOS background killer, which is the single thing this phase is most likely to be bitten by.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **4a**: ADR-0024,
0003, 0006, 0007, 0009. **4b**: ADR-0007, 0023, 0018, 0003, 0002, 0024. **4c**: ADR-0018, 0014, 0004, 0015,
0013. **4d**: ADR-0001, 0008, 0004, 0024. **4e**: ADR-0005, 0003, 0024. **4f**: ADR-0012. **4g**: ADR-0007,
0023, 0003. **4h**: ADR-0009, 0007, 0023, 0013.

**Gate:**

- A reminder set for +2 minutes fires while backgrounded, and still fires after a reboot.
- A reminder fires after the phone has sat idle in **Doze overnight** — screen off, app unopened, 12h+ — on
  the real Xiaomi. The +2-minute happy path is **not** sufficient evidence of reliability (ADR-0003). If it
  does not fire, that is recorded as a finding and the feature presents as best-effort, which is the honest
  state the app already has copy for.
- **Exactly one enqueued work item exists in the app**, before and after adding reminders, archiving a bunny
  and rebooting — the sweep, under its one unique name (ADR-0024).
- While battery-optimisation exemption is unconfirmed, a reminder says so **in words** rather than presenting
  as an armed alarm — and the exemption prompt appears once, at first schedule, not during onboarding and not
  again afterwards.
- With notifications denied or the `care` channel muted, reminders present as **blocked** — stating they will
  only appear in the app — rather than as best-effort, and creating a reminder still works.
- The app posts nothing before the owner opts in: **`POST_NOTIFICATIONS` is asked from our own screen**, once,
  and skipping the setup step re-asks at point of use and nowhere else. **An install that completed setup
  under 1.0.1 gets that same ask at point of use**, from our own screen, once — which is the only path any 1.1
  install actually takes. On an API 26 device the same flows work with no runtime permission in the picture
  at all.
- Completing a reminder **back-dated to yesterday** schedules the next occurrence from yesterday; completing
  one three weeks overdue resets the interval rather than owing it. Neither is affected by what time of day
  the tick happened.
- A care reminder due today notifies **once** — a second day passing does not re-notify, and neither does a
  reboot — while the Care screen still shows it overdue.
- **Logging a weight satisfies a weigh-in reminder** without any tick, and deleting that weight puts the
  reminder back where it was.
- Tapping *Add to calendar* on a yearly reminder opens the calendar app with the date and yearly repeat
  already filled in; a second tap says it was already added rather than silently creating a second event; and
  on a device with no calendar app it fails with a message, not a crash.
- Tapping a care notification for a bunny who is not the selected one **switches to that bunny** and lands on
  their Care screen with a back stack that does not exit the app.
- A short-duration watch nags **once** on a day with no observation, does **not** nag the morning after an
  evening observation, and stops nagging the moment it auto-expires — with the expiry prompt showing the
  current trend, and dismissing it leaving no row behind.
- A trend flag offers **Start a watch** in all three of its hosts, and starting one from the flag pre-fills
  the default duration.
- **"Log a healthy day" refuses to cover a watched bunny**, naming the reason, while still covering a
  *flagged* one — the pair that has to differ, differs.
- An archived bunny has no scheduled reminders, no watch, and no way to start either: **the sweep produces no
  notification for it**, asserted as a fact about the derivation, since with one sweep there is no per-bunny
  work to cancel and none to orphan.
- Deleting a bunny counts its care reminders and events in the destroyed bucket, with correct pluralisation.
- **A worker woken by the OS while a schema mismatch is pending does nothing** — no wipe, no crash, and the
  sweep is re-enqueued after the owner consents on next launch. Asserted by test, since the race cannot be
  driven by hand.
- **1.0.1 upgraded in place to 1.1 keeps a real bunny's history**, and the committed schema-4 fixture written
  by 1.0.1 migrates and reads back in CI. Schema **5**'s exported JSON is committed and git-tagged.
- The Care tab opens onto a real screen; **`StubScreen` has no callers left in the app at all**.
- **Every screen renders correctly edge-to-edge in both orientations under both gesture and three-button
  navigation**, with nothing drawn under the status bar, the navigation bar or a display cutout — dialogs,
  bottom sheets and a landscape keyboard included.
- Every new string exists in both locales, counts use `<plurals>`, and `PolishTranslationTest` is green — the
  test being the gate, not a read-through.
- An empty database still produces no warnings, and no reminder or watch infers anything from silence
  (ADR-0001).
- Then the 1.1 release: internal track, then closed, installable from Play on the Xiaomi.

## Phase 5 — Vet, medications, documents, dose reminders — ships as 1.2

The phase that closes the roadmap, and the one whose failure mode is the worst in the app. A missed nail
trim is an inconvenience; a missed dose during treatment is the hazard ADR-0003 spends its entire
Consequences section on — *"a dose reminder that silently fails to fire is worse than none"* — which is why
this phase inherits Phase 4's notification plumbing wholesale and invents none of it.

**Four things are different from every phase before this one, and each of them costs something.**

The **schema is load-bearing in two directions now.** Phase 4 migrated exactly one released version;
this phase has to accept *either*. 1.0.1 (schema 4) is still installed on any device that never took 1.1,
and an upgrade that skips a version is the ordinary case on Play, not an exotic one. So `MIGRATION_5_6` is
written while `MIGRATION_4_5` stays proven, both chained on every launch, and the upgrade the gate actually
runs is the **longest one the field can produce**. This is also the **first `ALTER` on a table holding real
history**: `weights` gains a column where Phase 4 only ever added tables. SQLite allows exactly one shape of
that — a nullable column with `DEFAULT NULL`, `REFERENCES` clause permitted only because the default is null
— and if Room's `validateMigration` refuses it, the fallback is a full table rebuild against the one table
whose contents are the app's reason to exist. Which of the two it is gets decided by an instrumented test at
5b, not by a guess here.

**One migration creates all seven new tables**, the document ones included, well before any screen touches
them. Churn is free while 6 is pending (ADR-0007), but a *released* schema is a permanent obligation, and
that obligation is counted **per version, not per feature**: a second version for the document tables would
buy a second forever-migration and a third fixture for tables that settle at the same time as the other
five. **Documents ship in 1.2** — decided here rather than left open at 5g — so the split would answer a
question nobody is asking, at a price that never goes away.

The **second scheduling mechanism arrives, and it needs a permission the app cannot grant itself.** At
`targetSdk` 36, `SCHEDULE_EXACT_ALARM` is **denied by default** (Android 14 behaviour change); it is granted
by default only on API 31–33, needs nothing at all below 31, is settable **only in system settings**, and can
be revoked at any moment afterwards. So on a current phone the default state of a fresh 1.2 install is *no
exact alarms* — which makes 4a's three honest states not a corner case but the first-run experience, and
makes `canScheduleExactAlarms()` a check before **every** schedule rather than a setup-time question.

The **first dependency that can simply be absent.** ML Kit's document scanner is delivered by Play services
and its model is downloaded on demand, so it is missing on devices without Play services and can fail to
arrive on devices with them. ADR-0009 already requires the interface and the plain-camera fallback; what this
phase adds is that the fallback is not a courtesy path — it is the only path on a CI emulator without Play
services, which is where the API-26 leg runs. The dependency also lands in the merged manifest, and 4h's
finding was precisely that the merger writes permissions nobody declared. That check happens at 5g, when the
dependency enters, not at the release.

And **medications are the closest this app ever comes to medical advice.** Every other feature observes;
this one records what the owner was told to give. The rule is stated in an ADR rather than left to taste: the
app never reasons about a medication — no interactions, no dosage validation, no "you missed a dose" warning
dressed as a health signal. A derived slot with nothing recorded against it is displayed as **unanswered**,
which is a fact about the record, and ADR-0001's *never infer a health problem from missing data* is the same
sentence one domain over. The rule is also **on screen**: an ADR binds our copy and tells the owner nothing,
and 1.2 is the release where the app starts holding a number the vet chose and the owner will act on
(ADR-0026).

- Vets directory; visits linked to a bunny and optionally a vet — a health record, with no cost field
  (ADR-0017). **A vet outlives its visits**: deleting a vet sets `vetId` to null and keeps the visit, because
  a clinic closing is not a reason to lose a health record. A weight recorded on a visit is stored as **one**
  weight row written in the same transaction as the visit, tagged by `visitId` — never a second copy of the
  number, so the chart and the visit cannot drift apart. Deleting a visit makes an explicit, stated choice
  about that weighing: keep it as a standalone weight, or remove it with the visit.
- Medication courses with a start, a **nullable end** (an open course is ongoing), a **free-text dose
  amount**, and an optional daily schedule of clock times. Due doses derived, not stored (ADR-0002). Doses
  recordable ad hoc, with or without a schedule, as **given** or **deliberately skipped** — a skip is a
  recorded fact and not an absence. Derivation looks **forward only**: today and later derive slots, a past
  day lists what was recorded. Otherwise moving a course's time on day ten repaints the nine compliant days
  behind it as unanswered, which is an edit rewriting history it never touched (ADR-0002).
- Dose reminders on exact alarms, default on per course and switchable off (ADR-0003), reusing the
  notification plumbing from Phase 4 and adding only the alarm path. **Wall-clock semantics** (ADR-0003): the
  next trigger is resolved fresh in the device's current zone each time, so DST and travel keep a dose at its
  intended time of day; `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` receivers reschedule alongside
  `BOOT_COMPLETED`, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` joins them because a
  permission granted after the fact must re-arm what was scheduled inexactly.
- **One pending dose alarm at a time, rebuilt from truth** — ADR-0024's discipline surviving its own
  exception. Doses diverge from the sweep in *mechanism* because ADR-0003 needs the timing; they do not
  diverge in bookkeeping. There is no per-course alarm to orphan: one `PendingIntent` under one request code,
  recomputed from the courses table after every write, **every bunny archive, un-archive and delete**, every
  reboot, every clock change and every fire — **and by the daily care sweep and process start**, because one
  alarm is also one point of failure and the rebuild is idempotent, so the answer is more occasions to
  rebuild rather than more alarms to track. A **30-minute grace window** decides what a fired alarm is still
  allowed to post, because the best-effort path is late by design and a rule written against *now* would
  make it deliver nothing at all.
- Documents via the ML Kit scanner behind ADR-0009's interface, with the existing `TakePicture` path as the
  fallback; attached to a bunny and optionally a visit; multi-page, reorderable, viewable with zoom, and
  deletable. All writes go through `MediaFiles.persist(Document)` — whose 3000 px / q92 spec is marked
  *unverified until the phase that ships them* in its own source comment, and this is that phase, so
  verifying it against a real vet printout is a deliverable and not an assumption.
- The **backup agent's document admission**, deferred from Phase 3: documents newest-first under a ceiling
  *below* the ~25 MB quota, as a pure function over `(core bytes, documents newest-first, budget)` with a JVM
  test, plus the one-time exclusion notification. Both were unbuildable at 1.0 in the only sense that matters
  — `documents/` was empty, so the ceiling admitted nothing and the notification could not fire — and
  ADR-0005's guard exists first to keep the evidential core under quota, which is a claim that can only be
  exercised once there is something to exclude.
- **The Care & Meds tab earns its name back.** 4c moved the label to "Care" for 1.1 because a tab named for
  medications with none behind it advertises a feature that is not there; the label moves back at 1.2 and the
  nav key `CareAndMeds` — persisted back-stack state since 1.0 — is still never renamed.
- **`CONTEXT.md` gains `Vet` and `Visit`.** Both are used throughout this plan and neither is in the
  vocabulary; the terms land with the entities that make them real (5b), not after the screens are written.

**Carried in from Phase 4**, which closed on the build with two halves outstanding. Neither is new work for
this phase to design — both are evidence this phase is already standing in front of.

- **Does the Phase-4 notification plumbing actually work in the wild?** 4g's overnight-Doze run proved the
  sweep *survives* deep Doze (10.5 h, HyperOS, no battery exemption) but not that it *fires while dozing* —
  the phone was plugged in seven minutes before the sweep, so the last stretch was awake and on power. Phase 5
  puts a second, stricter mechanism on the same device and its gate already demands an overnight Doze run for
  doses. So one unplugged night still owes an answer — phone off charge from the evening, not plugged in
  until after the 09:00 sweep, read with the same read-only `dumpsys` commands before the shade is touched.
  It carries the care sweep firing while **still in Doze**, and alongside it a watch **auto-expiring**
  (nagging stops that morning, the prompt shows the *current* trend, dismissing leaves no row behind) — two
  different mechanisms with two different signatures, so one night can hold both without ambiguity.
  **Neither is date-fixed.** Bijou's watch happens to run out on Thursday 2026-08-06 and is free evidence if
  the phone is idle that night, but `seedWatches` already back-dates `startedAt` — `watches.start(bijou,
  DAYS_7, now.daysAgo(4))` — so an expiry on any chosen morning is a fixture parameter, re-armable in a
  minute on a freshly migrated database. Nothing here blocks a checkpoint, and no checkpoint is held off the
  device to protect it. That leaves 5a's and 5i's runs owing only what they are about: the exact-alarm path,
  a different mechanism under different Doze rules.
- **4h's Console half** — the upgrade proof (1.0.1 → 1.1 over real bunny history), both listings' screenshots,
  and the internal-then-closed track uploads. Deferred because 1.0.1's closed-testing run was counting against
  Play's 12-testers / 14-day requirement and 1.1 was not worth risking it. 1.2 goes up the same path, so the
  proof to actually run is the **longest** upgrade the field will see — whatever version a real device is on,
  forward to 1.2, history intact. `MIGRATION_4_5` is meanwhile proven against the committed schema-4 fixture
  on every pull request; only the real-history mile is untested.

### Checkpoints

**Ten**, and the ordering is deliberate three times over. The **alarm path goes first, on an empty database**
— 4a's lesson repeated for the same reason: a debug-only "dose in two minutes" proves the permission, the
receiver set, the channel and the Doze behaviour while the payload is boring, so a dose that fails to fire
later has one suspect instead of two. The **overnight-Doze run is armed at 5a and again at 5i**, because the
first one is not ceremony: if `setExactAndAllowWhileIdle` does not survive HyperOS overnight, doses ship
explicitly as best-effort (ADR-0003 says so in advance), and that is a finding that reshapes the phase — it
must arrive in week one, not at the gate. **Documents come after medications** even though they are the
easier feature: they carry the phase's only new third-party dependency and the only new merged-manifest risk,
and the backup admission at 5h needs real documents on disk to exclude.

**The schema rule for this phase, stated once.** Version **6** is reached by `MIGRATION_5_6`, written at 5b
and creating **every** table the phase needs — documents included, unused until 5g. As the shape churns across 5b–5g the version does **not** climb —
`6.json` is regenerated in place and the migration rewritten to match, which is the "rewriting pending
migrations is still fair game" ADR-0007 grants the debug build. What must stay true throughout is that a
release-shaped open of a schema-**4** *and* a schema-**5** file both succeed, asserted by test against two
committed fixtures written by two shipped builds. Version 6 is frozen and its JSON git-tagged at 5i, and only
there.

**Decisions this phase owes, written where decisions live.** Five, all small, and all landing **with this
plan** rather than with the code they justify: an **amendment to ADR-0017** recording that `visitId` is the
*only* stored origin fact and `source` is derived from it (the ADR's actual claim — one row, never a copy —
is unchanged; two columns that can disagree is the pattern 4b already refused for day-of-month), that the
index is unique so the claim is enforced by the schema rather than by the editor, and that vets outlive
visits; an **amendment to ADR-0002** clamping derivation to today-and-forward and keying a recorded dose by
its slot's *local* date and time; an **amendment to ADR-0021** taking a visit-tagged weighing out of the
same-instant collision resolver, because *replace* was written when a weighing had one owner and would
otherwise silently overwrite or delete the vet's number; **ADR-0025**, one pending dose alarm rebuilt from
truth, as the ADR-0024 exception it is, now also carrying the 30-minute grace window and the bunny-level
rebuild triggers; **ADR-0026**, the app records doses and never advises on them, on screen as well as in the
copy.

1. **5a — The exact-alarm path, proven while nothing depends on it.** 🔨 *(built; lint 0/0, JVM tests green —
   **not closed**: the overnight-Doze run has now been read once — the night of 4→5 August, result under its
   bullet below — but it fired on the **best-effort** path, so the question the three outcomes were written for
   is untouched and the tick still waits. **Everything else is proven on the device**, 2026-08-04 on the Xiaomi: the two-minute dose arrived, with
   `1 wakeups` recorded against `DoseAlarmReceiver` — so the alarm woke the phone rather than riding somebody
   else's wakeup; `doses` exists at `mImportance=4` with the other three at 3; the appop went `default` →
   `allow` through the app's own deep link, so the permission path works end to end; the slot store is empty
   afterwards, which is post-then-mark; **exactly one** notification was posted on `channel=doses`, which is the
   fire-then-rebuild loop *not* happening — a slot stays answerable for another 29 minutes after firing, so
   without the mark the rebuild re-arms the same instant and fires again; and `dumpsys alarm` reports no pending
   alarm afterwards, which is ADR-0025's invariant in the one place it is checkable. The **autostart state is now recorded**, which ADR-0025 asked for and no result in the
   project had: on 2026-08-04, before anything was changed, HyperOS's *Background autostart* listed nine
   permitted apps and **neither Binky build was among them**. So 4g's 10.5-hour Doze run and 4h's gate reboot
   were both taken with autostart **denied** — a stronger reading of those results than they were being given,
   and the denied half of the two-state reboot check the ADR's gate requires. **How to read it, since the app
   cannot**: the count in *Settings → Apps → Permissions → Autostart* ("N apps can start in the background") and
   the group of apps under it are the only trustworthy signal — a `uiautomator` dump's `checked` attribute
   reports false for every row on that list, including ones the header proves are enabled, so it must not be
   used. Each overnight run records its own autostart state alongside its result; granting it for the first run
   is deliberate, because a MIUI denial can stop an alarm waking the app at all and would return outcome 3 for a
   reason that is not `setExactAndAllowWhileIdle`. What
   the build added beyond the bullets below: the debug dose is **stored** and arms through
   `rescheduleDoseAlarm()` rather than placing an alarm of its own — otherwise the three receivers have nothing
   observable to rebuild at a checkpoint whose whole premise is proving them early — and it gained a second
   action, **the next 08:00**, because a two-minute delay cannot answer an overnight question. 08:00 rather
   than the sweep's 09:00 so one night can hold both mechanisms without their signatures colliding.
   `DebugDose.kt` is the seam: 5d replaces two function bodies with the `dueDoses` derivation and deletes the
   file.)* No schema change at all, deliberately —
   the same split 4a used, so a failure in the alarm path and a failure in the migration cannot be confused.
   - `SCHEDULE_EXACT_ALARM` enters the manifest and **`USE_EXACT_ALARM` does not** (ADR-0009): the latter is
     auto-granted but Play permits it only for apps whose core function is an alarm clock or calendar, and
     this app's core function is a rabbit's weight chart. The permission is **denied by default** at
     `targetSdk` 36, so the app assumes nothing.
   - **The ask is a deep link, not a dialog.** `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` opens system settings;
     there is no runtime-permission path and no result to read, so the state is re-read on resume via
     `canScheduleExactAlarms()`. Asked **once**, at the point a course first schedules something — ADR-0006's
     point-of-use rule, and the same shape 4a used for the battery exemption. Never during onboarding: at
     first run there is no medication, so the reason would not be on screen.
   - **The three-state resolver gains a fourth input, doses only.** Notification permission denied or the
     channel muted stays **blocked** — certain, and detectable. Exact-alarm permission absent is
     **best-effort**, not blocked: the alarm still goes in via `setAndAllowWhileIdle`, which pierces Doze but
     within an OS-chosen window, so the reminder is real and merely imprecise, and the copy says which. It is
     the one case where the app degrades a mechanism rather than a promise. **And the state is tappable** —
     it is rendered on every course row anyway, so best-effort reopens the exact-alarm settings and blocked
     reopens the app's notification settings. That is not a second ask (ADR-0006 still gets exactly one); it
     is the label refusing to be dead text, and it earns its place because revoking `SCHEDULE_EXACT_ALARM`
     on Android 14+ drops the owner into best-effort without their ever having chosen it.
   - **The grace window, and it lands here because best-effort is what needs it** (ADR-0025). A slot is
     answerable while `now - slot ≤ grace`, a **named 30-minute constant**, and fire and reschedule share the
     one predicate rather than each deciding for itself. Without it the obvious rule — fire what is due
     *now*, skip what is past — breaks the app's **default** configuration rather than a corner of it:
     `setAndAllowWhileIdle` is delivered when the OS chooses, in Doze a window of minutes, so an alarm placed
     for 08:00 and delivered at 08:04 finds its own slot already past, posts nothing and re-arms for the
     next. Doses would then arrive **never**, on the path built to degrade honestly, looking exactly like the
     correct quiet of nothing being armed. Thirty minutes clears the Doze window and stays far short of the
     eleven-hours-late shade answer 5f refuses. Note what cannot catch this: the two-minute debug action on a
     screen-on phone fires promptly, and so does every emulator.
   - **A fourth channel, `doses`, at `IMPORTANCE_HIGH`** — the level 4a deliberately spent nowhere so that
     this one reads as a real signal instead of as the volume everything already sits at. **Fourth, not
     third**: `care`, `watch` and `backup` all ship in 1.1, and 4e's export prompt took the third slot. Two
     changes to 4a/4e code fall out, and they are this checkpoint's rather than discoveries later.
     `ReminderChannel` gains a **per-entry importance**, because `ensureReminderChannels` today creates every
     entry at a hardcoded `IMPORTANCE_DEFAULT` and `doses` cannot be HIGH without it. And creation goes **per
     channel at its own first use** instead of all of them at once — the rule that file already states,
     *"a channel exists when something is posting on it"*, and the one its loop currently breaks: adding
     `doses` to the enum as it stands puts a medication row in the notification settings of every owner who
     has never opened a medication screen. Muting doses must not mute care, which is why there are four.
   - **Three receivers, one function.** `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` join
     `BOOT_COMPLETED`, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+) joins them because
     an alarm placed inexactly must be re-placed exactly the moment the owner grants the permission. All four
     call the same `rescheduleDoseAlarm()`, and all four hit **ADR-0007's guard first**: a receiver woken by
     the OS while a schema mismatch is pending does nothing, exactly as the sweep does.
   - **A debug-only "dose in two minutes"** beside 4a's reminder action, on its own one-shot path rather than
     through any course. It is what makes this checkpoint provable with no medication in existence, and it
     stays afterwards as the fastest way to re-prove delivery.
   - **The first overnight-Doze run is armed from here**, unplugged, using that debug action. Phase 4's
     re-read rides its own night, so this run answers one question and no others: does
     `setExactAndAllowWhileIdle` fire *on time* on HyperOS after 12 h idle. **The three outcomes are written
     down before the run**, because the third is the one that will be argued away at 06:00 with a phase half
     built:
     1. **Fires inside the grace window** → doses ship armed, the phase proceeds as planned.
     2. **Fires late but reliably, outside grace** → the constant widens to cover the observed lateness,
        recorded as a device-specific finding; doses still ship on the exact path, and the copy says
        best-effort on this device.
     3. **Does not fire until the phone is touched** → the exact path is **not** the delivery mechanism.
        Doses fall back to a more frequent sweep — the one mechanism with overnight evidence on this device —
        and ADR-0003 is amended to say so. The exact alarm survives as an *optimisation* taken when the
        permission is granted and the device honours it, never as the promise. Holding this branch open is
        nearly free: a sweep-driven fallback wants the same `dueDoses` derivation and the same rebuild
        function, and only the trigger differs. Shipping the channel, four receivers, the deep link and
        ADR-0025's apparatus so the app can say "best-effort" in words would be a lot of machinery to keep
        after its premise had been disproven on the only hardware in the project.
   - **The first run, read 2026-08-05 08:26 — it answered a question, and not this one.** The dose posted at
     **08:14:46** for its 08:00 slot: **14m46s late, inside grace**. `dumpsys batterystats --history` puts
     `device_idle=full` **07:38:39 → 08:23:05** unbroken across that, on battery, and `plug=usb` at **08:23:29**
     — nine minutes *after* the alarm, so unlike 4g's run nothing was awake or on power when it fired. (Same
     anchor 4g used: `plug=usb` at `+1d09h57m57s` puts offset 0 at 2026-08-03 22:25:32. The stretch before it
     was another unbroken `device_idle=full` 03:11 → 07:08, ~10 h idle in all.) The alarm woke the phone itself
     — `*walarm*:…/DoseAlarmReceiver`, `1 wakeups`. Exactly **one** notification on `doses` at `importance=4`;
     `debug-dose.xml` is `<map />` with mtime 08:14, so post-then-mark holds overnight and not only on a
     screen-on phone; no pending alarm remains, binky appearing in history alone.
     **But `SCHEDULE_EXACT_ALARM` had reverted to `default`** — `allow` on 2026-08-04 through the deep link,
     denied again by the time it fired, most likely an uninstall during 5b/5c — and `dumpsys alarm` agrees:
     *Allow while idle history* is empty while binky sits in ***Allow while idle compat history***, the
     `setAndAllowWhileIdle` bucket. `setExactAndAllowWhileIdle` was never placed, the three outcomes above do
     not apply, and **the exact-alarm night is still owed**. Read the appop before trusting any rerun; a
     reinstall silently downgrades the mechanism under test while every other signature looks identical.
     What it does prove, which no result in the project held before: the **default** Android-14 configuration —
     exact-alarm permission denied, no battery exemption — delivers a dose through deep Doze on HyperOS, and
     the resolver picked that path on its own rather than throwing. And the grace window is **load-bearing,
     not theoretical**: the bullet above predicted 08:04 and the device produced 08:14:46, so without
     `DOSE_GRACE` this morning posts nothing and re-arms — the silent failure that looks exactly like the
     correct quiet of nothing being armed. Half the constant spent on one ordinary night is also the first
     evidence about how wide it should be; if the exact path runs as late, outcome 2 is live rather than
     hypothetical. **Autostart was not read for this run**, so it is recorded as unknown — the omission the
     bullet below exists to prevent, and it must not repeat. Re-armed for the night of **5→6 August**,
     alongside the sweep rerun that catches Bijou's watch expiring: re-grant the permission through the deep
     link first, or the night proves this same thing twice.
   - **The phone's autostart state is read and recorded here, before anything else** (ADR-0025). It is one
     look at *Ustawienia → Aplikacje → Autostart*, and it decides what every reboot result the project
     already holds actually proved — 4h's gate reboot passed in a state nobody wrote down, and two of the
     three rebuild occasions are gated on it.
   - Tests, JVM: the extended delivery resolver as a case table across notification × channel × exact-alarm
     state; the ADR-0007 guard as a pure predicate, reused rather than re-derived.
2. **5b — The whole schema: vets, visits, and the first column added to a shipped table. Schema 6.**
   🔨 *(built; spotless, `assembleDebug` and JVM tests green, lint 0/0 — **not closed**: the schema-5
   fixture is the one bullet still owed, see below. Instrumented: **141 tests on the Xiaomi, all
   green**, 2026-08-04, run through `am instrument` after the split install refused. One install note
   worth keeping, because it cost two rounds: HyperOS's first-install refusal applies **per package**,
   so the app APK updated cleanly while `…debug.test` — dropped when an earlier split-install session
   rolled back — was refused instantly every time, looking exactly like the USB-install gate being
   shut. The fix is the one CLAUDE.md already names, and the tell is `pm list packages`. Three
   decisions the plan's text did not settle, made here: the column is
   **`visitId`, not `visit_id`** — every column in this database is camelCase and Room derives
   `index_weights_visitId` from the property name, so the plan's snake_case above was a transcription
   slip and not a naming choice; **`medication_times` and `document_pages` carry UUID primary keys of
   their own**, which their field lists omitted, so that moving a time chip from 08:00 to 09:00 is an
   update of a held row rather than a delete-and-insert a half-finished edit could leave as neither —
   the unique index on `(courseId, time)` is unchanged and still what makes "08:00 twice" impossible;
   and **the five empty tables get entities but no DAOs**, because Room validates schema 6 from
   entities alone and a DAO written now would be a guess at 5d's and 5g's queries. `VetDao` and
   `VisitDao` do land — 5c needs them, and 5b's own cascade tests read through them rather than
   through raw SQL. One obligation this creates for **5g**: `BunnyRepository.delete` deletes photo
   files by hand, and document pages will need the same, or a deleted bunny leaves its scans in
   `documents/`. Nothing leaks today because the table is empty.)*
   - **Every id here is a `String` UUID**, as every entity since 1.0 has been, and the reason is worth
     stating correctly because the obvious version of it is wrong. **A restore does not merge rows** —
     `BackupRestorer` replaces the database wholesale and merges only *media*, and a preserved copy is a
     whole database file. So UUIDs are not buying row-level collision safety across installs. What they buy
     is that **every media path is globally unique** (`documents/<uuid>.jpg` merging into a directory that
     already holds another install's files) and that an id stays meaningful when a row is read out of a
     preserved copy taken on a different phone. An autoincrementing integer would break both.
   - `VetEntity` — `id`, `name`, `clinic: String?`, `phone: String?`, `notes: String?`, `createdAt`.
     **App-wide, with no bunny FK**: a household's bunnies see the same vet, and a directory per bunny would
     make the owner type the clinic in twice.
   - `VisitEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `vetId` FK **`SET NULL`** nullable indexed,
     `visitedOn: LocalDate` (4b's converter — a visit happens on a day), `reason: String`, `notes: String?`,
     `createdAt`. **No cost field**, and the entity says so in a comment so it reads as ADR-0017's decision
     rather than as an omission someone will helpfully fix.
   - `WeightEntity` gains **`visitId: String?`**, FK to the visit, `SET NULL`, **uniquely** indexed. **That is
     the whole origin tag.** `WeightSource` exists as a derived value in the domain layer — `if (visitId != null) Visit
     else Manual` — and never as a second column: two stored facts that can disagree is exactly what 4b
     refused for the intended day-of-month, and `SET NULL` then makes "keep the weighing when the visit goes"
     correct by construction rather than by a repository remembering to clear a second field. The index is
     **unique** because nothing else stops two rows claiming one visit — NULLs are distinct in SQLite, so
     every manual weighing stays unconstrained while "one row, never a copy" becomes a property of the
     schema instead of a property of the editor being careful, or of a backup having been written by a build
     where it was.
   - **`MIGRATION_5_6` ships in this commit, whole**: **seven** `CREATE TABLE`s — `vets`, `visits`,
     `medication_courses`, `medication_times`, `doses`, `documents`, `document_pages` — their indices, and one
     `ALTER TABLE weights ADD COLUMN visitId TEXT REFERENCES visits(id) ON DELETE SET NULL DEFAULT NULL`
     — the only form SQLite accepts for a foreign-keyed column added to an existing table, and it is accepted
     only because the default is null — **followed by its own
     `CREATE UNIQUE INDEX index_weights_visitId ON weights (visitId)`**. The `ALTER` enforces nothing on its
     own; `ADD COLUMN` cannot carry a `UNIQUE` constraint, so the index is a separate statement and it is the
     statement the "one row, never a copy" claim actually rests on. The five tables 5d and 5g will fill are
     created **here and left empty**: their entities and DAOs exist so Room can validate schema 6 as a whole,
     and no screen reads them until the checkpoint that owns them. Whether Room's `validateMigration` agrees is settled by the
     instrumented test in this same commit; if it does not, the fallback is the create-copy-drop-rename
     rebuild, which is a bigger change to the one table that must not lose a row, and so is a decision made
     with a red test in hand.
   - **Two fixtures now, not one.** 4b committed a schema-4 backup zip written by the shipped 1.0.1 build; a
     **schema-5 zip written by the shipped 1.1.0 build** joins it, both carrying fabricated bunnies and never
     real history, both restored through 3d's staged path in instrumented tests. Together they are the
     skipped-version upgrade, run in CI on every pull request instead of once by hand at the release.
     ✅ **Done at 5j, which closes 5b.** `bunny-schema-5-fixture.zip` is committed and
     `aSchemaFiveBackupWrittenBy110MigratesTheLastStep` restores it: manifest `schemaVersion` 5 in,
     `BUNNY_SCHEMA_VERSION` out, the 1.0.1-era counts unchanged (43 weighings, 5 observations, 5 photos, 2
     symptom links — **identical to the schema-4 fixture**, because the seeder and the pinned `now` are the
     same), 1.1's own rows carried through a *second* migration (4 care reminders, 2 care events, 2 watches —
     the half the schema-4 fixture can only ever show as empty), and `vets`, `visits`, `medication_courses`
     and `documents` present-and-empty, which is the correct outcome for a build that had none.
     **Producing it turned out not to need hands on the phone**, and the note that it did was wrong in an
     instructive way. `adb` cannot drive the SAF picker — still true, and by 5j it could not drive taps at
     all — but the picker only chooses *where the bytes land*: `exportTo` has already built the archive into
     `cache/exports` before the share sheet is ever shown. So an `androidTest` class on the `v1.1.0`
     worktree, calling the real `AppContainer`'s repositories and `backupExporter` with a pinned
     `2026-08-05T12:00:00Z`, writes a faithful archive in 1.4 s and is re-runnable, where the manual route
     was neither. The provenance requirement was always *"written by the shipped build's own container,
     seeder, Room and exporter"* — never *"written by a human tapping"*. Generalised in
     `docs/adr/0007`-adjacent practice: when a flow's **output** is what matters rather than its screens,
     check whether the shipped code can be called directly before booking a device chore.
   - `recordCounts` gains visits, courses, doses and documents — all **sole-owned**, so the destroyed bucket
     (ADR-0004), and all countable from here because the tables exist; a count of zero is an honest count
     until the features land. Vets are not: they are
     app-wide, they survive their visits, and a bunny's deletion must not take the clinic's phone number with
     it.
   - Tests, instrumented: visits cascade with their bunny; a deleted vet leaves its visits standing with a
     null `vetId`; a deleted visit leaves its weight row standing with a null `visitId`; `LocalDate`
     round-trips; **a second weight row claiming the same `visitId` is rejected by the index**; both
     migration fixtures. JVM: `WeightSource` derived from `visitId` as a two-case table,
     which is trivial and is the point — it is the assertion that there is nothing else to get wrong.
3. **5c — Visits and the vet directory on screen, and the origin-tagged weight.**
   ✅ *(built and closed. spotless, `assembleDebug` and JVM tests green, lint **0/0**; instrumented
   **153 tests on the Xiaomi, all green** — 5b's 141 plus twelve new ones in `VisitRepositoryTest` —
   2026-08-04, through `am instrument` after two plain installs. The screens were **reviewed on the
   phone**, not only asserted: the seeded fixture now writes two vets and three visits, and the round
   trip More → Vets → directory, Care → Vet visits → a visit carrying "Weighed 2,380 g", Weight → the
   12:00 row saying "Recorded at a vet visit" with *Edit* and *Delete* absent → *Open the visit* →
   the editor with every field filled, all render as designed. Five decisions the plan's text did not
   settle, made here: **the editor is the visit's only screen** — there is no separate detail view,
   and it takes the shell's `readOnly` the way `CareReminderScreen` does, so an archived bunny's
   visits are readable in full with no Save and no pickers; **the Care tab keeps one `ViewModel`**
   (house rule), so visits arrive through `CareViewModel` and its `combine` lands exactly on Kotlin's
   five-flow typed overload; **a visit-tagged row loses *Edit* and *Delete* in the weight list too**,
   which is wider than the plan's "read-only in the weight editor" and is the same rule — leaving
   *Delete* on the row would take the vet's number out of the series with none of the stated choice
   the visit's own delete dialog exists to ask; **`VisitDetails` is a joined projection** (visit + vet
   name + weighing) rather than three reads, and Room re-emits it on a write to any of the three
   tables, so renaming a vet moves the list with nothing telling it to; and the delete dialog states
   its choice as a **radio pair defaulting to keep** with Delete/Cancel beneath, rather than as two
   buttons that both destroy — one confirmation, still cancelable. `RecordedAtField` gained an
   `enabled` flag rather than a second read-only copy of itself. **`CONTEXT.md` gained `Vet` and
   `Visit` here**, which 5b owed and did not land. The tab is **still labelled "Care"**: the name
   moves back to "Care & Meds" at 5e, with the medications that make the second half of it true.)*
   - **Where they live is a decision, so it is made here.** The `CareAndMeds` tab is **bunny-scoped** and
     becomes a hub with three lists — care reminders (4c), medication courses (5e), visits — because all three
     are that bunny's ongoing care. The **vet directory is not bunny-scoped**, so it lives in **More** beside
     Archived bunnies, Settings and Backup (ADR-0015). Under **"All bunnies"** the tab asks which bunny first,
     reusing 2f's `ChooseBunnyDialog`, unchanged.
   - The visit editor: date **back-datable** on the same terms as every other entry — defaults to today, past
     allowed, future rejected with the reason stated; reason; notes; optional vet through `SearchablePicker`
     (built for symptoms at 2d, reused rather than rebuilt) with "add a new vet" inline, because the moment an
     owner needs a vet record is the moment they are typing a visit; and an **optional weight in grams**,
     using the same entry control as Weight (`Int` grams, house rule).
   - **The weight write is one transaction**: the visit row and the weight row, or neither. Editing the
     visit's weight edits *that row*; clearing it deletes that row. There is no path that produces two numbers.
     A visit happens on a `LocalDate` and a weight is an `Instant`, so the weighing takes
     **`min(noon on visitedOn, now)`** — noon because it cannot land in a DST gap the way midnight can and it
     puts the chart point in the middle of the right day, clamped to now because a visit dated today and
     entered at 09:00 must not write a weighing three hours in the future. Editing the visit's date
     re-derives it.
   - **And the weight side has to stop being able to overwrite it** (ADR-0021 amendment). "No path produces
     two numbers" guards duplication; the live exposure is the opposite direction and it is already shipped
     code. ADR-0021's same-instant collision resolver defaults to *replace*: adding a manual weighing at an
     occupied timestamp **updates the row already there** (`WeightEntryViewModel.kt:205`), and editing one
     onto that timestamp **deletes the clashing row** (`:213`). Since every visit on a day lands at the same
     noon, both are reachable by accident — the first leaves the visit displaying a figure the vet never
     recorded while the row keeps its `visitId`, the second makes the visit's weighing vanish with no stated
     choice and no ADR-0004 ceremony. The unique index catches neither; it stops two rows claiming one visit,
     not one row being quietly rewritten. So a **visit-tagged row is excluded from `replacing`** — a clash
     against one offers *add a second weighing* or *open the visit*, with the destructive option absent
     rather than merely not-default — and a **visit-tagged weighing is read-only in the weight editor**,
     which is what keeps the visit's re-derivation the single path. The visit write path itself never
     prompts.
   - **Deleting a visit states the choice** rather than guessing: *"Also delete the 2 380 g weighing recorded
     at this visit?"* — keep standalone, or remove. One confirmation, not ADR-0004's two-stage ceremony, which
     is calibrated to a bunny's whole history.
   - The weight list and the visit both show the link — a weighing from a visit says so and offers to open it,
     and the visit shows the weighing. The **chart plots it identically**: a weight is a weight, and a
     visit-recorded number is not a different kind of truth (ADR-0022's display-only rule stands).
   - **A visit weighing satisfies a weigh-in care reminder for free**, because 4b resolved a weigh-in's last
     completion as `max(care event, latest weight)` and this is a weight. Nothing to build; asserted as a fact
     about the derivation so it cannot regress.
   - In the `Archived(id)` scope, visits and the vet picker render read-only (ADR-0004).
4. **5d — Medication courses and derived due doses: the data layer and the arithmetic.**
   ✅ *(built and closed. spotless, `assembleDebug` and JVM tests green, lint **0/0**; instrumented
   **171 tests on the Xiaomi, all green** — 5c's 153 plus eighteen new ones in
   `MedicationRepositoryTest` — 2026-08-05, through `am instrument` after two plain installs. **No
   schema change**: 5b's three entities were right, so `6.json` is untouched and this checkpoint is
   DAO, repository, arithmetic and tests only. Six decisions the plan's text did not settle, made
   here: **`DoseWindow` is a type whose only constructor opens on today**, so "derivation looks
   forward" is something the compiler holds rather than a rule each call site has to remember —
   there is no window reaching into the past to hand `dueDoses` in the first place; **a second answer
   to a slot corrects the first rather than throwing**, because the unique index is the guarantee and
   `answer` is the code that stays on the right side of it — an owner who taps *Given* having already
   tapped *Skip* in 5f's shade has changed their mind, not hit a data error; **`setTimes` is
   delete-then-insert inside one transaction with the editor's row ids carried through**, which keeps
   the identity `MedicationTimeEntity`'s UUID exists for while sidestepping the one case a per-row
   diff cannot survive — two chips swapping times trips the unique index halfway through, an empty
   table for the length of the transaction cannot; **`CourseWithTimes` is a Room `@Relation`** (one
   flow, `@Transaction`) rather than two flows combined, which care could not do because its sort key
   is derived and this one is a column; **active-before-ended is ordered in SQL with `today` as a
   parameter**, for the same reason — and a parameter rather than `date('now')`, whose day is UTC's
   and not the owner's; and **the amount is trimmed but never required**, unlike the name, because an
   owner told "one syringe, morning and night" has nothing to type in it and insisting would make
   them invent a number the app would then show as if the vet had said it. `DebugDose.kt`'s
   "**5d** replaces this body" comments now say **5f**: the swap to real derivation belongs with the
   real alarm path, and 5d only supplies the `dueDoses` it will read.)*
   - `MedicationCourseEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `name`, `doseAmount: String` (free text,
     ADR-0002 — the app never sums, converts or reasons over it), `startOn: LocalDate`, `endOn: LocalDate?`
     (null = ongoing), `notes: String?`, `remindersEnabled: Boolean`, `createdAt`.
   - `MedicationTimeEntity` — `courseId` FK `CASCADE`, `time: LocalTime`, unique index on `(courseId, time)`.
     A child table rather than a converted list because the scheduler's question is *"what is the next dose
     time across every active course"*, which is a query over times; and because the unique index makes
     "08:00 twice" impossible instead of merely unlikely.
   - `DoseEntity` — `id`, `courseId` FK `CASCADE` indexed, **`scheduledOn: LocalDate?` and
     `scheduledTime: LocalTime?`** (null *together* for an ad-hoc dose), `recordedAt: Instant`,
     `status: DoseStatus` (`GIVEN | SKIPPED`, stored by name), `note: String?`. A recorded dose **does not
     re-specify the amount** (ADR-0002). A **unique index on `(courseId, scheduledOn, scheduledTime)`** is
     what makes a derived slot answerable exactly once — the join between a row that exists and a slot that
     does not — and NULLs being distinct in SQLite is what leaves ad-hoc doses out of it without a partial
     index.
   - **The slot's key is local, never an `Instant`** (ADR-0002). Slots resolve wall-clock in the current zone
     because ADR-0003 requires it, so the same 08:00 dose is `06:00Z` in Warsaw and `07:00Z` in London: an
     instant-keyed row stops matching its own slot the moment the owner travels, and a dose already given
     reads as unanswered and re-arms its alarm. An instant is computed only to place that alarm.
     `recordedAt` stays an `Instant`, because that one *is* a real moment.
   - **`dueDoses(course, times, window, zone)` as a pure JVM function**: clamped to
     **`[max(startOn, today), endOn ?: ∞]`**, one slot per time per day, each resolved wall-clock in the *current* zone (ADR-0003). Spring-forward gap
     → **once**, shifted to the first valid instant; fall-back overlap → **once**, at the earlier offset;
     never zero, never twice. `java.time`'s default `ZonedDateTime` resolution gives both, which is why the
     ADR chose it — the test asserts it rather than trusting it.
   - **Shortening a course drops its future due doses and touches no recorded one**, which is not a code path
     but a consequence of deriving; asserted as such. Closing an open course is setting `endOn` to today.
   - **Derivation looks forward only, and that is a correctness rule rather than a scope cut** (ADR-0002).
     Times live in one row each, so deriving the past means the *current* times decide what every past day
     should have contained: move a fortnight's 08:00 course to 20:00 on day ten and the nine compliant days
     behind it re-derive as unanswered, with nine recorded doses matching no slot at all. So today and later
     derive slots; **a past day lists what was recorded** — given, skipped, ad hoc — and never a gap. "Did I
     miss one" is answered by comparison **for today**, never by absence, and never at all for a day already
     over: ADR-0001's *silence means nobody looked*, one domain across. The app does not warn, does not
     colour anything as a health problem, and does not chase a dose after the fact.
   - **A slot stops existing at local midnight**, with two consequences worth stating rather than
     discovering: a dose given at 00:30 for the previous evening is recorded **ad hoc and back-dated**, in
     the app; and 5f's notification is given `setTimeoutAfter` its own day, so the shade never holds a
     one-tap answer for a slot the app no longer models.
   - Tests, JVM: the `dueDoses` case table — both DST boundaries, an open course, a course with no times (no
     slots, ever, and reminders therefore meaningless), a course ending today, a course starting in the
     future, **a window that starts before `startOn` yielding nothing for the days already gone**, and **a
     zone change leaving an answered slot answered**, which is the whole reason the key is local.
     Instrumented: courses, times and doses cascade with their bunny and with their course; the unique
     index rejects a second answer to the same slot **and accepts any number of ad-hoc doses**;
     `recordCounts` counts courses and doses.
5. **5e — Courses and doses on screen.**
   ✅ *(built and closed. spotless, `assembleDebug` and JVM tests green, lint back to **0/0** — the one
   warning was `LocalContextResourcesRead`, this file's own; **no schema change**, so `6.json` is
   untouched and no instrumented run is owed here (PLAN's verification line puts `connectedAndroidTest`
   at 5b, 5d, 5f, 5g and the gate). Ten new JVM cases in `DoseNextTest`. **Reviewed on the Xiaomi**,
   2026-08-05, against the seeded fixture in both locales: the tab reads **Care & Meds**; today's two
   Metacam slots render with one answered and one open; *Given* and *Skipped* each write on one tap and
   the row updates with no refresh; answering tonight moved the course row from "Next dose at 8:00 PM"
   to "Next dose tomorrow at 8:00 AM" on the same emission; the delete dialog named **12 recorded
   doses** and *end course instead* kept every one of them; Nugget shows the empty state with **no
   delivery line at all**; the archived scope renders every row with no affordance on it. Four
   decisions the plan's text did not settle, made here: **the medications section goes first on the
   tab**, fixed rather than conditional — a dose has a clock time today where a nail trim has a week,
   and a screen that reorders itself under the owner costs more than the three lines an empty section
   takes; **the delivery line is hosted once per section and not per course row**, against this
   checkpoint's own wording, because what it describes is a fact about the *phone* and a copy under
   every course would be the same four sentences repeated with nothing to tell them apart — it is
   further gated on some course actually having times with reminders on, so a bunny with no schedule
   is not warned about a mechanism that will not run; **a course gets its own detail screen**
   (`MedicationCourse`, the mirror of 4c's `CareReminder`), because the editable dose history has two
   actions per row and a fourth list of those inside a three-list tab is where the tab stops being
   readable; and **answering is one tap with no dialog, correcting is another tap**, which 5d's
   `answer` already made safe by treating a second answer as a change of mind rather than a constraint
   violation. One case the arithmetic turned out to need a word for: a course ending **today** whose
   remaining slots are all answered is running but derives nothing further — `DoseNext.Done`, distinct
   from `Ended`, and it is what *end course instead* leaves on screen.)*
   - The course list per bunny: active first, then ended, each row naming its schedule in words and its next
     dose in relative time, carrying 5a's delivery state rather than presenting as an armed alarm — and that
     state is tappable, so best-effort is a route to the setting rather than a label the owner can only read.
   - **Deleting a course counts what it destroys.** `DoseEntity` is `CASCADE`, so one tap can take forty rows
     saying what was actually given to a sick rabbit — after weights, the most clinically meaningful history
     the app holds. One confirmation, not ADR-0004's two-stage ceremony, but it names the number with
     `<plurals>` the way the destroyed bucket does, and an open course offers **"end course instead"** in the
     same dialog, because that operation already exists (`endOn = today`) and keeps every dose.
   - The course editor: name, free-text amount, start, optional end with an explicit **"ongoing"** state that
     is the default and not an empty field, notes, and the clock times as chips the owner adds — with the
     **reminder switch defaulting on when times exist** and absent when they do not (ADR-0003).
   - **Today's doses are the screen's centre**, because that is the question an owner opens the app to answer:
     each derived slot with **Given** / **Skipped**, both one tap, both writing a `DoseEntity` against that
     slot. Recording is **back-datable** and ad hoc — a dose with no slot behind it is normal, not an error.
   - A course's dose history, editable and deletable, since a dose recorded against the wrong slot is exactly
     the kind of thing an owner notices an hour later.
   - **The copy never advises** (ADR-0026): the screen says what was given and what is unanswered. It does not
     say a dose was *missed*, does not warn, and does not suggest.
   - **And the rule goes on screen**, because the owner cannot read ADRs: one quiet permanent line under the
     course list, in both locales — *what your vet prescribed, as you recorded it; Binky never checks doses
     or interactions.* Not a dialog (dismissed once, then never seen, and ADR-0006 keeps that path for
     permissions) and not a warning; it states what the record **is**, in the app's own voice (ADR-0012).
     It is also the cheapest answer to a Play reviewer looking at medication screenshots on a Lifestyle app,
     because the disclaimer is in the screenshot.
   - The **medication half of the sample-data action**: an open twice-daily course with a partial history, a
     course that ended last week, and one with no schedule at all — so 5f has something to arm and 5i has
     something to look at.
   - In the `Archived(id)` scope the list renders read-only and no alarm is ever placed for an archived bunny —
     a fact about the derivation, as 4c made it for the sweep.
6. **5f — Dose reminders on the real alarm path.**
   ✅ *(built and closed. spotless, `assembleDebug` and JVM tests green, lint **0 errors and 0
   warnings**; instrumented **187 tests on the Xiaomi, all green** — 5d's 171 plus sixteen new ones in
   `DoseAlarmTest` — 2026-08-05, through `am instrument` after two plain installs. **No schema
   change**, so `6.json` is untouched. Twenty new JVM cases in `ArmedDosesTest`. `DebugDose.kt` is
   deleted as 5d promised, and with it the two Settings actions — **the overnight run is armed with a
   real course from here on**, which is what 5i asks for anyway. Nine decisions the plan's text did
   not settle, made here: **firing hands the rebuild a `postedThrough` instant**, which is a loop the
   5a fixture could not have — that fixture *cleared* its stored slot after posting, where a real dose
   notification does not answer its own slot, so the slot stays derived and stays answerable for
   another half hour and a plain rebuild would arm the same instant, fire immediately and go round
   forever; the receiver therefore passes back the latest slot it posted and the rebuild skips
   anything at or before it, with **no persisted "last posted" state**, because ADR-0025 says nothing
   incremental; **the three exclusions are a Kotlin predicate rather than the `WHERE` clause** this
   checkpoint's own wording describes, following `careDueForNotifying` — "reminders on, bunny not
   archived" are ADR-0001 and ADR-0004 rules, and a rule in SQL is a rule no JVM case table can
   assert, so the DAO reads every course joined to its bunny and `armedDoses` decides; **`endOn` is
   not one of those exclusions at all**, because `dueDoses` already clamps the window at it and
   restating the rule would be two facts that can disagree; **the horizon is one constant shared with
   the screens** (`DOSE_HORIZON_DAYS`), so a row reading *Next dose Sunday* and an alarm armed on
   nothing cannot both be the app's answer to one sentence; **the rebuild is a `DoseAlarmScheduler`
   the repositories hold**, defaulting to a no-op so a test constructs one without a phone, and it
   fires after **every** write including the two that cannot change the answer — an ad-hoc dose
   answers no slot and a note correction moves nothing — because deciding per write which ones deserve
   one is the enumeration that lost the bunny-level paths the first time; **dose notifications carry
   `setOnlyAlertOnce` and no group**, the first because any rebuild inside the grace window can re-post
   a slot that is already in the shade and the second because bundling puts an expand between the
   owner and the one tap the feature exists for; **the two buttons reuse the course screen's own
   `Given` / `Skipped` labels** rather than this checkpoint's "Given / Skip", since the same tap
   answered from two places must not have two names; **cancelling destroys the `PendingIntent` and not
   only the alarm**, asked for with `FLAG_NO_CREATE`, which is what turns "none when no course is
   armed" into something assertable rather than merely true of the alarm list; and **the action
   receiver does not re-arm for itself** — `answer()` rebuilds, because ADR-0025 puts the rebuild at
   the repository and a call-site rebuild would contradict the thing it is there to prevent. Read on
   the phone after the run: **no pending dose alarm, and that is the correct state** — the seeded
   fixture's Metacam ends today with both of today's slots answered from 5e's review, Recovery food
   has reminders on and no times, and Panacur has reminders off, so the derivation is right to arm
   nothing. **What is still unproven is delivery itself**, which is the overnight run's question and
   not something a green suite can answer.)*
   - **One pending alarm, rebuilt from truth** (ADR-0025): the earliest unanswered derived slot at or after
     now. One request code, one `PendingIntent`, `FLAG_UPDATE_CURRENT`. Recomputed after every course, time
     or dose write, **after every bunny archive, un-archive or delete**, on boot, on zone or clock change,
     when the exact-alarm permission is granted, and immediately after firing. Nothing incremental, nothing
     per-course, nothing to orphan.
   - **The query is defined by what it excludes, and `startOn` is not one of the exclusions.** A course is
     read if reminders are on, `endOn` is null or not before today, and its bunny is not archived. Whether it
     has *started* belongs to `dueDoses`, whose window already opens at `max(startOn, today)` — filter on
     `startOn` here and a course beginning tomorrow arms nothing tonight, with the next rebuild being
     tomorrow's 09:00 sweep, an hour after the 08:00 dose. "Start tomorrow morning" is how most courses are
     created, so that reading loses the first dose of nearly every one of them.
   - **The three bunny-level writes are in the list because they reach the alarm sideways.** Archiving,
     un-archiving and deleting a bunny all change the answer without touching a medication table, and a
     delete takes the courses by cascade with no course write happening at all — so a rebuild hung off the
     medication writes alone misses them, while the gate demands the invariant hold across exactly those
     operations. The rebuild is therefore wired **at the repository layer**, on every write that could change
     the answer, rather than remembered at each call site: it is idempotent and costs one query, and
     enumerating call sites is how the sideways paths were missed in the first place.
   - **Two more occasions to rebuild, because one alarm is one point of failure.** The daily care sweep calls
     `rescheduleDoseAlarm()` as its last step, and `AppContainer` does the same on process start. The failure
     this covers is invisible by construction — zero pending alarms is *also* the correct state when nothing
     is armed, so nothing can tell "no course" from "alarm lost" — and it is reachable: a receiver that hits
     ADR-0007's guard correctly does nothing, which includes not re-arming; revoking `SCHEDULE_EXACT_ALARM`
     on 14+ cancels pending exact alarms and force-stops the app, after which only a launch runs anything.
     Since the rebuild is idempotent, this costs no new state and leaves the invariant untouched. It does
     **not** unify the mechanisms: the sweep never delivers a dose, never posts on `doses`, and never decides
     when one is due. The coupling runs one way only (ADR-0025).
   - **The invariant, asserted at the gate**: *at most one pending dose alarm exists* — none when no course is
     armed — mirroring 4a's single-enqueued-work-item rule, and checkable with `dumpsys alarm`.
   - Firing posts one notification per due course on the `doses` channel, naming bunny, medicine and amount.
     **`Given` and `Skip` are notification actions**, because a dose answered from the shade is the whole point
     of the reminder — each is a receiver that hits ADR-0007's pending-schema guard first, writes the row, and
     re-arms. Tapping the body opens that course's screen through 3f's back-stack repair, switching the
     selected bunny the way 4c decided. **The notification expires with its slot** — `setTimeoutAfter` local
     midnight — because ADR-0002 stops deriving a day once it is over, and a shade still offering a one-tap
     **Given** at 19:00 for the 08:00 dose is the same eleven-hours-late answer the next bullet refuses, just
     arriving by a different route.
   - **Late is answerable, retroactive is not, and 5a's 30-minute grace constant is the line between them.**
     Fire and reschedule share the one `now - slot ≤ grace` predicate. Inside it the slot is posted — which
     is what makes the best-effort path deliver at all, since `setAndAllowWhileIdle` is routinely minutes
     late. Outside it the slot is skipped and appears unanswered in the app: a stack of 3 a.m. notifications
     at breakfast is a lie about when the app knew, and answering a dose eleven hours late from the shade is
     worse than opening the app.
   - Per-course off switch (ADR-0003), and a course with no times has no switch to show.
   - Tests, JVM: "the earliest unanswered slot" as a case table — an answered slot skipped, a reminders-off
     course excluded, an archived bunny's course excluded, all courses ended → no alarm, and **a course
     starting tomorrow arming tomorrow's first slot today**, which is the assertion that `startOn` never
     became a filter. The grace predicate as its own table: fired on time, 4 minutes late, 40 minutes late,
     phone off six hours — the first two post, the last two do not. Instrumented: the
     write paths that must re-arm, each asserted to leave exactly one pending alarm; **a sweep run with the
     alarm already correct leaves exactly one**, which is the assertion that the heartbeat is idempotent
     rather than additive.
7. **5g — Documents: the scanner, the fallback, and the viewer. No schema change.**
   ✅ *(built. spotless, `assembleDebug` and JVM tests green; **`connectedAndroidTest` green on the
   Xiaomi, 203 tests**, including thirteen new `DocumentRepositoryTest` cases and a new
   Records-scope round-trip. **No schema change**, so `6.json` is untouched.*

   ***The merged-manifest inspection produced a real finding, and it is the second half of 4h's.***
   *The three questions were asked of the release artifact, and two came back clean:*
   - ***`INTERNET` arrives.** Not from the scanner API but from `com.google.android.datatransport:
     transport-backend-cct:2.3.3`, a transitive of it — traced in the merger's blame report, not
     guessed. The claim in `docs/play-app-content.md` was **reworded to the truth rather than
     deleted**: what it was actually asserting — that this app's own code opens no socket — is still
     true and is still what Data safety rests on, and it is now stated as a claim about the app's
     code rather than about the artifact's permission list. The privacy policy moved in the same
     commit and names the scanner in the owner's words. `INTERNET` moved from the script's
     `FORBIDDEN` list to `EXPECTED`; **`CAMERA` moved the other way**, so a future dependency that
     merges one fails rather than shipping quietly.*
   - ***No `CAMERA`.** The deliberate non-declaration survives: both scan paths run on the system
     camera intent, which needs none.*
   - ***No `<uses-feature>` at all** — the half that decides who can install. `scripts/aab-permissions.py`
     grew the section anyway, because a null result from a tool that cannot see is not the same
     answer as a null result from one that can. It was proven on both branches before being trusted:
     a temporary `required="false"` and a temporary defaulted one, each read correctly, each failing
     the check, then reverted. Reading `android:required` needed the source string in `ATTR_VALUE`
     with the compiled `Item` as the fallback — the first attempt read the wrong field numbers and
     reported an explicit `false` as "absent", which is exactly the direction that would have shipped.*

   ***AAB size, measured rather than estimated: 11,404,302 → 11,951,123 bytes, +534 KB (+4.8%)**, by
   building the bundle with the dependency and again without it.*

   *Four decisions the plan's text did not settle, made here: **the viewer and the detail screen are
   one screen**, because a document is its pages and a metadata card between the list and the page
   would put two taps before the only thing anyone opened it for; **a scan is saved under a
   localised default title and opens straight into its own document** rather than stopping at a
   naming dialog — the title is then the first thing on screen and one tap from editable, and there
   is no modal to lose to a low-memory kill while the camera is still unwinding; **deleting a
   document's last page leaves the document standing**, since the title, the date and the visit are
   records in their own right and destroying them because a bad scan was removed is a delete nobody
   asked for; and **the attach picker offers only documents no visit has claimed**, because
   `visitId` is single-valued and offering a claimed one would silently detach it from the other
   visit. `capturedAt` is **never** filled from the image's EXIF, which is the one place the photo
   pipeline's instinct is wrong: that instant is when the scan was taken, and this column is the
   date printed on the page.*

   ***Still owed, and it needs a real vet printout rather than a fixture:*** *the `MediaKind.Document`
   3000 px / q92 spec is still marked unverified in its own source comment. The sample fixture
   exercises the downsample on a 3200 px page and the pinch-zoom viewer is in place to read it back,
   but the judgement — is the small print legible on the phone, and what does the file weigh — is a
   scan of a real printout and has not been made.)*
   - The tables **already exist from 5b** and have been empty since; this checkpoint gives them a UI.
     **Documents ship in 1.2** — that was settled when the migration was written, not here.
   - `DocumentEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `visitId` FK `SET NULL` nullable indexed,
     `title`, `capturedAt: Instant?`, `createdAt`. `DocumentPageEntity` — `documentId` FK `CASCADE`,
     `path: String` (relative, `documents/<uuid>.jpg`), `position: Int`. **A document is the paperwork; pages
     are its images**, because a two-page result is one vaccination record and not two documents, and because
     reordering is something a scanner's output actually needs.
   - **The scanner behind ADR-0009's interface**, with two implementations: ML Kit's
     `GmsDocumentScanning` (`IntentSender` via `StartIntentSenderForResult`), and the **existing**
     `TakePicture` path from `ui/common/CameraTarget.kt` as the fallback — already written for photos, so the
     fallback costs wiring rather than a feature. Availability is a **runtime** question, resolved at use and
     never cached across installs; the fallback engages silently and the UI states the difference (no
     auto-crop, no page detection) rather than explaining an absence the owner cannot act on.
   - **All writes through `MediaFiles.persist(Document)`** (house rule) — and its 3000 px / q92 spec, which
     its own source comment marks *unverified until the phase that ships them*, is **verified here**: a real
     vet printout scanned, the small print read back on the phone, the file size recorded. If it is wrong,
     changing the spec now costs nothing and changing it after 1.2 re-encodes nobody's existing scans.
   - The viewer is a pager with **pinch-zoom**, which the photo pager did not need and this does: the entire
     value of a document is legible small print. Missing media renders as a placeholder, never a crash.
   - Documents are attachable from a visit and from the bunny's document list, and detaching one from a visit
     leaves the document with its bunny.
   - The **document half of the sample-data action**, matching 5e's medication half: multi-page documents
     whose page images are generated through `MediaFiles.persist(Document)`, no ML Kit involved. It earns its
     place on 5h — breaching a ~25 MB budget needs a lot of documents on disk, and hand-scanning that many
     vet printouts is not a test anyone will re-run.
   - **Deleting a bunny has to take the page files with it.** `BunnyRepository.delete()` already has the
     shape: read the paths *inside* the transaction, before the cascade takes the rows and there is nothing
     left to ask, then delete the files after commit — that ordering is ADR-0020's file-first rule in
     reverse, and it is why the avatar and the photos survive a rolled-back delete. Documents add
     `documentPageDao.pathsOf(bunnyId)` to that same block. Missing it orphans the app's *largest* files
     with nothing left pointing at them.
   - **If the dependency is the problem, the dependency goes — not the feature.** ML Kit brings the merged
     manifest, the AAB size and the Play-services-absent path; documents as *data* bring none of them, and
     the `TakePicture` fallback is already written. So the contingency is dropping ML Kit and shipping
     documents on the camera path, losing auto-crop and page detection and nothing else — which is precisely
     what ADR-0009's interface was for. **The interface and the fallback are built and proven before the ML
     Kit implementation lands**, so "drop the dependency" stays a one-line container change for the whole
     checkpoint instead of a rewrite under release pressure.
   - **The dependency's merged manifest is inspected in this commit** with `scripts/aab-permissions.py` — 4h's
     finding was that the merger writes permissions nobody declared. Three answers matter, not one:
     - **`INTERNET`**, since `docs/play-app-content.md` claims *"no network code of our own"* and Data safety
       is cross-checked against the privacy policy. If it arrives, the claim is **reworded to the truth**,
       not deleted, and the privacy policy moves in the same commit.
     - **`CAMERA`**, which this app has deliberately never declared — the manifest says why: declaring it
       makes it *required at install*, and firing the system camera intent needs none. A merged `CAMERA`
       changes the store listing, so keeping the scanner at that price is a decision to take here rather
       than a merge artefact discovered at 5j.
     - **`<uses-feature>` and its `required` attribute.** A merged `android.hardware.camera` at
       `required="true"` — the default when the attribute is omitted — **filters the app off every device
       without a camera on Play**. That is a distribution change that no permission list would show, so
       `scripts/aab-permissions.py` grows a `uses-feature` section: it is the tool of record and it currently
       cannot see the half that decides who can install.

     AAB size before and after is recorded too.
   - Tests, instrumented: documents and pages cascade with their bunny and their document; a deleted visit
     leaves its documents attached to the bunny; `recordCounts` counts documents; a **Records-scope export
     round-trips them**, which is free — `BackupScope.Records` has listed `MediaKind.Document` since 3d — and
     is asserted rather than assumed.
8. **5h — The backup agent's document admission, and the exclusion notice.**
   ✅ *(built. spotless, `assembleDebug` and JVM tests green; no schema change, no instrumented run —
   every decision here is arithmetic over `File`s. **And then run for real on the Xiaomi**, against
   `com.android.localtransport/.LocalTransport` so nothing left the phone, with the transport put back
   to Google's afterwards.*

   ***The device run found a defect no JVM test would have.*** *`POST_NOTIFICATIONS` was denied on the
   debug app at the time, so `postReminderNotification` returned silently — and the flag recording
   "the owner has been told" was written anyway. A one-time notice consumed by a notification nobody
   saw, on the exact phone whose owner would then grant the permission and never hear about it. The
   fix is one check against the vocabulary Phase 4 already built: `ReminderDelivery.Blocked` means
   nothing will arrive and the flag stays unset, so the next launch asks again; `BestEffort` still
   delivers something and still counts. This is the failure ADR-0005 names — data quietly not
   protected — arriving through the notice meant to prevent it.*

   ***What the phone proved, end to end.** 48 document pages / 23.4 MB on disk (5g's fixture, padded
   with copies under fresh uuids — the agent admits from the filesystem, so copies are faithful
   input). `bmgr backupnow` transferred 20,830,208 bytes and succeeded, and the agent — in its own
   process, with no `AppContainer` — wrote `excludedDocuments=7` into the marker. The Backup screen
   read **7**, the notification read **7**: one number, two renderings, which is the property the
   marker was given the field for. A second launch with the condition unchanged posted **nothing**.
   Padding removed, a fresh run wrote `excludedDocuments=0` and the next launch posted nothing again.
   The re-arm after a cleared episode is JVM-tested only; the device saw the flag set and the silent
   states, not a second episode.*

   ***Five decisions the plan's text left open, made here.*** *The **ceiling is 20 MiB**, not the
   ~25 MB quota: the quota is undocumented as an API, the penalty for crossing it is rejection of the
   whole dataset rather than a trim, and the headroom buys the core arriving even when this file's
   arithmetic disagrees with the transport's — tar overhead, or a database that grew between the set
   being computed and the bytes being read. The real run landed 20,830,208 bytes against a 20,971,520
   ceiling, which is that margin doing its job. Admission **skips rather than stops**: one oversized
   scan must not exclude the smaller history behind it, so newest-first buys *priority* — an older
   document never displaces a newer one that fits — rather than a prefix. A **device-to-device
   transfer carries every document**, with the ceiling not consulted at all, for the reason the
   gallery already travels there: no cloud account, no quota, and dropping half an owner's paperwork
   on a phone upgrade is the worse failure. "Newest" is the **file's own mtime**, because the agent
   has no database to ask and `MediaFiles` writes the file before the row anyway (ADR-0020); the
   filename breaks ties so an unchanged phone produces an unchanged set. And the notice is **once per
   episode, not once ever** — the flag clears when the count returns to zero, so an exclusion that
   recurs years later is still allowed to speak. The channel is the existing `backup` one at
   `IMPORTANCE_DEFAULT`, as the plan required.*

   *The marker's tolerance now runs **both** ways and is tested both ways: an unknown key is ignored
   (which is what let this field be added at all), and a **missing** count reads as zero, so a marker
   written by 1.0 or 1.1 describes a backup that happened rather than a file that will not parse.)*
   - **`admitDocuments(coreBytes, documentsNewestFirst, budget)` as a pure function over `File`s** — no
     `Context`, per ADR-0005, because the agent runs in a process where `AppContainer` does not exist and
     reaching for it would force the very `lazy` ADR-0007 guards. The budget is **dynamic**: documents get
     what is left under the ceiling after the core, so a growing database shrinks the document allowance
     instead of taking the whole dataset over quota. Keeping the evidential core under quota is the first
     duty; preserving as many documents as fit is the second, and the function's shape says so.
   - `autoBackupFileSet` **changes shape**: admission is per-file and ordered, so it stops being a flat list
     of files and directories and becomes the unconditional core plus the admitted documents, newest-first.
     That is a signature change to a function `BinkyBackupAgent` already calls, not merely a new caller.
   - The **marker gains its excluded count** — the file, the temp-then-rename write and the unknown-key
     tolerance all landed at 3d precisely so this could be added without invalidating a marker written by 1.0.
   - Backup settings' status line says it in words: *"Last automatic backup: 3 days ago — 12 documents were
     too large to include; use manual export to keep them."* Never dropped silently (ADR-0005).
   - **The agent writes, the app posts.** The one-time notice cannot come from the backup agent, and the
     reason is already written down in `AutoBackup.kt`: the agent cannot reach the app's DataStore, *"its
     writes are `suspend` inside blocking backup callbacks"*. So it has nowhere to record that the notice has
     fired — and Auto Backup runs roughly daily, which turns a "one-time" notice into a nightly one on the
     channel an owner is most likely to mute. Instead the agent writes `excludedDocuments: N` into the
     marker, and **the app posts on next launch** if the count is non-zero and has not been notified,
     recording that in DataStore where once-only bookkeeping works. The status line reads the same field, so
     the words and the notification cannot disagree. The cost is honest: the notice arrives when the app is
     next opened rather than at 3 a.m. — which is the right latency for a standing condition rather than an
     event, and it is still the first thing the owner sees.
   - **No new channel.** The notice posts on the **existing `backup` channel at `IMPORTANCE_DEFAULT`**, and
     the plan's earlier `IMPORTANCE_LOW` is withdrawn as unavailable rather than merely reconsidered: 4e
     created `backup` at DEFAULT, 1.1 has shipped it, and an app cannot change an existing channel's
     importance — only the owner can. Declaring LOW would give fresh 1.2 installs one behaviour and upgraded
     ones another. It is also the wrong instinct twice over: `ReminderChannels.kt` already refuses to make
     the mute decision on the owner's behalf "in the one direction that cannot be undone", and this is the
     notice that says data is **not** protected, which is not "information, not an event".
   - Tests, JVM: the admission function as a case table — everything fits; nothing fits; the newest-first
     order respected at the boundary; a core already over budget admitting zero documents rather than going
     negative; the marker round-tripping an excluded count and an old marker without one still reading.
9. **5i — The gate pass, freezing schema 6, and the definitive overnight Doze run.**
   🔨 *(in progress, 2026-08-05. The **software half is green**: spotless, `assembleDebug` and JVM
   tests, and `lint` back to **0 errors and 0 warnings** (17 hints), which 3g reached and the job
   since has been to hold. **201 instrumented tests on the Xiaomi, all green** in 29 s — 5f's 187
   plus what 5g and 5h added — and CI's matrix is green **by name** at API 26 / 34 / 36 on `main`.
   **Not closed**: the overnight Doze run is armed for the night of 5→6 August, and everything that
   would disturb it — the writes against the armed course, denying notifications, the destructive
   halves of three dialogs, the reboot pair and the timezone change — is deliberately after it.*

   ***Three findings from arming it, none of which a JVM test could have produced.*** *`am instrument`
   **cancels the app's pending alarms**: the runner force-stops the target package, and a force-stopped
   app loses every alarm it placed. So the instrumented suite must never be the last thing done before
   an overnight run — afterwards `dumpsys alarm` shows nothing pending, which is indistinguishable
   from a broken rebuild. Launching the app put the alarm back at the identical instant, which is
   ADR-0025's launch occasion proving itself twice by accident. **`dumpsys alarm` is the only place
   the two mechanisms are visibly different**: with `SCHEDULE_EXACT_ALARM` at `default` the pending
   dose reads `window=+38m55s`, `flags=0x20` (allow-while-idle compat) and a `maxWhenElapsed` 39
   minutes past `whenElapsed` — the OS's own statement that this is `setAndAllowWhileIdle`, and the
   corroboration the "best-effort in words" bullet never had. An exact alarm has `window=0` and the
   two instants equal, so **that pair of fields is what a run must be read against** before it can
   claim to have tested the exact path; the 4→5 August run could only say so from the appop
   afterwards. And the **autostart state, read before anything was touched**: ten apps permitted,
   `Binky` (the Play build) among them and **`Binky Debug` not** — so it was granted for the build
   under test, header 10 → 11, deliberately, so a MIUI kill cannot return outcome 3 for a reason that
   is not `setExactAndAllowWhileIdle`. Battery-optimisation exemption stays absent.*

   ***What the device has already answered.*** *ADR-0021 from both sides: a visit's weighing offers
   only* Recorded at a vet visit *and* Open the visit *— no Edit, no Delete, unlike every other row —
   and a manual weighing entered at that same noon offers* Add a second *or* Open the visit*, with no
   Replace anywhere. Both delete dialogs say what the gate asks: the visit names its weight in grams
   and offers* Keep the weighing on its own */* Delete the weighing too*, and the vet reads "Every
   visit that named them stays exactly as it is, without the name." A two-page document reopens after
   a real process restart with its pages in order. The tab reads* Care & Meds*; the medication screen
   carries ADR-0026's line; a skipped dose reads* Skipped *with its note rather than as an absence;
   and no string in either locale says* missed *or* overdue *outside Phase 4's care reminders, where
   a passed date genuinely is overdue.*

   ***4f's capture script now covers this phase*** *(`scripts/edge-to-edge.py`, 46 → 59 scenes): the
   course detail and the end of its dose history — the longest list the app builds — the course
   editor with its end-of-scroll and its IME, the visit editor, the vets list and vet editor, the
   documents list, the document viewer, the record-dose dialog, the document-actions menu, the Care
   tab's new middle, and `care-empty` on the wiped install. The matrix itself runs after the overnight
   run, because its `wipe` steps would take the armed course with them.*
   - The gate below, driven by hand on the Xiaomi. The **overnight Doze run is a calendar item, not a task**:
     armed the evening before, **left unplugged past the fire time** — which is the one thing 4g could not
     claim — with a real medication course. The Phase-4 carry rides its own night rather than this one, so
     this run asks one question about one mechanism: does the dose alarm fire at its exact clock time out of
     deep Doze. Any night will do for either; neither is date-fixed.
   - **The reboot half of the alarm invariant runs twice — autostart granted and autostart denied**
     (ADR-0025). Two of the three rebuild occasions are gated on autostart, which is off by default on
     HyperOS and unreadable from inside the app, so a single run in whichever state the phone happens to be
     in cannot distinguish "self-heals within a day" from "self-heals when someone opens the app". The denied
     run is the one that describes an owner who skipped the prompt, and whatever it says is what ADR-0025's
     consequence is reworded to.
   - **Schema 6 is frozen**: `6.json` committed and git-tagged (ADR-0007), `MIGRATION_5_6` no longer pending.
   - `lint` back to **0 errors and 0 warnings**, which 3g reached and the job since has been to hold.
   - The CI instrumented matrix green at API 26 / 34 / 36. **Both ends earn their place this time**: 26 is
     where no exact-alarm permission exists at all *and* where an emulator without Play services runs the
     scanner fallback; 34+ is where `SCHEDULE_EXACT_ALARM` is denied by default and the best-effort path is
     the default path.
10. **5j — 1.2 to the tracks, and Phase 4's Console half.**
    - Release-please cuts 1.2.0; the bundle is checked **against the artifact rather than the config** — 3a's
      lesson, reinforced by 4h's six-permissions finding — for `versionName`, `versionCode`, the upload key,
      the Polish strings in `base/resources.pb`, and the full permission list from `scripts/aab-permissions.py`.
      ✅ **Done 2026-08-05.** `v1.2.0` → `4097448`, and the bundle built from it reads: `versionName` 1.2.0,
      `versionCode` **211** (matching `git rev-list --count HEAD`), upload key `CN=Maksymilian Sredniawa,
      O=Binky, C=PL` SHA384withRSA, **709/709** Polish strings present, and **8 permissions, all accounted
      for, none of the four forbidden ones present, zero `<uses-feature>`**. The permission set is exactly
      what 5g predicted from the pre-release build, so ML Kit changed nothing between 5g and the release —
      which is the answer §7's one live question was waiting on.
    - **The upgrade proof, twice over.** The committed schema-4 and schema-5 fixtures migrate to 6 in CI on
      every pull request; on the phone, the run that matters is the **longest chain the field can produce** —
      whatever version a real device is on, forward to 1.2, real bunny history intact. This is 4h's carried
      item, and 1.2 is where it is finally cheap to do properly, because the fixtures already say what the
      answer should be.
      ✅ **The CI half is done** — both fixtures are committed and both migrate to 6 on every pull request at
      API 26 / 34 / 36 (see 5b). ⏳ **The phone half is carried again, and the chain got longer than
      expected**: the Xiaomi's Play build is on **1.0.0**, not the 1.0.1 that 4h assumed, so the field chain
      is 1.0.0 → 1.2 across *both* hand-written migrations. It cannot be run locally — the installed build is
      signed by Play's app-signing key and a locally built APK is refused over it with a signature mismatch,
      so the update has to arrive **from a track**. That makes this strictly downstream of the upload below,
      and the upload is downstream of Play's 12-testers / 14-day count, which was still running at 5j.
    - **`docs/play-app-content.md` re-verified, with one live question rather than three**: §7 Data safety,
      if ML Kit changed the permission set. §4 and §10 are **re-reads, not open questions** — the document
      answered both at Phase 3 with reasoning that medication does not disturb: Play's Health apps policy and
      its declaration are written for **human** health, Binky is listed under **Lifestyle** rather than
      Health & Fitness, and IARC's controlled-substances question is about depicting drug use, not about
      recording a vet's prescription for a rabbit. ADR-0026's on-screen line is the visible half of the same
      position, and it lands in the screenshots.
    - **Screenshots for both listings**, deferred once at 4h and owed for the screens 1.1 and 1.2 both added.
      ⏳ **Blocked at 5j on a device gate, not on the work.** `scripts/edge-to-edge.py` navigates with
      `input tap` / `input swipe`, and on 2026-08-05 the Xiaomi went back to **dropping synthetic taps** —
      exit 0, nothing delivered, on a screen provably on, unlocked and focused. `input keyevent` still works
      (`KEYCODE_HOME` leaves the app), so the two go through different paths and keyevents working is **not**
      evidence taps do; test the distinction with `KEYCODE_HOME` before planning any tap-driven run.
      Presumably *Debugowanie USB (ustawienia zabezpieczeń)* was reset — it needs a Mi account, and it is the
      same toggle that gates USB install. Nothing downstream is actually waiting: the listings cannot be
      updated while the Play count runs, so this rides with the upload below.
    - Internal track first, then closed — the same order every release has taken. If 1.0.1's closed run has by
      then satisfied Play's 12-testers / 14-day requirement, **production becomes available for the first
      time**; whether 1.2 is the build that takes it is an ADR-0009 decision made then, not an automatic
      consequence of being allowed to.
      ⏳ **Not done at 5j: the count was still running on 2026-08-05**, so nothing was uploaded and the
      Console half carries for the second release running. This is the same blocker 4h recorded, and it is
      Play's clock rather than anything in the build — the bundle itself is cut, signed and verified above.
      The upgrade proof, the screenshots and the track uploads therefore all land together in one sitting
      once the count clears, which is cheaper than the three separate device trips 4h imagined.

    ***What 5j closed, and what it did not.*** *The build half is finished and evidenced: 1.2.0 is tagged and
    artifact-verified, schema 6 is frozen and tagged (`schema-6` → `01a769e`, the same convention `schema-4`
    and `schema-5` follow), and `bunny-schema-5-fixture.zip` closes 5b's last item so both hand-written
    migrations are now proven on every pull request against archives this build did not describe. The Console
    half is untouched, blocked entirely on Play's testing count. One assumption died usefully: the schema-5
    fixture was written down as needing hands on the phone, and did not — the SAF picker only chooses where
    the bytes land, so driving the shipped build's own exporter from an `androidTest` class produced a
    faithful archive in 1.4 s. The generalisation is worth keeping: when a flow's* output *is what matters
    rather than its screens, check whether the shipped code can be called directly before booking a device
    chore. And a second assumption was corrected rather than died: the field chain is longer than 4h thought,
    because the Xiaomi's Play build is on **1.0.0**, not 1.0.1.*

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 5b, 5d,
5f and 5g — the migration, the two data layers and the media path — and again at the gate; `lint` at the gate,
holding at **0 errors and 0 warnings**. CI runs the instrumented suite on every pull request at API 26 / 34 /
36, which is what makes both schema fixtures always-on, and the Xiaomi run stays at the gate: an emulator has
no HyperOS background killer, and this phase's whole reliability argument is about that killer.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **5a**: ADR-0003,
0009, 0006, 0007, 0024, 0025. **5b**: ADR-0007, 0017, 0002, 0023, 0004. **5c**: ADR-0017, 0021, 0015, 0004,
0022, 0013.
**5d**: ADR-0002, 0003, 0001. **5e**: ADR-0002, 0026, 0001, 0004. **5f**: ADR-0025, 0003, 0024, 0007.
**5g**: ADR-0009, 0020, 0017, 0005. **5h**: ADR-0005, 0007. **5i**: ADR-0007, 0023, 0003. **5j**: ADR-0009,
0013, 0007, 0012.

**Gate:**

- A dose reminder fires **at its exact clock time** after the phone has sat idle in **Doze overnight** —
  screen off, app unopened, 12h+, **still unplugged when it fires** — on the real Xiaomi. The two-minute happy
  path is not sufficient evidence (ADR-0003). If it does not fire, that is recorded as a finding and doses
  ship explicitly as best-effort, which is the honest state the app already has copy for.
- **The Phase-4 carry is already settled by then**, on its own unplugged night rather than here: the care
  sweep firing while **still in Doze** (the half 4g could not claim), and a watch **auto-expiring** — the
  nagging stops that morning, the prompt shows the *current* trend, and dismissing it leaves no row behind.
  Any night will do; `seedWatches` back-dates `startedAt`, so the expiry morning is chosen rather than waited
  for.
- **At most one pending dose alarm exists in the app**, before and after adding courses, recording doses,
  **archiving and un-archiving a bunny, deleting a bunny with an armed course**, changing the clock,
  rebooting **and running the care sweep** — and **none** when no course is armed (ADR-0025). The sweep's
  rebuild is the heartbeat, so it has to be provably idempotent on the device, not only in a JVM test. The
  three bunny-level operations are in the list because they reach the alarm without touching a medication
  table.
- **The reboot check runs twice, autostart granted and denied** (ADR-0025), because two of the three rebuild
  occasions are gated on it and the app cannot read it. Whatever the denied run says is what the ADR's
  self-heal consequence is reworded to.
- With the exact-alarm permission **denied** — the default on Android 14+ — a dose reminder still arrives, and
  the app says **best-effort** in words rather than presenting as an armed alarm. Granting the permission
  afterwards re-arms the pending alarm exactly, with no app launch in between. **Arriving is the assertion
  here, not arriving punctually**: `setAndAllowWhileIdle` is delivered when the OS chooses, and a reminder
  that posted nothing because its own slot had gone four minutes stale would look exactly like the correct
  quiet of nothing being armed (5a's grace window).
- **A course created today and starting tomorrow arms tomorrow's first dose**, before any sweep runs. The
  first morning of a course is the one an owner is least likely to have a routine for.
- With notifications denied or the `doses` channel muted, doses present as **blocked**, and creating a course
  still works.
- **A two-page scanned document reopens after restart**, its pages in the order they were left in, legible
  enough to read a printed dose off it.
- On a device **without Play services**, scanning falls back to the camera path and produces the same
  document rows — the feature degrades, it does not disappear or crash.
- **A visit-recorded weight appears in the chart**, exactly once, and deleting the visit offers the stated
  choice — keeping it leaves a standalone weighing, removing it takes both. Deleting the **vet** leaves the
  visit standing with no vet named.
- **A visit's weighing cannot be overwritten from the weight side** (ADR-0021): it opens read-only with a
  route to the visit, and entering a manual weighing at the same noon offers *add a second* or *open the
  visit* — never replace. Checked with **two visits on one day**, which is where the collision is reachable
  without anyone trying.
- **Logging a weight at a visit satisfies a weigh-in care reminder** without any tick, exactly as a manual
  weighing does.
- **Shortening a course removes its future due doses without touching recorded ones**, and closing an open
  course is the same operation. A course with no schedule still records doses ad hoc.
- A skipped dose is visible as **skipped**, not as an absence; today's unanswered slot reads as unanswered
  and the app makes no health claim about it (ADR-0001, ADR-0026).
- **Changing a course's dose times leaves its history alone.** A course answered daily at 08:00 and then
  moved to 20:00 still shows those days as given — no day already over acquires a derived gap (ADR-0002).
- **Changing the device's timezone leaves today's answered doses answered**, and does not re-arm an alarm
  for a dose already given — the reason a slot is keyed by local date and time rather than by an instant.
- The medication screen carries the **records-not-advice line** in both locales, and no string anywhere in
  the phase says *missed*, *overdue* or warns (ADR-0026).
- Deleting a bunny counts visits, courses, doses and documents in the destroyed bucket, with correct
  pluralisation, and offers their preserved copies (ADR-0004).
- A **Records-scope export** carries documents and restores them, and an **Auto Backup** over the quota
  excludes documents newest-first while the database, preferences and avatars still go — with the excluded
  count visible in Backup settings and notified once.
- **A receiver woken by the OS while a schema mismatch is pending does nothing** — no wipe, no crash, no alarm
  placed — for all four of boot, timezone, clock and permission-granted. Asserted by test.
- **The longest upgrade the field can produce keeps a real bunny's history**: a device on 1.0.1 (schema 4)
  taken straight to 1.2 (schema 6), and both committed fixtures migrating in CI. Schema **6**'s exported JSON
  is committed and git-tagged.
- The tab reads **Care & Meds** again, with medications actually behind it, and the nav key is unchanged from
  the one 1.0 persisted.
- Every new string exists in both locales, counts use `<plurals>`, and `PolishTranslationTest` is green — the
  test being the gate, not a read-through.
- Every new screen renders correctly edge-to-edge in both orientations under both navigation modes, dialogs,
  sheets and a landscape keyboard included — 4f's matrix re-run for what this phase adds, with its scripted
  capture.
- An empty database still produces no warnings, and nothing in this phase infers anything from silence
  (ADR-0001).
- Then the 1.2 release: internal track, then closed, installable from Play on the Xiaomi.

## Releasing — at the end of Phases 3, 4, 5 and 6

Signed release build, keystore out of git, signing config from `local.properties`. A Play listing cannot
change its signing key, so the key is generated **once**, backed up off this machine, and never
regenerated (ADR-0009).

Releasing is not a phase. It happens three times, and the schema shipped at each one acquires a permanent
migration obligation (ADR-0007) — which starts at 1.0, while the medication and vet tables are still being
designed. That is already provided for: their churn happens on a throwaway debug database, and a single
consolidated, tested migration from the last released version is written once each feature settles.

## Verification

- Per phase: `assembleDebug installDebug` on the phone and exercise the new screens; `lint` clean.
- **JVM unit tests** for logic that is easy to get subtly wrong: trend math, derived dose schedules,
  reminder next-occurrence arithmetic including DST boundaries (a clock-time dose **fires once, at the
  intended local time**, across both spring-forward and fall-back), backup zip round-trip.
- **Instrumented Room tests** against an in-memory database: DAO queries, cascade deletes, migrations.
  Exported schema JSONs are committed so migrations are reviewable. Note: split-APK installs prompt for
  confirmation on the Xiaomi device.
