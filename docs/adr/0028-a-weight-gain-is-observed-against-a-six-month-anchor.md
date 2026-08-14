# A weight gain is observed against a six-month anchor

ADR-0001 fixes the trend flag's shape — a level trigger 5 % below baseline, interval-independent,
noise-floored, derived on read — and ADR-0021 fixes what "baseline" means. Both were written about **loss**,
which is the acute, hours-matter signal. A tester reported on 2026-08-09 that a bunny putting on a lot of
weight — their words were *"5 kg plus"* — produces no flag, no notification, nothing.

The code agrees, and it was deliberate rather than an oversight: the trigger is `current.grams <=
baseline.grams - threshold`, and ADR-0021 chose a baseline resistant to rises precisely so that a lasting
gain could not park a bunny permanently "above baseline" and mute every later drop.

**A gain now raises the same flag, judged by a different rule.** It is the same `WorthACloserLook`, the same
card and the same dot, because a redraw of the same fact in a second visual vocabulary would be a second
thing for the owner to learn about one number.

## The rule

**Anchor**: the single weighing **nearest to six months before the current reading**, accepted only if it
falls **4–8 months** back. No reading in that window means **no claim** — not a statement that the bunny is
fine (ADR-0001).

**Trigger**: the current reading is **at least 10 % above the anchor**.

**Growth gate**: silent for a bunny **under 12 months old**. When `birthDate` is null the flag fires anyway,
and **the card asks how old the bunny is** — see below, because that is the load-bearing half.

**Precedence**: when the loss trigger and the gain trigger both hold, **loss wins** and the gain waits.

## A gain cannot reuse the loss baseline, because a trailing median is a tracker

ADR-0021's baseline is the median of the three weighings prior to the current one. Its own limitation table
records that it lags a level shift by exactly one reading **and then catches up** — accepted there, because
the blind window is a single reading and self-heals.

Applied to a gain, that same property is fatal. A bunny gaining slowly drags its own baseline up behind it,
so a chronic rise is invisible to the estimator built to be resistant to rises. The rule would only ever
catch a gain fast enough to outrun the median, which is not the event anybody reported.

## Interval-independence does not carry over, and that is the central sentence

ADR-0001 is emphatic that damping the loss signal by elapsed time is forbidden: an acute drop after a long
gap is exactly as acute as one between weekly weighings. That is right, and it is right **because loss is a
level event**.

Gain is not. It only means anything per unit time — the same +10 % is gut and bladder over three days and a
condition step over six months. The existing baseline is windowed by **count** (three priors), not by time,
so a symmetrical rule would fire on "+10 % across your last three weighings" and mean two completely
different things depending on how often the owner happens to weigh: three days for a daily weigher, three
months for a monthly one. Same code, two claims, chosen by the owner's habits rather than by the rabbit.

So the gain rule is **time-anchored where the loss rule is count-anchored**, deliberately, and ADR-0001's
interval-independence is scoped to loss by this ADR rather than quietly contradicted.

## The threshold is chosen, with reasoning — not cited

ADR-0001 did not invent 5 %; it borrowed the figure welfare guidance already uses for loss. **There is no
equivalent published figure for gain**, and pretending otherwise would be the kind of dressed-up method this
project avoids stating.

What exists instead is the five-point body condition scale — the PFMA "Size-O-Meter", used by the RWAF —
whose bands are defined in percentage terms: *very thin* is more than 20 % below ideal body weight, *thin* is
10–20 % below. **One condition step is therefore about 10 % of body weight**, which is a unit a vet would
recognise.

**10 % over six months is one condition step in half a year.** Note honestly that the published bands are
stated on the *thin* side of ideal; mirroring them upward is this ADR's assumption, not a citation. The
figure is defensible and it is ours, and it is written here so it can be argued with rather than discovered
in the code.

## Growth is the hard case, and an absent birthday may not be read as adulthood

A growing rabbit gains legitimately and enormously — a kit at four months might be 1.8 kg and at ten months
2.6 kg, **+44 % over six months, entirely healthy**. Any gain rule fires continuously on a young rabbit and
is arithmetically correct every time.

`BunnyEntity.birthDate` is nullable and commonly absent: rescue rabbits arrive with no known age, and the
app's own sample bunny has no birthday. So the guard is unavailable exactly where it is most needed.

**Reading "no birthday" as "adult" is forbidden** — it raises a health signal on the strength of an absent
field, which is the move ADR-0001 exists to ban. But silence for every rescue would delete the feature for a
large share of the app's users.

**So the app asks instead of assuming.** With `birthDate` null the flag fires, and the card carries one
action — *"How old is Bijou?"* — leading to the bunny editor. The app never claims to know the bunny is
adult; it says what it sees and offers the single tap that would let it judge properly. Answering it once
switches the guard on permanently.

This matters more than it looks, because of the re-raise bar. The watermark stores grams and re-raises when
a later reading breaks through it by more than the noise floor; a growing kit clears 20 g between almost any
two weighings. Without the question, an unknown-age kit would produce a caution dot that **re-raises after
every single weighing for months** and cannot be silenced. The action is what ends that loop.

## Loss takes precedence, and that is what keeps the schema at 6

Because the two directions use different baselines, **both triggers can hold at once**: a bunny that gained
800 g over five months and then dropped 150 g last week trips both. `TrendAcknowledgmentEntity` is keyed on
`bunnyId` alone and stores one watermark, so two simultaneous acknowledgeable flags would need a second row
or a second column — a schema bump, a hand-written migration and an instrumented run, for a card that would
be saying two things at once anyway.

**Loss wins.** It is ADR-0001's premise that the drop is the signal where hours matter, and the card should
say the most urgent true thing. Nothing is lost, only deferred: when the drop resolves, the gain flag
appears if it still holds.

One clause makes the single watermark safe: **when the displayed direction changes, the watermark is
discarded.** Otherwise a loss would be judged against grams acknowledged for a gain. Direction is always
re-derivable from the series, so this costs no stored column and the schema stays at **6**.

**This is a claim about the gain rule, not a promise about the release.** Phase 7.5 went on to take a
separate schema bump for multi-valued droppings; that migration owes nothing to this decision, and the point
stands — a second acknowledgment row would have been *this feature's* cost, and it was not paid.

## Considered and rejected

**A quieter treatment than the flag** — a plain informational line with no dot, so the caution mark keeps one
meaning. Rejected: two visual treatments of one number is a second thing to learn, and a fact the app
considers worth printing is a fact worth marking.

**A median of the readings in a bucket around the anchor**, which would have inherited ADR-0021's outlier
resistance and mirrored its two-reading tiebreak (taking the *lower* of two, since for a gain it is a
spuriously **high** anchor that fails silent). Rejected in favour of the single nearest reading, which is
the version a person can hold in their head and which the card can explain. **The cost is a real silent
failure** — one high reading at the anchor, the bunny weighed in its carrier or straight after a big meal,
suppresses the flag entirely and says nothing about having done so. Pinned by a unit test, in ADR-0021's own
tradition of pinning a known limitation rather than engineering around it.

**A plateau gate instead of an age gate** — switch the rule on only once the weight has been stable for some
months, which needs no birthday and works for rescues. Rejected as harder to explain in a card, and because
the question the app asks is cheaper and self-correcting.

**Showing both flags at once.** Rejected on cost: schema 7, a migration, a fixture and an instrumented run,
for a card that would have to say two things.

**A fixed adult-weight anchor.** Rejected because a rabbit that got heavy once would carry the card forever,
and ADR-0001 holds that a routinely-dismissed signal is worse than none.

## Consequences

The flag stays one mechanism with one acknowledgment path, one dot and one title. Three new strings —
`trend_flag_rise`, a vet-advised-gain mirror of `trend_flag_vet_diet`, and the age question — against four
reused verbatim, including `trend_flag_not_advice`, which is what keeps this an observation about numbers.
`trend_flag_long_gap` does **not** appear on a gain: it earns its place on a loss because a loss is usually
sudden, whereas a gain is always measured over four to eight months, and a caveat that always fires is
wallpaper.

The card names the anchor's **real date** — *"up 500 g since 12 February"* — reusing `TrendDrop.baselineAt`,
so the 4–8 month window never makes the copy claim a span it did not measure. Changes are in grams, per the
house rule.

**Two accepted limitations, both pinned by tests.** A single high reading at the anchor suppresses the flag
silently. And an acknowledged gain interrupted by a loss episode returns unacknowledged once the loss
resolves, because the watermark was discarded on the direction change — the owner re-answers a card they had
already seen.

**One thing this ADR knowingly does not establish.** The rule is closed by unit tests and a device check
against a patched seed, not against the history of the bunny that prompted it. Whether +10 % over six months
would have fired for that tester is unknown, and is a question this decision chose not to ask.
