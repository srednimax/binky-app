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
