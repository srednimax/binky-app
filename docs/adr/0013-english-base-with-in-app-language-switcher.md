# English is the base language; Polish is added with an in-app switcher

All user-facing text lives in `res/values/strings.xml` in English from the first screen. English is the
base because it is the fallback for every unmatched locale, and the eventual Play audience is wider than
the author's own. Code, comments and the `CONTEXT.md` vocabulary stay English regardless.

Polish arrives as `res/values-pl/strings.xml` before the Play release — one new file, no code changes,
provided nothing was ever hardcoded.

Android selects the translation from the device language automatically. On top of that the app offers its
own **language switcher**, so the app's language can differ from the phone's. This is native on Android
13+ (`locales_config.xml` plus `setApplicationLocales()`, and it appears in system settings too); below 13
it needs AppCompat's backport, which is the one dependency accepted purely for this feature.

The switcher **ships** when Polish lands — a switcher with one language in it is pointless. Its
**mechanism** is built earlier, with English alone in the list, and that is not a contradiction: shipping it
early is not the goal, finding out what the backport costs is. At `minSdk` 26 most of the supported range
takes that backport rather than the platform API, and if it requires the app's single `ComponentActivity`
rebuilt as an `AppCompatActivity` with AppCompat theming beneath a pure Compose and Material 3 app, then
this is not a Settings row — and that is not a thing to discover in the week the translation lands.

**It was checked, and it is the expensive answer.** AppCompat's pre-13 locale support is applied through
`AppCompatDelegate`, which exists only inside an `AppCompatActivity`, which in turn requires an
AppCompat-descended theme — and this app is one `ComponentActivity` under
`android:Theme.Material.Light.NoActionBar` with no AppCompat in the dependency graph at all. So the cost is
a new dependency, the app's only activity's base class, and the root theme.

It is accepted anyway, which is what this ADR reserved in advance, with three consequences that follow from
it being shell work rather than a Settings row:

- The theme reparents to **`Theme.AppCompat.DayNight.NoActionBar`**, not a Material Components theme.
  `AppCompatActivity` needs only an AppCompat-descended theme, and Compose M3 draws every pixel of real UI,
  so pulling in `com.google.android.material` would add a second dependency that renders nothing.
- Persistence uses AppCompat's own `AppLocalesMetadataHolderService` with `autoStoreLocales="true"` rather
  than a DataStore key, because DataStore's asynchronous read would let the app draw a frame in the wrong
  language before resolving.
- It lands in **its own checkpoint near the front of Phase 3**, not beside the translation at the end. A
  base-class and root-theme change is the cheapest thing in that phase to do early and among the most
  expensive to do late; landing it first means every later checkpoint's hand-verification passes over it
  for free, instead of a theme regression surfacing underneath the release.

The alternatives were weighed and rejected on the record. The platform API alone (`LocaleManager` on 33+,
no switcher below) is about ten lines and no dependency, and most Polish users would get Polish regardless
because that is ordinary resource resolution rather than the switcher — but it abandons the pre-13 range
that `minSdk` 26 exists to serve. A hand-rolled `attachBaseContext` override avoids the dependency and
lands the same obligation permanently on every context that resolves strings outside the activity, which
Phase 4's notifications and WorkManager would inherit.

## Consequences

Counts use `<plurals>`, not string concatenation. Polish has four plural categories (`one`, `few`, `many`,
`other`) against English's two, and hand-rolled pluralisation will be wrong in one of the languages.

Dates, numbers and weights are formatted through the platform so separators follow the locale — `2,45 kg`
in Polish, `2.45 kg` in English. Never hand-format a date.

The built-in symptom list (ADR-0010) is seeded as **stable keys rendered via `strings.xml`**, not as
English strings in the database. A seeded English row would display in English on a Polish device,
bypassing translation entirely and leaving the symptom picker half-translated. Only owner-added symptoms
are untranslatable literal text, which is expected.
