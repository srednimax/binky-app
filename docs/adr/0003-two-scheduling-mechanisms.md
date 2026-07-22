# Dose reminders use exact alarms; care reminders use WorkManager

Care reminders (nail trim, vaccination, weigh-in) are due "around" a date, so WorkManager is the right
fit — it survives reboot and Doze with no special permission. Medication doses are different: a course is
typically once or twice daily at set times, and a late or dropped reminder can mean a missed dose during
treatment. Those therefore use `AlarmManager.setExactAndAllowWhileIdle` with a `BOOT_COMPLETED` receiver.

Dose reminders default to on when a course has a schedule, and can be switched off per course — an owner
whose rabbit is not in a risky condition, or who dislikes alarms, should not be forced into them.

## Consequences

The exact-alarm permission is `SCHEDULE_EXACT_ALARM`, requested via a prompt into system settings — see
ADR-0009 for why `USE_EXACT_ALARM` is not used.

Two scheduling mechanisms exist deliberately; don't unify them. The exact-alarm path runs only while a
medication course is active, which is expected to be rare.

Reliability on aggressive Android skins (the test device is Xiaomi HyperOS) depends on the app being
exempted from battery optimisation and allowed to autostart. Neither mechanism fires reliably without
that, so the app must detect it and ask, rather than assuming scheduling works.
