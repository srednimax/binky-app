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
      Still undrawn: Settings, Support, Documents, Photos, Setup, Watch expiry, Schema mismatch,
      Reminders opt-in. The calendar in `7a`/`7b` is a new route and **out of scope** for this phase.
- [x] **Theme commit first** — 2026-08-08. `Color.kt` generated from the seeds (both schemes in full, 22
      contrast checks green), `Type.kt`, `Spacing.kt`, `dynamicColor` off. Nunito is a new bundled asset,
      not a dependency. Two silent traps written up in `phase-7.md`'s order-of-work step 4.
- [ ] **The Material You toggle** — the half of ADR-0027 that did not ship with the theme commit. Until it
      exists, dynamic colour is not "off by default", it is unavailable. A key in `AppPreferences.kt`, a
      Settings row, and two strings in **both** locales (ADR-0013).
- [ ] Then screen by screen, tab by tab, starting with the two that were mocked.
- [ ] **Rules the new look inherits** — weight changes always in grams; the chart plots real timestamps,
      not index; missing media is a placeholder, never a crash; image writes go through the media helper
      (ADR-0020); no empty state infers a problem from silence (ADR-0001); no *missed*/*overdue* outside
      care reminders (ADR-0026); every new string is a resource in **both** locales (ADR-0013).
- [ ] **Gate: 4f's edge-to-edge matrix re-run in full**, both orientations, both navigation modes. A
      visual overhaul is exactly what that matrix exists to catch.
- [ ] Re-capture and compare, same routes, same locales. `lint` still 0 errors, 0 warnings.
- [ ] **Answer whether any string changed** — a clean "no" would let Phase 8 start in parallel.
- [ ] **Decide 1.4 vs 2.0 at the release**, not now: nothing breaks in the data, the schema or the backup
      format, so 1.4 is the honest reading — but a single `feat!:` is what `release-please` reads as 2.0,
      and an overhaul is the one moment a major bump tells a user something true.

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
      `scripts/edge-to-edge.py` needs a `--locale` flag first.
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
