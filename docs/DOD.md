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
evidence half open. Status read 2026-08-05 20:30. **Phase 6** (the support contact — ships as 1.3) is
built, device-tested and documented as of 2026-08-06 and **closed 2026-08-16**, when the last of §5's two
hand items landed; 1.3.0 is waiting on a release PR. **Phase 7** (the redesign — ships as 1.4) **closed 2026-08-13**; §6 is its
closing note, and the one thing it carried out is §7's first box.

**Phase 7.5** (the interlude — ships as **1.5**, opened 2026-08-14) collects what was scattered here and
**owns §3, §5, §8, §9 and §7's capture-driver box**, plus three owner-facing findings from 2026-08-14 that
live only in §6.5. **It bumps the schema to 7** — added 2026-08-14 with the droppings work, so it is no
longer the migration-free interlude it was drafted as.
Its reasoning is [`phase-7.5.md`](phase-7.5.md); §6.5
below is its summary. **Phase 8 retargets to 1.6** — two phases cannot both claim 1.5, and `release-please`
answers to commit subjects rather than to this file.

**So what is actually open is evidence, then one short phase of code**: §1's overnight run and the gate
items behind it, and Play's own count (§4). **Phase 7.5's own boxes are all ticked as of 2026-08-16** —
§5's two hand items (2026-08-15 and 2026-08-16) and the driver's English matrix (292 cells, zero errors).
Everything that touches the app's own code is done and
device-proven; the schema bump, its migration and its instrumented run are green. What is left of the
phase is closing it: `PLAN.md`, emptying §6.5 and the sections it borrowed, and the 1.5 cut. Then Phase 8.

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

## 3 — The document downsample spec 🟢 answered 2026-08-14

Phase 5's intro called this *"a deliverable and not an assumption"*. Judged on the phone against a real
scan, not the fixture.

- [x] **`Document` stays `LongEdge(maxEdge = 3000, quality = 92)`.** A4 of dense 9 pt text stored
      **2129×3000, 1.29 MiB**; legible at 1:1 with no ringing or blocking. Quality 92 sits just above the
      knee — 85 costs fourteen times the damage to save 18% of the file. The "unverified" comment is gone
      and the measurement is in its place.
- [x] Same sitting settled §6.5's new **`MediaKind.Observation` = `LongEdge(2048, quality = 88)`**, which
      **disproved** the "closer to `Document`" hypothesis. Reasoning in phase-7.5.md §2 and §7.

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
- [ ] **Screenshots for both listings** (EN + PL), owed for the screens 1.1 and 1.2 both added.
      **No longer blocked on anything of ours**: §2's tap blocker was fixed at 6c, and Phase 7 closed on
      2026-08-13, so the screens are final and a set taken now will not be stale. The **English** set can
      be taken today — `~/binky-screenshots/phase-7/after/` already holds 63 scenes in light and dark, so
      the listing shots are a *selection* from it rather than a new run. **Polish waits on §7's
      locale-aware driver**, which is the same blocker as the Polish after set.
- [ ] **The field upgrade proof: 1.0.0 → 1.3**, real bunny history intact. The Xiaomi's Play build is on
      **1.0.0**, not the 1.0.1 4h assumed, so the chain crosses *both* hand-written migrations. It cannot
      run locally — the installed build is Play-signed and a local APK is refused on signature mismatch —
      so the update must **arrive from a track**, downstream of the upload above.

---

## 5 — Phase 6: the support contact ✅ closed 2026-08-16

Built, driven on the device and written up — **6a, 6b, 6c and 6d are all done**. The record is
[`phase-6.md`](phase-6.md), and `PLAN.md`'s status list ticks Phase 6 on the build and the documents.
`release-please` already has **1.3.0** waiting on PR #93; merging it is the cut.

Two boxes outlived the code, and neither was work a build could do. **Both are closed** — the oldest open
boxes in the project, shut a day apart:

- [x] Set `binky.support@gmail.com` as Play's **per-app contact email** in *Store settings*. ✅ **Done
      2026-08-15.** The app, the listing and the privacy policy's *Contact* section now name one inbox.
- [x] **Read a support mail that actually arrived**, carried from 6c's gate. ✅ **Done 2026-08-16.** A bug
      report sent from the phone lands in the **inbox proper** and the diagnostics block is **visible** —
      not collapsed behind Gmail's signature `…`. That was the whole claim: the block is separated by a
      blank line and never `-- ` for exactly this reason, and only a delivered message could prove it.
      Everything up to the send was already verified in both locales.
      ✅ **Delivery was proven first (2026-08-15)** — the `mailto:` hand-off, recipient, subject and body
      all reach a real inbox.
      ⚠️ **That first one landed in Spam**, which is silent on both ends: the sender sees a sent message
      and the maintainer sees an empty inbox. **Fixed by a filter on the receiving account** —
      `subject:bug OR subject:feature` → *Never send it to Spam*, applied 2026-08-15. It works in every
      locale because the `#bug` tag is a Kotlin constant rather than a string resource
      (`SupportHandoff.kt`), so **one rule covers all nine of Phase 8's languages** — and the filter is
      what put the settling report in front of the eye that read it.

**1.3 supersedes 1.2.0 on the tracks — do not upload both.** Same schema 6, same two hand-written
migrations, so §4's field-upgrade proof retargets to **1.0.0 → 1.3** and still crosses `MIGRATION_4_5` and
`MIGRATION_5_6`. Uploading 1.2.0 first buys a second release cycle and proves nothing 1.3 would not.

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

## 6.5 — Phase 7.5: the interlude 🟢 planned, ships as 1.5

Design in **[`phase-7.5.md`](phase-7.5.md)**. It owns no boxes of its own — it is the *order* over five
that are already written down elsewhere in this file. Each is cheaper before nine languages than after, and
three get more expensive with time. **Both ADRs are made** — ADR-0028 and
[ADR-0029](adr/0029-droppings-are-multi-valued-and-the-tray-is-worth-a-photo.md), grilled and written
2026-08-14, so nothing here is waiting on a decision. **Every box below is ticked as of 2026-08-16** — the
second hand item that morning, the English matrix that afternoon — so what remains is closing the phase,
not working it.

- [x] **The launch gate refused every schema-bumping update — fixed 2026-08-16, and it blocked 1.5.**
      `BinkyApplication` treated *any* version mismatch as a reason to show the blocking screen, so a
      release build met an owner's 1.4.0 database with *"This version cannot open the records on this
      phone"* and a dead end — `MIGRATION_6_7` never ran, because the gate returns before Room is
      constructed. Nothing was deleted (the file is untouched, a copy lands in `preserved/`), but the app
      was unusable, and the same shape shipped at 1.1 and 1.2. Every existing proof of the migrations
      opens the database *directly* and so walks past the gate entirely.
      **The fix** is `schemaGateDecision` (`SchemaGate.kt`): a pure `Open`/`Consent`/`Refuse` over the
      on-disk version, this build's version, the migrations *actually registered*, and whether the
      fallback is armed — with `destructiveAllowed` asked first, or a debug build would skip its own
      consent screen. `SchemaGateTest` is the truth table. ADR-0023 carries the reasoning.
      **Proven on the phone**: a real 1.4.0-written schema-6 database, a release-shaped build installed
      over it, refusal screen before / app open after, `user_version` 6 → **7**, and every count intact —
      44 weighings, 5 observations, 2 vets, 3 visits, 3 courses, 14 doses, 4 documents over 7 pages, the
      droppings in their join tables, `trayPhotoPath` null throughout.
      **Both live upgrade paths watched on the phone (2026-08-16)**, on the app's own database rather
      than through the staged restore path — a 1.4.0 seed taken to 1.5, and a **1.1.0 seed taken
      straight to 1.5**, the skipped-version jump a phone that never took 1.2–1.4 would make. Compared
      table by table on *common columns*: **zero differing rows in every table**, both times. The only
      changes are the ones the migrations are for — `weights.visitId` and `observations.trayPhotoPath`
      added and null, `droppingsSize`/`droppingsForm` moved into the join tables per observation (the
      one that answered neither contributes no rows), and the schema-6 tables arriving present-and-empty
      on the 5 → 7 path.
      ℹ️ **An app update does not cancel the pending dose alarm** — same alarm object, still armed for
      20:00, across the package replace with the app never launched.
      ✅ **The dormant window is closed (2026-08-16).** `schemaWipePending()` became
      `schemaBlocksBackgroundWork()` and asks the *gate's* question: a debug build still blocks on any
      mismatch (it has no migrations registered, so opening means wiping) and a release build still
      blocks on a file no migration covers, but an upgrade a migration can walk now falls through — and
      opening is what migrates. **Proven in-process**: the real `bunny.db` left at schema 6 under a
      release-shaped build, `DoseAlarmTest` **16/16 green**, and the file at **7** when the run ended.
      The background path performed the migration instead of sitting out the morning.
      ⚠️ **Correcting yesterday's reading of this**: the broadcast that "showed the guard skipping a
      dose" never reached the app at all — HyperOS did not start the process for it (`pidof` empty,
      `stopped=false`). What the receiver does over a stale database is proven by the in-process run
      above, not by an `adb` broadcast.
      ℹ️ **`PackageReplacedReceiver` is in as belt-and-braces and is unverified on this phone.** After a
      real `adb install -r` the receiver never ran: WorkManager's own database held only
      `ReminderSweepWorker`, no `UpdateCatchUpWorker` row. HyperOS does not deliver
      `MY_PACKAGE_REPLACED` without autostart, and the ROM has no `AUTO_START` appop to grant over
      `adb`. It should work on ROMs that deliver it; the guard above is what carries the fix here.
- [x] **§5's two hand items** — Play's per-app contact email (2026-08-15) and a delivered support mail
      read (2026-08-16). The oldest open boxes in the project, shut a day apart.
      ⚠️ **The first mail delivered but was filed as Spam**, fixed with a
      `subject:bug OR subject:feature` → *Never send it to Spam* filter on the receiving account. One
      rule covers all nine languages, because the tag is a Kotlin constant. **The next report landed in
      the inbox proper with the diagnostics block visible**, which is what the box was for. See §5.
- [x] **§7's capture-driver box**, moved here. ✅ **The code is done and device-proven (2026-08-14)** —
      `return_to_home` + DND as setup/teardown, seed variants through a debug-only broadcast receiver
      (`crowded` is the first), and needles resolved through the string resources before the first tap.
      The default seed is **not** changed: **58** scenes rest on it.
      ✅ **The Polish run is done (2026-08-15)** — 73 scenes in light and dark, **146/146**, in
      `~/binky-screenshots/phase-7.5/pl`. That is the **Polish after set**, and it unblocks §4's Polish
      listing screenshots.
      ✅ **The English matrix is done (2026-08-16)** — **292 cells, 292 reached, zero errors, no
      confirmed defect**, in `~/binky-screenshots/phase-7.5/en`. The 20:00 dose was made live *on demand*
      rather than waited for: `DOSE_GRACE` is 30 minutes and a cell is nearer fifty, so an evening run
      goes quiet halfway. A `due_dose` seed variant re-armed a slot a minute in the past **before every
      scene**, and `mBypassDnd=false` was read off the channel first, so DND suppressing it is evidence
      rather than luck. **Three driver defects had to be fixed to get the run, and none was in the app** —
      see phase-7.5.md §4.
      ⚠️ **`Care & Meds` and `Backup & restore` matched nothing in English**, because `dump_ui` parses the
      XML with a regex and never unescaped `&amp;`. Those two needles head roughly **twenty** scenes.
      Unreachable in Polish (*"Opieka i leki"*, *"Kopia zapasowa i przywracanie"*), so the 146/146 run
      could not see it — **the first defect this phase where English is the broken half**, and it arrived
      with the 2026-08-14 lengthening that fixed a different ambiguity.
      ⚠️ **`tap`'s four-try scroll budget is a portrait-shaped constant.** A landscape swipe covers ~464 px
      against portrait's ~1030, so seven landscape cells reported controls unreachable that `care-bottom`
      scrolls straight past. It scrolls while the screen keeps changing now.
      ⚠️ **A partial re-shoot used to overwrite the report** with only the re-shot cells — the Polish run's
      2026-08-15 note, now folded into `write_report`, which merges by scene name.
      ⚠️ **`photos` and `photos-bottom` had been shooting the wrong screen in Polish, silently** —
      found 2026-08-15 and **fixed**. `more_photos` is *"Zdjęcia"* and `bunny_avatar_placeholder` is
      *"Nie ma jeszcze zdjęcia"*, the same form; `find` takes the smaller node and the avatar (60×60)
      beats the row (170×59), so the tap opened the bunny switcher. `photo-add-menu` failed loudly on
      its next tap and **that is the only reason the other two were found**. All three now use
      `tap_text` — the third structural needle — and were re-shot.
      ℹ️ **The English evidence is not retracted**: *"No photo yet"* is singular, so the needle
      *Photos* cannot match it. This defect is unreachable in English, in any configuration.
      ℹ️ **The relaunch was already landing on Home** — measured from a detail route and from a non-Home
      tab — so the isolation step is a *check*, and the 2026-08-12 cell is better explained by the dose
      banner re-posting on every relaunch. See phase-7.5.md §4 for the rest of what the build found.
      ⚠️ **`weight-entry-ime` has never had a keyboard in it, Phase 7's after set included** (found
      2026-08-14). The grams field has no label and no placeholder, so the needle *"Weight in grams"*
      matched the **help line underneath it** — a plain `Text` that focuses nothing. Third IME scene
      caught by that trap and the only silent one. Fixed by tapping the *n*-th `EditText` instead;
      **assume the existing `weight-entry-ime` evidence is wrong** rather than re-reading it.
- [x] **§3's downsample answer**, taken while the phone was already in hand — **both** specs, `Document`
      kept and `Observation` set, on one tray and one printout. 2026-08-14.
- [x] **§9's gain signal** ✅ **built and device-proven 2026-08-14**, per
      **[ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md)** — the rule in
      `WeightTrend.kt`, three strings in **both** locales, the age question wired to the bunny editor from
      all three of the flag's hosts, a **`gaining` seed variant** and three scenes so the card has a
      permanent home in the harness. `TrendDrop` is now `TrendChange` and carries a direction; the
      watermark's own direction is read back off its grams, so ADR-0028's flip discard still costs no
      column, and `WeightRepository.add` asks `evaluateTrend` whether the row is stale rather than keeping
      a second copy of the rule.
      ⚠️ **The first capture found a defect the rule did not have**: the flag's action row is a `Row`, and
      a third action does not clip there — it crushes *Start a watch* to one character wide and spells it
      down the card. `FlowRow` now, in the banner **and** in both of the dialog's button slots. Seen in
      English and Polish. **Second time seed variants have caught a state no screenshot held.**
      ℹ️ **"No reading in the 4–8 month window" evaluates to `Steady`, deliberately** — it is a distinct
      *case* and pinned as one, but nothing renders either as reassurance, so a third variant would be a
      distinction with no consumer. Reasoning in phase-7.5.md §1.
      The rule itself is **not restated here** — it is ADR-0028's, tested in `WeightTrendTest` and
      summarised in §9 below, which stays for the question it started as.
- [x] **Droppings are several things at once, and worth a photo** ✅ **built and device-proven 2026-08-14**,
      per **[ADR-0029](adr/0029-droppings-are-multi-valued-and-the-tray-is-worth-a-photo.md)**. Nothing was
      cut — the multiselect, the six new values, the `DroppingsAppearance` rename **and** the tray photo all
      ship, so the release valve is unspent. **Schema 7 is frozen and tagged** (`schema-7` → `ddb430a`);
      `MIGRATION_6_7` rebuilds `observations` create-copy-drop-rename with the symptom links staged across
      the cascade, pinned by `Migration6To7Test` in five cases. **`connectedAndroidTest` green on the
      Xiaomi at 215/215** — the gate item nothing else in this phase owed.
      ⚠️ **The first run after a schema bump fails 13 `DoseAlarmTest` cases, and it is not a regression** —
      expect it again at schema 8. `rescheduleDoseAlarm` opens with `schemaWipePending()`, which reads the
      header of the **real** `bunny.db` rather than the test's in-memory database, so a phone still carrying
      the previous schema's debug database disarms every alarm the suite tries to arm. The tell is that
      every failure is an `assertTrue(armed())` and the three `assertFalse` cases pass. Clearing the debug
      database through the app's own consent screen made the same run 215/215 with no code changed.
      ℹ️ **The debug build wipes 6 → 7 rather than migrating, by design** (`BunnyDatabase.kt`: a build takes
      the fallback **or** the migrations, never both). It preserved
      `files/preserved/bunny-20260814T163225Z.db` — the replaced database's size to the byte — before
      wiping, which is ADR-0007's promise watched for real for the first time. So the phone is **not** where
      `MIGRATION_6_7` is proven; `aReleaseShapedOpenOfASchemaSixFileSucceeds` is.
      ✅ **`bunny-schema-6-fixture.zip` is in (2026-08-16)**, exported on the Xiaomi by the `v1.4.0` tag's
      own container, seeder, Room and exporter — a real artifact, like the other two. It is the first
      fixture whose schema-6 tables are **full** rather than present-and-empty: 2 vets, 3 visits, 3 courses
      over 3 times and 14 doses, 4 documents over 7 pages, carried through the migration that rebuilds
      `observations` and drops `observation_symptoms` before putting it back.
      `aSchemaSixBackupWrittenBy140MigratesTheLastStep` asserts all of it, plus the 7 document pages and
      5 photos landing on disk. **2.8 MB**, because a faithful Everything export carries the seeded
      3000 px scans; the schema-4 and schema-5 files are 9 KB and 15 KB for want of anything to carry.
      **216/216 instrumented green on the Xiaomi** with it in — but the chore itself leaves the phone's
      real `bunny.db` at schema 6, which disarms the dose alarms until `databases/bunny.db*` is deleted
      through `run-as`. Details, and the two beliefs the artifact corrected, in phase-7.5.md §7.
      ✅ **The droppings assertion is in (2026-08-15)**, in *both* fixture restores and by **value rather
      than count**: four observations arrive as `ROUND`, the sizes as `NORMAL, NORMAL, SMALL, SMALL`, and
      the fifth — which carried neither column — contributes **no** rows, which is the half a row count
      could not tell apart from a migration that defaulted it. 12/12 green on the Xiaomi, and no new
      fixture was needed, as predicted — the `zip` above landed the next day and asserts the same values
      from a third artifact.
      ℹ️ **HyperOS's ADB install prompt is drivable** with `input touchscreen tap`, contrary to the standing
      note. `INSTALL_FAILED_USER_RESTRICTED` after ~12 s is a *missed prompt*; the same string returned
      instantly is the first-install refusal. The delay tells them apart.
      The question as it stood, and every decision behind it, is phase-7.5.md §7 and ADR-0029.
- [x] **The healthy day moves behind the `+`** ✅ **built and device-proven 2026-08-14, in both locales.**
      The "+" opens a `ModalBottomSheet` of two `ListRow`s — the healthy day carrying `healthy_day_help` as
      its subtitle, because one tap commits four facts on the owner's behalf (ADR-0001) — and the Timeline
      button is gone, so there is one entry point rather than two. **No schema and no new strings**:
      `healthy_day_action`, `healthy_day_help` and `observation_add_title` are reused verbatim.
      The write moved with it into a **shell-scoped `HealthyDayViewModel`** — the "+" is the shell's button
      and the snackbar host was already the shell's — so the healthy day now writes **from Home**, which it
      never could before, with the receipt and its Undo unchanged.
      ⚠️ **Reusing the FAB's string cost the driver a needle.** The FAB's `contentDescription` and one sheet
      row now say the same words, and `find` takes the *smallest* match — the FAB — so the old one-tap
      route would have re-tapped the button and dismissed the sheet on its own scrim. Fixed structurally
      like `tap_field`: a new **`tap_text`** step matches a node's text and ignores content descriptions.
      Proven in Polish, where both read *"Zapisz obserwację"*. `OPEN_OBSERVATION_FORM` is the two-tap route
      the four form scenes now share, and `record-day-sheet` is the sheet's own scene.
- [x] **The housemates line at five bunnies** ✅ **built and device-proven 2026-08-14, in both locales.**
      Both halves, as decided: `capHousemates` names two and folds the rest into *"& N others"* **from four
      housemates up**, archived first, through **one `plurals` entry** joined as the last *item* of the list
      so `joinNames` punctuates it per locale; plus `maxLines = 2, overflow = Ellipsis` at all three sites.
      The rule lives in `housematesLabel`, never in `joinNames`, which the healthy-day receipt shares and
      must not truncate (ADR-0008). Bijou reads *"Lives with Clover, Nugget & 2 others"* on one line —
      *"Mieszka z: Clover, Nugget i 2 inne"* in Polish, the `few` form agreed correctly — and two long
      names still take exactly two, bounded, which is what the backstop is for.
      ℹ️ The cap is a **pure function with a JVM table** (`HousematesTest`), which is what pins *"& 1 other"*
      never rendering across one to nine housemates. The width half is not testable there and is what the
      `crowded` seed variant photographs.
- [x] **§8's licence attribution** ✅ **built and device-proven 2026-08-14, in both locales.**
      **`app.cash.licensee`** at build time, rendered by the app's own Compose screens. No ADR owed — it
      adds no runtime dependency, so ADR-0009 is untouched, and it emits structured data plus a build
      failure when a licence changes, which is §8's own stated fear. The Play plugin was rejected for a
      second Play-services library *and* a stock Activity in a redesigned app.
      A row on Support's last card opens *Open-source licences*: **201 artifacts** in the release variant,
      grouped under four licences, one section each, with the artifact's coordinates under its name. The
      list is generated **per variant**, so the debug build's 206 include `ui-tooling` and the release
      build's do not — the screen names what *this* binary contains.
      ⚠️ **The generator found a licence nobody knew about on its first run**: `BSD-3-Clause`, covering
      exactly one artifact (`androidx.datastore:datastore-preferences-external-protobuf`, androidx's
      repackaging of protobuf-javalite). A hand-typed list would have been wrong the day it was typed.
      ℹ️ **The licence *text* ships, not a link to it** — §8's words are "travel with the binary", and a URL
      does not travel. `assets/licences/<spdx-id>.txt` holds Apache-2.0 and BSD-3-Clause, rendered verbatim
      on their own screen. The Android SDK and ML Kit terms are Google's and are **not** ours to
      redistribute, so those two groups link out instead — a distinction the model carries rather than the
      screen guessing.
      ⚠️ **`allow("X")` in the build file has a build failure behind it; `assets/licences/X.txt` has
      nothing.** Forgetting the second silently downgrades the screen from shipping a licence to pointing
      at one, so `LicencesTest` reads `build.gradle.kts` and the asset directory and asserts they agree.
      It is the only test in the project that reads the build file, and that is why.
      ℹ️ *Open-source licences* is deliberately two resources with one value — the row and the screen it
      opens — which is §6's collision and **benign here**: the two never share a screen and both translate
      the same, so `resolve_needles` reports the duplicate and resolves it anyway. The ambiguity check is
      about candidates *disagreeing*.
      Two scenes, `licences` and `licence-text`. Everything else is phase-7.5.md §3.

**Commit rule carries over from Phase 7: `feat:`/`fix:`, never `feat!:`.**

---

## 7 — Phase 8: nine languages 🟡 nine shipped and nine listings written; the driver re-proof is all
that is left

Design in **[`phase-8.md`](phase-8.md)**. **Runs after Phases 6 and 7** — translating a string set about
to gain a Support screen, and then to have its copy rewritten by a redesign, means translating it twice
in nine languages and having it read twice by nine native speakers.

- [x] **The English base re-read end to end** ✅ **2026-08-16**, before anything was translated from it.
      **689 resources, zero orphans, zero hardcoded owner-visible strings** — ADR-0013's rule holds, and
      the only bare literals in `main/` are a file path, a `require()` message and debug-only sample data.
      No string says *missed*; the only two saying *overdue* are Phase 4 care reminders, where ADR-0026
      permits it. Three resources became `translatable="false"` — the two endonyms and
      `med_editor_name_placeholder` (*Metacam* is a brand name, and Polish had already left it alone).
      ⚠️ **Four look untranslatable and are not**, which is the half a machine draft gets wrong:
      `0.3 ml` (Polish writes `0,3 ml`), `%1$s kg` / `%1$s g` (Ukrainian writes **кг**, **г**), `~%1$s`,
      and the breed array — where the rule is *translate the descriptive names, keep the registered ones*.
- [x] **Polish re-read end to end** ✅ **2026-08-16**, the base for nothing but worth the same pass.
      Mechanically exact (655 = 659 − 4 untranslatable, all 29 plurals with four categories, no English
      left behind). **Seven defects fixed**, none of which any test could see:
      ⚠️ **Two broke the file's own stated rule 1** — `Prosiłeś(-aś)` and `sam(a)` are gendered second
      person behind a parenthesis, which is the workaround that rule exists to forbid; one of them was in
      a **notification**. Rewritten impersonally.
      ⚠️ **`photo_gallery_empty_help` had drifted in meaning while keeping its format argument** — `%1$s`
      moved from the thing the photos are *of* to the gallery they land *in*, describing a folder that
      does not exist. **This is the failure mode no mechanical check reaches**, and the reason a language
      ships on a person's word.
      ⚠️ **Two droppings chips had lost information**: *Suche **i** twarde* for English's "or", and
      *Bardzo ciemne* with "or tarry" dropped — tarry being the distinctive half.
      Plus `med_empty` grammar and `support_diagnostics_explain`'s *dopisujemy* ("we", against an app
      made by one person).
      ℹ️ **Polish uses one word — *obserwacja* — for both *Observation* and *Watch***, which CONTEXT.md
      keeps apart. **Decided 2026-08-16: keep the word**, since every alternative is worse in a pet-health
      app, and disambiguate only the two strings where they collide *on the observation form* (now
      *baczna obserwacja*, the qualifier the file already used).
- [x] Generalise `PolishTranslationTest` → `TranslationTest`, parameterised over the locale table, with
      **per-language plural categories from CLDR** (not a hardcoded set of four). Do this **first**, on
      `en` + `pl`, so it can fail before there is anything to check. ✅ **Done 2026-08-16.** The locale
      list is read from `locales_config.xml`, so a tenth language is one line of XML; `CLDR_PLURALS`
      carries all nine rows ready. **Proven able to fail** — dropping `few` from one Polish plural
      reddens the build.
      ⚠️ **It had not been able to fail at all, and neither had the old one.** Both translation tests read
      `res/` off disk as plain files, invisible to Gradle's up-to-date check, so editing a translation and
      re-running `test` printed `:app:test UP-TO-DATE` and a green build **having checked nothing**. Fixed
      by declaring `src/main/res` as a test input in `app/build.gradle.kts`.
- [x] ⤷ **The locale-aware capture driver is built and proven — in Phase 7.5 (§6.5), on `en` + `pl`.**
      What this phase owed was the **re-proof**: that the needle table resolves now that seven more
      locales exist, and that needles naming resources the drafts reworded still find them. A needle is a
      claim about what some string says, and this phase changed eight files of strings. The reasoning, the
      two driver facts that paid for it (DND suppressing the seed's 20:00 dose banner; the return-to-Home
      isolation step) and the Polish after set are §6.5's record and are not repeated here.
      ✅ **Re-proved 2026-08-18.** 45 needles over 74 scenes: in **all nine locales 39 resolve, zero
      ambiguous**, and the six that fall through to the literal are all sample data — `Bijou`, `Juniper`,
      `Pip`, `Rosemary` from the seed variants, plus `Metacam` and `Vaccination record`. Then on the
      phone, the full suite in `de` and `uk` (66 scenes each) and a twelve-scene set in
      `es fr it pt-BR cs pl`: **every locale walked end to end with no failed tap.**
      ⚠️ **It found that Brazilian Portuguese could not be driven at all, in either spelling.** `--locale`
      is documented as a BCP-47 tag and was handed straight to both `cmd locale` *and* the resource path,
      so `--locale pt-BR` died looking for `values-pt-BR/`. The obvious workaround is worse than the
      crash: the phone **accepts** `--locales pt-rBR` and stores the language as `rbr`, so the app comes
      up in English while the needle table is Portuguese and the run reports on a language it never
      displayed. Fixed with `resource_qualifier()` — `TranslationTest.qualifier`'s third copy, and the
      shape of mistake this phase named in advance — plus a `require_bcp47()` guard that refuses the
      resource spelling outright. `screenshots.py` imports both from the driver, so the store-screenshot
      run inherits the fix.
      ℹ️ **No locale introduces an edge-to-edge finding English does not already have.** The two
      `drawn`-tier ones are Phase 7.5's known pair, and the measurements rule out copy length as a cause:
      `reminders-sheet` overlaps 48 px in `en`, `pl` **and** `uk` and only **39 px in `de`** — shorter,
      not longer — while `document-viewer` is 13 px in all four. Everything else is the `touch` tier,
      an unlabelled hit area inflated by `minimumInteractiveComponentSize`.
      ℹ️ A `language-picker` scene was added to `scripts/edge-to-edge.py` with the report row.
      ℹ️ **`scripts/aab-locale.py` checked `pl` and only `pl`** — the script that exists *because* 1.0.1
      shipped without Polish reaching the artifact. At nine languages that is eight going to the tracks
      unverified against the very failure it was written for. It now reads `locales_config.xml`, takes
      BCP-47 tags, and checks every shipped locale before exiting; `RELEASING.md` invokes it bare.
- [x] `settings_language_*` → `translatable="false"` — endonyms are locale-invariant, and this removes
      81 duplicated entries at nine languages. ✅ **Done 2026-08-16**, with a general assertion behind it
      rather than `app_name`'s specific one.
- [x] Translator brief + per-language banned-word lists (ADR-0026's *missed*/*overdue*, ADR-0001's
      inference-from-silence, `CONTEXT.md`'s vocabulary and its *Avoid* lists).
      ✅ **[`translator-brief.md`](translator-brief.md) written 2026-08-16.** Carries the three rules that
      outrank fluency, the vocabulary with its *Avoid* column, the do-not-translate table **and its
      inverse**, and the traps — which are the part that had never been written down anywhere.
      ⚠️ **The banned lists are drafts until each native reviewer confirms their own row**, and Polish
      proved why: *pominięta* was on the draft list and came off it. `pominąć` is **agentive** — the thing
      the owner deliberately did — where *przegapiona* and *zapomniana* carry the passive sense ADR-0026
      forbids. `dose_status_skipped` is *Pominięta* and is correct.
      ℹ️ **The biggest trap was missing from the first draft**: the app knows **neither the owner's gender
      nor the bunny's**, which breaks second-person past tense and predicate adjectives in every remaining
      language. English hides it completely. Now §7.3, with both Polish rewrites as worked examples.
- [x] Draft `de es fr it pt-BR cs uk` into **`translations/<locale>/`, not `res/`** — `values-de/`
      existing means every German phone gets it, reviewed or not. `locales_config.xml` is a *picker*
      list, **not** a delivery filter.
      ✅ **All seven drafted 2026-08-16 → 2026-08-17**, 685/685 each and mechanically green.
      **The per-language record now lives in [`phase-8.md`](phase-8.md)** rather than here — seven blocks
      of traps priced, techniques found and decisions taken. Each ends in *"four decisions the native
      read-through has to confirm"* with a **named fallback**, and under
      [ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md) there is no
      read-through, so those fallbacks are the pre-decided answers waiting for the first user report that
      touches one. That is why they moved instead of being deleted with this section.
- [x] Promote one language per commit — move into `res/`, add the `<locale>` line, the `AppLanguage`
      entry, and the endonym label. ✅ **All seven promoted 2026-08-17**, one commit each. `translations/`
      no longer exists, and the gate reads **688 × 8, complete**.
      ⚠️ **This box used to end "only after its native read-through", and that gate is gone.** No native
      reviewer was available for any of the seven and none was findable, so the gate as written had one
      outcome: seven finished, mechanically green drafts sitting unshipped indefinitely while eight of nine
      markets read the app in English. Replaced, per
      **[ADR-0030](adr/0030-a-language-ships-on-an-audit-not-a-native-read-through.md)**, by the half of a
      read-through that needs no native speaker plus a channel for the half that does — an **ethics audit**
      (~24 high-consequence resources × 7, plus a blame- and alarm-word scan over all 685 × 8: **zero rule
      violations**, one wording changed), a **back-translation and argument-role pass** (all 25
      multi-argument strings, the name-substituting single-argument class, and the pre-inflection invariant
      checked against *both* hosts: **no drift**), and a **report row in the language picker** wired to the
      existing support hand-off, proven on the phone end to end into a Gmail draft.
      ⚠️ **Promotion was rehearsed before it was committed, and the throwaway compile found three things no
      draft could have.** A staged draft is never compiled, so this was the first time any of the seven met
      `aapt2`; all seven compiled, which was not a given — French carries ~200 typographic apostrophes.
      **Four navigation labels clipped**: `destination_observations` in de/es/uk and `destination_care` in
      de/it. Fixed by `TextAutoSize.StepBased(8sp, 11sp)` on the label, **not** by shortening the strings —
      with no reviewer, prefer the fix that needs no vocabulary judgement, and *Beobachtung* /
      *observación* / *спостереження* are `CONTEXT.md`'s concept rather than a phrasing. Wrapping stays
      rejected for 1.0's reason (`Navigation.kt`'s comment). Consequence: **six of nine keep the meds
      half** of the Care tab. On branch `fix/nav-label-autosize`, off `main`, to be merged first.
      **`ImpliedQuantity` lint warnings** in fr/pt-BR/uk on the four `care_unit_*` plurals — genuine false
      positives (bare agreeing unit words, the count rendered elsewhere), suppressed with `tools:ignore`.
      ⚠️ **Attribute order is load-bearing**: the gate matches `<plurals name=` with `name` first, so
      `tools:ignore` written ahead of it reads as four missing resources. It fails safe, and it cost a
      debug cycle. And **stale `DRAFT` headers** left in the promoted files.
- [x] `AppLanguageTest` extended to compare resource directories too (`values-pt-rBR` vs `pt-BR` — two
      spellings of one locale in two files). ✅ **Done 2026-08-16**, though it landed in `TranslationTest`
      rather than `AppLanguageTest`: the BCP-47 → qualifier conversion is a single function there, so the
      two spellings cannot be written independently and then compared. `AppLanguageTest` keeps its own
      job, which is the enum against the XML.
- [x] **Play listing title + short + full description in all nine** ✅ **Written 2026-08-17**, in
      [`store-listing.md`](store-listing.md), all 27 fields measured against Play's 30 / 80 / 4000 limits.
      Screenshots may lag, because Play falls back to the default listing's and they need the driver
      re-proof above; **discovery has no fallback**, so the text did not wait on them.
      ⚠️ **The English description was cut at 1.0 and had to be rewritten first.** It described an app
      with no care reminders, no watch, no vet visits, no medications and no documents — 1.0 was the only
      build a listing had ever been written for. Translating *that* into seven languages would have been
      the one thing this phase's whole ordering argument exists to prevent: translating twice. So the
      English was brought to 1.6 scope, Polish brought level with it, and the seven written from there.
      ⚠️ **The copy must not go up before the build it describes is on the track.** Play treats
      "advertises features the app does not have" as a listing violation, and the tracks are still on
      1.0.0 / 1.3 (§4). Listing and release go up together.
      ⚠️ **French and Italian have no headroom** — 3992 and 3993 of 4000, where English is 3628. French
      came in at 4134 and needed thirteen trims. A future English paragraph cannot simply be translated
      into those two; something has to come out first.
      ℹ️ Each locale uses **its own keywords and the vocabulary its own draft settled on** — *Köttel*,
      *cagarrutas*, *crottes*, *palline*, *bolinhas*, *bobky*, *котяхи* — rather than being a translation
      of the English. A listing using the clinical word for what the app calls something else reads as a
      different app. All nine carry ADR-0001's closing *"a record, not a diagnosis"* paragraph.
      ℹ️ **Release notes for 1.1 → 1.6 are owed and deliberately not drafted** — they are written at
      upload time against what that build changes. Nine per release from 1.6 on.
- [x] **Decide the lagging-translation policy** when the test is generalised — strict red build, or a
      dated `translations-pending` allowlist. ✅ **Answered 2026-08-16, and it was neither.** Both options
      ask *how much lag to tolerate*; the answer is *where to ask*. **Completeness moved out of the test
      into `scripts/translation-gate.py`**, which CI runs on every pull request beside the schema gate:
      **free while you work, strict before it merges.** The point is translating **once** — under a
      red-build rule the copy is translated against the draft wording and again after review reworded it,
      nine times over. The allowlist was dropped as unnecessary: it existed to make lag *visible*, and a
      gate that refuses the merge makes lag impossible. The gate catches **missing** (split by whether
      this branch introduced them), **stale** (English moved here, translation did not), **orphans**, and
      an **unusable comparison** (no merge base, so the stale check cannot run — added 2026-08-17);
      `--report` prints the same list and exits 0 for use mid-branch. **All failure modes proven to fire
      against CI itself**, one pushed commit each on PR #140. That is what caught the stale check being
      dead in CI all along: the build job checked out the PR merge ref at depth 1 against a shallow
      `main`, the two histories shared no ancestor, and `git merge-base` failing read as "nothing
      changed". Fixed by `fetch-depth: 0`, and by making the gate fail rather than degrade.

---

## 8 — Open-source licence attribution 🟢 built 2026-08-14, ships in 1.5

Raised while grilling Phase 6 and deliberately **not** folded into it. The app ships Room, Compose,
Coil 3, Vico and ML Kit and carries **no attribution of any kind** — no string, no asset, no screen.
Apache-2.0 §4 asks for the licence and NOTICE to travel with the binary.

- [x] **Decide the mechanism**, which is a dependency question wearing a UI costume: Google's
      `play-services-oss-licenses` plugin (off the shelf, but a **second** Play-services-dependent
      library in a project that quarantines its first one behind an interface — ADR-0009), or a Gradle
      task generating an asset the app renders itself (no dependency, more code, ours to keep working).
      A hand-typed list is neither — it is wrong one dependency bump later and nobody notices.
      **Decided 2026-08-14**: `app.cash.licensee` at build time, rendered by our own Compose screen —
      see [`phase-7.5.md`](phase-7.5.md) §3. Build-time only, so ADR-0009 is untouched.
- [x] Then build it where the answer says it belongs. Support is the app's only About-shaped screen.
      **Built 2026-08-14**: a row on Support's last card, a generated list of **201 artifacts** under
      four licences, and the Apache-2.0 and BSD-3-Clause **texts bundled** so the licence travels with
      the binary rather than being linked. Seen in both locales.

**Before production launch**, which is when the exposure stops being theoretical — not before 1.3.

---

## 9 — A weight *gain* raises nothing 🟢 built 2026-08-14, ships in 1.5

Found by a tester, 2026-08-09, and written down here so it does not get lost. **Decided 2026-08-14 in
[ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md)** and **built the same day**
(§6.5's fourth box, now ticked); everything below is the question as it stood, kept because the ADR answers
it point by point. **The tester's own reply is still owed** — their *"5 kg plus"* was a number rather than
a change, and the honest answer is that Binky will never call a weight too high, only say that it moved
(phase-7.5.md §1).

**What they saw.** A bunny putting on a lot of weight — their words were "5 kg plus" — produces no flag,
no notification, nothing. Only losses are ever raised.

**Confirmed in the code, and it is deliberate rather than an oversight.** `WeightTrend.kt`'s trigger is
one-sided: `current.grams <= baseline.grams - threshold`. The baseline is the *second-lowest* of the prior
window, chosen so a lasting rise cannot park the bunny permanently "above baseline" and mute every later
drop. So the whole mechanism is built around loss, which is what ADR-0001 was written about — a rabbit
losing weight is the acute, hours-matter signal.

**What has to be decided before anything is built**, and none of it is obvious:

- **Is a gain the same kind of event?** Loss is acute; gain is chronic. A symmetrical trigger would fire
  on the same timescale as a loss and be wrong about what it means.
- **What threshold, against what baseline?** The current baseline exists to be resistant to rises. A gain
  rule cannot reuse it — it would need its own, and possibly its own window.
- **What may the copy say?** ADR-0026 and ADR-0001 both bind here, and "your rabbit is overweight" is
  medical advice, which this app does not give. *Health features observe; they never advise.* The honest
  form is closer to the trend flag's own voice: a fact about the numbers, not a verdict about the rabbit.
- **Is 5 kg even the case to serve?** A Flemish Giant is legitimately 6–10 kg. Any absolute number is
  wrong for some breed, which is an argument that only *change* can be flagged, never a weight.
- **Which surface?** The trend flag card already exists and already says "worth a closer look" without
  diagnosing. Reusing it is cheaper than a second mechanism — but then the dot means two things.

**Do not fold this into Phase 7.** That phase was *same functionality, new looks*, and a new trigger is new
functionality by definition — which is why it went to 7.5 and ships as 1.5. The decision was grilled first
and written as ADR-0028; the build is §6.5's fourth box.

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
