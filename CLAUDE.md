# bunny-app

Native Android app for tracking the health of one or more pet bunnies: weight over time, observations of
droppings and wellbeing, vet visits, medications, scanned documents, photos, and reminders for recurring
care. Free, ad-free, no server, all data on the device.

- **Vocabulary:** [`CONTEXT.md`](CONTEXT.md) — use these terms in code and UI.
- **Decisions and why:** [`docs/adr/`](docs/adr/) — read before changing anything they cover.
- **What's still open:** [`docs/DOD.md`](docs/DOD.md) — the live checklist. **Read this one first**;
  it is short by design, and `PLAN.md` is the long record behind it.
- **The phases still open:** each in its own `docs/phase-N.md` — [`phase-6.md`](docs/phase-6.md),
  [`phase-7.md`](docs/phase-7.md), [`phase-7.5.md`](docs/phase-7.5.md), [`phase-8.md`](docs/phase-8.md).
  Finished phases stay in `PLAN.md`;
  don't load it to build one.
- **Roadmap and status:** [`docs/PLAN.md`](docs/PLAN.md) — read the phase you're in, not the file.
- **Commits & releasing:** [`docs/RELEASING.md`](docs/RELEASING.md). Commit subjects **must** be
  [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `feat!:`, `docs:`, …) — a
  `commit-msg` hook rejects anything else, and release-please derives the version + `CHANGELOG.md` from them.
  `versionName` and `versionCode` are automated; never hand-edit either.

Don't restate ADR reasoning here; link to it. This file is loaded every session and must stay short.

## Working with the person who owns this repo

Fluent in **JavaScript/TypeScript, new to Kotlin**. Comment code where a Kotlin/Compose idiom has no
direct JS analogue — `Flow` vs promises/observables, `suspend` vs `async`, `remember`/`derivedStateOf`
vs `useMemo`, data class `copy()` vs object spread, sealed classes vs discriminated unions. Explain
the *why*, don't restate the line. Prefer explicit and readable over clever.

## Stack

| Choice | Note |
| --- | --- |
| Kotlin, Jetpack Compose, Material 3 | Android only, `minSdk` 26 |
| **Navigation 3** (`androidx.navigation3`) | Replaces Navigation Compose 2.x — don't reintroduce the old one |
| Room via KSP | DAOs return `Flow`; schema evolution per ADR-0007 |
| Manual DI via `AppContainer`, **not Hilt** | ~15 screens; constructor injection is clearer and easy to migrate later |
| WorkManager + exact alarms | Two mechanisms on purpose — ADR-0003 |
| ML Kit Document Scanner, behind an interface | Needs Play services — ADR-0009. It merges `INTERNET` into the manifest (5g); `scripts/aab-permissions.py` is the record |
| Photo Picker + `TakePicture` intent, **not CameraX** | Far less code; system camera is fine here |
| Vico charts, Coil 3 images | Compose-native, actively maintained |
| On-device storage only | No backend, ever. Backup per ADR-0005 |

## House rules

- **An update never loses an owner's data.** Any change to `BUNNY_SCHEMA_VERSION` ships with its
  `MIGRATION_x_y` *registered in* `BUNNY_MIGRATIONS`, the exported `app/schemas/*/N.json` committed, a
  `SchemaGateTest` assertion that the launch gate lets the upgrade through, and a migration test that
  proves the rows survive. `scripts/schema-gate.py` enforces the mechanical half in CI; the correctness
  half is the committed backup fixtures. **Every migration test opens the database directly and so walks
  past the launch gate** — that is how 1.5 nearly shipped a refusal screen to every existing install
  (ADR-0023's Phase 7.5 amendment). Verify an upgrade on the phone, not only in a test.
- **Media paths in the DB are relative** and split by kind — `avatars/<uuid>.jpg`, `photos/<uuid>.jpg`,
  `documents/<uuid>.jpg` — resolved against `filesDir` at read time. Absolute paths change across installs
  and break restored backups; the split makes ADR-0005's export scopes a list of directories.
- **All image writes go through the media helper** — it downsamples and re-encodes per kind, and writes the
  file before the row (ADR-0020). Bypassing it puts full-resolution bitmaps in memory and blows up the photo
  grid.
- **Missing media renders as a placeholder, never a crash.** A restore may legitimately lack photos.
- **Weight is stored as `Int` grams.** Never a float. Entry is in grams (that's what scales show); display
  unit is a user preference defaulting to kg; **changes are always shown in grams**, because `−0.04 kg`
  hides the signal that `−40 g` makes obvious.
- **DAOs return `Flow`**, screens collect it. Don't hand-roll refresh calls.
- **Enums with `TypeConverter`s, not loose strings**, for droppings, mood and care type — closed
  vocabularies. For **care type** the closed enum only tags the *known* kinds; a care reminder is
  `{label, interval, optional type}`, so custom reminders carry a free-text label with no type (ADR-0018).
  Symptoms are the other exception: a seeded table, because owners add their own (ADR-0010). Store enums by
  **name, never ordinal**, so adding a value can't rewrite history.
- **The weight chart plots real timestamps, not list index** — weighings are irregular and index-based
  plotting silently lies about the trend.
- **Never infer a health problem from missing data** (ADR-0001). Silence means nobody looked.
- Health features are for *observation*, never medical advice.
- One `ViewModel` per screen, UI state as a single immutable data class.

## Commands

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on the connected phone
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented Room/DAO tests — needs a device
./gradlew lint
adb devices                      # confirm the phone is attached
```

## Layout

```
app/src/main/java/app/binky/tracker/
  MainActivity.kt, Navigation.kt, NavigationKeys.kt
               the app shell and Nav3 wiring stay at the package root, not under ui/ — they describe
               how the app hangs together rather than any one screen
  data/        Room entities, DAOs, database, type converters, repositories
  media/       MediaFiles.kt — the single path for persisting images, kind-aware
               (avatar / photo / document, each with its own directory and downsample spec). Named to
               avoid colliding with Android's own android.provider.MediaStore
  scan/        the document scanner behind ADR-0009's interface: ML Kit's guided one and the
               plain-camera fallback. **Nothing outside this package names ML Kit** — that is what
               keeps "drop the dependency" a one-line change in AppContainer
  ui/          Compose screens + ViewModels, one package per tab
  work/        reminder scheduling and notifications
```

## Versions

Scaffolded with `android create` (the current CLI; `sdkmanager` is deprecated — use `android sdk`).
Pinned in `gradle/libs.versions.toml`: **AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Compose BOM 2026.03.01**.
`compileSdk`/`targetSdk` 36, `minSdk` 26, JDK 21 toolchain.

`compileSdk` stays at **36** even though SDK platform 37.1 is installed locally — 36 is the combination the
AGP 9.0.1 template validates against. Bump only deliberately, with a build to prove it.

## Environment notes

Linux dev machine, no Android Studio — build and install from the CLI against a physical phone over USB.
An emulator would additionally need `usermod -aG kvm` and a re-login.

Test device is a **Xiaomi (HyperOS)**. Split-APK installs (`connectedAndroidTest`) prompt for confirmation
**on the phone**, and a missed prompt fails the run with `INSTALL_FAILED_USER_RESTRICTED`. If it keeps
refusing, skip the split install entirely — install both APKs plain, then run the tests directly:

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w binky.bunny.and.rabbit.tracker.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Same APKs, same runner, same tests — it just asks for two ordinary install confirmations instead.

**A brand-new `applicationId` is a different failure with the same error string.** HyperOS refuses the
*first* install of a package outright — `INSTALL_FAILED_USER_RESTRICTED` returns immediately with no
dialog to miss, where a missed prompt takes a few seconds first. Updates to a package that already
exists are fine, which is why this only bites after an `applicationId` change (3h). Either enable
*Ustawienia → Dodatkowe ustawienia → Opcje programisty → Instalowanie przez USB* (needs a Mi account),
or sidestep it entirely — `adb push` the APK to `/sdcard/Download/` and tap it in the phone's file
manager, which is an ordinary user install and not USB-gated.

The debug build takes `applicationIdSuffix = ".debug"` (ADR-0023), which is why the instrumentation
package above is `binky.bunny.and.rabbit.tracker.debug.test`. Note the **`applicationId` is
`binky.bunny.and.rabbit.tracker` while the `namespace` stays `app.binky.tracker`** — install identity and
source package deliberately disagree, because a Play Console package name cannot be changed after the
app entry is created (PLAN.md 3h). Kotlin packages and imports follow the namespace. The debug app installs
alongside the Play one as a separate install labelled **Binky Debug**; the two never replace each other,
which is the whole point — the Play build holds real bunny history.

Xiaomi also kills background work aggressively; scheduled notifications need battery-optimisation
exemption and autostart.
