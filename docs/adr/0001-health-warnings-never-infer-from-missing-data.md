# Health warnings are never inferred from missing data

Observations are recorded when the owner notices something, not on a schedule, so an empty database is
indistinguishable from a bunny in trouble. We therefore only warn about a bunny's health when an
Observation explicitly records the concerning state (e.g. *no droppings seen*) — never because time has
passed without an entry. A warning that fires from silence would cry wolf during any ordinary busy week,
and a safety signal that is routinely dismissed is worse than none at all.

Chasing the owner for fresh observations happens only while a **Watch** is active on that bunny, and
that nagging is framed as a prompt about the owner's checking, not as a claim about the bunny. A Watch is
**time-boxed** — the owner sets a duration when starting it and it auto-expires, rather than persisting
until manually switched off. A nag that never ends becomes wallpaper the owner stops seeing, which is the
same dismissal failure this ADR exists to prevent; auto-expiry forces a conscious re-arm so the signal
keeps meaning something. The nag is once daily, best-effort, and satisfied by logging any observation for
that bunny that day.

## Consequences

If the owner stops logging entirely, the app stays silent — accepted deliberately. Do not "improve" this
by adding staleness-based health alerts.
