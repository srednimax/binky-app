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
   the six steps. `dynamicColor` now defaults **off**, which is the half of ADR-0027 that ships;
   **the Settings toggle is still owed** — a key in `AppPreferences.kt`, a Settings row, and two strings
   in both locales.

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

- **1.4 or 2.0?** `release-please` derives the version from commit subjects, so a single `feat!:` would
  make this 2.0. Nothing about the data, the schema or the backup format breaks — only the appearance —
  so 1.4 is the honest reading. But a full visual overhaul is the one moment where a major bump says
  something true to a user looking at a changelog. Decide at the release, not now.
- **Does any string change?** Assume yes (see Phase 8's ordering). Worth answering properly once the
  hero screens exist, because a "no" would let Phase 8 start in parallel.
- **How is "more user friendly" judged?** Today the answer is one person's eye. That is acceptable for a
  free app with no installs, and it should be *stated* rather than dressed up as a method.

## When it closes

Write the results into this file, tick **Phase 7** in `PLAN.md`'s status list, and empty §6 of `DOD.md`.
