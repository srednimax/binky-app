# One pending dose alarm, rebuilt from truth

ADR-0024 refused per-reminder scheduled work for care and gave the feature one sweep. Doses cannot use that
sweep — ADR-0003 gives them `setExactAndAllowWhileIdle` because a late dose during treatment has
consequences a nail trim does not, and a once-daily pass is the opposite of exact. So doses diverge in
**mechanism**. They do not diverge in **bookkeeping**.

There is **one** pending dose alarm for the whole app: one request code, one `PendingIntent`,
`FLAG_UPDATE_CURRENT`. It targets the earliest unanswered derived slot at or after now, across every active
course with reminders on, for every non-archived bunny. It is recomputed from the courses table after every
course, time or dose write, on boot, on timezone or clock change, when the exact-alarm permission is
granted, and immediately after firing.

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

**A dropped alarm self-heals within a day** instead of never, and within seconds if the owner opens the app.
That is the whole reason to accept the coupling.

**The invariant is a gate item, not a unit test.** Idempotence is assertable in JVM tests, but "exactly one
pending alarm on a real device after adding courses, recording doses, archiving a bunny, changing the clock
and rebooting" is only answerable with `dumpsys alarm` on the Xiaomi.

**A slot whose time passed while the phone was off is not fired retroactively.** On rebuild, past slots are
skipped. A stack of 3 a.m. notifications at breakfast is a lie about when the app knew.

**A dose notification expires with its slot.** `setTimeoutAfter` local midnight, because ADR-0002 stops
deriving a day once it is over — a shade that still offers a one-tap **Given** for a slot the app no longer
models is offering to record something it would otherwise make the owner back-date deliberately.

**Firing posts one notification per due course**, and answering from the shade re-arms. Each action receiver
hits ADR-0007's guard first, writes the row, then rebuilds — in that order, so a guarded receiver leaves the
database untouched and the alarm unchanged rather than half-applied.
