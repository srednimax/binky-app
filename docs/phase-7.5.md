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

- **Four owner-facing findings** arrived on 2026-08-14, after the phase was drafted: the healthy day is hard
  to find, droppings are several things at once, a tray is worth photographing, and a fluffle of five
  overflows a label nobody has ever seen it in. Three of the four add copy, so the same argument applies to
  them; the fourth is the one that pays for the schema bump they need.

**What it is not.** It is not a redesign follow-up in the sense of repairing something Phase 7 drew wrongly —
that phase closed with no confirmed defect. §8 is the near miss: the housemates line is a **state the
redesign never saw**, because the seed has exactly two bonded bunnies, which is the same blind spot §4's
seed variants exist to remove.

**This phase bumps the schema to 7.** That was not true when it was drafted, and it is the single biggest
change to its shape: §7 needs a join table and a media link, so a hand-written migration, a schema fixture
and a `connectedAndroidTest` run come with it. Every other item still costs nothing on that axis —
**ADR-0028's "the schema stays at 6" is a claim about the gain rule, and it remains true**; the release
around it simply carries a bump for unrelated reasons.

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
| The healthy day behind the `+` | Reworked entry point | `Navigation.kt`, `ui/observations/` | yes |
| Droppings: multi-valued, photographable | New functionality, **schema 7** | `ObservationEntity.kt`, `media/` | yes |
| The housemates line at five bunnies | Layout + one plurals entry | `ui/bunny/BunnyLabels.kt` | yes |

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

## 6 — The healthy day belongs behind the `+`

Reported 2026-08-14: *logging a healthy day is not intuitive; the owner should just go by the `+` button.*

**The code says the same thing.** The `+` is a single app-level FAB (`Navigation.kt:365`) that opens the
**full** observation form. *Log a healthy day* is a separate action rendered **inside the Timeline** on the
Observations screen. So the app has two ways to record that you looked at your rabbit, they have no visual
relationship, and **the discoverable one is the long one** — which is backwards, because the healthy day is
the path meant to be taken most often.

It matters more than a misplaced button, because of what the healthy day is *for*: ADR-0001's whole
position is that silence means nobody looked, and the one-tap shortcut is how an ordinary day stops being
silence. A shortcut nobody finds does not do that job. It is also what settles a running watch — `Each
morning Binky asks whether you have checked on them… a healthy day counts` — so the least discoverable
action in the app is the one that answers its most repeated question.

**The recommendation, to grill:** the `+` becomes the single entry point and offers the two paths as
siblings — *"Everything was normal"* and *"Record what you saw"*. It costs the healthy day one tap, and
that is the trade being made deliberately: one tap is cheap, and being unfindable is not. The alternative
worth arguing for is putting the shortcut **at the top of the observation form** instead, so there is one
button and one screen, and the fast path is the first thing on it.

Whichever wins, **the two entry points collapse into one**. No schema, and no new strings if the existing
`healthy_day_action` copy moves rather than being rewritten.

## 7 — Droppings are several things at once, and worth a photo 🔴 this is the schema bump

Two reports, one shape. **ADR-0029 is owed before either is built**, and it should be grilled the way
ADR-0028 was.

**Multiselect.** `droppingsForm` is a single nullable column over `ROUND, MISSHAPEN, STRUNG_TOGETHER, SOFT,
DIARRHOEA`. A tray genuinely holds more than one kind at once — round pellets *and* soft ones is the
commonest early sign of a gut going wrong — and today the owner must pick one and file the rest as prose,
which is precisely what the enum exists to prevent: *"a form that can be counted over time is worth more
than prose."* **The current model forces a lie on the exact field it was built to make countable.**

**Multiselect fits the existing model rather than straining it.** The droppings fields are already
*tray-level* — `single per group, identical across every row, edited group-wide` — so nothing has to
attribute which bunny produced which pellet, which is ADR-0008's premise and the reason a shared tray is
modelled the way it is.

**The recommendation: form and size become multi-valued, amount stays single.** `FEW` *and* `MANY` is a
contradiction about one tray; *small and normal* and *round and soft* are both things an owner can actually
see. Splitting the three fields by whether they describe a quantity or a mixture is the distinction the ADR
has to make, and getting it wrong in the other direction — multiselecting amount — would make the field
meaningless rather than merely awkward.

### The vocabulary is incomplete, and one value is a trap

Checked 2026-08-14 against veterinary guidance and against the app's *other* vocabulary. What is covered is
covered well — `NONE` amount is the stasis emergency, and **`SOFT` and `DIARRHOEA` are separate values**,
which matches the sources and avoids the classic owner error of filing uneaten caecotrophs as diarrhoea.
`symptom_dirty_bottom` already covers the smeared-caecotroph presentation, so that is not a gap.

**`STRUNG_TOGETHER` silently absorbs a different sign, and that is worse than a gap.** The value means
strung *with fur* — moulting. Mucus presents identically: thick pale goop strung between the pellets, often
enclosing them. An owner seeing mucus would reasonably pick the fur value, and the app would record moulting
where the sign was gut irritation. **Mucus needs its own value because the existing one is a trap for it**,
which is the same argument the entity doc already makes for `STRUNG_TOGETHER` existing at all.

**Blood is absent, and the absence is asymmetric.** The seeded symptom list carries
`symptom_blood_in_urine` — and red rabbit urine is usually harmless porphyrins. **There is nowhere in the
app to record blood in droppings**, which is the one that is always serious. The app has a field for the
false alarm and none for the real one.

**Decided 2026-08-14: close the gaps with values, add no new field.** `DroppingsForm` gains **`MUCUS`,
`BLOOD`, `VERY_DARK`** (melena reads fine as a form value — *"very dark, tarry"*), **`DOUBLED`** (fused
pellets are specifically a slowing-motility sign, not general misshapenness) and **`DRY`** (dehydration,
today only inferrable from `SMALL`). `Cecotropes` gains **`EXCESS`**, which its own doc already anticipates.

**A colour field was rejected on cost, not on tidiness.** The observation form already carries droppings ×3,
caecotropes, appetite, mood, activity, water, note and symptoms — and §6's one-tap healthy day exists
*because* a long form deters logging, which is ADR-0001's silence problem wearing a UI costume. Values on a
multiselect cost nothing on screen when unused; a colour row costs height on every observation forever, to
carry *pale* and *greenish* — the two weakest and most ambiguous signals in the set. They stay out.

Adding values is safe on the data axis: enums are stored **by name, never ordinal**, so a sixth
`DroppingsForm` value cannot rewrite history — and §7 is already opening this field.

**The line ADR-0029 has to hold:** several of these values are alarming by nature, and **the app records them
without commenting**. No per-value urgency copy, no "see a vet now" attached to `BLOOD` — that is advice, and
ADR-0026 forbids it. The register is the trend flag's: state the fact, and let the existing *"not a
diagnosis; if you are worried, ask a vet"* do the rest.

**The photo.** Observations carry no media today. A dropping photo is tray-level like the rest, so it
belongs to the observation *group*, not to a bunny, and it goes through `MediaFiles` per the house rule —
which raises a genuine spec question this phase is already equipped to answer: **pellet shape is closer to
`Document`'s "small detail matters" than to `Photo`'s gallery spec.** Answer it beside §2's downsample
judgement; it is the same kind of judgement made on the same phone in the same sitting.

The justification is ADR-0026's line read forwards: a photo of what you saw is **observation, not advice**,
and it is the single most useful thing an owner can hand a vet about a gut problem that has already changed
by the time of the appointment.

**Cost, stated plainly.** A join table for the multi-valued fields and a link for the photo — one migration
covering both, **schema 7**, a schema-7 fixture, and the `connectedAndroidTest` run every other item in this
phase avoids. That is the price of the two, and it was accepted knowingly on 2026-08-14.

## 8 — A fluffle of five, or two long names

Reported 2026-08-14, and it is the one item here that is a **defect rather than a feature**.

**The grammar is already right.** `joinNames` (`BunnyLabels.kt`) builds from the right through string
resources — `"Thumper, Clover & Hazel"` — precisely so other languages can punctuate lists their own way,
so five names come out correctly in every locale.

**The layout is not.** Both Home render sites (`HomeScreen.kt:210` and `:443`) and the archived list
(`ArchivedBunniesScreen.kt:170`) draw the label as a plain `Text` with **no `maxLines` and no `overflow`**.
A five-bunny fluffle therefore produces a two- or three-line subtitle on every profile card and every
switcher row, and two long names do it with just two. Nothing clips — the card simply grows, and the row
rhythm the redesign spent a phase establishing goes with it.

**Nobody has ever seen it, and that is the interesting part.** The seed has exactly two bonded bunnies with
short names, so no capture, no matrix cell and no before/after shot has ever contained this state. It is the
same blind spot as §1's gain card, which makes the five-bunny fluffle the **second customer for §4's seed
variants** — and the argument that they belong in the driver rather than in a throwaway patch.

**The recommendation: cap the names, not the pixels** — *"Lives with Thumper, Clover & 3 others"*. An
ellipsis through a name is unreadable, and `maxLines` alone would truncate mid-word in a label whose whole
job is naming individuals. Costs one `plurals` entry in both locales, which is exactly the sort of thing
worth spending before nine languages rather than after.

**It gets worse in Phase 8, which is the other reason it is here.** Every locale's *"Lives with"* is longer
than English's, so this label is a copy-length canary — and §4's locale-aware driver plus a five-bunny seed
variant is the pair that would photograph it.

## Decisions

- **This ships as 1.5, and Phase 8 becomes 1.6.** `release-please` derives the version from commit subjects,
  and both the gain signal and the attribution screen are honestly `feat:`. Two phases cannot both claim
  1.5, and the alternative — writing everything as `fix:` to hold 1.4.1 — would be lying to the changelog
  about work that adds functionality. `PLAN.md` and `phase-8.md` are retargeted with this file.
- **The `feat!:` ban carries over from Phase 7 and is not spent — and the schema bump does not spend it.**
  A migrated schema is not a breaking change: ADR-0023 is why the migration is hand-written, an existing
  install upgrades in place, and a restored backup still restores. Nothing downstream of these commits has
  to change, which is the test. One `!` cuts 2.0 no matter what these files say.
- **Schema 7, for §7 alone.** The multi-valued droppings fields need a join table and the tray photo needs
  a link; one hand-written migration covers both, per ADR-0007. Every other item in this phase is
  schema-neutral, and **ADR-0028's "the schema stays at 6" stays true as a statement about the gain rule** —
  it was never a promise about the release. The bump is what buys the `connectedAndroidTest` run back into
  the gate.

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

**Instrumented, owed by §7's schema bump alone.** `MIGRATION_6_7` from a schema-6 fixture at API 26/34/36,
asserting that **an existing single droppings value survives as one row in the join table** — a migration
that silently drops it would erase exactly the history ADR-0023 stopped the database being disposable for.
Plus the DAO round-trip for a multi-valued field and for an observation carrying a tray photo.

**JVM, for the rest.** The multi-valued fields keep their `TypeConverter` discipline — stored **by name,
never ordinal** — and a test pins that adding a sixth `DroppingsForm` value cannot rewrite history.
`housematesLabel` gets a table: one, two, three, five and nine housemates, an archived one among them, and
a name long enough to prove the cap fires on width as well as on count.

`PolishTranslationTest` keeps both locales level over every new string — three for the gain signal, the
droppings additions, and the `plurals` entry §8's cap needs. It is the one test that has to pass before
Phase 8 rather than during it.

What no test holds is the downsample judgement, the droppings media spec and the delivered mail. All three
are written answers.

## Gate

- **ADR-0028 merged before any trend code**, and the copy in it read against ADR-0026 and ADR-0001 line by
  line — no verdict about the rabbit, only a fact about the numbers, in grams.
- The gain flag on the device in all its states, reached through a **seed variant** rather than a throwaway
  patch (§4), so the card has a permanent scene and the default seed is untouched.
- **Schema 7 frozen and tagged**, with `MIGRATION_6_7` hand-written and the schema-6 fixture migrating to 7
  in CI at API 26/34/36, beside the schema-4 and schema-5 fixtures that already do. **A restored schema-6
  backup opens with its droppings intact** — the single-value column becomes one row in the join table, and
  a migration that quietly drops the old value would erase history ADR-0023 exists to protect.
- `connectedAndroidTest` green on the Xiaomi — owed this phase, unlike every other item in it. Note the
  HyperOS split-install trap in `CLAUDE.md` before assuming a failed run means broken code.
- **The housemates label at five bunnies and at two long names**, seen on the device through a seed variant
  — the state that has never been photographed.
- The downsample answer written into `MediaFiles.kt` — numbers retuned **or** the "unverified" comment
  deleted and the observed file size recorded in its place.
- Attribution reachable from Support, listing **every bundled dependency** rather than a remembered subset,
  in both locales.
- **A full English matrix run clean with the 20:00 dose live** — the case the 244-scene run never faced —
  and a Polish run reaching every scene, which is the Polish after set.
- **One entry point to record a day**, not two — the healthy day reachable from the `+` and nowhere else,
  checked in both locales.
- `spotlessApply`, `assembleDebug`, `test` at each checkpoint; `lint` at the gate, holding at **0 errors and
  0 warnings**.

## Order of work

1. **The two hand items.** Oldest, cheapest, block nothing and are blocked by nothing.
2. **The capture driver** — isolation step first, proven in English against the live 20:00 dose, then the
   locale needles proven on `pl`, the one complete locale. Shoot the Polish after set with it.
3. **The two spec judgements in one sitting**, while the phone is already in hand and before the code churn
   starts: §2's document downsample and §7's `MediaKind` for a tray photo. Same question, same page, same
   pinch-zoom.
4. **ADR-0029, then the droppings work** — the long pole, and deliberately not last. Schema 7, the
   migration, the fixture and the instrumented run are the only things here that can fail in a way that
   costs days, so they go early enough to fail with time left.
5. **ADR-0028** — grill it, write it, merge it — then build the gain signal against a driver that is by now
   correct, so its new card state is captured the first time.
6. **The two cheap ones together**: §6's entry point and §8's housemates cap. Both change what scenes see,
   so they land after the driver and before the final matrix run.
7. **Licence attribution**: mechanism decided, then built.

The device items sit before the code items on purpose: they want the phone and no rebuild churn, and the
driver has to be right *before* it photographs states that did not exist last phase — of which this phase
now has three (the gain card, a five-bunny fluffle, a tray photo).

## When it closes

Write the results here, tick **Phase 7.5** in `PLAN.md`'s status list, and empty §6.5 of `DOD.md` along
with the sections it borrowed — §3, §5, §8, §9 and §7's driver box all close with it. Phase 8
starts from a driver that already speaks Polish.
