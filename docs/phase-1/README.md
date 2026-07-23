# Phase 1 — working plan

> **Temporary.** This directory is scaffolding for executing Phase 1 and is **deleted when Phase 1 ships**.
> The durable record is [`ADR-0014`](../adr/), [`CONTEXT.md`](../../CONTEXT.md) and
> [`docs/PLAN.md`](../PLAN.md) — which these PRs write anyway. Don't mistake it for permanent
> documentation, and don't cite it after Phase 1.

Phase 1 is split into three PRs, landed in order. Each file below carries its own steps and its own
verify block; this README carries what they share.

| PR | File | Contents |
| --- | --- | --- |
| 1 | [`pr-1-rename.md`](pr-1-rename.md) | Rabbit → Bunny rename, `CONTEXT.md` vocabulary, ADR-0014 |
| 2 | [`pr-2-data-layer.md`](pr-2-data-layer.md) | Room, `MediaFiles`, wipe guard, `AppContainer`, tests. No UI |
| 3 | [`pr-3-screens.md`](pr-3-screens.md) | Navigation keys and every Phase 1 screen |

---

## Where the project stands

**Phase 0 is complete**, verified rather than assumed:

- `./gradlew assembleDebug test lint` → `BUILD SUCCESSFUL`, lint clean
- `adb devices` → `132bc856 device` (the Xiaomi is attached)
- CI green on `main` (run `29959565730`), including the emulator `instrumented` job — the
  `connectedAndroidTest` half of the gate
- Toolchain matches what `CLAUDE.md` pins: AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Compose BOM
  2026.03.01, `compileSdk` 36 / `minSdk` 26, JDK 21, Navigation 3

What exists is still the `android create` template: `data/DataRepository.kt` emits `listOf("Android")`,
`ui/main/MainScreen.kt` renders it, `Navigation.kt` has one destination. No database, no DI, no media
handling, no real screen.

## Two risks that were probed, not guessed

Both were open questions in the first draft of this plan. Both were settled by building, so neither needs
a contingency:

- **KSP works.** A throwaway copy in a scratch directory with KSP 2.3.10 + Room 2.8.4 and a real
  `@Entity`/`@Dao`/`@Database` against Kotlin 2.3.20 compiled clean — `kspDebugKotlin` and
  `compileDebugKotlin` both succeeded, no warning. KSP changed to plain versioning at 2.3.0; the latest is
  2.3.10 and none exists for 2.3.20, but the mismatch is tolerated. **No KAPT fallback needed.**
- **No `kotlinx-serialization-json` dependency.** `kotlinx-serialization-core:1.7.3` is already on the
  classpath transitively via `androidx.savedstate`, which is what Nav3 uses for `NavKey`s.

## Decisions

Each row is a decision taken deliberately, with the reason it went that way. The PR files assume these and
do not restate them.

| Decision | Why |
| --- | --- |
| **Rabbit → Bunny everywhere** — product name, package, and the domain vocabulary itself | Nothing is released, so `applicationId` is free to change now and never again |
| Schema is **`Bunny`, `Photo`, `Fluffle` only** | ADR-0007 permits destructive wipes before Phase 3, so later phases add their own entities. A model you have used beats one you guessed |
| Media **split by kind** — `avatars/`, `photos/`, `documents/` | ADR-0005's three export scopes then become a list of directories. With one folder, Phase 3 would have to query the DB to classify every file, and an orphaned file could not be placed in a scope at all |
| **Avatar is its own field and its own file**, not a `Photo` row | ADR-0005 backs avatars up as Essential and the gallery only as Everything, so they cannot share storage. `CONTEXT.md` is corrected — its Photo entry claimed the avatar was a Photo, which the backup design had already made untrue |
| **Wipe guard built in Phase 1** | ADR-0007 says "regardless of phase, a destructive wipe never happens silently". It needs only a 4-byte read of the file header, not a backup system — deferring it was based on a false premise |
| Living arrangement is a **Fluffle** | ADR-0008 uses "group" for two unrelated things: the shared-observation link and the living arrangement. Naming the living one Fluffle keeps `groupId` free for Phase 2's observation link |
| `BunnyDetail` is a **summary that drills down**, not a tab row | Six tabs would scroll off a phone screen and Phase 5 makes it seven — and it demotes Overview to one tab among equals, when a summary surfacing the last weight, last observation and next reminder is the screen that serves the app's stated purpose |
| Logging is reachable **fluffle-level and bunny-level** | ADR-0008: an observation often cannot be attributed to one bunny. If the only entry point were inside a bunny, every shared observation would start framed as that bunny's with a checkbox bolted on — the exact failure the ADR was written to prevent. `ObservationForm` therefore takes a **list** |
| **`Settings` is the hub** for screens belonging to no bunny | With no bottom nav, the list's top bar is the only host. Vets and symptoms are editable collections rather than settings, so they get their own nav keys pushed from Settings — and stay reachable contextually from the forms that use them |
| **Welcome step in Phase 1**, growing to ADR-0006's three steps in Phase 3 | Backup scope and reminders opt-in cannot exist until Phases 3 and 4, so they are not shown as dead placeholders. Pulls DataStore into Phase 1 for the flag |
| The onboarding flag lives in **DataStore, never the database** | Same reasoning ADR-0011 applies to the donation prompt: ADR-0007 lets the database be wiped, and a wipe must not replay onboarding. The list's empty state stays, because after a wipe the flag survives but the bunnies do not |
| **Care reminders may hand off to the calendar** — ADR now, built Phase 4 | A yearly vaccination reminder is where in-app scheduling is weakest. ADR-0003 already concedes neither mechanism fires reliably on aggressive skins without a battery exemption, and a WorkManager job is being asked to survive a year of reboots, an OS upgrade and possibly a new phone |
| **Three PRs** | The rename is ~90 mechanical edits that would bury Phase 1 logic in a diff. PR 2 has no UI, so its tests are the review. Each is independently green on CI |

## The Phase 1 gate

Run after PR 3, on the device:

```bash
./gradlew assembleDebug test lint
./gradlew connectedAndroidTest      # accept the install prompt on the phone
./gradlew installDebug
```

1. First launch shows the **welcome**; add a bunny from it. Force-stop and reopen — the welcome does not
   return, and Back from the list does not reach it.
2. Add a second bunny with an avatar, one from the album and one from the camera. Force-stop, reopen —
   both bunnies and both avatars survive.
3. Put both in one fluffle; the list groups them under its header.
4. Add photos to one bunny, open the pager, edit a caption, set one as the avatar, delete a photo. Confirm
   the avatar is a separate file and the gallery delete did not disturb it:
   `adb shell run-as app.bunny.tracker ls files/avatars files/photos`
5. Archive a bunny — it leaves the active list, its photos survive, it can be unarchived.
6. Delete a bunny that has photos — **two** confirmations, the second naming the counts with correct
   pluralisation for 1 and for 3; afterwards its rows *and* its files are gone.
7. **Break an image deliberately** (`adb shell run-as app.bunny.tracker rm files/photos/<one>.jpg`),
   reopen the gallery — placeholder, no crash.
8. **Exercise the wipe guard**: bump the schema version locally, reinstall, confirm the blocking screen
   appears and `files/preserved/bunny-<timestamp>.db` exists.
9. Confirm `app/schemas/…/1.json` is committed.

Then tick Phase 1 in [`docs/PLAN.md`](../PLAN.md) and delete this directory.
