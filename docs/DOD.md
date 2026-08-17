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

## 7 — Phase 8: nine languages 🟢 planned, not started

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
- [ ] ⤷ **Phase 7.5 owns this box as of 2026-08-14** — the tool is built there, on `en` + `pl`, so this
      phase starts with it in hand and the Polish after set is shot in passing. Everything below stays
      here as the record of *why*; the work is §6.5's.
      **Make the capture driver locale-aware — needles that resolve through resource names**, carried
      from Phase 7 (§6) on 2026-08-13, where it was the one box that phase did not close. `--locale`
      already exists on `screenshots.py` and already switches the app; what does not work is everything
      after it, because **the scene needles in `edge-to-edge.py`'s table are English string literals**
      and `tap("Choose which bunny")` matches nothing in Polish. Every scene fails at its first tap.
      **The fix has a clean shape, thanks to ADR-0013**: every user-visible string is a resource in every
      locale and `TranslationTest` keeps them level, so a needle can resolve *through the resource name* —
      parse `values/strings.xml` and `values-<locale>/strings.xml`, build the map, translate at tap time.
      Two wrinkles: several needles are deliberate **substrings** of their string (`"What you noticed"`),
      and several are not resources at all (`Bijou`, `Metacam` are sample data, identical in every
      locale), so the lookup must **fall through to the literal** rather than fail.
      Build it **before drafting the seven** — it is the copy-length canary, and a draft that clips is
      cheaper to find before a native speaker reads it than after. It belongs in `edge-to-edge.py`, where
      the needles live; `screenshots.py` imports that table rather than copying it, so both get it.
      **Two driver facts carried out of Phase 7, both paid for the hard way:**
      ⚠️ **The seed's 8:00 PM dose wrecks evening captures, and Do Not Disturb is the fix.** Two runs were
      wrecked and a third crippled before the cause was pinned: `reset_to_seeded` recreates the Metacam
      course, whose 20:00 dose is minutes in the past, so a heads-up banner (`importance=4`, two actions)
      posts a minute or so after **every** seed — over Home, exactly where `SELECT_BUNNY` taps. The tap
      opens the course, `AUTO_CANCEL` clears the banner (so a later `dumpsys notification` finds nothing
      and the evidence looks impossible), and **Nav3's `rememberNavBackStack` then restores that screen on
      every relaunch**, so one stolen tap poisons every scene after it. `cmd notification set_dnd on`
      suppresses the banner without touching `POST_NOTIFICATIONS`, so the reminder copy the scenes
      photograph stays truthful. With DND on a full dark cell ran 62/62 with zero skips where the two runs
      before it had cascaded. **Turn it off afterwards** — it is a phone-wide setting.
      ⚠️ **Scene isolation needs a return-to-Home step, and it is still unwritten.**
      `am start -S -f 0x10008000` in `relaunch()` is correct hardening and stays, but it does **not** clear
      a restored Nav3 back stack, which is the actual failure above. `KEYCODE_BACK` is not the fix either:
      backing past Home exits to the launcher and makes the following scenes worse. The matrix's 212 clean
      scenes did not prove the current code against this case — that run started after the 20:00 dose had
      already fired. **Write the step while building the locale work**, since a locale run walks every
      scene twice over.
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
- [ ] Draft `de es fr it pt-BR cs uk` into **`translations/<locale>/`, not `res/`** — `values-de/`
      existing means every German phone gets it, reviewed or not. `locales_config.xml` is a *picker*
      list, **not** a delivery filter.
      🟡 **`de` drafted 2026-08-16** — 685/685, and the staging area itself now exists: `TranslationTest`
      holds a draft to every rule it holds a shipped file to (proven by breaking the German plural
      and the German format argument and watching both redden), `translations/` is a declared test
      input so Gradle cannot report a stale verdict on it, and the gate prints what a draft still
      owes without ever gating on it.
      ℹ️ **German costs nothing to §7.3, and that is the surprise.** The perfect tense with *haben*
      carries no gender and predicate adjectives do not inflect, so *"Es ist archiviert"* and
      *"Lebt allein"* are simply correct — the trap that rewrote two Polish strings does not exist
      here. `care_every` is where German pays instead: the host takes either a bare unit (*Woche*)
      or a counted gap (*6 Wochen*), and no German preposition governs both — *"Alle Woche"* is not
      idiomatic and *jede/jeder/jedes* would have to guess the unit's gender. It is `Rhythmus: %1$s`,
      a label rather than a sentence, and it is the first thing to put in front of the reviewer.
      ⚠️ **Four decisions the native read-through has to confirm, not just read past:**
      `destination_care` = *Pflege & Medis* (a bottom-nav tab, and *Pflege & Medikamente* does not
      fit — the fallback is *Pflege* alone, losing the meds half rather than clipping it); *Klo* for
      the litter tray and *Köttel* for droppings (what German rabbit owners say, where *Kot* is the
      clinical word §5 rejects); *Im Blick* for the watch (*Beobachtungszeitraum* collides with
      Beobachtung and *Wachphase* reads as sleep); and three breed rows that are mappings rather
      than translations — Harlequin (*Japaner* in the standards, *Harlekin* in use), the UK *Polish*
      (*Hermelin*), and the lop family, which splits differently either side of the Atlantic.
      German's banned list needed no argument: *überfällig* appears nowhere, `care_due_overdue` is
      *%1$s nach dem Termin* and `care_notification_overdue` is *der Termin ist verstrichen* — the
      same move Polish makes with *po terminie*. `dose_status_skipped` is *Ausgelassen*, agentive
      the way *Pominięta* is.
      🟡 **`es` drafted 2026-08-16** — 685/685, mechanically green, into `translations/es/`.
      **Two to go: `cs uk`.**
      ℹ️ **Spanish is the first language to pay §7.3 on the bunny's side rather than the
      owner's**, which is the reverse of German. The owner's half is free — the compound perfect
      with *haber* does not agree with its subject, so *has guardado* and *he mirado* carry no
      gender and the file needs no rewrite. But *conejo* is masculine where *Kaninchen* is
      neuter, so an adjective about the animal has to pick one. **The policy is the masculine
      generic, Spanish's unmarked form, stated in the file's header rather than hidden** — with
      the genderless wording preferred wherever it is equally natural: *Vive sin compañía* (not
      *solo*), *%1$s (en el archivo)* (not *archivado*), and `archived_banner` / `archived_on`
      both recast around the noun. No parenthesised suffix appears anywhere, which is the rule
      that actually matters.
      ⚠️ **Four decisions the native read-through has to confirm, not just read past:**
      `destination_care` is ***Cuidados* alone** — Spanish has no established short form for
      "meds" the way German has *Medis*, and *Cuidados y medicación* is twenty-one characters in
      a bottom-nav tab, so the meds half is lost rather than clipped and 5e's point with it;
      ***cagarrutas*** for droppings, the *Köttel* decision one language on (fallback
      *excrementos*, and *bolitas* was rejected outright as exactly the food-pellet ambiguity the
      brief warns about); ***seguimiento*** for the watch, where *vigilancia* carries alarm
      ADR-0001 forbids; and ***Bienvenida a Binky*** as a noun, because *Bienvenido/a* would
      gender the owner on the first screen they ever see.
      ⚠️ **Four breed rows are mappings rather than translations**, as German's three were:
      **Dutch → *Holandés*** against **Netherland Dwarf → *Enano holandés***, which Spanish
      genuinely collides where German splits them with *Farbenzwerg*; the **lop family**
      (*belier*), which splits differently either side of the Atlantic; **Himalayan →
      *Himalayo***, where continental standards say *Ruso*; and the UK **Polish → *Polaco***.
      ℹ️ **Three traps cost Spanish nothing, and it is worth knowing which.** §7.1's *Normal* —
      Polish's five distinct forms and the file's single biggest trap — is one word six times,
      because *normal* is invariable in gender. §7.2's `care_every` is *Cada %1$s*, and *cada*
      governs a bare unit and a counted gap alike, so the sentence German had to give up
      survives. And `photo_import_partial` earns its plural for the second language running:
      *no se pudo leer* against *no se pudieron leer*, where English has nothing to vary.
      🟡 **`fr` drafted 2026-08-16** — 685/685, mechanically green, into `translations/fr/`.
      ⚠️ **German's `care_every` problem is not German's**, which is what the last draft asked
      to have checked. *Tous les jours* but ***toutes** les semaines*, and the app cannot know
      which unit it is about to substitute; the unit plurals cannot carry the article either,
      because the editor puts them beside a number field of their own. So the host became a
      label — `Rythme : %1$s` — and that decided `care_due_in` and `care_due_overdue` in turn:
      *À faire dans %1$s* and *À faire depuis %1$s*, an invariable infinitive that states the
      timing and judges nothing. **Two of three languages pay it**, which makes Spanish's
      *Cada* the exception rather than the rule.
      ⚠️ **§7.4 exists in French, for a reason its entry does not name.** French has no cases —
      but *de* elides before a vowel, and the app can no more elide a name the owner typed than
      Polish can decline it. *"Photo de Alice"* is simply wrong. Six strings put the name first
      or drop the preposition: `home_about_bunny`, `photo_description`,
      `bunny_avatar_description`, `watch_expired_title`, `watch_notification_title` and
      `document_page_description`, whose second argument is a document title. A seventh,
      `photo_gallery_empty_help`, keeps its argument in the same job by changing the verb —
      *des photos qui **montrent** %1$s* — which is §7.6's trap and §7.4's in one string.
      ⚠️ **Apostrophes are typographic (’), not `\'`.** Correct French typography, and it also
      removes a class of failure the other drafts never faced: French needs some two hundred
      escapes, and **a missed one would not surface until promotion**, because a staged draft is
      never compiled. A later edit must not "fix" them back.
      ⚠️ **Four decisions the native read-through has to confirm, not just read past:**
      `destination_care` is ***Soins* alone** — Spanish's outcome for Spanish's reason, since
      *médocs* is too casual for a label seen on every screen and *méds* is not French;
      ***crottes*** for droppings (the *Köttel* / *cagarrutas* decision a third time, where
      *excréments* is the clinical word §5 rejects and *crottins* belong to horses), with
      ***caecotrophes*** beside it; ***suivi rapproché*** for the watch, where *surveillance*
      carries the alarm ADR-0001 forbids; and ***Sautée*** for `dose_status_skipped`, agentive
      the way *Pominięta*, *Ausgelassen* and *Omitida* are — *la dose s’est sautée* is not
      French, which is §6's own test. The fallback is *Omise*.
      ℹ️ **French finds a §7.1 divergence English hides**: *weigh-in* and *weighing* are two
      words there and one word here. `care_type_weigh_in` is ***Contrôle du poids*** rather than
      *Pesée*, or `care_history_weight_help` would have read "les pesées comptent comme des
      pesées". Nothing predicted it — it shows only from the sentence downstream.
      ⚠️ **Six breed rows are mappings rather than translations**: the **lop family** (*bélier*),
      **Himalayan → *Russe*** — the continental standards' name, where Spanish went the other
      way with *Himalayo* — the UK **Polish → *Hermine*** (German's *Hermelin*), **Dutch →
      *Hollandais*** against **Netherland Dwarf → *Nain néerlandais***, which French keeps apart
      where Spanish collides them, plus **Mini Rex → *Rex nain*** and **Rhinelander → *Rhénan***.
      ℹ️ **Three traps priced, against the table the other two drafts started.** §7.1's *Normal*
      costs **three** forms of six, between Polish's five and Spanish's one. §7.3 splits the way
      Spanish's does rather than German's — the owner's half free, because the compound past
      with *avoir* does not agree with its subject; the bunny's half paid, because *lapin* is
      masculine, so the masculine generic is stated in the header and genderless wording used
      wherever it is equally natural. And `photo_import_partial` earns its plural for the
      **third** language running (*n’a pas pu être lue* / *n’ont pas pu être lues*) while
      needing a **dodge** neither of the others did: the *added* count sits on the same string
      and the wrong plural axis, so it is a noun — *Ajout : %1$d sur %2$d* — rather than a
      participle that would be wrong half the time.
      🟡 **`it` drafted 2026-08-17** — 685/685, mechanically green, into `translations/it/`.
      ℹ️ **`care_every` survives as a sentence, which settles what the German draft asked.**
      *Ogni* governs a bare unit and a counted gap alike (*ogni settimana*, *ogni 6 settimane*),
      so Italian needs neither German's label nor French's, the unit plurals stay in the citation
      form, and `care_due_in` / `care_due_overdue` are rewritten for §6's reason rather than for
      grammar: *Da fare tra %1$s* and *Da fare da %1$s*. **Two of four pay it, two do not** —
      Spanish was not the exception it looked like, so the thing to ask a new language is which
      side it falls on, not whether it is the odd one out.
      ℹ️ **§7.4 costs Italian nothing, and that is the first nil result in four.** Italian
      declines nothing *and* does not have to elide — *foto di Alice*, *informazioni su Alice*,
      *a Alice* are all correct as they stand, the euphonic *ad* being a style choice rather than
      a rule — and no article precedes a first name. Every name-substituting string was read with
      a vowel-initial name in it and none needed reordering, so `home_about_bunny` keeps English's
      shape. **"Nothing" is a legitimate answer to §7.4**, not a sign the check was skipped.
      ⚠️ **Italian's own §7.2 trap is the preposition swallowing the article**, which is Polish's
      problem in a language with no cases: *in* + *gli* is **negli**, *in* + *l’* is **nell’**, and
      the app cannot contract at run time. The four `weight_chart_window_*` are **pre-inflected** —
      *negli ultimi 30 giorni*, *nell’ultimo anno* — exactly as Polish pre-inflects for the
      locative, which is the first reuse of that technique outside the language it was written
      for. Both hosts take the fragment bare; a third host with a different preposition would
      break all four at once.
      ⚠️ ***Saltata* stays banned, which is the mirror image of Polish's *pominięta*.** Same test,
      opposite answer: *saltare* is agentive when transitive (*ho saltato la dose*), but *è saltata
      la dose* is idiomatic for a thing that simply fell through, and a status chip has no subject
      to disambiguate it. `dose_status_skipped` is ***Omessa*** — Spanish's *Omitida* and French's
      fallback, reached independently. *Mancata*, *dimenticata* and *scaduta* appear nowhere, and
      `backup_folder_forget` is *Rimuovi questa cartella* rather than *Dimentica*, because the gate
      reads the file for those words rather than for their sense.
      ⚠️ **Four decisions the native read-through has to confirm, not just read past:**
      `destination_care` is ***Cure e farmaci*** — **the meds half survives**, where Spanish and
      French both dropped it, at fourteen characters, the width German accepted for *Pflege &amp;
      Medis*; **the capture driver is what settles it**, and the fallback is *Cure* alone.
      ***Palline*** for droppings, where *feci* is the clinical word §5 rejects and *cacca* the
      baby talk §3 rejects — Spanish's objection to *bolitas* does not carry over, because pelleted
      food is *mangime* or *pellet* in Italian, never *palline*. ***Controllo ravvicinato*** for the
      watch: *sorveglianza* carries the alarm ADR-0001 forbids, *osservazione* collides with the
      record type, *monitoraggio* is clinical. And ***terapia*** for the medication course, because
      **English *care* and *course* both want *cura*** and the two meet on one screen — a §7.1
      collision running the other way, two English words folding into one Italian one and split by
      hand.
      ℹ️ **Three traps priced against the running table.** §7.1's *Normal* costs **three** forms of
      six, French's count: *Normale* four times, ***Normali*** for the droppings' size — which
      agrees with the pellets rather than with the measurement, the only split in the file that
      turns on number instead of gender — and *Beve normalmente* for water. §7.3 splits the
      Spanish/French way, the owner's half free because *avere* does not agree with its subject
      (the trend flag is *Dal %3$s %1$s ha perso %2$s*), the bunny's half paid — with **two dodges
      neither of them had**: the possessive agrees with the thing possessed (*i suoi dati*), and a
      pronoun can hang off the common noun *il coniglio*, whose masculine is a fact about the word
      rather than a guess about the animal. And `photo_import_partial` earns its plural for the
      **fourth** language running, needing French's noun dodge for the added count.
      ⚠️ **`observation_not_checked` is the string most worth reading in place.** One resource sits
      under four fields of two genders — *appetito* and *umore* masculine, *attività* and *acqua*
      feminine — so any participle is wrong on half the screen. It is *Nessun controllo*, a noun
      phrase, which is also what keeps it a fact about the record (ADR-0001).
      🟡 **`pt-BR` drafted 2026-08-17** — 685/685, mechanically green, into `translations/pt-BR/`.
      Brazilian throughout, not European: *celular*, *tela*, *arquivo*, and the gerund
      (*está comendo*, never *está a comer*). pt-PT would be a second locale, not an edit to this
      one.
      ⚠️ **Portuguese's own trap is the plural category `one`, and it is the first one no other
      language's record warns about.** CLDR gives `pt` *one: i = 0..1* — French's rule, not
      Italian's — so **a count of 0 renders the singular item**. In French that is correct
      (*0 jour*); in Brazilian Portuguese it is wrong, because nobody writes *0 página*. **No
      plural table can fix it**: the category is right and the language disagrees with it, so the
      only question is whether a given count can actually be zero. The code was read for it and
      **three can**: `backup_restored_overlaid` renders unconditionally (an Essential-scope backup
      of a bunny with no avatar restores none), `delete_records_sole_owned` renders whenever the
      second delete dialog opens and `DeleteConfirmation.kt` opens it when *either* count is above
      zero, and `document_page_count` can be zero for a document whose pages were all removed. The
      first two are recast as labels — *Imagens vindas do backup: %d* — which is right at 0, 1 and
      2 alike; **the third is left wrong knowingly**, because a label reads badly in a list row and
      the state is rare. Everything else is guarded at ≥ 1, checked one by one. **Every future
      plural in this app now owes this question.**
      ⚠️ **Four decisions the native read-through has to confirm, not just read past:**
      ***consulta*** for a vet visit, which is §5's *Avoid* column overruled on purpose — the brief
      rejects *consultation*, and in Brazil *consulta* is simply what the appointment is called
      while *visita* means somebody coming to see **you**; the vocabulary's intent survives in the
      copy instead, since nothing in `visit_*` mentions money. ***Backup*** left in English, the
      one word where ADR-0013's "no English left behind" and ordinary Brazilian usage disagree
      (*cópia de segurança* reads as Portugal) — roughly thirty strings if the ruling goes the
      other way, and note `destination_home` went the other direction as ***Início***.
      `destination_care` is ***Cuidados*** alone, since *Cuidados e remédios* is nineteen
      characters and there is no short BR form for *meds* — **three of five now drop the meds
      half**. And ***bolinhas*** for droppings, the *Köttel* / *cagarrutas* / *crottes* /
      *palline* decision a fifth time: Spanish rejected *bolitas* for colliding with food pellets
      and Portuguese has no such collision, because pelleted food here is *ração*. Cecotropes are
      *cecotrofos*, the tray is a *caixa de areia*, and the watch is ***acompanhamento***
      (*vigilância* carries ADR-0001's alarm, *observação* collides with the record type).
      ℹ️ **`dose_status_skipped` is *Pulada*, and it is Italian's finding with the opposite
      answer.** Same metaphor, §6's same test: *pulei a dose* is agentive and *a dose pulou* is
      not Portuguese at all, where *è saltata la dose* **is** ordinary Italian — which is exactly
      why *saltata* stays banned there and *pulada* passes here. The fallback pair, if a reviewer
      finds it too colloquial, is *Omitida* / *Administrada*, and it moves together with
      `dose_status_given` (*Dada*): the two have to share a register.
      ℹ️ **Three traps priced, and two questions closed.** §7.2's `care_every` is ***A cada %1$s***
      and governs a bare unit and a counted gap alike, so **three of five keep the sentence** —
      German's and French's label is now the minority shape, not the expected one. §7.2's
      *pre-inflection* is paid in full, in Italian's coin: *em* + *os* is **nos**, so the chart
      windows carry the contraction (*nos últimos 30 dias*, *no último ano*) and both hosts take
      them bare — third language for the technique. §7.1's *Normal* costs **three** forms of six
      (*Normal*, ***Normais*** for the droppings' size, ***Bebendo normalmente*** for water, whose
      neighbouring chips are gerunds), and §7.1's other rows cost **nothing**: *Nome* serves both
      the bunny and the vet, *Não se sabe* serves both *Unknown*s — identical on purpose, and the
      second is also the only genderless option. ***Controle de peso*** for the weigh-in reminder
      against *pesagem* for one weighing, the French/Italian divergence found a **third** time.
      §7.3's owner half is nearly free (the simple past does not agree), paid twice:
      *Boas-vindas ao Binky* and *Você pediu este lembrete*.
      ℹ️ **§7.4 comes back empty for the second time, and this one has a caveat worth keeping.**
      Portuguese neither declines nor elides, so *Sobre Alice* and *Foto de Alice* stand and not
      one string was reordered — **but the trap exists one register away**: spoken Brazilian
      Portuguese puts an article before a first name (*a Alice*) and *de* + *a* contracts to *da*.
      The file stays in the written standard, which takes no article, and that is what keeps the
      contraction out of reach. A reviewer who prefers the spoken register **cannot have it** —
      the app cannot know a name's gender, so *da/do* is unproduceable.
      ⚠️ **The breed rows go the other way from every earlier draft.** Brazil's pet-rabbit
      vocabulary follows ARBA rather than the European standards, so **the whole lop family stays
      in English** (*Mini Lop*, *Holland Lop*, *French Lop*, *English Lop*, *Dwarf Lop*, *American
      Fuzzy Lop*) where Spanish, French and Italian each had a native name (*belier*, *bélier*,
      *ariete*) — the single row most likely to be wrong, and one for a Brazilian breeder rather
      than a dictionary. The same lean argues *Polonês* for the UK **Polish**, and it is
      ***Arminho*** anyway, joining *Hermelin* / *Hermine* / *Ermellino*, with the choice handed to
      the reviewer. **Himalayan → *Himalaio*** goes Spanish's way against French's and Italian's
      *Russe* / *Russo*; **Dutch → *Holandês*** stays apart from **Netherland Dwarf → *Anão
      holandês***; **Lionhead → *Cabeça de leão*** is genuinely current here unlike the English
      names around it; and **Mixed / unknown → *Sem raça definida***, Brazil's real idiom (SRD),
      which is why it earns first place rather than being sorted there.
- [ ] Promote one language per commit, only after its native read-through: move into `res/`, add the
      `<locale>` line, the `AppLanguage` entry, and the endonym label.
- [x] `AppLanguageTest` extended to compare resource directories too (`values-pt-rBR` vs `pt-BR` — two
      spellings of one locale in two files). ✅ **Done 2026-08-16**, though it landed in `TranslationTest`
      rather than `AppLanguageTest`: the BCP-47 → qualifier conversion is a single function there, so the
      two spellings cannot be written independently and then compared. `AppLanguageTest` keeps its own
      job, which is the enum against the XML.
- [ ] Play listing title + short + full description in all nine. Screenshots may lag (Play falls back);
      they need the locale-aware driver above — **not** a `--locale` flag, which already exists and is
      not the missing half.
- [x] **Decide the lagging-translation policy** when the test is generalised — strict red build, or a
      dated `translations-pending` allowlist. ✅ **Answered 2026-08-16, and it was neither.** Both options
      ask *how much lag to tolerate*; the answer is *where to ask*. **Completeness moved out of the test
      into `scripts/translation-gate.py`**, which CI runs on every pull request beside the schema gate:
      **free while you work, strict before it merges.** The point is translating **once** — under a
      red-build rule the copy is translated against the draft wording and again after review reworded it,
      nine times over. The allowlist was dropped as unnecessary: it existed to make lag *visible*, and a
      gate that refuses the merge makes lag impossible. The gate catches **missing** (split by whether
      this branch introduced them), **stale** (English moved here, translation did not) and **orphans**;
      `--report` prints the same list and exits 0 for use mid-branch. **All three failure modes proven to
      fire.**

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
