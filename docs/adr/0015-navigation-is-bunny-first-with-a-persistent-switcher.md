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
real screen does not exist yet — the photo gallery (Phase 3), Care & Meds (1.1-1.2) — so the back-stack
and entry points are settled while the structure is still cheap to change (ADR-0012 #5).

Because 1.0 now ships at the end of Phase 3 (ADR-0019), those stubs would be **seen by real users**, and a
bottom-navigation tab opening onto "coming in a later version" is a fifth of primary navigation spent on a
dead end. Deciding the structure and rendering the destination are different claims, and ADR-0012 #5 only
requires the first. Each top-level destination therefore carries a **visibility state — `Hidden` /
`ComingSoon` / `Live`** — while every nav key and route exists in code from Phase 1 exactly as before. A
dead **tab** is `Hidden`; a dead **row** inside More or Settings may be `ComingSoon`, because it costs one
line in a list rather than a fifth of the navigation. Promoting a destination is then a one-value change,
not a restructure — which is the cost ADR-0012 #5 exists to avoid in the first place.

The enum is **defined in Phase 1**, not introduced in Phase 3, or that promotion is an introduction rather
than the one-value change just described. Through Phases 1-2 everything stays visible, there being no users
to mislead. The same reasoning covers the global "+": route and nav key from Phase 1, but **no FAB renders**
until observations exist (Phase 2) — deciding the structure and rendering it are different claims, and the
app's primary write action is the worst one to teach the owner is inert.

**Back stack: one stack, and switching top-level destination replaces rather than pushes.** Back from
Weight / Observations / Care & Meds / More returns to Home; back from Home exits. Per-tab back stacks are
where Nav3 wiring turns hairy, and this app's detail screens are shallow — a weight entry, a bunny editor —
so pushing top-level destinations would only turn Back into a history tour of the bottom bar. Stubs render
the **selected bunny's name**, which is what makes the switcher's wiring falsifiable while it is still cheap
to change: a stub scoped to the wrong bunny is otherwise indistinguishable from one scoped to the right one.

The bunny switcher is app-wide state, not per-screen: a `StateFlow` on `AppContainer`, **persisted to
DataStore** so an aggressive Xiaomi background-kill lands the owner back on the same bunny rather than a
default. It is **resolved reactively against the live list of active bunnies**, so archiving or deleting the
selected bunny self-heals with no explicit event — falling back to the sole remaining active bunny, else
**"All bunnies"** (never silently auto-attributing to an arbitrary one), else the add-a-bunny empty state.
Per-bunny screens read the selected bunny; the global observation entry and the "All bunnies" list
deliberately ignore it. Because there is no "All bunnies" weight chart (below), the Weight screen shows a
pick-a-bunny prompt while "All bunnies" is selected.

Healing is **resolve-on-read, with no write-back.** DataStore holds the owner's last *explicit* choice and
the resolver renders reality against it, so archiving a bunny yields "All bunnies" while unarchiving her a
week later **restores the selection** — the considerate reading of an archive-by-mistake, and the simpler
implementation, since a write hidden inside a read path is how a `Flow` graph acquires feedback loops. A
*deleted* bunny's id is cleared from DataStore in the delete transaction rather than left dangling.

**"All bunnies" is offered only once two active bunnies exist.** For the likeliest owner in the world — one
bunny — it is otherwise a two-tap path to a Home that is a one-card dashboard and a Weight screen that
refuses to render, in exchange for nothing. A persisted "All" resolves to the single bunny when the count
drops to one, so that owner can never reach the pick-a-bunny prompt, which then exists only for owners for
whom it is a real question.

The switcher is therefore a **scope indicator first and a picker second** — with one bunny it still shows
name and avatar, saying whose data is on screen. It **always opens a menu**: the active bunnies,
"All bunnies" once ≥2 exist, and **"Add a bunny"** always. That last item is load-bearing, because there is
**no separate bunny-list screen** (below) — without it a single-bunny owner would have nowhere to add a
second. Editing a bunny stays on its profile, reached from Home.

A third selection state, **`Archived(id)`**, is entered only by tapping a bunny in the archived list
(ADR-0004). It scopes the ordinary screens **read-only** — a banner, no write actions — and is **never
persisted**: a background kill must not reopen the app into a read-only memorial, since the persistence
exists to restore *working* context. It also separates the two cases the resolver would otherwise conflate,
the selected bunny having *vanished* (heal) versus the owner having *deliberately opened* an archived one
(honour it).

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
  miniature. It is also **the bunny list** — there is no separate one. In Phase 1, before any vitals exist,
  the card carries avatar, name, age and "Lives with"; it grows into the vitals card in Phase 2. Two screens
  rendering the same rows would diverge the moment one of them gained a field.
- **Observations** becomes the **combined day-grouped timeline** across every active bunny, each row showing
  which bunnies it covered (shared observations are already multi-bunny, ADR-0008). Selecting a single bunny
  *filters* to observations that include it; here the single-bunny view is the special case and "All
  bunnies" is the natural default.
- **Weight** alone **refuses** "All bunnies" and shows a pick-a-bunny prompt, because weight is individual
  and no honest cross-bunny chart exists (above).

Three screens, three empty-selection behaviours, driven entirely by whether the underlying data is
individual (weight) or fluffle-shaped (observations) — not an inconsistency to smooth over.
