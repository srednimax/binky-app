# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [ ] **Phase 1** — Data layer, bunnies, photos
- [ ] **Phase 2** — Weight and observations
- [ ] **Phase 3** — Backup and first-run setup
- [ ] **Phase 4** — Care reminders and watch
- [ ] **Phase 5** — Vet, medications, documents, dose reminders
- [ ] **Phase 6** — Release

There is no intermediate public release: the app ships to end users only once every phase is complete.

## Phase 0 — Toolchain, project skeleton, docs ✅

JDK 21, Android SDK under `~/Android/Sdk`, `ANDROID_HOME` in `~/.zshrc`, Xiaomi device over USB.
Compose project scaffolded with `android create` (AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Navigation 3),
package `app.bunny.tracker`.

**Gate met:** `assembleDebug`, `test`, `lint`, and `connectedAndroidTest` all pass; the app runs on the phone.

## Phase 1 — Data layer, bunnies, photos

- Room entities, DAOs, database, type converters, `AppContainer`, repositories.
- `MediaFiles.kt` — the single path for persisting images (downsample, re-encode, relative path out).
- Retire the template placeholders as real screens land: keep `Navigation.kt` / `NavigationKeys.kt` as the
  Nav3 wiring; delete `DataRepository.kt`, `MainScreen.kt`, `MainScreenViewModel.kt` and their two tests.
- Bunny list / add / edit / archive / delete, per ADR-0004. Avatar from camera or album.
- Per-bunny photo gallery: lazy grid, full-screen pager, captions.
- Missing-media placeholder, required by ADR-0005.

**Gate:** two bunnies with avatars survive a restart; deleting a bunny with records requires two
confirmations and removes its media; a deliberately broken image path renders as a placeholder.

## Phase 2 — Weight and observations

- Weight entry and chart. The chart plots **real timestamps, not list index**.
- Trend summary: change since last entry and over 30 days, flagging a drop past a threshold. This is the
  app's **single load-bearing safety signal** — the one thing that fires without the owner pre-diagnosing
  (CONTEXT.md) — so its threshold math, gram-delta display and time-correct chart get the most careful
  unit tests in the project.
- Observation entry (ADR-0001): every field optional — droppings, appetite, mood, activity, water,
  cecotropes, symptoms, note. Timeline grouped by day for display only.
- Warnings derive from recorded observations, never from silence.

**Gate:** unit tests for trend math pass; the chart is time-correct with deliberately uneven dates; an
empty database produces no warnings.

## Phase 3 — Backup and first-run setup

Moved ahead of vet/meds: by the end of Phase 2 the app holds irreplaceable data with no way off the device.

- Auto Backup covers database, preferences, avatars and scanned documents (the evidential core); the photo
  gallery excluded with an honest size guard; WAL checkpointed or journal files excluded (ADR-0005).
- Manual export at the three scopes — Essential / Records / Everything — via the share sheet.
- Restore, stating honestly what the file contains.
- First-run setup: add first bunny (skippable) → backup scope → reminders opt-in (skippable), per ADR-0006.
- Then attempt the remembered-folder destination and **verify on the real device** whether Google Drive's
  provider accepts writes. Still the plan's biggest unverified assumption — but with the evidential core
  now in Auto Backup (ADR-0005), it gates only the sentimental photo gallery, not vet evidence, so its
  failure is survivable.

**Gate:** export at each scope, clear app data, restore, and confirm what should be present is present and
what was excluded degrades gracefully.

## Phase 4 — Care reminders and watch

Care reminders depend only on a bunny existing, and use the simpler mechanism. Building them first
establishes the notification channel, permission flow, reboot rescheduling and Xiaomi battery-exemption
prompt on easy ground, so dose reminders later add only the exact-alarm path.

- Care reminders on WorkManager, rescheduled after reboot.
- Repeat handled as "complete → record the care event → schedule the next", not an OS periodic trigger.
- Presets: nail trim (~6 weeks), vaccination (annual), weigh-in (weekly).
- Watch: opt-in per bunny; only while active does the app chase for fresh observations (ADR-0001).
- Battery-optimisation exemption requested here, at the point something is first scheduled.
- Care reminders optionally hand off to the owner's calendar, one-way, no permission (ADR-0014).

**Gate:** a reminder set for +2 minutes fires while backgrounded and still fires after a reboot; a reminder
also fires after the phone has sat idle in Doze **overnight** (screen off, app unopened) on the real
Xiaomi — the +2-minute happy path is not sufficient evidence of reliability (ADR-0003); tapping
*Add to calendar* on an annual reminder opens the calendar app with the date and yearly repeat already
filled in.

## Phase 5 — Vet, medications, documents, dose reminders

- Vets directory; visits linked to a bunny and optionally a vet. A weight recorded on a visit is stored as
  **one** weight entry tagged with its origin (`source = manual | visit`, plus the visit id) in the same
  transaction — never a second copy of the number, so the chart and the visit cannot drift apart. Adding
  `source`/`visitId` is a Phase-5 migration (every earlier weight is `manual`). Deleting a visit makes an
  explicit, stated choice about its origin-tagged weight: keep it as a standalone weighing, or remove it.
- Medication courses with start/end and an optional daily schedule of clock times. Due doses derived, not
  stored (ADR-0002). Doses recordable ad hoc, with or without a schedule.
- Dose reminders on exact alarms, default on per course and switchable off (ADR-0003), reusing the
  notification plumbing from Phase 4.
- Documents via the ML Kit scanner, attached to a bunny and optionally a visit; reorder, delete, view.

**Gate:** a two-page scanned document reopens after restart; a visit-recorded weight appears in the chart;
shortening a course removes its future due doses without touching recorded ones; a dose reminder fires at
its exact clock time after an **overnight Doze idle** on the real Xiaomi, and while battery-optimisation
exemption/autostart are unconfirmed it presents as **best-effort**, never as an armed alarm (ADR-0003).

## Phase 6 — Release

Signed release APK, keystore out of git, signing config from `local.properties`.

## Verification

- Per phase: `assembleDebug installDebug` on the phone and exercise the new screens; `lint` clean.
- **JVM unit tests** for logic that is easy to get subtly wrong: trend math, derived dose schedules,
  reminder next-occurrence arithmetic including DST boundaries, backup zip round-trip.
- **Instrumented Room tests** against an in-memory database: DAO queries, cascade deletes, migrations.
  Exported schema JSONs are committed so migrations are reviewable. Note: split-APK installs prompt for
  confirmation on the Xiaomi device.
