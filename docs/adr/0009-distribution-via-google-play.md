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

Three things about that rule are easy to get wrong, and all three change the plan:

- **The internal track runs no clock.** The prerequisite is satisfied by a **closed** test and by nothing
  else, so opening an internal track early buys the pipeline proof and zero calendar progress.
- **The clock cannot be started early anyway.** A closed track means real installs on other people's
  phones, and ADR-0007 attaches the migration obligation the moment a schema version reaches a device
  holding real data — an alpha tester included, as this ADR already says. Opening a closed track before the
  schema settles costs either a hand-written migration on a moving target or a bricked install for every
  tester the day the next version lands, since ADR-0023 makes a release build throw rather than wipe. The
  clock therefore starts with the first build that is fit for someone else to keep.
- **Google assigns nobody.** The twelve are people to recruit — distinct Google accounts that opt in through
  the closed-track link and stay opted in continuously; tester-swap and paid-tester services are a policy
  violation that can end the account. Opting out mid-run resets the streak, so the ask is "keep it installed
  for a fortnight and glance at it", not a daily chore.

What genuinely parallelises, then, is the **recruiting**, which is also the only part with someone else's
lead time in it. That is what starts at the beginning of Phase 3. **1.0 ships to the internal track**;
production access is a later decision costing twelve testers and a fortnight rather than any engineering.

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

A signed release build and a keystore kept out of git are needed at the **start of Phase 3**, not at the end
of the roadmap (ADR-0019) and not at the end of the phase either — the pipeline is proved while the payload
is still Phase 2's feature set, because every failure it can have is cheaper to meet then.

The artifact is an **Android App Bundle**. Play requires one for new apps, so `bundleRelease` is the release
command and `assembleRelease` exists only for automated checks — an `.aab` cannot be `adb install`ed, which
makes the build Play delivers the only one ever installed, and also the only one signed the way a real user
receives it.

**Play App Signing** is likewise mandatory for new apps, and it changes what "the key is permanent" means.
Google holds the permanent *app signing* key; what this project generates is an *upload* key, and an upload
key can be **reset** through the Console if it is lost. Generating it once and backing it up off the machine
is still right, but it is hygiene rather than a one-shot catastrophe. The consequence that does bite is a
different one: the Play build carries Google's signature and a local release build carries the upload key's,
so the two can never replace or sit beside each other under one `applicationId` — which is why ADR-0023's
`applicationIdSuffix` is the only way the phone holds both.
