# Build it functional and plain; visual design is a dedicated phase at the end

Screens are built with stock Material components and default theming until every feature works. They will
look boring, and that is accepted deliberately — the app is being written by one person learning Kotlin,
and making it beautiful before it is useful is how it ends up neither.

Compose has no stylesheet, so "style it later" is not literally available the way it is on the web. Five
things are therefore held to from the first screen, because retrofitting them means touching every screen
and a retrofit that large does not get done:

1. **Colours and text styles come from `MaterialTheme`, never literals.** The visual pass then edits one
   file, and dark mode comes free. Hardcoded colours across forty screens cannot be reliably found later.
   *(Amended by ADR-0027: this held for the screens but not for the theme. `dynamicColor = true` meant that
   on Android 12+ the one file was never read, so the promise was true only on API 26–30. Phase 7 turns
   dynamic colour off by default and makes it an opt-in, which is what makes this rule true on a shipping
   device.)*
2. **Screens are stateless** — composables take state and callbacks, ViewModels hold state. Restyling then
   touches only presentation, and `@Preview` works, which is the only sane way to iterate on visuals.
3. **User-facing text lives in `strings.xml`**, never inline. This also gates translation.
4. **Touch targets are at least 48dp and icons have content descriptions.**
5. **Navigation structure is decided up front.** Changing which screens exist and how they connect
   invalidates every entry point and back-stack assumption.

When the same visual pattern appears a third time it becomes a shared composable. Those, plus the theme,
are where the eventual design work lands.

## Consequences

Deferred entirely to the design phase: custom components, animation, illustration, empty-state art,
in-app iconography, spacing refinement, visual identity. None of these may be used as a reason to
restructure screens — if that becomes necessary, the split above was not respected.

**One stated exception: identity assets are due at the first release.** The launcher icon, the app name and
the store listing's graphics are not "how the app looks" in the sense this ADR defers — they are how it is
recognised, and unlike spacing or colour they cannot be changed later without changing what people already
have on a home screen. They are also simply required: Play will not publish to any track without them. So
the icon is designed at Phase 3's first checkpoint, alongside the developer account and the keystore rather
than alongside the visual pass, and shipping the AGP template's green robot is not an available reading of
"boring is accepted deliberately". Everything else in the list above stays deferred.
