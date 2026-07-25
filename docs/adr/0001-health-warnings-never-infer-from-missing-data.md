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

A Watch is started by the owner and never by the app — but the **trend flag offers one**, as a "Start a
watch" action pre-filled with the default duration. The moment a drop is detected is exactly when fresh
observations are worth having, and the owner should not have to independently remember that the feature
exists. Offering is not claiming: *"worth a closer look"* is already the flag's voice, and a button that
acts on that sentence presumes less than the sentence does. Symmetrically, the auto-expiry prompt shows the
**current trend**, because "is it still dropping" is the question the owner is being asked to answer and
they should not have to go and look it up.

## The weight trend flag is derived, present-tense, and self-clearing

The trend flag — the one signal that fires without the owner pre-diagnosing — is **derived on read**, never
a stored event; the same choice as due doses (ADR-0002). It is evaluated only against the **latest
weigh-in and its trailing baseline**, so it is a live, present-tense claim ("down X since Y"), not an audit
of history. This makes editing a fat-fingered timestamp self-healing, and it means a **back-dated weight
inserted into the middle of history recomputes the *current* flag but never resurrects a flag for a past
moment** the bunny has since recovered from. A dip already recovered from is not news.

The **only** persisted piece is the acknowledgment watermark, and it is **episode-scoped**: acknowledging
stores the weight it was acknowledged at, and the watermark is **discarded the instant the raw trigger goes
false** on any weight write. Because the flag only clears when the trigger goes false, "discard on first
non-trip" is exactly "the episode ended" — so a genuinely new drop later fires from scratch, and a
months-old acknowledgment of a since-recovered episode can never silence it. A later reading re-raises the
flag when it falls **below the watermark by more than the gram noise-floor** — a tighter bar than the 5%
trigger, because a bunny already flagged *and* acknowledged must not be allowed to slide a further 5% in
silence.

The watermark is **also discarded when the weight it was taken against is edited or deleted** — weight
entries are individually correctable, value as well as timestamp. A watermark measured against a number
that no longer exists is a suppression the owner never agreed to.

## The trigger's constants

The shape above is fixed. These are the numbers, and they are **chosen now rather than left pending vet
input**: a constant labelled provisional in a document ships as whatever was typed first, because the label
does not remove itself.

- **Trigger — 5% below baseline.** The figure rabbit-welfare guidance generally treats as significant, and
  defensible without a specialist in the room.
- **Noise floor — `max(20 g, 2% of baseline)`**, proportional rather than flat. The app must serve a 1.1 kg
  Netherland dwarf and a 6.5 kg Flemish giant — a 6× range, over which 5% is 55 g at one end and 325 g at
  the other, and over which day-to-day gut and bladder variation scales the same way. A flat gram floor
  would consume most of the trigger on a small bunny and mean nothing on a large one. The 20 g absolute
  stops the floor collapsing to noise on the very smallest.

Both live as named constants in one file with this reasoning in comments. Vet input remains welcome as
later tuning; because the shape does not depend on the values, acting on it is a one-line change.

One case is an **accepted limitation, not engineered around**: a vet-directed weight-loss diet trips the
flag on exactly the intended loss, and the watermark cannot suppress a sustained managed decline. Rather
than build a diet-suppression mode — which would risk silencing a real drop that coincides with a diet, and
would need its own auto-expiry or become wallpaper — the flag's own copy names the case honestly ("on a
vet-directed diet? you can ignore these"). Managed dieting is a minority case a vet is already watching; a
mild repeat prompt is the honest cost.

## Consequences

If the owner stops logging entirely, the app stays silent — accepted deliberately. Do not "improve" this
by adding staleness-based health alerts.
