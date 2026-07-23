# Dose reminders use exact alarms; care reminders use WorkManager

Care reminders (nail trim, vaccination, weigh-in) are due "around" a date, so WorkManager is the right
fit — it survives reboot and Doze with no special permission. Medication doses are different: a course is
typically once or twice daily at set times, and a late or dropped reminder can mean a missed dose during
treatment. Those therefore use `AlarmManager.setExactAndAllowWhileIdle` with a `BOOT_COMPLETED` receiver.

Dose reminders default to on when a course has a schedule, and can be switched off per course — an owner
whose bunny is not in a risky condition, or who dislikes alarms, should not be forced into them.

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
