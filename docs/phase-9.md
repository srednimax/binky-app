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

**A third leg arrived unasked for on 2026-08-22**, and it is the only reading that exercises rule 4. The
debug install had been reinstalled at 00:24 that morning, so the daily sweep was armed at first launch; it
fired unforced at 09:01:26 and posted the nail trim alone. The watch was *active* with `lastNaggedOn` null —
rules 2 and 3 both let it through — and what silenced the nag was an observation logged at 18:00 the evening
before, 15 h inside the rolling 24 h `WATCH_SATISFIED_WITHIN`. The A/B could not show this: it varied
`endsAt` and forced the job. ⚠️ **The natural auto-expiry, due 2026-08-25 08:30, is deferred to after the
production deploy** — a field confirmation of claims already answered, gating nothing, and it needs re-arming
if the deploy slips past Monday morning.

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

### Why it waits — and what "it" turned out to be

⚠️ **After 1.7, and after 9i.** Moving three files between source sets changes what is in the artifact,
and 9i is a proof *about* that artifact. `build.gradle.kts:146` already made this argument once, for R8:
"a sixth divergence whose failures are release-only, runtime and reflection-shaped is the opposite of what
this checkpoint proves." The same sentence applies to a source-set move on the eve of a release.

✅ **The wait is over: 9i closed 2026-08-22.** The proof was taken on 1.7.0 / versionCode 379, in place
from the closed track, so the artifact this branch changes is no longer the one anything is pending on.

**The rule survives; what it constrains turned out to be narrower than the sentence above says.** It is
about the artifact, and the artifact is made by a merge. Writing the code changes nothing until it lands,
so on **2026-08-21** all three parts were built on `chore/9k-debug-affordances` and the branch was left
unmerged. The waiting moved to the only place it was ever load-bearing — the merge — and off the part
where waiting only meant a later session paying to rediscover the finding.

### What was built

**The seam is a source set, not a flag**, which is the difference between a strip and a hide.
`ui/settings/DebugSettings.kt` exists twice: `src/debug/` composes the section, `src/release/` is
`@Composable fun DebugSettings() = Unit`, and `SettingsScreen` calls it with no `BuildConfig.DEBUG` in
sight. `SampleData.kt` and `DebugReminder.kt` moved to `src/debug/` unchanged.

**`SettingsViewModel` had to give up state**, because a screen ViewModel in `main/` holding
`SampleDataOutcome` and calling `seedSampleData` is the leak in a different shape. It went to a debug-only
`DebugSettingsViewModel` — a **second ViewModel on one screen**, against the house rule and argued in the
file. It stays a ViewModel rather than a `rememberCoroutineScope` because [`SampleData.kt`] is not
idempotent by merging: it declines to run twice by checking whether Bijou is already there, so a seed
cancelled by a rotation leaves Bijou behind and every later run reports *already present* over a fixture
missing most of its rows — which the capture harness would then photograph.

**Thirteen strings to `src/debug/res/values/strings.xml`**, outside both scripts' `app/src/main/res`
scope, and deleted from all eight `values-*/`. **693 → 680 × 8**, gate green. They carry
`translatable="false"` there anyway — not for the gate, which never looks in that directory, but for
Android lint, which reads the *merged* debug resources and failed the build with thirteen
`MissingTranslation` errors until they were marked.

**The finding is closed by measurement.** Both variants built, both APKs unzipped: the release dex has
**zero** occurrences of `SampleDataKt`, `DebugReminderWorker`, `seedSampleData` and
`scheduleDebugReminder`, and its `resources.arsc` zero of the thirteen names, where the debug APK carries
all of them. The only `DebugSettings` in the release dex is the empty stub.

**`isMinifyEnabled` was revisited and stays off**, which is an answer rather than a seventh deferral. The
*reason* R8 was wanted here is gone — the move already excludes what R8 would have stripped, so "R8 would
strip the branches on its own" stopped being the cheaper half and became a redundant one. The *condition*
was never met and its subject moved: "against a known-good 1.0" becomes a known-good **1.7**, and 1.7 is
not on a track. Nor can it be met on the bench — a release build cannot be installed over the Play one,
which refuses a locally-signed APK on signature mismatch, so `assembleRelease` succeeding is not evidence
about a phone. The comment now says that instead of pointing at a release that shipped six versions ago.

### The one thing the move nearly broke

`scripts/edge-to-edge.py` taps two of the thirteen labels **by name** — *Add the sample data* in `seed()`,
*Reminder settings* in the `reminders-sheet` scenes — and its `load_strings` read `app/src/main/res` only.
After the move those were unknown resources, and `resolve_needles` would not have failed loudly: it would
have fallen to its **substring** case and resolved each needle to whichever *other* string happened to
contain the words, which is a scene that shoots the wrong screen rather than a run that stops.

It now layers `src/debug/res/values` over the English base. That is honest independently of this change —
the driver only ever drives the debug build, so `values/` alone never described what is on the screen it
is reading. Simulated offline against `pl`, `uk` and `pt-BR`: both needles resolve to the literal English,
which is exactly what the debug build renders in every locale (resource resolution falls back to the
unqualified `values/`), and no needle became ambiguous.

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
- ~~**The debug affordances out of `main/`** and the thirteen strings out of the gate — after the upgrade
  proof, never before it.~~ ✅ **Built 2026-08-21** on `chore/9k-debug-affordances`, and the branch is held
  rather than merged: the rule is about the artifact, and an unmerged branch is not in one. **The merge is
  still gated on 9i.**
- **1.0.0 → 1.7 watched on the phone**, arriving from Play, with a table-by-table diff on common columns
  that is empty.

## When it closes

Write the results into [`PLAN.md`](PLAN.md), tick **Phase 5** and **Phase 9** in the status list, and
empty [`DOD.md`](DOD.md) §10. At that point every phase in the project is closed and the app is on Play
in nine languages — which is the first time both sentences have been true at once, and the point at
which "what is open" stops being a release checklist and starts being whatever owners report.

## Appendix — the live checklist as it stood when Phase 9 closed

Moved here verbatim from [`DOD.md`](DOD.md) on 2026-08-24, when Phase 9 was ticked and that file
was emptied back down to the standing schema gate. It is kept rather than deleted because the
run narratives below are the evidence itself, not a summary of it — §1 is 9a's record in full,
§2 is 9b's, §4 is 9h and 9i's, and §10 is the index with 9d–9k's own entries under it. The
shorter sections (§3, §5–§9) are closed items belonging to earlier phases, carried along so that
nothing had to be judged worth losing at the moment of the move.

Headings are demoted one level from the originals; nothing else is edited. Where a section says
"open" or "still owed", read it as of that date — Phase 9 is closed and 1.8.0 is live in
production.

---
### 1 — The exact-alarm overnight Doze run ✅ 9a answered 2026-08-19

**Read the two result blocks below first** — *"the alarm did not fire"* and *"9a passes"*. Everything
above them is the pre-run record, kept because the arming procedure and its traps are the expensive half
to rewrite. What is still open in this section is the **Phase-4 carry** (the care sweep firing while still
in Doze, and a watch auto-expiring), not 9a.

5a's outcome and the first bullet of Phase 5's gate. **Still owed**: the 4→5 Aug night fired on the
*best-effort* path (`setAndAllowWhileIdle`) because the permission had reverted, so the question the
three outcomes were written for is untouched.

**Armed for the night of 18→19 August 2026**, read 2026-08-18 21:40. The state the 2026-08-05 read
found broken is now right, and this time the *exact* mechanism is proven armed before the night rather
than guessed at from the appop afterwards:

- `SCHEDULE_EXACT_ALARM` reads **`allow`** for `binky.bunny.and.rabbit.tracker.debug` (now `u0a507` —
  the uid moved, so the 2026-08-05 note's `u0a497` is stale).
- One pending `DoseAlarmReceiver` alarm, `RTC_WAKEUP #74`, `origWhen=2026-08-19 08:00:00.000`,
  **`window=0`, `exactAllowReason=permission`, `whenElapsed == maxWhenElapsed`** (both
  `+10h24m58s508ms`). That is the exact path, not `setAndAllowWhileIdle`.
- Exactly one WorkManager job, `TIME=+11h24m35s` → **2026-08-19 09:00**, which carries the Phase-4 sweep.
- Battery-optimisation exemption **absent** (`dumpsys deviceidle whitelist` has no binky), autostart
  **not** granted to the debug build — deliberately, so the run is the honest best-effort presentation.
- The build on the phone is debug **versionCode 346 / 1.5.0**, not HEAD's 1.6.0. It was left in place on
  purpose: `git diff v1.5.0..HEAD -- app/src/main/java/app/binky/tracker/work
  app/src/main/java/app/binky/tracker/data` is **empty**, so the alarm and sweep code under test is
  byte-identical to 1.6.0, and a reinstall is the one action known to revert the appop under the run.

#### The dose was moved to 03:00, deliberately

**Changed 2026-08-18 22:0x, through the app's own course editor** — the ordinary write path, which is
itself the thing that re-arms the alarm. Bijou's Metacam went from `8:00 AM & 8:00 PM` to a single
**`3:00 AM`**, and the alarm re-armed at `origWhen=2026-08-19 03:00:00.000`, `window=0`,
`exactAllowReason=permission`, `whenElapsed == maxWhenElapsed`.

The reason is the one thing every previous attempt got wrong: **deep Doze needs the phone stationary,
not merely screen-off.** An 08:00 fire competes with the owner waking up, and a phone carried out of the
house is in continuous motion — `active=1000:"motion"` is what spoiled 4→5 Aug. At 03:00 the phone is
face-down on a nightstand and has been still for hours, which is the strongest condition this test can
have. On this device Doze entry is fast — `inactive_to=15s`, `sensing_to=15s`, `locating_to=5s`,
`idle_pending_to=5m`, so roughly **six minutes** of stillness reaches `device_idle=full`, against stock
Android's half hour.

Two side-effects of the edit, both accepted: the 20:00 slot is gone (the chip row reflows as chips are
removed, so a retried tap took it), and the course is now one dose a day. Neither matters — it is
seeded data, and one alarm is a cleaner test than two.

#### What the morning must show — one post at 03:00, four at 09:00

Written down before the night so the read is falsifiable rather than a story told afterwards:

1. **03:00, channel `doses` (importance 4)** — Bijou's Metacam, the only slot on the course, in deep
   Doze, on battery, through the **exact** mechanism. **This is 9a, and it is the only one that matters.**
2. **09:00, channel `care`** — Bijou's **Nail trim**, worded *overdue*: `firstDueOn` 20672 = 2026-08-07,
   never completed, `notifiedForDueOn` null. The care tab says "11 days overdue".
3. **09:00, channel `care`** — Bijou's **Weigh-in**, worded *overdue*: last `care_events` completion is
   20655 = 2026-07-21 against a weekly interval, so it is 21 days behind.
4. **09:00, channel `care`** — the **group summary**, because `CareNotifier` posts one for two or more
   and cancels it otherwise. Two due reminders is the case that produces it.
5. **09:00, channel `watch`** — "Have you checked on Bijou today?": watch active until 2026-08-21 08:30,
   `lastNaggedOn` null, last observation 2026-08-17 18:00, outside `WATCH_SATISFIED_WITHIN`.

And nothing else: Nugget's **Hay order** (`firstDueOn` 20686 = 2026-08-21) and Bijou's **vaccination**
(20805) are both in the future and must stay silent.

**Only item 1 is expected to be in Doze.** The 09:00 sweep runs at `DEFAULT_REMINDER_TIME`, and
`reminderTime` is a preference **nothing in the UI ever writes** — it is plumbing for a setting that was
never built, so 09:00 cannot be moved without fabricating a state no owner can reach. If the phone is
carried to work it will be awake and moving at 09:00, and items 2–5 then prove only that the sweep fires,
which 4g already showed. **The care-sweep-in-Doze half of the Phase-4 carry therefore rides a morning the
phone stays home** — as does the watch auto-expiry, which needs 2026-08-21 anyway. Both are secondary;
9a is the blocker and 9a is armed.

#### Pre-flight, in this order

- [x] Re-seed a real medication course, and Bijou's watch for the Phase-4 carry below. `seedWatches`
      back-dates `startedAt`, so the expiry morning is a parameter, not something to wait for.
      *(Seeded 2026-08-18 20:28; the watch's `endsAt` landed on 08-21, hence the carry note above.)*
- [x] Grant the exact-alarm permission **through the app's own deep link** (that is the path under test).
      *(Reads `allow`.)*
- [x] Confirm the pending alarm is the *exact* mechanism: `window=0` and `whenElapsed == maxWhenElapsed`.
      The best-effort alarm reads `window=+38m55s`, `flags=0x20` and a `maxWhenElapsed` ~39 min later.
      **This pair of fields is the only pre-run proof of which mechanism is armed** — the 4→5 Aug run
      could only tell from the appop afterwards, too late.
- [x] Read and record the **autostart** state before touching anything: the count in the header of
      *Ustawienia → Aplikacje → Uprawnienia → Autostart* and the apps under it. A `uiautomator` dump's
      `checked` attribute lies on that screen — every row reports false. (Read 2026-08-18 21:42, via
      `am start -n com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`
      then `uiautomator dump` — the header reads **"10 apps can start in the background"**: Binky,
      Calendar, Clock, Facebook, Google Wallet, Instagram, Messenger, Mi Fitness, Notes, WhatsApp.
      **`Binky Debug` is not among them**, unchanged from the last read, and not needed — an alarm
      broadcast arrives on its own temporary allowlist, `temporaryAppAllowlistReasonCode=302`.)
- [x] **Unplug**, evening, and leave it unplugged past the fire time. Charging blocks Doze — this is the
      half 4g could not claim. *(Done. Unplugged overnight, plugged back in 06:50:47 — and for the first
      time in four attempts every condition held. See the result below.)*

**Trap:** never run `connectedAndroidTest` after arming. `am instrument` force-stops the package, which
cancels every alarm it placed, and the result is indistinguishable from a broken rebuild.

#### Reading it the next morning — read-only, before the shade is touched

```bash
adb shell dumpsys alarm > alarm.txt        # pending alarms are ABOVE the "Removal history:" section
adb shell cmd appops get binky.bunny.and.rabbit.tracker.debug SCHEDULE_EXACT_ALARM
adb shell dumpsys batterystats --history   # device_idle=full unbroken across the fire time; plug=usb after
adb shell dumpsys notification --noredact  # exactly one post on channel=doses, importance=4
```

- [x] **Dose outcome recorded** against 5a's three written-down outcomes — **outcome 3, "not until
      touched"**, but for a reason none of the three anticipated. Written up below; it does not close 9a.
- [x] **Phase-4 carry ✅ both halves answered** — the sweep half 2026-08-20, the watch half **2026-08-21**.
      The care sweep firing while **still in Doze** is done; the result block below replaces this bullet's
      guess that the 07:28 plug-in spoiled it. The watch half — a watch **auto-expiring**, where nagging
      stops, the prompt shows the *current* trend, and closing it leaves no row behind — was **not** read at
      the 09:00 sweep on Saturday 2026-08-22. That arming no longer existed by the time Saturday came, so it
      was read on the bench the evening before, as a two-leg A/B against the same sweep. Second result block
      below. **This was the last of Phase 4's carry, so Phase 5 ticks with it.**

#### Result of the 18→19 Aug run: the alarm did not fire, and **Doze is not why** 🔴

Read 2026-08-19 06:51 with `scripts/doze-capture.sh`, before the shade was touched. **The conditions were
finally right** — this is the first of four attempts where nothing about the setup is in question:

- Unplugged and stationary all night. `device_idle=full` **unbroken from 01:07:08 to 03:07:09**, straight
  across the 03:00 fire time, on battery at 27%. (Anchor: batterystats offset 0 = `2026-08-17 17:46:42`;
  offsets past a day carry a `+1d` prefix, which is easy to miss when grepping.)
- The alarm was verified exact before the night and never cancelled — the removal history's newest entry
  for `u0a507` is the seeding at 20:27:45, hours before it was armed.
- No reboot (`up 15 days`), battery never died (25% at the read).

**And it did not fire.** There is no wake event at 03:00 at all; the only exit from idle, at 03:07:09,
reads `wake_reason=0:"248 WLAN_CE_2"` — a WLAN wake, not an alarm. The dose was delivered **3h50m47s
late, at 06:50:47**, the moment the phone was plugged in.

**The cause is Xiaomi's process freezer, not AOSP's Doze:**

```
06:50:47.481 D GreezeManager: THAW uid = 10507 pid = [17392] reason : enable:28-thawAll caller : 1000
06:50:47.982 D Aurogon    : sendPendingAlarm  uid = 10507
06:50:47.987 I SmartPower : binky…debug/10507(17392): idle->background(3040566ms) R(alarm start)
```

`GreezeManager` is HyperOS's cgroup freezer and `Aurogon` its power framework. The app's process was
**frozen**, and Aurogon **held its pending alarm** until the thaw, then released it half a second later —
`sendPendingAlarm` is not an AOSP log line. Plugging in is what thawed it (`thawAll`). So the honest
statement is not "an exact alarm does not survive Doze" but **"an exact alarm does not reach a frozen app
on HyperOS, and the vendor decides when to thaw"**.

**The app itself behaved correctly, which is a real positive result and was previously untested.**
`DOSE_GRACE` is 30 minutes; the delivery was 3h50m late, so `postDueDoses` posted **nothing** — right, not
broken, because a notification for a slot four hours stale is worse than silence — and
`rescheduleDoseAlarm` armed the successor, now pending for **2026-08-20 03:00**, `window=0`,
`exactAllowReason=permission`. Late delivery is handled exactly as designed.

**The untested variable is autostart.** `Binky Debug` is not in the autostart list; the Play `Binky`
build is. The 2026-08-18 pre-flight note above guessed autostart was "not needed — an alarm broadcast
arrives on its own temporary allowlist"; that guess is now **wrong**, and the `temporaryAppAllowlistReasonCode=302`
in the alarm's `idle-options` evidently does not outrank the freezer.

- [x] **Re-run with autostart GRANTED to `Binky Debug`, one variable changed.** Granted 2026-08-19
      07:47 — the header now reads **"11 apps can start in the background"** with `Binky Debug` in the
      allowed list, against 10 the night before. That is the only variable that moved.
- [x] **The 10:00 run — it fired. Autostart is the lever.** Read 2026-08-19 16:34.

#### 9a passes: with autostart granted the exact alarm fires in deep Doze ✅

One variable changed from the run that failed — `Binky Debug` added to autostart — and the result
inverts completely. Anchoring batterystats to its `TIME: 2026-08-19-09-01-02` marker:

| | |
| --- | --- |
| Unplugged | **07:55:38** (`-plugged`), on battery at 30% for the whole test |
| Deep Doze entered | **09:09:34** (`device_idle=full`) |
| **Alarm fired** | **10:00:00.779** — `wake_reason=0:"35 pm8xxx_rtc_alarm"`, `+tmpwhitelist=u0a507`, then `NotificationManagerService:post:binky…debug` |
| Deep Doze left | **10:09:24** |

So the fire sits **50 minutes inside an unbroken 59m51s stretch of `device_idle=full`**, on battery,
and the notification landed **779 ms** after the scheduled instant. `Metacam`, channel `doses`,
importance 4. That is 5a's **outcome 1, "fires in grace"** — not merely in grace, but on the second.

**And the app was never frozen.** The logcat covers 09:06:35 onward and contains **no `GreezeManager`
and no `Aurogon` line for the app at all** — no freeze, no thaw, no `sendPendingAlarm`. Those three were
the entire story the night before. Autostart does not make the alarm louder; it stops HyperOS freezing
the process that receives it.

**The conclusion, stated carefully:** on HyperOS an exact alarm is delivered on time in deep Doze **if
and only if the app has autostart**. Without it the vendor freezes the process and queues the alarm
until something thaws it — which, in the failing run, was plugging the phone in 3h50m later. Neither
`SCHEDULE_EXACT_ALARM`, nor `window=0`, nor the alarm's own `temporaryAppAllowlistReasonCode=302`
changes that.

**Remaining caveat, and it is small.** The gap between the app last running and the fire was ~1 h today
(the 09:00 sweep) against ~5 h on the failing night, so today does not *prove* a five-hour-idle app
stays unfrozen. The 03:00 slot is still armed on the same course and costs nothing, so tonight supplies
the matched gap. **It is confirmation, not a new experiment — 9a is answered.**

#### Armed for the night of 19→20 August: the matched gap, and the Phase-4 carry

Re-armed **2026-08-19 22:07**, through `scripts/alarm-gate.py`'s own helpers rather than by hand —
`autostart_state`, `set_autostart`, `arm_single_slot(3)`, `dose_alarms`. The first arming in this project
that was not tapped out by eye, which is worth a line because the helpers were written for 9b's readings
and turn out to be the arming procedure as well.

- **The build on the phone moved and nobody wrote it down.** It is now debug **versionCode 358 / 1.6.0**,
  installed 2026-08-19 21:15:05, where the 18→19 pre-run note above records 346 / 1.5.0. Data survived —
  `bunny.db` intact, Bijou's Metacam still on the Care tab.
- **The autostart grant was gone again.** Read 22:05: *"10 apps can start in the background"*, no
  `Binky Debug` — 14 hours after it was granted at 07:47 and the 10:00 run passed on it. **This sighting
  is confounded**, unlike the one CLAUDE.md records: a reinstall happened at 21:15 in between, and that
  earlier note explicitly cleared `pm clear` and `adb install -r` of responsibility. So this is evidence
  the grant does not survive *this* reinstall, not a second sighting of a spontaneous lapse. Re-granted
  → **11 apps, `Binky Debug` listed.**
- **The dose had reverted to 08:00** and is re-armed at **`origWhen=2026-08-20 03:00:00.000`,
  `window=0`, `exactAllowReason=permission`, `whenElapsed == maxWhenElapsed` (+4h52m45s857ms)** — the
  exact path, through the app's own course editor, which is the write path under test.
- Exactly one WorkManager job, `TIME=+10h52m45s` → **2026-08-20 09:00**, carrying the Phase-4 sweep.
- Battery-optimisation exemption still **absent**. Autostart remains the only lever that has moved.
- **No notification channels exist for the debug package at all.** `dumpsys notification` prints 860
  channels and not one is binky's; the reinstall dropped them. **This cannot swallow the post**: every
  posting path calls `ensureReminderChannel` immediately before building the notification
  (`ReminderNotifications.kt:130`), so `doses` is created at importance 4 at post time. Two consequences
  worth having in writing — 9b's silenced-channel failure mode is **not** available as tomorrow's
  explanation, and `reminderChannelImportance` falls back to the creation importance rather than
  reporting a not-yet-created channel as muted, which is the behaviour its comment claims.
- **The idle gap is the whole point of tonight.** The app was last touched at 22:10, so it sits ~4h50m
  idle before the fire — against ~5 h on the night it was frozen and ~1 h on the morning it passed. That
  is the matched gap the caveat above asks for.
- **Left to a person: unplug it** and leave it stationary. Charging blocks Doze, and plugging in is what
  thawed the frozen process on 18→19.

**What the morning must show**: one post at **03:00 on `doses`**, inside an unbroken `device_idle=full`
stretch, on battery, and **no `GreezeManager` / `Aurogon` line for the app** — that trio is what the
failing night looked like and their absence is what passing looks like. Then the 09:00 sweep.
`scripts/doze-capture.sh`, read-only, before the shade is touched.

**The watch auto-expiry is not tonight.** The seeded watch ends 2026-08-21 08:30, so it belongs to the
20→21 night — and its state is **unverified** at this arming: `sqlite3` is not on the device (`run-as`
finds no binary), and the app must not be relaunched now, because `e2e.relaunch` force-stops and that
cancels the alarm just armed. Read it from the Care tab tomorrow, **before** re-arming.

#### Result of the 19→20 Aug run: confirmed on the matched gap ✅

Read 2026-08-20 07:30 with `scripts/doze-capture.sh`, shade untouched. **Every line of "what the morning
must show" holds**, and the caveat the 10:00 pass carried is retired.

- **The alarm fired at 03:00:01.599**, 1.6 s after `origWhen 1787187600000` (03:00:00.000) — and the ROM
  **started the process for it**: `am_proc_start [… ,broadcast,{…/DoseAlarmReceiver}]` against a process
  `SmartPower` logs as `died->background`. A cold start for a broadcast is the autostart signature, and
  it is exactly what the 18→19 night could not do.
- **Posted 03:00:02.4 on `doses` at importance 4** — "Metacam / 0.3 ml for Bijou", *Given* and *Skipped*.
  The channel was created at post time as the arming note predicted; the reinstall having dropped every
  channel changed nothing.
- **`device_idle=full` unbroken 01:08:00 → 03:07:55.** The fire sits 1 h 52 m inside deep Doze, and Doze
  did not break *for* it — the 03:07:55 exit is the maintenance window. (Anchor: the `TIME:` marker at
  `+2d06h48m12s535ms` = 2026-08-20 01:01:56.)
- **On battery throughout.** First `plug=usb` at **07:28:44**, at 47%.
- **No `GreezeManager` or `Aurogon` line for uid 10507 anywhere near the fire.** The only two in the whole
  logcat are `FZ uid = 10507 reason =new process success !` at **03:04:42** — three and a half minutes
  *after* the broadcast ended — and the `THAW` at 07:28:43 when the cable went in. The freezer took the
  process only once it had done its work.
- **Nobody touched it**: `seen=true` at 07:13:51 (lock screen), `posttimeToFirstClickMs=-1`,
  `posttimeToDismissMs=-1`.
- **The successor is armed**: one `RTC_WAKEUP`, `origWhen` = 2026-08-21 03:00:00. ADR-0025's "at most one"
  holds across a firing, not only at rest.
- **The idle gap was ~4h50m**, against ~5 h on the frozen night and ~1 h on the morning it passed. That is
  the matched gap the caveat above asks for, and it closes it: a five-hour-idle app on this phone stays
  unfrozen **with autostart granted**.
- **The autostart grant survived the night** — re-read 07:35, still *"11 apps can start in the
  background"* with `Binky Debug` listed, 9½ h after granting. That is not a second sighting of the
  spontaneous lapse CLAUDE.md records; it is mild evidence against one.

**Finding 1 — the alarm fires twice when it is what cold-starts the process.** 🟠 Two deliveries,
03:00:01.599 and 03:00:08.477, same `origWhen`, the second with `whenElapsed` bumped +7.4 s — the
signature of AlarmManager firing a re-arm whose trigger time is already in the past. The cause is
`BinkyApplication.kt:183`: the alarm started the process, so process-init's `rescheduleDoseAlarm()` ran
**with no `postedThrough`** and armed the slot it was still inside `DOSE_GRACE` of.
`DoseAlarmReceiver` does pass `postedThrough`, and armed tomorrow correctly. It is self-limiting to one
extra fire — the second delivery starts no process, so init does not run again — and the owner sees one
notification, because the id plus `ONLY_ALERT_ONCE` collapse the second post into an update. What it
actually costs is a second wakeup and a second full rebuild at 03:00. `DoseAlarm.kt:88` documents this
exact loop and defends the receiver against it; **the process-start rebuild is the hole in that defence,
and it opens only when the alarm is what starts the process** — that is, precisely on the phones and at
the hours this whole run exists to test, which is why five months of the receiver path being right never
surfaced it.

**Finding 2 — no overnight run has ever tested whether a dose reminder can wake anyone.** 🟠 The post made
no sound: `mLastAudiblyAlertedMs=-1`, `mBuzzBeepBlinkCode=0`, and `notification_alert` reads zero for
buzz, beep and blink. **This is the phone, not the app** — `settings get global mode_ringer` reads `0`
(silent) with the notification stream muted, and nothing alerted audibly all night: WhatsApp at 20:54 and
the deskclock at 05:50 and 06:50 read identically. `doses` was at importance 4 with `mUserLockedFields=0`,
so `resolveReminderDelivery` was right to say `Armed` and 9b's silenced-channel failure mode is not in
play. The visual half did work — the AOD lit at 03:00:08. Two things to carry: **read `mode_ringer` before
taking a silent post for a delivery defect**, and every run in this file has proven *delivery*, never
*audibility*.

**The 09:00 sweep was still pending at the read, and the cable threatens it.** Exactly one WorkManager
job, `TIME=+1h29m22s787ms` from 07:30:38 → **09:00**, with only `0x80000000` — the timing delay —
unsatisfied. But the phone went on charge at 07:28:44 and **charging blocks Doze**, so on the cable the
sweep fires out of idle and the Phase-4 carry's "still in Doze" half is not settled by this morning.

#### What 9a cost the app: `Armed` was a promise this phone does not keep ✅ fixed

The finding falsifies a claim the app was already shipping. An owner on a Xiaomi who granted the battery
exemption reached `ReminderDelivery.Armed` and was told *"this phone is set up to let them through"* — on
a phone that had just held a dose for 3h50m. ADR-0003's opening argument is that a dose reminder which
silently fails is worse than none, so this is a `fix:`, not a feature, and it rides 1.7.

- [x] `hasAutostartSettings()` is now an input to `resolveReminderDelivery` (`oemAutostartUnreadable`),
      ranked last of the three best-effort reasons because it is the only one the app cannot read back.
      Where the list exists, **`Armed` is unreachable**; a stock phone still reaches it on the exemption
      alone, so the hedge is scoped to the phones that earned it.
- [x] Two new strings — the autostart variant of the care line and of the dose line — saying **hours**,
      and saying the reminder turns up when the phone is next picked up. Translated into all eight.
- [x] The exemption now *reveals* the autostart line rather than ending the conversation. That was a real
      hole: the autostart block on the opt-in screen renders only while the app is unexempted, so an
      exempt Xiaomi owner previously had no way in at all.
- [x] `ReminderCaveats` ranks the same way, so the Care & Meds card follows the delivery line.
- [x] ADR-0003 amended with both runs and what they change.

**Not yet done on the phone**: the untested link in the chain is whether an *ordinary* app — not
`adb shell`, which has more privilege — can actually launch the MIUI autostart activity.
`hasAutostartSettings()` uses `resolveActivity`, so the button is not blind, but nobody has watched it
open. Worth one tap before 1.7 goes out, since the new copy points at it.

**What it costs the product**, and this is now the real work:

- [x] **ADR-0003 needs an amendment.** Its exact-alarm promise holds only where the OEM does not freeze
      the app, and this is the single most popular Android OEM in several of the nine markets shipped to.
      ✅ Written as *"the autostart list gates the honest state after all"*, and amended a second time in
      9b for the silenced channel.
- [x] **An owner on a Xiaomi will hit this and never know.** They cannot be expected to find
      *Settings → Apps → Permissions → Autostart* unaided, and the app currently says nothing. Whatever
      is built here is user-facing copy at minimum and probably a check plus a deep link — i.e. a
      **`feat:`**, which changes Phase 9's "exactly one feature commit" versioning note above.
      ✅ Shipped in `ed42638` as copy plus a deep link — `hasAutostartSettings()` puts the phone at
      best-effort, `doses_state_best_effort_autostart` names the cost in hours, `openAutostartSettings()`
      is the way in. **It went in as a `fix:`, not the `feat:` feared here**: the ladder already had the
      state and the card, so this added a reason to an existing rung rather than a capability. The
      versioning note survives untouched.
- [x] Decide whether this is a Phase 9 item or the thing that opens Phase 10. It was not in the plan
      because nobody knew it existed.
      ✅ **A Phase 9 item, and so are 9b's two.** Answered 2026-08-19 as one decision over all three
      findings — see *"One decision over three findings"* in [`phase-9.md`](phase-9.md). Phase 10 stays
      unopened; 1.7 carries all of it as `fix:` commits.

#### The 09:00 sweep on 2026-08-19, the same morning: also fine, and the prediction was wrong in the app's favour

Two posts at 09:00:27, not the four predicted: `Nail trim` / "Overdue for Bijou." on `care`
(`notifiedForDueOn` now 20672), and the `watch` nag. **No weigh-in and therefore no group summary** —
and the app is right, the prediction was wrong. `lastCompletedOn` takes the later of the care event and
**the latest weighing** for `WEIGH_IN` (`CareSchedule.kt`), and Bijou was weighed 2026-08-16, so it is
not due until 08-25. (Those two dates do not sit a week apart, so one of them is mistyped — `careDueOn`
is `lastCompletedOn + interval` and nothing else; the mechanism is what the paragraph is for.) Reading `care_events` alone is what produced the bad prediction. With one care
reminder due, `CareNotifier` correctly posts no summary.

That also settles the freezer's reach: **a WorkManager job was not held either** — the sweep ran at
09:00:27, 27 s after its slot, while the phone was on battery.

**Not a Doze failure, so 4g's result stands**: a WorkManager job survived 10.5 h of Doze on 2026-08-04.
Nothing here contradicts that; the freezer is a different mechanism reached by a different path.

#### The sweep *inside* deep Doze, 2026-08-20 — the Phase-4 carry's first half ✅

Read 2026-08-20 17:00–18:00, shade untouched. The carry bullet above assumed this morning's sweep was
spoiled by the plug-in. It was not, on two counts: the read that called the sweep "pending" happened at
**07:30, ninety minutes before the slot**, and the cable was in for **twenty minutes** — `+plugged`
07:28:43, `-plugged` 07:48:24, then on battery until 17:05:35.

- **`device_idle=full` 08:32:05 → 09:02:05, unbroken**, on battery.
- **The sweep posted at 09:02:03.8** — `Nail trim` / "Overdue for Bijou." on `care`, the `watch` nag at
  09:02:03.87 — inside that stretch, with Doze exiting ~1 s later.
- **Two minutes late is the WorkManager window, not a freeze**, which is the question the carry was for:
  whether the freezer eats a sweep the way it ate the 18→19 dose alarm. It does not.

**Anchor the history to its `TIME:` markers, never to the RESET.** The relative clock is elapsed-realtime
and drifts across Doze — RESET-based arithmetic put the plug-in at 07:01, twenty-seven minutes early,
where the marker at `+2d13h13m39s708ms = 2026-08-20-07-27-23` puts it at 07:28:43, the figure the morning
read had recorded independently.

**What is not settled, and re-running would not settle it:** whether the job ran *inside* `full` or in the
maintenance window that ended it. The anchor is ±1 s and logcat no longer reaches 09:02 — its buffer
starts 10:05. That distinction belongs to the platform; the app's half is answered.

**Two posts again, and again the app is right.** `notifiedForDueOn` is now **20673** (2026-08-08) on the
nail trim. The weigh-in stayed null because `careDueOn` is `lastCompletedOn + interval` and
`lastCompletedOn` for a `WEIGH_IN` counts weighings — Bijou's latest is **2026-08-17 08:30**, so it is not
due until **08-24**. One care reminder due, so `CareNotifier` correctly posts no summary; the summary in
the shade is Android's own `Aggregate_AlertingSection` autogroup on `watch`, not the app's.

**The 2026-08-19 21:25 re-seed moved every seeded date a day later** than the predictions written above —
the nail trim's `firstDueOn` 20672 → 20673, the watch's `endsAt` to 08-22. Predictions written against the
older seed do not apply, and neither does `sqlite3` being unavailable: the database reads fine on the
host, pulled with `adb exec-out run-as … cat databases/bunny.db` plus its `-wal`.

#### The watch half, 2026-08-21: an A/B on the bench, because the sunrise had been destroyed ✅

**The 09:00 Saturday reading no longer existed to take.** 9g reseeds the database once per scene, and every
scene but one taps *Close it* on the watch prompt — which deletes the row. After eleven reseeds `watches` was
empty, so the watch due to expire at 08:30 on 08-22 was gone, and with it the arming this box had been
waiting on since 2026-08-05. **A screenshot run is a destructive act against anything armed, and nothing
warned** — that belongs in the driver, not in a reader's memory.

What replaced it is a two-leg A/B against the same sweep, on the debug install (dummy data — ADR-0023's
Phase 9 amendment). A watch was written straight into the database, `startedAt` 2026-08-14 20:09:43 →
`endsAt` 2026-08-21 20:09:43, `lastNaggedOn` 20685 (yesterday), and the daily sweep forced with `cmd
jobscheduler run -f`. **`endsAt` is the only field that differs between the legs**, so rule 2 of
`WatchSweep.kt` is the only filter that can account for a difference between them.

| | leg A — 19:16:27 | leg B — 20:12:08 |
| --- | --- | --- |
| `endsAt` | in the **future** | in the **past** (20:09:43) |
| the worker | ran | ran — `WM-WorkerWrapper: Starting work for …ReminderSweepWorker`, then `SUCCESS` |
| `channel=watch` post | **yes**, id `2083104992` | **none** — 0 records before, 0 after |

- [x] **Nagging stops at expiry.** The A/B above: same bunny, same day, same sweep, one field changed, and
      the nag disappears. `WatchSweepTest."an expired watch stops nagging immediately…"` already held this
      in-process; what was missing was the sweep doing it on the phone.
      **The claim rests on the notification record, not on the watermark.** Leg B's `lastNaggedOn` was read
      without the WAL (see the traps below) and so cannot tell "unchanged" from "not yet checkpointed"; the
      `channel=watch` count is a direct measurement and is independent of the database entirely.
- [x] **The prompt names the *current* trend** — the one claim with no test anywhere in either tree. Read off
      the phone at 20:10:50: *"The watch on Bijou has run out / It has already stopped asking. Extend it if
      you are still keeping an eye out, or close it."*, carrying the flag itself rather than a copy of it —
      **"Bijou is down 170 g since Aug 7, 2026. 2.470 kg then, 2.300 kg now."** The database agrees exactly:
      Aug 7 = 2470 g, newest = 2300 g on Aug 19, difference **170 g**.
      **The anchor is what makes it current rather than remembered.** Aug 7 is fourteen days before *today*,
      not fourteen days before the watch's own start on Aug 14 — a prompt rendering the trend as it stood
      when the watch was created would have read *down 10 g since Jul 31* (2490 → 2480). It reads neither, so
      `evaluateTrend` is running at display time, which is what `WatchExpiryViewModel.prompt` reading
      `Instant.now()` on every emission is for. The change is in grams and the absolutes in kg, per the house
      rule, and ADR-0001's sentence is *in the dialog* — "an observation about the numbers, not a diagnosis"
      — next to the vet-directed-diet caveat, rather than a page away.
- [x] **Closing it leaves no row.** *Close it* tapped 20:13; `watches` is **empty**. `WatchExpiry.kt` makes
      close, dismiss and swipe-away one action deliberately — that is what makes "prompts once" true without
      a column recording it — and the prompt did not return across a force-stop and a relaunch, with Home
      offering *Start a watch* in its place. `WatchRepositoryTest`'s
      `closingDeletesTheRowAndStartingAgainIsNotBlockedByAStaleOne` covers the same ground in-process.

**Two ways this evening nearly recorded a false result, and neither was the app's fault.** Both are about
reading the phone, both produce output that looks exactly like a pass, and both are cheap to defend against.

- **A forced job proves nothing until the worker is seen to run.** `cmd jobscheduler run -f -n
  androidx.work.systemjobscheduler … 0` answered *"Could not find job 0"*, because `am force-stop` cancels an
  app's jobs and WorkManager re-enqueued under **id 1** when the app was next launched. The output of that
  non-run is **identical to a pass** — no nag posted, no watermark moved — and it was very nearly written up
  as one. Read `WM-WorkerWrapper: Starting work for …ReminderSweepWorker` out of logcat before believing any
  sweep result, and read the id out of `dumpsys jobscheduler` rather than assuming it is still 0.
- **A database pulled without its `-wal` is a stale database.** Three reads in a row reported that the watch
  row had survived *Close it*. It had not — the delete was sitting in an 8 KB write-ahead log that was never
  pulled, and the conclusion heading for this file was an app bug that does not exist. Pull `bunny.db` **and**
  `bunny.db-wal`, then `PRAGMA wal_checkpoint(TRUNCATE)` on the host, on every read. The arming recipe
  already said so; the reading half did not, and that asymmetry is the whole trap.

#### A third leg, unasked for: rule 4 seen in the field, 2026-08-22 09:00 ✅

**The A/B varied `endsAt` and forced the sweep. This one varied nothing and forced nothing**, and it is the
only reading in this file where `WatchSweep.kt`'s **rule 4** — any observation inside
`WATCH_SATISFIED_WITHIN` settles it — is what silences the nag. Free, in the sense that nobody armed it: the
debug install was reinstalled at **00:24:36** that morning for unrelated reasons, so the daily sweep was
armed at first launch and had to survive the night with no help.

```
09:01:26.307  am_proc_start        … SystemJobService        WorkManager woke the process
09:01:27.942  notification_enqueue channel=care groupKey=app.binky.tracker.care
```

**One post, 87 s after the slot**: `Nail trim` / "Overdue for Bijou.", and `notifiedForDueOn` moved to
`2026-08-11`, so the stamp landed. 08-19 posted **two** at the same slot; the difference is entirely one
fresh observation, and all three silences are the app being right:

| what did not post | why, and it is correct |
| --- | --- |
| **Weigh-in** | Not due. `CareSchedule.kt` takes `lastCompletedOn` as the later of the care event **and the latest weighing** — Bijou was weighed 2026-08-20 08:30, interval 1 week, so 08-27. Reading `care_events` alone (last completion 2026-07-25) is the same mistake that produced the wrong four-post prediction on 08-19. |
| **Group summary** | `CareNotifier` posts one only for two or more due. One reminder, correctly none. |
| **The watch nag** | Rules 2 and 3 both **let it through** — the watch is active (2026-08-18 08:30 → 2026-08-25 08:30) and `lastNaggedOn` is null. Rule 4 stopped it: Bijou's newest observation is 2026-08-21 **18:00**, 15 h before the sweep, inside the 24 h rolling window. |

That last row is the one worth keeping. `WATCH_SATISFIED_WITHIN` is a **rolling** 24 h where `lastNaggedOn`
is a **calendar day**, and the source argues the shapes must differ so that "an owner who logged at 20:00
should not be chased at 09:00 the next morning over a calendar-day boundary they had no reason to care
about". This is that sentence happening: logged 18:00, not chased at 09:01.

⚠️ **This is not the auto-expiry reading, and it does not reopen the box above.** That watch expires
**2026-08-25 08:30**, three days after this sweep, and its three claims were already answered on the bench
on 08-21. **The natural expiry is deliberately deferred to after the production deploy** (2026-08-22
decision) — it is a field confirmation of something already proven, not a gate on anything, and nothing in
§4 or Phase 9 waits on it.

⚠️ **The arming will be spent before that.** The watch runs out on its own at 08-25 08:30 whether anyone is
watching or not, so a deploy later than Monday morning arrives to an empty `watches` table. Re-arm by
writing a row straight into the debug database — the recipe is the A/B's, with `endsAt` set a few minutes
out — rather than expecting this one to still be there. **9g destroyed the last arming this way**, and an
arming that expires unobserved is the same loss by a slower route.

---

### 2 — The gate items parked behind that run ✅ 9b closed 2026-08-19 — the last bullet is 9c and stays open

All deliberately after it, because each would disturb the armed course. **All six non-matrix
items are answered, 2026-08-19**, by `scripts/alarm-gate.py` — a driver that taps the write an owner
actually makes and then reads `dumpsys alarm`, because the question is not whether the rebuild is
correct (`DoseAlarmTest` has that, in-process) but whether the **UI write paths reach it at all** on a
phone with a vendor ROM in the loop. Run it with `--only <check>`; it prints one row per reading and
writes them as JSON.

**9b is closed, and it did not close on a tick.** The run found two ways a reminder fails while the app
says it is fine, and the one the app can *read* is fixed in the same branch — `ReminderDelivery.Silent`,
the fourth delivery state, re-driven on the phone at 9/9. The one it cannot read gets corrected wording
and no copy, on the rule written into ADR-0003: **the app speaks when it can read the fact and the owner
can act on it.** Both are recorded in their bullets below, and the reasoning is in
[`phase-9.md`](phase-9.md) §9b.

**The seventh bullet is not 9b's.** The 75-scene edge-to-edge re-run lives at the end of this section
because it shares the reason for being parked, not because it shares the item. It is 9c and is
untouched.

- [x] Writes against the armed course — add, edit, shorten, record and skip a dose; **at most one pending
      alarm** after each, and **none** when nothing is armed.
      ✅ **10/10, `--only writes`.** Ten writes, ten readings, every armed one on the *exact* mechanism
      (`window=0`, `exactAllowReason=permission`, `whenElapsed == maxWhenElapsed`). In order: the seeded
      course armed at today 20:00 → *Given* on the Care tab moved it to tomorrow 08:00 → deleting that
      answer put it **back** to today 20:00, which is the case ADR-0025 calls out and the one a
      point-forward-only rebuild would fail → *Skipped* moved it to tomorrow 08:00 again → removing the
      08:00 chip landed it on tomorrow **20:00**, not tomorrow 08:00 → *End the course* took it to
      **zero** → a new course with one time armed it at 21:00 → adding a second time moved it
      **earlier**, to 20:00 → removing that time moved it back to 21:00 → deleting the course took it to
      **zero** again. Two of the ten end at nothing armed, which is the half a stale alarm breaks in
      silence.
- [x] Bunny-level rebuilds: archive, un-archive, delete a bunny with an armed course. Same invariant.
      ✅ **5/5, `--only bunny`.** Armed at today 20:00 → archive → **0** → un-archive → **1**, back at
      20:00 → delete → **0**. This is ADR-0025's reason for hanging the rebuild off the container's
      writes rather than the medication tables: not one medication row moves in any of the three.
      The delete's second stage counted what goes — *"70 records kept only for this bunny are
      destroyed."*
- [x] Notifications denied / `doses` channel muted → presents as **blocked**, and creating a course still works.
      ✅ **`--only blocked`, and the two are not one state.** Both resolve to `ReminderDelivery.Blocked`,
      and `ReminderCaveats` is right to split them. App-wide denial presents the **point-of-use ask**
      (ADR-0006) — the opt-in block, which explains before it requests — not a caveat sentence; the
      `doses` channel muted on its own presents `doses_state_blocked`, *"Notifications are off, so dose
      reminders will only appear inside the app."* A course was created from scratch in **both** states
      and appeared on the tab. Granting the permission back and switching the channel back on each
      cleared their line.
      ⚠️ **Un-muting a channel does not restore its importance, and the app can never raise it.**
      Switched off and on again through system settings, `doses` comes back at `IMPORTANCE_LOW` (2)
      rather than the `HIGH` (4) it was created with, and `mUserLockedFields=4` — the framework's
      record that a person has touched it, after which an app's `createNotificationChannel` may only
      lower it. Confirmed a relaunch does not help; only `pm clear` puts it back to 4. At importance 2
      a dose reminder posts with **no sound and no heads-up**, and `resolveReminderDelivery` calls that
      state fine, because it only treats `IMPORTANCE_NONE` as blocked. Same shape as 9a's finding: a
      delivery the app describes more confidently than the phone will honour.
      ✅ **Fixed on this branch, as a 1.7 `fix:`** — `ReminderDelivery.Silent`, a fourth state between
      `Blocked` and `BestEffort`, returned for any importance below `IMPORTANCE_DEFAULT` and given its
      own rung in `caveatFor` for both `doses` and `care`. The card points at the *channel's* own
      settings page rather than the app's, because that is the only screen the level can be raised
      from. The cliff is `DEFAULT` and not the channel's own creation level on purpose: a `doses`
      channel lowered to exactly `DEFAULT` keeps its sound and loses only the heads-up, and *"it will
      arrive silently"* would be a false sentence about it.
- [x] The destructive halves of three dialogs (delete visit with its weighing, delete vet, delete bunny counts).
      ✅ **5/5, `--only dialogs`.** The seeded weighing read 2.380 kg on the Weight tab; the visit dialog
      named it (*"A weighing of … was recorded at it"*); the **destructive** branch — *Delete the
      weighing too*, not the *Keep* one a careless run takes — was pressed, and the weighing was gone
      from the Weight tab afterwards. The vet was removed and their name left the visit while the visit
      itself stood, which is ADR-0004's shared-entry rule. The bunny counts are the reading in the
      bullet above, where the deletion was already happening.
- [x] **Reboot twice — autostart granted and autostart denied.** Whatever the denied run says is what
      ADR-0025's self-heal consequence gets reworded to.
      ✅ **8/8, `--only reboot`, and autostart turned out not to be the variable.** A slot two hours out
      was armed through the course editor, the phone rebooted, and the alarm list was read with nothing
      launched: **both** arms came back with exactly one alarm at exactly the same instant —
      `2026-08-19 22:00`, `window=0` — and both were still right after the app was opened. Autostart
      governs whether a *frozen* process is thawed to receive an alarm hours later (9a); it does not
      govern the boot rebuild.
      🔴 **But the rebuild does not happen at boot, and that is the consequence beyond a tick.**
      `--only locked-boot`, the check written once the reboot readings looked too good: with the phone
      **left locked** after a restart there was **no pending dose alarm and no process** at +45 s,
      +105 s and +165 s. The alarm appeared only after the phone was unlocked. The cause is not the ROM
      and not a defect: this device is `ro.crypto.type=file`, and under File-Based Encryption with a
      secure lock screen `ACTION_BOOT_COMPLETED` is not sent when the kernel finishes booting — it is
      sent when the owner's **credential-encrypted storage** is unlocked, which is the first time they
      enter their password. `BootReceiver` cannot opt out with `directBootAware`: it opens the
      database, and the database is in CE storage by definition.
      **So ADR-0025's "the alarm is rebuilt from truth at boot" is wrong on any phone with a lock
      screen**, which is most of them. The accurate sentence is *rebuilt at the owner's first unlock
      after a restart* — and on this phone not even promptly then: it was absent 20 s after the unlock
      and present when next looked at. A phone that restarts itself for an OTA at 02:00 and is picked
      up at 07:00 has **no dose alarm for those five hours**, so a 03:00 dose is not late, it never
      exists. Nothing in the app can detect the state, because nothing in the app is running during it.
      **Two things this needs**, and neither is a code change to `BootReceiver`: ADR-0025 amended to say
      what actually happens, and a decision on whether the delivery ladder should say anything to the
      owner — the same open question as 9a's autostart finding and the muted-channel one above. The
      check now polls after the unlock so the next run puts a number on the latency.
      ✅ **Decided 2026-08-19: the ADR wording only, and no user-facing copy.** The other two findings
      each got a rung because the app can *read* the fact and the owner can *act* on it. This one has
      neither property: nothing is running to detect the state, so anything said about it would be said
      unconditionally, on every phone, forever — a permanent line about a window most owners never sit
      in, which is precisely the wallpaper the delivery ladder is built to avoid. Revisit only if a
      mechanism appears that can tell an owner it *happened*, after the fact, rather than that it can.
- [x] Timezone change: today's answered doses stay answered, no alarm re-armed for a dose already given.
      ✅ **5/5, `--only timezone`.** Armed at today 20:00, answered, alarm moved to tomorrow 08:00; the
      phone moved **six hours west** to `America/New_York`, where today's answered 20:00 becomes an
      instant that has not happened yet. The alarm stayed on tomorrow 08:00 and today's dose stayed
      answered. A rebuild that re-derived slots without carrying their answers across would have armed
      the app to tell someone to double-dose a rabbit.
      ℹ️ **`suggest_manual_time_zone` is not usable from `adb`** — it is guarded by
      `SUGGEST_MANUAL_TIME_AND_ZONE`, which uid 2000 does not hold, and it fails with a
      `SecurityException` while leaving the zone untouched, which reads exactly like a change the app
      ignored. `cmd time_zone_detector set_time_zone_state_for_tests --zone_id <id>` writes
      `persist.sys.timezone` for real; `date` moves with it and so does the broadcast.
- [x] Edge-to-edge matrix re-run ✅ **2026-08-20/21 — 300 cells, 0 errors, no defect.** All **75**
      scenes across four configurations, into `~/binky-screenshots/phase-9/en`. 35 findings, every
      one benign: 28 `touch`, and 7 `drawn` that each have an exact counterpart in the 2026-08-16
      baseline. ⚠️ **The count is `grep -c '^    Scene(' scripts/edge-to-edge.py`, never a number
      written in this file** — that is how 73 went stale twice.
      **Everything this bullet used to warn about was already spent before it was read.** The
      `empty` suite in landscape, `watch-expiry`, `medication-course`, `medication-course-bottom`
      and `record-dose` were all fixed by `011a07d` (2026-08-13) and re-proved by the 2026-08-16
      run three days later — which this run's own reports confirm scene by scene. **A warning
      outlives its defect silently**, and this one had been telling three sessions to distrust
      evidence that was sound.
      **Two driver defects had to be fixed to get the run**, both the same shape and both in
      `swipe_up` — a swipe aimed at the wrong rectangle. Record in [`phase-9.md`](phase-9.md) §9c.

---

### 3 — The document downsample spec ✅ answered 2026-08-14, closed with Phase 7.5

**Done, on the phone against a real scan rather than the fixture.** `Document` stays
`LongEdge(maxEdge = 3000, quality = 92)`: A4 of dense 9 pt text stored **2129×3000, 1.29 MiB**, legible at
1:1 with no ringing or blocking, and quality 92 sits just above the knee — 85 costs fourteen times the
damage to save 18 % of the file. The same sitting settled Phase 7.5's new
**`MediaKind.Observation` = `LongEdge(2048, quality = 88)`** and **disproved** the "closer to `Document`"
hypothesis. The "unverified" comment in `MediaFiles.kt` is gone and the measurement stands in its place.
Reasoning in [`phase-7.5.md`](phase-7.5.md) §2 and §7.

Both were taken **before** real documents piled up, which was the point: `MediaFiles` re-encodes at write
time and keeps no original, so every scan already taken is permanent at the spec in force when it was
written.

---

### 4 — 9h: The Console sitting ✅ production access GRANTED — nothing on Google's side is blocking

**Approved**, recorded 2026-08-19, on the request that went in 2026-08-18 after closed testing ended and
the 12-tester count cleared. **Production is available for the first time**, and the one item in this file
that nothing in the repo could move is gone. Publishing is now entirely a question of when this repo is
ready.

Two holds lift with it, and one does not:

- ✅ **The listing paste is no longer blocked by a reviewer.** The review is over; nothing is sitting in
  front of someone checking the app against copy it does not carry.
- ✅ **1.7 can go to internal whenever it is built.** It was held only so that a *reject* reason could be
  read before the artifact changed. There is no reject reason.
- ⚠️ **The listing still goes up with the build, and only with it.** `store-listing.md`'s nine-language
  copy describes **1.6-and-later** scope while the tracks still serve **1.0.0 / 1.3** — no redesign, no
  multi-valued droppings, and seven of the nine languages are not in those builds at all. That rule was
  never about the review; it is `store-listing.md`'s standing rule and it holds. **Upload the AAB first,
  then paste.** Screenshots the same: prepare them (9g), upload with the build.

#### What is actually blocking the release now, and it is all in this repo

Google is not in the list. In rough order: **9b** and **9c** (the gate items parked behind 9a, and the
75-scene edge-to-edge re-run), **9d–9g** (close Phase 5, the Pages front door, the fluffle, nine locales
of screenshots). ✅ **9i, the field upgrade proof 1.0.0 → 1.7, is closed 2026-08-22** — it was the one
that must not be skipped, being the only thing standing between an existing owner and a refusal screen,
and with it **production is no longer gated on the upgrade assembly**.

Whether 1.7 takes **production** immediately, or goes to internal → closed → production a step at a time,
is an ADR-0009 decision to make at upload — the access being granted does not decide it.

⚠️ **This section said "upload 1.3" until 2026-08-18, and it had been stale for four releases; it then
said 1.7 until 2026-08-22.** The build that goes up is **1.8.0** (versionCode 386) — Phase 9's own plus
9k, carrying everything from 1.4 forward. Every downstream claim moves with it, most importantly the
upgrade proof, which carries because 1.8.0 changes no entity, no migration and no launch gate. Uploading
an intermediate version first spends a release cycle to prove nothing 1.8.0 would not.

**The listing and the build go up together.** `store-listing.md`'s copy describes 1.6-scope features;
putting it on a track still serving 1.0.0 is a listing violation, not a rounding error.

#### Before the AAB goes up

- [ ] **Release notes ×9** — **written 2026-08-21** into [`store-listing.md`](store-listing.md), waiting
      only to be entered. None had ever been needed since 1.0.1, so 1.7 owed the first, and a locale with
      a listing and no note falls back to the default language's, which is worse than terse. ⚠️ **They
      cover 1.1 through 1.6 as well as 1.7**, because whoever takes this update sees this note and no
      other: one scoped to 1.7's own commits would describe the housemates sheet and nothing else. All
      nine are measured under Play's 500 — the first draft put five locales *over* it, which is the
      failure mode Play truncates rather than warns about.
- [ ] **Title / short / full description ×9** — paste-ready in [`store-listing.md`](store-listing.md),
      written at Phase 8 and never yet entered. ⚠️ French and Italian sit at **3992 and 3993 of 4000**
      characters: a paragraph added to English cannot simply be translated into those two.
- [ ] **Screenshots ×9** — **shot and padded 2026-08-21** (9g below), waiting only to be entered.
      Min 2, max 8, **1526×2713** padded from the native 1220×2712, because Play's aspect limit is 2:1
      and the raw capture is 2.22:1. ⚠️ This line said the fill was `#121318` until 2026-08-21 and had
      been wrong since Phase 7 redrew the palette — that is the *pre*-redesign dark surface. Nothing
      read it: `art/pad-screenshot.py` takes the fill from the image's own edge, which is why the sets
      are correct anyway at `#16130D` dark and `#FFF8EF` light, Binky's own surfaces (`theme/Color.kt`).
      Ready at `~/binky-screenshots/phase-9/listing/_play/{light,dark}/<tag>/`; **dark is the set to
      upload**, because it is what the store already shows.
- [ ] **Feature graphic** 1024×500 and **icon** 512², both already in [`art/`](../art/).
- [ ] **Store settings**: category **Lifestyle**; contact email **`binky.support@gmail.com`** — the
      per-app address, *not* the account-level developer one, because `SupportHandoff.kt` hardcodes it and
      the privacy policy defers to it; **Website** ← `https://srednimax.github.io/binky-app/`, which 9e created.
- [ ] **App content, all ten sections** — answers are paste-ready in
      [`play-app-content.md`](play-app-content.md). Data safety must still agree with the privacy policy;
      Play cross-checks the two and a mismatch is its own rejection reason.
- [x] **Artifact checks** ([`RELEASING.md`](RELEASING.md)) — ✅ **re-run all four green 2026-08-22** at
      **1.8.0** against `app/build/outputs/bundle/release/app-release.aab`, 12.3 MB, built by
      `bundleRelease` from `1ee12dd` (`v1.8.0`). `aab-version.py`: **versionCode 386**, versionName
      **1.8.0**, the count matching `git rev-list --count HEAD`. ⚠️ **The build that goes up is 1.8.0,
      not the 1.7 this section named until 2026-08-22** — 9k's merge and release-please's bump moved
      `main` after 9i closed, and the rule two paragraphs down is the one that caught it. 1.8.0 is
      1.7.0 plus 9k, the driver fix and docs: **schema stays 7, no entity change, the migration chain
      and the launch gate 9i proved are byte-for-byte the ones in this artifact**, so the field upgrade
      proof carries. The bundle is **30 KB smaller** than 1.7.0's, which is the debug surface leaving
      `main/`. ⚠️ **This said 377 at `54b2e78` (`v1.7.0`) for twenty minutes.**
      `versionCode` is the commit count, so the two docs PRs that landed after the first build moved
      it — same app code, same 12.4 MB, different number. Rebuilt and re-checked rather than left to
      disagree, because a record that names a versionCode the artifact does not carry is the exact
      failure `aab-version.py` exists to catch. **Rebuild and re-run all four whenever `main` moves
      before the upload**; the number is cheap to refresh and expensive to be wrong about. `aab-permissions.py`: **8 permissions**, every one
      accounted for, **none of the four forbidden**, **0 `<uses-feature>`**. `aab-locale.py`: all
      **5 954** translated strings of all **8** shipped locales present in `base/resources.pb` — it was
      6 058 at 1.7.0, and the difference is 9k's thirteen debug strings per locale leaving the gate —
      it reads `locales_config.xml` and checks every one where it checked only `pl` until Phase 8;
      the ninth is `en`, which *is* the base it compares against rather than a locale it can miss.
      `keytool`: `CN=Maksymilian Sredniawa, O=Binky, C=PL`, the upload key, SHA-256
      `3E:11:8C:FB:…:04:C7`. All three scripts exit non-zero rather than leaving you to read; each
      exists because the corresponding claim was once wrong in a shipped artifact while every
      source-side check was green. ⚠️ **The bundle is not committed and not reproducible by hand** —
      rebuild it at upload time from the tag if this one is stale.

#### The sitting itself

- [x] Upload **1.7** to **internal**, verify, promote to **closed**. ✅ **Done** — and **closed testing
      served it in place on 2026-08-22**, which is what 9i below is read off. **Uploaded 2026-08-21**:
      versionCode **379**, versionName **1.7.0**, on the **internal** track — the first build past
      1.0.0 to reach a track at all. Verify and promote still open. ⚠️ **An internal release needs no
      review to reach a tester**, and the "send changes for review" banner is the *listing and app
      content* queue, not this one; what does gate it is the rollout actually being started rather
      than left a draft, the device's account being on the testers list, and that account having
      accepted the **opt-in URL**. Google takes a few minutes to process the upload before the track
      will serve it.
- [ ] Countries/regions, pricing (free), ads declaration (none).
- [ ] Production, **if** the count has cleared — whether 1.7 takes it is an ADR-0009 decision made then.

#### 9i — then, and only then, the field upgrade proof

- [x] **1.0.0 → 1.7**, every row 1.0.0 wrote still there. ✅ **Closed 2026-08-22** — in two runs, one
      for the migrations and one for the assembly; both results are below. ⚠️ **Those rows are dummy
      data, not real bunny
      history** — this line claimed otherwise until 2026-08-21. It does not weaken the proof, which asks
      whether the chain preserves what the old build wrote and does not care what the rows mean. It does
      move the coverage into your hands: **whatever tables the diff should cover have to be filled while
      the phone is still on 1.0.0**, because there is no way back down once 1.7 lands.
      The Xiaomi's Play build is on **1.0.0**, not the 1.0.1
      4h assumed, so the chain crosses **all three** hand-written migrations — `MIGRATION_4_5`,
      `MIGRATION_5_6`, `MIGRATION_6_7` — and the launch gate ADR-0023's Phase 7.5 amendment rewrote. It
      cannot run locally: the installed build is Play-signed and a local APK is refused on signature
      mismatch, so the update must **arrive from a track**, downstream of the upload above. This is the
      standing gate's item 5, on the release it matters most for.

      **How the diff is read: [`scripts/upgrade-diff.py`](../scripts/upgrade-diff.py) `before.zip`
      `after.zip`.** ⚠️ **Not a `bunny.db` pulled off the phone** — a release build is not debuggable,
      `adb shell run-as` is refused, and there is no route to that file at all. The **backup export is
      the route**, and a faithful one: the archive carries the raw database and `BackupExporter`
      checkpoints the WAL before zipping (true at `v1.0.0`, not only on `main`, which is what makes the
      *before* image trustworthy). The script asserts five things in the order they break — `user_version`
      climbed, no table vanished, every row survives on the columns the two schemas **share**, the
      droppings values `MIGRATION_6_7` *moves* arrived in the join tables that replaced them, and the
      media files survived. **The fourth cannot be a column diff**: those columns are gone from both
      sides by definition, and it is the only place in the whole chain where an owner's data changes
      tables. Exits non-zero on any of them.

      ⚠️ **Read both results below before reading the paragraph above.** The plan it describes is what
      happened on the **second** run, 2026-08-22, and not on the first: on 2026-08-21 the rows reached
      1.7 by a hand restore, because the internal track's opt-in page had already taken the install with
      it. The paragraph is kept because its reasoning is what both runs were read by.

- [x] **The migrations, proven on the phone against real 1.0.0 rows** ✅ **2026-08-21**, clean:

      ```
      user_version   4 -> 7        tables 8 -> 20
      bunnies 2, fluffles 1, observation_symptoms 2, observations 39,
      photos 5, symptoms 13, trend_acknowledgments 0, weights 52   — every row present
      observations.droppingsForm  36 value(s) -> observation_droppings_appearance
      observations.droppingsSize  36 value(s) -> observation_droppings_sizes
      media files 5 -> 5
      ```

      All three hand-written migrations ran on the Xiaomi against rows a real 1.0.0 build wrote, and
      **nothing was lost**. The two symptom ticks are the ones that matter most: they are what
      `MIGRATION_6_7`'s cascade would have taken, and what Room's own `runMigrationsAndValidate` would
      have passed over without a word. The 36 + 36 droppings values are the second: the only place in
      the chain where data physically changes tables. `trend_acknowledgments` is empty on both sides,
      so it is carried but untested.

      **The evidence is two files, and they are outside the repo**:
      `~/Downloads/bunny-everything-20260821T201940Z.zip` (schema 4, taken 22:19 off the 1.0.0 install)
      and `~/Downloads/bunny-everything-20260821T213110Z.zip` (schema 7, 23:31). Re-run with
      `python3 scripts/upgrade-diff.py <before> <after>`. They are ~220 KB of dummy rows and five
      photos, kept out of git for the same reason the 72 screenshots are — but **the before image
      cannot be produced again**, so do not delete it.

- [x] **The in-place update path — the assembly itself** ✅ **2026-08-22**, from the **closed** track,
      clean. This was the box that gated production, and it is shut. It had been deferred on 2026-08-21
      with the reasoning below, which is kept because it is what the run was read by.

      **What the first run could not answer.** The rows got into 1.7 by **restoring the schema-4 export
      by hand**, not by Play swapping the APK under an existing data directory. Each *component* was
      already covered: the migrations by the committed fixtures in CI at API 26/34/36; ADR-0023's launch
      gate *on upgrade* by `SchemaGateTest`, which asserts
      `schemaGateDecision(onDiskVersion = 4, appSchemaVersion = 7)` lets the owner in and that 4→7, 5→7
      and 6→7 all have paths; and the migrations against real 1.0.0 rows on real hardware. ⚠️ **An
      earlier draft of this box said "the gate on upgrade is not proven" and that was wrong** — it is
      unit-tested, and it is what stood in front of the 1.5 near-miss. What was untested was the
      **assembly**: Play replaces the APK, the process cold starts, and the gate reads an on-disk
      version out of an *installed* database rather than a constructed one.

      **What was done.** 1.0.0 taken fresh off **production** (`firstInstallTime` 2026-08-22 01:01:43),
      then the 2026-08-21 schema-4 export restored into it **through 1.0.0's own restore path**, so the
      before-image is a database 1.0.0 wrote and not a file dropped into place. Exported 10:35:40 local.
      The **closed** track's update taken thirty seconds later — `lastUpdateTime` 2026-08-22 10:36:10,
      `installerPackageName=com.android.vending`, versionCode **379**, versionName **1.7.0**. Exported
      again 11:38:19. The app opened, and the diff is empty:

      ```
      user_version   4 -> 7        tables 8 -> 20
      bunnies 2, fluffles 1, observation_symptoms 2, observations 39,
      photos 5, symptoms 13, trend_acknowledgments 0, weights 52   — every row present
      observations.droppingsForm  36 value(s) -> observation_droppings_appearance
      observations.droppingsSize  36 value(s) -> observation_droppings_sizes
      media files 5 -> 5
      ```

      ⚠️ **`firstInstallTime` ≠ `lastUpdateTime` is the whole proof, and the diff alone is not.** An
      uninstall-and-reinstall sets the two equal; these are nine and a half hours apart, so the data
      directory was never wiped and the migration ran over rows already on the disk. A hand restore
      produces **exactly** the same table counts — which is how 2026-08-21 read as a pass for something
      it had not tested. Whoever repeats this: read the install times off `dumpsys package`, not only
      the script's output.

      **The evidence is two more files outside the repo**:
      `~/Downloads/bunny-everything-20260822T083540Z.zip` (schema 4) and
      `~/Downloads/bunny-everything-20260822T093819Z.zip` (schema 7). Re-run with
      `python3 scripts/upgrade-diff.py <before> <after>`.

      ✅ **The closed track behaved as this file predicted**: an ordinary in-place update, with no
      uninstall asked for. That is what made the run possible at all, and it is the reason to keep
      using it. **The release-shaped debug build** ([`phase-7.5.md`](phase-7.5.md) §7) was the planned
      fallback and was not needed; it stays the bench route for any future bump, since it needs no
      track, no opt-in page and no fresh 1.0.0.

      ⚠️ **How the Play install was lost, so it is not repeated.** The **internal track's opt-in page
      instructs the tester to uninstall the current build first**, and an uninstall wipes the data
      directory. That destroyed the original 1.0.0 install (`firstInstallTime` 2026-07-29 → 23:14, then
      → 23:29 for a *fresh* 1.7). **The closed track does not ask for this** — it delivers an ordinary
      in-place update — which is the track to use for an upgrade proof. The export taken at 22:19 is
      the only reason any of this was recoverable, and it is the argument for exporting *before*
      touching a track, not after.

### 5 — Phase 6: the support contact ✅ closed 2026-08-16, ships as 1.3

**Done** — 6a, 6b, 6c and 6d built, driven on the device and written up. The record is
[`phase-6.md`](phase-6.md); `PLAN.md` ticks Phase 6 and `v1.3.0` is tagged. The two boxes that outlived the
code were the oldest in the project and shut a day apart: Play's **per-app contact email** (2026-08-15), so
the app, the listing and the privacy policy name one inbox; and a **support mail read after it arrived**
(2026-08-16), landing in the inbox proper with the diagnostics block **visible** rather than collapsed
behind Gmail's signature `…`, which was the whole claim and only a delivered message could prove it.

⚠️ **The first delivered mail was filed as Spam**, which is silent on both ends — the sender sees a sent
message and the maintainer an empty inbox. Fixed by a `subject:bug OR subject:feature` → *Never send it to
Spam* filter on the receiving account, and **one rule covers all nine languages** because the `#bug` tag is
a Kotlin constant rather than a string resource (`SupportHandoff.kt`).

**1.3 supersedes 1.2.0 on the tracks — do not upload both.** Same schema 6, same two hand-written
migrations, so §4's field-upgrade proof retargets to **1.0.0 → 1.3** and still crosses `MIGRATION_4_5` and
`MIGRATION_5_6`. Uploading 1.2.0 first buys a release cycle and proves nothing 1.3 would not.

---

### 6 — Phase 7: the redesign ✅ closed 2026-08-13, ships as 1.4

**Done.** The record is [`phase-7.md`](phase-7.md) — the per-route checkpoint table, the idiom the sweep
built (`Surfaces.kt`, `Forms.kt`, `Dialogs.kt`), the four new-functionality decisions, the 244-scene matrix
result, and the before/after comparison. `PLAN.md`'s status list is ticked.

**One thing left the phase rather than closing in it:** the **Polish after set**, moved to Phase 8 on
2026-08-13 because it turned out to need a *translation* tool rather than a capture — the scene needles are
English string literals, so `--locale pl` switches the app and then every scene fails at its first tap. It
is §7's first box, along with the two driver findings that came out of this phase's captures.

**§4's Play screenshots are unblocked by this** — they were waiting on the redesign so they would not be
taken twice. The screens they photograph are now final.

---

### 6.5 — Phase 7.5: the interlude ✅ closed 2026-08-18, ships as 1.5

**Done and released.** `v1.5.0` was cut 2026-08-16 at schema **7**, frozen and tagged (`schema-7` →
`ddb430a`). The record is [`phase-7.5.md`](phase-7.5.md). The phase owned no boxes of its own — it was the
*order* over five that were already open here, and all five close with it: §3, §5, §8, §9 and §7's
capture-driver box.

**What it shipped**, all built and device-proven in both locales: ADR-0028's **gain signal** against a
six-month anchor; ADR-0029's **multi-valued droppings and the tray photo**, which is what took the phase
from migration-free to `MIGRATION_6_7`; **licence attribution** over 201 artifacts with the texts bundled;
both **downsample specs** settled on the phone; the **healthy day** moved behind the `+` so there is one
entry point rather than two; the **housemates line** capped at five bunnies; and the **capture driver**
taken from English-only to scene isolation, seed variants and resource-resolved needles — **146/146 in
Polish** (the after set Phase 7 carried out) and **292 cells, zero errors** in English.

🛑 **The most valuable hour of the phase was not on its list.** Asking what a real owner meets when 1.5
lands on a phone holding 1.4.0 data found the **launch gate refusing every schema-bumping update** — *"This
version cannot open the records on this phone"* and a dead end, with `MIGRATION_6_7` never running because
the gate returns before Room is constructed. The same shape shipped at 1.1 and 1.2. It is
`schemaGateDecision` (`SchemaGate.kt`) now, `SchemaGateTest` is its truth table, and ADR-0023 carries the
amendment — which is why the standing gate at the top of this file has a fifth item no test can satisfy.
Both live upgrade paths were then watched on the phone — 1.4.0 → 1.5, and the skipped-version
**1.1.0 → 1.5** — compared table by table on *common columns*: **zero differing rows, both times**.
`bunny-schema-6-fixture.zip` is in, written by the `v1.4.0` tag's own container, and the instrumented suite
reads **216/216** on the Xiaomi.

⚠️ **Two traps to expect again at schema 8**, both in [`phase-7.5.md`](phase-7.5.md) §7. The first run after
a bump fails every `assertTrue(armed())` case in `DoseAlarmTest` and it is **not** a regression: the phone's
*real* `bunny.db` is still at the old version and the background guard reads it — clear
`databases/bunny.db{,-wal,-shm}` through `run-as`, never `pm clear`, which takes the runtime permissions the
rest of the suite depends on. And the debug build **wipes rather than migrates**, by design
(`BunnyDatabase.kt` gives a build the fallback *or* the migrations, never both), so the phone is not where a
migration is proven — `aReleaseShapedOpenOfASchemaSixFileSucceeds` is.

**Commit rule carried over from Phase 7: `feat:`/`fix:`, never `feat!:`.**

---

### 7 — Phase 8: nine languages ✅ closed 2026-08-18, ships as 1.6

**Done.** Nine languages shipped, nine listings written. The record is [`phase-8.md`](phase-8.md) — a
block per drafted language with the traps it priced, each ending in the pre-decided fallbacks that stand
in for a native read-through under
[ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md).

The last box was the **capture driver's re-proof**, and it closed on what it found rather than on what it
went looking for. The needle table survived eight files of reworded strings intact: 39 of 45 resolve in
all nine locales, zero ambiguous, and the six literals are all sample data. The defect was in the driver.
`--locale` fed one spelling of a locale to two things that spell it differently, so `pt-BR` crashed —
and the workaround is worse than the crash, because `cmd locale` **accepts** `pt-rBR` and stores the
language as `rbr`, which would have driven an English app against a Portuguese needle table and called it
a pass. **The failure mode of a two-spelling locale is not a crash, it is a green run on the wrong
language**, so the guard belongs where the tag is taken rather than where it is used.

Three claims that needed a device rather than a test are proven, and one of them became a test anyway:

- **The switcher**, tapped through all nine, each landing in its own language. An endonym bound to the
  wrong enum entry is green in every test in this repo and ships two wrong languages.
- **The fallback** for a language Binky does not ship: `nl` pinned, strings back from `values/`,
  `gap_days` rendered through English's own rule. Numbers and dates stay local, which is correct.
- **Plural selection at 1, 2, 5 and 22** — now `PluralSelectionTest`, instrumented, because CLDR's rules
  live in the platform and `TranslationTest` can only prove a category is *declared*. Czech's `many` is
  for fractions alone, so `5 dní` is right where `5 dne` looks right.

**No locale introduces an edge-to-edge finding English does not already have**, and copy length is ruled
out by measurement rather than argued: the one varying overlap is *smaller* in German (39 px) than in
English, Polish or Ukrainian (48 px).

⚠️ **`scripts/aab-locale.py` checked `pl` and only `pl`** — the script that exists because 1.0.1 shipped
without Polish reaching the artifact at all. At nine languages that is eight going to the tracks
unverified against the exact failure it was written for. It now reads `locales_config.xml` and checks
every shipped locale; `RELEASING.md` invokes it bare.

**What outlived the phase is in §4** — the nine listings' screenshots, and the rule that listing copy and
the build it describes go up together.

---

### 8 — Open-source licence attribution ✅ built 2026-08-14, shipped in 1.5

**Done**, and the mechanism was the real question. **`app.cash.licensee`** at build time, rendered by the
app's own Compose screen — not Google's `play-services-oss-licenses`, which would have put a **second**
Play-services library into a project that quarantines its first one behind an interface (ADR-0009), and a
stock Activity into a redesigned app. Build-time only, so ADR-0009 is untouched.

A row on Support's last card opens *Open-source licences*: **201 artifacts** in the release variant under
four licences, generated **per variant** so the screen names what *this* binary contains, with the
Apache-2.0 and BSD-3-Clause **texts bundled** — Apache-2.0 §4 asks for the licence to travel with the
binary, and a URL does not travel. Google's SDK and ML Kit terms are not ours to redistribute and link out
instead.

⚠️ **The generator found a licence nobody knew was in the build** on its first run — `BSD-3-Clause`, over
exactly one artifact. A hand-typed list would have been wrong the day it was typed. `LicencesTest` reads
`build.gradle.kts` against the asset directory, because `allow("X")` has a build failure behind it and
`assets/licences/X.txt` has nothing. Details in [`phase-7.5.md`](phase-7.5.md) §3.

---

### 9 — A weight *gain* raises nothing ✅ answered and built 2026-08-14, shipped in 1.5

Found by a tester 2026-08-09: a bunny putting on *"5 kg plus"* produced no flag, because `WeightTrend.kt`'s
trigger was one-sided **by design** — loss is the acute, hours-matter signal ADR-0001 was written about, and
the loss baseline is deliberately rise-resistant so a lasting gain cannot mute every later drop.

**Decided in [ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md) and built the same
day.** A gain raises the same flag against a **six-month anchor** rather than the loss rule's baseline,
because gain is chronic where loss is acute; the copy states a fact about the numbers, in grams, and never a
verdict about the rabbit (ADR-0026, ADR-0001 — *health features observe, they never advise*). `TrendDrop`
became `TrendChange` and carries a direction. Reasoning in [`phase-7.5.md`](phase-7.5.md) §1.

- [x] **9j — replied 2026-08-21.** Owed since 08-09, and it was never the feature. Their *"5 kg plus"*
      was a **number, not a change**: a Flemish Giant is legitimately 6–10 kg, so any absolute weight is
      wrong for some breed, and Binky will never call a weight too high — only say that it moved, by how
      much, since a date. ADR-0028 shipped the gain signal they were actually asking for; the reply is
      the half that explains why the app will not do the thing they literally asked for, which is better
      said than left to be discovered.

---

### 10 — Phase 9's index, and the four items that are new

[`phase-9.md`](phase-9.md) is the reasoning; these are the boxes. **Ships as 1.7, schema stays 7** — no
entity changes, so the standing gate at the top of this file does not fire in this phase.

| | What | Boxes |
| --- | --- | --- |
| **9a** | The overnight Doze run ✅ answered 2026-08-19 — autostart is the lever, and the delivery state was fixed to say so. **§1 is closed**: the Phase-4 carry's watch half was read 2026-08-21 as a bench A/B, 9g having destroyed the 08-22 arming | §1 |
| **9b** | The six gate items parked behind it ✅ **closed 2026-08-19** — it found that the boot rebuild waits for the first unlock, and that a lowered channel was being reported as armed; the second is fixed in the same PR | §2 |
| **9c** | The 75-scene edge-to-edge re-run ✅ **closed 2026-08-21** — 300 cells, 0 errors; it found two driver bugs and that its own warnings had gone stale | §2, last bullet |
| **9d** | Close Phase 5 | below |
| **9e** | The Pages front door ✅ **closed 2026-08-19** — `docs/index.md` is the root, and `_config.yml`'s "copied verbatim" comment was wrong | below |
| **9f** | Seeing the whole fluffle ✅ **closed 2026-08-20** — the sheet is built, tested and driven; the archived route and the landscape half-height state were both found on the phone | below |
| **9g** | Nine locales of screenshots ✅ **closed 2026-08-21** — 72 padded PNGs, and it found a doubled full stop in Ukrainian that no test could see | below |
| **9h** | The Console sitting ✅ production access granted 2026-08-19 — the release is repo-side only now | §4 |
| **9i** | The field upgrade proof 1.0.0 → 1.7 ✅ **closed 2026-08-22** — the in-place assembly, from the closed track, over a real 1.0.0 data directory; it found that a clean diff cannot on its own tell an in-place update from a hand restore | §4 |
| **9j** | The tester's reply ✅ **replied 2026-08-21** | §9 |
| **9k** | The debug affordances that ship anyway — swept 2026-08-21, one finding, and ✅ **built 2026-08-21** on `chore/9k-debug-affordances`; the branch waited for 9i, the code did not, and 9i closed 2026-08-22 | below |

**Four edges must not be reordered**, and everything else is free: ✅ **9i before 9k's *merge*** — spent.
The distinction is the whole of it: the code built on `chore/9k-debug-affordances` changed no artifact
while it sat on a branch, and what had to wait was that branch *landing on `main`*, because 9k changes
what is in the artifact and 9i is the proof about *this* artifact. **9i closed 2026-08-22**, so the merge
is free; **9a before 9b and 9c**, because both
disturb the armed course and the run costs a night; **9f before 9g**, because 9g photographs a screen 9f
changes; **9g and the listing copy before 9h before 9i**, because the upgrade proof needs an update that
arrives from a track.

#### 9d — Close Phase 5

- [x] Write 9a's and 9b's results into [`PLAN.md`](PLAN.md)'s 5a / 5i / 5j entries and **tick Phase 5**.
      ✅ **Done 2026-08-21.** It had been the one unticked box since 2026-08-05 while four later phases
      closed around it. The carry it was waiting on — §1's watch half — was read the same evening, so the
      tick lands over evidence rather than over an intention, and no carry had to be tracked outside Phase 5
      to get it. Two stale markers were corrected on the way past: **5b** still read "not closed" over a
      bullet 5j had already written, and **5i** still read "in progress, 2026-08-05". A phase cannot honestly
      be ticked over checkpoints whose own text contradicts their marker.

#### 9e — The front door ✅ closed 2026-08-19

`docs/` is served by Pages from `main` and **had no `index.md`**, so the site root was a 404. Probed
2026-08-18: `/` → **404**, `/privacy-policy.html` → **200**, `/PLAN.html` → **200**, `/DOD.html` → **200**.
Nothing was broken; Play's privacy-policy link has always worked. There was simply no page at the root,
and the root is what anyone types.

- [x] **`docs/index.md`** with front matter: what Binky is, the privacy policy, the support address, a
      link to the repo. Not a site. It is also the URL for the listing's empty **Website** field (§4):
      `https://srednimax.github.io/binky-app/`. Written from `README.md` and the English full description
      in [`store-listing.md`](store-listing.md), so the front door and the listing say the same things —
      including *a record, not a diagnosis*, which is the one paragraph that must not be softened for a
      landing page (ADR-0001).
- [x] **Corrected `_config.yml`'s comment.** It claimed a Markdown file without front matter is "copied
      verbatim rather than rendered", and offered that as the reason planning documents are safe to leave
      in a published directory. Pages injects default front matter, so **every `.md` in `docs/` renders as
      a themed, crawlable page** — `PLAN.html` and `DOD.html` above are the proof. The comment now says
      that, and says what actually makes the directory safe: the repo is public and holds nothing that is
      not already on GitHub, so **anything that must not be published must not be in `docs/` at all.**
- [x] **Re-probed after the merge ✅ 2026-08-19.** `/` → **200**, served as rendered HTML with
      `<title>Binky — a health record for your rabbit</title>`, and Pages' `jekyll-relative-links`
      resolved the page's `privacy-policy.md` link to `/binky-app/privacy-policy.html`, which is a 200
      too — so the one link on the front door that matters works. **The Website field is unblocked**
      (§4). Original wording kept below because the reasoning is the reusable part: Pages builds from `main`, so the 404 above only becomes a 200
      after the merge — and the Website field must not be pasted into the Console before it is
      (§4 is downstream of this, not of the branch). Re-probed from the branch on 2026-08-19 and the
      baseline still holds — `/` **404**, `/privacy-policy.html` **200** — so a 200 at the root is a
      real signal that the page went live rather than a stale cache. One
      `curl -sS -o /dev/null -w '%{http_code}' https://srednimax.github.io/binky-app/`; the Pages build
      takes a minute or two to land after the merge.

#### 9f — Seeing the whole fluffle ✅ closed 2026-08-20

`housematesLabel` names **two** and folds the rest into "& N others" (`BunnyLabels.kt:60`, from four up).
The cap is right — it exists because the line grew the card without bound — but with five housemates the
owner **cannot see who three of them are, anywhere in the app**.

- [x] **Tap the "Lives with" line on Home's profile header** → `HousematesSheet.kt`, a modal bottom sheet
      titled *Lives with*, one row per housemate — avatar, name, `(archived)` where it applies — and
      tapping a row switches to that bunny. **Zero new strings**, as aimed for.
      The line is now a `Row` carrying the label and the dashboard card's own chevron, wrapped in
      `minimumInteractiveComponentSize()`: a tappable line that looks like the two inert lines above it
      is a feature nobody finds, and a one-line label is half of Material's 48dp target. Driven on the
      Xiaomi 2026-08-20 against the `crowded` seed — four housemates, one archived, all four listed.
- [x] **Leave the other two sites alone.** Both still render the plain `housematesLabel`.
- [x] **A test that the sheet lists *every* housemate, archived included** — `housematesInSheet` and two
      cases in `HousematesTest`, asserted *against* `capHousemates` so that reusing the cap here goes
      red. `capHousemates`' own table is unchanged.

**Two things the device found that no test would have.**

1. **The archived path is not the switcher's navigation**, and using it would have been a bug. An
   active housemate is `selectBunny` (persisted); an archived one is `openArchivedScope` — in memory
   only, because ADR-0015 forbids reopening the app into a memorial. And `resolveSelection` gives the
   archived scope **outright precedence**, so the reverse trip needs `closeArchivedScope()` *first* or
   selecting a live housemate from an archived bunny's profile writes the choice and leaves the screen
   where it was. All four transitions watched on the phone.
2. **The sheet opened half-height in landscape**, showing the same two housemates the line already
   named. Fixed with `skipPartiallyExpanded = true` — expanded is the content's own height, so portrait
   is unchanged and landscape now opens showing all four. Re-checked at 2712×1220.

**A sheet, not a tooltip**, and not for style: M3's `TooltipBox` is long-press-only on touch so the
affordance is invisible, dismisses on any touch elsewhere, cannot scroll at eight housemates, cannot be
**tapped through** to the bunny, and is the one element the capture harness could not photograph — it
would ship with no screenshot evidence in any of the four configurations. Expanding the line in place
re-introduces exactly the unbounded card growth the cap was written to stop.

⚠️ **Aim for zero new strings.** The sheet title is `R.string.bunny_lives_with_label` and the archived
suffix `R.string.bunny_archived_name`, both already translated in all nine. A phase that adds no English
string owes the translation gate nothing; if one turns out to be needed it ships in all nine.

#### 9g — Nine locales of screenshots ✅ closed 2026-08-21

- [x] **Nine locales × four scenes × two themes**, `home` / `weight` / `observations` / `backup`,
      through `screenshots.py --locale <tag>` at ~7 min a locale. 72 native captures at 1220×2712 and
      72 padded at exactly 1526×2713, in
      `~/binky-screenshots/phase-9/listing/<tag>/{light,dark}/` and `…/listing/_play/{light,dark}/<tag>/`.
      Every locale reached all four scenes; no skips, no errors. This improves the listing rather than
      unblocking it — Play falls back to the default listing's set — which is why it waited until the
      tracks could carry the build it describes.

⚠️ **"No errors" was the driver's opinion, and in two locales it was wrong.** `cs` and `uk` shot
**Home with the record-day sheet half-open over it** where `observations` should have been, in both
themes — found by eye on 2026-08-22, four days after the run was recorded clean. `find` matched
needles by *substring*, and the Czech tab label `Pozorování` sits inside the shell "+"'s description
`Zapsat pozorování`, as `Спостереження` sits inside `Записати спостереження`. The FAB is the smaller
node, so the smallest-match rule tapped it, the sheet counted as "the screen moved", and the scene
passed. **The other seven locales are clean by accident of grammar** — English *Observations* is not
inside *Record an observation* — which is the same shape as 1.5's `29d442d`, *"a collision only
Polish can express"*, and of the ampersand defect only English could express.

The padding carried the same defect twice over: `art/pad-screenshot.py` samples the image's own edge,
the edge was under the sheet's scrim, so both files padded to `#ADA8A2` instead of `#FFF8EF` in light
and `#0F0D09` instead of `#16130D` in dark. Glaring in light, invisible in dark.

- [x] **Fixed and re-shot 2026-08-22.** `edge-to-edge.py` gained `TAB_NEEDLES`: a tab is matched
      **exactly, on text only** (`find(..., exact=True)`), and the tap is not believed until
      `showing_tab` sees that tab reporting itself selected. **The assertion is the load-bearing
      half** — exactness stops this bug, but "the screen moved" was never a claim about *arriving*,
      and without the check the next collision shoots another wrong screen just as quietly. Eight
      files replaced (`{cs,uk}/{light,dark}/observations.png` and their four in `_play`), all
      1526×2713 with the right fill, all within 8 KB of the seven good locales. The two manifests
      carry the new byte counts and a note.

⚠️ **The debug app was not installed when the re-shoot started** — only the Play 1.0.0 install, which
9i needs and which nothing here touched. `installDebug` put it back without a HyperOS refusal. The
first run then failed anyway: **a per-app locale does not stick on an app that has never been
launched.** `cmd locale set-app-locales` reported success, `get-app-locales` came back `[]`, and the
Czech run shot an English setup wizard. One launch fixes it for good. Worth knowing before any capture
run that follows a fresh install.

**English was a run, not a selection**, and that line had gone stale in the day between being written
and being read. It said the 63 scenes in `~/binky-screenshots/phase-7/after/` were already final; **9f
changed Home's profile header on 2026-08-20** and that set is from 08-13, so its `home.png` is missing
the chevron and the tappable *Lives with* row — on the one screenshot that leads the listing. Shooting
English cost seven minutes and the check cost one crop. **A "no run needed" claim about a screen is
only as old as the last commit that touched it.**

`--locale en` had also never been runnable: `load_strings` built `values-<qualifier>` from the tag and
English is the base in `values/`, so it died before the first tap. Fixed in the same branch. Without
it English inherits the phone's system language, which here is Polish.

**What the run found, and only a run could:** Ukrainian rendered `7 серп. 2026 р..` — its CLDR medium
date pattern ends in a period and six strings appended a sentence one. Fixed in the same branch and
`uk` re-shot; the detail is in the header of `values-uk/strings.xml`. Every one of those strings is
well-formed on its own, which is why `TranslationTest` is green and always was: **the defect only
exists once a formatter and a translation meet on screen.** The other eight locales were clean — no
truncation, no overflow, no clipped control.

---

#### 9k — The debug affordances that ship anyway ✅ built 2026-08-21, **held for merge until after 9i**

**Swept 2026-08-21 across the whole repo. One finding, and it is hygiene rather than a hole.**

**All three boxes are built and proven on `chore/9k-debug-affordances`, and the branch is deliberately
not merged.** The ordering rule below is unchanged and it was never about when the code could be
*written* — it is about what is in the artifact 9i proves. An unmerged branch changes no artifact, so
the work waits where waiting costs nothing rather than in a session that has to rediscover it.

- [x] **Move `SampleData.kt`, `DebugReminder.kt` and `SettingsScreen`'s `DebugSection` into
      `app/src/debug/`**, which already exists, leaving a no-op seam in a `release` source set so
      `SettingsScreen` still compiles. They sat in `main/` behind `if (BuildConfig.DEBUG)` at the call
      site (`SettingsScreen.kt:179`) — a **runtime** guard, and `isMinifyEnabled = false` for release
      means R8 never runs to strip the dead branch, so the seeder and the two-minute reminder were
      **compiled into the release AAB**, unreachable. The seam is now
      `ui/settings/DebugSettings.kt`, one file per variant: `src/debug/` composes the section,
      `src/release/` is `@Composable fun DebugSettings() = Unit`, and `SettingsScreen` calls it with
      no flag at all. `SettingsViewModel` gave up `SampleDataOutcome`, `seedSampleData` and
      `clearSampleDataOutcome` to a debug-only `DebugSettingsViewModel` — a second ViewModel on one
      screen, against the house rule and argued in the file, because folding the state back would put
      exactly what was removed straight back into `main/`.
- [x] **Drop the 13 debug strings from the gate.** `settings_sample_data*`, `settings_debug_*` and
      `debug_reminder_notification_*` (`values/strings.xml:294–306`) carried no `translatable="false"`, so
      they were inside the 693 and **fully translated into all nine** — developer-facing copy paid for nine
      times, shipping in the release resource table and inside `aab-locale.py`'s scope, a check that exists
      to catch a *missing* translation. They moved to `src/debug/res/values/strings.xml`, which is outside
      both scripts' `app/src/main/res` scope, and out of all eight `values-*/`. **693 → 680 × 8, gate
      green.** They carry `translatable="false"` there anyway, for Android lint rather than the gate:
      lint reads the *merged* debug resources, saw thirteen unqualified strings with eight translated
      siblings and failed the build with thirteen `MissingTranslation` errors.
- [x] **Revisit `isMinifyEnabled`** — and the revisit's answer is that it stays off, recorded in
      `build.gradle.kts` in place of the stale "Revisit at 1.1" note. The *reason* R8 was wanted here is
      gone: the source-set move excludes what R8 would have stripped. The *condition* is not met and its
      subject moved — "against a known-good 1.0" becomes a known-good **1.7**, which is not on a track
      yet — and it cannot be met on the bench either, because a release build cannot be installed over
      the Play one (signature mismatch), so a green `assembleRelease` is not evidence about a phone.

⚠️ **The branch does not merge before 1.7 is up and 9i is watched.** Adding a source-set divergence to
the artifact you are about to prove is the mistake the same file already reasoned its way out of once —
R8 is off precisely because "a sixth divergence whose failures are release-only, runtime and
reflection-shaped is the opposite of what this checkpoint proves". **After 9i**, for the same reason:
9i is a claim about the artifact that goes up, and this changes it.

**Closed by measurement, not by reading the diff**, and the how is in
[`phase-9.md`](phase-9.md#9k--the-debug-affordances-that-ship-anyway): zero occurrences of the moved
classes in the release dex and of the thirteen names in its `resources.arsc`, both variants built; the
section, the seeder and the two-minute reminder all exercised on the Xiaomi; and `edge-to-edge.py` taught
about the overlay, which is the one thing the move nearly broke.

**No safety hole, and the sweep is why that can be said rather than assumed.** `BuildConfig.DEBUG` is
false in a release build, so the section never composes and the seeder — which writes through the
repositories — has nothing to reach it. The two-minute reminder posts on `ReminderChannel.Care`, an
existing production channel, so it leaves no stray channel in a user's notification settings either.

**What the sweep cleared, so it is not re-swept:**

- **The manifest.** `main/` declares exactly **one** exported component, the launcher activity. Every
  receiver, provider and service is `exported="false"`, each with the comment saying why. `src/debug/`'s
  `SeedVariantReceiver` is `exported="true"` on purpose and variant-scoped, so the release build has no
  such file to merge.
- **Logging.** No `Log.d`, no `Log.v`, no `println`, no StrictMode. The five `Log.w`/`Log.i` calls are all
  on genuine failure paths — photo import, scan storage, the ML Kit fallback — and belong in a release.
- **The other three `BuildConfig.DEBUG` uses are legitimate behaviour, not leakage.** WorkManager's
  logging level (`BinkyApplication.kt:100`); `destructiveMigrationAllowed` (`BunnyDatabase.kt:124`), which
  is ADR-0023's whole point; and the `-debug` marker in the support email's version label
  (`SupportHandoff.kt:92`), which exists so a report says which build sent it.

### Already proven — do not re-run

1.2.0 tagged (`v1.2.0` → `4097448`) and verified against the **artifact**: versionName 1.2.0, versionCode
211, upload key, 709/709 Polish strings, 8 permissions with none of the four forbidden, zero
`<uses-feature>`. Schema **6** frozen and tagged (`schema-6` → `01a769e`); both the schema-4 and schema-5
fixtures migrate to 6 in CI on every PR at API 26/34/36. Lint **0 errors, 0 warnings**. ~201 instrumented
tests green on the Xiaomi. ADR-0021 from both sides, both delete dialogs, the two-page document surviving
a process restart, the *Care & Meds* label, ADR-0026's line, and the *skipped*/*missed*/*overdue* string
audit — all checked on the device at 5i.
