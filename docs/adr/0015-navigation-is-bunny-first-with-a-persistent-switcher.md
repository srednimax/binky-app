# Navigation is bunny-first, with a persistent switcher and a global observation entry

ADR-0012 requires the navigation structure decided before the first screen, because changing which screens
exist and how they connect invalidates every entry point and back-stack assumption. This ADR fixes it, so
that rule is actually honoured rather than left blank until screens start landing.

The data does not nest cleanly onto one axis. Almost everything hangs off a single bunny — weight, avatar,
photos, documents, care reminders, medication courses — which argues bunny-first. But **observations can
cover several bunnies at once** (ADR-0008), and the owner often thinks "how is the fluffle today" rather
than filing a fact under one rabbit; and **reminders and doses** are not reached by first selecting the
right bunny. A strictly bunny-first structure would trap shared-observation logging inside one bunny; a
strictly feature-first one would bury the central noun.

The structure is therefore **bunny-first with two escapes**:

- A **persistent bunny switcher** in the app bar selects the bunny that scopes the per-bunny screens, plus
  an **"All bunnies"** mode for the list and fluffle-wide review.
- Bottom-navigation destinations: **Home** (the selected bunny's overview — avatar, current weight and
  trend flag, active watch and medication courses, recent observations), **Weight**, **Observations**,
  **Care & Meds**, **More** (documents, photos, settings, support).
- **Logging an observation is a global "+" action**, not a per-bunny one. It opens with participants
  pre-selected by the bunny's fluffle (ADR-0008), so a shared observation is never trapped inside one
  bunny.
- **Reminders and doses surface on Home and via notifications**, never only by drilling into a bunny to
  find them.

## Consequences

The top-level destinations and the switcher model are fixed **before Phase 1** and built as stubs where the
real screen does not exist yet — the photo gallery (Phase 3), Care & Meds (Phases 4-5) — so the back-stack
and entry points are settled while the structure is still cheap to change (ADR-0012 #5).

The bunny switcher is app-wide state, not per-screen: a `StateFlow` on `AppContainer`, **persisted to
DataStore** so an aggressive Xiaomi background-kill lands the owner back on the same bunny rather than a
default. It is **resolved reactively against the live list of active bunnies**, so archiving or deleting the
selected bunny self-heals with no explicit event — falling back to the sole remaining active bunny, else
**"All bunnies"** (never silently auto-attributing to an arbitrary one), else the add-a-bunny empty state.
Per-bunny screens read the selected bunny; the global observation entry and the "All bunnies" list
deliberately ignore it. Because there is no "All bunnies" weight chart (below), the Weight screen shows a
pick-a-bunny prompt while "All bunnies" is selected.

Weight is always individual (ADR-0008), so the Weight screen is always bunny-scoped. There is no
"All bunnies" weight chart — overlaying unrelated animals of different sizes on one axis would say nothing
true.

## Each screen resolves "All bunnies" by the shape of its data

"All bunnies" is not one behaviour — each top-level screen answers it by whether its data is individual or
fluffle-shaped, and the resulting asymmetry is intentional:

- **Home** becomes a **fluffle dashboard**: one compact vitals card per active bunny — current weight and
  trend flag, any active watch, any active medication course, the date of the most recent observation. This
  is the most valuable screen for a multi-bunny owner, and it is where a trend flag on *bunny B* catches the
  eye of someone who opened the app thinking about bunny A. Each card is the single-bunny Home summary in
  miniature.
- **Observations** becomes the **combined day-grouped timeline** across every active bunny, each row showing
  which bunnies it covered (shared observations are already multi-bunny, ADR-0008). Selecting a single bunny
  *filters* to observations that include it; here the single-bunny view is the special case and "All
  bunnies" is the natural default.
- **Weight** alone **refuses** "All bunnies" and shows a pick-a-bunny prompt, because weight is individual
  and no honest cross-bunny chart exists (above).

Three screens, three empty-selection behaviours, driven entirely by whether the underlying data is
individual (weight) or fluffle-shaped (observations) — not an inconsistency to smooth over.
