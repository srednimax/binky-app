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

**So what is actually open is evidence and Play, not code** — and as of 2026-08-18 that work has a phase
of its own: **Phase 9**, record in [`phase-9.md`](phase-9.md), **ships as 1.7**. Everything through Phase 8
is built, device-proven and tagged — `v1.6.0` was cut 2026-08-18 at schema **7** — but **the tracks are
still on 1.0.0 / 1.3**, so none of it has reached an owner's phone. Closing that gap is what Phase 9 is.

**§10 is Phase 9's index**: it maps each of 9a–9j to the section that holds its boxes, and carries in full
the four that are new (9d, 9e, 9f, 9g). The older sections keep their numbers because they also keep the
device state and the traps, which is the expensive half to rewrite.

---

## 1 — The exact-alarm overnight Doze run ✅ 9a answered 2026-08-19

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

### The dose was moved to 03:00, deliberately

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

### What the morning must show — one post at 03:00, four at 09:00

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

### Pre-flight, in this order

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

### Reading it the next morning — read-only, before the shade is touched

```bash
adb shell dumpsys alarm > alarm.txt        # pending alarms are ABOVE the "Removal history:" section
adb shell cmd appops get binky.bunny.and.rabbit.tracker.debug SCHEDULE_EXACT_ALARM
adb shell dumpsys batterystats --history   # device_idle=full unbroken across the fire time; plug=usb after
adb shell dumpsys notification --noredact  # exactly one post on channel=doses, importance=4
```

- [x] **Dose outcome recorded** against 5a's three written-down outcomes — **outcome 3, "not until
      touched"**, but for a reason none of the three anticipated. Written up below; it does not close 9a.
- [ ] **Phase-4 carry — sweep half ✅ answered 2026-08-20, watch half owed 08-22.** The care sweep
      firing while **still in Doze** is done; the result block below replaces this bullet's guess that
      the 07:28 plug-in spoiled it. What is left is a watch **auto-expiring** — nagging stops that
      morning, the prompt shows the *current* trend, dismissing leaves no row behind. The seeded watch
      runs `startedAt` 2026-08-15 08:30 → `endsAt` **2026-08-22 08:30**, so the sweep that reports it is
      **09:00 on Saturday 2026-08-22**; the "08-21" written here before the 2026-08-19 21:25 re-seed is
      a day early. It needs **no arming and no Doze** — the app installed, the job enqueued, and
      `lastNaggedOn` read before the shade is swiped.

### Result of the 18→19 Aug run: the alarm did not fire, and **Doze is not why** 🔴

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

### 9a passes: with autostart granted the exact alarm fires in deep Doze ✅

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

### Armed for the night of 19→20 August: the matched gap, and the Phase-4 carry

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

### Result of the 19→20 Aug run: confirmed on the matched gap ✅

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

### What 9a cost the app: `Armed` was a promise this phone does not keep ✅ fixed

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

### The 09:00 sweep on 2026-08-19, the same morning: also fine, and the prediction was wrong in the app's favour

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

### The sweep *inside* deep Doze, 2026-08-20 — the Phase-4 carry's first half ✅

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

---

## 2 — The gate items parked behind that run ✅ 9b closed 2026-08-19 — the last bullet is 9c and stays open

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

## 4 — 9h: The Console sitting ✅ production access GRANTED — nothing on Google's side is blocking

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

### What is actually blocking the release now, and it is all in this repo

Google is not in the list. In rough order: **9b** and **9c** (the gate items parked behind 9a, and the
75-scene edge-to-edge re-run), **9d–9g** (close Phase 5, the Pages front door, the fluffle, nine locales
of screenshots), then **9i**, the field upgrade proof 1.0.0 → 1.7 — which is the one that must not be
skipped, because it is the only thing standing between an existing owner and a refusal screen.

Whether 1.7 takes **production** immediately, or goes to internal → closed → production a step at a time,
is an ADR-0009 decision to make at upload — the access being granted does not decide it.

⚠️ **This section said "upload 1.3" until 2026-08-18, and it had been stale for four releases.** The build
that goes up is **1.7** — Phase 9's own, carrying everything from 1.4 through 1.7. Every downstream claim
moves with it, most importantly the upgrade proof. Uploading an intermediate version first spends a
release cycle to prove nothing 1.7 would not.

**The listing and the build go up together.** `store-listing.md`'s copy describes 1.6-scope features;
putting it on a track still serving 1.0.0 is a listing violation, not a rounding error.

### Before the AAB goes up

- [ ] **Release notes ×9**, written at upload time against what *this* build changes. None has ever been
      needed since 1.0.1, so 1.7 owes the first — and a locale with a listing and no note falls back to
      the default language's, which is worse than terse.
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
- [ ] **Artifact checks** ([`RELEASING.md`](RELEASING.md)): `aab-version.py`, `aab-permissions.py`,
      `aab-locale.py` — which now reads `locales_config.xml` and checks **all nine** locales where it
      checked only `pl` until Phase 8 — and `keytool` on the bundle. All three exit non-zero rather than
      leaving you to read; each exists because the corresponding claim was once wrong in a shipped
      artifact while every source-side check was green.

### The sitting itself

- [ ] Upload **1.7** to **internal**, verify, promote to **closed**.
- [ ] Countries/regions, pricing (free), ads declaration (none).
- [ ] Production, **if** the count has cleared — whether 1.7 takes it is an ADR-0009 decision made then.

### 9i — then, and only then, the field upgrade proof

- [ ] **1.0.0 → 1.7**, real bunny history intact. The Xiaomi's Play build is on **1.0.0**, not the 1.0.1
      4h assumed, so the chain crosses **all three** hand-written migrations — `MIGRATION_4_5`,
      `MIGRATION_5_6`, `MIGRATION_6_7` — and the launch gate ADR-0023's Phase 7.5 amendment rewrote. It
      cannot run locally: the installed build is Play-signed and a local APK is refused on signature
      mismatch, so the update must **arrive from a track**, downstream of the upload above. This is the
      standing gate's item 5, on the release it matters most for.

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

- [x] **9j — replied 2026-08-21.** Owed since 08-09, and it was never the feature. Their *"5 kg plus"*
      was a **number, not a change**: a Flemish Giant is legitimately 6–10 kg, so any absolute weight is
      wrong for some breed, and Binky will never call a weight too high — only say that it moved, by how
      much, since a date. ADR-0028 shipped the gain signal they were actually asking for; the reply is
      the half that explains why the app will not do the thing they literally asked for, which is better
      said than left to be discovered.

---

## 10 — Phase 9's index, and the four items that are new

[`phase-9.md`](phase-9.md) is the reasoning; these are the boxes. **Ships as 1.7, schema stays 7** — no
entity changes, so the standing gate at the top of this file does not fire in this phase.

| | What | Boxes |
| --- | --- | --- |
| **9a** | The overnight Doze run ✅ answered 2026-08-19 — autostart is the lever, and the delivery state was fixed to say so. **§1's last box is still open**: the Phase-4 carry's watch half, owed at the 09:00 sweep on 2026-08-22 | §1 |
| **9b** | The six gate items parked behind it ✅ **closed 2026-08-19** — it found that the boot rebuild waits for the first unlock, and that a lowered channel was being reported as armed; the second is fixed in the same PR | §2 |
| **9c** | The 75-scene edge-to-edge re-run ✅ **closed 2026-08-21** — 300 cells, 0 errors; it found two driver bugs and that its own warnings had gone stale | §2, last bullet |
| **9d** | Close Phase 5 | below |
| **9e** | The Pages front door ✅ **closed 2026-08-19** — `docs/index.md` is the root, and `_config.yml`'s "copied verbatim" comment was wrong | below |
| **9f** | Seeing the whole fluffle ✅ **closed 2026-08-20** — the sheet is built, tested and driven; the archived route and the landscape half-height state were both found on the phone | below |
| **9g** | Nine locales of screenshots ✅ **closed 2026-08-21** — 72 padded PNGs, and it found a doubled full stop in Ukrainian that no test could see | below |
| **9h** | The Console sitting ✅ production access granted 2026-08-19 — the release is repo-side only now | §4 |
| **9i** | The field upgrade proof 1.0.0 → 1.7 | §4 |
| **9j** | The tester's reply ✅ **replied 2026-08-21** | §9 |

**Three edges must not be reordered**, and everything else is free: **9a before 9b and 9c**, because both
disturb the armed course and the run costs a night; **9f before 9g**, because 9g photographs a screen 9f
changes; **9g and the listing copy before 9h before 9i**, because the upgrade proof needs an update that
arrives from a track.

### 9d — Close Phase 5

- [ ] Write 9a's and 9b's results into [`PLAN.md`](PLAN.md)'s 5a / 5i / 5j entries and **tick Phase 5**.
      It has been the one unticked box since 2026-08-05 while four later phases closed around it.
      ⚠️ **Waits on §1's watch half (08-22).** Phase 4 closed on the build with its delivery evidence
      carried into Phase 5, and that carry is the last thing §1 is still holding — so the tick lands
      after Saturday's reading unless the carry is deliberately tracked outside Phase 5.

### 9e — The front door ✅ closed 2026-08-19

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

### 9f — Seeing the whole fluffle ✅ closed 2026-08-20

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

### 9g — Nine locales of screenshots ✅ closed 2026-08-21

- [x] **Nine locales × four scenes × two themes**, `home` / `weight` / `observations` / `backup`,
      through `screenshots.py --locale <tag>` at ~7 min a locale. 72 native captures at 1220×2712 and
      72 padded at exactly 1526×2713, in
      `~/binky-screenshots/phase-9/listing/<tag>/{light,dark}/` and `…/listing/_play/{light,dark}/<tag>/`.
      Every locale reached all four scenes; no skips, no errors. This improves the listing rather than
      unblocking it — Play falls back to the default listing's set — which is why it waited until the
      tracks could carry the build it describes.

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
*and* **Phase 9** in the checklist at the top, and empty this file down to the standing schema gate.

**Both at once, because they are the same boxes.** Phase 5's evidence half is what Phase 9 §1 and §2 are;
9d is the tick. At that point every phase in the project is closed and 1.7 is on Play in nine languages —
the first time both sentences are true at once — and what is open stops being a release checklist and
starts being whatever owners report.
