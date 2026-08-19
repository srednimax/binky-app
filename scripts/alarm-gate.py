#!/usr/bin/env python3
"""DOD §2's gate items, driven against the phone and reported as a table.

**What this is for.** ADR-0025's invariant is one sentence — *at most one pending dose alarm
exists, and none when no course is armed* — and `DoseAlarmTest` already proves it in-process
against an in-memory database. What that test cannot reach is the half the gate actually asks
about: whether the app's **own write paths**, tapped through on a real phone with a vendor ROM in
the loop, reach the rebuild at all. A repository method that forgets to reschedule passes every
instrumented assertion about the database and none of the readings below.

So each check here is *drive the UI, then read `dumpsys alarm`* — the platform's own answer, not
Kotlin's. "At most one" needs no assertion: there is a single request code (`DOSE_ALARM_REQUEST`),
so a second pending dose alarm is not expressible. What is asserted is **how many** exist and
**which instant** the one carries, because a rebuild that fires but computes the wrong slot looks
identical to a correct one until you read `origWhen`.

**Read the count and the instant, never the notification.** A dose notification is downstream of an
alarm that fired; this script only ever asks what is *armed*. That keeps every check runnable at any
hour of the day, which the notification path is not.

Usage:

    python3 scripts/alarm-gate.py --list
    python3 scripts/alarm-gate.py --only writes
    python3 scripts/alarm-gate.py            # every check that does not need a reboot

The driving vocabulary — `tap`, `swipe_to_end`, `reset_to_seeded` and the rest — is imported from
`edge-to-edge.py` rather than copied. That file is the expensive asset in this repo (see
`screenshots.py`, which does the same); its `tap` already knows that this phone drops a bare
`input tap`, that a screen still composing dumps as an empty `ComposeView`, and that a needle has to
be re-looked-for while the list is still moving.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

# `edge-to-edge.py` is not an importable module name — the hyphen makes it un-`import`-able by the
# ordinary statement — so it is loaded by path. Exactly the dance `screenshots.py` does, and for the
# same reason: the alternative is a second copy of `tap` that will drift from the first.
_E2E = Path(__file__).resolve().parent / "edge-to-edge.py"
_spec = importlib.util.spec_from_file_location("e2e", _E2E)
e2e = importlib.util.module_from_spec(_spec)
sys.modules["e2e"] = e2e
_spec.loader.exec_module(e2e)

StepFailed = e2e.StepFailed

# The receiver's fully-qualified name as `dumpsys alarm` prints it in an alarm's `tag=`. Note this
# is the **namespace** (`app.binky.tracker`), not the `applicationId` — the two deliberately
# disagree, and the tag carries both: `*walarm*:binky.bunny.and.rabbit.tracker.debug/app.binky…`.
DOSE_TAG = "app.binky.tracker.work.DoseAlarmReceiver"

# How long a write is given to reach the alarm before the reading is taken. **Fixed, and not a poll
# that stops when it likes the answer** — a retry-until-it-passes loop turns "the rebuild is slow"
# into "the rebuild happened", which is the one failure this whole file exists to catch. Generous
# enough for a Room write plus a Flow collection plus the AlarmManager round trip on a cold screen.
REBUILD_SETTLE = 2.5


# ------------------------------------------------------------------------------------------------
# Reading the alarm
# ------------------------------------------------------------------------------------------------


@dataclass(frozen=True)
class DoseAlarm:
    """One pending dose alarm as `dumpsys alarm` describes it."""

    orig_when: str  # "2026-08-20 03:00:00.000", the wall-clock instant it will fire at
    window: str  # "0" for the exact mechanism, "+38m55s" or similar for best-effort
    exact_reason: str  # "permission" when exact; absent (and so "") on the degraded path
    when_elapsed: str
    max_when_elapsed: str

    @property
    def exact(self) -> bool:
        """Whether this is `setExactAndAllowWhileIdle` rather than the degraded path.

        **The only pre-fire proof of which mechanism is armed** (DOD §1). `window=0` alone is not
        enough and neither is the appop: the pair `whenElapsed == maxWhenElapsed` is what says the
        OS has been given no latitude at all. The best-effort alarm reads a window of tens of
        minutes and a `maxWhenElapsed` that far ahead of its `whenElapsed`.
        """
        return self.window == "0" and self.when_elapsed == self.max_when_elapsed

    @property
    def at(self) -> str:
        """The fire instant to the minute — what a check compares against."""
        return self.orig_when[:16]


def dose_alarms() -> list[DoseAlarm]:
    """Every *pending* dose alarm, which is the count the invariant is about.

    **Everything above `Removal history:` is pending; everything below it has already gone.** That
    boundary is not cosmetic — the removal history holds every alarm this package has ever had
    cancelled, so a parser that reads the whole dump reports a dozen and calls the invariant broken.
    """
    out = e2e.shell("dumpsys alarm")
    lines = out.splitlines()
    end = next((i for i, line in enumerate(lines) if "Removal history:" in line), len(lines))

    found: list[DoseAlarm] = []
    for i in range(end):
        if DOSE_TAG not in lines[i]:
            continue
        # The fields are spread over the three lines after the tag, so the block is read as one
        # string rather than field by field.
        block = "\n".join(lines[i : i + 6])
        orig = re.search(r"origWhen=([\d-]+ [\d:.]+)", block)
        window = re.search(r"\bwindow=(\S+)", block)
        reason = re.search(r"exactAllowReason=(\S+)", block)
        pair = re.search(r"\bwhenElapsed=(\S+)\s+maxWhenElapsed=(\S+)", block)
        found.append(
            DoseAlarm(
                orig_when=orig.group(1) if orig else "?",
                window=window.group(1) if window else "?",
                exact_reason=reason.group(1) if reason else "",
                when_elapsed=pair.group(1) if pair else "?",
                max_when_elapsed=pair.group(2) if pair else "??",
            )
        )
    return found


# ------------------------------------------------------------------------------------------------
# The report
# ------------------------------------------------------------------------------------------------


@dataclass
class Report:
    """What every check writes into: one row per reading, and a pass/fail tally."""

    rows: list[dict] = field(default_factory=list)

    def record(self, check: str, step: str, expected: str, actual: str, ok: bool, note: str = "") -> None:
        self.rows.append(
            {"check": check, "step": step, "expected": expected, "actual": actual, "ok": ok, "note": note}
        )
        mark = "ok  " if ok else "FAIL"
        print(f"  [{mark}] {step:38s} expected {expected:22s} got {actual}" + (f"  ({note})" if note else ""))

    @property
    def failed(self) -> int:
        return sum(1 for row in self.rows if not row["ok"])


REPORT = Report()
_CHECK = "?"


def armed(step: str, at: str | None = None, *, exact: bool | None = None) -> list[DoseAlarm]:
    """Assert exactly one pending dose alarm, optionally at a named minute.

    `at` is "YYYY-MM-DD HH:MM". Left out where the check is about the *count* rather than the
    instant — archiving a bunny, for one, where "one alarm survives" is the whole claim.
    """
    time.sleep(REBUILD_SETTLE)
    alarms = dose_alarms()
    want = f"1 alarm{f' @ {at}' if at else ''}"
    if len(alarms) != 1:
        REPORT.record(_CHECK, step, want, f"{len(alarms)} alarms", False)
        return alarms
    got = alarms[0]
    ok = at is None or got.at == at
    if ok and exact is not None:
        ok = got.exact == exact
    REPORT.record(
        _CHECK,
        step,
        want,
        # The **date** as well as the time. Reading only `HH:MM` is what made a run near the seed's
        # own 20:00 slot unreadable: "20:00" and "08:00" were today's and tomorrow's, and which was
        # which was the entire question.
        f"1 alarm @ {got.at}",
        ok,
        f"{'exact' if got.exact else 'best-effort'}, window={got.window}",
    )
    return alarms


def disarmed(step: str) -> None:
    """Assert **no** pending dose alarm — the half of the invariant a stale alarm breaks silently."""
    time.sleep(REBUILD_SETTLE)
    alarms = dose_alarms()
    REPORT.record(_CHECK, step, "0 alarms", f"{len(alarms)} alarms", len(alarms) == 0)


def observe(step: str, expected: str, actual: str, ok: bool, note: str = "") -> None:
    """Record something that is not an alarm count — a screen's words, a row count, a state."""
    REPORT.record(_CHECK, step, expected, actual, ok, note)


# ------------------------------------------------------------------------------------------------
# Driving vocabulary this file adds
# ------------------------------------------------------------------------------------------------


def visible() -> list[str]:
    """Every label the app is currently showing, structural nodes dropped.

    Used for the checks that are about *words on a screen* rather than an alarm — the blocked
    delivery line, the delete dialog's counts — where the assertion is "this sentence is on screen".
    """
    skip = {"View", "Button", "FrameLayout", "LinearLayout", "ComposeView", "ScrollView", "CheckBox"}
    return [n.label for n in e2e.dump_ui() if n.package == e2e.PACKAGE and n.label and n.label not in skip]


def on_screen(needle: str) -> bool:
    """Whether any label contains `needle`, case-insensitively. A question, never a tap."""
    return any(norm(needle) in norm(label) for label in visible())


def norm(text: str) -> str:
    """Times as a needle can spell them.

    The app formats a time of day with a **narrow no-break space** before AM/PM (U+202F) — the
    correct typography, and invisible in a terminal — so the chip an owner reads as "8:00 AM" is
    `8:00\u202fAM` in the dump and a needle typed with an ordinary space matches nothing. Both
    unusual spaces are folded here rather than in each needle, because a needle that has to be
    written with an escape in it is a needle someone will get wrong.
    """
    return text.replace("\u202f", " ").replace("\u00a0", " ").casefold()


def all_labels() -> list[str]:
    """Every label on a screen top to bottom, collected while scrolling to the end.

    **What a viewport-only read gets wrong here.** The Care tab's delivery line — the sentence this
    whole gate item is about — is composed *below* Routine care, so a dump of the tab as it opens
    does not contain it and `on_screen` answers False for a line that is plainly on the screen.
    Worse, the negative form of that check then passes for the same wrong reason, which is a cell
    that cannot fail.
    """
    seen: list[str] = []
    previous = ""
    for _ in range(e2e.TAP_SCROLL_CAP):
        for label in visible():
            if label not in seen:
                seen.append(label)
        current = e2e.screen_signature(e2e.dump_ui())
        if current == previous:
            break
        previous = current
        e2e.swipe_up()
    return seen


def anywhere(needle: str) -> bool:
    """Whether `needle` appears anywhere on a screen, scrolling to look. A question, never a tap.

    [on_screen] asks about the current viewport, which is the right question for a dialog and the
    wrong one for a row in a year-long list or a line under a tab's last section.
    """
    return any(norm(needle) in norm(label) for label in all_labels())


def delivery_line() -> str:
    """The Care tab's reminder-delivery sentence, whichever of the six it currently is.

    Recorded rather than merely matched, so a reading that fails says *which* state the app was in
    — the difference between "blocked" and "best-effort, battery" is the whole finding.
    """
    # The **longest** match, because the section this line sits under is headed *Reminders* and a
    # first-match read returns the heading — a one-word answer that looks like a state and is not.
    lines = [label for label in all_labels() if "reminder" in norm(label)]
    return max(lines, key=len) if lines else "(no delivery line)"


def matches(needle: str, *, exact: bool = False, scroll: bool = False) -> list:
    """Every distinct node containing `needle`, in the order the dump lists them.

    `exact` compares the node's **text** for equality instead, which is what tells a button apart
    from the dialog title above it: tapping a course's *Delete* opens *"Delete this course?"*, and a
    substring needle then matches the question rather than the answer. Every confirm below uses it.

    **`e2e.find` returns the smallest match and that is wrong here.** A course detail screen carries
    a *Delete* for the course and one for every recorded dose; the smallest of them is whichever the
    font happened to lay out narrowest, which is not a choice at all. Document order is, because the
    course's own actions are composed above its dose history — so index 0 is always the course's.

    Nodes are de-duplicated by centre, since Compose publishes a merged node for a row as well as
    the leaf inside it and both match the same needle.
    """
    if scroll:
        # The same scroll-while-the-screen-is-still-moving loop `e2e.tap` uses, and it is needed for
        # the same reason: Home's *Archive* and *Delete* sit under the whole profile card, so a
        # single dump of the top of the screen reports them absent. The signature check is what
        # stops it swiping sixteen times on a screen that cannot scroll.
        previous = ""
        for attempt in range(e2e.TAP_SCROLL_CAP):
            here = matches(needle, exact=exact)
            if here:
                return here
            current = e2e.screen_signature(e2e.dump_ui())
            if attempt >= e2e.MIN_TAP_TRIES - 1 and current == previous:
                return []
            previous = current
            e2e.swipe_up()
        return []

    wanted = norm(needle)
    seen: dict[tuple[int, int], object] = {}
    for node in e2e.dump_ui():
        if node.package != e2e.PACKAGE or node.bounds.area <= 0:
            continue
        if exact:
            if norm(node.text) != wanted:
                continue
        elif wanted not in norm(node.text) and wanted not in norm(node.desc):
            continue
        centre = ((node.bounds.left + node.bounds.right) // 20, (node.bounds.top + node.bounds.bottom) // 20)
        if centre not in seen or node.bounds.area < seen[centre].bounds.area:
            seen[centre] = node
    return [seen[key] for key in sorted(seen, key=lambda k: (k[1], k[0]))]


def tap_node(node) -> None:
    """Tap a node already found, with the same look-again retry `e2e.tap` uses.

    The retry is not optional on this phone: exit status proves nothing about whether a tap landed,
    so the screen is asked instead.
    """
    x = (node.bounds.left + node.bounds.right) // 2
    y = (node.bounds.top + node.bounds.bottom) // 2
    before = e2e.screen_signature(e2e.dump_ui())
    for _ in range(3):
        e2e.shell(f"input touchscreen tap {x} {y}")
        e2e.settle(1.2)
        if e2e.screen_signature(e2e.dump_ui()) != before:
            return


def tap_nth(needle: str, index: int = 0, *, exact: bool = False, scroll: bool = True) -> None:
    """Tap the `index`-th node containing `needle`, top to bottom. See [matches]."""
    found = matches(needle, exact=exact, scroll=scroll)
    if index >= len(found):
        raise StepFailed(f"asked for match {index} of {needle!r}, found {len(found)}: {visible()[:20]}")
    tap_node(found[index])


def tap_exact(text: str, index: int = 0) -> None:
    """Tap a control whose label is exactly `text` — the confirm-button form of [tap_nth]."""
    tap_nth(text, index, exact=True)


def confirm(title: str, button: str) -> None:
    """Press `button` in the dialog headed `title`, and fail if that dialog is not up.

    **Position is the only thing that tells a dialog's button from the screen's own.** A course
    detail carries a *Delete* for the course and one per recorded dose; opening any of them raises a
    dialog whose confirm says *Delete* as well, and `uiautomator` hands back one flat tree with no
    reliable marker of which window a node came from. What is reliable is that a dialog's buttons
    are drawn **below its title**, so the confirm is the first exact match under it.

    Checking the title first is the other half: a tap that missed leaves the screen unchanged, and
    without this the next tap would hit the *screen's* Delete and destroy the wrong row — an
    unnoticed second delete looks exactly like a rebuild that did not run.
    """
    e2e.settle(0.8)
    # Never scrolling: a dialog is not a list, and a swipe aimed at one lands on the scrim behind it.
    heading = next((n for n in matches(title)), None)
    if heading is None:
        raise StepFailed(f"dialog {title!r} never opened; on screen: {visible()[:20]}")
    below = [n for n in matches(button, exact=True) if n.bounds.top > heading.bounds.top]
    if not below:
        raise StepFailed(f"dialog {title!r} has no {button!r} under it; on screen: {visible()[:20]}")
    tap_node(below[0])


def pick_time(hour: int, minute: int = 0) -> None:
    """Drive the Material time picker's clock dial, then OK.

    The dial publishes each position as a node described *"N hours"* / *"N minutes"*, which is the
    only reason this is drivable at all — there is no text field to type into unless the owner
    switches modes, and the mode toggle is one more thing to get wrong in nine locales. The hour
    ring is 24 positions here, so `hour` is 0–23 whatever the display format says.
    """
    e2e.settle(1.0)
    e2e.tap(f"{hour} hours")
    e2e.settle(0.8)
    e2e.tap(f"{minute} minutes")
    e2e.settle(0.8)
    e2e.tap("OK")
    e2e.settle(1.0)


SWITCH_ID = "com.android.settings:id/switchWidget"

AUTOSTART_ACTIVITY = "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"

# The label HyperOS lists this build under. The debug build takes `applicationIdSuffix = ".debug"`
# (ADR-0023) and its own label, so it appears beside the Play `Binky` rather than replacing it —
# which is the whole point, and also why the needle has to be the longer of the two names.
AUTOSTART_LABEL = "Binky Debug"


def shell_ok(cmd: str) -> str:
    """`adb shell`, tolerating a non-zero exit.

    `e2e.shell` raises on one, which is right for the commands that ought to succeed and wrong for
    the ones whose *failure is the answer*: `pidof` exits 1 when the process is not running, which
    is precisely the reading the reboot check is taking.
    """
    import subprocess

    done = subprocess.run(["adb", "shell", cmd], capture_output=True, text=True)
    return done.stdout


def _system_xml() -> str:
    """A `uiautomator` dump as raw XML, for the screens that are not this app's.

    `e2e.dump_ui` filters to the app's own package, which is right everywhere else and useless here:
    the autostart list and the channel settings both belong to the OEM.
    """
    e2e.shell("uiautomator dump /sdcard/alarm-gate.xml")
    return e2e.adb("exec-out", "cat", "/sdcard/alarm-gate.xml")


def autostart_state() -> tuple[int, bool]:
    """The autostart screen's header count, and whether this build is in the allowed list.

    **The header is the only readable signal.** A `uiautomator` dump's `checked` attribute lies on
    this screen — every row reports false, granted ones included (DOD §1, read 2026-08-18) — so the
    state is inferred from the count in the heading and from which names appear above the
    "aren't allowed" divider. The screen also *keeps its scroll position* between visits, so it is
    wound back to the top before reading or the heading is simply not on screen.
    """
    # **Force-stopped rather than scrolled back to the top**, and that is a fix for damage this
    # helper did rather than a tidiness. The screen keeps its scroll position between visits, so the
    # heading needs the list wound back — but a swipe that ends near the top of the display pulls
    # the *notification shade* down instead once the list has nowhere left to go, and on a locked
    # phone the shade takes focus and will not give it back to `adb`. Restarting the activity from
    # cold puts it at the top with no swiping at all.
    e2e.shell("am force-stop com.miui.securitycenter")
    e2e.settle(1.0)
    e2e.shell(f"am start -n {AUTOSTART_ACTIVITY}")
    e2e.settle(3.5)
    xml = _system_xml()
    count = re.search(r'text="(\d+) apps can start', xml)
    allowed = xml.split("aren&#39;t allowed")[0] if "aren&#39;t allowed" in xml else xml.split("aren't allowed")[0]
    return (int(count.group(1)) if count else -1, f'text="{AUTOSTART_LABEL}"' in allowed)


def set_autostart(on: bool) -> bool:
    """Grant or revoke autostart for this build, by tapping its row's switch.

    **There is no appop for this.** `AUTO_START` is not in `cmd appops`' vocabulary — it is a
    Settings toggle and nothing else, which is why every previous run had to be set up by hand. The
    switch sits at the right edge of the row, so the tap is aimed at the row's vertical centre and
    the screen's right margin rather than at the label.
    """
    for _ in range(3):
        count, listed = autostart_state()
        if listed == on:
            return True
        # Wound back to the top by [autostart_state]; the row is found by scrolling down from there,
        # which reaches it whether it is in the allowed list or the long denied one below it.
        for _ in range(40):
            xml = _system_xml()
            row = re.search(
                rf'text="{re.escape(AUTOSTART_LABEL)}"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml
            )
            if row:
                top, bottom = int(row.group(2)), int(row.group(4))
                width = re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml)
                right_margin = int(width.group(1)) - 130 if width else 1050
                e2e.shell(f"input touchscreen tap {right_margin} {(top + bottom) // 2}")
                e2e.settle(2.0)
                break
            # Downward through the list only. The upward direction is [autostart_state]'s problem
            # and it does not swipe at all any more.
            e2e.shell("input swipe 600 2000 600 900 250")
            e2e.settle(0.6)
        else:
            return False
    return autostart_state()[1] == on


def wait_for_unlock(timeout: float = 900.0) -> None:
    """Stop and ask a person to unlock the phone, then carry on when they have.

    **This device has a password and `adb` cannot get past it** — not `wm dismiss-keyguard`, not a
    swipe, not a power cycle. So a reboot is not a step a script may take and then continue through;
    it is a step that hands the run back to a person for a moment.

    Ploughing on instead is worse than stalling, because a locked phone refuses far more than taps
    and every refusal is misleading: `am start` answers `Error type 3 / Activity class ... does not
    exist` for an activity plainly in the resolver table, `adb install` answers
    `INSTALL_FAILED_USER_RESTRICTED` because its prompt cannot be shown, and `uiautomator dump`
    comes back empty. On 2026-08-19 those three cost a session's worth of chasing a package that was
    never broken. Asking the question first turns all of them into one known state.
    """
    if "isKeyguardShowing=false" in e2e.shell("dumpsys window | grep isKeyguardShowing"):
        return
    print("\n  ⏸  THE PHONE IS LOCKED — please unlock it. Waiting…", flush=True)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if "isKeyguardShowing=false" in e2e.shell("dumpsys window | grep isKeyguardShowing"):
            print("  ▶  unlocked, carrying on", flush=True)
            # Held awake while on USB for the rest of the run, so the screen cannot lock itself
            # again halfway through the taps that follow and strand the check a second time.
            # `main` puts it back.
            e2e.shell("svc power stayon usb")
            e2e.settle(2.0)
            return
        time.sleep(5)
    raise StepFailed("the phone was never unlocked; the reboot arm cannot continue without it")


def reboot_and_wait(timeout: float = 180.0) -> float:
    """Reboot the phone and come back when it is up. Returns how long that took.

    **Nothing is launched afterwards, and that is the whole design of the check.** An alarm does not
    survive a reboot — `AlarmManager` forgets every one of them — so a pending dose alarm read after
    boot can only have been put there by `BootReceiver`. Touching the app first would arm it from
    the ordinary process-start path and prove nothing about the receiver.
    """
    started = time.time()
    e2e.adb("reboot")
    time.sleep(8)
    e2e.adb("wait-for-device")
    while time.time() - started < timeout:
        if e2e.shell("getprop sys.boot_completed").strip() == "1":
            # The receiver is not synchronous with `sys.boot_completed`: BOOT_COMPLETED is queued
            # behind the rest of the boot, and reading the alarm list too early reports an empty
            # one for a receiver that simply had not run yet — a false negative that looks exactly
            # like the finding.
            time.sleep(45)
            # Reading the alarm list needs no screen, but everything after it does — and the check
            # must not walk into a locked phone's misleading refusals. See [wait_for_unlock].
            return time.time() - started
        time.sleep(3)
    raise StepFailed(f"the phone never finished booting in {timeout:.0f}s")


def doses_channel_importance() -> int:
    """The `doses` channel's importance as the framework holds it, or -1 if the channel is absent.

    Read from `dumpsys notification` rather than from the app, because the app's reading of it is
    the thing under test. `4` is the channel as created, `0` is `IMPORTANCE_NONE` — the owner having
    switched this one category off in system settings, which nothing in the app can ask back.

    The channel does not exist until the Care tab has been opened with a course on it: creating it
    lazily is deliberate, so an owner with no medications never sees the row in their phone's
    settings at all.
    """
    out = e2e.shell("dumpsys notification --noredact")
    found = re.search(r"mId='doses'.{0,200}?mImportance=(-?\d+)", out, re.S)
    return int(found.group(1)) if found else -1


def set_doses_channel(on: bool) -> bool:
    """Switch the `doses` channel on or off through the phone's own settings screen.

    **There is no `adb` setter for this.** `cmd notification` can allow a listener, set DND and post
    a notification, but it cannot change a channel's importance — the only writer is the system
    settings screen, so the screen is what gets driven. `CHANNEL_NOTIFICATION_SETTINGS` opens
    straight onto the one channel, which keeps the drive to a single toggle rather than a walk
    through a package's whole notification tree.

    Verified from `dumpsys` after every attempt rather than from the switch's own `checked`
    attribute: this is the same phone whose autostart screen reports `checked=false` on every row
    including the granted ones, so a toggle's self-report is not evidence here.
    """
    want = 4 if on else 0
    for _ in range(3):
        if doses_channel_importance() == want:
            return True
        e2e.shell(
            "am start -a android.settings.CHANNEL_NOTIFICATION_SETTINGS "
            f"--es android.provider.extra.APP_PACKAGE {e2e.PACKAGE} --es android.provider.extra.CHANNEL_ID doses"
        )
        e2e.settle(2.5)
        e2e.shell("uiautomator dump /sdcard/alarm-gate.xml")
        xml = e2e.adb("exec-out", "cat", "/sdcard/alarm-gate.xml")
        # The first `switchWidget` on the screen is *Show notifications*, the channel's own master
        # toggle. Named by resource id because this screen is the OEM's and its wording is not ours
        # to rely on — it happens to be English here and need not stay that way.
        node = re.search(rf'resource-id="{re.escape(SWITCH_ID)}"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if node is None:
            node = re.search(rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?resource-id="{re.escape(SWITCH_ID)}"', xml)
        if node is None:
            return False
        left, top, right, bottom = (int(g) for g in node.groups())
        e2e.shell(f"input touchscreen tap {(left + right) // 2} {(top + bottom) // 2}")
        e2e.settle(2.0)
        e2e.shell("input keyevent KEYCODE_BACK")
        e2e.settle(1.0)
    return doses_channel_importance() == want


def open_course(name: str) -> None:
    """Open a course's detail from the Care tab, by name, from the **Courses** section.

    The *last* match rather than the smallest, and that is the fix for a real failure rather than a
    precaution. A course with a slot due today appears twice on the tab — once in *Today*, where the
    row is an answer prompt carrying *Given* and *Skipped*, and again under *Courses*, where it is a
    link. `e2e.find` returns the smallest match, which on 2026-08-19 was the Today row for a course
    with no amount, and tapping it navigated nowhere; the check then failed looking for *Edit* on a
    screen it had never left. *Courses* is composed below *Today*, so the last match is the link.
    """
    found = matches(name)
    if not found:
        raise StepFailed(f"no course named {name!r} on the tab; on screen: {visible()[:20]}")
    tap_node(found[-1])


def clear_times() -> None:
    """Remove every time-of-day chip from an open course editor.

    Each chip's remove button describes itself as *Remove <time>*, so they are found by that prefix
    and tapped one at a time — the row reflows as each goes, which is why this re-dumps rather than
    collecting the list once.
    """
    for _ in range(8):
        found = [n for n in e2e.dump_ui() if n.package == e2e.PACKAGE and norm(n.desc).startswith("remove ")]
        if not found:
            return
        tap_node(found[0])
        e2e.settle(0.8)


def arm_single_slot(hour: int) -> None:
    """Put the seeded course on **one** time of day, `hour`, and nothing else.

    **The reboot check cannot use the seed's own schedule, and finding that out cost a run.** The
    sample data arms 08:00 and 20:00, and a check that happens to run near either one watches the
    alarm legitimately change under it three times: the slot ages into `DOSE_GRACE`, so a rebuild
    arms it in the past, `AlarmManager` fires a past trigger immediately, `postDueDoses` posts it,
    and the reschedule then skips *past* it — tomorrow's slot — because a posted slot is still
    unanswered and re-arming it would loop. Every one of those is correct, and between them they
    make "the same alarm came back after the reboot" unprovable. Two hours out, nothing moves.
    """
    open_care()
    open_course("Metacam")
    tap_nth("Edit", 0, exact=True)
    clear_times()
    e2e.tap("Add a time")
    pick_time(hour)
    tap_exact("Save")
    e2e.settle(1.5)


def open_care() -> None:
    """Home → the bunny in scope → the Care & Meds tab. Where every medication write starts."""
    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("Choose which bunny")
    e2e.tap("Bijou")
    e2e.tap("Care & Meds")


def twelve_hour(hour: int, minute: int = 0) -> str:
    """`20` → `"8:00 PM"` — a chip's label, so a needle can name the chip to remove.

    The app formats times for the owner, not for a driver, so the schedule chips carry 12-hour
    labels whatever the picker's dial does. [norm] handles the narrow space these are set with.
    """
    return datetime(2000, 1, 1, hour, minute).strftime("%-I:%M %p")


def today_at(hour: int, minute: int = 0) -> str:
    return datetime.now().replace(hour=hour, minute=minute).strftime("%Y-%m-%d %H:%M")


def tomorrow_at(hour: int, minute: int = 0) -> str:
    return (datetime.now() + timedelta(days=1)).replace(hour=hour, minute=minute).strftime("%Y-%m-%d %H:%M")


# ------------------------------------------------------------------------------------------------
# The checks
# ------------------------------------------------------------------------------------------------


def check_writes() -> None:
    """DOD §2 bullet 1 — *add, edit, shorten, record and skip*, against the armed course.

    The seed arms Bijou's Metacam at 08:00 and 20:00 with today's morning dose already answered, so
    the run starts with exactly one alarm at today 20:00 and every step below moves it somewhere
    provable. Two steps end at **zero**, which is the half of the invariant a stale alarm breaks in
    silence — an alarm left behind by a deleted course fires into a database that no longer has a
    dose to post, and nothing on the phone says so.
    """
    now = datetime.now()
    # The two times the added course uses. Chosen relative to the clock so the run is not a
    # 6-o'clock-only script: `late` is armed first and `early` is added on top of it, so the reading
    # after the edit proves the alarm *moved backwards*, which is the thing a rebuild that never ran
    # cannot fake.
    late, early = now.hour + 3, now.hour + 2
    if late > 23:
        raise StepFailed(f"too late in the day to run this check (needs an hour ≤ 20:00, it is {now:%H:%M})")

    print("\n-- reseeding, so the armed course is the one the readings assume")
    e2e.reset_to_seeded()

    open_care()
    armed("baseline: the seeded course", today_at(20), exact=True)

    # --- record ---------------------------------------------------------------------------------
    # The Care tab's *Given* button answers today's due slot in one tap; that is the write an owner
    # actually makes, and it is a different code path from the course detail's *Record a dose* form.
    # Exact text, because the row above it reads "Due 8:00 AM · Given at 5:45 PM" and a substring
    # needle matches the sentence rather than the button.
    tap_exact("Given")
    armed("record a dose (Given)", tomorrow_at(8))

    # --- delete the answer ----------------------------------------------------------------------
    # Back to the slot being unanswered, which is the case ADR-0025 calls out: a deleted answer has
    # to *re-arm* the slot it freed, not merely stop pointing past it.
    open_care()
    open_course("Metacam")
    tap_nth("Delete", 1, exact=True)  # 0 is the course's own Delete; 1 is the newest dose row's
    confirm("Delete this dose?", "Delete")
    armed("delete that dose", today_at(20))

    # --- skip -----------------------------------------------------------------------------------
    open_care()
    tap_exact("Skipped")
    armed("skip a dose", tomorrow_at(8))

    # --- edit -----------------------------------------------------------------------------------
    # Removing the 08:00 chip leaves the course at 20:00 only, and today's 20:00 is answered — so a
    # correct rebuild lands on *tomorrow* 20:00 rather than tomorrow 08:00.
    open_care()
    open_course("Metacam")
    tap_nth("Edit", 0, exact=True)
    tap_nth("Remove 8:00 AM")
    tap_exact("Save")
    armed("edit: remove the 08:00 time", tomorrow_at(20))

    # --- shorten --------------------------------------------------------------------------------
    # *End the course* sets the end to today, and today's only slot is already answered, so nothing
    # is left to arm. Recovery food has no schedule and Panacur ended a week ago, so this is the
    # whole database going quiet.
    open_care()
    open_course("Metacam")
    # No confirmation: the button applies straight away, and the range on the screen above it
    # changes from "From Aug 13" to "Aug 13 to <today>". Checked on the phone rather than assumed —
    # a stray second tap would have landed on whatever took its place.
    tap_exact("End the course")
    disarmed("shorten: end the course")

    # --- add ------------------------------------------------------------------------------------
    open_care()
    e2e.tap("Add a course")
    e2e.tap_field("0")
    e2e.type_text("Gate")
    # **Dismiss the IME before scrolling.** The keyboard covers the lower half of the form, so the
    # scroll that goes looking for *Add a time* runs out of screen before it reaches it — the tap
    # then fails on a control that is plainly there, which is the same shape of bug as a landscape
    # scroll budget written for a portrait screen.
    e2e.back()
    e2e.settle(0.8)
    e2e.tap("Add a time")
    pick_time(late)
    tap_exact("Save")
    armed("add a course with one time", today_at(late))

    # --- edit, moving the alarm earlier ---------------------------------------------------------
    open_care()
    open_course("Gate")
    tap_nth("Edit", 0, exact=True)
    e2e.tap("Add a time")
    pick_time(early)
    tap_exact("Save")
    armed("edit: add an earlier time", today_at(early))

    # --- shorten, moving it back ----------------------------------------------------------------
    open_care()
    open_course("Gate")
    tap_nth("Edit", 0, exact=True)
    tap_nth(f"Remove {twelve_hour(early)}")
    tap_exact("Save")
    armed("shorten: remove the earlier time", today_at(late))

    # --- delete ---------------------------------------------------------------------------------
    open_care()
    open_course("Gate")
    tap_nth("Delete", 0, exact=True)
    confirm("Delete this course?", "Delete")
    disarmed("delete the course")


def check_bunny() -> None:
    """DOD §2 bullet 2 — archive, un-archive and delete a bunny that holds an armed course.

    ADR-0025's reason for hanging the rebuild off the *container's* writes rather than off the
    medication tables: a bunny write changes the answer without touching a single medication row, so
    a scheduler wired to the medication DAO would leave an alarm armed for a bunny who is no longer
    in the switcher — and an alarm nothing will ever answer fires into a database with no dose to
    post, silently, at three in the morning.

    All three actions live on **Home's profile card**, not in the bunny editor: *Edit*, *Archive*
    and *Delete* sit under the facts, and un-archiving is a button on the archived list's row.
    """
    print("\n-- reseeding")
    e2e.reset_to_seeded()
    open_care()
    armed("baseline", today_at(20))

    e2e.relaunch()
    e2e.return_to_home()
    tap_exact("Archive")
    confirm("Archive Bijou?", "Archive")
    disarmed("archive the bunny")

    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("More")
    e2e.tap("Archived bunnies")
    tap_exact("Unarchive")
    armed("un-archive the bunny")

    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("Choose which bunny")
    e2e.tap("Bijou")
    tap_exact("Delete")
    # **Two stages, and the counts are on the second one.** The first dialog offers archiving
    # instead; only once that is declined does the app say what is actually destroyed, in ADR-0004's
    # two buckets — records solely this bunny's, and shared entries that survive for the others.
    confirm("Delete Bijou?", "Delete")
    e2e.settle(1.0)
    counted = [line for line in visible() if "record" in line.casefold()]
    observe(
        "the second stage counts what goes",
        "a count of sole-owned records",
        "; ".join(counted) or "nothing counted",
        bool(counted),
    )
    confirm("Delete Bijou and their records?", "Delete")
    disarmed("delete the bunny")


def check_dialogs() -> None:
    """DOD §2 bullet 4 — the **destructive** halves of the three dialogs that offer a choice.

    Each of these has a safe branch that gets taken by accident in testing and a destructive one
    that nobody presses twice. Pressing the destructive branch is the whole point: a dialog that
    offers to delete a weighing along with its visit has to actually delete it, and a vet who is
    removed has to leave the visits that named them standing (ADR-0004's shared-entry rule) rather
    than taking them along.

    The bunny delete's counts are checked in [check_bunny], where the deletion is already happening.
    """
    print("\n-- reseeding")
    e2e.reset_to_seeded()

    # --- a visit with a weighing recorded at it -------------------------------------------------
    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("Choose which bunny")
    e2e.tap("Bijou")
    e2e.tap("Weight")
    before = anywhere("2.380")
    observe("the seeded weighing is on the Weight tab", "2.380 kg present", str(before), before)

    e2e.tap("Care & Meds")
    e2e.tap("Weighed 2,380")
    tap_exact("Delete")
    e2e.settle(1.0)
    named = on_screen("A weighing of")
    observe("the visit dialog names the weighing", "True", str(named), named)
    # The destructive branch. The safe one — *Keep the weighing on its own* — is what a careless run
    # takes, and it would leave this check passing while proving nothing.
    tap_nth("Delete the weighing too")
    confirm("Delete this visit?", "Delete")
    e2e.settle(1.5)

    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("Choose which bunny")
    e2e.tap("Bijou")
    e2e.tap("Weight")
    after = anywhere("2.380")
    observe("the weighing went with the visit", "False", str(after), not after)

    # --- a vet, whose visits must survive without the name --------------------------------------
    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("More")
    e2e.tap("Vets")
    e2e.tap("Kowalska")
    tap_exact("Delete")
    confirm("Remove this vet?", "Delete")
    e2e.settle(1.5)

    e2e.relaunch()
    e2e.return_to_home()
    e2e.tap("Choose which bunny")
    e2e.tap("Bijou")
    e2e.tap("Care & Meds")
    gone = not anywhere("Kowalska")
    observe("the vet's name is gone from the visit", "True", str(gone), gone)
    kept = anywhere("Add a visit")
    observe("the visits section still stands", "True", str(kept), kept)


def check_blocked() -> None:
    """DOD §2 bullet 3 — notifications denied, and the `doses` channel muted.

    **The two are not one state, and the app is right to say them differently.** Both resolve to
    `ReminderDelivery.Blocked`, but `ReminderCaveats` splits on which: app-wide, nothing has ever
    arrived and the honest response is the point-of-use ask (ADR-0006) — the opt-in block, which
    explains before it requests; per-channel, the owner has switched one category off in system
    settings and no dialog the app can raise will ask it back, so it states the consequence and
    points at the screen. A check that expected one sentence for both would have been wrong about
    the better of the two behaviours.

    The second half of the bullet is the one worth having twice over: **creating a course still
    works**. A phone that cannot deliver a reminder must not become a read-only app — the record is
    the point and the reminder is the extra.

    **A third half since 9b**, and it is the reason this check was worth writing: un-muting does not
    restore the importance, and the state it leaves behind used to be reported as armed. The last
    reading is the new `Silent` caveat standing where that silence was.
    """
    print("\n-- reseeding")
    e2e.reset_to_seeded()
    # Opening the tab once is what brings the `doses` channel into existence, which the second half
    # then mutes. Lazy creation is deliberate; see [doses_channel_importance].
    open_care()
    all_labels()

    # --- half one: the app-wide permission ------------------------------------------------------
    e2e.shell(f"pm revoke {e2e.PACKAGE} android.permission.POST_NOTIFICATIONS")
    e2e.settle(1.5)
    open_care()
    ask = "Binky can remind you about recurring care"
    line = delivery_line()
    observe("notifications denied → the opt-in ask", ask, line[:80], norm(ask) in norm(line))

    open_care()
    e2e.tap("Add a course")
    e2e.tap_field("0")
    e2e.type_text("Blocked")
    e2e.back()
    e2e.settle(0.8)
    tap_exact("Save")
    e2e.settle(1.5)
    made = anywhere("Blocked")
    observe("denied: a course can still be created", "the course on the tab", str(made), made)

    e2e.shell(f"pm grant {e2e.PACKAGE} android.permission.POST_NOTIFICATIONS")
    e2e.settle(1.5)
    open_care()
    line = delivery_line()
    observe("permission back → the ask goes", f"not {ask!r}", line[:80], norm(ask) not in norm(line))

    # --- half two: the `doses` channel muted on its own -----------------------------------------
    muted = set_doses_channel(False)
    observe("the doses channel is muted", "importance 0", str(doses_channel_importance()), muted)

    open_care()
    blocked = "only appear inside the app"
    line = delivery_line()
    observe("channel muted → blocked line", blocked, line[:80], norm(blocked) in norm(line))

    open_care()
    e2e.tap("Add a course")
    e2e.tap_field("0")
    e2e.type_text("Muted")
    e2e.back()
    e2e.settle(0.8)
    tap_exact("Save")
    e2e.settle(1.5)
    made = anywhere("Muted")
    observe("muted: a course can still be created", "the course on the tab", str(made), made)

    set_doses_channel(True)
    restored = doses_channel_importance()
    open_care()
    line = delivery_line()
    observe("channel back → blocked line clears", f"not {blocked!r}", line[:80], norm(blocked) not in norm(line))
    # **Audible again, but not as it was.** Switching the channel back on brings it up at
    # `IMPORTANCE_LOW` rather than the `HIGH` the app created it with, and `mUserLockedFields`
    # records that the owner has touched it — so the app can never raise it again, by design of the
    # framework. The reading is recorded rather than asserted at 4, because 4 is not something
    # either the app or this script gets to choose once a person has been in that screen.
    observe(
        "un-muting restores audibility, not importance",
        "importance > 0 (4 unreachable once user-locked)",
        f"importance {restored}",
        restored > 0,
        "IMPORTANCE_LOW; mUserLockedFields=4" if restored == 2 else "",
    )

    # **And what the app now says about it.** At importance 2 it used to say nothing: the resolver
    # treated everything above `IMPORTANCE_NONE` as fine and returned `Armed`, so the owner of a
    # channel their phone had quietly lowered was told the reminder was set up to get through. It
    # now returns `ReminderDelivery.Silent` below `IMPORTANCE_DEFAULT`, and the card names the
    # consequence and opens the channel's own settings page — the only screen the level can be
    # raised from. Recorded as not-applicable rather than failed on a phone that hands the channel
    # back audible, because there is then no silent state to describe.
    silent = "no sound and no pop-up"
    if restored < 3:
        observe("lowered channel -> the silent caveat", silent, line[:80], norm(silent) in norm(line))
    else:
        observe(
            "lowered channel -> the silent caveat",
            "n/a on this phone",
            f"restored audible at {restored}",
            True,
            "nothing to describe",
        )
    # A `pm clear` is the only thing that puts the channel back at 4, so the phone is not left with
    # a permanently quieter dose reminder than the one every other check assumes.
    e2e.reset_to_seeded()


def check_timezone(zone: str = "America/New_York") -> None:
    """DOD §2 bullet 6 — a timezone change must not re-arm a dose already answered.

    A dose is answered against a *date*, and the schedule is a wall-clock time of day. Move the
    clock five hours west and today's 20:00 slot becomes an instant that has not happened yet in the
    new zone — so a rebuild that re-derives slots without carrying their answers forward would arm
    an alarm for a dose the owner already gave. That is the worst class of reminder bug there is:
    the app telling someone to double-dose a rabbit.
    """
    print("\n-- reseeding")
    e2e.reset_to_seeded()
    open_care()
    armed("baseline, Europe/Warsaw", today_at(20))

    tap_exact("Given")
    armed("answer today's remaining dose", tomorrow_at(8))

    original = e2e.shell("getprop persist.sys.timezone").strip()
    try:
        e2e.shell("cmd time_zone_detector set_auto_detection_enabled false")
        # **`set_time_zone_state_for_tests`, not `suggest_manual_time_zone`.** The suggestion API is
        # the one a user's manual pick goes through and is the obvious choice, but it is guarded by
        # `SUGGEST_MANUAL_TIME_AND_ZONE`, which `adb shell` (uid 2000) does not hold — it fails with
        # a `SecurityException` and leaves the zone untouched, which reads exactly like a change the
        # app ignored. The test setter writes `persist.sys.timezone` for real: `date` moves with it,
        # and so does the `ACTION_TIMEZONE_CHANGED` the app rebuilds on.
        e2e.shell(f"cmd time_zone_detector set_time_zone_state_for_tests --zone_id {zone} --user_should_confirm_id false")
        e2e.settle(4.0)
        moved = e2e.shell("getprop persist.sys.timezone").strip()
        observe("timezone actually moved", zone, moved, moved == zone)

        time.sleep(REBUILD_SETTLE)
        alarms = dose_alarms()
        # **The failure this is hunting.** Five hours west, today's 20:00 becomes an instant that has
        # not happened yet, so a rebuild that re-derives slots without carrying their answers across
        # would arm an alarm for a dose already given — the app telling someone to double-dose a
        # rabbit. A correct rebuild is still on tomorrow's first *unanswered* slot.
        got = f"{len(alarms)} alarms" + (f" @ {alarms[0].at}" if alarms else "")
        observe("one alarm, still tomorrow's slot", f"1 alarm @ {tomorrow_at(8)}", got,
                len(alarms) == 1 and alarms[0].at == tomorrow_at(8))
        open_care()
        still = on_screen("Given at") or on_screen("Given ·")
        observe("today's answered dose stays answered", "True", str(still), still)
    finally:
        e2e.shell(f"cmd time_zone_detector set_time_zone_state_for_tests --zone_id {original} --user_should_confirm_id false")
        e2e.shell("cmd time_zone_detector set_auto_detection_enabled true")
        e2e.settle(3.0)
        print(f"  -- timezone restored to {e2e.shell('getprop persist.sys.timezone').strip()}")


def check_reboot() -> None:
    """DOD §2 bullet 5 — **reboot twice, autostart granted and autostart denied.**

    The one item here with a consequence beyond a tick. ADR-0025 says the alarm is rebuilt from
    truth at boot, and `BootReceiver` is how: an alarm does not survive a reboot, so the pending one
    read afterwards is the receiver's work or it is nothing. On this phone that claim has a vendor
    condition attached — without autostart HyperOS does not start the process for a broadcast at all
    — and **whatever the denied run says is what ADR-0025's self-heal consequence gets reworded to**.

    Granted first, because the grant is what the phone is currently in and a reseed does not disturb
    it. Nothing is launched between the reboot and the reading; see [reboot_and_wait].
    """
    for grant in (True, False):
        arm = "granted" if grant else "denied"
        print(f"\n-- autostart {arm}")
        e2e.reset_to_seeded()
        # Two hours out, so nothing about the slot changes across the reboot. See [arm_single_slot].
        arm_single_slot((datetime.now().hour + 2) % 24)
        # No hardcoded instant: the claim here is not *which* slot but that **the same one comes
        # back**. Reading it first and comparing afterwards is the stronger assertion anyway — a
        # receiver that rebuilt some other slot would satisfy a bare count.
        before = armed(f"{arm}: armed before the reboot")
        expected_at = before[0].at if before else "?"

        set_autostart(grant)
        count, listed = autostart_state()
        observe(f"{arm}: autostart state", str(grant), f"{count} apps, listed={listed}", listed == grant)

        # Off before the reboot rather than in the `finally`: a reboot in the middle of a run would
        # otherwise leave the phone in Do Not Disturb with nothing left to turn it off.
        e2e.set_dnd(False)
        took = reboot_and_wait()
        print(f"  -- back up after {took:.0f}s")

        # Read **before** asking for the unlock: this is the whole reading, and it must be taken
        # with nothing having touched the app. Unlocking the screen does not start it, but waiting
        # for a person to do so gives the ROM minutes it would not otherwise have had.
        alarms = dose_alarms()
        running = shell_ok(f"pidof {e2e.PACKAGE}").strip()
        # **The keyguard state belongs in the reading.** `BOOT_COMPLETED` waits for the first unlock
        # on this phone (see [check_locked_boot]), so "the alarm came back" means something quite
        # different depending on whether a person had already reached for it — and without this the
        # two runs are indistinguishable in the report.
        locked = "isKeyguardShowing=true" in e2e.shell("dumpsys window | grep isKeyguardShowing")
        observe(
            f"{arm}: the alarm is rebuilt after a reboot",
            f"1 alarm @ {expected_at}, nothing launched",
            f"{len(alarms)} alarms" + (f" @ {alarms[0].at}" if alarms else "")
            + f", pid={running or 'none'}, keyguard={'up' if locked else 'down'}",
            len(alarms) == 1 and alarms[0].at == expected_at,
        )

        # **The second reading is the one that decides the rewording.** If the boot broadcast never
        # lands, the question ADR-0025 actually cares about is whether the alarm is still rebuilt
        # from truth the moment the owner opens the app — the self-heal at process start. A phone
        # that rebuilds on launch has a reminder that is late by however long the app went unopened;
        # a phone that rebuilds at neither has no reminder at all, and those are different promises.
        # Everything past here drives the UI, so the phone has to be in a person's hands first.
        wait_for_unlock()
        e2e.set_dnd(True)
        open_care()
        after = dose_alarms()
        observe(
            f"{arm}: rebuilt at process start instead",
            f"1 alarm @ {expected_at}",
            f"{len(after)} alarms" + (f" @ {after[0].at}" if after else ""),
            len(after) == 1 and after[0].at == expected_at,
        )


def check_locked_boot() -> None:
    """**Is the alarm rebuilt at boot, or at the first unlock?** — the question §2's bullet 5 hid.

    `BootReceiver` listens for `ACTION_BOOT_COMPLETED`, and this phone is `ro.crypto.type=file`
    with a secure lock screen. Under File-Based Encryption that broadcast is not sent when the
    kernel finishes booting; it is sent when the owner's **credential-encrypted storage** is
    unlocked, which is the first time they enter their password. A receiver can opt out of the wait
    with `directBootAware`, and this one must not — it opens the database, and the database is in CE
    storage by definition.

    So the honest form of ADR-0025's self-heal claim may be *"rebuilt at the first unlock"* rather
    than *"rebuilt at boot"*, and the difference is a real dose: a phone that restarts itself for an
    OTA at 02:00 and is picked up at 07:00 has **no dose alarm at all** for those five hours, and a
    03:00 slot inside them is not late, it is gone. Nothing in the app can see this state, because
    nothing in the app is running during it.

    The check reads the alarm list repeatedly **while the phone is still locked** — which needs no
    screen and starts nothing — and then once more after the unlock. Whichever reading first shows
    an alarm is the answer.
    """
    print("\n-- reseeding")
    e2e.reset_to_seeded()
    arm_single_slot((datetime.now().hour + 2) % 24)
    before = armed("armed before the reboot")
    expected_at = before[0].at if before else "?"

    e2e.set_dnd(False)
    print("\n  ⚠  LEAVE THE PHONE LOCKED after it reboots — the readings need it locked.")
    took = reboot_and_wait()
    print(f"  -- back up after {took:.0f}s")

    for wait in (0, 60, 120):
        if wait:
            time.sleep(wait)
        locked = "isKeyguardShowing=true" in e2e.shell("dumpsys window | grep isKeyguardShowing")
        alarms = dose_alarms()
        running = shell_ok(f"pidof {e2e.PACKAGE}").strip()
        observe(
            f"locked, +{45 + wait}s after boot",
            "recorded, not asserted",
            f"keyguard={'up' if locked else 'down'}, {len(alarms)} alarms"
            + (f" @ {alarms[0].at}" if alarms else "")
            + f", pid={running or 'none'}",
            True,
            "the reading is the finding" if locked else "unlocked early — this reading proves nothing",
        )

    wait_for_unlock()
    # **Polled, and the number is the point.** The first run of this check read once at +20 s, found
    # nothing, and recorded a failure that was really a stopwatch started too early: the alarm was
    # there when the phone was next looked at. How long the owner's dose reminder does not exist for
    # is the whole consequence, so it is measured rather than sampled.
    unlocked_at = time.time()
    for _ in range(30):
        after = dose_alarms()
        if after:
            break
        time.sleep(30)
    waited = time.time() - unlocked_at
    observe(
        "after the first unlock",
        f"1 alarm @ {expected_at}",
        f"{len(after)} alarms" + (f" @ {after[0].at}" if after else "") + f", {waited:.0f}s after unlocking",
        len(after) == 1 and after[0].at == expected_at,
    )
    e2e.set_dnd(True)


CHECKS = {
    "writes": check_writes,
    "bunny": check_bunny,
    "dialogs": check_dialogs,
    "reboot": check_reboot,
    "locked-boot": check_locked_boot,
    "blocked": check_blocked,
    "timezone": check_timezone,
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", action="append", choices=sorted(CHECKS), help="run just this check; repeatable")
    parser.add_argument("--list", action="store_true", help="name the checks and exit")
    parser.add_argument("--report", type=Path, help="write the readings as JSON here")
    parser.add_argument("--keep-dnd", action="store_true", help="leave Do Not Disturb on afterwards")
    args = parser.parse_args()

    if args.list:
        for name, fn in sorted(CHECKS.items()):
            print(f"{name:10s} {(fn.__doc__ or '').splitlines()[0]}")
        return 0

    wanted = args.only or sorted(CHECKS)

    # Do Not Disturb for the length of the run, off again whatever happens. The seed's live dose
    # posts an `importance=4` heads-up over Home a minute after every reseed, and a tap aimed at the
    # tab bar lands on the banner instead — the 2026-08-12 failure, recorded at length in
    # `edge-to-edge.py`. A driver that taps a notification is not a driver.
    e2e.set_dnd(True)
    try:
        for name in wanted:
            global _CHECK
            _CHECK = name
            print(f"\n=== {name} ===")
            try:
                CHECKS[name]()
            except StepFailed as failure:
                REPORT.record(name, "(aborted)", "the check to finish", str(failure)[:120], False)
    finally:
        if not args.keep_dnd:
            e2e.set_dnd(False)
        # Whatever [wait_for_unlock] may have set. Left on, the phone never sleeps on the charger,
        # which is exactly the condition an overnight Doze run cannot have.
        e2e.shell("svc power stayon false")

    print(f"\n{len(REPORT.rows) - REPORT.failed}/{len(REPORT.rows)} readings as expected")
    if args.report:
        args.report.write_text(json.dumps({"taken": datetime.now().isoformat(timespec="seconds"), "rows": REPORT.rows}, indent=2))
        print(f"written to {args.report}")
    return 1 if REPORT.failed else 0


if __name__ == "__main__":
    sys.exit(main())
