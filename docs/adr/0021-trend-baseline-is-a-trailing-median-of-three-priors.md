# The trend baseline is a trailing median of three priors

ADR-0001 fixes the trend flag's *shape* — a level trigger 5 % below baseline, interval-independent,
noise-floored, derived on read — and its constants. It does not say what "baseline" means, and that choice
decides whether the flag fires at all. An estimator picked by accident is an estimator nobody tests.

The baseline is the **median of the 3 weighings prior to the current reading**, always **excluding the
current reading** so a real drop cannot dilute its own signal, and the flag **cannot fire until ≥ 2 priors
exist**. At exactly two priors the baseline is the **higher of the two, not their median**.

**Median, never mean.** One fat-fingered entry must not drag the baseline. A `250` typed for `2500` is a
normal event, and the mean hands it a third of the estimator's weight.

**Higher-of-two at exactly two priors**, because the median of an even set *is* the mean of the middle pair —
so at two priors the "median" silently becomes the most outlier-sensitive estimator in the scheme, in the
very window where the flag first switches on. It fails **silent**: priors of `2500, 250` average to 1375 g,
which puts a healthy bunny permanently "above baseline" and suppresses every later drop. Higher-of-two
matches the mean on a fat-fingered *high* prior (both fire) and fixes the low one, so it strictly dominates.
The failure it admits — a false alarm from a typo'd high prior — is self-announcing, since the flag shows
grams and dates and the value is editable.

## Max-of-three was considered and rejected

Taking the **highest** of the three priors is a real alternative, and stronger than it first looks: it wins
every *silent* failure and collapses the two-prior exception into the general rule.

| priors | median | max | |
| --- | --- | --- | --- |
| `2500, 250` (two only) | 1375 ✗ | 2500 ✓ | max makes higher-of-two the rule, not an exception |
| `2500, 250, 250` | 250 ✗ silent | 2500 ✓ | max better |
| `2200, 800, 500` (after a gap) | 800 ✗ silent | 2200 ✓ | max better |
| `25000, 2500, 2500` (high typo) | 2500 ✓ | 25000 ✗ loud | median better |
| `2500, 2450, 2550` (daily weigher) | 2500 | 2550 | max fires at 2400; median does not |

It is rejected anyway, for two reasons. It turns "5 % below baseline" into "5 % below recent peak", which on
the owner who weighs **daily** fires on ordinary gut and bladder variation — and ADR-0001 holds that a
routinely-dismissed signal is worse than none, so false alarms are not free here either. And the median's
one weakness is **temporary**: the blind window below is a single reading and self-heals on the next, whereas
the two-element mean's was *permanent*, which is exactly why that one had to be fixed and this one does not.

Because the shape does not depend on the estimator, switching to max-of-3 later is a one-line change.

## The series has a stated total order

`recordedAt` alone is **not** a total order. A minute-granularity picker, two entries in one session and the
sample-data seeder all produce ties, and without a stated rule the baseline depends silently on SQLite's row
order. The order is **`recordedAt` desc, then `createdAt` desc, then `id`**; *current* is the first row and
the priors are the next three.

**The pure trend function owns this windowing, never SQL.** If a DAO query had already chosen the three
priors, the project's heaviest tests — the back-dating cases especially — would be measuring a stub. The
full series is loaded regardless: five years of weekly weighings is 260 rows of `(String, Int, Long)`.

## A duplicate timestamp displaces a real prior, and that is fixed at entry

The commonest correction an owner makes is to re-type the number: enter `250`, watch the flag fire, enter
`2500` for the same minute. Under the total order the later-created row becomes *current* and the typo
becomes a **prior** — where the median resists it as an *outlier* but not as a **displacer**. It occupies one
of only three slots and pushes a good weighing out, silently shortening effective history for three
weigh-ins; two typos and the median itself goes.

So the entry form, on an **exact** `recordedAt` collision for that bunny, offers *replace the existing entry*
or *add a second*, defaulting to replace. That routes the common correction through an update, which
ADR-0001's discard rule already handles cleanly. Not a `UNIQUE(bunnyId, recordedAt)` constraint — it would
reject legitimate double-weighings and the seeder, and the total order exists precisely to handle ties the
owner chose to keep. Exact match only: a fuzzy window would need its own tuning constant and would nag on a
genuine re-weigh. The prompt is UI-level, so writes through the repository still produce the tied rows the
trend tests want.

### Amended at Phase 5: a weighing that belongs to a visit is not the resolver's to replace

*Replace* was written when every weighing had one owner and one editor. From 1.2 a weighing can carry a
`visitId` (ADR-0017), and a visit weighing lands at `min(noon on visitedOn, now)` — a timestamp the owner
never typed and can collide with by accident, since **noon is where every visit on that day lands**.

Under the rule above, the default action then rewrites a row the owner is not looking at. Adding a manual
weighing at an occupied timestamp *updates the row already there*, so the vet's number is silently replaced
while the row keeps its `visitId` and the visit goes on displaying a figure nobody recorded at it. Editing a
weighing onto that timestamp *deletes* the clashing row, so the visit's weighing disappears with none of
ADR-0017's stated-choice dialog and none of ADR-0004's ceremony. Neither is a drift the unique index catches:
it stops two rows claiming one visit, not one row being quietly rewritten.

So **a visit-tagged row is excluded from `replacing`**. A clash against one offers *add a second weighing* or
*open the visit*, and the destructive option is absent rather than merely not-default — the resolver cannot
be trusted to be careful about a row whose edits belong to another screen. For the same reason a visit-tagged
weighing is **read-only in the weight editor**: grams, date and deletion are the visit's, which is what keeps
the visit's re-derivation of the timestamp the single path and stops the two disagreeing.

The visit write path never prompts at all. Two visits on one day both landing at noon is intended, and the
total order above already handles the tie by `createdAt`.

## Accepted limitation: a trailing median lags a level shift by one reading

| recordedAt | grams | priors | baseline | fires? |
| --- | --- | --- | --- | --- |
| Mar | 300 | — | — | no (< 2 priors) |
| Apr | 500 | 300 | — | no |
| May | 800 | 500, 300 | 500 | no (a gain) |
| Jun, +1 yr | 2200 | 800, 500, 300 | 500 | no — nothing recent to fall from |
| Jul | 2050 | 2200, 800, 500 | **800** | **no** — a 150 g / 6.8 % drop, silent |
| Aug | 1900 | 2050, 2200, 800 | 2050 | yes |

It is not juvenile-specific: **any unlogged rise followed by a drop** does it, because two of the three
priors are still stale. It is accepted rather than engineered around — the blind window is one reading, and
the rise being unlogged is a period nobody recorded, which is ADR-0001's premise. A unit test pins the case
so it stays a known limitation instead of becoming a discovery.

**The obvious fix is forbidden: do not ignore priors older than N days.** That breaks
interval-independence outright. If all three priors are stale and get discarded there is no baseline at all,
so the app goes *silent* on an acute drop after a long gap — the single pattern ADR-0001 says must never be
dampened into silence.

## Consequences

The estimator, the ≥ 2-prior gate, the higher-of-two case and the total order all live in the one pure
function with this reasoning in comments, and its test reads as a table of cases. The kit case that makes
the noise floor bind (ADR-0001) is pinned there too, so the `max` cannot be "simplified" away.

A bunny's first two weighings can never raise a flag. Accepted: two points do not describe a trend.
