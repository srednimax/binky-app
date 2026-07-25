# Distribution is Google Play, from the internal testing track onward

The app is free and ad-free, with no server and no running costs. It is distributed through **Google Play**,
starting on the **internal testing track at Phase 3** — the point ADR-0007 identifies as the moment the app
begins holding real data, and the point ADR-0019 makes 1.0. The one-time $25 registration fee is paid then.

Hand-delivered signed APKs were the original plan and are rejected, because they buy the full cost of having
users while providing none of the infrastructure that makes it survivable. ADR-0007 attaches a migration
obligation the moment a schema version reaches a device holding real data — an alpha tester included — and
with sideloading:

- there is **no way to know which version a tester is on**. ADR-0011 forbids the app's own code from making
  network requests, deliberately and correctly, so there is no telemetry and no check-in. The migration
  obligation would be owed to an unknown schema version;
- there is **no update channel**. Each build is a file sent by hand, and a tester three schema versions
  behind is invisible until something breaks;
- there is **no crash reporting** — which matters most for the two features whose failure mode is silence,
  the Doze-dependent scheduling of ADR-0003 and the `BackupAgent` of ADR-0005. Neither announces its own
  failure, so neither would be heard about.

The internal testing track fixes all three **with no application code at all**: automatic updates so testers
converge on one version, visibility of which build each is running, and Play Console crash vitals. Those
vitals are collected by the operating system and the Play Store app, **not** by this app — so ADR-0011's
real property, that *our own code* makes no network requests, survives untouched. That property was never a
claim about what the platform does; it is about what this app does with a pet's medical records.

**Register early, not at release.** Google requires new *personal* developer accounts to run a closed test
meeting a minimum tester count over a minimum period before production access is granted — 12 testers for
14 days at the time of writing. Discovering that at the end turns "ship it" into a multi-week wait with a
recruiting problem attached. ADR-0011 already says to re-read current Play policy before publishing; this is
the same instruction pointed at a different rule, and it needs checking **now**, because it changes *when to
register*, not merely how to ship.

Two implementation choices follow from Play being the target, and both are made now because retrofitting
them later is worse than adopting them early:

- Medication dose reminders use **`SCHEDULE_EXACT_ALARM`** with a prompt sending the user to system
  settings, not `USE_EXACT_ALARM`. The latter is auto-granted but Play permits it only for apps whose core
  purpose is alarms or calendars, which a pet health tracker is not.
- Document scanning sits behind a small interface with a plain-camera fallback. ML Kit's scanner is
  delivered by Google Play services and is absent on devices without them.

F-Droid is out of scope. It forbids Google Play services, which would mean losing the ML Kit scanning UX
entirely; revisit only if open distribution becomes a goal.

## Consequences

A signed release build and a keystore kept out of git are needed at the **end of Phase 3**, not at the end
of the roadmap (ADR-0019). The signing key is then permanent — a Play listing cannot change it — so it is
generated once, backed up off the machine, and never regenerated.
