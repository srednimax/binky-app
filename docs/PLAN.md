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

- Weight entry and chart. Entry defaults to now but the date/time is **editable, and back-dating is
  allowed** (weigh in the morning, log in the evening), while **future timestamps are rejected**. Existing
  entries' timestamps are editable too — a fat-fingered date otherwise distorts the trend permanently. The
  chart plots **real timestamps, not list index**.
- Trend flag — the app's **single load-bearing safety signal**, the one thing that fires without the owner
  pre-diagnosing (CONTEXT.md), so it gets the most careful unit tests in the project. The trigger is
  **interval-independent**: flag whenever `current ≤ baseline − max(5% of baseline, gram noise-floor)`,
  regardless of the gap between weigh-ins — an acute drop *after a long gap* is the most dangerous pattern
  and must never be dampened into silence.
- **In practice that trigger is 5% of baseline, and it is worth being honest about why.** `2%` is always
  less than `5%`, so the inner `max` can only ever resolve to the **20 g absolute**, and 20 g exceeds 5% of
  baseline only below **400 g** — a four-week-old kit. Across the whole 1.1 kg – 6.5 kg range the app exists
  to serve, the floor never binds here. It stays in the formula as a deliberate **juvenile guard** (on a
  300 g kit, 5% is 15 g, inside real scale noise), and a unit test pins that case so the `max` is not
  "simplified" away later. The floor's genuinely load-bearing role is the **re-raise bar** below, where
  ADR-0001 wants a tighter threshold than 5%.
- The **baseline is the median of the 3 *prior* weighings**, always **excluding the current reading**, so a
  real drop can't dilute its own signal; the flag **cannot fire until ≥2 prior weighings exist**. At exactly
  two priors the baseline is the **higher of the two, not their median** — because the median of an even set
  is the mean of the middle pair, and a 2-element mean is the most outlier-sensitive estimator in the
  scheme, in the very window where the flag first switches on. It fails *silent*: priors of `2500, 250`
  average to 1375 g, putting a healthy bunny permanently "above baseline" and suppressing every later drop.
  Higher-of-two matches the mean on a fat-fingered *high* prior (both fire) and fixes the low one, so it
  strictly dominates. The failure it admits — a false alarm from a typo'd high prior — is self-announcing,
  since the flag shows grams and dates and the value is editable.
- The delta is displayed in **grams**
  (house rule) and framed "down [X] g since [date] — worth a closer look," **never a diagnosis** (ADR-0001,
  no medical advice). The interval is used **only as framing** ("much of that may be recent — weigh again in
  a day or two"), **never to withhold the flag**. The constants are **fixed now, not left pending vet
  input** (ADR-0001): trigger **5% of baseline**, noise floor **`max(20 g, 2% of baseline)`**. The floor is
  proportional because the app spans a 1.1 kg Netherland dwarf to a 6.5 kg Flemish giant — a 6× range over
  which a flat gram floor would consume most of the re-raise bar at one end and mean nothing at the other.
  Vet input is later tuning, a one-line change; the *shape* (level trigger, baseline-relative, noise-floored,
  interval-independent) is fixed.
- The flag surfaces **at the point of entry** — the moment a just-logged weight trips the threshold, shown
  in the entry flow — **and persists on Home / the weight screen**. It **auto-clears** when the latest
  weigh-in no longer trips the trigger against the *current* trailing baseline — covering both a real regain
  and a **stabilized-low** bunny whose baseline has caught up, because the signal is about a *drop*, not
  absolute thinness, and a flag that never clears becomes wallpaper (the ADR-0001 auto-expiry logic).
  **Manual acknowledge** stores the weight it was acknowledged at; the watermark is **episode-scoped** —
  discarded the instant the trigger goes false, so a since-recovered episode can never silence a new drop —
  and a later reading re-raises only when it falls **below the acknowledged weight by more than the
  noise-floor**. It is **also discarded by any edit or deletion of any of that bunny's weights** (not by
  inserts, which the trigger and the re-raise bar already handle). That is wider than ADR-0001's original
  "the weight it was taken against", and deliberately so: editing a *baseline* weight can deepen the real
  drop while leaving the current reading — and therefore the watermark comparison — untouched, so the flag
  would stay silent on a drop that just got worse. The wider rule is also the simpler one to implement,
  needing no "was this the acknowledged row?" test. The flag is **derived on read**, never stored, so editing a fat-fingered timestamp
  self-heals and a back-dated weight recomputes the *current* flag but never resurrects one for a past,
  since-recovered moment; a **vet-directed diet** is an accepted limitation the flag names in its own copy
  rather than suppressing (all ADR-0001). **No push
  notification:** a drop can only appear when a weight is logged and the owner is present at that moment, so
  a push would be redundant and would drift toward sounding diagnostic.
- Observation entry (ADR-0001): every field optional — droppings, appetite, mood, activity, water,
  cecotropes, symptoms, note. The closed vocabularies (each also carrying *not checked*): droppings amount
  `none·few·normal`, size `small·normal·large`, form `normal·misshapen·soft·watery·mucus`; cecotropes
  `eaten·left uneaten`; appetite `none·reduced·normal`; mood `bright·subdued·distressed`; activity
  `normal·reduced·very low`; water `none·reduced·normal·increased` — water the only field that records
  "more than usual", since only there is it a signal. Symptoms attach as a binary tick, severity carried
  by the symptom's name (ADR-0010). Back-dating supported on the same terms as weight. Droppings **amount
  defaults to "not checked," never a silent "normal"** (CONTEXT.md): auto-filling the earliest health
  signal with an unverified "fine" is a false reassurance the app must not manufacture. The one-tap healthy
  day is preserved by an explicit **"Log a healthy day"** shortcut that *affirmatively* records the
  **glance-level** facts — droppings normal, cecotropes eaten, **no symptoms** — and leaves the *graded*
  fields (appetite, mood, activity, water) as **"not checked"**, since auto-normalising those would
  manufacture the same unverified "fine" (ADR-0001). On a bunny that lives with others it writes a
  **shared observation** across the fluffle (tray-level facts propagated, per-bunny "no symptoms"), never a
  solo row that falsely attributes the shared tray (ADR-0008).
  Timeline grouped by day for display only. The one-tap shortcut **names who it covered**, as a snackbar
  with undo — the only write path in the app that commits participants without review, so the review
  happens immediately afterwards instead (ADR-0008).
- **Every weight and observation is individually editable and deletable** — the *value* as well as the
  timestamp — behind a single confirmation, since ADR-0004's two-stage ceremony is calibrated to destroying
  a bunny's whole history, not one mistyped number. A fat-fingered `250` for `2500` trips the flag hard and
  immediately, and then sits in the trailing baseline suppressing a real drop for the next three weigh-ins;
  correcting the date cannot fix that, so correction has to reach the number. The acknowledgment watermark
  is discarded by any weight edit or deletion (ADR-0001, as widened above), or it stands against a reading
  that no longer exists — or against a baseline that has since moved beneath it.
- The chart carries a **range selector — 30 days / 90 days / 1 year / All, defaulting to 90 days**. An
  all-time axis compresses the two- or three-week drop the app exists to surface into a couple of percent
  of chart width — the same signal loss the gram/kilogram house rule exists to prevent, in geometry rather
  than arithmetic — and a juvenile growth curve from 900 g to 2.4 kg sets a y-axis that flattens every adult
  fluctuation afterwards. Range is **display only**: the flag always reads the full series regardless of
  what is on screen, so the two cannot drift. The selector therefore creates a **third empty state** —
  weighings exist but none fall in the selected range — which must say so and name the last weighing's date,
  never "no weight recorded yet", which would be the app claiming ignorance of data it holds. It is reached
  on an ordinary path: weigh monthly, skip a quiet winter, open to a blank 90-day window. The honest
  consequence is a **trend flag rendered above an empty chart**; that is correct and must not be "fixed" by
  making the flag respect the range.
- Warnings derive from recorded observations, never from silence.
- The **blocking wipe screen** lands here (ADR-0007): from Phase 2 the database holds a weight series that
  cannot be retyped, so startup reads the schema version before Room opens it and asks before destroying
  anything. The preserve half already exists from Phase 1.
- The delete confirmation's **record counts become real** here, since this is the phase that creates records
  to count — two buckets, sole-owned versus shared-participation (ADR-0004, ADR-0008). The buckets are
  counted by **survivorship, not provenance**: a shared observation where this bunny is the *last remaining
  participant* is destroyed outright, so it belongs in the sole-owned bucket. Counting it as "shared" would
  make the second dialog reassure the owner that the records survive for the other bunnies at the exact
  moment the loss is total — and ADR-0004's whole justification for a second dialog is that it states what
  is actually destroyed.

### Checkpoints

Six rather than Phase 1's four, because this is roughly twice the phase: weight and observations each split
into a data layer proven by tests and a UI verified by hand, and the two pieces that are easy to entangle
with everything else — the trend math and the chart — are kept separate so they can be reviewed for what
they are. Dependencies still run one way, and the Xiaomi's split-APK confirmation prompt still makes
`connectedAndroidTest` a boundary run rather than a per-commit one.

**Two schema bumps, two wipes** — version 2 at 2a, version 3 at 2e. Both are free under ADR-0007 and both
are a chance to exercise the consent screen on a real device. The consequence stands for the whole phase:
until Phase 3 the phone's database is disposable, so weights worth keeping are written down outside the app.

1. **2a — Weight data layer, and the consent half of the wipe guard.**
   - `WeightEntity` — `id`, `bunnyId` FK `CASCADE` indexed, `grams: Int` (house rule — never a float),
     `recordedAt: Instant` (the moment on the scale, back-datable), `createdAt: Instant`. Indexed on
     `(bunnyId, recordedAt)`, because every query this app makes is one bunny's series in time order. No
     `source` / `visitId`: already deferred to a Phase-5 migration.
   - `TrendAcknowledgmentEntity` — the flag's **only** persisted piece (ADR-0001): `bunnyId` as primary key
     (at most one live episode per bunny), `weightId` FK `ON DELETE CASCADE`, `grams`, `acknowledgedAt`.
     A table rather than columns on `bunnies` so that "discarded when the weight it was taken against is
     deleted" is a **database constraint rather than a rule someone has to remember**. The semantics that
     govern the row are 2b's; only the shape lands here.
   - `WeightDao` / `WeightRepository`: the series as a `Flow`, insert / update / delete. **No "the *n*
     weighings prior to an instant" query** — windowing belongs to 2b's pure function and must be defined in
     exactly one place. The DAO returns the bunny's series in a **total order** (`recordedAt` desc, then
     `createdAt` desc, then `id`), because `recordedAt` alone is not one: a minute-granularity picker, two
     entries in a session and the 2f seeder all produce ties, and without a stated rule the baseline depends
     silently on SQLite's row order. The full series is loaded anyway — 2d feeds the trend function the
     unfiltered series, and five years of weekly weighings is 260 rows of `(String, Int, Long)`.
   - `WeightRepository.update` and `.delete` **discard that bunny's acknowledgment row**, per the widened
     ADR-0001 rule above. One line each, no "was this the acknowledged weight?" test. The FK below stops
     being the mechanism and becomes a backstop.
   - Schema → **2**, and **the consent screen lands in the same commit as the bump**. This is the first wipe
     ADR-0007's consent half exists for; shipping the bump a commit earlier would spend it on the phone that
     matters.
   - The screen is **honest about having no alternative**: Phase 1's preserve half has already taken the
     copy, so it states what is about to be destroyed, where the copy is
     (`files/preserved/bunny-<timestamp>.db`), how to pull it off the phone, and offers one forward button.
     What ADR-0007 forbids is the *silent* wipe, not the unavoidable one.
   - It has to block **before Room opens the file**, which today it does not: `AppContainer.selectedBunny`
     is `SharingStarted.Eagerly` over `activeBunnies`, so merely constructing the container runs a query.
     `selectedBunny` becomes `WhileSubscribed(5_000)` and `AppContainer` exposes the pending wipe for
     `MainActivity` to gate the whole UI on.
   - **Not `database by lazy`** — that would be cargo. Room's `build()` does not open the file (which is
     precisely why 2e's `onCreate` seeding callback works: it fires on first *access*), and the lazy would
     be forced immediately anyway, because `fluffleRepository` and `bunnyRepository` are eager `val`s taking
     `database` as a constructor argument. The guard is therefore **indirect and must be named as such**:
     nothing collects, so no query runs, so the file stays shut. An instrumented test pins it — constructing
     `AppContainer` over a stale-version file leaves that file **byte-identical**.
   - `preserveBeforeWipe` names the copy from **`databaseFile.lastModified()`**, not `Instant.now()`
     (which stays as the injected default for tests). Phase 1 was idempotent by accident: preserve ran, Room
     wiped immediately, and the next launch saw a matching version. A *blocking* screen removes that, so
     every relaunch before consent would mint another full copy plus `-wal`/`-shm` — and CLAUDE.md notes the
     Xiaomi kills backgrounded apps aggressively, which is exactly what a hesitating owner triggers. A
     deterministic name makes the re-copy overwrite itself, and dates the *data* rather than the moment of
     panic.
   - Weight display unit becomes `AppPreferences`' **second key** — kg by default, grams the alternative.
     Entry is in grams either way; only display moves. Its toggle lands in 2c, not here: a preference with
     no setter is a constant with a DataStore round-trip, and it would leave the grams branch of every
     display site unexecuted for a whole phase.
   - `BunnyDao.recordCounts` gets its first real SQL (weights are sole-owned), which makes 1d's structurally
     built **two-stage delete ceremony reachable for the first time**. It reaches its final form in 2e.
   - Tests, instrumented: weights cascade with their bunny; an acknowledgment row disappears with the weight
     it names; a stale-version database file survives container construction untouched. The out-of-order
     windowing test moves to **2b**, as JVM arithmetic — faster, and free of the Xiaomi's split-APK prompt.
2. **2b — Trend math.** Pure JVM, no Room and no Android — `deleteConfirmationFor` is the precedent: a
   decision function in `data/` whose test reads as a table of cases.
   - Input is the bunny's **whole series** as a plain list of `(id, grams, recordedAt)` plus the current
     acknowledgment, output a sealed result. Deliberately **not** Room types, so the tests stay arithmetic.
   - **This function owns the windowing**, not the DAO: it sorts, takes the latest reading as *current*, and
     takes the priors beneath it. That is the only reason 2b's back-dating cases mean anything — if SQL had
     already chosen the three priors, the project's heaviest tests would be measuring a stub.
   - Both constants live in this one file with ADR-0001's reasoning in comments: trigger **5 % of baseline**,
     noise floor **`max(20 g, 2 % of baseline)`** — with a comment recording that the floor cannot bind in
     the trigger above a 400 g baseline, so its real job is the re-raise bar.
   - The rules, restated as code: baseline is the **median** of the 3 prior weighings (never the mean — one
     fat-fingered outlier must not drag it) but the **higher of the two** when only two priors exist, since
     a 2-element median *is* a mean; **excluding** the current reading; **≥ 2 priors** before anything can
     fire; level trigger, **interval-independent**; the acknowledgment is discarded the instant the raw
     trigger goes false, and re-raises only below the watermark by more than the floor.
   - The project's heaviest unit tests, as a case table: a long gap before an acute drop still fires; one
     prior weighing never fires and two do; **at exactly two priors a fat-fingered low prior does not
     suppress** (`2500, 250` must not yield a 1375 g baseline); the floor behaves at both ends of the
     1.1 kg – 6.5 kg range **and binds in the trigger only on a ~300 g kit**, so the `max` cannot be
     simplified away; a stabilized-low bunny auto-clears as the baseline catches up; acknowledge → further
     slide re-raises, acknowledge → wobble within the floor stays quiet; a trigger going false discards the
     watermark so the next episode fires from scratch; a back-dated insert into the middle of history changes
     the current flag and never resurrects a past one; ties in `recordedAt` resolve by the stated total
     order; rows arriving out of order window correctly (moved here from 2a).
3. **2c — Weight entry, history, the flag surfaced, and Settings.**
   - A **`WeightEntry(bunnyId, weightId: String? = null)` nav key** — null adds, non-null edits, mirroring
     `BunnyEditor`. This **closes a Phase-1 omission rather than adding scope**: ADR-0015 names weight entry
     as one of the app's two shallow detail screens, and `NavigationKeys.kt` promises every route exists from
     Phase 1 "even where the screen behind them is still a stub" — this one didn't. The global "+" stays
     **observation-only** (ADR-0015) and is never the way in.
   - Entry defaulting to now, date/time editable, back-dating allowed, **future rejected with the reason
     stated** rather than silently clamped.
   - The per-bunny history list, every row editable and deletable — **value as well as timestamp** — behind
     **one** confirmation.
   - Every write path re-evaluates the flag; the watermark is maintained by 2a's repository rule (any edit or
     delete discards it) plus discard-on-trigger-false, with the FK as backstop.
   - One flag composable rendered in three places: at the point of entry the moment a logged weight trips it,
     on the weight screen, and on Home's card. Grams, dated, "worth a closer look", the long-gap framing when
     the gap warrants it, the vet-diet line, an acknowledge action — and **no notification**. Built with room
     for a **second action**, since Phase 4 adds *Start a watch* to this same composable.
   - Home under "All bunnies" is **one vitals card per active bunny**, so it is *N* series reads and *N*
     trend evaluations per emission, recomputed on any weight change. Stated, not optimised — at three
     rabbits it is free, and "derived on read" plus "a card each" is the pairing that stops being free
     quietly.
   - A minimal **Settings screen**, flipping More's `more_settings` row from "coming soon" to live — one row,
     the weight display unit. Same shape as `ArchivedBunnies`: a detail route off More. Settings has to exist
     before 1.0 regardless, since ADR-0013's language switcher needs the same screen.
   - **One weight formatter, in one place**, so kg-vs-grams and the "changes are always shown in grams"
     house rule are expressed once rather than re-derived at the axis, the row and the card.
   - Weight stops being a stub and still refuses "All bunnies". No Compose tests (ADR-0012, as in 1c); the
     logic beneath is already covered by 2b.
4. **2d — The chart.** Vico enters `libs.versions.toml` here and nowhere earlier. Real `recordedAt` on the
   x-axis; range selector 30 d / 90 d / 1 y / All defaulting to 90 d. **Three** empty states, not two: no
   weighings at all, a single point, and **weighings exist but none in range** — the third naming the last
   weighing's date and offering one tap to *All*, never claiming the app has no data. No auto-widening: a
   selector that silently overrides the owner's choice lies about its own state.
   **Range is display-only** — the trend function is fed the unfiltered series, never the chart's list, which
   is what keeps the two from drifting, and which means **the flag can render above an empty chart**. That
   composition is correct and gets verified by eye, because it is the one that looks like a bug. Its own
   checkpoint on purpose: a new charting dependency against
   Compose BOM 2026.03.01 either drops straight in or eats a day, and neither outcome should be tangled up in
   the review of the entry flow.
5. **2e — Observation data layer.** Schema → **3**.
   - `ObservationEntity`, one row per bunny (ADR-0008): `id`, `bunnyId` FK `CASCADE`, `groupId: String?`
     (non-null only when shared), `recordedAt`, `createdAt`, the tray-level fields (droppings amount / size /
     form, cecotropes) and the individual ones (appetite, mood, activity, water, note).
   - **Sharedness is `groupId IS NOT NULL`, never a count of rows sharing it.** The count would silently
     downgrade exactly the record ADR-0008 exists to protect — deleting one participant has to leave the
     survivors still reading "observed together" even when a single row is left — but `groupId` already
     survives that, and every other correction path besides. There is deliberately **no `observedTogether`
     column**: it would be a second spelling of the same fact, unable to do anything but drift out of step
     with the first. Converting a solo observation to shared mints a `groupId` and back-fills it onto the
     existing row, inside the transaction that is already there.
   - Every vocabulary column is a **nullable enum stored by name**, and `null` *is* "not checked" — no
     `NOT_CHECKED` entry, or absence gets two spellings and every query has to handle both.
   - `SymptomEntity` (ADR-0010): `id`, `key: String?` for built-ins — the stable identity, whose English
     label lives in `strings.xml` — `label: String?` for owner-added rows, `hiddenAt: Instant?`. Both label
     columns earn their place: built-in labels **must not** be stored, because ADR-0013 needs them
     translatable out of `strings.xml`, while owner labels must be. There is no `ownerCreated` flag —
     `key == null` already says it, and a second column could only drift. Seeded on create and **reconciled
     on open** with an `INSERT OR IGNORE` over the built-in set keyed on `key`: fifteen rows, idempotent by
     construction, and it keeps the list in code identical to the list in the database once wipes stop being
     free after Phase 3. Matching on `key` leaves a hidden symptom's `hiddenAt` untouched; built-ins are
     retired by hiding, never by deleting. `ObservationSymptomEntity` joins them on a composite key,
     `CASCADE` from the observation and **no cascade from the symptom** — hiding a symptom is not deleting
     it.
   - `ObservationRepository` owns the shared write as **one transaction**: one `groupId`, tray-level facts
     written identically onto every participant, individual fields blank. Editing a tray-level field is an
     `UPDATE … WHERE groupId = :groupId`; editing an individual one touches one row.
   - `recordCounts` reaches its final form, bucketed by **survivorship, not provenance**: shared means a
     grouped observation with `EXISTS` at least one row belonging to a *different* bunny; a grouped
     observation where this bunny is the last participant is **destroyed**, so it counts as sole-owned.
     Archived bunnies count as survivors — archive is not deletion and their rows persist. `deleteConfirmationFor`
     is untouched: either bucket being non-zero still yields `TWO_STAGE`, so only the numbers get honest.
     Note the two different questions this leaves — *"was this observed together?"* (history, immutable,
     `groupId`) and *"will anything be left of it?"* (present-tense, the `EXISTS`) — which deserve different
     predicates rather than one column doing both jobs badly.
   - Tests, instrumented: the shared write lands one `groupId` and identical tray facts on every
     participant; editing a tray fact moves every row and editing a mood moves one; deleting one participant
     leaves the rest marked observed-together; deleting a bunny cascades its observations and symptom links
     but no symptom; **the last surviving participant's observations count as sole-owned, and an archived
     housemate keeps them counted as shared**; the seed runs once, survives a wipe, and tops up on open
     without resurrecting a hidden symptom; a hidden symptom still resolves on an old observation. JVM: the
     healthy-day field set as a pure function, so 2f only wires it up.
6. **2f — Observation UI, the "+", and the healthy day.**
   - The global "+" FAB **finally renders** — Phase 1 settled its route and deliberately left it inert.
   - The full form: every field optional, droppings amount landing on **not checked**, participants
     pre-selected from the current fluffle's *active* members and editable, the symptom picker with
     add-your-own, note, back-dating and future-rejection on the same terms as weight.
   - Pre-selection is built as a **filter with a stated reason per exclusion**, even though Phase 2 excludes
     nobody — so Phase 4's watch exclusion is one predicate added, rather than the participant picker and the
     snackbar copy being reworked together under reminder-scheduling pressure.
   - The timeline grouped by day **for display only**, shared entries naming who they covered; under "All
     bunnies" it is the combined timeline.
   - Edit and delete per observation behind one confirmation, respecting the tray/individual split.
   - **"Log a healthy day"** — one tap, affirmative on the glance-level facts, "not checked" on the graded
     ones, and a snackbar naming who it covered with **Undo**. The Watch-based exclusion is Phase 4's and is
     not stubbed here.
   - A **flagged bunny is not excluded**, but the snackbar **names the flag** — *"Logged a healthy day for
     Bijou (weight flag) and Nugget"*. The flag is about **weight**; the healthy day records droppings,
     cecotropes and symptoms, and a bunny losing weight with entirely normal droppings is real and useful
     data. Excluding would add friction to the one-tap path over exactly the stretch that most wants daily
     observations. Phase 4's Watch is different in kind and its exclusion still stands: a Watch is the owner
     declaring concern about *observation* and exists to elicit deliberate ones, whereas letting an
     app-derived signal block the owner from recording what they saw would have the app presuming more than
     its own "worth a closer look" copy does. Naming it in the snackbar puts the fact in front of the owner
     while **Undo** is still on screen, which is ADR-0008's logic for why that snackbar exists at all.
   - A **`BuildConfig.DEBUG`-only sample-data action** in Settings, writing **through the repositories** so
     it cannot seed rows the app itself could not produce. It generates the fixture the gate describes — a
     year-long series with deliberately uneven and back-dated timestamps, a fat-fingered entry, a long gap
     before an acute drop, and a shared observation across two bunnies. Two wipes land in this phase and the
     gate needs that data three times; retyping a year of back-dated entries through a date picker is the
     kind of toil that gets skimmed, and a seeder makes the fixture *identical* each time, so a chart that
     looks wrong at 2d and again at 2f is being compared like with like. It pays for itself again at every
     schema bump in Phases 3–5, and never ships.
   - Observations stops being a stub, and Home's card completes its growth into ADR-0015's vitals card: last
     weight, last observation, the flag.

`spotlessApply`, `assembleDebug` and `test` at every checkpoint; `connectedAndroidTest` at the end of 2a and
2e, the two that add instrumented tests; `lint` at the gate.

Each checkpoint is meant to survive being picked up cold, so read its decisions first — **2a**: ADR-0007,
0004. **2b**: ADR-0001. **2c**: ADR-0001, 0004, 0012. **2d**: ADR-0001, 0012. **2e**: ADR-0008, 0010, 0004.
**2f**: ADR-0008, 0010, 0001, 0013.

**Gate:**

- Trend-math unit tests pass: interval-independent level trigger, trailing baseline of the 3 prior weighings
  excluding the current one, the ≥ 2-prior firing gate, the noise floor, the gram delta, and the
  auto-clear / acknowledge / re-raise transitions — **including that a long gap before an acute drop still
  fires**.
- At exactly two priors the baseline is the higher of the two: `2500, 250` does not yield a 1375 g baseline
  and does not silence a later drop.
- The noise floor binds in the trigger only below a ~400 g baseline; the kit case is covered, so the `max`
  cannot be dropped without a red test.
- Correcting a mistyped weight clears the flag it caused and restores the baseline; deleting a duplicate
  weighing does the same; either one also discards an acknowledgment taken against it. **Editing an
  unrelated weight discards it too** — including a baseline weight whose correction deepens the drop.
- Constructing `AppContainer` over a database file at a stale schema version leaves that file byte-identical,
  and relaunching before consenting does not add a second preserved copy.
- The chart is time-correct with deliberately uneven and back-dated dates, and switching range never changes
  whether the flag is showing. A range holding no weighings says so and names the last weighing's date rather
  than reporting no data, and the flag still renders above it.
- A future-dated weight is rejected, in both the weight and the observation forms.
- An untouched droppings field records "not checked", not "normal".
- "Log a healthy day" records the glance-level fields, leaves the graded ones "not checked", and names the
  bunnies it covered in a snackbar that can be undone.
- Deleting a bunny that has weights and shared observations shows **two** confirmations, with the two buckets
  counted separately and correct pluralisation at 1 and at 3; the shared observations survive for the other
  bunnies, still marked observed-together. Deleting the **last remaining participant** counts those
  observations as destroyed, not as surviving.
- The blocking wipe screen appears on a real schema bump, names the preserved file, and the file is there.
- An empty database produces no warnings.
- No user-facing string is hardcoded; counts use `<plurals>`, and the built-in symptom labels resolve
  through `strings.xml` rather than being stored.

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
