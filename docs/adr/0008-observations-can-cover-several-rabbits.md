# An observation can cover several rabbits at once

Bonded rabbits share a litter tray, so droppings frequently cannot be attributed to an individual. Forcing
every observation onto exactly one rabbit would record something untrue and then feed it to the health
warnings — one rabbit appearing to have stopped producing entirely, purely because of which name was
tapped.

An observation may therefore cover several rabbits. It is stored as one row per rabbit, linked by a shared
group id and marked as observed together, so every per-rabbit query, chart and warning stays simple while
the attribution remains honest. The app surfaces which rabbits an observation covered rather than implying
individual attribution.

Weight is unaffected — a rabbit is always weighed individually.

Rabbits that live together are put in a group, declared when adding or editing a rabbit rather than
inferred from habit. The group pre-selects them when logging and is shown as a visual cue, so the living
arrangement is visible rather than implied.

## Consequences

A "no droppings" warning is weaker for a shared observation, since an empty tray means neither rabbit
produced anything. The app must say so rather than implying it is about one rabbit.

Separating a rabbit for treatment needs no model change: stop covering both, and individual observations
resume immediately.
