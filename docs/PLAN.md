# Roadmap

Sequence and status only. Decisions and their reasoning live in [`docs/adr/`](adr/); vocabulary lives in
[`CONTEXT.md`](../CONTEXT.md); commands, layout and house rules live in [`CLAUDE.md`](../CLAUDE.md).
The data model lives in the Room entities, so it cannot drift from the code.

## Status

- [x] **Phase 0** — Toolchain, project skeleton, docs
- [ ] **Phase 1** — Data layer, bunnies, avatars
- [ ] **Phase 2** — Weight and observations
- [ ] **Phase 3** — Backup, first-run setup, photo gallery
- [ ] **Phase 4** — Care reminders and watch
- [ ] **Phase 5** — Vet, medications, documents, dose reminders
- [ ] **Phase 6** — Release

There is no intermediate public release: the app ships to end users only once every phase is complete.

## Phase 0 — Toolchain, project skeleton, docs ✅

JDK 21, Android SDK under `~/Android/Sdk`, `ANDROID_HOME` in `~/.zshrc`, Xiaomi device over USB.
Compose project scaffolded with `android create` (AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Navigation 3),
package `app.bunny.tracker`.

**Gate met:** `assembleDebug`, `test`, `lint`, and `connectedAndroidTest` all pass; the app runs on the phone.

## Phase 1 — Data layer, bunnies, avatars

- Room entities, DAOs, database, type converters, `AppContainer`, repositories.
- `MediaFiles.kt` — the single path for persisting images (downsample, re-encode, relative path out).
  Avatars alone exercise the whole pipeline (write through the helper, relative split path, placeholder on
  a broken path); the sentimental photo gallery is deferred to Phase 3 (ADR-0015) so Phase 1 keeps the
  media machinery without the extra surface area.
- Navigation shell per ADR-0015: the bunny-first top-level destinations (Home / Weight / Observations /
  Care & Meds / More), the persistent bunny switcher and the global "+" observation entry are fixed now —
  as stubs where a screen doesn't exist yet — because ADR-0012 requires the structure decided before the
  first screen.
- Retire the template placeholders as real screens land: keep `Navigation.kt` / `NavigationKeys.kt` as the
  Nav3 wiring; delete `DataRepository.kt`, `MainScreen.kt`, `MainScreenViewModel.kt` and their two tests.
- Bunny list / add / edit / archive / delete, per ADR-0004. Avatar from camera or album.
- Missing-media placeholder, required by ADR-0005.

**Gate:** two bunnies with avatars survive a restart; deleting a bunny with records requires two
confirmations and removes its media; a deliberately broken image path renders as a placeholder; the
top-level destinations exist (as stubs where needed) and the bunny switcher scopes the per-bunny screens.

## Phase 2 — Weight and observations

- Weight entry and chart. Entry defaults to now but the date/time is **editable, and back-dating is
  allowed** (weigh in the morning, log in the evening), while **future timestamps are rejected**. Existing
  entries' timestamps are editable too — a fat-fingered date otherwise distorts the trend permanently. The
  chart plots **real timestamps, not list index**.
- Trend flag — the app's **single load-bearing safety signal**, the one thing that fires without the owner
  pre-diagnosing (CONTEXT.md), so it gets the most careful unit tests in the project. The rule: flag a
  **drop of ≥ ~5% from a trailing-median baseline** (median of the last 2–3 weighings), **rate-normalized**
  over the interval so an irregular gap between weigh-ins doesn't distort it, with a small **gram
  noise-floor** so day-to-day gut/bladder fluctuation never trips it. The delta is displayed in **grams**
  (house rule) and framed "down [X] g since [date] — worth a closer look," **never a diagnosis** (ADR-0001,
  no medical advice). The exact percentage and baseline size are tunable with vet input; the *shape*
  (percentage, baseline-relative, rate-normalized, noise-floored) is fixed.
- The flag surfaces **at the point of entry** — the moment a just-logged weight trips the threshold, shown
  in the entry flow — **and persists on Home / the weight screen** until the trend recovers or the owner
  acknowledges it. **No push notification:** a drop can only appear when a weight is logged and the owner is
  present at that moment, so a push would be redundant and would drift toward sounding diagnostic.
- Observation entry (ADR-0001): every field optional — droppings, appetite, mood, activity, water,
  cecotropes, symptoms, note. Back-dating supported on the same terms as weight. Droppings **amount
  defaults to "not checked," never a silent "normal"** (CONTEXT.md): auto-filling the earliest health
  signal with an unverified "fine" is a false reassurance the app must not manufacture. The one-tap healthy
  day is preserved by an explicit **"Log a healthy day"** shortcut that *affirmatively* records normal.
  Timeline grouped by day for display only.
- Warnings derive from recorded observations, never from silence.

**Gate:** unit tests for trend math pass (percentage, trailing baseline, rate-normalization, noise-floor,
gram-delta); the chart is time-correct with deliberately uneven and back-dated dates; a future-dated weight
is rejected; an untouched droppings field records "not checked", not "normal"; an empty database produces
no warnings.

## Phase 3 — Backup, first-run setup, photo gallery

Moved ahead of vet/meds: by the end of Phase 2 the app holds irreplaceable data with no way off the device.

- Photo gallery, moved here from Phase 1 (ADR-0015): per-bunny lazy grid, full-screen pager, captions. It
  lands alongside backup because photos are the sentimental bulk excluded from Auto Backup and covered only
  by the "Everything" manual scope — building the gallery and that boundary together keeps them in step.
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
  Completion can be **back-dated** (did the nail trim yesterday, log it today) on the same terms as Phase 2
  entry; the next occurrence is scheduled from the recorded completion, not from when it was ticked off.
- Presets: nail trim (~6 weeks), vaccination (annual), weigh-in (weekly).
- Watch: opt-in per bunny and **time-boxed** — the owner sets a duration when starting it (default ~7 days)
  and it **auto-expires** with a prompt to extend or close, never silently persisting into wallpaper
  (ADR-0001). Only while active does the app chase for fresh observations: a **once-daily best-effort
  WorkManager notification** framed about the owner's checking, not the bunny's state, and **satisfied by
  logging any observation** for that bunny that day. A missed watch nag is low-stakes, so best-effort
  delivery is fine — it needs none of the exact-alarm treatment doses get.
- Battery-optimisation exemption requested here, at the point something is first scheduled.
- Care reminders optionally hand off to the owner's calendar, one-way, no permission (ADR-0014).

**Gate:** a reminder set for +2 minutes fires while backgrounded and still fires after a reboot; a reminder
also fires after the phone has sat idle in Doze **overnight** (screen off, app unopened) on the real
Xiaomi — the +2-minute happy path is not sufficient evidence of reliability (ADR-0003); tapping
*Add to calendar* on an annual reminder opens the calendar app with the date and yearly repeat already
filled in; a short-duration watch stops nagging once it auto-expires.

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
