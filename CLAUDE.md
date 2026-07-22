# rabbit-app

Native Android app for tracking the health of one or more pet rabbits: weight over time, observations of
droppings and wellbeing, vet visits, medications, scanned documents, photos, and reminders for recurring
care. Free, ad-free, no server, all data on the device.

- **Vocabulary:** [`CONTEXT.md`](CONTEXT.md) — use these terms in code and UI.
- **Decisions and why:** [`docs/adr/`](docs/adr/) — read before changing anything they cover.
- **Roadmap and status:** [`docs/PLAN.md`](docs/PLAN.md).

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
| ML Kit Document Scanner, behind an interface | Needs Play services — ADR-0009 |
| Photo Picker + `TakePicture` intent, **not CameraX** | Far less code; system camera is fine here |
| Vico charts, Coil 3 images | Compose-native, actively maintained |
| On-device storage only | No backend, ever. Backup per ADR-0005 |

## House rules

- **Media paths in the DB are relative** (`photos/<uuid>.jpg`), resolved against `filesDir` at read time.
  Absolute paths change across installs and break restored backups.
- **All image writes go through the media helper** — it downsamples and re-encodes. Bypassing it puts
  full-resolution bitmaps in memory and blows up the photo grid.
- **Missing media renders as a placeholder, never a crash.** A restore may legitimately lack photos.
- **Weight is stored as `Int` grams.** Never a float. Entry is in grams (that's what scales show); display
  unit is a user preference defaulting to kg; **changes are always shown in grams**, because `−0.04 kg`
  hides the signal that `−40 g` makes obvious.
- **DAOs return `Flow`**, screens collect it. Don't hand-roll refresh calls.
- **Enums with `TypeConverter`s, not loose strings**, for droppings, mood and care type — closed
  vocabularies. Symptoms are the exception: a seeded table, because owners add their own (ADR-0010).
  Store enums by **name, never ordinal**, so adding a value can't rewrite history.
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
app/src/main/java/app/rabbit/tracker/
  data/        Room entities, DAOs, database, type converters, repositories
  media/       MediaFiles.kt — the single path for persisting images. Named to avoid colliding
               with Android's own android.provider.MediaStore
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
**on the phone** — accept it or the run fails with `INSTALL_FAILED_USER_RESTRICTED`. Xiaomi also kills
background work aggressively; scheduled notifications need battery-optimisation exemption and autostart.
