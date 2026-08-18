# Phase 9 — Ship it — ships as 1.7

**Status: opened 2026-08-18. Closed testing has ended and production access is applied for — Google's
decision is pending**, which puts 9h and 9i behind it and leaves everything else free to run (see 9h).
Boxes in [`DOD.md`](DOD.md) §10; this file is the reasoning. Finished
phases are in [`PLAN.md`](PLAN.md) and in `phase-6/7/7.5/8.md`, and are not needed to build this one.

**This is not a build phase.** Every phase through 8 is closed, `v1.6.0` is tagged and schema **7** is
frozen — and none of it has reached an owner's phone, because the tracks are still on **1.0.0 / 1.3**.
What Phase 9 does is close the evidence Phase 5 never finished, do the Console sitting that has been
blocked behind Play's count, and fix the two things a first-time visitor actually meets: a 404 where the
project's front door should be, and a fluffle line that names two bunnies out of five.

**It ships as 1.7 rather than 1.6.1** because of exactly one `feat:` — 9f. Everything else is evidence,
documents and Console work, which `release-please` correctly declines to version.

**Schema stays at 7.** No entity changes, no `MIGRATION_7_8`, so the standing gate at the top of
[`DOD.md`](DOD.md) does not fire in this phase. That is deliberate: the release that finally crosses
1.0.0 → 1.7 on a real owner's phone should carry no migration of its own, so if anything goes wrong the
suspect list is the three migrations already proven rather than a fourth written the same week.

**Decisions it leans on:** ADR-0003 and ADR-0025 (what 9a is testing), ADR-0009 (the Play distribution
this phase exists to complete), ADR-0023 (the launch gate the upgrade proof walks through), ADR-0004 and
ADR-0008 (what 9f is showing), ADR-0030 (why nine listings ship without a native read-through).

## The three things that must not be reordered

Most of this phase is independent work that can be done in any order. Three edges are not:

1. **9a before 9b and 9c.** Both disturb the armed course, and the run cannot be repeated cheaply — it
   costs a night, unplugged, and the permission state has already reverted under it once.
2. **9f before 9g.** 9g photographs the app in nine languages. Shooting a screen you are about to change
   is how the 1.0 screenshots became stale, and this file is written by the project that already paid
   that once.
3. **9g and the listing copy before 9h, and 9h before 9i.** The upgrade proof needs an update that
   *arrives from a track*, so it is downstream of the upload by construction — the installed Play build
   is Play-signed and refuses a local APK on signature mismatch.

Everything else — 9d, 9e, 9j — can be done whenever.

## 9a — The overnight Doze run 🔴 blocking

5a's outcome, the first bullet of Phase 5's gate, and the oldest open item in the project. The 4→5 Aug
night fired on the **best-effort** path because the permission had reverted under it, so the question the
three outcomes were written for is still untouched.

Pre-flight, reading and the three outcomes are in [`DOD.md`](DOD.md) §1 and are not duplicated here.
The two things worth stating as reasoning rather than as steps:

- **`window=0` and `whenElapsed == maxWhenElapsed` is the only pre-run proof of which mechanism is
  armed.** The 4→5 Aug run could tell only from the appop the next morning, which is too late to do
  anything about. Read the pair before going to bed or the night is spent on an unknown.
- **The permission is granted through the app's own deep link**, not `appops set`. That path is what is
  under test — ADR-0003 says a denied exact alarm degrades to best-effort and the app says so in words,
  and the deep link is the sentence the owner is offered.

**Trap:** never run `connectedAndroidTest` after arming. `am instrument` force-stops the package, which
cancels every alarm it placed, and the result is indistinguishable from a broken rebuild.

## 9b — The gate items parked behind it

Seven, all in [`DOD.md`](DOD.md) §2. They were parked deliberately: each writes to the armed course or
force-stops the app, and doing any of them first costs the night 9a is waiting for.

The one with a consequence beyond a tick is **reboot twice, autostart granted and autostart denied**.
Whatever the denied run says is what ADR-0025's self-heal consequence gets reworded to — on this phone,
without autostart the ROM does not start the process for a broadcast at all, so "the alarm is rebuilt
from truth at boot" may be a claim this device cannot keep.

## 9c — The edge-to-edge matrix, 73 scenes

The re-run `scripts/edge-to-edge.py` has owed since Phase 7.5 added twelve scenes. It is also the first
run in which **the `empty` suite has ever genuinely been shot in landscape**: all six of its scenes wipe
as their first step, `pm clear` cost the rotation, and HyperOS wrote `user_rotation` back to 0 — so the
setup wizard has been photographed in portrait under a landscape filename, 53 times per cell, passing
every time. `wipe()` re-pins the rotation now and the per-config re-seed runs before `apply_config`.

**A cell that cannot fail is not evidence.** Four scenes' existing PNGs should be assumed wrong rather
than re-read — `watch-expiry`, `medication-course`, `medication-course-bottom`, `record-dose` — for the
reasons recorded in [`DOD.md`](DOD.md) §2.

**Expect one broken needle per route 9f touches.** Five routes' worth of evidence says the same thing:
a needle on *chrome* survives a redraw of what the chrome contains, and only needles that reach into
*content* are fragile. 9f adds a tap target to content on Home, so `home` and its siblings are where to
look first.

## 9d — Close Phase 5

Write 9a's and 9b's results into [`PLAN.md`](PLAN.md)'s 5a / 5i / 5j entries and tick **Phase 5** in the
status list. It has been the one unticked box since 2026-08-05 while four later phases closed around it,
which is confusing to read and will be more confusing in six months.

## 9e — The front door

`docs/` is served by GitHub Pages from `main`, and **has no `index.md`**, so the site root is a 404.
Probed 2026-08-18:

```
https://srednimax.github.io/binky-app/                      404
https://srednimax.github.io/binky-app/privacy-policy.html   200
https://srednimax.github.io/binky-app/PLAN.html             200
https://srednimax.github.io/binky-app/DOD.html              200
```

Nothing is broken — Play's privacy-policy link has always worked and still does. There is simply no page
at the root, and the root is what anyone types.

**Two findings come out of that probe, and the second is the one worth writing down.**

- **`_config.yml`'s comment is factually wrong.** It says a Markdown file without YAML front matter is
  "copied verbatim rather than rendered", and gives that as the reason `PLAN.md`, `RELEASING.md` and the
  ADRs are safe to leave in a published directory. They are not copied verbatim: GitHub Pages injects
  default front matter, so every `.md` in `docs/` renders as a themed, crawlable HTML page. The repo is
  public, so nothing leaks — but the *stated reasoning* is untrue, and a comment that explains why
  something is safe is exactly the kind that gets trusted rather than re-checked. Correct it; do not
  delete it.
- **A landing page is worth more than the 404 it fixes.** Play's listing has an optional **Website**
  field, currently empty, and this is the URL for it: what Binky is, the privacy policy, the support
  address, a link to the repo. It is also the page a reviewer lands on if they go looking.

Scope: one new `docs/index.md` with front matter, one corrected comment. Not a site.

## 9f — Seeing the whole fluffle

The one piece of new code in the phase, and the one owner-facing gap Phase 7.5's cap left behind.

`housematesLabel` names **two** housemates and folds the rest into "& N others"
(`BunnyLabels.kt:60`, from four up). The cap is right — it was written because the line grew the card
without bound, and `maxLines = 2` sits behind it as the backstop. But with five housemates the owner
**cannot see who three of them are, anywhere in the app**. The information exists and is unreachable.

**The affordance goes on the profile header, not on the list card.** The line renders in three places
and they are not symmetric:

| Where | Today | 9f |
| --- | --- | --- |
| Home, single bunny (`HomeScreen.kt:212`) | inert text under the name | **tap → sheet** |
| Home, all bunnies (`HomeScreen.kt:457`) | the whole card is one click target that switches bunny | unchanged — a tap is already spoken for |
| Archived bunnies (`ArchivedBunniesScreen.kt:171`) | inert text | unchanged — a list of the archived is not where you go to navigate a fluffle |

**A modal bottom sheet, not a tooltip**, and the reasoning is not style:

- M3's `TooltipBox` / `RichTooltip` is long-press-only on touch, so the affordance is invisible; it
  dismisses on any touch elsewhere; it cannot scroll when a fluffle is eight strong; and it cannot be
  **tapped through** to the bunny, which is the thing the owner actually wants once they can see the
  names. It would also be the one element in the app the capture harness cannot photograph, so it would
  ship with no screenshot evidence in any of the four configurations.
- Expanding the line in place is cheapest and re-introduces exactly the unbounded card growth the cap
  was written to stop.

So: tapping the line opens a sheet titled *Lives with*, one row per housemate — avatar, name,
`(archived)` where it applies — and tapping a row switches to that bunny through the switcher's existing
navigation. A truncated label becomes a working piece of navigation rather than a longer string.

**Aim for zero new strings.** The sheet title is `R.string.bunny_lives_with_label` and the archived
suffix is `R.string.bunny_archived_name`, both already translated in all nine languages. A phase that
adds no English string owes the translation gate nothing. If one turns out to be needed, it ships in all
nine — completeness is a merge gate, not a test.

**The archived-first fold in `capHousemates` stays.** The sheet is not the line: it shows everyone, in
the order they arrived, and the archived ones are marked rather than sunk.

## 9g — Nine locales of screenshots

~2 h of device time through `screenshots.py --locale <tag>`, re-proved across every shipped locale on
2026-08-18 — Brazilian Portuguese included, which is the one that crashed in both spellings until the
guard moved to where the tag is taken.

**English is a selection, not a run**: the 63 scenes in `~/binky-screenshots/phase-7/after/` are already
the final screens. The other eight are real captures.

Play falls back to the default listing's screenshots for a locale that has none, so this improves the
listing rather than unblocking it — which is exactly why it was never done before the tracks could carry
the build it describes.

## 9h — The Console sitting

Everything in [`DOD.md`](DOD.md) §4, in one sitting, in that order.

⚠️ **[`DOD.md`](DOD.md) §4 said "upload 1.3", and that went stale four releases ago.** It was written
when 1.3 was the tip. The build that goes up is **1.7**, and every downstream claim moves with it —
most importantly the field upgrade proof, which retargets from 1.0.0 → 1.3 to **1.0.0 → 1.7** and now
crosses `MIGRATION_4_5`, `MIGRATION_5_6` **and** `MIGRATION_6_7`. Uploading any intermediate version
first spends a release cycle to prove nothing 1.7 would not.

**The 12-testers / 14-day count cleared on 2026-08-18**, and the production-access request went in the
same day. What is now waiting is **Google's decision**, which is still Console state and still the one
item nothing in this repo can move — but it is a different state from the one this phase was planned
under, and it responds differently.

⚠️ **The review is not a freeze on the repo, and it is a freeze on the listing.** Everything in Phase 9
except the paste carries on; 9a in particular should be started immediately, because it costs a night and
is the item most likely to still be open when the decision lands. The listing and the screenshots are the
exception: `store-listing.md`'s copy describes **1.6** scope and the closed track holds **1.3** — no
redesign, no multi-valued droppings, seven of the nine languages absent — so pasting nine localised
descriptions now would put a feature list in front of a reviewer for a build that does not carry it. The
standing rule meeting the worst possible week to break it.

**If the answer is *reject*, write the stated reason down verbatim before acting on it.** The reason
determines whether the fix is Console work or app work, and paraphrasing it is how a re-application ends
up answering a different question than the one asked.

**The listing and the build go up together.** That is the only rule `store-listing.md` has ever had, and
it now points the other way from the way it used to: the copy describes 1.6-scope features, and putting
it on a track still serving 1.0.0 is a listing violation rather than a rounding error.

## 9i — The field upgrade proof

1.0.0 → 1.7 on the Xiaomi, arriving from a track, with real bunny history intact. The Play build on that
phone is **1.0.0**, not the 1.0.1 that 4h assumed, so the chain crosses all three hand-written
migrations and the launch gate that ADR-0023's Phase 7.5 amendment rewrote.

**This is the standing gate's item 5**, the one no test can satisfy, on the release it matters most for.
Both live upgrade paths were watched on the phone at 7.5 — 1.4.0 → 1.5 and the skipped-version
1.1.0 → 1.5, zero differing rows on common columns both times — but neither of those was a *Play*
delivery to an install carrying an owner's real records.

## 9j — The tester's reply

Owed since 2026-08-09 and it is not the feature. Their *"5 kg plus"* was a **number, not a change**: a
Flemish Giant is legitimately 6–10 kg, so any absolute weight is wrong for some breed, and Binky will
never call a weight too high — only say that it moved, by how much, since a date. ADR-0028 shipped the
gain signal they were actually asking for; the reply is the part that explains why the app will not do
the thing they literally asked for, which is better said than left to be discovered.

## Tests

The phase is mostly evidence, so it adds few. What it does add:

- **`capHousemates`' table is already a JVM test** and 9f does not change it — the sheet shows everyone,
  so it has no cap to test. What 9f owes is a test that the sheet lists **every** housemate including the
  archived ones, which is the claim the line cannot make.
- **`TranslationTest` and `scripts/translation-gate.py` hold continuously** and will catch a new English
  string the moment one appears. That is the guard behind 9f's zero-new-strings goal — it does not need
  a new test, it needs the existing one to stay green.
- **No `SchemaGateTest` change**, because no schema change. If that stops being true, the phase has
  grown a fourth migration and the standing gate applies in full.

## Gate

Phase 9 closes when all of these hold:

- `spotlessApply`, `assembleDebug`, `test`, `lint` at 0/0, and the instrumented suite green on the Xiaomi.
- **9a's outcome recorded** against 5a's three written outcomes, and Phase 5 ticked in `PLAN.md`.
- **9b's seven** ticked, with ADR-0025 reworded if the autostart-denied reboot says it must be.
- **73 scenes** clean, with the four suspect scenes re-shot and the `empty` suite seen in landscape for
  the first time.
- The Pages root serves a page, and `_config.yml` no longer claims something untrue.
- The fluffle sheet built, driven on the device, and reachable from Home with any number of housemates.
- **1.7 live on a track**, with nine listings' copy, nine screenshot sets and nine release notes.
- **1.0.0 → 1.7 watched on the phone**, arriving from Play, with a table-by-table diff on common columns
  that is empty.

## When it closes

Write the results into [`PLAN.md`](PLAN.md), tick **Phase 5** and **Phase 9** in the status list, and
empty [`DOD.md`](DOD.md) §10. At that point every phase in the project is closed and the app is on Play
in nine languages — which is the first time both sentences have been true at once, and the point at
which "what is open" stops being a release checklist and starts being whatever owners report.
