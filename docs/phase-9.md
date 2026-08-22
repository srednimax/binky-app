# Phase 9 — Ship it — ships as 1.7

**Status: opened 2026-08-18. Production access GRANTED 2026-08-19** — closed testing ended, the 12-tester
count cleared, and Google approved. Nothing external gates this phase any more: 9h and 9i are unblocked
along with everything else, and the release date is whatever date this repo is ready (see 9h). **9a is
answered too**, 2026-08-19, which frees 9b and 9c.
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
3. **9g and the listing copy before 9h, and 9h before 9i.** ✅ **Spent 2026-08-22.** The upgrade proof
   needs an update that *arrives from a track*, so it was downstream of the upload by construction — the
   installed Play build is Play-signed and refuses a local APK on signature mismatch. The closed track
   delivered it. **A fourth edge is now live: 9i before 9k**, because 9k moves three files between source
   sets and so changes the artifact 9i is a proof about.

Everything else — 9d, 9e, 9j — can be done whenever. **9e is closed** (2026-08-19), its post-merge probe
included, and **9j is replied** (08-21).

## 9a — The overnight Doze run ✅ answered 2026-08-19, confirmed on the matched gap 08-20

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

**§1's last box ✅ closed 2026-08-21, and it was never 9a's own question.** The Phase-4 carry came in two
halves. The sweep half was answered 2026-08-20 — the care sweep posted at 09:02 *inside* an unbroken
`device_idle=full`, so the freezer does not eat a sweep the way it ate the 18→19 dose alarm. The watch half
— a watch **auto-expiring**, where nagging stops, the prompt shows the *current* trend, and closing it
leaves no row behind — was supposed to ride the 09:00 sweep on Saturday 2026-08-22.

**It could not, and 9g is why.** Every screenshot scene but one taps *Close it* on the watch prompt, which
deletes the row; after eleven reseeds `watches` was empty and the 08-22 expiry no longer existed. The
reading was taken the evening before instead, as a two-leg A/B in which `endsAt` is the only field that
differs — leg A nags, leg B does not, and the worker is *seen* to run in both. All three claims hold, and
the one that had no test anywhere in either tree, the prompt naming the current trend, is the one that
needed the phone. Readings in [`DOD.md`](DOD.md) §1.

**Two reading traps came out of it and both are worth more than the box.** A forced job that answers
*"Could not find job 0"* produces output identical to a passing sweep — `am force-stop` cancels an app's
jobs and WorkManager re-enqueues under a new id — so no sweep result counts until `WM-WorkerWrapper` is seen
in logcat. And a database pulled without its `-wal` is a stale database: it reported the watch row surviving
*Close it* three times running, and an app bug that does not exist was most of the way into `DOD.md`.

## 9b — The gate items parked behind it ✅ closed 2026-08-19

Seven, all in [`DOD.md`](DOD.md) §2 — six here and the seventh is 9c. They were parked deliberately:
each writes to the armed course or force-stops the app, and doing any of them first costs the night 9a
is waiting for.

**Closed with the fix, not with the readings.** Six items answered at 38 readings is what was asked
for; what it cost is a delivery state the app had been getting wrong since 4a, and 9b does not close
until that is built and driven. It is — `ReminderDelivery.Silent`, re-run at 9/9 — and the second
finding is answered in wording rather than in code, deliberately. See *"One decision over three
findings"* below for why those two answers are the same answer.

**They are driven by a script now, `scripts/alarm-gate.py`**, and that is worth a sentence because the
obvious reading of this item is that it was already done. `DoseAlarmTest` proves ADR-0025's invariant
in-process against an in-memory database, fifteen cases of it, and passes. What it cannot reach is the
half the gate is actually about: whether the app's **own write paths**, tapped through on a real phone,
reach the rebuild at all. A repository method that forgets to call `rescheduleDoseAlarm` passes every
assertion in that test and none of the readings here. So each check taps the write an owner actually
makes — *Given* on the Care tab, a chip removed from a course editor, *Archive* on Home — and then reads
`dumpsys alarm`, which is the platform's answer rather than Kotlin's.

**Thirty-eight readings**, across writes, bunny-level rebuilds, the blocked states, the destructive
dialogs, a timezone change and four reboots. Every armed alarm came back on the *exact*
mechanism. The results are in `DOD.md` §2 and are not repeated here; three things are reasoning rather
than record:

- **Two of the ten write readings end at nothing armed**, and that is the half worth paying for. "At
  most one" cannot fail — there is a single request code, so a second pending dose alarm is not
  expressible — so the only reading with any information in it is **zero**, and a stale alarm left
  behind by a deleted course is invisible until it fires into a database with no dose to post.
- **The two blocked states are not one state, and the check was wrong before the app was.** The bullet
  asks that a denied permission and a muted channel both "present as blocked". They do, but not in the
  same words: app-wide, nothing has ever arrived and `ReminderCaveats` shows the point-of-use ask
  (ADR-0006) instead of a caveat; per-channel, the owner has switched one category off and no dialog
  the app can raise will ask it back, so it states the consequence. Expecting one sentence for both
  would have made the better of the two behaviours look like a failure.
- **Un-muting a channel is the second `Armed`-shaped finding in two days.** Switched off and on again,
  `doses` returns at `IMPORTANCE_LOW` with `mUserLockedFields` set, so the app may never raise it back
  — and at that importance a dose reminder posts silently, which for a 03:00 dose is most of the way to
  not posting at all. `resolveReminderDelivery` calls it fine, because only `IMPORTANCE_NONE` is
  blocked. It is one more rung in `caveatFor`'s ladder, not a redesign; whether it rides 1.7 or opens
  Phase 10 is the same open question 9a's autostart finding raised, and they should be answered
  together.

  **Answered 2026-08-19, and built on this branch as a 1.7 `fix:`.** See *"One decision over three
  findings"* below.

### One decision over three findings

9b closed with three ways a reminder can fail while the app says it is fine — 9a's autostart list,
this section's lowered channel, and the post-restart window below. They are one question asked three
times: **how much of a phone's unreliability should the app narrate to someone who cannot fix most of
it?** Answering them separately is how a delivery ladder turns into a wall of hedges, so they were
answered together, on one rule:

> **The app speaks when it can read the fact *and* the owner can act on it.** Everything else is a
> line that would be printed unconditionally, and an unconditional caveat is wallpaper — the thing
> ADR-0003 already refuses to print for the armed state, for the same reason.

That rule sorts all three without further argument:

| Finding | Readable? | Actionable? | What the app does |
| --- | --- | --- | --- |
| Autostart list (9a) | no — the list is unreadable, only its *existence* is | yes, by hand | already hedges: `BestEffort` with the way in offered, never claimed back (shipped in 1.7) |
| Lowered channel (9b) | **yes**, exactly | **yes**, on one screen | **new: `ReminderDelivery.Silent`**, one rung, pointing at the channel's own page |
| Post-restart window (9b) | **no** — nothing is running to read it | no | nothing. The ADR wording is corrected and the app says nothing |

**The fourth state, and why it is a state rather than a caveat branch.** The cheap version of this fix
is a new `when` arm in `caveatFor` alone, leaving the resolver as it was. That would have left
`resolveReminderDelivery` returning `Armed` for a silenced channel — the promise, still being made, in
the one function whose whole job is to be the honest answer — and every *other* reader of it
(`RemindersOptIn`, the backup screen, `BackupExclusionNotifier`) still believing it. Making it an enum
entry is what turns the fix into a compiler error at each of those sites, which is how the opt-in
screen's own delivery line got the same sentence without anybody remembering it existed.

**The audible cliff is `IMPORTANCE_DEFAULT`, not the level the channel was created at.** Below DEFAULT
Android plays no sound, and that is exactly where *"it will arrive silently"* becomes true. A `doses`
channel lowered from HIGH to precisely DEFAULT loses its heads-up and keeps its sound, and is
deliberately not reported: the sound is the half an owner responds to, and spending the one card on
the pop-up would be hedging about the smaller fact. It is the one place this resolver knowingly says
less than it could, and `ReminderDeliveryTest` asserts the silence as a case so it reads as a choice
rather than an oversight.

**Versioning.** A `fix:` — the ladder gained a rung, not a capability — so 9f's *"exactly one `feat:`"*
note for 1.7 still holds and Phase 10 stays unopened.

### The item with a consequence beyond a tick, and it was not the one expected

**Reboot twice, autostart granted and autostart denied.** The fear written down was that without
autostart the ROM would not start the process for a broadcast, so *"the alarm is rebuilt from truth at
boot"* would be a claim this device cannot keep. **Autostart turned out to be irrelevant to it**: with a
slot armed two hours out, both arms came back from a reboot with exactly one alarm at exactly the same
instant. Autostart governs whether a frozen process is thawed to receive an alarm hours later, which is
9a's finding and a different mechanism entirely.

The claim is wrong anyway, and for a reason that has nothing to do with Xiaomi. **The rebuild does not
happen at boot; it happens at the owner's first unlock.** Left locked after a restart, the phone had no
pending dose alarm and no process at +45 s, +105 s or +165 s. This device is `ro.crypto.type=file`, and
under File-Based Encryption with a secure lock screen `ACTION_BOOT_COMPLETED` is not sent when the
kernel finishes booting — it is sent when credential-encrypted storage is unlocked, which is the first
time a password is entered. `BootReceiver` cannot opt out of that with `directBootAware`, and should
not want to: it opens the database, and the database is in CE storage by definition.

**That is a real dose, not a technicality.** A phone that restarts itself for an OTA at 02:00 and is
picked up at 07:00 has no dose alarm for five hours; a 03:00 slot inside them is not delivered late, it
is never armed. And nothing in the app can notice — nothing in the app is running. It also means the
first three readings of the earlier reboot run were only as good as the moment someone happened to
reach for the phone, which is why the check records the keyguard state now and why `locked-boot` exists
as a check of its own.

So ADR-0025's self-heal sentence gets reworded to **"rebuilt at the owner's first unlock after a
restart"** — and on this phone not even promptly then. Whether the delivery ladder should say anything
about it is the third instance of the same open question, alongside 9a's autostart finding and the
muted channel above. **All three want one decision, and it is a product decision rather than a
technical one**: how much of a phone's unreliability an app should narrate to someone who cannot fix
most of it.

### What the run cost, and two environment notes it corrects

An early reboot left the phone on the lock screen with the notification shade holding focus, which
refuses `am start`, `adb install` and `uiautomator` alike — and each refusal lies about its cause: the
activity "does not exist", the install was "canceled by user", the dump is empty. The cause was this
file's own helper winding the autostart list back to the top by swiping upward; a swipe that ends near
the top of the display pulls the shade down once the list has nowhere left to go. It restarts the
activity from cold now, and `wait_for_unlock()` makes every reboot stop and ask rather than walk into
those three errors. **The phone has a password and `adb` cannot get past it** — a reboot is a step that
hands the run back to a person.

Two notes in [`CLAUDE.md`](../CLAUDE.md) and §1 came out of runs where autostart was **denied**, and
with it granted they no longer hold:

- **`MY_PACKAGE_REPLACED` was delivered.** After `adb install -r` the process was running and the alarm
  had been rebuilt at the same instant, exactly as `UpdateCatchUp` intends. §1's reading that the ROM
  "does not start the process for a broadcast at all" is a statement about a phone without autostart,
  not about this phone.
- **The `SCHEDULE_EXACT_ALARM` appop survived that reinstall**, reading `allow` afterwards — against
  §1's "a reinstall is the one action known to revert the appop".

And one that is new, and is a trap for every future overnight run: **the autostart grant lapses on its
own.** It was granted 2026-08-19 07:47 and read as absent the same evening, with the header back to ten
apps. Neither `pm clear` nor `adb install -r`, each tested on its own afterwards, removes it. So the
grant is not durable and **must be re-read immediately before any run that depends on it** — the header
count on the autostart screen is the only honest signal, because a `uiautomator` dump's `checked`
attribute reports false on every row including the granted ones.

## 9c — The edge-to-edge matrix, 75 scenes ✅ closed 2026-08-21

**300 cells, 300 reached, zero errors, no defect in the app** — 67 `full` scenes, 6 `empty` and 2
`mismatch`, each across all four configurations, 20:05 to 23:59 into `~/binky-screenshots/phase-9/en`.
35 findings, every one benign: 28 `touch` and 7 `drawn`, and each `drawn` one has an exact counterpart
in the 2026-08-16 baseline, down to the overlap in pixels. **Two driver defects had to be fixed to get
it**, both in `swipe_up`, both the same shape, and neither in the app.

### Its own warnings had gone stale, which is the finding worth keeping

This section and [`DOD.md`](DOD.md) §2 both said the run would be the first to shoot the `empty` suite
in landscape, and that four scenes' PNGs — `watch-expiry`, `medication-course`,
`medication-course-bottom`, `record-dose` — should be assumed wrong. **None of that was true any more.**
`011a07d` fixed the wipe's rotation and the `keeps_watch_prompt` sort on 2026-08-13, and the 2026-08-16
run three days later already used the fixed driver: its `empty` landscape PNGs are genuinely
2712x1220, and [`phase-7.5.md`](phase-7.5.md) says in its own record that *"`medication-course` is
checked correctly for the first time"*. Diffing that run's reports against `SCENES` put it at **73 of
the 75** — only `language-picker` and `home-fluffle-sheet` had never been shot at all.

**A warning outlives the defect it was written for, and nothing makes a noise when it does.** Three
sessions could have read "assume this evidence is wrong" about evidence that was sound. The count went
stale the same way twice, which is why `DOD.md` now carries the `grep` instead of a number.

**The full re-run was still the right call**, for a reason the old text does not give: four commits
since 2026-08-16 touch UI that scenes photograph — `9b46f41` (`Navigation.kt`, the nav label, and so
the bottom bar on all 21 `tab` scenes, which is where every overlap in this matrix lands), `b34b3fc`
(Settings and Support), `ed42638`/`6575e4a` (the reminders caveats), and `c086112` (9f: `HomeScreen`
and `BunnyProfile`, which `ArchivedBunniesScreen` also draws). Drawing the line around those by hand is
the same judgement that let the scene count go stale.

### Two swipes aimed at the wrong rectangle

**`user_rotation` is a claim about the pin, not a reading of the screen.** It records what the display
was last pinned to and means nothing while `accelerometer_rotation` is 1. The run's *first*
`ensure_seed` happens before any `apply_config`, so nothing had pinned anything yet — and the key read
`1`, left over from an earlier session, against a live 1220x2712 portrait screen. `swipe_up` built a
2712-wide swipe from it and sent every gesture to x=1356, off the right edge of the display. Nothing
scrolled, `tap` read the unchanged screen as *"nowhere left to go"*, and the seed walk died on the
wizard's *Continue* before a single cell was captured. Fixed by `screen_size()`, which reads `cur=WxH`
from `dumpsys window displays` — already rotated, and the one reading that cannot disagree with the
screen.

**A swipe at the middle of the screen is a claim that the screen is what scrolls.** `home-crowded-all`
came back unreachable in *both* landscape configs — as it had on 2026-08-16, where 7.5 read it as a
scroll budget too short for a landscape viewport and re-shot it clean. That was not the whole cause.
The bunny switcher is a `DropdownMenu` 212dp wide anchored under its app-bar control, and *All bunnies*
sits below every active bunny — six of them on the `crowded` seed, which fills the menu and leaves it
scrolling internally. Measured on the phone: the menu occupies `x[178-897]` of a 2712px-wide landscape
screen, and `swipe_up` was swiping at **x=1356**, outside it. The gesture went to the window behind.
Portrait passes because its midpoint, x=610, happens to fall *inside* the menu — the scene was never
right in landscape, only lucky in portrait.

Fixed by `content_box`, which scrolls inside the rectangle the app's own nodes occupy. A dump taken
while the menu is open contains **only** that window's nodes, so the box is the menu; on an ordinary
screen it is the whole display. Measured rather than assumed before the change went in: Care & Meds and
Settings both box to exactly `(0, 0, 2712, 1220)` in landscape, so every full-screen scene swipes
precisely where it always did. Both cells came back clean, and the screenshot is the real *All bunnies*
view rather than a Home screen wearing its name.

- **Compose does not publish `scrollable="true"`** — Care & Meds and the housemates sheet report zero
  scrollable nodes. The `DropdownMenu` is the exception because it wraps a real
  `android.widget.ScrollView`. So a fix keyed on that attribute would have worked here and nowhere
  else, which is why the box is measured from the nodes instead.

### What it proves for 9f

`home-fluffle-sheet` is **clean in all four configurations**, and the landscape shot is the one the
scene was written for: the sheet opens past the half-height state with all four housemates on screen —
Clover, Nugget, Thistle and *Pumpkin (archived)*, the marker included — over a 1220px viewport. Three
of those names were unreachable anywhere in the app before 9f.

**The predicted broken needle did not appear.** This file expected one per route 9f touches, and `home`
and its siblings all came through clean; the one scene that did break is on a route 9f never touched.
Five routes' worth of evidence said needles on chrome survive a redraw and only content needles are
fragile — 9f's needle is `Lives with`, which is content, and it held. The rule now has an exception,
and the thing that actually broke was geometry rather than vocabulary.

**No route that changed since 2026-08-16 produced a finding** — `home`, `home-fluffle-sheet`,
`settings`, `support`, `archived`, `language-picker`, all clean in all four cells. `9b46f41`'s nav-label
change touches the bottom bar on 21 scenes and cost nothing.

## 9d — Close Phase 5

Write 9a's and 9b's results into [`PLAN.md`](PLAN.md)'s 5a / 5i / 5j entries and tick **Phase 5** in the
status list.

✅ **Closed 2026-08-21.** It waited on the 08-22 reading so the tick would not be over an unmade
observation — Phase 4 closed *on the build*, with its delivery evidence carried into Phase 5, and §1's watch
half was the last of that carry. The escape hatch written here was to track the carry outside Phase 5 and
tick without it; it was not needed. 9g destroyed the 08-22 arming, the reading was taken on the bench the
same evening instead, and the tick lands over evidence. It had been the one unticked box since 2026-08-05
while four later phases closed around it.

**What went into `PLAN.md`**: 5a closes on 9a with the answer stated as the conditional it actually is —
an exact alarm does not reach a frozen app, and autostart is what stops the freeze — plus the two things a
bare tick would have lost, `DOSE_GRACE` correctly posting nothing at 3h50m late and `Armed` becoming
`ReminderDelivery.Silent`. 5i closes on 9a and 9b together, carrying the FBE finding that makes ADR-0025's
"rebuilt from truth at boot" wrong on any phone with a lock screen. 5j's three carried items get pointers
rather than rewrites, because the detail is Phase 9's. Two stale markers were fixed on the way: 5b read
"not closed" over a bullet 5j had written, and 5i read "in progress, 2026-08-05".

## 9e — The front door ✅ closed 2026-08-19

`docs/` is served by GitHub Pages from `main`, and **had no `index.md`**, so the site root was a 404.
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

**What shipped.** `docs/index.md` — what Binky is, what it records, *a record, not a diagnosis*, the
privacy policy, `binky.support@gmail.com`, the repo and its licence — written from `README.md` and the
English full description in [`store-listing.md`](store-listing.md) rather than freshly, so the front door
and the Play listing cannot drift into saying different things about the same app. Nine languages are
named on it; the page itself is English only, which is the honest state — a translated landing page is
not in this phase and pretending otherwise would owe the translation gate nine files it is not getting.

The corrected comment says what actually makes the directory safe, which the old one did not: **not**
that planning documents go unrendered, but that the repo is public and holds nothing that is not already
on GitHub. Stated that way it generalises to the rule that matters — anything that must not be published
must not be in `docs/` at all — where the old wording quietly invited someone to drop a file in and
trust that leaving the front matter off would keep it private.

**Merged and probed the same day.** `/` answers **200** with the page rendered — Pages'
`jekyll-relative-links` turned the page's `privacy-policy.md` link into `/binky-app/privacy-policy.html`,
which is also a 200, so the front door's one outbound link works rather than merely existing. The box
was held open until then on purpose, and it was not bookkeeping: Pages builds from `main`, so the root
was still a 404 on the branch, and a Website field pointing at a 404 is worse than the empty field it
replaces. **§4's Website field is now unblocked.**

## 9f — Seeing the whole fluffle ✅ closed 2026-08-20

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

### What it shipped as, and the two things only the phone said

`HousematesSheet.kt`, `housematesInSheet` in `BunnyLabels.kt` beside the cap it contrasts with, a
`HousematesLine` on Home's profile header, one wire in `Navigation.kt`, and `Housemate` grown an
`avatar` — resolved in `toProfile` like the profile's own, so no composable learns where `filesDir`
is. **Zero new strings**, as aimed for. Built, `spotlessApply`/`assembleDebug`/`test`/`lint` clean,
and driven on the Xiaomi against the `crowded` seed on 2026-08-20.

**The affordance is the chevron.** The write-up above says a tooltip fails because "the affordance is
invisible" — and a line made tappable with no other change is invisible in exactly the same way, one
inert-looking line among three. It is the dashboard card's own chevron, so it costs no new vocabulary
and no new string, and `minimumInteractiveComponentSize()` gets the row to Material's 48dp: a
one-line label is 18dp, less than half a touch target. Checked by tapping 38px below the text, which
opens the sheet.

**1. "The switcher's existing navigation" is the wrong navigation for half the rows.** The sheet
lists archived housemates, and `selectBunny` *persists* — ADR-0015 forbids reopening the app into a
memorial, which is why the archived list uses `openArchivedScope` and keeps it in memory. Worse in
the other direction: `resolveSelection` gives the archived scope **outright precedence over the
stored selection**, so tapping a live housemate from an archived bunny's profile would have written
the choice and left the owner staring at the same memorial. It needs `closeArchivedScope()` first.
Both directions watched on the phone; neither is visible from the JVM.

**2. The sheet opened half-height in landscape** — Material's partially-expanded state — showing
Clover and Nugget, *the same two the line already named*, with the two it exists to reveal one drag
below the fold. `skipPartiallyExpanded = true` fixes it: expanded is the content's own height, so
portrait is unchanged and landscape opens showing all four. This is precisely the failure the
tooltip argument warned about, arriving through the door that was supposed to be safe — and it was
invisible in portrait, which is the configuration anyone would check first.

**It is captured, and that was the argument all along.** `home-fluffle-sheet` joins the matrix as a
`full`-suite scene on the `crowded` seed, in the `overlay` family with the app's two other sheets —
a sheet is anchored to the bottom edge, which is where the navigation bar is, and that is the inset
case no dialog covers. The tooltip was rejected partly for being unphotographable; shipping the
replacement without a scene would have kept the defect and moved the excuse. **The suite is 75, not
73**: `language-picker` had already taken it to 74 with commit `b34b3fc` and nobody edited the count
in `DOD.md`, so 9c should read the number off `scripts/edge-to-edge.py` rather than off prose.

## 9g — Nine locales of screenshots ✅ closed 2026-08-21

Four scenes — `home`, `weight`, `observations`, `backup` — in nine locales and both themes, through
`screenshots.py --locale <tag>` at ~7 min a locale. 72 padded PNGs at exactly 1526×2713, in
`~/binky-screenshots/phase-9/listing/_play/{light,dark}/<tag>/`. No scene was missed in any locale.

Play falls back to the default listing's screenshots for a locale that has none, so this improves the
listing rather than unblocking it — which is exactly why it was never done before the tracks could carry
the build it describes.

**"English is a selection, not a run" was true when it was written and false when it was read.** The 63
scenes in `~/binky-screenshots/phase-7/after/` are from 2026-08-13; **9f changed Home's profile header on
08-20**, so that set's `home.png` has neither the chevron nor the tappable *Lives with* row — on the
screenshot that leads the listing. The ordering rule *9f before 9g* was written precisely to stop 9g
photographing a screen 9f was about to change, and it worked; what it could not do was update the
sentence next to it. **A claim that a screen needs no re-shoot expires with the next commit that touches
it**, and the check is one crop.

`--locale en` had never run at all, for an unrelated reason: `load_strings` built `values-<qualifier>`
from the tag, and English is the base in `values/` with no `values-en/` to find. So the flag died before
the first tap, and without it English inherits the phone's system language — Polish, here. One line.

### What the run found: Ukrainian doubled a full stop

`Bijou важить на 170 г менше, ніж 7 серп. 2026 р..` — on the lead screenshot. Ukrainian's CLDR medium
date pattern is `d MMM y 'р'.` and **ends in a period**; six strings closed on that date and appended a
sentence one. `trend_flag_drop`, `trend_flag_rise`, `trend_flag_acknowledged`, `backup_reminder_next`,
`weight_chart_none_in_range`, `watch_expired_weight`. The stop came off — the abbreviation's period does
the sentence's work, as it ordinarily does in Ukrainian — and `uk` was re-shot.

**The rule is *ends on the date argument*, not *ends on an argument*.** `backup_auto_recorded` and
`backup_restored_scope` keep their full stop, because `dateTimeLabel` closes them with a short time
rather than a date. No other shipped locale's medium pattern ends in punctuation: Czech and German close
on the year's digits, `pt-BR` on `de 2026`.

**Every one of those six strings is well-formed on its own**, which is why `TranslationTest` was green
and would have stayed green: it checks format arguments, plural categories and orphans, and each of
those questions has the right answer here. The defect does not exist in the file. It exists once a
formatter and a translation meet on screen, and the only instrument that reaches it is a screenshot —
which is a second reason to shoot all nine, beyond the listing that asked for them.

**Taking the period off the *date* would have been the wrong fix.** `р` without its period is not an
abbreviation of *рік*; it is a stray letter. The sentence yields, not the date.

## 9h — The Console sitting

Everything in [`DOD.md`](DOD.md) §4, in one sitting, in that order.

✅ **Production access was granted 2026-08-19**, on the request that went in the day before, once closed
testing ended and the 12-tester count cleared. **Nothing on Google's side is waiting** — the review is
over, production is available for the first time, and the release date is whatever date this repo is
ready. What is left in the Console is paste: the AAB, nine listings, nine screenshot sets, nine release
notes, the store settings and App content's ten sections. None of it is gated on a decision any more.

⚠️ **The listing still goes up with the build, and only with it.** That rule was never about the review;
it is `store-listing.md`'s standing rule and it outlived the thing it was mistaken for. The copy
describes **1.6-and-later** scope while the tracks still serve **1.0.0 / 1.3** — no redesign, no
multi-valued droppings, seven of the nine languages absent — so pasting nine localised descriptions
today puts a feature list in front of an owner for a build that does not carry it. **Upload the AAB
first, then paste.** Screenshots the same: prepare them at 9g, upload them with the build.

⚠️ **Access granted is not a track chosen.** Whether 1.7 goes straight to production or walks
internal → closed → production is an ADR-0009 decision made at upload. The grant makes production
possible; it does not make that call.

**The build that goes up is 1.7**, carrying everything from 1.4 through 1.7, and every downstream claim
moves with it — most importantly the field upgrade proof, which retargets from 1.0.0 → 1.3 to
**1.0.0 → 1.7** and now crosses `MIGRATION_4_5`, `MIGRATION_5_6` **and** `MIGRATION_6_7`. Uploading an
intermediate version first spends a release cycle to prove nothing 1.7 would not.

## 9i — The field upgrade proof ✅ closed 2026-08-22

1.0.0 → 1.7 on the Xiaomi, arriving from a track, with every row 1.0.0 wrote still there. The Play build
on that phone is **1.0.0**, not the 1.0.1 that 4h assumed, so the chain crosses all three hand-written
migrations and the launch gate that ADR-0023's Phase 7.5 amendment rewrote.

⚠️ **That install holds dummy data, not real bunny history**, which this file and DOD §4 both claimed
until 2026-08-21. The proof is unharmed — it asks whether the chain preserves what the old build wrote,
and a dummy row migrates exactly as a real one does. What it costs is the coverage that months of
dogfooding would have given for free: **the diff can only cover tables that have rows, and rows can only
be added while the phone is still on 1.0.0**, since a Play-signed install refuses a local APK and nothing
brings 1.7 back down. Check what the install actually contains *before* the upload, not after.

**This is the standing gate's item 5**, the one no test can satisfy, on the release it matters most for.
Both live upgrade paths were watched on the phone at 7.5 — 1.4.0 → 1.5 and the skipped-version
1.1.0 → 1.5, zero differing rows on common columns both times — but neither of those was a *Play*
delivery to an install carrying an owner's real records.

### What it took two runs to prove, and why the first one was not enough

**2026-08-21 proved the migrations. 2026-08-22 proved the assembly.** They are different claims and only
the second one gates production.

The first run got the schema-4 rows into 1.7 by **restoring the export by hand**, because the internal
track's opt-in page had already instructed the tester to uninstall — which wiped the original 1.0.0 data
directory. All three hand-written migrations ran against rows a real 1.0.0 build wrote and nothing was
lost, so what it left open was narrow but load-bearing: Play replacing the APK *under an existing data
directory*, the process cold starting, and ADR-0023's launch gate reading `user_version` out of an
installed database rather than a constructed one.

The second run closed it. 1.0.0 fresh off **production** at 01:01:43, the schema-4 export restored
through 1.0.0's own restore path, exported at 10:35:40, and the **closed** track's update taken thirty
seconds later at 10:36:10. `installerPackageName=com.android.vending`, versionCode 379. Exported again at
11:38:19; `scripts/upgrade-diff.py` reports `4 -> 7`, tables `8 -> 20`, every row of all eight surviving
tables present on shared columns, both droppings moves landed (36 + 36), media files `5 -> 5`.

⚠️ **The finding worth keeping: a clean diff cannot tell an in-place update from a hand restore.** The
table counts are identical either way, which is exactly why 2026-08-21 looked like a pass for a claim it
had not tested. What separates them is on the phone, not in the archive:

```
firstInstallTime = 2026-08-22 01:01:43     # the fresh 1.0.0
lastUpdateTime   = 2026-08-22 10:36:10     # Play swapped the APK under it
```

An uninstall-and-reinstall sets those equal. Nine and a half hours apart means the data directory
survived. **Read `dumpsys package` as part of this proof, not only the script's exit code.**

✅ And the **closed track delivers an ordinary in-place update** with no uninstall demanded — as DOD §4
predicted, and unlike internal. That is the track to use for an upgrade proof.

## 9j — The tester's reply ✅ replied 2026-08-21

Owed since 2026-08-09 and it was never the feature. Their *"5 kg plus"* was a **number, not a change**: a
Flemish Giant is legitimately 6–10 kg, so any absolute weight is wrong for some breed, and Binky will
never call a weight too high — only say that it moved, by how much, since a date. ADR-0028 shipped the
gain signal they were actually asking for; the reply is the part that explains why the app will not do
the thing they literally asked for, which is better said than left to be discovered.

## 9k — The debug affordances that ship anyway

**A repo-wide sweep on 2026-08-21 for developer-only surface reaching the production build. One finding.**

The question was whether the two-minute reminder button, the sample-data seeder and anything like them are
kept out of the Play build. **Partly.** `app/src/debug/` is real exclusion — `SeedVariantReceiver`, the
debug manifest and the *Binky Debug* launcher label are variant-scoped and the release build never
compiles them. But the *user-facing* half of those tools lives in `main/`, behind `if (BuildConfig.DEBUG)`
at the call site (`SettingsScreen.kt:179`), and that is a **runtime** guard.

**`isMinifyEnabled = false` is what turns that from a strip into a hide.** With R8 off, nothing removes a
branch whose condition is a compile-time `false`, so `SampleData.kt`, `DebugReminder.kt` and
`DebugSection` are compiled into the release AAB. Unreachable — but present.

**Unreachable is the accurate word, and the sweep is why it can be said rather than assumed.**
`BuildConfig.DEBUG` is false in a release build, so the section never composes; the seeder writes through
the repositories but nothing can call it; and the two-minute reminder posts on `ReminderChannel.Care`, a
channel the app already ships, so it does not leave a stray entry in a user's notification settings. There
is no path from an installed Play build to any of it.

### What it actually costs: thirteen strings, translated nine times

`values/strings.xml:294–306` carries no `translatable="false"`, so the debug copy is inside the gate's 693
and has been translated into every shipped language:

> `settings_debug_reminder_help` → *Надсилає одне сповіщення за дві хвилини, власним шляхом…*

Developer-facing text, paid for nine times, shipping in the release resource table, and inside
`aab-locale.py`'s scope — a check written to catch a *missing* translation now also standing guard over
copy nobody will read. **That is the whole of the finding**, and it is hygiene, not risk.

### What the sweep cleared

- **The manifest.** `main/` declares exactly one exported component: the launcher activity. Every
  receiver, provider and service is `exported="false"` with the reasoning beside it.
- **Logging.** No `Log.d`, no `Log.v`, no `println`, no StrictMode. The five `Log.w`/`Log.i` calls sit on
  real failure paths and belong in a release.
- **The other three `BuildConfig.DEBUG` uses**, all of which are deliberate behaviour rather than leakage:
  WorkManager's logging level, `destructiveMigrationAllowed` — which *is* ADR-0023 — and the `-debug`
  marker in the support email's version label, which exists so a bug report says which build sent it.

### Why it waits

⚠️ **After 1.7, and after 9i.** Moving three files between source sets changes what is in the artifact,
and 9i is a proof *about* that artifact. `build.gradle.kts:146` already made this argument once, for R8:
"a sixth divergence whose failures are release-only, runtime and reflection-shaped is the opposite of what
this checkpoint proves." The same sentence applies to a source-set move on the eve of a release.

The work, when it comes, is three things at once: move the files to `src/debug/` with a no-op seam in a
`release` source set so `SettingsScreen` still compiles; delete the thirteen strings and their nine
translations; and **revisit `isMinifyEnabled`**, which that comment marked for "1.1, against a known-good
1.0" and is now six releases overdue. A known-good 1.7 is exactly the baseline it was waiting for — and R8
would strip the branches on its own, which is the cheaper half of this item arriving for free.

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
- ~~**The Phase-4 carry's watch half read** at the 09:00 sweep on **2026-08-22** — §1's last open box, and
  the only item in this phase with a date it cannot be moved off.~~ ✅ **Read 2026-08-21**, and the date it
  "cannot be moved off" turned out to be the one thing about it that was wrong: 9g destroyed the arming, and
  an A/B on the bench answered all three claims the same evening.
- **9b's six** ticked ✅, ADR-0025 reworded ✅ — the reboot said it must be, though not for the reason
  feared — and the readable half of what the run found *fixed*, not merely recorded ✅. (The seventh
  bullet in §2 is 9c's and is gated on the line below, not this one.)
- ~~**75 scenes** clean, with the four suspect scenes re-shot and the `empty` suite seen in landscape.~~
  ✅ **2026-08-21 — 300 cells, 0 errors, no defect in the app.** The four suspect scenes and the
  landscape `empty` suite had been sound since 2026-08-16; what the run actually cost was two driver
  bugs, both in `swipe_up`. See §9c.
- The Pages root serves a page, and `_config.yml` no longer claims something untrue.
- ~~The fluffle sheet built, driven on the device, and reachable from Home with any number of housemates.~~
  ✅ **2026-08-20** — and it is a matrix scene, so 9g photographs it in all four configurations.
- ~~Nine screenshot sets prepared.~~ ✅ **2026-08-21** — 72 padded PNGs, one defect found and closed.
- **1.7 live on a track**, with nine listings' copy, nine screenshot sets and nine release notes.
- **The debug affordances out of `main/`** and the thirteen strings out of the gate — after the upgrade proof, never before it.
- **1.0.0 → 1.7 watched on the phone**, arriving from Play, with a table-by-table diff on common columns
  that is empty.

## When it closes

Write the results into [`PLAN.md`](PLAN.md), tick **Phase 5** and **Phase 9** in the status list, and
empty [`DOD.md`](DOD.md) §10. At that point every phase in the project is closed and the app is on Play
in nine languages — which is the first time both sentences have been true at once, and the point at
which "what is open" stops being a release checklist and starts being whatever owners report.
