# Care reminders are delivered by one daily sweep, not by per-reminder scheduled work

The obvious way to build care reminders is one `OneTimeWorkRequest` per reminder, enqueued at its derived
due date and rebuilt whenever that date moves. It is what "the OS schedule is a cache of the derived value"
suggests on first reading, and it is wrong here for two reasons.

**It cannot keep "notifies once".** A due reminder notifies once and never again — a notification that
re-fires daily for a nail trim is the wallpaper failure ADR-0001 rejects for the watch, and the subject
does not change the argument. With per-reminder work, "once" is implicit in the request being one-shot, but
the schedule is also rebuilt from scratch on boot. A reminder that came due last week and already notified
is re-enqueued with a delay in the past and fires again. Three reboots, three notifications, for a task the
owner already saw.

**It is N independent bets against the same background killer.** Reliability on aggressive skins is the
open question this whole area lives under (ADR-0003), and a missed care reminder is missed permanently by
design. Per-reminder work means every reminder is its own wager, an annual vaccination is a 365-day pending
request asked to survive a year of reboots and an OS upgrade, and the evidence gathered for one says
nothing about the others.

**So there is one worker.** A single sweep, enqueued as unique work under one name, which on each run
derives what is due for every active bunny, posts what needs posting, does the watch nag, checks the export
reminder's interval, and enqueues tomorrow's run before returning. It stays enqueued permanently and
no-ops when there is nothing to do.

## Consequences

**"Notifies once" becomes a recorded fact.** `CareReminderEntity.notifiedForDueOn` holds the due date a
notification was posted *for*, and the sweep notifies when the derived due date differs from it. Storing
*when* it last notified would have to be cleared on every path that moves a due date — a completion, an
edited or deleted care event, an edited interval, and for a weigh-in a back-dated weight that writes
nothing to the reminder at all — and a missed clear fails silently in the direction where the owner never
hears about a reminder that came due. Comparing against derived truth needs no clearing anywhere.

**There is no per-bunny scheduled work**, so there is none to cancel and none to orphan. Archiving a bunny
does not cancel anything; the sweep skips archived bunnies when it derives what is due, which is a fact
about the derivation and asserted as one. Deleting a bunny cannot leave scheduled work behind, because
there was never any to leave. The invariant is checkable in one line: **exactly one enqueued work item
exists at any time.**

**The boot receiver has exactly one job** — re-enqueue the sweep — and reads no persisted schedule, because
there is none.

**Notifications arrive at one time of day**, the app-wide reminder time. A reminder created in the
afternoon and due today waits for the next sweep. That is the same constraint the single time-of-day
preference already imposes, not a new one, and per-reminder clock times would promise a precision ADR-0003
deliberately reserves for doses.

**A late sweep is a caught-up sweep.** One that misses its window fires late and posts everything overdue in
one pass, where N one-shots would each miss independently.

**The overnight-Doze gate has one target.** If the sweep fires after 12 h idle on the Xiaomi, every reminder
in the app fires; if it does not, one finding covers the feature. That is the whole reliability question,
asked once.

**Phase 5's dose alarms do not follow this.** ADR-0003 gives doses `setExactAndAllowWhileIdle` because a
late or dropped dose reminder during treatment has consequences a nail trim does not, and a sweep is the
opposite of exact. The two halves of ADR-0003's split therefore diverge further than the ADR describes:
care is one derived daily pass, doses are individually armed alarms. Do not unify them, and do not
"simplify" doses onto the sweep later.

**The debug "remind me in two minutes" action keeps its own one-shot path.** It exists to prove channels,
permission and delivery with no reminders in existence, which a daily sweep cannot do.
