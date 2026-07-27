# First-run setup, and when permissions are asked

First-run setup is three short steps: **add your first bunny** (skippable), **choose a backup scope**
with a plain explanation of the trade-off, and **enable reminders** (skippable). The backup choice is
deliberately here rather than in settings, because a backup buried in settings never gets made. All of it
remains changeable in settings afterwards.

The reminders step is our own screen explaining what reminders are for, with an opt-in button that then
triggers Android's `POST_NOTIFICATIONS` dialog — never the bare system dialog on launch. Android permits
only two denials before the permission is permanently refused with no further dialog, and a prompt shown
before any reminder exists is the most likely to be dismissed. Since medication dose reminders are the one
notification with real consequences (see ADR-0003), a silently denied permission is a genuine failure mode.

The backup step also **asks whether system backup is switched on**, with a deep link into Android's backup
settings. The app cannot detect this for itself (ADR-0005), and first-run setup is the one moment the owner
is already thinking about backup — so asking here is the only alternative to assuming.

Anyone who skips is asked again at point of use, when the first reminder or medication course is created.

## Consequences

Setup is built in three stages, and **each step ships with the feature it is about**. Phase 1 ships the
welcome step alone, because the backup scope and reminders opt-in cannot exist yet and would otherwise be
dead placeholders. Phase 3 adds the backup scope. The **reminders step waits for 1.1**, with the reminders.

Phase 3 looked like the obvious home for all three and is the wrong one: 1.0 has nothing that posts a
notification, so its opt-in would spend one of the two available denials on a screen that cannot
demonstrate anything — which is the exact failure this ADR was written about, arrived at from the other
direction. The point-of-use ask below then becomes the *first* ask rather than the second.

Battery-optimisation exemption is *not* part of onboarding. It is requested when something is first
scheduled, where the reason is visible. Weight units and other preferences are likewise asked in context.
