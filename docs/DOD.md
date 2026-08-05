# Definition of done — what is still open

The **live checklist**. `PLAN.md` holds the reasoning and the record; this file holds only what is not
yet ticked, so a session can pick up the work without loading 3 000 lines. Keep it short: when an item
closes, tick it here, write the *result* into `PLAN.md`, and delete the detail from this file.

**Phase 5** (vet, medications, documents, dose reminders — ships as 1.2) — software half **done**,
evidence half open. Status read 2026-08-05 20:30.

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
- [ ] Edge-to-edge matrix re-run (`scripts/edge-to-edge.py`, 59 scenes).
      🔴 **Second blocker:** the Xiaomi is dropping synthetic taps again — `input tap` exits 0 and
      delivers nothing on a screen provably on and focused, while `input keyevent` still works. Test the
      distinction with `KEYCODE_HOME` before planning any tap-driven run. Likely *Debugowanie USB
      (ustawienia zabezpieczeń)* was reset; it needs a Mi account.

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

- [ ] Upload 1.2.0 to the **internal** track, then **closed**. If the count has cleared, production
      becomes available for the first time — whether 1.2 takes it is an ADR-0009 decision made then.
- [ ] **Screenshots for both listings** (EN + PL), owed for the screens 1.1 and 1.2 both added. Needs
      §2's tap blocker fixed.
- [ ] **The field upgrade proof: 1.0.0 → 1.2**, real bunny history intact. The Xiaomi's Play build is on
      **1.0.0**, not the 1.0.1 4h assumed, so the chain crosses *both* hand-written migrations. It cannot
      run locally — the installed build is Play-signed and a local APK is refused on signature mismatch —
      so the update must **arrive from a track**, downstream of the upload above.

---

## 5 — Phase 6: the support contact 🟢 planned, not started

Design and reasoning in **[`phase-6.md`](phase-6.md)** — its own file, so building it does not cost
`PLAN.md`'s 3 000 lines of finished history. Touches no schema, no alarm, no permission and no
dependency, so it **cannot disturb Phase 5's open evidence** — it is the safe thing to build while the
overnight run and Play's count are outstanding.

- [ ] `Support` nav key + `SupportScreen`, reached from More.
- [ ] Two buttons → `ACTION_SENDTO` `mailto:binky.support@gmail.com`, subject **passed as
      `EXTRA_SUBJECT`** (a `#` in the mailto query string is parsed as the fragment and the subject
      arrives empty).
- [ ] Subject = **constant tag + localised description**: `#bug — Bug report — Binky 1.2.0 (211)` /
      `#bug — Zgłoszenie błędu — Binky 1.2.0 (211)`. The tag alone is a Kotlin constant (ADR-0013
      exception, it is a filter token); everything after it is a string resource. One Gmail rule
      (`subject:#bug`) then covers every locale, now and for any language added later.
- [ ] Bug mail prefills the diagnostics block (version, build, Android, device, app locale); feature mail
      does not. Screen states what the block contains before it is tapped.
- [ ] Address rendered as selectable text — the fallback when no mail app exists.
- [ ] Third button: **Rate Binky on Google Play** → `market://details?id=…`, browser URL as fallback.
      **Not** the In-App Review API — Google's own docs say don't put that behind a button (quota can
      silently no-op it) and say to link to the Store instead. Saves a Play Core dependency too.
- [ ] The store URL hardcodes `binky.bunny.and.rabbit.tracker` — **never** `packageName`, which in the
      debug build carries `.debug` and opens *item not found* on the one phone that tests it. Unit test
      asserts the URL does not end in `.debug`.
- [ ] **No donation link** — decided against: Play Payments §3 exempts only tax-exempt donations, §4
      forbids leading users to other payment methods, and StreetComplete was rejected for exactly this.
- [ ] `<queries>` gains `mailto` and `market` entries; no `resolveActivity` pre-check anywhere.
- [ ] Delete the divider and `more_coming_soon` from `MoreScreen.kt` and **both** locales — Support was
      the last "coming soon" in the app.
- [ ] JVM tests on the pure subject/body builders; both locales; `PolishTranslationTest` green.
- [ ] Set `binky.support@gmail.com` as Play's **per-app contact email** in Store settings, so the app,
      the listing and the privacy policy name the same inbox.

---

## 6 — Phase 7: nine languages 🟢 planned, not started

Design in **[`phase-7.md`](phase-7.md)**. **Runs after Phase 6** — translating a string set about to
gain a Support screen means translating it twice, in nine languages.

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
      dated `translations-pending` allowlist. See phase-7.md's open question.

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
