# Distributed by sideloaded APK now, Google Play eventually

The app is free and ad-free, with no server and no running costs. It will be handed out as a signed APK to
begin with — to the owner and a few alpha testers — with Google Play as the eventual destination. Play
charges a one-time registration fee (currently $25) and applies policy review, neither of which is worth
taking on before the app is finished.

Two implementation choices follow from Play being the eventual target, and both are made now because
retrofitting them later is worse than adopting them early:

- Medication dose reminders use **`SCHEDULE_EXACT_ALARM`** with a prompt sending the user to system
  settings, not `USE_EXACT_ALARM`. The latter is auto-granted but Play permits it only for apps whose core
  purpose is alarms or calendars, which a pet health tracker is not. `SCHEDULE_EXACT_ALARM` works both
  sideloaded and on Play.
- Document scanning sits behind a small interface with a plain-camera fallback. ML Kit's scanner is
  delivered by Google Play services and is absent on devices without them.

F-Droid is out of scope. It forbids Google Play services, which would mean losing the ML Kit scanning UX
entirely; revisit only if open distribution becomes a goal.
