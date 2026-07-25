# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [ ] **Phase 1** — Data layer, bunnies, avatars
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

## Phase 1 — Data layer, bunnies, avatars

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
  **interval-independent**: flag whenever `current ≤ baseline − max(~5% of baseline, gram noise-floor)`,
  regardless of the gap between weigh-ins — an acute drop *after a long gap* is the most dangerous pattern
  and must never be dampened into silence. The **baseline is the median of the last 3 *prior* weighings**
  (fewer if fewer exist, but **at least 2**, and always **excluding the current reading**, so a real drop
  can't dilute its own signal); the flag **cannot fire until ≥2 prior weighings exist**. The small **gram
  noise-floor** — `max(20 g, 2% of baseline)`, proportional rather than flat — stops day-to-day gut/bladder
  fluctuation tripping it. The delta is displayed in **grams**
  (house rule) and framed "down [X] g since [date] — worth a closer look," **never a diagnosis** (ADR-0001,
  no medical advice). The interval is used **only as framing** ("much of that may be recent — weigh again in
  a day or two"), **never to withhold the flag**. The constants are **fixed now, not left pending vet
  input** (ADR-0001): trigger **5% of baseline**, noise floor **`max(20 g, 2% of baseline)`**. The floor is
  proportional because the app spans a 1.1 kg Netherland dwarf to a 6.5 kg Flemish giant — a 6× range over
  which a flat gram floor would consume most of the trigger at one end and mean nothing at the other. Vet
  input is later tuning, a one-line change; the *shape* (level trigger, baseline-relative, noise-floored,
  interval-independent) is fixed.
- The flag surfaces **at the point of entry** — the moment a just-logged weight trips the threshold, shown
  in the entry flow — **and persists on Home / the weight screen**. It **auto-clears** when the latest
  weigh-in no longer trips the trigger against the *current* trailing baseline — covering both a real regain
  and a **stabilized-low** bunny whose baseline has caught up, because the signal is about a *drop*, not
  absolute thinness, and a flag that never clears becomes wallpaper (the ADR-0001 auto-expiry logic).
  **Manual acknowledge** stores the weight it was acknowledged at; the watermark is **episode-scoped** —
  discarded the instant the trigger goes false, so a since-recovered episode can never silence a new drop —
  and a later reading re-raises only when it falls **below the acknowledged weight by more than the
  noise-floor**. The flag is **derived on read**, never stored, so editing a fat-fingered timestamp
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
  is discarded when the weight it was taken against is edited or deleted (ADR-0001), or it stands against a
  reading that no longer exists.
- The chart carries a **range selector — 30 days / 90 days / 1 year / All, defaulting to 90 days**. An
  all-time axis compresses the two- or three-week drop the app exists to surface into a couple of percent
  of chart width — the same signal loss the gram/kilogram house rule exists to prevent, in geometry rather
  than arithmetic — and a juvenile growth curve from 900 g to 2.4 kg sets a y-axis that flattens every adult
  fluctuation afterwards. Range is **display only**: the flag always reads the last three prior weighings
  regardless of what is on screen, so the two cannot drift.
- Warnings derive from recorded observations, never from silence.
- The **blocking wipe screen** lands here (ADR-0007): from Phase 2 the database holds a weight series that
  cannot be retyped, so startup reads the schema version before Room opens it and asks before destroying
  anything. The preserve half already exists from Phase 1.
- The delete confirmation's **record counts become real** here, since this is the phase that creates records
  to count — two buckets, sole-owned versus shared-participation (ADR-0004, ADR-0008).

**Gate:** unit tests for trend math pass (interval-independent level trigger, trailing baseline of the 3
prior weighings excluding the current one, the ≥2-prior firing gate, noise-floor, gram-delta, and the
auto-clear/acknowledge/re-raise transitions — including that a long gap before an acute drop still fires);
the chart is time-correct with deliberately uneven and back-dated dates; a future-dated weight is rejected;
an untouched droppings field records "not checked", not "normal"; "Log a healthy day" records the
glance-level fields, leaves the graded ones "not checked", and names the bunnies it covered; correcting a
mistyped weight clears the flag it caused and restores the baseline; deleting a duplicate weighing does the
same; an empty database produces no warnings.

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
