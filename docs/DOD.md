# Definition of done — what is still open

The **live checklist**. `PLAN.md` holds the reasoning and the record; this file holds only what is not
yet ticked, so a session can pick up the work without loading 3 000 lines. Keep it short: when an item
closes, tick it here, write the *result* into `PLAN.md`, and delete the detail from this file.

## The standing schema gate — never ticked, checked at every bump

**An update must migrate an existing install without losing anything.** This one does not close with a
phase. Whenever `BUNNY_SCHEMA_VERSION` changes, all five hold before the release goes out:

1. `MIGRATION_x_y` written **and registered in** `BUNNY_MIGRATIONS` for every step. A migration Room
   never runs is not a migration.
2. The exported `app/schemas/*/N.json` committed and git-tagged (ADR-0007) — every later migration is
   written from it.
3. `SchemaGateTest` asserting `appSchemaVersion = N`, so the **launch gate** is proven to let the upgrade
   through. Every migration test opens the database directly and walks past that gate; this is the only
   thing standing in front of it (ADR-0023's Phase 7.5 amendment).
4. A migration test proving the **rows survive** — the committed backup fixtures, restored and counted by
   value, at API 26/34/36.
5. **An actual upgrade watched on the phone**: seed the previous tag, install the new build over it,
   confirm the app opens, `user_version` climbed, and a table-by-table diff on common columns is empty.
   A release-shaped debug build is how to do this without touching the Play install (phase-7.5.md §7).

`scripts/schema-gate.py` enforces 1–3 in CI on every pull request. 4 and 5 are judgement, and 5 is the one
that caught the refusal screen 1.5 would otherwise have shipped to every existing owner.

**Phase 5** (vet, medications, documents, dose reminders — ships as 1.2) — software half **done**,
evidence half open. Status read 2026-08-05 20:30. Phases **6** (1.3), **7** (1.4), **7.5** (1.5) and
**8** (1.6) are **closed**, each with its record in its own phase file; §5, §6, §6.5 and §7 below are their
closing notes and hold no work.

**So what is actually open is evidence and Play, not code**: §1's overnight run and the gate items parked
behind it (§2), the Console half (§4), and one reply owed to a tester (§9). Everything since 1.2 is built,
device-proven and either tagged or ready to tag — `v1.5.0` was cut 2026-08-16, and Phase 8's nine languages
sit on `main` waiting for the 1.6 cut — but **the tracks are still on 1.0.0 / 1.3**, so none of it has
reached an owner's phone yet. Closing that gap is §4, and §1 is what stands in front of it.

---

## 1 — The exact-alarm overnight Doze run 🔴 blocking, and time-sensitive

5a's outcome and the first bullet of Phase 5's gate. **Still owed**: the 4→5 Aug night fired on the
*best-effort* path (`setAndAllowWhileIdle`) because the permission had reverted, so the question the
three outcomes were written for is untouched.

As of 2026-08-05 20:30 the device is **not armed** — all three of these are wrong right now:

- `SCHEDULE_EXACT_ALARM` reads `default` (denied) for `binky.bunny.and.rabbit.tracker.debug` (`u0a497`).
- No pending `DoseAlarmReceiver` alarm. Last removal is `Reason=data_cleared` at **19:24:08** — the
  edge-to-edge matrix run took the armed course with it, as 5i said its `wipe` steps would. `files/`
  has carried no media directories since, so nothing is re-seeded.
- The phone is **USB powered**, so Doze cannot start.

### Pre-flight, in this order

- [ ] Re-seed a real medication course, and Bijou's watch for the Phase-4 carry below. `seedWatches`
      back-dates `startedAt`, so the expiry morning is a parameter, not something to wait for.
- [ ] Grant the exact-alarm permission **through the app's own deep link** (that is the path under test).
- [ ] Confirm the pending alarm is the *exact* mechanism: `window=0` and `whenElapsed == maxWhenElapsed`.
      The best-effort alarm reads `window=+38m55s`, `flags=0x20` and a `maxWhenElapsed` ~39 min later.
      **This pair of fields is the only pre-run proof of which mechanism is armed** — the 4→5 Aug run
      could only tell from the appop afterwards, too late.
- [ ] Read and record the **autostart** state before touching anything: the count in the header of
      *Ustawienia → Aplikacje → Uprawnienia → Autostart* and the apps under it. A `uiautomator` dump's
      `checked` attribute lies on that screen — every row reports false. (Last read: 10 apps, `Binky`
      among them, `Binky Debug` not; granted for the debug build → header 11.)
- [ ] **Unplug**, evening, and leave it unplugged past the fire time. Charging blocks Doze — this is the
      half 4g could not claim.

**Trap:** never run `connectedAndroidTest` after arming. `am instrument` force-stops the package, which
cancels every alarm it placed, and the result is indistinguishable from a broken rebuild.

### Reading it the next morning — read-only, before the shade is touched

```bash
adb shell dumpsys alarm > alarm.txt        # pending alarms are ABOVE the "Removal history:" section
adb shell cmd appops get binky.bunny.and.rabbit.tracker.debug SCHEDULE_EXACT_ALARM
adb shell dumpsys batterystats --history   # device_idle=full unbroken across the fire time; plug=usb after
adb shell dumpsys notification --noredact  # exactly one post on channel=doses, importance=4
```

- [ ] **Dose outcome recorded** against 5a's three written-down outcomes (fires in grace / late but
      reliable / not until touched). Outcome 3 rewrites ADR-0003 and the phase's delivery mechanism.
- [ ] **Phase-4 carry, same night or its own**: the care sweep firing while **still in Doze**, and a
      watch **auto-expiring** — nagging stops that morning, the prompt shows the *current* trend,
      dismissing leaves no row behind. Different signatures, so one night can hold both.

---

## 2 — The gate items parked behind that run

All deliberately after it, because each would disturb the armed course.

- [ ] Writes against the armed course — add, edit, shorten, record and skip a dose; **at most one pending
      alarm** after each, and **none** when nothing is armed.
- [ ] Bunny-level rebuilds: archive, un-archive, delete a bunny with an armed course. Same invariant.
- [ ] Notifications denied / `doses` channel muted → presents as **blocked**, and creating a course still works.
- [ ] The destructive halves of three dialogs (delete visit with its weighing, delete vet, delete bunny counts).
- [ ] **Reboot twice — autostart granted and autostart denied.** Whatever the denied run says is what
      ADR-0025's self-heal consequence gets reworded to.
- [ ] Timezone change: today's answered doses stay answered, no alarm re-armed for a dose already given.
- [ ] Edge-to-edge matrix re-run (`scripts/edge-to-edge.py`, **73 scenes** — Phase 7.5 added twelve,
      seven of them on the two seed variants).
      ⚠️ **A wipe costs the rotation, so every wiping scene in a landscape cell has been shot in
      portrait — in this run and in 4f's** (found 2026-08-12). `pm clear` kills the app, the
      portrait-locked launcher takes the foreground, and HyperOS writes `user_rotation` back to **0**;
      `accelerometer_rotation` stays 0, so nothing puts it back. The cell goes on capturing, checking
      and reporting *clean* at 1220×2712 under a name saying `landscape`. Confirmed both ways:
      `settings get system user_rotation` read `0` moments after `apply_config` set it to `1`, and the
      committed `docs/edge-to-edge/ime-landscape-*.png` are genuinely 1400×630, so landscape did work
      where nothing wiped. **The blast radius is the `empty` suite** — all six of its scenes wipe as
      their first step, so the setup wizard has never actually been seen in landscape by this harness;
      `mismatch` corrupts the database without a wipe and is unaffected, and `full` was only exposed
      once a per-config re-seed was added. Fixed in `wipe()`, which now re-pins the rotation it just
      cost, with `apply_config` recording what to re-pin to; and the per-config re-seed now runs
      **before** `apply_config` rather than after it. **A cell that cannot fail is not evidence** —
      the check ran 53 times per landscape cell and passed every time, on portrait screenshots.
      ✅ **The tap blocker is gone** (6c, 2026-08-06). It was never a permission: `input` picks a
      default source when none is named, and HyperOS stopped honouring that inference. **`input
      touchscreen tap` lands where bare `input tap` is dropped** — A/B'd on one screen at one
      coordinate — and `keyevent` and `swipe` were never affected. `edge-to-edge.py` is fixed and both
      new scenes ran clean in all four configurations, so the matrix is driveable again.
      ⚠️ **`watch-expiry` needs re-shooting when the matrix is re-run** (found 2026-08-06, building Phase
      7's capture). The seed leaves exactly **one** expired watch — Nugget's 3-day, started 4 days ago;
      Bijou's 7-day is still running — and every scene that is not `keeps_watch_prompt` opens by tapping
      *Close it*, which **deletes the row** (`WatchExpiry.kt`: "close, dismiss and swipe-away are one
      action"). In `SCENES` order `home` runs ~20 scenes ahead of `watch-expiry`, so by then there is no
      prompt and the PNG is a plain Home screen under a dialog's name. `screenshots.py` sorts
      `keeps_watch_prompt` scenes first within each suite; `edge-to-edge.py` still does not, and its
      existing `watch-expiry` evidence should be assumed wrong rather than re-read.
      ⚠️ **`medication-course`, `medication-course-bottom` and `record-dose` likewise** (found
      2026-08-06, same session, and this one is the worse of the two). The Care screen grows a
      blocked-state banner for **each of two permissions** — notifications off, and exact alarms not
      permitted — and both buttons are `action_open`, **the same "Open"** the medication-course row
      uses. `find` is a case-insensitive substring match, so `tap("Open")` hit a banner and launched
      HyperOS's Settings: both `medication-course` shots were **screenshots of the system Settings
      app**, and `record-dose` failed with an empty node list because the foreground had left this
      package. Confirmed twice by `dumpsys window` — `Settings$AppNotificationSettings`, then
      `Settings$AlarmsAndRemindersAppActivity` once the first was granted.
      **This applies to the 4f run too** — §1 records it ended `Reason=data_cleared`, so it wiped,
      so it held neither permission. Assume those three scenes' existing evidence is wrong.
      ℹ️ **`observation-entry-ime`'s needle changed with Phase 7's `2c`** (2026-08-09) — not wrong
      evidence, but it would have become wrong on the next run. It tapped *"Anything else"*, which is
      now a label **above** the note box rather than the box's own floating label; the tap would have
      landed on a plain `Text` with nothing to focus and shot a form with no keyboard. It taps the
      placeholder now. **Expect one of these per redrawn route** — a scene needle is a claim about what
      the UI says, and this phase rewrites exactly that.
      ℹ️ **`course-editor-ime` broke on `3e`** (2026-08-09) — the same trap as `observation-entry-ime`,
      one route later, which is now two of two for IME scenes. It tapped *"What is it?"*, a floating
      label that is a plain `Text` **above** the box since the redraw; the tap would have focused
      nothing and shot a form with no keyboard. It taps the placeholder (*"Metacam"*) now. **An IME
      scene's needle is a claim that some text belongs to a focusable control**, and the form idiom
      moves exactly that text. `course-editor-bottom` did not break but its note did: Save is in the
      app bar now, so the bottom edge is the notes box rather than a button.
      ℹ️ **`5a`/`5b` and `4e` broke none either** (2026-08-09), and between them they turn the
      exception into the rule's other half. Four needles across the two routes — `vets` and
      `vet-editor` tap a More row and the *Add a vet* button; `bunny-editor` and its two siblings tap
      *Edit* on Home and then swipe — and **not one of them names anything the redraw touched**, even
      though Vets deleted two buttons off every row and the bunny editor moved six fields into cards.
      `bunny-editor-bottom`'s shot changes, as `course-editor-bottom`'s did, but its needle does not.
      **A needle on chrome survives a redraw of what the chrome contains**; only content needles are
      fragile, which is now five routes' worth of evidence.
      ℹ️ **`2a`/`2b` Observations broke none** (2026-08-09), which is worth recording as the exception
      rather than as proof the rule was wrong: it *deleted* a string (`observation_observed_together`)
      and added three, and both its scenes came through clean because neither needle ever named
      anything inside a card — they tap the tab and swipe. **Needles that reach into content are the
      fragile ones**; a needle on chrome survives a redraw of what the chrome contains.
      **The fix is the needle, not the permissions**: the scenes now open the course by name
      (`MEDICATION_COURSE = "Metacam"`, the sample data's first). Granting both would also clear it
      and is the wrong lever — `SCHEDULE_EXACT_ALARM` is denied by default on Android 14+, so that
      banner is a state real users genuinely see, and §1 wants the permission granted through the
      app's own deep link because that path is what is under test. `reset_to_seeded()` does grant
      `POST_NOTIFICATIONS` back, separately, so a seeded install stands in for an app in use; the
      `empty` suite keeps the denied state, where it is the truth of a first run.

---

## 3 — The document downsample spec ✅ answered 2026-08-14, closed with Phase 7.5

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

## 4 — The Console half of 5j 🟡 blocked on Play, not on us

Play's 12-testers / 14-day count was still running on 2026-08-05. Nothing here is work we are holding.
All three land in **one sitting** once the count clears, in this order:

- [ ] Upload **1.3** to the **internal** track, then **closed** — not 1.2.0, per §5's closing note: same
      schema, same migrations, and uploading both spends a release cycle to prove nothing extra. If the
      count has cleared, production becomes available for the first time — whether 1.3 takes it is an
      ADR-0009 decision made then.
- [ ] **Screenshots for all nine listings**, owed for the screens 1.1 and 1.2 both added.
      **Nothing of ours blocks this any more.** §2's tap blocker was fixed at 6c, Phase 7 closed on
      2026-08-13 so the screens are final, and §7's locale-aware driver was re-proved against every
      shipped locale on 2026-08-18 — `screenshots.py --locale <tag>` now runs all nine, Brazilian
      Portuguese included. The **English** set is a *selection* from
      `~/binky-screenshots/phase-7/after/`'s 63 scenes rather than a new run; the other eight are ~2 h of
      device time, deliberately not spent yet because **Play falls back to the default listing's
      screenshots** and the tracks are still on 1.0.0 / 1.3, where the 1.6 copy cannot go up anyway.
- [ ] **The field upgrade proof: 1.0.0 → 1.3**, real bunny history intact. The Xiaomi's Play build is on
      **1.0.0**, not the 1.0.1 4h assumed, so the chain crosses *both* hand-written migrations. It cannot
      run locally — the installed build is Play-signed and a local APK is refused on signature mismatch —
      so the update must **arrive from a track**, downstream of the upload above.

---

## 5 — Phase 6: the support contact ✅ closed 2026-08-16, ships as 1.3

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

## 6 — Phase 7: the redesign ✅ closed 2026-08-13, ships as 1.4

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

## 6.5 — Phase 7.5: the interlude ✅ closed 2026-08-18, ships as 1.5

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

## 7 — Phase 8: nine languages ✅ closed 2026-08-18, ships as 1.6

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

## 8 — Open-source licence attribution ✅ built 2026-08-14, shipped in 1.5

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

## 9 — A weight *gain* raises nothing ✅ answered and built 2026-08-14, shipped in 1.5

Found by a tester 2026-08-09: a bunny putting on *"5 kg plus"* produced no flag, because `WeightTrend.kt`'s
trigger was one-sided **by design** — loss is the acute, hours-matter signal ADR-0001 was written about, and
the loss baseline is deliberately rise-resistant so a lasting gain cannot mute every later drop.

**Decided in [ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md) and built the same
day.** A gain raises the same flag against a **six-month anchor** rather than the loss rule's baseline,
because gain is chronic where loss is acute; the copy states a fact about the numbers, in grams, and never a
verdict about the rabbit (ADR-0026, ADR-0001 — *health features observe, they never advise*). `TrendDrop`
became `TrendChange` and carries a direction. Reasoning in [`phase-7.5.md`](phase-7.5.md) §1.

- [ ] **The tester's reply is still owed**, and it is not the feature. Their *"5 kg plus"* was a **number,
      not a change**: a Flemish Giant is legitimately 6–10 kg, so any absolute weight is wrong for some
      breed and Binky will never call a weight too high — only say that it moved, by how much, since a date.
      Saying so is better than letting them find out.

---

## Already proven — do not re-run

1.2.0 tagged (`v1.2.0` → `4097448`) and verified against the **artifact**: versionName 1.2.0, versionCode
211, upload key, 709/709 Polish strings, 8 permissions with none of the four forbidden, zero
`<uses-feature>`. Schema **6** frozen and tagged (`schema-6` → `01a769e`); both the schema-4 and schema-5
fixtures migrate to 6 in CI on every PR at API 26/34/36. Lint **0 errors, 0 warnings**. ~201 instrumented
tests green on the Xiaomi. ADR-0021 from both sides, both delete dialogs, the two-page document surviving
a process restart, the *Care & Meds* label, ADR-0026's line, and the *skipped*/*missed*/*overdue* string
audit — all checked on the device at 5i.

## Closing the phase

When every box above is ticked: write the results into `PLAN.md`'s 5a / 5i / 5j entries, tick **Phase 5**
in its checklist at the top, and empty this file down to the next phase's open items.
