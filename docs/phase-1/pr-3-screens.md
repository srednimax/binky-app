# PR 3 — Navigation and screens

**Branch:** `screens` · **Depends on:** PR 2 merged (`AppContainer`, `MediaFiles`, the DAOs) ·
**Decisions and reasoning:** [`README.md`](README.md)

---

## Navigation

Rewrite `NavigationKeys.kt` and `Navigation.kt`. Keep Nav3 (`NavDisplay`, `entryProvider`,
`rememberNavBackStack`) — do **not** reintroduce Navigation Compose 2.x.

Every key is declared now, including ones whose screens arrive in later phases: ADR-0012 #5 fixes the
navigation structure up front, because changing which screens exist invalidates every entry point and
back-stack assumption.

```kotlin
@Serializable data object Welcome : NavKey                                      // first run only
@Serializable data object BunnyList : NavKey                                    // root
@Serializable data class  BunnyDetail(val bunnyId: String) : NavKey             // summary cards
@Serializable data class  BunnyForm(val bunnyId: String? = null) : NavKey       // null = add
@Serializable data class  BunnyPhotos(val bunnyId: String) : NavKey
@Serializable data class  PhotoPager(val bunnyId: String, val startIndex: Int) : NavKey
@Serializable data object Settings : NavKey
// declared now, stubbed until their phase:
@Serializable data class  BunnyWeight(val bunnyId: String) : NavKey             // Phase 2
@Serializable data class  BunnyObservations(val bunnyId: String) : NavKey       // Phase 2
@Serializable data class  ObservationForm(val bunnyIds: List<String>) : NavKey  // Phase 2, ADR-0008
@Serializable data object SymptomList : NavKey                                  // Phase 2
@Serializable data class  BunnyCare(val bunnyId: String) : NavKey               // Phase 4
@Serializable data class  BunnyVisits(val bunnyId: String) : NavKey             // Phase 5
@Serializable data object VetDirectory : NavKey                                 // Phase 5
```

`ObservationForm` takes a **list** deliberately: a fluffle logs for all its members, a bunny logs for
itself, and the honest default falls out of where the owner tapped rather than from a checkbox they might
not notice (ADR-0008). Stubs are a single centred `Text` naming the phase.

`MainActivity` blocks on the wipe-guard state from PR 2 before showing `MainNavigation()`. The back stack
starts at `Welcome` when `onboardingCompleted` is false, otherwise at `BunnyList`; `Welcome` replaces
itself rather than being pushed, so Back never returns to it.

**Delete once the real screens land:** `data/DataRepository.kt`, `ui/main/MainScreen.kt`,
`ui/main/MainScreenViewModel.kt`, and the two tests under `ui/main/`.

## Screens — `ui/bunnies/`, `ui/photos/`, `ui/settings/`

One ViewModel per screen; UI state as a single immutable data class; composables stateless — state and
callbacks in, ViewModel holds it (ADR-0012 #2).

All user-facing text in `res/values/strings.xml`; colours and type from `MaterialTheme`, never literals;
touch targets ≥48dp; content descriptions on icons (ADR-0012 #1/#3/#4). Stock Material 3, deliberately
plain — visual design is its own phase.

Two things ADR-0013 requires from the first screen, both of which appear in Phase 1: **counts use
`<plurals>`**, never string concatenation (the delete confirmation), and **dates format through the
platform**, never hand-built (the birth date).

### 0. `WelcomeScreen`

Shown once, on first launch. What the app is for, and one honest line that the data never leaves the
phone. Two buttons: *Add your first bunny* → `BunnyForm()`, and *Skip*. Either sets `onboardingCompleted`.

ADR-0006's other two steps — backup scope and reminders opt-in — cannot exist until Phases 3 and 4 and are
**not** shown as placeholders.

### 1. `BunnyListScreen`

Active bunnies with avatar and name, grouped under fluffle headers. A fluffle header carries its own log
action (stubbed to `ObservationForm` in Phase 1) — this is the ADR-0008 entry point. Archived bunnies in a
collapsed section. FAB → `BunnyForm()`. Top bar ⚙ → `Settings`.

The empty state still explains and points at the FAB: it is what the owner sees after skipping the
welcome, and after an ADR-0007 wipe, where the preference survives but every bunny is gone.

### 2. `BunnyFormScreen`

Add and edit. Name required, everything else optional. Avatar via Photo Picker or `TakePicture` — not
CameraX. Birth date with an "approximate" checkbox. `neutered` as a tri-state (Yes / No / Unknown), since
`null` means unknown and must stay reachable. "Lives with" fluffle picker: choose an existing fluffle or
name a new one — declared, not inferred (ADR-0008).

### 3. `BunnyDetailScreen`

A scrolling summary of cards: details, a photo strip → `BunnyPhotos`, and weight / observations / care /
visits cards reading "nothing recorded yet" until their phase, each already wired to its nav key.

Overflow: Edit, **Archive** (single confirmation, reversible), **Delete** (ADR-0004 — confirm, and when
the bunny has records a *second* dialog naming the counts). Archive and delete must never share a code
path, and delete is never one tap.

### 4. `BunnyPhotosScreen`

`LazyVerticalGrid`, add via picker or camera, tap → `PhotoPager`.

### 5. `PhotoPagerScreen`

`HorizontalPager` full-screen, caption edit, "set as avatar", delete (removes the row *and* the file).

### 6. `SettingsScreen`

The hub. Phase 1 ships About only; Backup, Language, Vets and Symptoms appear as disabled rows naming
their phase, so the structure is visible and fixed.

### `BunnyImage`

A composable wrapping Coil's `AsyncImage` with `MediaFiles.resolve` plus `error`/`fallback` placeholders,
so **missing media renders as a placeholder, never a crash** (ADR-0005 — a restore may legitimately arrive
without media). Every image in the app goes through it.

> **Kotlin note** (worth a line in the commit message): `data class copy()` is the object-spread analogue
> — `state.copy(name = it)` is `{...state, name: it}`, and it is how a ViewModel replaces one field of an
> immutable UI-state class.

## Docs

Tick Phase 1 in `docs/PLAN.md`, and record there that the drill-down screens and `ObservationForm`'s
signature were fixed in Phase 1 under ADR-0012 #5.

---

## Verify

The full Phase 1 gate — see [`README.md`](README.md#the-phase-1-gate). Once it passes, delete
`docs/phase-1/`.
