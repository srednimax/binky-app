# Build it functional and plain; visual design is a dedicated phase at the end

Screens are built with stock Material components and default theming until every feature works. They will
look boring, and that is accepted deliberately — the app is being written by one person learning Kotlin,
and making it beautiful before it is useful is how it ends up neither.

Compose has no stylesheet, so "style it later" is not literally available the way it is on the web. Five
things are therefore held to from the first screen, because retrofitting them means touching every screen
and a retrofit that large does not get done:

1. **Colours and text styles come from `MaterialTheme`, never literals.** The visual pass then edits one
   file, and dark mode comes free. Hardcoded colours across forty screens cannot be reliably found later.
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
iconography, spacing refinement, visual identity. None of these may be used as a reason to restructure
screens — if that becomes necessary, the split above was not respected.
