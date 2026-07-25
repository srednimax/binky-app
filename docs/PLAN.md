# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [x] **Phase 1** — Data layer, bunnies, avatars
- [ ] **Phase 2** — Weight and observations
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

1. **2a — Weight data layer, and the consent half of the wipe guard.**
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
2. **2b — Trend math.** Pure JVM, no Room and no Android — `deleteConfirmationFor` is the precedent: a
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
3. **2c — Weight entry, history, the flag surfaced, and Settings.**
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
4. **2d — The chart.** Vico enters `libs.versions.toml` here and nowhere earlier. Real `recordedAt` on the
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
5. **2e — Observation data layer.** Schema → **3**.
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
6. **2f — Observation UI, the "+", and the healthy day.**
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
   - The **observation half of the sample-data action**: the two bunnies it needs, a shared observation across
     them, symptom links. Re-running it after 2e's wipe regenerates 2c's weight fixture identically, which is
     what makes the 2d and 2f chart reviews like-for-like.
   - In the `Archived(id)` scope the timeline renders read-only, with no "+", no healthy day and no per-row
     edit or delete (ADR-0004).
   - Observations stops being a stub, and Home's card completes its growth into ADR-0015's vitals card: last
     weight, last observation, the flag.

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
- First-run setup: add first bunny (skippable) → backup scope → reminders opt-in (skippable), per ADR-0006.
  The backup step also **asks whether system backup is switched on**, with a deep link into Android's
  settings — the app cannot detect it, and this is the one moment the owner is already thinking about it.
- Top-level destinations get their **visibility state** (`Hidden` / `ComingSoon` / `Live`) set for real
  before this ships — the enum was defined in Phase 1, so this is the one-value flip, not an introduction —
  since 1.0 is the first build real users see: Care & Meds is hidden rather than opening onto a stub, while
  unbuilt rows inside More may read "coming soon" (ADR-0019, ADR-0015).
- Then attempt the remembered-folder destination and **verify on the real device** whether Google Drive's
  provider accepts writes. Still the plan's biggest unverified assumption — but with the evidential core
  now in Auto Backup (ADR-0005), it gates only the sentimental photo gallery, not vet evidence, so its
  failure is survivable.

**Gate:** export at each scope, clear app data, restore, and confirm what should be present is present and
what was excluded degrades gracefully; a device that has never run Auto Backup says so in words rather than
showing a blank; a restore does not carry the source phone's backup timestamp onto the target; no
bottom-navigation tab opens onto a stub. Then the 1.0 release itself — signed build on the internal testing
track, and installable from Play on the Xiaomi.

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
