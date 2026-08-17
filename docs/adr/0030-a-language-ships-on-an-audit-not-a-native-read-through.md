# A language ships on an audit and a report row, not on a native read-through

[ADR-0013](0013-english-base-with-in-app-language-switcher.md) put every owner-visible string in
`res/values/strings.xml` and promised a translation would be read end to end before it shipped. Phase 8
turned that promise into a gate: *"a language ships when its native review passes, not when its draft
lands"*, seven times over ([`phase-8.md`](../phase-8.md)).

**Seven native reviewers were not available, and no plausible way of finding them was.** Recruiting
strangers to proofread a pet-health app for free was considered and rejected as a plan rather than
overlooked. So the gate as written had exactly one outcome: seven finished, mechanically green drafts
sitting in `translations/` indefinitely, and eight of nine markets reading an app in English.

**Decision: the seven languages ship without a native read-through.** What replaces it is not a weaker
version of the same check — it is the half of that check which does not need a native speaker, done
properly, plus a channel for the half that does.

## Why this is not simply lowering the bar

The read-through was doing two jobs at once, and they are not equally dangerous.

- **Fluency.** Wrong register, a stilted phrase, a breed name a hobbyist would spell differently. The cost
  of getting this wrong is embarrassment, it is visible to every single user of that language, and it is
  cheap for any one of them to report. Nothing about it is irreversible: a string is a one-line edit and a
  release.
- **The three rules that outrank fluency.** [ADR-0026](0026-the-app-records-doses-and-never-advises-on-them.md)
  forbids *missed* and *overdue* outside Phase 4's care reminders; [ADR-0001](0001-health-warnings-never-infer-from-missing-data.md)
  forbids inferring a problem from silence; and health copy observes rather than advises. A translation that
  breaks one of these turns a record into an accusation or a warning, in a language nobody here reads. This
  is the dangerous half, and it is the reason this phase was never a mechanical job.

The load-bearing observation is that **only the first job needs a native speaker.** The second is a bounded
checklist against a fixed list of resources: does this string blame the owner, does it claim a problem the
data does not show, does it advise. Answering it needs the *brief* and the *string*, not fluency — and it
was answerable, so it was answered.

## What was actually done instead

Three things, all complete before promotion, all on the record in `DOD.md` §7:

1. **An ethics audit** over the ~24 high-consequence resources in each of the seven languages — the care
   and dose statuses, the trend flag, the watch, the observation fields — plus a blame- and alarm-word scan
   across all 685 × 8. **Zero rule violations**, one wording changed (`observation_not_checked` in French).
2. **A back-translation and argument-role pass**: all 25 multi-argument strings, the class of single-argument
   strings that substitute a bunny's name, and the pre-inflection invariant checked against *both* hosts that
   consume it. **No drift** — no argument had quietly changed what it refers to, which is the failure
   `photo_gallery_empty_help` shipped in Polish and which no test can see.
3. **A report row in the language picker**, wired to the existing support hand-off, so the fluency half has a
   route back. This is the substantive change: the read-through was a gate *before* shipping, and what
   replaces it is a channel *after*. It is one tap from the screen where the owner chose the language, in
   their own language, and it lands in the same inbox behind the same Gmail filter as every other report
   (that filter matches a Kotlin constant, so it already covers all nine languages —
   [ADR-0013](0013-english-base-with-in-app-language-switcher.md)'s Phase 6 amendment).

Each draft's record in `DOD.md` §7 ends with *"four decisions the native read-through has to confirm"* and a
**named fallback** for each. Those do not evaporate — they become the answers waiting for the first report
that touches one. A reviewer's job was to choose between the two; a user's report now does the same job,
later and one string at a time.

## The compile-and-render check, which is why "mechanically green" was not enough

The seven drafts were promoted temporarily, built, installed and screenshotted before any of this was
committed. It is the first time any of them met `aapt2`, and it is worth recording that it found things:

- **Four navigation labels clipped** — `destination_observations` in `de`/`es`/`uk`, `destination_care` in
  `de`/`it`. A staged draft is never compiled, so no amount of green tests could have shown it.
- **`ImpliedQuantity` lint warnings** in three languages, all false positives on bare agreeing unit words.
- **Stale `DRAFT` headers** in the promoted files.

None was predictable from the draft, and none needs a native speaker to see.

## Consequences

- **Clipping is fixed by shrinking the label, never by shortening the string.** With no reviewer, prefer the
  fix that needs no vocabulary judgement. `Beobachtung` / `observación` / `спостереження` are `CONTEXT.md`'s
  concept, not a phrasing to be traded away. `Navigation.kt` auto-sizes the label (`TextAutoSize.StepBased`);
  wrapping stays rejected for the reason its comment gives.
- **The completeness gate now demands eight translations for every future English string**, with no review
  cycle behind it. `phase-8.md` rejected a `translations-pending` allowlist on a *translate once, not twice*
  argument that assumed a round of review would reword the copy. Without that round the gate is pure
  mechanical completeness, and the drafting is one pass rather than two — so the argument survives its
  premise, and the allowlist stays rejected.
- **A language can now be wrong in the field, and that is accepted.** The owner of this repo accepts that
  users will report language bugs. The exposure is bounded by the audit above: what can be wrong is
  register and idiom, not what the app claims about a rabbit.
- **This changes ADR-0013's promise, not its rule.** Every owner-visible string is still a resource in every
  locale and `TranslationTest` still keeps them level. What is retracted is *"read end to end by a native
  speaker before it ships"* — replaced by *audited against the three rules before it ships, and reported on
  by the people who read it*. A tenth language takes the same path.
- **Fluency findings arrive as ordinary bug reports** and are fixed as ordinary strings. The one thing a
  report must not be allowed to do is talk the app back across the three rules — a plausible-sounding
  suggestion is still checked against the brief, because the reporter has the fluency and the brief has the
  ADRs.

## What was rejected

- **Waiting.** Seven complete drafts sitting unshipped for an unbounded time, for a review with no plausible
  source, while the same drafts rot against every English string added after them.
- **Shipping a subset.** The languages differ in trap density, not in review risk; no principled line divides
  them, and each shipped language pays the same eight-translation gate cost anyway.
- **Paid review.** Not rejected on principle — it stays available for any language that reports enough
  trouble to earn it, which is a better trigger than doing all seven up front on the guess that they need it.
- **Machine back-translation as the gate itself.** It is in as one of three checks (above) because it catches
  argument-role drift, which is mechanical. It cannot see register, so it is not a substitute for a person —
  only for the part of a person's job that was never about being a person.
