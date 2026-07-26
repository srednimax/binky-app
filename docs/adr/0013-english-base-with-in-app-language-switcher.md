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
**mechanism** is built one checkpoint earlier, with English alone in the list, and that is not a
contradiction: shipping it early is not the goal, finding out what the backport costs is. At `minSdk` 26
most of the supported range takes that backport rather than the platform API, and if it requires the app's
single `ComponentActivity` rebuilt as an `AppCompatActivity` with AppCompat theming beneath a pure Compose
and Material 3 app, then this is not a Settings row — and that is not a thing to discover in the week the
translation lands.

## Consequences

Counts use `<plurals>`, not string concatenation. Polish has four plural categories (`one`, `few`, `many`,
`other`) against English's two, and hand-rolled pluralisation will be wrong in one of the languages.

Dates, numbers and weights are formatted through the platform so separators follow the locale — `2,45 kg`
in Polish, `2.45 kg` in English. Never hand-format a date.

The built-in symptom list (ADR-0010) is seeded as **stable keys rendered via `strings.xml`**, not as
English strings in the database. A seeded English row would display in English on a Polish device,
bypassing translation entirely and leaving the symptom picker half-translated. Only owner-added symptoms
are untranslatable literal text, which is expected.
