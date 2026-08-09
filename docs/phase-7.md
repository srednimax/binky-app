# Phase 7 — The redesign — ships as 1.4

**Status: sketched, not planned in full.** This file holds the shape of the phase and the decisions that
block it; the per-screen worklist is deliberately *not* written yet, because it cannot be written before
the visual language exists. The boxes that do exist are in [`DOD.md`](DOD.md) §6. Finished phases live in
[`PLAN.md`](PLAN.md), which is 3 000 lines of record and is not needed to build this one.

**Prerequisite: Phase 6 ships first.** It adds a Support screen; designing it in the old language and
then again in the new one is the same screen drawn twice.

**Nothing here blocks on Phase 5's open evidence.** No schema, no alarm, no permission, no dependency —
the same reason Phase 6 is safe to build while the overnight run and Play's count are outstanding.

**Decisions it leans on:** ADR-0001 (never infer a problem from missing data), ADR-0013 (every
user-visible string is a resource), ADR-0020 (all image writes go through the media helper), ADR-0026
(what the copy may never say).

## What ships

**The same app, easier to use.** Every feature stays, every route stays, every table stays. What changes
is the look: palette, type scale, spacing rhythm, how a list row and a card are drawn, what an empty
screen says, and how much of a screen you must read before the useful thing is visible.

**25 routes today, 26 once Phase 6's Support screen lands** — five tabs (`Home`, `Weight`,
`Observations`, `CareAndMeds`, `More`) and the editors, galleries, viewers and setup screens behind them.

The scope constraint is what makes this tractable: **no schema change, no new nav key, no new dependency,
no migration.** The existing test suite stays valid from the first commit to the last, so the phase ships
**screen by screen** rather than as one long-lived branch, and every step is independently verifiable on
the device.

## The decision that blocks everything else

`theme/Theme.kt` ships `dynamicColor: Boolean = true`. On Android 12+ Binky takes its palette from the
user's **wallpaper**, so `LightColorScheme` is only ever seen on API 26–30. A brand palette designed
today would be invisible to most of the people who install the app.

That is a fork, not a detail, and the whole phase inherits whichever way it goes:

- **Keep Material You.** The redesign becomes layout, hierarchy, density and spacing; colour is used
  through tonal *roles* (`primaryContainer`, `surfaceVariant`) and Binky looks different on every phone.
- **Own a brand.** Dynamic colour goes off, or becomes a Settings toggle defaulting off, and
  `theme/Color.kt` stops being `android create`'s `Purple40`.

**The recommendation is the second.** A listing with no reviews and no installs gets more from a
recognisable app than from wallpaper harmony, and the current state is neither — the theme is still the
scaffold's, so there is nothing to preserve. A toggle keeps Material You available for anyone who wants
it, at the cost of one preference.

**Decide this before any mockup exists.** Every other visual choice is downstream of it.

### Decided, 2026-08-06: own a brand — [ADR-0027](adr/0027-binky-owns-its-palette-material-you-is-opt-in.md)

Dynamic colour defaults **off**; Material You stays available as a Settings toggle. The argument that
settled it was not the one written above, and it is worth recording which one did.

**It is ADR-0012 that forces this, not brand preference.** That ADR's first rule buys the whole redesign:
*colours come from `MaterialTheme`, never literals — the visual pass then edits one file.* With
`dynamicColor = true`, on Android 12+ nothing reads that file. The visual pass could pick a palette, wire
it in, build, install, and see no change at all, with no error to explain why. Turning dynamic colour off
is what makes ADR-0012's promise true on a device anyone owns.

Two consequences land on this phase directly. **A full scheme is owed, not three roles** — the current
`lightColorScheme(primary, secondary, tertiary)` leaves `surface`, `background`, `outline` and every
`on*`/`*Container` at M3's baseline, which is itself purple and which most users will now actually see.
And the **toggle needs a label and help line as resources in both locales** (ADR-0013), which is the first
answer to this file's "does any string change?" question: yes, at least two.

## Why it runs before Phase 8, not after

Phase 8 is 631 translatable resources × 7 new languages ≈ 4 400 strings, each **read by a native speaker
before its language ships**. A redesign aimed at *user-friendliness* rewrites copy by definition — the
section header nobody understood, the empty state that explained nothing, the button whose label was the
reason people tapped the wrong thing.

Ship the languages first and every one of those rewrites costs seven re-translations **and** seven
re-reads. The ordering is not a preference; it is the difference between drafting a string set once and
drafting it twice.

*(If the phase turns out to be strictly pixels — not one string changed — the constraint relaxes. Do not
assume that: assume copy moves, and let it be a pleasant surprise if it does not.)*

## What the redesign may not change

"Same functionality" is easy to say and easy to lose one screen at a time. These are the rules the new
look inherits, all of them already load-bearing somewhere:

- **Weight changes are always shown in grams**, whatever the display-unit preference — `−0.04 kg` hides
  the signal that `−40 g` makes obvious. A prettier weight card that switches to kilograms is a
  regression, not a redesign.
- **The weight chart plots real timestamps, not list index.** Weighings are irregular; an evenly spaced
  chart is a better-looking lie.
- **Missing media renders as a placeholder, never a crash.** A restored backup may legitimately lack
  photos, and a new photo grid must survive that on day one.
- **All image writes still go through the media helper** (ADR-0020). A redesigned gallery that loads
  full-resolution bitmaps to look sharper blows up in memory.
- **Nothing infers a health problem from missing data** (ADR-0001), and no screen says *missed* or
  *overdue* outside Phase 4's care reminders (ADR-0026). New empty states are exactly where this rule
  gets broken — "no observations" must not become "nothing to worry about".
- **Every user-visible string is a resource, in both locales** (ADR-0013). A redesign that hardcodes
  English is a Phase 8 bug planted a phase early, and `PolishTranslationTest` is what catches it.
- **Health features observe; they never advise.**

## Where the design happens

**Claude Design** (`claude.ai/design`) — beta, included in the Pro subscription, drawing on the same
usage pool as everything else. It renders HTML/CSS, **not Compose**: nothing comes back as shippable
code, and the translation to `Modifier` chains and `MaterialTheme.colorScheme` roles is done by hand.

So it is a **mockup surface, not a codegen path**, and it is used for the part where iterating is
expensive in Compose and cheap in HTML:

- the **visual language** — palette, type scale, spacing rhythm, list-row and card treatment, empty
  states, the shape of a primary action;
- **two hero screens**, `Home` and `Weight`, drawn until they are right.

**Then stop.** The remaining 24 routes are applied in Compose directly, against the language those three
artefacts fix. Mocking a screen in HTML that will be hand-written in Kotlin anyway is work paid for
twice — and the phone, not the browser, is where "easier to use" is actually judged.

**The "before" set is an input.** Every screen captured before anything changes: it is what the design work
is a response to, and afterwards it is the only way to answer "is this better?" with something other than
an opinion.

`scripts/screenshots.py` is what takes it, and again at the gate — same scenes, same cells, so the two sets
are comparable by construction rather than by care. It imports `edge-to-edge.py`'s 61 scenes rather than
copying them: those tap sequences are the expensive asset in this repo, and two drifting copies would both
go on producing screenshots, just of the wrong screens.

Its matrix is **theme × locale** where `edge-to-edge.py`'s is **rotation × navigation mode**, and it drops
the inset arithmetic that is the other script's whole point. Portrait + gesture only, deliberately: the
gate below re-runs the orientation matrix in full, and shooting one design in four orientations teaches
nothing about the design. The first set is **light + dark, English**, 61 scenes each.

Output stays **out of the repo** — `docs/edge-to-edge/` holds two PNGs, the pair that illustrated one
finding, and that is the convention: the full run is evidence to look at, not to commit, and only the shots
that make a point get checked in.

### Where the design actually lives — read this before starting a sub-phase

Project **“Binky mobile app design”**, `748bb56e-50eb-4b44-9a9e-7b6af513d47e`:
<https://claude.ai/design/p/748bb56e-50eb-4b44-9a9e-7b6af513d47e>

A session reads it with the **`DesignSync` tool**, passing `projectId` explicitly. **`list_projects`
returns an empty list** — it filters to *design-system* projects and this is an ordinary one. That is not
evidence the project is missing; pass the id and `get_project`/`list_files`/`get_file` all work.

| File | What it is |
| --- | --- |
| `github.md` | **Read this first.** A screen map: every mockup against the repo files it lands in, plus a *Not yet drawn* list. Small, and it saves opening the big file to find out what exists. |
| `Binky Design Language.dc.html` | All the mockups. See the truncation trap below. |
| `support.js` | The generated `dc-runtime` canvas renderer. **No design content — never read it.** |
| `uploads/before/{light,dark}/` | The before set, re-uploaded into the project. |

**The trap: `get_file` caps at 256 KiB and truncates with no error.** `Binky Design Language.dc.html` is
over the cap, so it comes back cut mid-attribute and looks complete. **Check for a closing `</x-dc>`; if it
is absent, the file is partial.** The document is ordered **newest turn first** (`t7` → `t1`), so the cut
eats the *oldest* turn — which is turn 1, the design language and the two hero screens, i.e. exactly the
part a visual pass needs. Export the file to disk from the browser, or split it in the project, rather than
reasoning about what is in it. *(A read on 2026-08-08 lost the tail of `1c` onward and wrongly concluded
`Weight` had never been drawn; `github.md` says it was.)*

Structure, for reading it a piece at a time instead of whole: `<section class="dv-turn" id="tN">` per turn,
`<div class="dv-opt" id="NX">` per mockup (`1a`, `1b`, `4c2`, …). Split on the opt divs and open one.

**Solved on 2026-08-09 — export the project and slice it locally.** The exported
`Binky Design Language.dc.html` is **295 007 bytes**, so `get_file`'s 262 144-byte cap cuts it 733
bytes into `1c` — which is exactly the 2026-08-08 symptom, and puts `1d` (starts at 270 361) and
`1e` (282 279) permanently out of reach of that tool. There is no read that gets them; the export is
the only path.

Once exported, **never read the file whole** — it is ~72k tokens and almost all of it is markup.
Slice one `dv-opt` and strip the tags: that is ~1.5k tokens per frame, and the stripped form keeps
the colour and size hints that carry the spec. The byte offsets of every mockup come from one
`grep -bo 'class="dv-opt" id="[0-9a-z]*"'`, and **diffing a light frame against its dark twin** is
how to read a dark variant for almost nothing — it is a pure role swap, so the diff *is* the
information.

### Two things in the project that the brief did not ask for

- **The calendar (`7a`/`7b`) is out of scope.** The doc itself calls it “the one thing in this pass that is
  not in the app today”. This phase adds no route and no nav key — park it for a later phase.
- **The colours in the mockups are hand-picked, and the doc says they are not.** Section 1 claims all the
  roles are “generated from these by Material's tonal palette builder. Nothing below is hand-picked.” They
  are not on a tonal grid: the stated `P40` is tone 42.2 at chroma 42 against a seed of chroma 32, and the
  neutral family runs hue 94.6°/89.5°/88.9° and then 48.2° for `onSurface`. A generated palette holds hue
  constant per family and lands on exact tones.
  **So take the four seeds and generate — do not transcribe mockup hexes.** `scripts/gen_scheme.py` is that
  generator (with `scripts/hct.py`, a CAM16/HCT port); it emits `theme/Color.kt` whole, and it is verified
  against Material's published baseline scheme. Most roles land within dE 1.3 of the mockups; `primary`,
  `outlineVariant` and `onPrimaryContainer` differ visibly, and that is the accepted cost of a scheme whose
  contrast holds in both themes. Edit the seeds in that script and re-run; never hand-edit `Color.kt`.

## Order of work

1. **Capture the before set** — `scripts/screenshots.py`, on the Xiaomi. ✅ *2026-08-06.*
2. **Settle dynamic colour.** Nothing else starts first. ✅ *2026-08-06 — ADR-0027, see above.*

   *(These two are listed in this order and `DOD.md` §6 says the colour decision blocks everything. Both
   are right: the capture is a photograph of what already ships, so it depends on no decision and is the
   one task that can run alongside one. Nothing that **changes** a pixel starts before step 2.)*
3. **Fix the visual language** in Claude Design, plus `Home` and `Weight`. ✅ *2026-08-08 — see the project
   above; it went well past the two hero screens, so most routes now have a drawing to work against.*
4. **Theme commit first** — `Color.kt`, `Type.kt` and `Theme.kt` stop being the scaffold's. One commit,
   and the whole app moves at once; every screen after it is an adjustment rather than a reinvention.
   ✅ *2026-08-08.* `Color.kt` generated from the seeds, both schemes in full, 22 contrast checks passing
   in light and dark. `Type.kt` carries the scale; **Nunito is a new bundled asset** (`res/font/`, OFL in
   `docs/licenses/`) — not a Gradle dependency, so ADR-0009's quarantine is untouched. `Spacing.kt` adds
   the six steps. `dynamicColor` now defaults **off**, which is the half of ADR-0027 that ships.

   ✅ *2026-08-09 — the Settings toggle landed*, so ADR-0027 is whole: dynamic colour is now *off by
   default* rather than unavailable. `material_you` in `AppPreferences.kt`, a switch row on Settings
   hidden below Android 12 (no wallpaper palette to take, and a control that provably does nothing is
   the furniture ADR-0013 warned about), two strings in both locales.

   **One structural thing came out of it, and it is worth knowing before touching the theme again.**
   The preference has to be read *in front of* ADR-0007's gate: `BinkyTheme` wraps the schema-mismatch
   screen as well as the app, and `BinkyApplication.container` is the `lazy` that **is** the wipe
   guard — reading `container.preferences` from `MainActivity` would have forced the gate open from
   inside the screen standing in front of it. So the one `AppPreferences` now lives on the
   application and is handed *to* the container. Anything else the theme ever needs to know follows
   the same path.

   Two traps found doing it, both silent:
   - **`FontVariation.Settings` is ignored for resource fonts.** Nunito ships only as a variable font whose
     default instance is ExtraLight 200, so the app rendered *thinner* than before with no error anywhere.
     Pin the `wght` axis in an XML font resource instead — `res/font/nunito_bold.xml` is the pattern.
   - **Dynamic colour was hiding wrong role choices.** The trend flag card was `errorContainer`; with the
     brand on it became an alarm-red panel, against ADR-0026 and against the design's own apricot marker.
     Now `tertiaryContainer`, and **no screen references `errorContainer` at all** — the right end state for
     an app with no emergencies. Expect more of these as screens land: roles picked when nobody could see
     the result.
5. **Screen by screen, tab by tab**, starting with the two that were mocked.
6. **Re-capture and compare**, same routes, same locales.

## The rewrite checkpoint — every route to the new language

Step 5 of the order of work, expanded. The theme commit moved the whole app at once; each item below is an
*adjustment* against a drawing, not a reinvention, and each is its own commit that builds and installs.

**Commit rule for the whole sweep: `feat:` and `fix:` only, never `feat!:`** — see the 1.4 decision below.

Mockup ids are the `dv-opt` ids in the design project (`1b`, `4c2`, …); see *Where the design actually
lives* for how to open one without loading the whole file.

| Route | Mockups | Notes |
| --- | --- | --- |
| `Home` (bunny selected) | `1b` / `1c` | ✅ 2026-08-09. Hero. Dark is not a tint — the flag card climbs to `surfaceContainerHigh` |
| `Home` under All bunnies | `4a` / `4b` | ✅ 2026-08-09. The flag stays inside the bunny it belongs to |
| `Home`, no bunnies | `4c` / `4c2` | ✅ 2026-08-09 — **code only, not yet seen on the device**: the phone holds the sample fluffle, and emptying it to look at one screen would take the Doze run's seed with it (`DOD.md` §1) |
| Bunny switcher | `4d` | ✅ 2026-08-09. Four items; *Archived* deliberately absent |
| `Weight` + chart | `1d` / `1e` | ✅ 2026-08-09. Hero. The row lost its buttons, so **deleting moved to the editor**; the chart's points became rings filled with the card |
| Record a weighing | `6e` / `6f` | ✅ 2026-08-09. The only oversized input in the app, and the **last-five line is adopted**. Building it found a shipped bug on the screen in front of it |
| New course | `3e` | ✅ 2026-08-09. Same six fields, same words — and *Save* moved to the app bar, which is the drawing's own recommendation against its own frame |
| Record a dose | `3f` / `3g` | ✅ 2026-08-09. Stays a dialog, and fixes the rules for **every** dialog — they live in `ui/common/Dialogs.kt` |
| Trend flag card | `1b` and every card that nests it | ✅ 2026-08-09, and it moved twice. `errorContainer` → `tertiaryContainer` → **a quiet `surfaceContainer` card with a 10dp apricot dot**. The drawings are explicit: *"the flag card is the same surface as every other card — apricot arrives as a 10dp dot"*. A whole panel of colour asserts an urgency the sentence inside it disclaims. Apricot as a *fill* survives in one place only, the active watch row |
| `Observations` | `2a` / `2b` | ✅ 2026-08-09. Four decisions, all of which generalise — the scope chip, the dense fact block, the tray's own subheading, and hay for what the owner recorded |
| Record an observation | `2c` / `2d` | ✅ 2026-08-09. Fixes the form rules for *every* editor — they live in `ui/common/Forms.kt` |
| `CareAndMeds` | `3a` / `3b` | ✅ 2026-08-09. Today's doses, then the courses that generate them. The largest redraw so far: four sections of 64dp rows, three deletes moved, and the delivery caveat rewritten |
| `CareAndMeds`, no bunnies | `3c` / `3d` | ✅ 2026-08-09. One sentence in a card the size of a row, and nothing else |
| Vets + vet editor | `5a` / `5b` | ✅ 2026-08-09. One grouped card where two vets used to fill two thirds of the screen, and the fifth delete to leave a list row. The editor has no drawing and changes no string |
| Bunny editor | `4e` | ✅ 2026-08-09. Same eight fields, same order, same words. Settles one of the four open decisions: the field-absent states already ship |
| Archived bunnies | `4f` / `4g` / `4h` | ✅ 2026-08-09. The one list that keeps its buttons — *Open* leads to a read-only bunny, so there is nowhere for *Delete* to move to. `4f`'s change is the weights |
| `More` | `6a` / `6b` | ✅ 2026-08-09. Same six destinations, same copy. Six headings with paragraphs become six 64dp rows in one card |
| Backup & restore | `6c` / `6d` | ✅ 2026-08-09. Six section rules become the header rhythm, and the automatic-backup status takes the apricot dot — which settles the stale-backup marker |
| Settings, Support, Documents, Photos, Setup, Watch expiry, Schema mismatch, Reminders opt-in | **none** | `github.md`'s *Not yet drawn* list — apply the language by hand |

**`ui/common/Surfaces.kt` is where the idiom lives, and every remaining row above depends on it.**
The mockups draw the same four things screen after screen — `SectionHeader`, `GroupedCard`, `FactRow`,
`RowDivider` — so they are written once rather than re-derived per route. It also holds `CardRadius`
(20dp), `NestedCardRadius` (16dp, for a card inside a card, which is how dark stays legible where a
tint would carry no meaning) and `FabClearance`. That last one fixes a bug older than the redesign:
`Scaffold` pads content for the bars it owns but **not** for the FAB floating over it, so Home's
*Delete* button sat underneath it in the before set. Any scrolling route with a FAB owes it.

**`GroupedCardItem` was added building `Weight`, and every long list owes it.** A grouped card holding
a whole history is a *single* `LazyColumn` item, so every row composes whether or not it is on screen —
which is the one thing `LazyColumn` exists to avoid, and Bijou's seeded history is already 39 rows. It
draws the same card the other way round: each row carries the surface itself and only the two at the
ends round their outer corners. Observations, Photos, Documents and the dose history all want it.

**Three places where the drawings and the shipped app disagree, all resolved against the drawing.**
Worth knowing before reading a mockup as gospel, and consistent with the palette lesson above:
- The mockups pad card interiors and row insets at **20px**; `Spacing.kt` has no such step and its
  own rule is *"no screen invents a seventh"*. Built at `Spacing.base` (16dp).
- The mockups set card titles in **Nunito 700 at 17px**; `Type.kt` deliberately confines Nunito to
  display and headline, and `titleMedium` is the default family. Built as `titleMedium`.
- `1d` dates the chart's x-axis **"Jul 15"**, with no year. The axis keeps its short localized date
  (`7/13/26`) instead: the selector offers *All*, a history can span years, and a month and day with
  no year would be ambiguous in exactly the range the drawing never had to show.
- `2b` puts Observations' cards on **`surfaceContainerLow`**, one step below every other route's.
  Built on `surfaceContainer` like the rest. The note's actual argument is a *relative* one — *"not
  up to High … the cards are the content, so they sit quietly"* — and in the generated scheme
  `surfaceContainer` already **is** the quiet level; `High` is what it rejects, and nothing here
  takes it. Weight is a list of cards on the same tab bar one tap away, and two sibling lists on
  different container levels reads as a rendering fault rather than a decision.
- `2a` sets the attribute rows **2dp** apart. Built at [`Spacing.hair`] (4dp): the app is committed
  to a 4dp grid and the difference is a rounding error at this size.
- `3a` draws a **"+" FAB** on Care & Meds. Not built: ADR-0015 puts the global "+" on Home and
  Observations only, because it logs an *observation* and nothing on this tab is one. A drawing may
  not quietly add a route-level action an ADR placed somewhere else. (Consequently this route needs
  no `FabClearance` — nothing floats over its last row.)
- `3a` omits the **vet visits** section entirely, and `github.md` maps `5a`/`5b` to the *vet
  directory* rather than to visits. Visits are per bunny (ADR-0017) and cannot leave this tab, so
  the section is built by hand in the same idiom, below routine care and above the caveat.
- `3e` puts each field's **question underneath its own box** as help text, leaving only the example
  inside it. Built the other way round, with `FieldLabel` above: a question below its own answer
  reads as a footnote about what you just typed, and Forms.kt's *"help belongs to the control above
  it"* was written for footnotes. The amount keeps its real footnote underneath, which is what the
  rule is for, and both examples stay in the boxes as drawn.
- `3e` **omits the reminder switch**, and it should not have: it draws a course with two times set,
  which is exactly the state in which the switch shows (ADR-0003 hides it only when there are no
  times). Built inside the *Times of day* card below a divider — *"remind me at these times"* is a
  sentence about the times in that card and about nothing else on the screen.
- `3a` shows a **hay tick** on an answered dose and never draws a *skipped* one. A tick beside
  "Skipped" would read as "yes, given", so skipped takes a neutral bar in the same hay circle —
  drawn as a `Box` rather than an icon, because Compose's **core** icon set has no minus and
  `material-icons-extended` is deliberately not a dependency (ADR-0009's neighbouring argument).
- `5a` keeps **Edit and Delete on every vet row** and argues in its own note only about their
  relative weight — *"Edit is primary and Delete is onSurfaceVariant … they were equal-weight blue
  peers before"*. Built with **neither**: the row carries a chevron and deleting lives on the
  editor. That is the drawing's own argument carried through — the strongest way to stop Delete
  being Edit's peer is to take it off the row — and it is `1d`'s rule, which by this point had
  already moved five deletes and would otherwise have left the vet directory as the one list in the
  app that destroys a record from a list row. **The whole sweep is now one grammar**: a row that is
  only telling you something opens.
- `3e` puts each field's question under its own box; `4e` does the same to *Name* and then draws
  *Colour and markings* the other way round in the same card. The label goes **above** in both
  cases, which is the rule `3e` already settled — worth recording only because the drawing broke it
  inconsistently within one frame, so neither reading could be taken as deliberate.

**`Observations` added three things to `Surfaces.kt`, and every one of them was forced by a drawing
whose reasoning generalises past this route:**

- **`TagChip`** — the read-only chip. `2a` is explicit that *"symptoms are hay chips, not apricot:
  apricot stays reserved for what the app raises; a symptom is something you recorded"*, which is
  ADR-0001's line drawn in colour. Not one of M3's `Chip`s: those are all controls, carrying a click,
  a ripple and a selected state a tag has no use for. `dense` is the smaller one that rides beside a
  card title.
- **`DenseFactRow`** — `FactRow`'s 28dp twin, **with no divider**, and the distinction is about
  meaning rather than size: *"dividers separate rows that are independent of each other; these are
  not."* Four droppings facts from one moment are one answer in four parts. Photos, the dose history
  and the vet record all have blocks shaped like this.
- **`RecordButtonHeight` / `RecordButtonRadius`** — 52dp and fully rounded, moved out of
  `WeightScreen` when *Log a healthy day* turned out to want exactly the same button. Two routes is
  where a private constant stops being private.

**The scope of an entry became a chip** — `2a`'s first decision, *"'Observed together with Nugget'
becomes a hay chip in the card's header row rather than a sentence, so the scope of what follows is
legible before you read any of it."* It kept ADR-0008's three cases intact and only shortened the
copy: `observation_observed_together` → **`observation_with`** ("With %1$s"), the lone survivor's
un-named *"Observed together"* unchanged, and a solo entry showing **no chip at all** on its own
bunny's timeline — the screen already is her — but her name under *All bunnies*, where the chip is
the only thing that says whose entry it is.

**The tray facts got their own subheading**, `2a`'s third decision, and it needed **two** strings
rather than the drawing's one: *"Shared — the litter tray"* is a claim, and a solo observation's tray
is nobody else's, so it reads plain **"The litter tray"** there.

⚠️ **One layout bug, found on the device and not in the drawing.** The mockups only draw entries with
attribute rows, so nothing in them shows a bunny whose entire share of an entry is *"Looked for
symptoms, saw none"* — which the sample data has two of. The gap belongs *between* two parts of a
block, and emitting it after an empty one left that sentence sitting the same 16dp from its own
heading as from the next bunny's, belonging to neither. Which of the three parts exist is now worked
out before any of them is drawn, because a part cannot answer "am I first?" from inside itself. **The
same shape will recur on every card built from optional blocks** — Care and the vet record next.

**`Care & Meds` added four more things to `Surfaces.kt`, and the first of them is the one every
remaining list route wants:**

- **`ListRow`** — `FactRow`'s opposite number. A fact row is a label and a value the owner *reads*;
  a list row is an object they *act on*, at 64dp with a title, one fact line and a trailing slot.
  It is what `3a`'s "five changes" note is mostly about: a dose was a whole card with an internal
  divider and two text buttons, so three filled the screen, and eight fit now.
- **`Chevron`** — the trailing mark of a row that opens something, decorative to a screen reader
  because the row's own text is what it announces.
- **`CautionDot`** — the 10dp apricot marker, lifted out of `TrendFlagUi` on its second caller.
- **`CaveatCard`** — dot, title, one paragraph, at most one action, on `GroupedCard`'s raised level.
  `GroupedCard`'s parameter was renamed `nested` → **`raised`** with it: the level says *this card is
  not like the others*, and a card inside a card was only its first reason. **Spend it once per
  screen** — `3b` is explicit that it is "the only thing on this route at surfaceContainerHigh".

**The row grammar `3a` fixes, and it generalises past this route.** *A row that is asking you
something carries the answer; a row that is only telling you something carries a chevron.* The
drawing states it for doses — *Given* / *Skipped* inline, collapsing to a hay marker once answered —
and the same rule is what decides that a care reminder keeps *Done* **only while it is actually
due**. The drawings never show a due reminder, so this had to be derived rather than read; it is the
same class of gap as `2a`'s optional blocks.

**Three deletes moved off the list, and the destination did not already have one.** `1d`'s rule
applied three times: a course, a reminder and a visit are all deleted from their own screen now, not
from a 64dp row. Where `Weight`'s editor could simply absorb it, none of these three could —
`MedicationCourseScreen` and `CareReminderScreen` delete *doses* and *completions*, which are
sub-records, and `VisitEditorScreen` had no delete at all. Each needed the dialog, a
`confirmingDelete` flag and three methods; `CareViewModel` lost all three sets in exchange, along
with `PendingCourseDelete`. **Check the destination before assuming the move is free.**

**The delivery line became one caveat card at the bottom, and the armed state stopped rendering.**
`DoseDeliveryLine.kt` is now `ReminderCaveats.kt`, and it renders **exactly one** caveat, ranked:
doses channel muted → exact alarms denied → battery policy. The states are not independent — one
switch silences both channels, one battery policy delays both — so stacking every true statement
showed the same fix twice under two titles. Taking a fix reveals the next on resume, which the
`LifecycleResumeEffect` was already doing for its own reasons. The **armed** sentence is gone
entirely, which is the rule the care line already followed: a line confirming that a working app
works is reassurance an owner learns to skip, and then skips the one that matters. `doses_state_armed`
went with it, and `action_open` — the string DOD §2's needle trap was named after — is now unused and
deleted.

**`ui/common/Forms.kt` is the same bet for editors, and it came out of `2c`.** `FormSection` (a
[`SectionHeader`] over a [`GroupedCard`]), `FieldLabel`, `HelpText`, `ErrorText`, `ChipRow`, `FormChip`
and `NoteField` — the four rules the drawing writes out, written once:

- **chips wrap, they never scroll sideways.** The before set cut *More than usual* and *Strung together*
  off the right edge, so the two answers most worth recording were the two hardest to find;
- **a section is a card**, replacing the hairline rules the old forms drew on the background;
- **help text belongs to the control above it** at `Spacing.tight`, not to the field below;
- **free text is an outlined box with a placeholder**, no floating label. `minLines = 3` rather than the
  drawing's literal 88dp: a fixed height centres the caret in an empty box, where three lines of room
  start the text at the top and grow.

Chips are **36dp**, which is M3's 32dp default overridden deliberately — a wrapping grid of chips is the
primary control on these screens, not a filter bar above a list. Compose still expands the touch target
to 48dp underneath.

**`3e` added four more to `Forms.kt`, and between them they finish the editor kit:**

- **`SingleLineField`** — [`NoteField`] one line tall, same 14dp box, same no-floating-label rule.
  The bunny editor and the vet editor are made of these.
- **`ChangeableValueRow`** — *moved out of* `RecordedAtField`, where it had been private, and given an
  optional **label**. Two dates in one card need naming (*Starts*, *Ends*); a date and a time do not,
  which is why `RecordedAtField` still passes none. Its screen-reader trick is the load-bearing part:
  three buttons on one card all read *Change* and still announce which is which.
- **`SwitchRow`** — a setting, its help line and the switch, with the **whole row** toggleable and the
  `Switch` itself taking `onCheckedChange = null`. One semantics node, so a reader announces the
  setting once instead of a paragraph followed by an unlabelled control.
- **`RemovableChip`** — the third chip, and the three now divide cleanly by what they are *for*:
  [`FormChip`] is an answer you choose, [`TagChip`] a fact you read, this one an entry you remove.
  Hay-filled like the tag rather than outlined like an unchosen option, because a time on this list is
  something the owner put there. The whole chip removes, not just the ✕ — a 12dp target inside a 36dp
  one is a miss waiting to happen.

**Save moved into the app bar, and that was a decision rather than a redraw.** `3e` draws the filled
button at the foot of the scroll and then argues against it in its own notes — *"the rule worth
adopting is the observation form's, since the bottom button drifts off-screen on a long form.
Changing it is a decision for you, not something to slip in."* Taken, deliberately: back arrow plus a
*Save* text button in the bar, matching `2c`. **Every editor left in the sweep now has one chrome to
copy** — `6e`, `4e`, `5b` and the archive screens included.

**`ui/common/Dialogs.kt` is the third file of the idiom, and `3f`'s notes are explicit that it is
decided once for all of them:** 28dp radius, 24dp padding, the title at `headlineSmall` with the
subject beneath it, actions bottom-right with the confirming one last. Almost all of that is M3's own
`AlertDialog` default, which is why `BinkyDialog` is a thin wrapper and not a re-implementation.

**The one thing M3 does not give is the level, and it goes in opposite directions in the two themes.**
Light steps *down* to `surfaceContainerLow` — the scrim already separates the dialog, so it need not
shout as well — and dark steps *up* to `surfaceContainerHigh`, because it has to lift off 50% black
and lighter is the only direction available. One constant cannot say that, which is why there are two.

**That forced `LocalCardSurface`, and it is the first `CompositionLocal` in the app.** A
[`GroupedCard`] inside a dialog must sit one step *above* the dialog, or in dark it renders darker
than the thing holding it — `3g` calls the pair "the only two-level nesting in the app". Which level
that is cannot be known by the card, and the alternative was a colour parameter threaded through
`GroupedCard`, `FormSection` **and** `RecordedAtField`, only the innermost of which uses it. The
pickers are deliberately left alone: `DatePickerDialog` and the time picker are M3 components with
their own container contract, and re-colouring the frame round a picker that still paints itself
would only make the two disagree.

**The subject line is real data or it is absent.** `3f` adds *"Metacam · 0.3 ml · for the 8:00 PM
dose"* because with two doses a day the course name alone does not say which one you are answering
for. The name and the amount reuse `MedicationsSection`'s `courseTitle`, so the row you tapped and
the dialog it opened cannot name the course two different ways. The **slot** clause only appears
where a slot exists — on the *edit* path, from `dose.scheduledTime` — and never on the ad-hoc one,
where by definition no slot is being answered (ADR-0002).

**`5a` and `4e` between them finished the kit rather than extending it**, which is the first sign the
idiom has settled: two whole routes cost four small additions and no new concept.

- **`MessageCard`** moved out of `CareAndMedsScreen` into `Surfaces.kt` — `3c`'s one-sentence card,
  wanted unchanged by an empty vet directory. It is *not* Care's `EmptySection`: that is one section
  of a populated screen saying it holds nothing, and it stays plain text precisely so it cannot be
  mistaken for a row.
- **`ListRowHeight` became public.** A vet is up to four lines at three weights, so the row is drawn
  by hand and borrows only the floor — a name-only entry still matches every other row in the app.
- **`ChangeableValueRow` grew `actionLabel` and `onClear`.** *Change* is the wrong verb beside *Not
  known*, which is why `4e` writes *"Set a birthday"* out in full; and clearing needs a second button
  that reads a bare *Clear* while announcing which field it empties — the same `contentDescription`
  trick the row already played for its first button, and for the same reason.
- **`SingleLineField` grew `keyboardOptions`**, and `NoteField`'s placeholder became optional. A
  placeholder is an *example*, so it is left out where there is no useful one: a vet's notes are
  whatever that owner wants to remember, and a specimen would imply the field expects a kind of
  answer.

**Neither route needed a new section idea, and the vet editor changed no string at all** — its four
labels read as well above a box as they did inside one. The bunny editor added two,
`bunny_editor_section_details` and a generic `action_clear`.

**Check the *set* state on a screen whose sample data is all absent**, which is the inverse of the
trap `2a` found and just as easy to ship. `4e` draws a bunny with no birthday, no breed and no
colour, and the seeded Bijou has none of the three either — so both the drawing and the ordinary
capture show only the empty half of a card whose whole point is the pair of states. Driving one of
each by hand is what proved the row does not crowd at *label · value · Change · Clear*, and that the
approximate switch lands between Birthday and Breed with a divider each side rather than after both.

⚠️ **`6e` found a shipped bug on the screen in front of it, and the way it was found is the point.**
Opening a **weigh-in** reminder raised *Correct this completion* with *Delete this completion?*
stacked on top, immediately and every time, and cancelling brought them back — the screen was
unusable. `CareEventRow.id` is null on every weight-derived row (a weighing is not a completion and
has no event to name), and the state resolved its dialog flags with
`rows.firstOrNull { it.id == open.event }`, so the ordinary **no-dialog** state matched the first
row whose id was also null. One reminder type, because only a weigh-in's history holds those rows.

Two lessons, both cheap and both general:

- **A scene that reaches a route *through* another one is testing both.** This was reachable by any
  owner tapping their weigh-in reminder, and it survived a phase of device passes because
  `care-reminder` opens a reminder that is *not* a weigh-in — so the one screen with null-id rows
  was never the one under the camera. Route coverage is not type coverage.
- **A deterministic scene failure is evidence, not noise.** It failed in all four cells, which was
  read first as a driver flake and rewarded a driver "fix" that changed nothing. Reproducing it with
  a single raw tap, and then with the row scrolled to a different position, is what ruled the driver
  out in two runs. *Rule out the harness by making the harness irrelevant, not by adjusting it.*

**`RecordedAtField` moved with it**, so the weight form inherits the treatment before `6e` redraws the
rest of it: a titled card of two rows with an inset divider, and buttons reading just **Change** — the
value each sits beside already says which. The old *"Change the date"* / *"Change the time"* survive as
their `contentDescription`s, because a screen reader has no value in view to disambiguate them.

**`2c` was worth doing early even though it is not a hero screen** — done 2026-08-09, and this is why:
its own label says the form rules get fixed there, and every other editor inherits them, so doing it
after the editors would have meant doing the editors twice.

**`6a` / `6c` / `4f` are the last three drawn routes, and they cost the idiom one parameter between
them** — which is the second sign it has settled, after `5a`/`4e`. What they did add is three rules
about *when the rules do not apply*:

- **`More` has no high surface at all**, and `6b` says so out loud: *"a navigation route has nothing to
  raise"*. The one-raised-card budget is a ceiling, not a quota — a screen may spend none of it.
- **`Backup` spends it on the section the owner cannot control**, which is `6d`'s reading and the
  clearest statement yet of what the level *means*: automatic backup is a mechanism Binky can neither
  switch on nor check, and everything below it is work the owner can actually do. The **selected scope
  row** and the **photo warning** take that same `surfaceContainerHigh` as a *fill inside* a card. That
  is not a second claim to be the exception — the budget is about cards standing on the background —
  and `6d` calls it "one mechanism, reused" rather than a tint of `primary`, which is what stops the
  chosen scope needing colour recognition to be legible. (It carries `titleMedium` as well as the fill,
  so the two say the same thing.)
- **`Archived bunnies` is the one list that stays separate cards**, and `4f` states the exception
  itself: *"a block with three actions of its own is not a row"*. It is also the one list that keeps
  its buttons rather than following `1d` — *Open* leads to a **read-only** bunny (ADR-0004), so the
  destination offers no actions by definition and there is nowhere for *Delete* to move to. `4f`
  therefore fixes the weights instead: *Unarchive* is `Open`'s peer because it asks nothing and only
  restores, and *Delete* drops to `onSurfaceVariant`.

**`ListRow` gained `enabled`, and it is deliberately not the same as having no `onClick`.** A row that
is *asking* a question is also unclickable and must not dim — that is the row grammar `3a` fixed. This
is the third case, which only `More` has: a row that would open something if it could, where the title
drops to `onSurfaceVariant` and the subtitle says why. The chevron goes with the tap, so an inert row
carries none rather than promising a screen that is not coming.

**`BackupScopePicker` draws rows now, not a card**, because on `6c` those three rows share their card
with the photo warning and the *Export* button. The setup wizard, which shares the picker (ADR-0006),
supplies its own — one line, and the only change this batch made outside its three routes.

ℹ️ **No scene needle broke on any of the three**, and the reason refines the rule rather than
contradicting it. Every needle into these routes reaches into **content**: `settings`, `backup`,
`archived`, `vets`, `photos`, `documents` and `support` all tap a `More` row *by its title*, and
`more`'s own six rows are now identical 64dp merged nodes — precisely the tie-breaking hazard Care &
Meds hit. They survive because `6a` changes **no string**, and because no two subtitles contain
another row's title. **A content needle survives a redraw that is structural only**; what breaks one
is a redraw that moves or rewords the text it names.

### New functionality the designs introduce

This phase's scope is *same functionality, new looks*, so **each of these is a decision, not a task.** They
are listed because a screen redrawn from a mockup will otherwise absorb them silently.

- **A calendar route** (`7a` / `7b`). The doc concedes it: *"this is a new route, which your original brief
  ruled out"*. **Defer** — a new nav key is out of scope by definition, and it wants its own phase.
- ~~**The last-five line on Record a weighing**~~ (`6e`). **Adopted 2026-08-09**, and it is the only one of
  the four that was. It is a *guard* rather than a feature, which is what earns it a place in a phase scoped
  to appearance: `2310` and `23100` look equally plausible in an empty box, and a digit too many silently
  poisons the very series ADR-0001's flag then reports on. It reads only weighings the route already loads
  and excludes the row being edited. **Printed oldest first**, ending on the most recent reading — the
  drawing's order, and the one that reads, since the eye finishes on the number the new one will follow.
- ~~**A stale-backup marker**~~ (`6c`: *"the status line gets the apricot marker"*, and `github.md`: *"the
  same marker badges a stale backup"*). **Settled 2026-08-09 building `6c`, and like `4e` it turned out not
  to be new functionality.** The staleness rule this line said "does not exist yet" **does** exist:
  `AutoBackupStatus.Recorded.stale` is a fortnight, `backup_auto_stale` is its own shipped sentence, and
  `stale` is already what decides whether *Open Android backup settings* appears at all. So the marker adds
  no rule, no threshold and no copy — it marks the two states the screen was already going to act on
  (`NeverRecorded`, and `Recorded` past a fortnight), which is exactly the `actionable` flag that was
  already there. A **fresh** recorded backup gets no dot: nothing is being raised, and a permanent marker
  beside a working net is the reassurance an owner learns to skip, which is `ReminderCaveats`' argument
  about the armed state made a second time.
- ~~**Field-absent states in the bunny editor**~~ (`4e`: *"birthday — not known"*, *"breed — not set"*).
  **Settled 2026-08-09 building `4e`, and it was never new functionality.** The check this line asked for
  came back the good way: `bunny_birthdate_none` = *Not known* and `bunny_breed_none` = *Not set* have
  shipped with exactly those two words since ADR-0016, and the distinction they draw is real — a birthday
  is a fact about the rabbit that nobody may know, a breed is a field on a form that nobody has filled in.
  The drawing was reading the app back to itself.
- **Chips wrap rather than scroll sideways** (`2c`), and *"not checked" is a real value selected by default*.
  The second is a data-meaning claim, not a layout one — verify it matches what the observation entity
  actually stores before the UI asserts it.

**Two Weight surfaces are deliberately not drawn**, and both need the language applied by hand:

- **The chart's empty state.** `1a` specifies it — centred, pinned to 220dp so switching range never makes
  the list below jump, naming the date of the most recent weighing rather than claiming nothing exists —
  but only as a spec swatch, never on a real frame.
- **The watch-expiry sheet.**

**The inventory is now complete.** It was built from the mockups that survived the 256 KiB truncation
plus `github.md`'s summary; the casualties were **`1d` and `1e`**, the two `Weight` frames, because
turn 1 sits last in the byte stream. **Both were read on 2026-08-09** from the export, and the answer
is that they **introduce nothing beyond this list** — "first" on the oldest row, the *· from a visit*
suffix and the *39 weighings* count are all presentation of data the route already had. So the four
decisions above are the whole of it, and the only one still owed by a built screen is the last-five
line, which lands with *Record a weighing* (`6e`).

What `1d` did decide, and it is a placement rather than a feature: **the history row has nowhere to
put a Delete**, so deleting moved to the editor. That is not a loss of functionality and it is not a
new one — but it is the shape every other list-plus-editor pair in the sweep should now follow.

## Gate

- **All 26 routes visited on the device**, in both locales, against the before set.
- **4f's edge-to-edge matrix re-run in full** — both orientations, both navigation modes. A visual
  overhaul is precisely the change that matrix exists to catch, and it is the one gate here that cannot
  be argued down.
- `PolishTranslationTest` green — a redesign that adds strings adds them in **two** languages.
- `spotlessApply`, `assembleDebug`, `test` at each checkpoint; `lint` at the gate, holding at **0 errors
  and 0 warnings**. No `connectedAndroidTest` is owed: no schema change and no media path.
- **The Play screenshots owed from Phase 5 are taken after this, not before** — otherwise they are taken
  twice, and the first set is obsolete before the count clears. See `DOD.md` §4.

## Open questions

- ~~**1.4 or 2.0?**~~ **Decided 2026-08-08: this ships as 1.4.** Nothing about the data, the schema or the
  backup format breaks — only the appearance — and a major bump should mean something a user has to act on.
  A restored backup, an existing install and every migration behave identically before and after, so 2.0
  would be telling them something untrue in order to sound impressive.

  **The consequence is a commit rule, and it is easy to break by accident.** `release-please` derives the
  version from commit subjects, so **one `feat!:` anywhere in this phase cuts 2.0** — no matter what this
  file says. A redesign is exactly the work where a `!` feels earned in the moment, on the commit that
  replaces a screen wholesale. It is not earned: nothing downstream of that commit has to change. Use
  `feat:` and `fix:`, and keep the breaking-change marker for something that actually breaks.
- **Does any string change?** Assume yes (see Phase 8's ordering). Worth answering properly once the
  hero screens exist, because a "no" would let Phase 8 start in parallel.
- **How is "more user friendly" judged?** Today the answer is one person's eye. That is acceptable for a
  free app with no installs, and it should be *stated* rather than dressed up as a method.

## When it closes

Write the results into this file, tick **Phase 7** in `PLAN.md`'s status list, and empty §6 of `DOD.md`.
