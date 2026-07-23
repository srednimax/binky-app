# An observation can cover several bunnies at once

Bonded bunnies share a litter tray, so droppings frequently cannot be attributed to an individual. Forcing
every observation onto exactly one bunny would record something untrue and then feed it to the health
warnings — one bunny appearing to have stopped producing entirely, purely because of which name was
tapped.

An observation may therefore cover several bunnies. It is stored as one row per bunny, linked by a shared
group id and marked as observed together, so every per-bunny query, chart and warning stays simple while
the attribution remains honest. The app surfaces which bunnies an observation covered rather than implying
individual attribution.

Weight is unaffected — a bunny is always weighed individually.

Bunnies that live together are put in a group, declared when adding or editing a bunny rather than
inferred from habit. The group pre-selects them when logging and is shown as a visual cue, so the living
arrangement is visible rather than implied.

## Consequences

A "no droppings" warning is weaker for a shared observation, since an empty tray means neither bunny
produced anything. The app must say so rather than implying it is about one bunny.

Separating a bunny for treatment needs no model change: stop covering both, and individual observations
resume immediately.
