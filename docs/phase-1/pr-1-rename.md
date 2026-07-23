# PR 1 — Rename and vocabulary

**Branch:** `rename-to-bunny` · **Depends on:** nothing, branches from `main` ·
**Decisions and reasoning:** [`README.md`](README.md)

Docs and mechanical edits only — ~29 files, ~90 occurrences, no logic. Reviewable in one pass.

---

## Package and build identity

Nothing is released, so `applicationId` is free to change now and never again.

- `git mv` `app/src/{main,test,androidTest}/java/app/rabbit/tracker/` → `app/bunny/tracker/`, so history
  follows the move.
- `app/build.gradle.kts:8,11` — `namespace` and `applicationId` → `app.bunny.tracker`.
- `settings.gradle.kts:32` — `rootProject.name` → `"bunny-app"`.
- Every `package` / `import` line across the 9 Kotlin files.

## Names and resources

- `RabbitTrackerTheme` → `BunnyTrackerTheme` — `theme/Theme.kt:33`, `MainActivity.kt:11,19`,
  `MainScreen.kt:13,48,54`.
- `res/values/themes.xml` — `Theme.RabbitTracker` → `Theme.BunnyTracker`, and the reference in
  `AndroidManifest.xml:11`.
- `res/values/strings.xml` — `app_name` → `Bunny Tracker`.

## Docs

`CLAUDE.md`, `CONTEXT.md`, `README.md`, `docs/PLAN.md`, and all 13 ADRs.

Substitute plurals **first** (`rabbits→bunnies`, `Rabbits→Bunnies`) or you get "bunnys". Then read the
prose back rather than trusting the substitution — several lines are biologically specific ("a soft
nutrient-rich dropping a bunny normally eats") and must still be true sentences afterwards.

- `git mv docs/adr/0008-observations-can-cover-several-rabbits.md` → `…-several-bunnies.md`; fix links
  to it.
- `CLAUDE.md` media house rule: the relative-path example becomes `avatars/…`, `photos/…`,
  `documents/…`. The single-folder example is now wrong — see the media-layout decision in the README.

## `CONTEXT.md` vocabulary changes

The glossary's whole job is to fix one word per concept, and four concepts moved.

- **Bunny** — new entry. `_Avoid_: rabbit`. Without it, the next session reintroduces "rabbit" in good
  faith and the vocabulary drifts back.
- **Photo** — drop the "including the bunny's avatar" clause. ADR-0005 backs avatars up as Essential and
  the gallery only as Everything, so they are genuinely different things and the glossary was wrong.
- **Avatar** — new entry: the small picture identifying a bunny across the app, kept separately from the
  gallery because it is part of the Essential backup.
- **Fluffle** — new entry: the set of bunnies that live **Together**, sharing a space and litter tray.
  `_Avoid_: group, warren, household, cage, hutch`. Cross-reference Together, and note that "group" is
  reserved for the shared-observation link. The on-screen label is "Lives with" — Fluffle is the code and
  glossary word.

## ADR-0014 — care reminders can be handed to the calendar

New `docs/adr/0014-care-reminders-can-be-handed-to-the-calendar.md`, in house style: a title stating the
decision, a short body, a `## Consequences` section. **Docs only — the button is built in Phase 4.**

**The argument.** A yearly vaccination reminder is where in-app scheduling is weakest. ADR-0003 already
concedes that neither mechanism fires reliably on aggressive skins without a battery-optimisation
exemption, and a WorkManager job is being asked to survive a year of reboots, an OS upgrade and possibly a
new phone. The owner's calendar is built for that horizon and syncs to their account — which makes it the
only thing in this app besides a manual export (ADR-0005) that survives losing the phone.

**The decision.**

- Any care reminder offers **Add to calendar**: `Intent.ACTION_INSERT` on
  `CalendarContract.Events.CONTENT_URI` with title, all-day begin time, and an `RRULE` matching the
  reminder's repeat (`FREQ=YEARLY` for vaccination). **No calendar permission is requested** — the owner's
  calendar app opens prefilled and they save it themselves.
- **Additive, not a replacement.** In-app reminders stay primary per ADR-0003; the calendar is offered per
  reminder, never automatic.
- **Care reminders only.** Dose reminders keep exact alarms — ADR-0003 gives them that path because a late
  dose has consequences, and a calendar entry is not an alarm.

**Consequences to state plainly**, because this is the part that bites:

- The app does **not** own the event. No event id is stored, so editing or deleting the reminder in-app
  leaves the calendar entry untouched, and completing a reminder ticks nothing off the calendar. The
  button must read as a one-way hand-off.
- Tapping it twice creates two events. Record that a hand-off happened, so the button can say "Added to
  calendar" instead of silently duplicating.
- `ACTION_INSERT` needs a calendar app installed. Guard the `startActivity` and fail with a message, not a
  crash.

Add to `docs/PLAN.md` Phase 4:

> - Care reminders optionally hand off to the owner's calendar, one-way, no permission (ADR-0014).
>
> **Gate** (append): tapping *Add to calendar* on an annual reminder opens the calendar app with the date
> and yearly repeat already filled in.

Also add a note to ADR-0006 that first-run setup ships its welcome step in Phase 1 and reaches all three
steps in Phase 3 — see the onboarding decision in the README.

---

## Verify

```bash
./gradlew assembleDebug test lint
./gradlew installDebug
```

Confirm the launcher reads **Bunny Tracker**. The `applicationId` changed, so Android treats this as a
*different* app — `adb uninstall app.rabbit.tracker` or two icons sit side by side.

## Then, by hand

Renaming the working directory has to happen outside a running session:

```bash
gh repo rename bunny-app
cd ~/repos && mv rabbit-app bunny-app && cd bunny-app && git remote -v   # check the URL updated
```
