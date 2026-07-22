# English is the base language; Polish is added with an in-app switcher

All user-facing text lives in `res/values/strings.xml` in English from the first screen. English is the
base because it is the fallback for every unmatched locale, and the eventual Play audience is wider than
the author's own. Code, comments and the `CONTEXT.md` vocabulary stay English regardless.

Polish arrives as `res/values-pl/strings.xml` before the Play release — one new file, no code changes,
provided nothing was ever hardcoded.

Android selects the translation from the device language automatically. On top of that the app offers its
own **language switcher**, so the app's language can differ from the phone's. This is native on Android
13+ (`locales_config.xml` plus `setApplicationLocales()`, and it appears in system settings too); below 13
it needs AppCompat's backport, which is the one dependency accepted purely for this feature. The switcher
is built when Polish lands, not before — a switcher with one language in it is pointless.

## Consequences

Counts use `<plurals>`, not string concatenation. Polish has four plural categories (`one`, `few`, `many`,
`other`) against English's two, and hand-rolled pluralisation will be wrong in one of the languages.

Dates, numbers and weights are formatted through the platform so separators follow the locale — `2,45 kg`
in Polish, `2.45 kg` in English. Never hand-format a date.
