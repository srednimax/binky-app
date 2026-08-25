# Binky owns its palette; Material You becomes an opt-in

`theme/Theme.kt` has shipped `dynamicColor: Boolean = true` since the app was scaffolded. On Android 12+
the palette is derived from the user's **wallpaper**, so `LightColorScheme` and `DarkColorScheme` are only
ever seen on API 26–30. `minSdk` is 26, but the overwhelming majority of installs will not be.

From Phase 7 the default is **off**. Binky ships its own light and dark schemes, and Material You is a
Settings toggle that defaults to off for anyone who prefers their phone's colours.

## The reason is ADR-0012, not taste

ADR-0012's first rule is the load-bearing one: *colours and text styles come from `MaterialTheme`, never
literals — **the visual pass then edits one file**, and dark mode comes free.* Every screen in this app was
built to honour it, and it is the reason a redesign is tractable at all.

**On Android 12+ that promise is currently false.** The one file the visual pass is supposed to edit is
`Color.kt`, and on API 31+ nothing reads it. A designer could pick a palette, wire it into
`lightColorScheme(...)`, build, install, and watch the app come up in the same wallpaper-derived colours as
before — with no error, no warning, and no obvious reason why. ADR-0012 spent five rules and three phases
buying a single-file visual pass; `dynamicColor = true` quietly spends it back.

So this is not a new decision so much as the missing half of an old one. Turning it off is what makes
ADR-0012 true on the devices people actually own.

## Why own a brand rather than keep Material You

The alternative was real: keep dynamic colour, and let the redesign be layout, hierarchy, density and
spacing, with colour used only through tonal roles (`primaryContainer`, `surfaceVariant`). That is a
coherent position and it is what Google recommends.

It loses on two specifics. **There is nothing to preserve** — the current scheme is still `android create`'s
`Purple40`/`Purple80`, three roles deep, with the generated `/* Other default colors to override */` comment
block intact. No identity is being discarded, because none was ever chosen. And **an app with no reviews and
no installs is helped more by being recognisable than by matching a wallpaper**: Binky's Play listing is new,
the screenshots are the listing, and four screenshots that look like four different apps is what wallpaper
harmony buys at exactly the wrong moment.

The toggle is what makes this cheap to be wrong about. Anyone who wants Material You keeps it, at the cost
of one preference.

## Consequences

**A full scheme is now owed, not three roles.** `lightColorScheme(primary, secondary, tertiary)` leans on
M3's baseline for `surface`, `background`, `outline`, every `on*` and every `*Container`. With dynamic colour
off those defaults are what most users see, and M3's baseline is itself purple. The visual pass owes both
schemes in full, and dark is not a derivation of light — contrast, elevation and the surface roles land
differently, which is why the before/after evidence is captured in both.

**The toggle is a preference, not a schema change.** It lives in DataStore beside the weight unit and the
language, so Phase 7's "no schema change, no migration" constraint holds. It needs a label and a help line
as resources in **both** locales (ADR-0013), and `PolishTranslationTest` is what enforces that.

**Phase 7's "before" screenshots record a wallpaper, not a palette.** They were captured with
`dynamicColor = true` still in force, so their colours are one phone's wallpaper on one day and are not
reproducible. This is accepted: the before set is read for structure, density, hierarchy and copy, and those
are exactly what does not depend on the palette. The after set is the first reproducible one.

**ADR-0012 is amended, not overturned.** Its deferral of visual identity to a dedicated phase stands, and
this is that phase arriving on schedule. What changes is that "the visual pass edits one file" becomes a
statement about the shipping app rather than about API 26–30.

## Amendment (Phase 10, 10f): the palette gains a light/dark lever

The decision above chose *which* colours Binky uses. It left *when* each of the two schemes applies to
the phone — `isSystemInDarkTheme()`, and nothing else. Settings now offers **System / Light / Dark**,
defaulting to System.

### Why the app needs its own answer

Three reasons, and none of them is taste.

**A phone on a dark-mode schedule flips the app mid-read.** An owner filling in an observation at dusk
watches the form change colour under them. That is the platform working correctly and it is still the
app's problem.

**The Play screenshots are light-only** (decided 2026-08-24), and the capture phone is a real device
with a real dark-mode setting. Driving the whole capture through the *system* setting means the
screenshot run and the owner's phone share one global — `edge-to-edge.py` has to put it back, and a
failed run leaves the phone somewhere the owner did not choose. An in-app override is a knob the
capture can turn that belongs to the app.

**Dark mode is not universal below Android 10.** `minSdk` is 26, and API 26–28 have no system-wide
dark setting at all. Without this, a third of the supported range cannot reach `DarkColors` — the very
scheme this ADR's consequences say is owed "in full, and dark is not a derivation of light". The
in-app override is what makes half the work this ADR commissioned reachable on the phones it commissioned
it for.

### The mechanism is `AppCompatDelegate`, not a Compose flag

`BinkyTheme` picks a `ColorScheme` and that is the whole of what Compose can reach. Two things the owner
sees are outside it:

- **The window background**, painted from `Theme.Binky` — a `Theme.AppCompat.DayNight` theme — *before*
  Compose composes anything.
- **The system-bar scrim**, a `values-night/` colour resolved at inflation (`values/colors.xml`, and
  `SystemBarsTest` for why there are four qualifier files).

Both follow Android's configuration. A Compose-only override leaves them following the **phone** while
the app follows the **owner**, which is the exact mismatch those four `colors.xml` files exist to
prevent, and it is worst on API 26–28 where there is no system dark mode to have agreed with by
accident.

`AppCompatDelegate.setDefaultNightMode` moves the configuration itself, so all three agree at once.
`theme/NightMode.kt` is the one call site.

### Consequences

**The preference is ours to persist, unlike the language.** ADR-0013's switcher stores nothing:
`setApplicationLocales` persists itself, which is why `AppLanguage` reads back from the delegate rather
than from DataStore. Night mode does **not** persist — it is process state, and a fresh process comes up
following the system. So this one is a DataStore key, and something has to re-apply it on every cold
start.

**That start-up read blocks the main thread, deliberately.** `AppPreferences.themeModeAtStartup()` is a
`runBlocking` read in `BinkyApplication.onCreate`, because the night mode has to be applied before the
first Activity exists and a flow's first emission does not arrive until after the first frame. The
alternative is a visible flash on every cold start of a phone set to Dark — the thing the setting exists
to remove. It is also cheap next to what that method already does synchronously: it reads a database
header and copies the whole database file.

**Two paths move the theme and they agree by construction.** `applyThemeMode` moves the window;
`MainActivity` collects the same preference and hands it to `BinkyTheme`, which moves the scheme. Neither
can reach what the other does, so both are needed. The manifest already lists `uiMode` in
`configChanges`, so AppCompat hands the running Activity an `onConfigurationChanged` rather than
recreating it — the tap repaints the app in place.

**`SystemBarAppearance` needed no change.** It keys off `BinkyTheme`'s resolved `darkTheme`, which is now
the override's answer rather than the system's, so the bar icons follow for free. That it needed no edit
is evidence for where this ADR put the runtime half in the first place.
