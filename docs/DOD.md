# Definition of done — what is still open

The **live checklist**. `PLAN.md` holds the reasoning and the record; this file holds only what is not
yet ticked, so a session can pick up the work without loading 3 000 lines. Keep it short: when an item
closes, tick it here, write the *result* into `PLAN.md`, and delete the detail from this file.

**Phase 5** (vet, medications, documents, dose reminders — ships as 1.2) — software half **done**,
evidence half open. Status read 2026-08-05 20:30. **Phase 6** (the support contact — ships as 1.3) is
built, device-tested and documented as of 2026-08-06; only §5's two hand items are left, and 1.3.0 is
waiting on a release PR.

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
- [ ] Edge-to-edge matrix re-run (`scripts/edge-to-edge.py`, **61 scenes** — Support added two).
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

## 3 — The document downsample spec, still uncalibrated 🟠 the only open item that can change code

Phase 5's intro calls this *"a deliverable and not an assumption"*, and `MediaFiles.kt:60` still carries
the **unverified** comment on `MediaKind.Document` = `LongEdge(maxEdge = 3000, quality = 92)`. The
fixture exercises the downsample on a 3200 px page and the pinch-zoom viewer reads it back, but the
judgement has never been made.

- [ ] Scan a **real vet printout** — not a fixture — and answer two questions: is the small print legible
      on the phone at full zoom, and what does the file weigh. Retune the two numbers if not, or delete
      the "unverified" comment if so.

Do this **before** real documents pile up. `MediaFiles` re-encodes at write time and keeps no original,
so every scan already taken is permanent at whatever spec was in force when it was written.

---

## 4 — The Console half of 5j 🟡 blocked on Play, not on us

Play's 12-testers / 14-day count was still running on 2026-08-05. Nothing here is work we are holding.
All three land in **one sitting** once the count clears, in this order:

- [ ] Upload **1.3** to the **internal** track, then **closed** — not 1.2.0, per §5's closing note: same
      schema, same migrations, and uploading both spends a release cycle to prove nothing extra. If the
      count has cleared, production becomes available for the first time — whether 1.3 takes it is an
      ADR-0009 decision made then.
- [ ] **Screenshots for both listings** (EN + PL), owed for the screens 1.1 and 1.2 both added.
      §2's tap blocker is fixed as of 6c, so this is no longer waiting on tooling — only on Phase 7,
      per §6's closing note.
- [ ] **The field upgrade proof: 1.0.0 → 1.3**, real bunny history intact. The Xiaomi's Play build is on
      **1.0.0**, not the 1.0.1 4h assumed, so the chain crosses *both* hand-written migrations. It cannot
      run locally — the installed build is Play-signed and a local APK is refused on signature mismatch —
      so the update must **arrive from a track**, downstream of the upload above.

---

## 5 — Phase 6: the support contact 🟡 built and documented, two hand items left

Built, driven on the device and written up — **6a, 6b, 6c and 6d are all done**. The record is
[`phase-6.md`](phase-6.md), and `PLAN.md`'s status list ticks Phase 6 on the build and the documents.
`release-please` already has **1.3.0** waiting on PR #93; merging it is the cut.

Two boxes outlive the code, and neither is work a build can do:

- [ ] Set `binky.support@gmail.com` as Play's **per-app contact email** in *Store settings*. **Not blocked
      by §4's testing count** — Store settings is editable today. It is what makes the app, the listing and
      the privacy policy's *Contact* section name one inbox; the app hardcodes the address, so anything
      else in that field points the listing at a mailbox the app does not use.
- [ ] **Read a support mail that actually arrived**, carried from 6c's gate: send a bug report from the
      phone and confirm in the inbox that the diagnostics block is **visible**, not collapsed behind
      Gmail's signature `…`. The block is separated by a blank line and never `-- ` for exactly that
      reason, and a draft cannot prove it — only a delivered message can. Everything up to the send is
      verified in both locales.

**1.3 supersedes 1.2.0 on the tracks — do not upload both.** Same schema 6, same two hand-written
migrations, so §4's field-upgrade proof retargets to **1.0.0 → 1.3** and still crosses `MIGRATION_4_5` and
`MIGRATION_5_6`. Uploading 1.2.0 first buys a second release cycle and proves nothing 1.3 would not.

---

## 6 — Phase 7: the redesign 🟢 sketched, not started

Shape and decisions in **[`phase-7.md`](phase-7.md)**. **Same functionality, new looks** across all 26
routes — no schema, no new nav key, no new dependency, so the test suite stays valid throughout and the
phase ships screen by screen. **Runs after Phase 6** (so Support is designed once) and **before Phase 8**
(so its copy is translated once).

The per-screen worklist is **not written yet, on purpose** — it cannot be, before the visual language
exists. These are the items that come first.

- [x] **Decide dynamic colour** — **own a brand**, 2026-08-06,
      [ADR-0027](adr/0027-binky-owns-its-palette-material-you-is-opt-in.md). Default off, Material You kept
      as a Settings toggle. The deciding argument turned out to be **ADR-0012**, not brand preference: that
      ADR buys the whole redesign with *"the visual pass then edits one file"*, and `dynamicColor = true`
      means nothing reads that file on Android 12+. Two things follow into the work below — a **full**
      scheme is owed rather than three roles (`surface`, `background`, `outline` and every
      `on*`/`*Container` are still M3's purple baseline), and the toggle needs **two new strings in both
      locales**, which is the first partial answer to "does any string change?" below: yes.
- [x] **Capture the before set** — 2026-08-06, **61 scenes × light and dark = 122 PNGs, 23 MB**, in
      `~/binky-screenshots/phase-7/before/{light,dark}/`. Out of the repo, per `docs/edge-to-edge/`'s
      precedent of committing only the shots that make a point. Taken by **`scripts/screenshots.py`**,
      which imports `edge-to-edge.py`'s scene table rather than copying it.
      **The axes changed from what this line asked for**: light/dark rather than en/pl, portrait +
      gesture only. Dark is not a variant of light and does not review as one, and the orientation
      matrix is the *gate* below rather than a design input. **Polish is therefore still owed** — the
      script takes `--locale`, and Phase 8's copy-length canary wants it before that phase starts.
      Four defects were found and fixed getting here; two were in `edge-to-edge.py` and are written up
      in §2, because they mean some existing 4f evidence is wrong rather than merely missing.
- [x] **Fix the visual language** — 2026-08-08, in Claude Design. It went past `Home` and `Weight`: most
      routes now have a drawing. **`phase-7.md` says where the project is and how to read it** — including
      that `get_file` truncates it at 256 KiB without saying so, and that its hexes are hand-picked
      despite claiming to be generated. Take the four seeds, not the hexes.
      ✅ **Nothing is undrawn any more** — 2026-08-10. A turn-10 export adds `10a`–`10n` and their dark
      twins `10a2`–`10n2`, and `github.md` closes with *"Nothing. All 46 frames exist in light and
      dark."* It covers the eight this line listed **and four routes no checkpoint row ever had** —
      course detail, reminder detail, reminder editor and visit editor. The calendar in `7a`/`7b` is a
      new route and **out of scope** for this phase.
      ✅ **And nothing new has arrived since** — re-checked 2026-08-11 against the *live* project
      rather than the download. Remote `github.md` is identical to the exported copy (`last sync
      2026-08-10T18:05Z`, closing line *"Nothing. All 46 frames exist in light and dark."*), and the
      export carries turns `t1`–`t10` with every `10a`–`10n2` present. **There is no turn 11**, so no
      part of the sweep is waiting on a drawing. `list_projects` returning an empty list is not
      evidence either way — it filters to design-*system* projects, and this is an ordinary one; pass
      the id. Reading remote `github.md` is the whole freshness check and costs ~1k tokens.
- [x] **Theme commit first** — 2026-08-08. `Color.kt` generated from the seeds (both schemes in full, 22
      contrast checks green), `Type.kt`, `Spacing.kt`, `dynamicColor` off. Nunito is a new bundled asset,
      not a dependency. Two silent traps written up in `phase-7.md`'s order-of-work step 4.
- [x] **The Material You toggle** — 2026-08-09, so ADR-0027 is whole and dynamic colour is now *off by
      default* rather than unavailable. Hidden below Android 12, where there is no wallpaper palette to
      take. **It moved `AppPreferences` onto `BinkyApplication`**: the theme wraps ADR-0007's
      schema-mismatch screen, and `container` is the `lazy` that *is* the wipe guard, so reading the
      preference through the container would have opened the gate from inside the screen guarding it.
- [x] **The rewrite sweep — every route to the new language.** ✅ **Closed 2026-08-13**, verified against
      the code rather than against this line: all **33** rows of `phase-7.md`'s checkpoint table are ✅, and
      exactly **three** `AlertDialog` sites survive — `MedicationCourseEditorScreen`, `RecordedAtField`,
      `SearchablePicker` — which are the three documented exemptions and no others. The per-route table
      with its mockup ids is
      in [`phase-7.md`](phase-7.md) *("The rewrite checkpoint")*; one commit per route, each building and
      installing. **`Home` (all three states), the bunny switcher, `Weight`, `Record an observation`,
      `Observations`, `Care & Meds` (both states), `New course` and `Record a dose` are done** —
      2026-08-09; Home's no-bunnies state is code-only so far, because emptying the phone to look at it
      would take §1's seed with it.
      ✅ **Care & Meds has now been seen on the device** (2026-08-09), which was the pass it owed for
      moving three delete paths: today's doses, the courses, the inline *Given*/*Skipped* and the
      *Done* on a due reminder all render as drawn, in both themes.
      ✅ **`5a`/`5b` Vets + vet editor and `4e` Bunny editor are done too** — 2026-08-09, both seen on
      the device in light and dark. Vets became one grouped card and gave up its row-level *Delete*,
      which is the fifth and last list-plus-editor pair to follow `1d`; its editor changes **no
      string at all**. The bunny editor kept all eight fields, in order, in the app's own words.
      ✅ **`6e`/`6f` Record a weighing is done too** — 2026-08-09, and the **last-five line is
      adopted**, so three of the four open decisions are now closed. Building it surfaced a shipped
      bug on the screen in front of it: a weigh-in reminder opened both completion dialogs by
      itself and could not be used. Fixed, and worth reading the phase file on *why* a phase of
      device passes missed it.
      ✅ **`6a`/`6b` More, `6c`/`6d` Backup & restore and `4f`/`4g`/`4h` Archived bunnies are done**
      — 2026-08-09, and with them **every drawn route really is redrawn** (this line claimed that one
      batch early; the checkpoint table in `phase-7.md` had the three of them still open). Between
      them they cost the idiom **one parameter** — `ListRow`'s `enabled`, for a row that would open
      something if it could — which is the second sign the language has settled. Each also states an
      exception worth knowing: More spends **none** of the raised-card budget, Backup spends it on
      the section the owner *cannot control*, and Archived bunnies is the one list that stays
      separate cards **and** keeps its buttons, because *Open* leads to a read-only bunny and there
      is nowhere for *Delete* to move to.
      ✅ **All three seen on the device, light and dark** (2026-08-09), including `4f`'s **populated**
      card, which the seed cannot reach — nothing is archived in the sample data, so the `archived`
      scene only ever shoots `4g`. Driven by hand: archive from Home, then More → Archived.
      Two defects came off the phone and neither was visible in the code, both **compounding
      padding**: `SwitchRow` carries its own insets *and* got a section gap, and a screen that ends
      in a footnote rather than a card needs `Spacing.section` at the bottom of the scroll or the
      last line sits under the gesture bar. The scope picker's divider was also asymmetric around the
      selected band. All fixed and re-shot.
      ❓ **One copy question is left open on purpose**: `4f` writes *"Lived with Marzipan"* where the
      app says *"Lives with Nugget"*. The drawing is arguably right — an archived bunny may have been
      rehomed or died — but `housematesLabel` is shared with Home and the bunny editor, so it means a
      second string in both locales. Not taken; recorded in `phase-7.md`.
      ℹ️ **No scene needle broke on any of the three** — the first batch that can say that — even
      though every needle into them reaches into content (`settings`, `backup`, `archived`, `vets`,
      `photos`, `documents`, `support` all tap a More row by title, and those six rows are now
      identical 64dp merged nodes, which is Care & Meds' exact tie-breaking hazard). They survive
      because `6a` changes **no string**. *A content needle survives a redraw that is structural
      only.*
      ✅ **`Settings` is done** — 2026-08-09, the **first route with no drawing at all**, seen on the
      device in light and dark including its picker. Five settings separated by five full-width
      rules become the header rhythm and two cards. It states the rule the other seven undrawn
      routes follow: **a control that cannot name itself gets a section header, a row that names
      itself does not** — *"Show weights in"* is a sentence two chips finish, where *Language*,
      *Colours from your wallpaper* and *Backup & restore* each say what they are inside the row.
      **No string added and none changed; one moved** — `settings_language_help` ("Changing this
      restarts the app") went from under the row into the picker, because it is a warning about the
      act of choosing. The device pass earned its keep again and this time on **type, not padding**:
      `SwitchRow` was at M3's `bodyLarge` and `ListRow` at `titleMedium`, invisible until Settings
      became the first card to hold both. Fixed in `Forms.kt`, so every switch in the app moved with
      it. No scene needle broke — `backup`, `settings-scrolled` and `reminders-sheet` all still land.
      ✅ **`Support` is done** — 2026-08-09, against `9c`/`9d`, seen on the device in light and dark
      (it fits without scrolling). Every string is the app's own, the order is unchanged, and what
      the drawing fixes is **weight**: *Report a bug* takes the full-width filled primary while
      *Request a feature* and *Rate Binky* are outlined, which `9c` promotes to an app-wide rule —
      **one filled button per screen, and it is the one that matters most.** *Privacy policy* became
      a chevron row paired with *Version* in one card, and that pairing is what cost the idiom
      `SingleLineRowHeight`: a subtitle-less `ListRow` is 56dp, not 64dp, and a `FactRow` beside one
      has to be lifted to match. Two of the drawing's measurements were declined for `2b`'s reason —
      20px card padding and 44dp outlined buttons have no home in the system.
      ⚠️ **A second export arrived mid-session and it redrew Settings too** (`9a`/`9b`), a few hours
      after Settings shipped hand-built. **The hand-built structure held** — same two cards, same
      rows, same chip pair, same rhythm — so the idiom is now strong enough to predict a drawing,
      which is the best evidence yet that `Surfaces.kt` earned being written first. `9a` corrected
      two things, both taken: the **current language moves into the row's trailing slot** beside the
      chevron (as a line under the title it read as a section heading of its own, and the hand-built
      version had traded it against the help text), and the **two debug blocks group under one
      *Debug builds only* header** instead of each repeating the phrase in its body. That last one is
      the sweep's **only copy change so far** — one new string plus the prefix stripped from two, in
      both locales, no new words invented. `9a` predates the Material You row and does not show it;
      kept, since removing a shipped setting is not what "new looks" means.
      ✅ **Every route left in the sweep is now drawn, in both themes** (2026-08-10) — so the
      undrawn half of this phase is over, and what remains is thirteen surfaces with a mockup each:
      the chart's four empty states (`8a`/`8b`), the watch-expiry prompt (`8c`/`8d`/`8e`, where `8d`
      is the no-live-flag case, "silence must not read as good news"), and the turn-10 pairs —
      Documents list and detail, Photos grid and viewer, the three setup steps, reminders opt-in,
      **both** schema-mismatch variants, and the four routes that had no row at all: one course, one
      reminder, the reminder editor and the visit editor. Setup has already taken one line of the
      language:
      `BackupScopePicker` draws rows rather than its own card now, so its two callers each supply one.
      **`ui/common/Surfaces.kt` is the shared idiom** every remaining route draws from — section header,
      grouped card, fact row, inset divider, the two card radii, `FabClearance`, and now
      `GroupedCardItem` for a grouped card whose rows are separate lazy items (any list that can run to
      hundreds of rows owes it).
      ✅ **`ui/common/Forms.kt` is the editor half of the same idiom**, decided on `2c` as its own label
      said it would be: `FormSection`, `FieldLabel`, `HelpText`, `ErrorText`, `ChipRow`, `FormChip`,
      `NoteField`. Chips wrap rather than scrolling sideways, a section is a card, help belongs to the
      control above it, free text is an outlined box with a placeholder. Every editor left in the sweep
      draws from it rather than re-deriving it. `RecordedAtField` moved with it, so `6e` inherits the
      treatment already.
      ✅ **`3e` finished the editor kit**: `SingleLineField`, `SwitchRow`, `RemovableChip`, and
      `ChangeableValueRow` lifted out of `RecordedAtField` with an optional label. **Save moved into
      the app bar** — `3e` draws a bottom filled button and then recommends against it in its own
      notes, and the change was taken deliberately rather than slipped in, so **every editor left in
      the sweep has one chrome to copy**.
      ✅ **The media pair is done** — 2026-08-11, `10a`–`10d`: Documents list and detail, Photos grid
      and viewer, all four seen on the device in light and dark. Between them they say where the
      language **stops**. `10b` is the one route where *put it in a card* is the wrong answer — a scan
      of a sheet is the content, so the page stays full-bleed and takes `surfaceVariant` under it, and
      the band that gives a page which does not fill the frame is only visible on the phone. `10c` is
      the one screen that goes **edge to edge** at all: the grid keeps its 2dp bleed while everything
      the app *says* about the photos — the import bar, the viewer's date and caption — stays in the
      16dp gutter, so the rhythm still holds wherever there is type. The empty states moved **in
      place**, as a card where the first row would be, which cost the idiom **one parameter**
      (`MessageCard`'s optional `title`) — the third such, after `ListRow`'s `enabled` and
      `SingleLineRowHeight`. **No string added, changed or moved on any of the four.**
      The device pass earned its keep on `10d`: *Add one* was pushed to the far screen edge by a
      `weight(1f)` on the caption, where the drawing sets it **beside** the caption it acts on.
      `weight(1f, fill = false)` is the fix and the whole of that line's design — a short caption and
      its action stay a pair, a long one still wraps rather than shoving the button off.
      ✅ **The two Weight surfaces that had no drawing are done** — 2026-08-11, `8a`–`8e`, both seen on
      the device in light and dark. **No string added, changed or moved on either**: all four chart
      sentences and every line of the prompt were already the app's own, which is the drawings reading
      the app back to itself for the third time this phase. `8a` adds the **only artwork in the app** —
      three dashed gridlines at `outlineVariant` with one ring on them, at the plot's own [POINT_SIZE]
      and [POINT_STROKE] so the sketch and the real chart draw the same marker. It goes on the two
      single-point states and **deliberately not on the other two**: a grid with a point in it says
      "one reading, nothing to join it to", where an empty grid says nothing the sentence has not
      already said and starts to read as a chart that failed to load. `8c`–`8e` is mostly `BinkyDialog`
      plus two designed lines — the nested trend flag (`nested = true` for the 16dp radius; the colour
      already came from the dialog's `LocalCardSurface`), and ***Close it* going quiet**. Closing a
      watch is an ordinary answer to the question asked, not a deletion — the row it removes is a
      present-tense state rather than a record — so it takes `onSurfaceVariant`, the same treatment
      *Delete document* took inside its menu on `10b`. `8d` needed no change at all: the no-live-flag
      case already stated the record and claimed nothing about the rabbit.
      **The seed cannot show `8c`, by design.** `SampleData.kt` pairs the flag with the *running* watch
      (Bijou) and the steady series with the *expired* one (Nugget), so an ordinary capture only ever
      reaches `8d` — the same trap `4e` fell into. Verifying it meant a throwaway seed patch, one
      expired watch on the flagged bunny, reverted before the commit.
      ✅ **The four Care detail routes are done** — 2026-08-11, `10k`–`10n`: one course, one
      reminder, the reminder editor and the visit editor, all four seen on the device in light and
      dark. These are the four the checkpoint table never listed, and three of them had already been
      *edited* by the Care & Meds redraw without being redrawn by it. **A course and a reminder turn
      out to be the same screen** — what is next, what to do about it, what has happened — so `10l`
      is `10k` twice over; the only difference is what sits below the card's hairline, which is
      *ending* on a course and the **calendar hand-off** on a reminder. A hairline rather than a gap
      is the point: a gap separates two subjects, a rule separates two kinds of action on one.
      **One mark is added and it is the only new one in the sweep**: an overdue reminder takes the
      apricot `CautionDot`. `10l` says the file "already uses it on Care & Meds" and the file did
      not — but the marker's own definition is *the app itself is raising this*, and a reminder whose
      day has been and gone is exactly that. The list row does **not** take it: its trailing slot is
      already spoken for by `3a`'s grammar (a row that asks carries the answer, a row that tells
      carries a chevron).
      **No string added, changed or moved on any of the four** — the fourth batch running that way.
      Two of the drawings' measurements were declined for `9c`'s reason (52dp/44dp buttons in a row
      of peers), and "Pick a date" stayed *Change* rather than minting a string in two locales.
      The idiom cost **three parameters this time, not one**, and each names a state the drawn routes
      never arranged: `ChangeableValueRow.stacked` (a label that is a **question** cannot ride beside
      its own answer, where *Starts*, *Ends* and *Birthday* can), and `enabled` on `SingleLineField`
      and `NoteField` — ADR-0004's archived scope, which the visit editor is the only editor to have.
      `ChangeableValueRow`'s *Clear* also went quiet, which moves the bunny editor with it: of two
      actions on one row, the one that takes something away is `onSurfaceVariant`.
      ℹ️ **One thing the device pass found that the code reads fine as**: a dose row's title wraps to
      two lines. The drawing writes "Given · 10 August at 08:04"; the app writes "Given · Aug 11, 2026
      at 6:47 PM", and two text buttons at M3's 58dp minimum width leave it about 250dp. Accepted —
      the row is still half the height of the card it replaced — but it is the reason a drawing's
      one-line row is a claim about *that* date format and not about the layout.
      ✅ **The last six frames are done** — 2026-08-11, `10e`–`10j`: the three setup steps, the
      reminders opt-in and both schema-mismatch variants. **Every drawn route in the sweep is now
      redrawn**, and this time the line is checked against the table rather than against itself.
      The wizard is *the one place in Binky with no app bar, no switcher and no nav*, which is what
      the whole of `10e` follows from: the step counter is the only orientation on screen, so it is
      tracked out to read as a position rather than a heading, and the title takes the display face
      the wizard alone spends. Step 2's **two horizontal rules become the header rhythm** — a line
      through a screen is the weakest way to say a new subject starts here — and its four loose
      paragraphs about Android's own backup become one card, because they are one subject. Step 3
      answers *two filled buttons on one screen* with **containment rather than demotion**: the
      battery ask sits in an apricot card and its button is the filled button *of that card*, while
      *Finish setup* is the filled button of the screen. Demoting it would have been the wrong answer
      — it is the difference between reminders working and reminders silently not.
      `10i`/`10j` are one screen in two states, and the only deliberate difference is emphasis:
      **share is filled in release**, where it is the only action, and outlined in debug, where the
      destructive continue outranks it. The path gains a container of its own, because a file path
      wrapping mid-name across a plain background is unreadable and this is the one string on the
      screen an owner has to type somewhere else.
      **One string was removed and none added** — step 2's scope help line, which `10f` drops
      because the header above it now says the same thing. Removed from both locales (ADR-0013).
      The idiom cost **one parameter**: `PhotosNotProtectedNote.caution`, and the difference it names
      is *where the note stands*. Inside Backup's export block a plain tint is enough, because the
      block already qualifies it; standing alone between two cards on step 2 it is the one sentence
      describing something the owner can lose, so it takes apricot and the dot.
      ℹ️ **Two of the drawings were declined, both because a frame described behaviour rather than
      looks.** `10h` frames the opt-in as a modal sheet where the app hosts it inline at the foot of
      Care & Meds — a sheet is a trigger to design, not a redraw, and the inline foot *is* ADR-0006's
      point of use. Every state the frame actually draws already shipped correct; what it won was a
      **title**, since the block used to open on a paragraph explaining what reminders are for.
      `10f`'s 20dp gutter was taken as `Spacing.base`: the drawing's stated reason — *so the cards
      line up with every other card in the app* — is right, every other card is at 16dp, and
      `Spacing` has no 20.
      ⚠️ **The device pass found a real defect, and it is the exact rule `10g` exists to answer.**
      All six frames were seen on the phone in light and dark — the wizard through the driver's
      `empty` suite, both mismatch variants through `fake_schema_mismatch`, and `10j` through a
      throwaway `destructiveMigrationAllowed()` patch, reverted. **`10g` draws step 3 *armed*, so it
      never arranged the state a genuine first run is in**: `pm clear` revokes `POST_NOTIFICATIONS`,
      so the honest first-run step 3 is *blocked* — and there *Turn reminders on* rendered as a bare
      filled button directly above *Finish setup*. Two filled buttons on one surface. The fix is the
      frame's own: containment, so the blocked ask takes the same apricot surface its sibling battery
      ask just got, and its button becomes the filled button *of that card*. The two asks are the
      same class anyway — an Android state that stops delivery, and the one screen that changes it.
      This is `10h`'s geometry declined a second time, and for the same reason: it draws the ask
      uncarded *in the sheet*, where it is the only filled button on screen and a card buys nothing.
      **A frame that draws one state has said nothing about the others** — the third time this sweep
      has paid for that (`2a`, `4e`, now `10g`), and the first where the missing state was the
      *common* one rather than the rare one.
      ℹ️ **The other device finding was padding**: the apricot photo note kept the 12dp it has inside
      Backup's export block, and standing alone between two 16dp cards on step 2 it read as cramped.
      The caution variant takes 16dp horizontally; the plain one keeps 12, where matching its
      parent would push its text out to the card's own edge.
      ℹ️ **The wizard is reachable and recoverable after all, which retires `4c`'s objection for this
      route.** `edge-to-edge.py` already had `wipe()` **and** `reset_to_seeded()`, and scenes for all
      three steps; the seed the wipe takes is put back by the same run. What made the earlier call
      right was the 5 Aug run that wiped and had nothing to restore with — that gap is closed.
      Check §1 is **not armed** before running it, which it was not on 2026-08-11.
      ✅ **The dialog retrofit is finished** — 2026-08-11, the **eleven** sites left after the route
      table went all-✅, in their own commit because none of them was a route. The three weight prompts
      (delete plus ADR-0021's two collisions), the observation delete, `TrendFlagDialog`,
      `StartWatchDialog`, `CompleteCareDialog`, `ChooseBunnyDialog`, and ADR-0004's archive and delete
      ceremonies. **Three `AlertDialog`s remain and all three are the documented exemption** — the two
      `TimePicker` frames and `SearchablePicker` — and the last one now carries a comment saying so,
      because a survivor looks like a miss. Two were more than frame swaps: `CompleteCareDialog` is
      `RecordDoseDialog`'s twin (the reminder's name became the **subject line**, the date took
      `RecordedAtField`'s section shape, the note took its placeholder as its label) and
      `ChooseBunnyDialog`'s bare clickable `Text`s became `ListRow`s in a `GroupedCard`. **The first
      batch of the whole sweep to cost the idiom nothing at all.** Five dialogs seen on the device;
      `complete-care`, `choose-bunny` and `start-watch` in both themes.
      ⚠️ **`BunnyDialogs.kt` hid from every count of this retrofit, and the filter was the cause.**
      The inventory was always `grep -v "Dialogs.kt"`, meant to spare `ui/common/Dialogs.kt` — it
      spared `BunnyDialogs.kt` too, by substring, so ADR-0004's two ceremonies were never in a total.
      **Exclude a path, not a basename**, and print what is left rather than counting it.
      ✅ **Four more `AlertDialog` sites took the retrofit** — the course-delete, dose-delete,
      reminder-delete and completion-delete confirmations, plus the visit-delete and attach-a-document
      dialogs, so six on this batch. The attach dialog dropped a `LazyColumn` doing so, for the third
      time in the sweep and the same reason.
      ✅ **Four of the ~20 `AlertDialog` sites took the retrofit `3f`/`3g` asked for**, since they sit
      on routes this batch was redrawing anyway: rename, attach-to-a-visit, manage-pages and both
      delete confirmations now call `BinkyDialog`. Two of them dropped a `LazyColumn` doing so —
      `BinkyDialog` scrolls its own content, and a lazy list nested in a scrolling parent measures
      against an unbounded height and composes every row, losing the only thing it is for.
      ✅ **`ui/common/Dialogs.kt` is the third file of the idiom**, decided on `3f`/`3g` for *all*
      dialogs. Most of the rules are M3's `AlertDialog` defaults; the one that is not is the level,
      and it runs in **opposite directions** — light steps *down* to `surfaceContainerLow`, dark steps
      *up* to `surfaceContainerHigh` to lift off the scrim. That forced the app's first
      `CompositionLocal`, `LocalCardSurface`: a card inside a dialog has to sit one step above it or
      it renders darker than the dialog holding it. The M3 pickers are left alone on purpose.
      ⚠️ **`Weight` moved deleting a weighing onto the editor** — the drawn history row carries a value,
      a timestamp, a change and a chevron and has nowhere to put a button. Nothing was lost: the delete
      ends in the same flag re-check a save does. Every remaining list-plus-editor pair should follow it.
      ✅ **`Care & Meds` followed it three times over** (2026-08-09) and that is the bulk of its diff:
      deleting a **course**, a **reminder** and a **visit** all moved off the list onto
      `MedicationCourseScreen`, `CareReminderScreen` and `VisitEditorScreen`. Unlike Weight, **the
      destination did not already have one** — those screens delete *doses* and *completions*, which
      are sub-records — so each needed the dialog, a `confirmingDelete` flag and three methods, and
      `CareViewModel` lost the machinery for all three. Check the destination before assuming this move
      is free. It also produced the route's other rule: **a row that is *asking* carries the answer, a
      row that is *telling* carries a chevron** — which is why *Done* survives on a care reminder only
      while it is actually due.
      ⚠️ **Two scene needles broke on this route, both silently, and one was a coin flip.**
      `OPEN_WEIGHT_FORM` tapped *"Record a weight"* on the Care list — a button that now appears only
      while the weigh-in is due, which depends on the seeded weighing dates. And `care-reminder` tapped
      *"Every"*: `find` returns the **smallest** matching node, and a course row and a reminder row are
      now both exactly 64dp of full-width merged semantics, so the tie breaks on list order and the
      course wins. Both fixed to name the thing they mean. **Uniform row heights are a new class of
      needle hazard** — the before set's differently-sized cards were breaking ties for us by accident.
- [x] **Decide the four pieces of new functionality the designs introduce** — ✅ **all four closed as of
      2026-08-12**: one adopted (the last-five line), three that turned out never to be new functionality
      (field-absent states, the stale-backup marker, and *"not checked"*). They are decisions, not tasks,
      because this phase is *same functionality, new looks*, and a screen redrawn from a mockup absorbs them
      silently otherwise. The last-five line on Record a weighing (likely adopt), a stale-backup marker
      (needs a staleness rule and copy that implies no fault), field-absent states in the bunny editor, and
      the claim that *"not checked"* is a real stored value. The **calendar route is deferred** — a new nav
      key is out of scope by definition. Listed with reasoning in `phase-7.md`.
      ✅ **The inventory is complete as of 2026-08-09.** It was provisional because `1d` and `1e`, the two
      `Weight` frames, sat past the 256 KiB truncation; both were read from the disk export while building
      `Weight`, and they **add nothing** to the four above. So the only one still owed by a screen that has
      not been built is the last-five line, which lands with *Record a weighing* (`6e`).
      ✅ **One of the four is now closed, and it was never new functionality.** `4e`'s *field-absent
      states* asked whether the app distinguishes "not known" from "not set"; it has since ADR-0016,
      in those exact words — a birthday is a fact nobody may know, a breed is a field nobody filled
      in.
      ✅ **The last-five line is adopted** (2026-08-09, with `6e`) — the only one of the four taken,
      and what earns it a place in an appearance phase is that it is a *guard* rather than a
      feature: a digit too many in an empty box poisons the series ADR-0001's flag reports on.
      ✅ **The stale-backup marker is closed too** (2026-08-09, with `6c`), and like the bunny
      editor's field-absent states it was **never new functionality**. The staleness rule this line
      said was missing already ships: `AutoBackupStatus.Recorded.stale` is a fortnight,
      `backup_auto_stale` is its own sentence, and `stale` already decides whether *Open Android
      backup settings* appears. So the dot adds no rule, no threshold and no copy — it marks the
      same two states the screen was going to act on, and a **fresh** backup gets none.
      ✅ **The last of the four is closed** (2026-08-12) — the *"not checked" is a stored value* claim,
      and it is **false in the half that matters**, correctly so. *Not checked* is a real default-selected
      **chip**, but no vocabulary carries a `NOT_CHECKED` entry: the column is nullable and `null` *is*
      "not checked" (ADR-0001). `NullableChoiceField` writes `null` and lights on `selected == null`, so
      the UI already says what the column means. No code, no string. **All four decided** — one adopted,
      three that were never new functionality.
- [x] **Rules the new look inherits** — **all seven checked against the code, 2026-08-12**, not asserted.
      `weightChangeLabel(deltaGrams: Int)` takes grams and renders grams with no unit preference in reach;
      the chart's `xs` are `daysBetween(origin, it.recordedAt)`, real elapsed time rather than list index;
      every `AsyncImage` in the app (`PhotoGalleryScreen`, `DocumentsScreen`, `BunnyAvatar`) sets **both**
      `error` and `fallback` to a placeholder; **no `Bitmap.compress` or `FileOutputStream` exists outside
      `media/MediaFiles.kt`** and the backup writer (ADR-0020); every empty state names the absence of
      *records* and what the surface is for — none turns silence into reassurance (ADR-0001); *overdue*
      survives in exactly two places and both are care reminders, `care_due_overdue` and
      `care_notification_overdue` (ADR-0026); and `PolishTranslationTest` is green, which is ADR-0013's
      enforcement rather than a promise about it.
- [x] **Gate: 4f's edge-to-edge matrix re-run in full** — **done 2026-08-12, 244 scenes**: `full` 53×4,
      `mismatch` 2×4, `empty` 6×4, both orientations and both navigation modes. **No confirmed
      edge-to-edge defect in the redesign.** `mismatch` and `empty` are perfectly clean (0 skips, 0
      `drawn`), and `landscape-threebutton` — the **vertical right-edge** navigation bar, a geometry
      nothing in this app had ever been checked against — came back 50/53 with no findings at all.
      Four `drawn`-tier findings, three closed:
      **`document-viewer` reads 13px in three different geometries** (48px bar, 142px bar, and
      landscape), and a residual that does not grow with the bar is the signature of *correct* inset
      handling — the caption moves up by exactly the bar's height and only its padding box crosses the
      line. Invariance across cells proved this where a screenshot could not.
      **`reminders-sheet` tracks the bar exactly** (48 → 142 → 48px): scrolling content passing under
      the bar, which the scene's own note already calls "a list scrolling, not a defect", and its
      `-bottom` companion proves the end clears.
      **`reminders-sheet-bottom` is the M3 scrim** (`View`, "Close sheet", a 16px full-width strip).
      The checker skips a scrim only when the overlap equals the *whole* inset rect; expanded, the
      scrim is reported as a thin strip and trips instead. **A harness false positive** — it will
      recur at every gate until the rule matches the shape rather than the coverage.
      ✅ **`care-reminder-editor` is closed too, by hand, and it is the reason the table grew a scene.**
      Its opening frame puts an `EditText` (the interval field) 27px under the gesture bar in
      **landscape only**, and with no `-bottom` companion nothing in the matrix could say whether the
      *end* of that scroll clears. Driven by hand at the same config: the opening frame reproduces the
      finding exactly (same bounds), and **scrolled to the end it is `drawn=0 touch=0`**. So it is
      `reminders-sheet`'s case — content below the fold in a scrolling form — and not a defect.
      `care-reminder-editor-bottom` is now scene **62**, because *a route whose opening frame can trip
      the check owes a `-bottom`* or every future run re-litigates it from scratch.
      ⚠️ **Six skips, all landscape, all reachability rather than needles** — `observation-entry-ime`
      (one `swipe_up` does not reach the note box on a 1220px-tall viewport) and
      `visit-editor`/`visit-editor-bottom` (*Add a visit* sits below routine care and out of reach).
      Identical in both landscape cells, clean in both portrait ones. **The scene table has never been
      landscape-proof** and could not have been known to be: every prior landscape cell was secretly
      portrait (see the rotation defect above), so these three scenes have never actually run.
      **All three fixed**: `observation-entry-ime` takes `swipe_end` rather than one `swipe_up` — the
      note is the *last* field, so scrolling to the end asks where it is instead of guessing — and both
      `visit-editor` scenes get a `swipe_up` before *Add a visit*, which is the last section of Care &
      Meds. `tap` does scroll when it cannot find its target, but one round is not enough at 1220px
      tall, which is the whole difference between the two orientations.
- [ ] Re-capture and compare, same routes, same locales. **`lint` is 0 errors, 0 warnings** (2026-08-12,
      after fixing two `UnusedAttribute` warnings the theme commit left on the Nunito weight XMLs — the
      attribute is API 28 and the files claimed 26, so `tools:targetApi` records the version gate and
      the comment now says what happens on 26-27).
      ✅ **English after set captured** — 2026-08-13, **62 light + 62 dark** in
      `~/binky-screenshots/phase-7/after/`, against the before set's 61+61. Every before scene has a
      counterpart in both themes; the one extra is `care-reminder-editor-bottom`, added today, which by
      definition has no before. **Compare on structure, density and copy — never on hue**: the before
      set was shot while `dynamicColor = true`, so its colours are that day's wallpaper, and the after
      set is Binky's own scheme. `screenshots.py`'s manifest said the old thing and now says this.
      ⚠️ **The seed's 8:00 PM dose makes evening captures unreliable, and Do Not Disturb is the fix.**
      Two runs were wrecked and a third crippled before the cause was pinned: `reset_to_seeded` recreates
      the Metacam course, whose 20:00 dose is minutes in the past, so a heads-up banner (`importance=4`,
      two actions) posts a minute or so after **every** seed — over Home, exactly where `SELECT_BUNNY`
      taps. The tap opens the course, `AUTO_CANCEL` clears the banner (so a later `dumpsys notification`
      finds nothing and the evidence looks impossible), and **Nav3's `rememberNavBackStack` then restores
      that screen on every relaunch**, so one stolen tap poisons every scene after it. `cmd notification
      set_dnd on` suppresses the banner without touching `POST_NOTIFICATIONS`, so the reminder copy the
      scenes photograph stays truthful. **With DND on, the dark cell ran 62/62 with zero skips** where the
      two runs before it had cascaded. **Turn DND off afterwards** — it is a phone-wide setting.
      ℹ️ **`am start -S -f 0x10008000` in `relaunch()` was aimed at the wrong mechanism.** It is correct
      hardening and stays, but it does not clear a restored Nav3 stack; the matrix's 212 clean scenes did
      not prove it against this case, because that run started after the 20:00 dose had already fired.
      **Scene isolation needs a return-to-Home step, not a restart** — unwritten, and `KEYCODE_BACK` is
      not it: backing past Home exits to the launcher and makes the next scenes worse.
- [x] **The Polish after set is moved to Phase 8** — decided 2026-08-13, and it moves as a *box*, not as a
      capture nobody takes. Attempted that day, and **every scene fails at its first tap**: `--locale pl`
      does switch the app (the dump comes back `14 dni, 3 dni, 7 dni`), but **the scene needles are English
      string literals**, so `tap("Choose which bunny")` matches nothing the moment the app is not English.
      The flag has never been exercised — it sets the locale correctly and then cannot drive a single
      scene, which is why both this file and `phase-8.md` have been treating a `--locale` flag as if it
      were the whole job.
      **What makes it Phase 8's rather than this phase's**: the fix is a *translation* tool, it is that
      phase's copy-length canary, and building it here means building it against one locale and then
      generalising it to nine. This phase's own claim is about looks, and looks are what the English set
      shows; ADR-0013 and `PolishTranslationTest` are what keep the Polish strings level meanwhile, and
      they are green. **What is genuinely deferred with it is copy-length overflow in Polish** — a longer
      string clipping or wrapping wrongly in the redesigned layouts — which is a real risk this phase is
      choosing not to photograph. It is already a gate line in `phase-8.md`, on the same languages that
      make it worst (German compounds, Ukrainian), so it is checked once rather than twice.
      The shape of the fix and its two wrinkles are written up in §7 and in `phase-8.md`.
- [x] **Answered, and it is not the clean "no" that line hoped for** — 2026-08-11, measured across the
      whole sweep (`61abe63^` → `91f524b`): **29 names added** (28 strings and one `plurals`), **9
      removed**, **5 reworded in place**, 648 → 668 entries. So Phase 8 does not start in parallel; it
      starts after this phase, as planned. **Both locales are level** — every added name exists in
      `values-pl`, every removed one is gone from it (ADR-0013), and the only en-only entry is
      `app_name`, `translatable="false"` on purpose. Nearly all of the churn is **naming rather than new
      words**: section headers the old screens did without (`weight_chart_section`,
      `med_editor_section_when`, `observation_tray_section`), placeholders the form idiom needs
      (`med_editor_name_placeholder`), and two generic actions (`action_change`, `action_clear`) taking
      over from per-screen ones. The five rewordings are the same move — `weight_grams_label` folded
      into `weight_grams_help` when the label left its box, and *"Debug builds only."* stripped from
      two help strings once `settings_debug_header` said it once. **Final only if the gate finds
      nothing**: the matrix re-run and the *"not checked"* decision can each still move copy.
- [x] **1.4, not 2.0** — decided 2026-08-08. Nothing breaks in the data, the schema or the backup format,
      so a major bump would claim something untrue. **This is now a commit rule: no `feat!:` anywhere in
      the phase**, because a single one makes `release-please` cut 2.0 regardless of what the docs say.
      A screen replaced wholesale is still `feat:`.

**§4's Play screenshots wait for this phase**, or they are taken twice and the first set is stale before
the testing count clears.

---

## 7 — Phase 8: nine languages 🟢 planned, not started

Design in **[`phase-8.md`](phase-8.md)**. **Runs after Phases 6 and 7** — translating a string set about
to gain a Support screen, and then to have its copy rewritten by a redesign, means translating it twice
in nine languages and having it read twice by nine native speakers.

- [ ] Generalise `PolishTranslationTest` → `TranslationTest`, parameterised over the locale table, with
      **per-language plural categories from CLDR** (not a hardcoded set of four). Do this **first**, on
      `en` + `pl`, so it can fail before there is anything to check.
- [ ] **Make the capture driver locale-aware — needles that resolve through resource names**, carried
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
- [ ] `settings_language_*` → `translatable="false"` — endonyms are locale-invariant, and this removes
      81 duplicated entries at nine languages.
- [ ] Translator brief + per-language banned-word lists (ADR-0026's *missed*/*overdue*, ADR-0001's
      inference-from-silence, `CONTEXT.md`'s vocabulary and its *Avoid* lists).
- [ ] Draft `de es fr it pt-BR cs uk` into **`translations/<locale>/`, not `res/`** — `values-de/`
      existing means every German phone gets it, reviewed or not. `locales_config.xml` is a *picker*
      list, **not** a delivery filter.
- [ ] Promote one language per commit, only after its native read-through: move into `res/`, add the
      `<locale>` line, the `AppLanguage` entry, and the endonym label.
- [ ] `AppLanguageTest` extended to compare resource directories too (`values-pt-rBR` vs `pt-BR` — two
      spellings of one locale in two files).
- [ ] Play listing title + short + full description in all nine. Screenshots may lag (Play falls back);
      they need the locale-aware driver above — **not** a `--locale` flag, which already exists and is
      not the missing half.
- [ ] **Decide the lagging-translation policy** when the test is generalised — strict red build, or a
      dated `translations-pending` allowlist. See phase-8.md's open question.

---

## 8 — Open-source licence attribution 🟠 owed, unscheduled

Raised while grilling Phase 6 and deliberately **not** folded into it. The app ships Room, Compose,
Coil 3, Vico and ML Kit and carries **no attribution of any kind** — no string, no asset, no screen.
Apache-2.0 §4 asks for the licence and NOTICE to travel with the binary.

- [ ] **Decide the mechanism**, which is a dependency question wearing a UI costume: Google's
      `play-services-oss-licenses` plugin (off the shelf, but a **second** Play-services-dependent
      library in a project that quarantines its first one behind an interface — ADR-0009), or a Gradle
      task generating an asset the app renders itself (no dependency, more code, ours to keep working).
      A hand-typed list is neither — it is wrong one dependency bump later and nobody notices.
- [ ] Then build it where the answer says it belongs. Support is the app's only About-shaped screen.

**Before production launch**, which is when the exposure stops being theoretical — not before 1.3.

---

## 9 — A weight *gain* raises nothing 🟠 reported by a tester, needs a decision first

Found by a tester, 2026-08-09, and written down here so it does not get lost. **Not scheduled, and not a
bug to fix blind** — the question it asks is a real one and the answer is an ADR-shaped decision.

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

**Do not fold this into Phase 7.** That phase is *same functionality, new looks*, and a new trigger is
new functionality by definition. Grill the decision first, write it as an ADR, then schedule the build.

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
