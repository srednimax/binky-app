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

The bunny switcher is app-wide state, not per-screen. Per-bunny screens read the selected bunny; the global
observation entry and the "All bunnies" list deliberately ignore it.

Weight is always individual (ADR-0008), so the Weight screen is always bunny-scoped. There is no
"All bunnies" weight chart — overlaying unrelated animals of different sizes on one axis would say nothing
true.
