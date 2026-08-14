# Phase 7.5 — The interlude — ships as 1.5

**Status: planned, not started.** Boxes in [`DOD.md`](DOD.md) §6.5; this file is the reasoning. Finished
phases are in [`PLAN.md`](PLAN.md) and are not needed to build this one.

**Why a phase and not a pile of chores.** Four unrelated-looking items share one property: each is cheaper
*now* than after Phase 8, and three of them get more expensive every week they wait.

- The **tester's question** — a weight *gain* raises nothing — is new copy. Phase 8's own prerequisite
  argument applies to it unchanged: a string added after nine languages ship is nine translations and nine
  native re-reads, and this one is health copy, which is the kind a translator most needs a brief for.
- **Licence attribution** is a screen with a title and a row label. Same argument, smaller.
- The **document downsample** carries no strings at all, but `MediaFiles` re-encodes at write time and keeps
  no original, so every scan taken before the judgement is made is permanent at whatever spec was in force.
  Its cost is measured in real vet printouts, not in weeks.
- The **capture driver** is the exception that proves the ordering: it is *Phase 8's* tool, and building it
  here is what lets Phase 8 start with a canary rather than build one. It also pays two older debts —
  Phase 7's deferred Polish after set, and the Polish half of `DOD.md` §4's listing screenshots.

**What it is not.** It is not a redesign follow-up. Phase 7 closed with no confirmed defect in the redesign,
and nothing here is a repair of it.

**Decisions it leans on:** ADR-0001 (never infer from missing data; the flag's shape and constants),
ADR-0021 (the trailing-median baseline), ADR-0026 (the app observes, it never advises), ADR-0009 (what a
second Play-services dependency would cost), ADR-0020 (the media pipeline's kind-aware specs), ADR-0013
(every user-visible string is a resource in every locale — the fact the driver work is built on).

## What ships

| Item | Kind | Lands in | In the binary? |
| --- | --- | --- | --- |
| The gain signal | New functionality, per ADR-0028 | `WeightTrend.kt`, the trend flag card | yes |
| Licence attribution | New screen or section | `ui/more/` (Support is the only About-shaped screen) | yes |
| Document downsample answer | A judgement, possibly two numbers | `MediaFiles.kt` | maybe — only if it retunes |
| Capture driver: isolation, seed variants, locale | Tooling | `scripts/edge-to-edge.py` | no |
| Play contact email, received support mail | Two hand items | Play Console, an inbox | no |

## 1 — The tester's question: a gain raises nothing

Reported 2026-08-09. A bunny putting on a lot of weight — the tester's words were *"5 kg plus"* — produces
no flag, no notification, nothing. Only losses are ever raised.

**The code agrees, and it is deliberate.** `WeightTrend.kt`'s trigger is one-sided —
`current.grams <= baseline.grams - threshold` — and the baseline is the trailing median of three priors,
chosen in ADR-0021 partly so that *a lasting rise cannot park the bunny permanently above baseline and mute
every later drop*. The whole mechanism is built around loss, which is what ADR-0001 was written about: a
rabbit losing weight is the acute, hours-matter signal.

So this is not a bug to fix by deleting a minus sign. **[ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md)
is this phase's first deliverable**, written and grilled on 2026-08-14, and no trend code is written before
it is merged. **The design lives there, not here** — this section says only what building it costs.

### What the ADR settled

> A gain raises the **same flag**, judged against the single weighing **nearest to six months back** (accepted
> only within a 4–8 month window), at **+10 %** — one body-condition step on the PFMA/RWAF five-point scale.
> Silent under **12 months** of age; when `birthDate` is null it fires and **the card asks the bunny's age**,
> so an absent field is never read as adulthood. **Loss takes precedence** when both hold, and the single
> watermark is discarded whenever the displayed direction changes.

**What it costs to build**, which is why it sits in this phase rather than after nine languages:

- **Three new strings**, against four reused verbatim — `trend_flag_title`, `trend_flag_readings`,
  `trend_flag_not_advice` and `trend_flag_acknowledge` are already direction-neutral. New: `trend_flag_rise`,
  a vet-advised-gain mirror of `trend_flag_vet_diet`, and the age question. That is the whole copy footprint
  of the feature, and it is the sharpest answer to "why before Phase 8".
- **No schema change.** Loss precedence is what buys it — see the ADR. Schema stays **6**, so no migration,
  no fixture and **no `connectedAndroidTest` is owed**.
- **Two limitations to pin by test**, not to engineer around: a single high reading at the anchor suppresses
  the flag silently, and an acknowledged gain returns unacknowledged after a loss episode interrupts it.
- **One new action wired to the bunny editor** — the age question, which is the half that stops an
  unknown-age kit re-raising a caution dot after every weighing for months.

### The tester still gets an answer, and it is not the feature

Their words were *"5 kg plus"* — **a number, not a change**. A Flemish Giant is legitimately 6–10 kg, so any
absolute weight is wrong for some breed, and **the app can only ever flag a change, never a weight**. So the
honest reply is that Binky will never tell them 5 kg is too much; what it will now tell them is that the
number moved, and by how much, since a date. Writing that reply is part of this phase — the thing built is
next to their question rather than inside it, and saying so is better than letting them find out.

### How it is proven, which is the phase's call rather than the ADR's

- **Unit tests and a seed-patch device check close it** — the project's established loop, self-contained,
  waiting on nobody. The tester's own weight history was considered as a JVM fixture and **not taken**, so
  it is recorded here that *we will not know whether +10 % over six months would have fired for the bunny
  that prompted this*. That is a chosen trade, not an oversight.
- **The gain card gets a permanent scene, via seed variants in the driver** — see §4. `SampleData.kt` has
  no rising series (Phase 7 records that it pairs the trend flag with the running watch and the steady
  series with the expired one), so without a mechanism the card would be verified once by hand and then
  never seen by the harness again. The default seed is **not** changed: it is load-bearing for 61 scenes
  and for the Play listing screenshots.

## 2 — The document downsample, finally calibrated

`MediaFiles.kt` still carries the **unverified** comment on `MediaKind.Document` =
`LongEdge(maxEdge = 3000, quality = 92)`, and Phase 5's intro called this *"a deliverable and not an
assumption"*. The fixture exercises the downsample on a 3200 px page and the pinch-zoom viewer reads it
back; the judgement has never been made.

**The numbers, so the judgement has something to be against.** 3000 px on A4's long edge is ≈ **256 dpi** —
comfortably above the 200 dpi floor that fax and OCR treat as the legibility line, so the resolution is
probably not the risk. **Quality 92 on text is.** JPEG's chroma handling shows first on thin dark strokes
against white, which is exactly what 8 pt small print is, and no amount of zoom recovers it.

- Scan a **real vet printout** — not a fixture — and answer two questions: is the small print legible on the
  phone at full zoom, and what does the file weigh.
- Retune the two numbers if not, **or delete the "unverified" comment if so**, recording the observed file
  size in it. A comment that says *checked, 1.4 MB on an A4 discharge sheet* is worth more than one that
  says nothing.

This is a written answer produced on the device, not a passing test.

## 3 — Licence attribution

The app ships Room, Compose, Coil 3, Vico and ML Kit and carries **no attribution of any kind** — no string,
no asset, no screen. Apache-2.0 §4 asks for the licence and NOTICE to travel with the binary. Raised while
grilling Phase 6 and deliberately not folded into it.

**Decided 2026-08-14: `app.cash.licensee` at build time, rendered by our own Compose screen.** No ADR is
owed — nothing here amends ADR-0009, which is the whole reason this option beat the obvious one.

**Two facts moved this off the mechanism `DOD.md` §8 assumed.**

- **The obligation is over the resolved runtime classpath, not the 13 runtime entries in
  `libs.versions.toml`.** Compose alone pulls dozens of transitive artifacts and Apache-2.0 §4 travels with
  each, so generation is the only correct mechanism rather than a convenience. The test dependencies
  (`junit`, `espresso`, `androidx.test`) are **out of scope** — they do not ship. And the list cannot be
  "everything is Apache" with one boilerplate blob: `com.google.android.gms` is under the Android SDK
  terms.
- **Phase 7 changed the calculus, and §8 was written before it.** `play-services-oss-licenses` ships its
  own Activity with its own styling — a stock Google list screen would be the one visibly foreign screen in
  an app that just spent a phase building `Surfaces.kt`, `Forms.kt` and `Dialogs.kt`. That is on top of its
  real cost, which is a **second** Play-services runtime library in a project that quarantines its first one
  behind an interface precisely so it can be dropped (ADR-0009).

**Why `licensee` and not the other generator.** Jared Burrows' `gradle-license-plugin` emits a ready-made
`open_source_licenses.html` into `src/main/assets` — an HTML page for a WebView, which is the thing being
avoided. `licensee` emits **structured data**, which is what a screen built from `ListRow` and
`SectionHeader` wants, and it **fails the build when a dependency's licence is not what was declared** —
which is exactly the failure §8 named: *wrong one dependency bump later and nobody notices*. A hand-typed
list is not a third option for the same reason.

It is a **build-time-only** plugin: nothing new ships in the binary except the generated asset and the
screen that reads it.

It lands on **Support**, the app's only About-shaped screen. String cost is small and belongs before Phase 8:
the licence text itself is **not translatable** — a licence is its English text — so what the nine languages
get is a screen title and a row label.

## 4 — The capture driver: isolation first, then locale

Two halves, both carried out of Phase 7, and the order matters — the isolation bug is provable in English
today, and fixing it second would mean debugging it through a translation layer.

### Scene isolation, still unwritten

`am start -S -f 0x10008000` in `relaunch()` is correct hardening and stays, but it does **not** clear a
restored Nav3 back stack — `rememberNavBackStack` restores the last route across a force-stop, and the
docstring's claim that a relaunch lands on Home is false. `KEYCODE_BACK` is not the fix either: backing past
Home exits to the launcher and makes every following scene worse.

- **A bounded `return_to_home()`**: back until the tab bar (`"Choose which bunny to show"`) is on screen,
  then set the scope explicitly — and **fail the scene loudly** when the bound is hit, rather than tapping
  into whatever is open. A driver that cannot find Home has to say so; the 244-scene run's clean cells were
  not evidence against this case, because that run started after the 20:00 dose had already fired.
- **Do Not Disturb becomes setup and teardown, not a note in a document.** `reset_to_seeded` recreates the
  Metacam course whose 20:00 dose is minutes in the past, so a heads-up banner posts a minute or so after
  **every** seed, over Home, exactly where `SELECT_BUNNY` taps — and `AUTO_CANCEL` clears it on the stolen
  tap, so the evidence afterwards looks impossible. `cmd notification set_dnd on` suppresses it without
  touching `POST_NOTIFICATIONS`, so the reminder copy the scenes photograph stays truthful. It is a
  **phone-wide** setting, so the `off` goes in a `finally` — a crashed run must not leave the phone silent.

### Seed variants, so a scene can reach a state the default seed hides

Phase 7 paid this cost over and over — *"the seed actively hides states, and a throwaway seed patch is the
cheapest way in"* — once for the chart's single-point states, once for `8c`'s expired watch, once for the
bunny editor's absent fields. Each time the patch was written, used and thrown away, so the state was seen
once and never again.

§1's gain card is the same shape and makes the case: `SampleData.kt` has no rising series, so without a
mechanism the card is verified by hand once and then invisible to the harness forever — no matrix cell, no
screenshot, no regression coverage for a permanent feature.

**A scene may request an alternate seed; the default is untouched.** That last clause is the constraint, not
a nicety: the default seed is load-bearing for 61 scenes, for the before/after comparison and for the Play
listing screenshots, so changing it — a third bunny, or a repurposed series — moves evidence that is already
banked. This is the third rewrite of the same throwaway patch, which is the project's own signal that it
belongs in `scripts/` rather than the scratchpad.

### Locale-aware needles

`--locale` already exists on `screenshots.py` and already switches the app; every scene then fails at its
first tap, because the needles in `edge-to-edge.py`'s table are English string literals. ADR-0013 is what
makes the fix small: parse `values/strings.xml` and `values-<locale>/strings.xml`, build the map, translate
at tap time.

**The substring wrinkle is benign, and saying why is the design.** `find` is a case-insensitive *substring*
match against node labels, so a needle that is a deliberate fragment (`"What you noticed"`) can resolve to
the **whole** translated string and still match the node. So the lookup is: exact match on an English value
→ use the locale's value; else the unique resource whose value *contains* the needle → use the locale's
whole value; else **fall through to the literal**, which is what carries `Bijou` and `Metacam` through
unchanged, since sample data is identical in every locale.

**Resolve the whole table once, before the first tap.** A needle matching two resources is ambiguous and a
needle matching none may be a typo; both are worth a report at startup rather than a failure 40 seconds into
a cell. A run is ~40 s per scene per cell, so the difference between failing early and failing late is the
difference between a minute and an evening.

It belongs in `edge-to-edge.py`, where the needles live; `screenshots.py` imports that table rather than
copying it, so one fix serves both.

**What it unblocks, all at once:** Phase 7's deferred **Polish after set**, the **Polish listing
screenshots** in `DOD.md` §4 (the English set is already shot), and Phase 8's copy-length canary.

## 5 — Phase 6's two hand items

Neither is work a build can do, and neither is blocked by Play's testing count. They go **first** because
they are the oldest open boxes in the project and cost an hour between them.

- Set `binky.support@gmail.com` as Play's **per-app contact email** in *Store settings*. It is what makes
  the app, the listing and the privacy policy's *Contact* section name one inbox; the app hardcodes the
  address, so anything else there points the listing at a mailbox the app does not use.
- **Read a support mail that actually arrived** — send a bug report from the phone and confirm in the inbox
  that the diagnostics block is **visible**, not collapsed behind Gmail's signature `…`. Everything up to
  the send is already verified in both locales; only a delivered message can prove the last step.

## Decisions

- **This ships as 1.5, and Phase 8 becomes 1.6.** `release-please` derives the version from commit subjects,
  and both the gain signal and the attribution screen are honestly `feat:`. Two phases cannot both claim
  1.5, and the alternative — writing everything as `fix:` to hold 1.4.1 — would be lying to the changelog
  about work that adds functionality. `PLAN.md` and `phase-8.md` are retargeted with this file.
- **The `feat!:` ban carries over from Phase 7 and is not spent.** Nothing here breaks a schema, a backup
  format or an install. One `!` cuts 2.0 no matter what these files say.
- **No schema change**, bought by ADR-0028's loss-precedence rule rather than assumed. Schema 7 would mean
  a hand-written migration, a fixture and a `connectedAndroidTest` run; if the build finds it unavoidable
  after all, that is a reason to re-examine the design, not to absorb it quietly.

## Tests

JVM, and mostly a table. `WeightTrendTest` gains gain cases beside its loss ones:

- a rise crossing +10 % inside the window, and one that does not;
- **no reading in the 4–8 month window → no claim**, distinct from "steady";
- **a growing kit**, birthday known, silent — and the same series with `birthDate` null, firing;
- **both triggers true at once**, asserting the loss flag is what shows (the case that decided the schema);
- the **direction-flip discard**: a watermark acknowledged for one direction never judges the other;
- the two pinned limitations from ADR-0028 — a single high anchor reading suppressing the flag, and an
  acknowledged gain returning unacknowledged after a loss episode. **These are tests that assert the app
  does the wrong-looking thing**, so that a later reader finds a decision rather than a bug.

`PolishTranslationTest` keeps both locales level over the three new strings — the one test that has to pass
before Phase 8 rather than during it.

What no test holds is the downsample judgement and the delivered mail. Both are written answers.

## Gate

- **ADR-0028 merged before any trend code**, and the copy in it read against ADR-0026 and ADR-0001 line by
  line — no verdict about the rabbit, only a fact about the numbers, in grams.
- The gain flag on the device in all its states, reached through a **seed variant** rather than a throwaway
  patch (§4), so the card has a permanent scene and the default seed is untouched.
- **Schema still 6**, and `app/schemas/` unchanged. If that is false, the phase re-opens its own design.
- The downsample answer written into `MediaFiles.kt` — numbers retuned **or** the "unverified" comment
  deleted and the observed file size recorded in its place.
- Attribution reachable from Support, listing **every bundled dependency** rather than a remembered subset,
  in both locales.
- **A full English matrix run clean with the 20:00 dose live** — the case the 244-scene run never faced —
  and a Polish run reaching every scene, which is the Polish after set.
- `spotlessApply`, `assembleDebug`, `test` at each checkpoint; `lint` at the gate, holding at **0 errors and
  0 warnings**. No `connectedAndroidTest` is owed unless the schema decision above goes the other way.

## Order of work

1. **The two hand items.** Oldest, cheapest, block nothing and are blocked by nothing.
2. **The capture driver** — isolation step first, proven in English against the live 20:00 dose, then the
   locale needles proven on `pl`, the one complete locale. Shoot the Polish after set with it.
3. **The document downsample**, while the phone is already in hand and before the code churn starts.
4. **ADR-0028** — grill it, write it, merge it — then build the gain signal against a driver that is by now
   correct, so its new card state can be captured the first time.
5. **Licence attribution**: mechanism decided, then built.

The two device items sit before the two code items on purpose: they want the phone and no rebuild churn,
and the driver has to be right *before* it photographs a screen that did not exist last phase.

## When it closes

Write the results here, tick **Phase 7.5** in `PLAN.md`'s status list, and empty §6.5 of `DOD.md` along
with the sections it borrowed — §3, §5, §8, §9 and §7's driver box all close with it. Phase 8
starts from a driver that already speaks Polish.
