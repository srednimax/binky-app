# The weight chart's range selector is display-only

The chart carries a range selector — **30 days / 90 days / 1 year / All, defaulting to 90 days** — and it
changes **only what is drawn**. The trend flag always reads the full series.

A selector is needed because an all-time axis compresses the two- or three-week drop the app exists to
surface into a couple of percent of chart width. That is the same signal loss the gram/kilogram house rule
exists to prevent, in geometry rather than arithmetic: `−0.04 kg` hides what `−40 g` makes obvious, and an
all-time x-axis hides what a 90-day one makes obvious. A juvenile growth curve from 900 g to 2.4 kg
compounds it by setting a y-axis that flattens every adult fluctuation afterwards.

**Range never reaches the trend function**, which is fed the unfiltered series and never the chart's list.
That is what keeps the two from drifting, and it has one consequence that must not be "fixed": **the flag
can render above an empty chart.** That composition is correct — it is the app holding data the current
window does not show — and it is the one that looks like a bug, so it gets verified by eye.

## Three empty states, not two

- No weighings at all.
- A single point.
- **Weighings exist, but none fall in the selected range.** This one must say so and name the last
  weighing's date, never "no weight recorded yet" — that would be the app claiming ignorance of data it
  holds, which is ADR-0001's silence failure in miniature. It is reached on an ordinary path: weigh monthly,
  skip a quiet winter, open to a blank 90-day window. It offers one tap to *All*.

**No auto-widening.** A selector that silently overrides the owner's choice lies about its own state. The
one-tap escape is offered instead of taken on their behalf.

## The selection is not persisted

Range lives in the screen's `ViewModel` and resets to 90 days each session. Persisting it would mean an
owner who once tapped *All* lives **permanently** in the view that hides the shape of the signal, having
opted in with a single tap they have long forgotten — and 90 days is the default for safety reasons, so
re-establishing it is deliberate rather than lazy.

This is **not** the auto-widening forbidden above: that changes the range *within* a session, in response to
data, after the owner has expressed a choice. A fresh session has no choice to override. Keeping it in the
`ViewModel` rather than plain `remember` is the difference between surviving a config change (it should) and
surviving a process death (it should not) — and CLAUDE.md notes the Xiaomi kills backgrounded apps
aggressively, so this is a routine event, not a rare one.

It is also the weakest candidate for a `AppPreferences` key, which holds durable owner choices and is
deliberately kept small.

## Consequences

The charting library is accepted on behaviour, not on whether it compiles. The house rule is that the chart
**plots real timestamps, not list index**, because weighings are irregular and an index axis silently lies
about the trend — and charting libraries make categorical axes the path of least resistance. So a library
that builds but wants an index axis, where a real time axis means fighting it, is **rejected even though it
built**: a hand-rolled `Canvas` chart that plots `recordedAt` honestly beats a library chart that needs
vigilance forever to keep from lying. The requirements are the floor of what any chart library offers — one
series, four fixed windows, a handful of axis labels, no stacking and no legend — so the fallback is
genuinely available, and the Compose BOM is not moved to satisfy a chart.
