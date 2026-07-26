# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [x] **Phase 1** — Data layer, bunnies, avatars
- [x] **Phase 2** — Weight and observations
- [ ] **Phase 3** — Backup, first-run setup, photo gallery — **ships as 1.0**
- [ ] **Phase 4** — Care reminders and watch — **ships as 1.1**
- [ ] **Phase 5** — Vet, medications, documents, dose reminders — **ships as 1.2**

The rule is **no release before the data is safe**, which Phase 3 satisfies (ADR-0019). It replaces the
former blanket ban on shipping before every phase was complete — a rule that held the weight trend flag,
the app's one load-bearing safety signal, hostage to a document scanner. Release work is therefore not a
phase of its own; it happens at the end of each of Phases 3, 4 and 5.

## Phase 0 — Toolchain, project skeleton, docs ✅

JDK 21, Android SDK under `~/Android/Sdk`, `ANDROID_HOME` in `~/.zshrc`, Xiaomi device over USB.
Compose project scaffolded with `android create` (AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Navigation 3),
package `app.bunny.tracker`.

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
- A deliberately broken avatar path (`adb shell run-as app.bunny.tracker rm …`) renders the placeholder,
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

## Phase 3 — Backup, first-run setup, photo gallery — ships as 1.0

Moved ahead of vet/meds: by the end of Phase 2 the app holds irreplaceable data with no way off the device.
That is also why this phase is 1.0 (ADR-0019) — the data being safe is the whole precondition for having
users, and everything after it is additive.

**Register the Play developer account and open the internal testing track at the *start* of this phase**,
not the end (ADR-0009). New personal accounts face a closed-testing prerequisite — 12 testers over 14 days
at the time of writing — which is a multi-week wait if discovered at release time. Verify the current
policy before assuming the numbers.

- Photo gallery, moved here from Phase 1 (ADR-0015): per-bunny lazy grid, full-screen pager, captions. It
  lands alongside backup because photos are the sentimental bulk excluded from Auto Backup and covered only
  by the "Everything" manual scope — building the gallery and that boundary together keeps them in step.
- Auto Backup via a **custom `BackupAgent`** (ADR-0005): checkpoints the WAL, includes database,
  preferences and avatars unconditionally, then admits scanned documents **newest-first up to a ceiling
  below the ~25 MB quota** — because Android rejects the *whole* over-quota dataset, the guard's first job
  is protecting the evidential core, not the documents. The photo gallery is excluded. What was dropped is
  persisted as a marker (timestamp + excluded count) and surfaced honestly in Backup settings plus a
  one-time notification — never silently.
- **The backup status line cannot be allowed to lie in either direction** (ADR-0005). Absence of a marker
  is rendered in words — *"No automatic backup has been recorded on this phone"*, with a button into system
  backup settings — never as a blank, which reads as a working net and is ADR-0001's silence failure applied
  to backup. `onRestoreFinished()` **clears the marker**, or a restore carries the old phone's timestamp
  onto a device that has never backed up anything. Past **14 days** the status says it is stale rather than
  showing a bare date.
- Manual export at the three scopes — Essential / Records / Everything — via the share sheet.
- Restore (ADR-0005): a full database replace but a **media merge** (keyed by relative uuid path, so an
  Essential restore keeps photos already on disk), gated behind an explicit confirmation and a pre-restore
  database snapshot, stating honestly what the file contains.
- First-run setup, **two steps at 1.0**: add first bunny (skippable) → backup scope. ADR-0006's reminders
  opt-in moves to 1.1 with the reminders themselves — 1.0 has nothing that posts a notification, and Android
  allows only two denials before the permission is refused for good. The backup step also **asks whether
  system backup is switched on**, with a deep link into Android's settings — the app cannot detect it, and
  this is the one moment the owner is already thinking about it.
- Top-level destinations get their **visibility state** (`Hidden` / `ComingSoon` / `Live`) set for real
  before this ships — the enum was defined in Phase 1, so this is the one-value flip, not an introduction —
  since 1.0 is the first build real users see: Care & Meds is hidden rather than opening onto a stub, while
  unbuilt rows inside More may read "coming soon" (ADR-0019, ADR-0015).
- **Polish, and the in-app language switcher** (ADR-0013). That ADR says Polish lands before the Play
  release, and 1.0 *is* that release — so it lands here rather than being deferred with an amendment. Four
  phases of "no user-facing string is hardcoded" exist to make the translation one new file with no code
  changes; the switcher's *mechanism* lands a checkpoint earlier, because at `minSdk` 26 the AppCompat
  backport may reach considerably further into the app than a Settings row.
- Then attempt the remembered-folder destination and **verify on the real device** whether Google Drive's
  provider accepts writes. Still the plan's biggest unverified assumption — but with the evidential core
  now in Auto Backup (ADR-0005), it gates only the sentimental photo gallery, not vet evidence, so its
  failure is survivable.

### Checkpoints

Six again, and for the same reason as Phase 2 — but the **first one is not code at all**, and the ordering
is deliberate twice over. ADR-0009's registration deadline and the closed-testing clock are calendar costs,
not engineering ones: started now they run in parallel with the work, left to the end they block the
release. And the two backup halves are built **provable-first** — manual export and restore before the
unattended agent — because they share the WAL checkpoint and the scope-to-file-list, and that machinery is
better built where a test can watch it. ADR-0019 gates 1.0 on *the data being safe*, which export and
restore satisfy on their own; the agent is ADR-0005's effortless net **on top**, so an agent that turns into
a swamp costs a release date rather than a release. Everything else runs one way: photos need the schema
bump, restore needs an export to restore, first-run setup needs a backup scope to offer.

**One schema bump, one wipe** — version 4 at 3b, and it is **the last free one** (ADR-0007). From 1.0
onward every schema version that reaches a device carries a tested forward migration, so the bump happens
before anyone else is on the testing track, and its exported JSON is the first that is git-tagged and
load-bearing. What that costs mechanically is not "write migrations from now on" — the destructive
fallback, the consent screen, the debug build and restore all have to move, which is **ADR-0023**, written
because this phase is where those four come due.

1. **3a — The release path, proven while the payload is boring.**
   - Pay the $25, register the developer account, and **re-read the current closed-testing policy rather
     than assuming 12 testers over 14 days** (ADR-0009) — that number decides *when to register*, not
     merely how to ship, so a stale reading of it is expensive in a way a stale API reading is not.
   - Keystore generated **once**, kept out of git, backed up off this machine; `signingConfigs` read from
     `local.properties`, and a release build with no key **fails loudly** rather than falling back to the
     debug key — a Play listing cannot change its signing key afterwards.
   - **`applicationIdSuffix = ".debug"` and a distinct debug label** (ADR-0023). Without it, `installDebug`
     stops working the day this checkpoint succeeds: the Play build is signed with the release key, a
     locally-signed build of the same `applicationId` can neither sit beside it nor replace it, and the only
     way through is uninstalling the Play build. ADR-0007 offered "or a separate DB name" as an
     alternative; it is not one, since it does nothing about two builds being unable to coexist.
     `FileProvider`'s authority already interpolates `${applicationId}`, so it follows; the instrumentation
     package becomes `app.bunny.tracker.debug.test`, which is a correction owed to **CLAUDE.md**'s Xiaomi
     fallback command.
   - One signed build on the **internal testing track**, at Phase 2's feature set. Nothing about the payload
     is new, which is the point: upload, track configuration, Play's review and install-from-Play on the
     Xiaomi are each proven while none of them are entangled with a feature under review.
   - That build is then **uninstalled**. It is a pipeline proof, not a dogfood build: leaving a release
     build at schema 3 sitting on the phone would create a migration obligation for a version nobody used,
     and 3b's wipe is spent on the debug app instead. The author's real bunny history starts at 1.0, which
     is the moment ADR-0007 says it should.
   - `versionName` / `versionCode` stay automated (release-please) and are never hand-edited. Two things to
     verify: that the automated values reach a **release** build's manifest, which no debug build has ever
     demonstrated, and that the `gitVersionCode` fallback to `1` **fails a release build** rather than
     shipping — a Play track never forgets its highest `versionCode`.
   - There is no test gate here. The gate is that the build is installable from Play on the Xiaomi, and that
     the debug app installs beside it — and discovering both at 3a rather than at 3f is the entire reason
     this checkpoint is first.
2. **3b — Photos: the gallery, and the last free wipe.** Schema → **4**.
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
     `FileProvider` plumbing — no CameraX, no new permission. Multi-select runs `persist` N times
     **sequentially, with progress**: a twenty-photo import that decodes twenty full-resolution bitmaps at
     once is precisely the failure the house rule about the media helper exists to prevent.
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
     so 3c and 3d have something real to include and exclude.
3. **3c — Manual export, and restore.**
   - Zip at three scopes — Essential (database + `avatars/`), **Records** (default; plus `documents/`),
     Everything (plus `photos/`). A scope is **a list of `MediaKind`**, which is what ADR-0020 gave the enum
     a `directory` for; no magic strings.
   - The **WAL checkpoint and the scope-to-file-list are built here**, as the shared helper 3d will reuse.
     An export that captures a mid-write database is the same bug as a backup that does, and it is written
     once, in the checkpoint where a test can watch it happen.
   - The scope is recorded in the filename — `bunny-<scope>-<timestamp>.zip` — so restore can state what the
     file actually contains. Out through the **share sheet**, from a new `cache/exports` FileProvider path:
     cache, so a share the owner abandons is reclaimed by the OS rather than doubling the app's footprint.
   - Restore is gated behind an explicit confirmation naming *"[scope] backup from [date]"*, and first takes
     an **automatic Essential-scope export of the current state** into the existing `preserved/` — a zip
     rather than a bare `.db`, so **undoing a bad restore is the ordinary restore path** rather than a
     recovery procedure with `adb` in it. Restore is the most destructive thing the app does; it is the one
     operation that should have a way back built out of parts already tested.
   - `preserved/` therefore holds two kinds of occupant with opposite properties — wipe copies (stale
     schema, unrestorable by design) and restore snapshots (current schema, restorable in one tap). Settings
     **names what each row is** and says which can be restored and which can only be shared; two files that
     look alike and mean opposite things is exactly the ambiguity ADR-0007's list was not written for.
   - **Stage, migrate, swap** (ADR-0023). Unzip to a staging database; refuse anything at a *newer* schema
     outright, since no migration runs backwards; open the staged file with the real migrations, so it is
     already at the current schema; then swap it in. A failure lands on the copy, before anything on the
     phone has been touched. The staged builder **pins its own configuration**, or 3b's debug fallback would
     quietly empty the very file it was asked to test.
   - This replaces comparing version numbers, which only asserts that a migration exists and never that it
     survives *this* file — and which, written as "refuse anything that isn't this build's version", would
     make every existing backup unrestorable the day 1.1 bumps the schema, inverting ADR-0005's whole
     promise.
   - Database **replaced**, media **merged** by relative `<kind>/<uuid>.jpg` path (ADR-0005): an Essential
     restore onto a phone that still holds its photo files keeps them instead of turning them all into
     placeholders.
   - The process then **restarts**. Half the app is holding `Flow`s over the file that was just replaced,
     and reopening in place serves stale rows out of live caches.
   - Tests, JVM, per the Verification section: the zip round-trip; what each scope's manifest contains; and
     merge semantics as a pure function over two file lists — kept, overlaid, orphaned. The **newer-schema
     refusal** is testable now; the older-schema migration path is not, because at 1.0 no older released
     schema exists — it becomes a real test at 1.1, and the plan should not claim it before then.
4. **3d — Auto Backup: the custom agent, and the marker that must not lie.**
   - `BunnyBackupAgent` registered with `android:backupAgent` **and `android:fullBackupOnly="true"`** —
     declaring an agent without it puts the app on the key/value path, which is not what ADR-0005 describes.
   - The agent **takes paths, not a `Context`** (ADR-0005). When the system starts the process *for* backup
     it binds the base `android.app.Application` rather than this app's subclass, so `AppContainer` is
     absent and a cast to `BunnyTrackerApplication` is a `ClassCastException` — and reaching for the
     container would in any case force the `lazy` that ADR-0007 makes the structural guard in front of a
     wipe. The failure ordering is what makes this worth building structurally: Auto Backup runs when the
     device is idle and charging, `bmgr backupnow` runs with the app on screen, so a container-dependent
     agent passes every test done by hand and fails only in production, silently.
   - So the file set, the quota admission and the marker are **functions over `File`**, in their own file
     with no Android dependency, and the agent is a thin shell that calls them. "Cannot reach the container"
     becomes a property of the types — the same move ADR-0007 made when it rejected a guard by discipline.
   - `onFullBackup` checkpoints the WAL into a consistent copy via 3c's shared helper and backs up **that
     copy**, never the live file with its sidecars — ADR-0005 names the alternative as a restore that comes
     back corrupt.
   - Unconditional: database, preferences, `avatars/`. Excluded: `photos/` (ADR-0005), and **`preserved/`**
     — ADR-0007 left that question open for this phase and the answer is no. Not on quota grounds, which do
     not hold at 1.0 where the whole set is under a megabyte, but because `preserved/` is the app's one
     **unbounded, never-pruned** directory, and Android rejects the *entire* over-quota dataset rather than
     trimming it. Admitting an unbounded set means one day losing the database and the avatars in order to
     have protected a duplicate. The owner's **share** tap is what makes a preserved copy safe.
   - Documents newest-first under a ceiling **below** ~25 MB. `documents/` is empty until Phase 5, so on
     device this admits nothing — which is exactly why the selection is a **pure function** over
     `(core bytes, documents newest-first, ceiling)` with a JVM test, rather than untested logic first
     exercised in a phase whose failure mode is silence.
   - The marker — last-backup instant plus excluded count — is a **plain file under `filesDir`**, written
     temp-then-rename, behind a `(File) -> Marker?` helper that the agent and Settings both call. Not
     DataStore: the agent cannot reach the app's instance without the container, and its writes are
     `suspend` inside blocking backup callbacks. ADR-0005's requirement is *outside the database*, which
     restore replaces; this satisfies it and stays readable from both sides.
   - Because the agent names its own file set, the marker is **never included**, so it cannot travel to
     another phone at all. `onRestoreFinished()` **clears it regardless**, for a second and different
     reason: after a restore the phone no longer holds the data the old marker vouched for. Two mechanisms
     failing differently — the exclusion is a static claim a later edit could break, the clear is a runtime
     guarantee at the exact event.
   - Settings gains the status line, with all three states **in words**: a date; **stale** past 14 days; and
     *"No automatic backup has been recorded on this phone"* with a button into system backup settings.
     A blank reads as a working net, which is ADR-0001's silence failure pointed at backup. The deep link is
     best-effort with a `resolveActivity` fallback to top-level settings — HyperOS moves that screen — and
     3e reuses it in first-run setup.
   - The one-time exclusion notification and the app's first notification channel are built here, but
     **cannot fire before Phase 5**, there being no documents to exclude. That is stated rather than
     glossed, and it is why 3e asks for no notification permission (ADR-0006).
   - The two template XML files resolve here. An agent that chooses its own file set makes
     `backup_rules.xml` and `data_extraction_rules.xml` dead, so the expected outcome is **deleting both
     along with their manifest attributes**, closing two of Phase 2's four standing lint warnings — but
     confirmed against a real backup run rather than against the documentation.
   - Driven with `adb shell bmgr backupnow app.bunny.tracker` and `bmgr restore`; if HyperOS will not drive
     `bmgr`, the fallback evidence is the marker file appearing under `run-as`, plus `dumpsys backup` and
     logcat around the callbacks. **If it cannot be observed at all, 1.0 still ships**: export and restore
     already satisfy ADR-0019, and the marker design means an agent that never ran renders as *"No automatic
     backup has been recorded on this phone"* — which is literally true. An unverifiable agent degrades into
     an honest app rather than a lying one, and Play Console vitals become how it is found out, which is one
     of the three reasons ADR-0009 chose Play.
5. **3e — First-run setup, the visibility flip, and the switcher mechanism.**
   - **Two steps at 1.0** (ADR-0006): add first bunny (skippable) → backup scope. Gated on a `setupComplete`
     preference — `AppPreferences`' **third** key — alongside the chosen backup scope as its **fourth**,
     which becomes 3c's export default and stays editable in Settings.
   - The reminders step is **not built here**. It ships with 1.1, with the reminders: Android allows two
     denials before the permission is refused permanently, 1.0 has nothing that posts a notification, and an
     opt-in that cannot demonstrate anything is the most likely to be dismissed — which is the failure
     ADR-0006 exists to prevent, arrived at from the other direction. ADR-0006's point-of-use ask becomes
     the *first* ask rather than the second.
   - The backup step **asks whether system backup is switched on**, using the deep link 3d already added.
     The app cannot detect it (ADR-0005), and this is the one moment the owner is already thinking about it.
   - **The visibility flip** (ADR-0015, ADR-0019) — the one-value change Phase 1 defined the enum for, not
     an introduction. `CARE` → `Hidden`; every other tab stays `Live`; More's Photos row went live at 3b,
     Documents and Support stay `ComingSoon`. The bottom bar renders from the non-`Hidden` entries, and a
     `Hidden` key arriving on a **restored back stack** — a Nav3 stack saved by a build where that tab was
     live — resolves to Home rather than to a blank destination.
   - Checked here too: `StubScreen` has **no remaining top-level caller**. If it does, either that screen is
     real or its tab is hidden; there is no third answer before a public 1.0.
   - **The language switcher's mechanism**, with English alone in the list (ADR-0013): `locales_config.xml`,
     the switcher row in Settings, and whatever AppCompat's pre-13 backport turns out to demand. At
     `minSdk` 26 most of the supported range takes that backport rather than the platform API, and if it
     wants `MainActivity` rebuilt as an `AppCompatActivity` with AppCompat theming under a pure Compose and
     Material 3 app, that is an activity-and-theme change rather than a Settings row. Shipping a
     one-language switcher is not the goal; finding out what it costs, a checkpoint before the translation
     lands, is.
6. **3f — 1.0: Polish, and the release.**
   - `values-pl/strings.xml` — one new file, no code changes, which is what four phases of "no hardcoded
     strings" bought. **250 strings and 10 plurals** stand today, before this phase's gallery, backup,
     restore and setup copy; it lands last because that is when the strings stop moving, and translating
     churn twice is the only way to make it more expensive.
   - Polish's **four plural categories** against English's two is where the `<plurals>` discipline finally
     becomes falsifiable: the delete ceremony's two buckets, the shared-observation participant counts and
     the excluded-documents line all get read in both languages at 1, 2 and 5.
   - Dates, numbers and weights already format through the platform, so `2,45 kg` is a **check rather than
     work** — the two places to look are `WeightFormat` and the chart's axis labels.
   - Release: `lint` clean, `test`, `connectedAndroidTest`, and the **schema-4 JSON committed and
     git-tagged** (ADR-0007) — the first schema version that is load-bearing, and the one every later
     migration is written from.
   - Then the signed build up the pipeline 3a already proved. If the closed-testing requirement that has
     been running since 3a is not yet met, 1.0 waits on the internal track until it is — which is exactly
     why registration was 3a's first line rather than 3f's.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 3b,
the only one that adds instrumented tests, and again at the gate; `lint` at the gate. This is the phase
where lint must reach **zero project-code warnings that are not a stated standing decision** — two of
Phase 2's four are 3d's to close.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **3a**: ADR-0009,
0019, 0023. **3b**: ADR-0020, 0015, 0004, 0007, 0023. **3c**: ADR-0005, 0023, 0020. **3d**: ADR-0005, 0001,
0007. **3e**: ADR-0006, 0015, 0019, 0013. **3f**: ADR-0013, 0009, 0007.

**Gate:**

- Export at **each** of the three scopes, clear app data, and restore each one: what the scope promised is
  present, and what it excluded degrades gracefully — placeholders in the grid, the pager, the switcher and
  Home's card, never a crash.
- An **Essential** restore onto a phone that still holds its photo files **keeps those photos**, rather than
  turning the gallery into placeholders.
- A backup at a **newer** schema version than the build is refused with both versions named, and the
  database on the phone is untouched.
- A restore leaves a restorable Essential export of the replaced state in `preserved/`, listed in Settings
  as what it is and distinguishable from a wipe copy, and **restoring it undoes the restore**.
- A device that has never run Auto Backup **says so in words**, with a button into system backup settings;
  a marker older than 14 days reads as **stale** rather than as a bare date.
- A restore does not carry the source phone's backup timestamp onto the target.
- A backup taken with a deliberately large photo gallery still **succeeds**: photos are out of the set, and
  the database and avatars are in — the whole reason ADR-0005 excludes them. If `bmgr` cannot be driven on
  the Xiaomi, this is recorded as **not observed** rather than as passed.
- Deleting a bunny counts its photos in the destroyed bucket, with correct pluralisation, and removes the
  files as well as the rows.
- A bulk import of photos spanning years lands in **capture order**, not import order.
- The gallery is read-only in the `Archived(id)` scope, and asks which bunny under "All bunnies".
- The **debug and release apps are both installed on the Xiaomi at once**, holding separate data, and
  `installDebug` still works with 1.0 on the phone.
- A release build **cannot** destructively wipe: asserted by test, since `run-as` does not reach a release
  build, and the release consent screen's no-forward-button variant is exercised by forcing it in a debug
  build.
- First run reaches **both** steps, the skippable one is genuinely skippable, and the backup scope chosen
  there is what the export sheet defaults to afterwards. **No notification permission is requested.**
- **No bottom-navigation tab opens onto a stub** — Care & Meds is absent, not "coming soon".
- Every screen in Polish with no English left behind, and the switcher changes the app's language without
  changing the phone's.
- Then the 1.0 release itself — signed build on the internal testing track, and installable from Play on
  the Xiaomi.

## Phase 4 — Care reminders and watch — ships as 1.1

Care reminders depend only on a bunny existing, and use the simpler mechanism. Building them first
establishes the notification channel, permission flow, reboot rescheduling and Xiaomi battery-exemption
prompt on easy ground, so dose reminders later add only the exact-alarm path.

- Care reminders on WorkManager, rescheduled after reboot.
- Repeat handled as "complete → record the care event → schedule the next", not an OS periodic trigger.
  Completion can be **back-dated** (did the nail trim yesterday, log it today) on the same terms as Phase 2
  entry; the next occurrence is scheduled from the recorded completion, not from when it was ticked off.
- A care reminder is `{label, interval, optional type}` (ADR-0018): the closed `CareType` enum tags only
  the known kinds — presets nail trim (~6 weeks), vaccination (annual), weigh-in (weekly), which map to
  calendar RRULEs and icons — while a custom reminder is a free-text label plus an owner-chosen interval.
- Watch: opt-in per bunny and **time-boxed** — the owner sets a duration when starting it (default ~7 days)
  and it **auto-expires** with a prompt to extend or close, never silently persisting into wallpaper
  (ADR-0001). Only while active does the app chase for fresh observations: a **once-daily best-effort
  WorkManager notification** framed about the owner's checking, not the bunny's state, and **satisfied by
  logging any observation** for that bunny that day. A missed watch nag is low-stakes, so best-effort
  delivery is fine — it needs none of the exact-alarm treatment doses get.
- The trend flag and the Watch are **connected in both directions**, now that both exist. The flag carries
  a *Start a watch* action pre-filled with the default duration — **offered, never automatic**, because
  "worth a closer look" is already the flag's voice and a button acting on that sentence presumes less than
  the sentence does (ADR-0001). The auto-expiry prompt shows the **current trend**, since "is it still
  dropping" is exactly what the owner is being asked. And a bunny under an active watch is **excluded from
  "Log a healthy day" pre-selection**, with the reason stated — the one unreviewed write path must not
  sweep a separated, ill bunny into a shared tray fact (ADR-0008).
- Battery-optimisation exemption requested here, at the point something is first scheduled.
- Care reminders optionally hand off to the owner's calendar, one-way, no permission (ADR-0014).

**Gate:** a reminder set for +2 minutes fires while backgrounded and still fires after a reboot; a reminder
also fires after the phone has sat idle in Doze **overnight** (screen off, app unopened) on the real
Xiaomi — the +2-minute happy path is not sufficient evidence of reliability (ADR-0003); tapping
*Add to calendar* on an annual reminder opens the calendar app with the date and yearly repeat already
filled in; a short-duration watch stops nagging once it auto-expires; a trend flag offers to start a watch
and "Log a healthy day" refuses to cover a watched bunny. Then the 1.1 release.

## Phase 5 — Vet, medications, documents, dose reminders — ships as 1.2

- Vets directory; visits linked to a bunny and optionally a vet — a health record, with no cost field
  (ADR-0017). A weight recorded on a visit is stored as
  **one** weight entry tagged with its origin (`source = manual | visit`, plus the visit id) in the same
  transaction — never a second copy of the number, so the chart and the visit cannot drift apart. Adding
  `source`/`visitId` is a Phase-5 migration (every earlier weight is `manual`). Deleting a visit makes an
  explicit, stated choice about its origin-tagged weight: keep it as a standalone weighing, or remove it.
- Medication courses with a start, a **nullable end** (an open course is ongoing), a **free-text dose
  amount**, and an optional daily schedule of clock times. Due doses derived, not stored (ADR-0002). Doses
  recordable ad hoc, with or without a schedule.
- Dose reminders on exact alarms, default on per course and switchable off (ADR-0003), reusing the
  notification plumbing from Phase 4. **Wall-clock semantics** (ADR-0003): the next trigger is resolved
  fresh in the device's current zone each time, so DST and travel keep a dose at its intended time of day;
  `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` receivers reschedule pending alarms alongside
  `BOOT_COMPLETED`.
- Documents via the ML Kit scanner, attached to a bunny and optionally a visit; reorder, delete, view.

**Gate:** a two-page scanned document reopens after restart; a visit-recorded weight appears in the chart;
shortening a course removes its future due doses without touching recorded ones; a dose reminder fires at
its exact clock time after an **overnight Doze idle** on the real Xiaomi, and while battery-optimisation
exemption/autostart are unconfirmed it presents as **best-effort**, never as an armed alarm (ADR-0003).
Then the 1.2 release.

## Releasing — at the end of Phases 3, 4 and 5

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
