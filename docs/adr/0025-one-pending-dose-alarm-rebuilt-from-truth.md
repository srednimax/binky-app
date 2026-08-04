# One pending dose alarm, rebuilt from truth

ADR-0024 refused per-reminder scheduled work for care and gave the feature one sweep. Doses cannot use that
sweep — ADR-0003 gives them `setExactAndAllowWhileIdle` because a late dose during treatment has
consequences a nail trim does not, and a once-daily pass is the opposite of exact. So doses diverge in
**mechanism**. They do not diverge in **bookkeeping**.

There is **one** pending dose alarm for the whole app: one request code, one `PendingIntent`,
`FLAG_UPDATE_CURRENT`. It targets the earliest unanswered derived slot at or after now. It is recomputed
from the courses table after every course, time or dose write, **after every bunny archive, un-archive or
delete**, on boot, on timezone or clock change, when the exact-alarm permission is granted, and immediately
after firing.

**The query is defined by what it excludes, and a start date is not one of the exclusions.** A course is
read if reminders are on, `endOn` is null or not before today, and its bunny is not archived. Whether the
course has *started* is `dueDoses`' business, not the query's — its window already opens at
`max(startOn, today)`, and a filter on `startOn` would drop the slots of a course beginning tomorrow, which
is how most courses are created. The first morning would then arm nothing, and the next rebuild after it
is the care sweep, an hour too late by construction.

The three bunny-level writes are in the list for the same reason: they change the answer without touching a
medication table, so a rebuild hung off the medication writes alone would miss them — and a bunny's deletion
takes its courses by cascade with no course write happening at all. Since the rebuild is idempotent and
costs one query, it is wired **at the repository layer**, on every write that could change the answer,
rather than remembered at each call site. Enumerating call sites is how the sideways paths were missed the
first time.

Nothing is incremental and nothing is per-course, so there is no alarm to orphan when a course is deleted,
a bunny is archived, or a schedule moves. The invariant is checkable in one line, the same shape as
ADR-0024's: **at most one pending dose alarm exists, and none when no course is armed** — visible in
`dumpsys alarm`.

This corrects ADR-0024's closing description of doses as "individually armed alarms". Individually *timed*
is the divergence ADR-0003 requires; individually *tracked* was never part of it.

## One alarm is one point of failure, so more things rebuild it

The cost of a single alarm is that a single missed rebuild ends every future dose reminder, silently, on the
feature with the worst failure mode in the app. The failure is invisible by construction: zero pending
alarms is also the correct state when nothing is armed, so nothing can tell "no course" from "alarm lost".
And the ways to lose it are real — a receiver that hits ADR-0007's pending-schema guard does nothing, which
correctly means it also does not re-arm; revoking `SCHEDULE_EXACT_ALARM` on Android 14+ cancels pending
exact alarms and force-stops the app; a process killed between posting and re-arming leaves nothing behind.

Because the rebuild is idempotent — same request code, recomputed from truth, never appended to — the answer
is not more alarms but **more occasions to rebuild the same one**. Two are added to the list above:

- **The daily care sweep** calls `rescheduleDoseAlarm()` as its last step. It is already permanently
  enqueued, already self-perpetuating, and already the one mechanism with overnight-Doze evidence on the
  test device.
- **Process start** does the same, which is the only thing that can help after a force-stop, since a
  force-stopped app runs no receivers and no workers until the owner opens it.

This does not unify the two mechanisms and must not grow into doing so. The sweep never delivers a dose,
never posts on the `doses` channel, and never decides when a dose is due; it repairs an alarm the exact
path owns. The coupling runs one way only — the reliable-but-imprecise mechanism repairs the precise one,
never the reverse — and ADR-0024's "do not simplify doses onto the sweep later" stands unchanged.

## Consequences

**A dropped alarm self-heals within a day where background work runs, and on next launch where it does
not.** That is the whole reason to accept the coupling, and the second half is not a hedge: two of the three
occasions above — the boot receiver and the care sweep — are gated on HyperOS by *autostart*, which is off by
default and which the app cannot read at all (the reason `Armed` does not depend on it, ADR-0003's Phase 4a
amendment). With autostart denied, a reboot leaves process start as the only rebuild, so the heal arrives
when somebody opens the app — on the feature whose entire point is that nobody had to.

This is stated rather than assumed because every reboot result the project holds was taken on one phone in
one unrecorded autostart state, which means none of them distinguishes the two cases. The gate therefore
runs its reboot check **twice, autostart granted and denied**, and the denied run is the one that describes
an owner who skipped the prompt.

**The invariant is a gate item, not a unit test.** Idempotence is assertable in JVM tests, but "exactly one
pending alarm on a real device after adding courses, recording doses, archiving a bunny, changing the clock
and rebooting" is only answerable with `dumpsys alarm` on the Xiaomi.

**A slot is answerable late, but only inside a stated grace window.** The naïve rule — fire the slots due
*now*, skip everything already past — is wrong in the app's **default** configuration rather than in a corner
of it. Without `SCHEDULE_EXACT_ALARM`, which is denied by default from Android 14, the alarm goes in via
`setAndAllowWhileIdle` and the OS chooses when to deliver it; in Doze that is a window measured in minutes.
An alarm placed for 08:00 and delivered at 08:04 would find its own slot in the past, post nothing, and
re-arm for the next one — so the honest-degradation path would deliver **no dose reminders at all**, looking
exactly like the correct quiet of no course being armed. That is ADR-0003's stated hazard arriving through
the door built to avoid it, and no two-minute test on a screen-on phone can see it.

So the rule is `now - slot ≤ grace`, with **grace a named constant of 30 minutes** — past the OS's Doze
window so a best-effort alarm still delivers, far short of the eleven-hours-late answer below. Fire and
reschedule share the one predicate rather than each deciding; a slot outside the window is skipped and reads
unanswered in the app, as before.

**A slot whose time passed while the phone was off is still not fired retroactively.** That is now the same
rule seen from the other end — the phone was off for hours, not minutes — rather than a second one. A stack
of 3 a.m. notifications at breakfast is a lie about when the app knew.

**A dose notification expires with its slot.** `setTimeoutAfter` local midnight, because ADR-0002 stops
deriving a day once it is over — a shade that still offers a one-tap **Given** for a slot the app no longer
models is offering to record something it would otherwise make the owner back-date deliberately.

**Firing posts one notification per due course**, and answering from the shade re-arms. Each action receiver
hits ADR-0007's guard first, writes the row, then rebuilds — in that order, so a guarded receiver leaves the
database untouched and the alarm unchanged rather than half-applied.
