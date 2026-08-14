# Definition of done — what is still open

The **live checklist**. `PLAN.md` holds the reasoning and the record; this file holds only what is not
yet ticked, so a session can pick up the work without loading 3 000 lines. Keep it short: when an item
closes, tick it here, write the *result* into `PLAN.md`, and delete the detail from this file.

**Phase 5** (vet, medications, documents, dose reminders — ships as 1.2) — software half **done**,
evidence half open. Status read 2026-08-05 20:30. **Phase 6** (the support contact — ships as 1.3) is
built, device-tested and documented as of 2026-08-06; only §5's two hand items are left, and 1.3.0 is
waiting on a release PR. **Phase 7** (the redesign — ships as 1.4) **closed 2026-08-13**; §6 is its
closing note, and the one thing it carried out is §7's first box.

**Phase 7.5** (the interlude — ships as **1.5**, opened 2026-08-14) collects what was scattered here and
**owns §3, §5, §8, §9 and §7's capture-driver box**, plus three owner-facing findings from 2026-08-14 that
live only in §6.5. **It bumps the schema to 7** — added 2026-08-14 with the droppings work, so it is no
longer the migration-free interlude it was drafted as.
Its reasoning is [`phase-7.5.md`](phase-7.5.md); §6.5
below is its summary. **Phase 8 retargets to 1.6** — two phases cannot both claim 1.5, and `release-please`
answers to commit subjects rather than to this file.

**So what is actually open is evidence, then one short phase of code**: §1's overnight run and the gate
items behind it, Play's own count (§4), and Phase 7.5's five items — and then Phase 8.

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

## 3 — The document downsample spec, still uncalibrated 🟠 ⤷ Phase 7.5

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

## 5 — Phase 6: the support contact 🟡 two hand items left ⤷ Phase 7.5

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
2026-08-14, so nothing here is waiting on a decision. What is left of step 1 is the two hand items.

- [ ] **§5's two hand items first** — Play's per-app contact email, and a delivered support mail read.
      Oldest open boxes in the project, blocked by nothing, an hour between them.
- [ ] **§7's capture-driver box**, moved here. ✅ **The code is done and device-proven (2026-08-14)** —
      `return_to_home` + DND as setup/teardown, seed variants through a debug-only broadcast receiver
      (`crowded` is the first), and needles resolved through the string resources before the first tap.
      The default seed is **not** changed: 61 scenes rest on it. **Two long runs are still owed** — a
      full **English matrix with the 20:00 dose live**, and a **Polish run reaching every scene**, which
      is the Polish after set and unblocks §4's Polish listing screenshots.
      ℹ️ **The relaunch was already landing on Home** — measured from a detail route and from a non-Home
      tab — so the isolation step is a *check*, and the 2026-08-12 cell is better explained by the dose
      banner re-posting on every relaunch. See phase-7.5.md §4 for the rest of what the build found.
      ⚠️ **`weight-entry-ime` has never had a keyboard in it, Phase 7's after set included** (found
      2026-08-14). The grams field has no label and no placeholder, so the needle *"Weight in grams"*
      matched the **help line underneath it** — a plain `Text` that focuses nothing. Third IME scene
      caught by that trap and the only silent one. Fixed by tapping the *n*-th `EditText` instead;
      **assume the existing `weight-entry-ime` evidence is wrong** rather than re-reading it.
- [ ] **§3's downsample answer**, taken while the phone is already in hand.
- [ ] **§9's gain signal**, per **[ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md)**
      — grilled and written 2026-08-14; merge it before any trend code. Same flag, anchored on the weighing
      nearest **six months** back (4–8 month window), at **+10 %** — one body-condition step on the
      PFMA/RWAF scale. Silent under 12 months; a null `birthDate` fires **and asks the age**, because
      reading an absent field as adulthood is the move ADR-0001 bans. **Loss takes precedence** when both
      hold, which is what keeps the schema at **6** — one watermark, discarded on a direction change.
      Three new strings. Two limitations pinned by test, not engineered around.
- [ ] **Droppings are several things at once, and worth a photo** 🔴 **this is the schema bump** — reported
      2026-08-14. `droppingsForm` is one nullable column, so a tray holding round *and* soft pellets forces
      the owner to pick one and file the rest as prose, on the field whose own doc says a countable form
      beats prose. **Decided in ADR-0029: appearance and size go multi-valued, amount stays
      single** (`FEW` *and* `MANY` is a contradiction about one tray). **Both hang off `observationId`** —
      there is no group *table*, and ADR-0008 forbids stamping a `groupId` on a solo observation, so the
      join table is keyed on the row and the photo is a path column on it, riding the `TrayFacts`
      propagation that already exists. The duplicated path buys one new rule: **the file goes only when no
      other row references it**, or deleting one bonded bunny takes the survivor's photo.
      ⚠️ **The vocabulary is also incomplete, and `STRUNG_TOGETHER` is a trap** (checked 2026-08-14). It
      means strung *with fur*; **mucus presents identically** — pale goop strung between or enclosing the
      pellets — so an owner seeing gut irritation would reasonably record moulting. And **blood in droppings
      is recordable nowhere**, while `symptom_blood_in_urine` is seeded — the app has a field for the false
      alarm (red urine is usually porphyrins) and none for the always-serious one. **Decided: close the gaps
      with values, add no field.** The field gains `MUCUS`, `BLOOD`, `VERY_DARK` (melena), `DOUBLED`
      (fused = slowing motility, not general misshapenness) and `DRY` (dehydration); `Cecotropes` gains
      `EXCESS`. **`DroppingsForm` is renamed with them** — five of the six are contents, colour or
      moisture, not shapes; `DroppingsAppearance` is **confirmed** in ADR-0029, and the rename is free
      because only value names are stored — the column itself disappears in the rebuild. **The existing
      label is reworded to *"Strung together with fur"***, because a new `MUCUS` value closes nothing while
      the screen still offers one plausible chip for pale goop. *Pale* and *greenish* stay out on **triage** — they are the weakest
      signals in the set — not on form length, which cannot tell the values kept from the values dropped.
      **Blood cannot be a symptom**: symptoms are individual, a shared tray is not attributable (ADR-0008).
      Values are stored **by name**, so additions cannot rewrite history. **No per-value urgency copy** —
      the app records `BLOOD` and says nothing about it (ADR-0026).
      Its `MediaKind` is **new — `Observation`, writing to `observations/`**, record-grade so `Records`,
      `Everything` and the cloud queue carry it; only its downsample numbers wait for §3's phone judgement.
      Cost: two join tables + media link, one `MIGRATION_6_7` that **rebuilds `observations`** (no
      `DROP COLUMN` at `minSdk` 26, and the drop cascades into `observation_symptoms` — stage the links and
      put them back), **schema 7**, a fixture and the `connectedAndroidTest` run nothing else here owes. Accepted knowingly 2026-08-14. **If the phase runs
      long the photo is the cut** — it does not cut the migration, and the multiselect is not cuttable.
- [ ] **The healthy day moves behind the `+`** — reported 2026-08-14. The FAB (`Navigation.kt:365`) opens
      the *full* form; *Log a healthy day* is a separate action inside the Timeline, so the discoverable
      entry point is the long one and the shortcut that settles the subject's watch is the hidden one.
      **Decided: the `+` opens a bottom sheet with both paths and the Timeline button goes.** A sheet and
      not a menu, because `healthy_day_help` has to travel with the label — one tap commits four facts on
      the owner's behalf (ADR-0001). No schema and **no new strings**: `healthy_day_action`,
      `healthy_day_help` and `observation_add_title` are reused verbatim, nicer labels deliberately not.
- [ ] **The housemates line at five bunnies** — reported 2026-08-14, and a **defect**, not a feature. The
      grammar is right (`joinNames` builds from the right through string resources); the layout is not —
      `HomeScreen.kt:210`/`:443` and `ArchivedBunniesScreen.kt:170` draw it as a plain `Text` with **no
      `maxLines` and no `overflow`**, so five names, or two long ones, grow the card by two lines. Never
      seen, because the seed has two short-named bunnies — the **second customer for seed variants**. Cap
      the names *and* bound the line: two named then *"& N others"* **from four housemates up** (never
      *"& 1 other"*), archived folded first, one `plurals` entry — in **`housematesLabel`, never
      `joinNames`**, which the healthy-day receipt shares and must not truncate (ADR-0008) — plus
      `maxLines = 2, overflow = Ellipsis` at the three sites, because a count cap cannot fix two long names.
- [ ] **§8's licence attribution** last: **`app.cash.licensee`** at build time, rendered by the app's own
      Compose screen. No ADR owed — it adds no runtime dependency, so ADR-0009 is untouched, and it emits
      structured data plus a build failure when a licence changes, which is §8's own stated fear. The Play
      plugin was rejected for a second Play-services library *and* a stock Activity in a redesigned app.

**Commit rule carries over from Phase 7: `feat:`/`fix:`, never `feat!:`.**

---

## 7 — Phase 8: nine languages 🟢 planned, not started

Design in **[`phase-8.md`](phase-8.md)**. **Runs after Phases 6 and 7** — translating a string set about
to gain a Support screen, and then to have its copy rewritten by a redesign, means translating it twice
in nine languages and having it read twice by nine native speakers.

- [ ] Generalise `PolishTranslationTest` → `TranslationTest`, parameterised over the locale table, with
      **per-language plural categories from CLDR** (not a hardcoded set of four). Do this **first**, on
      `en` + `pl`, so it can fail before there is anything to check.
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

## 8 — Open-source licence attribution 🟠 ⤷ Phase 7.5

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

## 9 — A weight *gain* raises nothing 🟠 ⤷ Phase 7.5, decision first

Found by a tester, 2026-08-09, and written down here so it does not get lost. **Decided 2026-08-14 in
[ADR-0028](adr/0028-a-weight-gain-is-observed-against-a-six-month-anchor.md)** and scheduled into Phase 7.5;
everything below is the question as it stood, kept because the ADR answers it point by point.

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
