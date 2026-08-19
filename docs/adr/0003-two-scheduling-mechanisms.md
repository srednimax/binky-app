# Dose reminders use exact alarms; care reminders use WorkManager

Care reminders (nail trim, vaccination, weigh-in) are due "around" a date, so WorkManager is the right
fit — it survives reboot and Doze with no special permission. Medication doses are different: a course is
typically once or twice daily at set times, and a late or dropped reminder can mean a missed dose during
treatment. Those therefore use `AlarmManager.setExactAndAllowWhileIdle` with a `BOOT_COMPLETED` receiver.

Dose reminders default to on when a course has a schedule, and can be switched off per course — an owner
whose bunny is not in a risky condition, or who dislikes alarms, should not be forced into them.

## Dose timing is wall-clock, not a fixed interval

A course's clock times are **wall-clock**: an "08:00 dose" is 08:00 local time, because the owner gives the
pill relative to their own day (breakfast, bedtime), not to an abstract 24-hour interval. The next dose's
absolute trigger is therefore resolved **fresh each time** — the `LocalTime` on the next date, in the
device's *current* zone and DST rules, converted to an `Instant` — so DST changes and travel are handled by
construction rather than by arithmetic on a stored epoch value. Using `java.time` `ZonedDateTime` default
resolution, a dose whose wall-clock time falls in a spring-forward *gap* fires **once**, shifted forward to
the first valid instant; one in a fall-back *overlap* fires **once**, at the earlier offset. Never zero
times, never twice.

A pre-computed absolute alarm points at the wrong wall-clock the moment the clock or zone changes, so
`ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED` receivers recompute pending dose alarms, alongside the
`BOOT_COMPLETED` receiver. Care reminders are day-granularity and all-day, so this applies to doses only.

## Consequences

The exact-alarm permission is `SCHEDULE_EXACT_ALARM`, requested via a prompt into system settings — see
ADR-0009 for why `USE_EXACT_ALARM` is not used.

Two scheduling mechanisms exist deliberately; don't unify them. The exact-alarm path runs only while a
medication course is active, which is expected to be rare.

Reliability on aggressive Android skins (the test device is Xiaomi HyperOS) depends on the app being
exempted from battery optimisation and allowed to autostart. Neither mechanism fires reliably without
that, so the app must detect it and ask, rather than assuming scheduling works.

A dose reminder that *silently* fails to fire is worse than none: the owner stops watching for the dose
themselves, trusting a prompt that never comes, so unreliability doesn't degrade the feature — it inverts
it into a hazard. *Asking* for the exemption is therefore not enough:

- **Hard reliability gate.** Before dose reminders are treated as trustworthy, a dose alarm must be proven
  to fire after the phone has sat idle in Doze **overnight** — screen off, app unopened for 12h+ — on the
  real Xiaomi. The two-minute happy path does not clear this gate.
- **Honest state, not a false alarm.** While battery-optimisation exemption and autostart are not
  confirmed, a dose reminder shows as **best-effort** ("may not fire reliably on this phone until you
  enable X"), never as an armed alarm. If the overnight gate cannot be met, dose reminders ship explicitly
  as best-effort — not as a safety-critical alarm the app cannot stand behind on its own hardware.

## Amendment (Phase 4a): autostart cannot gate the honest state, because nothing can read it

The condition above names two things — battery-optimisation exemption **and autostart**. Only the first has
an API. `PowerManager.isIgnoringBatteryOptimizations` answers it without any permission; HyperOS autostart
has no public state, and launching its settings screen returns no result. So "autostart confirmed" is
permanently false on the one device this project tests on, and a strict reading of the sentence means every
reminder in the app carries a best-effort hedge forever, no matter what the owner does.

That inverts the rule it comes from. A permanent hedge is wallpaper in exactly the way a permanent nag is:
it stops carrying information, and by the time it wraps dose reminders it is supposed to mean something.

So, as built:

- **Armed depends on the detectable exemption.** Autostart is offered once, alongside the exemption ask,
  where the Xiaomi intent resolves — and the app then claims nothing about it in either direction.
- **The owner is not asked to confirm autostart.** A checkbox would have the app repeating the owner's guess
  back to them as its own assurance, which is this ADR's central hazard sourced from a new place.
- **The evidence is the overnight-Doze gate**, run on the real device (PLAN 4g). Evidence from the hardware
  beats an unreadable flag.

Phase 4 also widens the state from two to three, because a *denied notification permission* or a *muted
channel* is not best-effort — it is certain, and it is detectable. Reminders present as **blocked**,
**best-effort** or **armed**, resolved by one pure function that Phase 5 inherits for doses.

## Amendment (Phase 9, 9a): the autostart list gates the honest state after all — and this time it was measured

4a's amendment above reasoned from an absence: autostart has no readable state, therefore conditioning
*armed* on it makes *armed* unreachable forever, therefore leave it out. That is sound as far as it goes,
and it went one step too far — it treated an unreadable fact as an unimportant one. 9a measured the fact.

Two runs on the test device, one variable between them, everything else identical (unplugged, stationary,
`SCHEDULE_EXACT_ALARM` granted, alarm verified `window=0` and `whenElapsed == maxWhenElapsed` before the
run):

- **Autostart denied.** `device_idle=full` unbroken from 01:07:08 to 03:07:09, straight across a 03:00
  dose. **No alarm fired at all.** The dose was delivered **3h50m47s late, at 06:50:47** — the instant the
  phone was plugged in. The logs name the mechanism: `GreezeManager: THAW uid = 10507`, then
  `Aurogon: sendPendingAlarm uid = 10507` half a second later. HyperOS had frozen the process and its
  power framework held the pending alarm until something thawed it.
- **Autostart granted.** Same course, same phone. Fired at **10:00:00.779**, fifty minutes inside an
  unbroken 59m51s stretch of `device_idle=full`, on battery. No `GreezeManager` or `Aurogon` line for the
  app anywhere in the capture — it was never frozen.

So the freezer is not Doze, and it is not something the app can out-argue: neither `SCHEDULE_EXACT_ALARM`,
nor `window=0`, nor the alarm's own `temporaryAppAllowlistReasonCode=302` outranks it. It holds the
*process*, so no mechanism this app schedules sits above it.

That falsifies the claim `ReminderDelivery.Armed` was making. As built since 4a, an owner on a Xiaomi who
granted the battery exemption was told "this phone is set up to let them through" by an app that had no
basis for saying so — and this ADR's opening argument is that a dose reminder which silently fails is worse
than none, because it inverts the feature into a hazard. So, as built now:

- **Where an OEM autostart list exists, `Armed` is out of reach**, and `hasAutostartSettings()` is the
  input that puts it there. That resolves to "this phone keeps such a list", never "the app is off it" —
  which is the weaker claim, and the only one available. Being hedged at while already on the list is the
  cost, and it is the right way round: over-hedging annoys, over-promising loses a dose.
- **The ceiling is best-effort with the reason named and the way in attached.** The autostart sentence is
  the third variant of that state, after the exemption and the exact-alarm ones, and it is reached only
  once both of those are satisfied — so nobody is shown two background-limit lines at once, and the line
  that does show is the one thing left to do.
- **The copy says hours and says when the reminder turns up**, because that is what was observed and
  because "may be delayed" is the phrasing an owner reads past. This is the one delivery line permitted to
  be that concrete; it earned it.
- **4a's other two rules stand unchanged.** The owner is still never asked to confirm autostart, and the
  app still claims nothing about the list in either direction. What changed is that *not knowing* now
  costs the promise rather than being waved through.
- **The wallpaper objection is answered by scope, not by volume.** The hedge appears only on phones that
  actually keep such a list, and it carries a fix; a stock phone still reaches *armed* on the exemption
  alone. `oemAutostartUnreadable` defaults to false for exactly that reason.

The evidence rule from 4a is unchanged and is what settled this: evidence from the hardware beats an
unreadable flag — including when it beats the conclusion the last amendment drew from one.
