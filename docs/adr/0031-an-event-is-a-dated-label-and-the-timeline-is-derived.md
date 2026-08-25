# An event is a dated label, and the timeline is derived

An owner asked for this in as many words, on 2026-08-23: *"a calendar or event list — when was the last
vet visit, the last nail trim, or other events the user would like to remember."* Two requests wearing
one sentence, and the difference between them is the whole of this decision.

The first half — *when was the last vet visit, the last nail trim* — asks for **nothing new to be
stored.** The app already knows both. What it does not have is a screen where they sit next to each
other, so answering it is a **read**, not a table.

The second half — *other events the user would like to remember* — genuinely is a new record. The day a
rabbit came home. A neutering next Thursday. An insurance renewal. Nothing in the app can hold any of
these today, and nothing should be stretched to.

**Decision: one small table for the second half, and a pure merge function for the first.**

## An event is a label, a day, and nothing else

`events` carries `{bunnyId, label, occursOn, note?, notifiedAt?, calendarHandedOffAt?}`. Three things it
deliberately does **not** carry:

- **No type enum.** Every other closed vocabulary in this app earned its enum by having a fixed set the
  app reasons about — droppings, mood, care type. An event's kinds are unbounded by definition: the
  request was for *other* events, meaning the ones nobody enumerated. A `MISC` member on an enum is a
  confession that the enum was the wrong shape.
- **No recurrence.** A care reminder is `{label, interval, optional type}` and repetition is its whole
  point ([ADR-0018](0018-care-reminders-are-a-free-label-list.md)). An event that repeated
  would be a second spelling of that fact, and the two would drift the first time an owner edited one
  and not the other. An owner who wants *annual vaccination* already has the screen for it.
- **No time of day.** `occursOn` is a `LocalDate`. Storing an `Instant` would invent a precision nobody
  typed, which is the same argument [ADR-0016](0016-bunny-profile-carries-sex-and-neuter-status.md)
  makes about an approximate birthdate.

**The date is freely in the past or the future**, and it is the only dated write in the app that is. A
weighing, an observation and a vet visit are all records of something that happened, so all three refuse
tomorrow; an event is as often an appointment as a keepsake, and refusing either direction would throw
away half of what was asked for. The editor says so in its own help text, because an owner who has met
the other three has no reason to expect this one to differ.

## The timeline stores nothing

`buildTimeline` takes four lists and returns month sections. Events, vet visits, care completions, and
the next-due dates [ADR-0002](0002-due-doses-are-derived-not-stored.md) already derives. Nothing
is written, nothing is cached, and there is no `timeline` table.

That is the same rule ADR-0002 applies to a due date, applied to a feed — and it buys the same thing.
Back-date a visit, delete a completion, change an interval, archive a bunny: the agenda moves, and
nothing had to be told. A stored feed would need an invalidation path from four different writers, and
the failure mode of getting one wrong is a screen that quietly disagrees with the tabs it summarises.

**Four sources, and only four.** Weighings, observations and doses are absent on purpose: each already
owns a screen with its own history, and a feed that repeated them would be noise rather than a record.
If they are ever wanted they arrive as filter chips over this function, not as a wider default.

### Upcoming above past, and an overdue reminder is still upcoming

The fold is not `on >= today`. A nail trim twenty-one days late is outstanding on every one of those
twenty-one days: it belongs with the things still to do, not filed in history under the month it was
first due. That is the reading the Care screen already takes, and taking a different one here would give
the owner two answers to one question.

The consequence is that `TimelineSection` carries which side it is on rather than deriving it from the
month — an overdue reminder puts a *past* month in the upcoming half, and a screen that recomputed the
side from the month would draw it under the wrong heading. It is also why the screen heads the two
halves out loud: without a heading, scrolling from one "February 2026" into another one is a mystery.

Ties inside a single day are broken by kind and then by id, so a day carrying a visit and two
completions renders identically on every recomposition. Any fixed order would do; being fixed is the
load-bearing part.

**All of this is pure and JVM-tested.** "Upcoming above past", "an overdue reminder is still
outstanding" and "a day holding four kinds sorts the same way twice" are a case table, and a case table
is a test rather than something to squint at on a phone with three rows.

## The timeline is read-only about everything but events

Only event rows can be created, edited or deleted from these screens. A vet visit, a care completion and
a derived due date each tap through to the screen that owns them. A derived list that could also destroy
its sources would be a second place to change every one of them, and the first thing to go wrong would
be a delete the owner could not find again.

## Delivery: the daily sweep, once

An event announces itself **once**, on the day, through the sweep [ADR-0024](0024-care-reminders-are-delivered-by-one-daily-sweep.md)
already runs — never an exact alarm. [ADR-0003](0003-two-scheduling-mechanisms.md)
reserves the exact-alarm path for doses because a late dose has consequences; an anniversary that
arrives at 09:00 instead of midnight has none, and spending the app's second delivery mechanism on one
would be an OS-level cost for nothing.

`notifiedAt` is what makes the second sweep on the same day silent, and it records *when the
notification went out* rather than which date it was for — the difference from a care reminder's
`notifiedForDueOn` being that a care reminder comes due again and again, while an event has exactly one
date in its life. Moving an event's date does **not** re-arm it: an owner fixing a typo in the year is
not asking to be told about it a second time.

**Its own notification channel**, not care's. Care is a job the app is asking for; an event is a day the
owner asked to be reminded of. Android's per-channel switch is the only place that distinction can be
acted on, and an owner who has muted weekly nagging must still hear about next Thursday's neutering.

Archived bunnies are excluded in the derivation rather than by the caller happening to ask only about
active ones — the same rule, in the same place, as care's. A notice about the anniversary of an archived
bunny's adoption is exactly the failure [ADR-0001](0001-health-warnings-never-infer-from-missing-data.md)
names for a trend flag on a memorial page.

## Where it lives

**No sixth bottom tab** ([ADR-0015](0015-navigation-is-bunny-first-with-a-persistent-switcher.md)): a row in More, and a
compact card on Home showing the next thing owed and the last two that happened. The card is a *pointer*
at the timeline rather than a short copy of it, and it is derived from the same `buildTimeline` output
the screen renders — a card that disagreed with the screen it links to would be worse than no card.

[ADR-0014](0014-care-reminders-can-be-handed-to-the-calendar.md)'s calendar hand-off extends to an
event for free: the same `ACTION_INSERT`, with the `RRULE` left out. One-way, permissionless, and
recorded only so the button stops offering itself a second time.
