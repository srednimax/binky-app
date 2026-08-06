#!/usr/bin/env python3
"""Drive the app through the edge-to-edge matrix and check it against the system insets (PLAN 4f).

Play Console raises edge-to-edge against every app targeting SDK 35+, and the notice is generic
advice rather than a detected defect. `MainActivity` already calls `enableEdgeToEdge()` and the
shell's `Scaffold` owns the insets, so the mechanism is in place; what 4f owes is *evidence*, and
evidence for four configurations across twenty-odd screens is not something anyone captures by hand
twice.

So this drives it. For each cell of the matrix it sets the rotation and the navigation mode, walks
the app to each scene, saves a screenshot, and — the part that makes the screenshots reviewable —
asks `uiautomator` where every text, icon and control actually landed, then intersects those
rectangles with the system-bar and display-cutout rectangles `dumpsys` reports for that same
configuration. A control inside the navigation bar's rectangle is the defect the checkpoint is
looking for, found by arithmetic instead of by squinting at 80 PNGs.

The screenshots are still the deliverable: this narrows which ones a human has to open.

Usage:
    scripts/edge-to-edge.py --out /path/to/dir            # the whole matrix
    scripts/edge-to-edge.py --out DIR --config landscape-threebutton
    scripts/edge-to-edge.py --out DIR --scene home,weight-chart
    scripts/edge-to-edge.py --restore                     # hand the phone back to auto-rotate

The phone is left in whatever configuration the last cell used; `--restore` puts it back.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

PACKAGE = "binky.bunny.and.rabbit.tracker.debug"
ACTIVITY = f"{PACKAGE}/app.binky.tracker.MainActivity"

# The three inset types a screen can be wrongly drawn under. `displayCutout` is listed separately
# from `statusBars` on purpose: in portrait they coincide, and in landscape they do not — which is
# the whole reason landscape is in this matrix.
INSET_TYPES = ("statusBars", "navigationBars", "displayCutout")


# --------------------------------------------------------------------------------------------
# adb
# --------------------------------------------------------------------------------------------


def adb(*args: str, binary: bool = False) -> str | bytes:
    """Run an adb command. `binary` uses exec-out, which does not mangle \\n into \\r\\n."""
    result = subprocess.run(["adb", *args], capture_output=True, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def shell(cmd: str) -> str:
    return adb("shell", cmd)


def settle(seconds: float = 0.6) -> None:
    time.sleep(seconds)


# --------------------------------------------------------------------------------------------
# The device configuration under test
# --------------------------------------------------------------------------------------------


@dataclass(frozen=True)
class Config:
    name: str
    rotation: int  # user_rotation: 0 portrait, 1 landscape (90°)
    nav: str  # "gesture" | "threebutton"


CONFIGS = [
    # Portrait + gesture is the cell 1.0's Play screenshots already evidence. It is captured
    # anyway, because the screens 4a-4e added have no prior evidence in any cell.
    Config("portrait-gesture", 0, "gesture"),
    Config("portrait-threebutton", 0, "threebutton"),
    Config("landscape-gesture", 1, "gesture"),
    Config("landscape-threebutton", 1, "threebutton"),
]


def apply_config(config: Config) -> None:
    """Pin the rotation and the navigation mode, then wait for the window to settle.

    HyperOS does not use the AOSP `com.android.internal.systemui.navbar.*` overlays — they are all
    present and all disabled — so the navigation mode is flipped through MIUI's own
    `force_fsg_nav_bar` global instead. Verified against the navigation bar's reported inset, which
    is the thing being tested and cannot be faked by the setting alone.
    """
    shell("settings put system accelerometer_rotation 0")
    shell(f"settings put system user_rotation {config.rotation}")
    shell(f"settings put global force_fsg_nav_bar {1 if config.nav == 'gesture' else 0}")
    # SystemUI rebuilds the navigation bar out of process; there is nothing to poll that is ready
    # before the new inset is published, so this is a wait rather than a check.
    settle(3.0)


def restore_device() -> None:
    shell("settings put global force_fsg_nav_bar 1")
    shell("settings put system accelerometer_rotation 1")


@dataclass(frozen=True)
class Rect:
    left: int
    top: int
    right: int
    bottom: int

    def intersects(self, other: "Rect") -> bool:
        return (
            self.left < other.right
            and other.left < self.right
            and self.top < other.bottom
            and other.top < self.bottom
        )

    def overlap(self, other: "Rect") -> "Rect | None":
        if not self.intersects(other):
            return None
        return Rect(
            max(self.left, other.left),
            max(self.top, other.top),
            min(self.right, other.right),
            min(self.bottom, other.bottom),
        )

    @property
    def area(self) -> int:
        return max(0, self.right - self.left) * max(0, self.bottom - self.top)

    def as_list(self) -> list[int]:
        return [self.left, self.top, self.right, self.bottom]


INSET_RE = re.compile(
    r"InsetsSource id=\w+ type=(\w+) frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\] visible=(\w+)"
)


def read_insets() -> dict[str, Rect]:
    """The system inset rectangles for the *current* rotation, straight from the window manager.

    Several providers report the same type (the status bar has one per top window); they agree, so
    the largest is taken. An invisible source contributes nothing — a hidden navigation bar is not
    an area anything can be wrongly drawn under.
    """
    dump = shell("dumpsys window displays")
    found: dict[str, Rect] = {}
    for match in INSET_RE.finditer(dump):
        kind, left, top, right, bottom, visible = match.groups()
        if kind not in INSET_TYPES or visible != "true":
            continue
        rect = Rect(int(left), int(top), int(right), int(bottom))
        if kind not in found or rect.area > found[kind].area:
            found[kind] = rect
    return found


# --------------------------------------------------------------------------------------------
# Reading the screen
# --------------------------------------------------------------------------------------------

NODE_RE = re.compile(r"<node ([^>]*?)/?>")
ATTR_RE = re.compile(r'([\w:-]+)="([^"]*)"')
BOUNDS_RE = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


@dataclass
class Node:
    bounds: Rect
    text: str
    desc: str
    cls: str
    package: str
    clickable: bool

    @property
    def label(self) -> str:
        return self.text or self.desc or self.cls.rsplit(".", 1)[-1]


def dump_ui() -> list[Node]:
    """Every visible node on screen, ours and the system's.

    `uiautomator dump` writes to the device and `exec-out cat` reads it back byte-for-byte; going
    through `adb shell cat` corrupts the XML on the way out.
    """
    for attempt in range(3):
        try:
            shell("uiautomator dump /sdcard/e2e-dump.xml")
            raw = adb("exec-out", "cat", "/sdcard/e2e-dump.xml", binary=True)
            xml = raw.decode("utf-8", "replace")
            if "<node" in xml:
                break
        except subprocess.CalledProcessError:
            pass
        settle(1.0)
    else:
        return []

    nodes: list[Node] = []
    for match in NODE_RE.finditer(xml):
        attrs = dict(ATTR_RE.findall(match.group(1)))
        bounds_match = BOUNDS_RE.search(attrs.get("bounds", ""))
        if not bounds_match:
            continue
        left, top, right, bottom = (int(value) for value in bounds_match.groups())
        nodes.append(
            Node(
                bounds=Rect(left, top, right, bottom),
                text=attrs.get("text", ""),
                desc=attrs.get("content-desc", ""),
                cls=attrs.get("class", ""),
                package=attrs.get("package", ""),
                clickable=attrs.get("clickable") == "true",
            )
        )
    return nodes


def screen_signature(nodes: list[Node]) -> str:
    """What the app's screen looks like, as a string, for "did anything happen?" comparisons."""
    return "|".join(f"{n.label}{n.bounds.as_list()}" for n in nodes if n.package == PACKAGE)


def find(nodes: list[Node], needle: str) -> Node | None:
    """The smallest node whose text or description contains `needle`, case-insensitively.

    Smallest, because Compose reports a merged semantics node for a whole row as well as the leaf
    inside it, and the leaf is the one whose centre is unambiguously on the thing named.
    """
    needle = needle.casefold()
    matches = [
        node
        for node in nodes
        if node.package == PACKAGE
        and (needle in node.text.casefold() or needle in node.desc.casefold())
        and node.bounds.area > 0
    ]
    return min(matches, key=lambda node: node.bounds.area) if matches else None


# --------------------------------------------------------------------------------------------
# Driving
# --------------------------------------------------------------------------------------------


class StepFailed(Exception):
    pass


def wait_for_app(timeout: float = 12.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if PACKAGE in shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"):
            # Focus arrives before the first composition does, and a tap sent into the gap is
            # swallowed — the node is found in the dump and the tap lands on nothing. Long enough
            # to cover a cold start of the heaviest screen.
            settle(1.8)
            return
        settle(0.4)
    raise StepFailed("the app never took focus")


def relaunch() -> None:
    """A fresh start, so every scene walks from the same place.

    `force-stop` rather than plain `am start`: the back stack is saved state, so a warm start would
    reopen wherever the previous scene left off.
    """
    shell(f"am force-stop {PACKAGE}")
    settle(0.5)
    shell(f"am start -n {ACTIVITY}")
    wait_for_app()


def tap(needle: str, *, optional: bool = False) -> None:
    # Retried rather than waited on a fixed delay: a screen that is still composing dumps as an
    # empty ComposeView, and how long that takes depends on the screen, not on the driver. An
    # *optional* tap is not retried — it is asking whether something is there, and four rounds of
    # waiting to be told "no" is most of the run time of the matrix.
    attempts = 1 if optional else 4
    for attempt in range(attempts):
        nodes = dump_ui()
        node = find(nodes, needle)
        if node is not None:
            break
        # Landscape is 1220px tall where portrait is 2712, so the control a portrait screen shows
        # without asking is often several screens down — scroll for it rather than calling the scene
        # unreachable, which would quietly drop exactly the configurations under test.
        if not optional:
            swipe_up()
        else:
            settle(1.0)
    if node is None:
        if optional:
            return
        visible = ", ".join(sorted({n.label for n in nodes if n.package == PACKAGE and n.label})[:25])
        raise StepFailed(f"no node matching {needle!r}; on screen: {visible}")
    x = (node.bounds.left + node.bounds.right) // 2
    y = (node.bounds.top + node.bounds.bottom) // 2
    # **`input touchscreen tap`, never bare `input tap`** — the source has to be named. On this phone
    # `input tap` began exiting 0 while delivering nothing at all: not flaky, dropped every time, on a
    # screen provably on and focused, while `input keyevent` and `input swipe` kept working. Proved by
    # A/B on one screen at one coordinate — bare `tap` never moved the selection, `touchscreen tap`
    # moved it every time. `input` picks a default source when none is given, and that inference is
    # what HyperOS stopped honouring; naming the source sidesteps the guess (6c, 2026-08-06).
    #
    # The retry below stays regardless: exit status still proves nothing, so the screen is asked
    # instead — tap, look, tap again if nothing moved. Without it the first tap after a cold start
    # goes missing often enough to skip whole scenes, and a skipped scene reads like a clean one.
    before = screen_signature(nodes)
    for _ in range(3):
        shell(f"input touchscreen tap {x} {y}")
        settle(1.2)
        if screen_signature(dump_ui()) != before:
            return
    # Fell through: three taps and nothing moved. That is a finding about the scene, not about the
    # phone — a control that does nothing — so it is left to the caller's next step to fail on.


def back() -> None:
    shell("input keyevent KEYCODE_BACK")
    settle(0.8)


def swipe_up() -> None:
    """Scroll a list towards its end — where the row nearest the navigation bar is."""
    size = shell("wm size")
    match = re.search(r"(\d+)x(\d+)", size.split(":")[-1])
    width, height = (int(match.group(1)), int(match.group(2))) if match else (1220, 2712)
    # In landscape the physical size is still reported portrait-first, so the swipe is built from
    # whichever is currently on screen.
    if shell("settings get system user_rotation").strip() == "1":
        width, height = max(width, height), min(width, height)
    else:
        width, height = min(width, height), max(width, height)
    # **Both ends of the swipe have to land inside the scrollable**, which is a smaller target than
    # it looks in landscape: a top-level tab there has a switcher above it and a navigation bar
    # below, leaving content between roughly 26% and 76% of a 1220px screen. Swiping from 75% —
    # comfortably inside a portrait screen — landed on the bottom bar's edge and scrolled nothing,
    # and a swipe that does nothing turns a scroll-to-end scene into a screenshot of the top of the
    # list wearing the name of the bottom.
    shell(f"input swipe {width // 2} {int(height * 0.70)} {width // 2} {int(height * 0.32)} 300")
    settle(1.0)


def swipe_to_end() -> None:
    """Scroll until the screen stops changing — the position the last row comes to rest in.

    This is the one that matters. Mid-scroll, a list *should* run under the navigation bar; that is
    what edge-to-edge looks like. The defect is a list whose last row still sits under the bar once
    it has nowhere left to go, and only the end of the scroll can tell the two apart.
    """
    # Each swipe now covers less of the screen, and the observation timeline holds a year of rows,
    # so the cap is generous: stopping early would report the middle of a list as its end.
    previous = ""
    for attempt in range(16):
        swipe_up()
        current = screen_signature(dump_ui())
        if current == previous:
            return
        previous = current


def type_text(value: str) -> None:
    shell(f"input text {value}")
    settle(1.2)


def wipe() -> None:
    """Back to a first run: no preferences, no database, so the setup wizard shows.

    Destructive, and deliberately only reachable from the `empty` suite — the `full` suite runs
    against seeded sample data and a wipe in the middle of it would quietly capture empty screens
    under populated names.
    """
    shell(f"pm clear {PACKAGE}")
    settle(1.0)
    shell(f"am start -n {ACTIVITY}")
    wait_for_app()


DATABASE = f"/data/data/{PACKAGE}/databases/bunny.db"


def fake_schema_mismatch() -> None:
    """Make the database on disk claim a schema this build does not know, and relaunch.

    `SchemaMismatchScreen` is the one screen that lives outside a `Scaffold` — it pads itself with
    `safeDrawingPadding()` — so it is exactly the outlier this matrix has to look at, and there is
    no way to it through the UI. ADR-0007's guard reads SQLite's own `user_version`, four big-endian
    bytes at offset 60 of the file header, so writing a version nothing recognises is enough.

    The file is copied aside first and put back by [restore_schema_version]. Nothing here consents
    to the wipe: the screen is captured and backed out of.
    """
    shell(f"am force-stop {PACKAGE}")
    settle(0.5)
    run_as = f"run-as {PACKAGE}"
    # Only ever back up the *good* file. This runs once per cell of the matrix, and an unguarded
    # copy on the second cell would file the already-corrupted database as the thing to restore —
    # after which the restore faithfully puts the corruption back and the app never opens again.
    shell(f'{run_as} sh -c "[ -f databases/bunny.db.e2e-backup ] || cp databases/bunny.db databases/bunny.db.e2e-backup"')
    # `user_version` is a big-endian 32-bit field, and every version this project will ever have
    # fits in its last byte — so only byte 63 has to change, from 5 to 99. Which is convenient,
    # because 99 is ASCII 'c': `printf c` writes exactly the byte wanted with no escaping to get
    # wrong through two layers of shell.
    shell(f'{run_as} sh -c "printf c > databases/e2e-version.bin"')
    shell(f"{run_as} dd if=databases/e2e-version.bin of=databases/bunny.db bs=1 seek=63 conv=notrunc")
    shell(f"am start -n {ACTIVITY}")
    wait_for_app()
    settle(1.5)


def restore_schema_version() -> None:
    shell(f"am force-stop {PACKAGE}")
    settle(0.5)
    run_as = f"run-as {PACKAGE}"
    shell(f"{run_as} cp databases/bunny.db.e2e-backup databases/bunny.db")
    shell(f"{run_as} rm -f databases/bunny.db.e2e-backup databases/e2e-version.bin")


STEP_RUNNERS = {
    "tap": lambda arg: tap(arg),
    "tap?": lambda arg: tap(arg, optional=True),
    "back": lambda arg: back(),
    "swipe_up": lambda arg: swipe_up(),
    "swipe_end": lambda arg: swipe_to_end(),
    "type": type_text,
    "wait": lambda arg: settle(float(arg)),
    "wipe": lambda arg: wipe(),
    "fake_schema_mismatch": lambda arg: fake_schema_mismatch(),
}


@dataclass(frozen=True)
class Scene:
    """One screen worth capturing, and the taps that reach it from a cold start.

    `family` is how the review is organised (PLAN 4f): screens sharing chrome fail identically, so
    they are reviewed as a group against a representative, and a member that differs from its
    representative is itself the finding.
    """

    name: str
    family: str
    steps: list[tuple[str, str]] = field(default_factory=list)
    note: str = ""
    # "full" runs against seeded sample data; "empty" runs against a wiped install, which is the
    # only way to see the first-run wizard and the only honest way to see an empty list.
    suite: str = "full"
    # The sample data seeds a watch that has already run out, so 4d's expiry prompt is waiting on
    # top of every launch until someone answers it — and answering it is permanent. So the prompt
    # is captured in all four configurations first and dismissed out of the way everywhere else.
    keeps_watch_prompt: bool = False


# The app-wide bunny selection is a preference, so it survives the relaunch each scene starts with
# — which makes it shared state between scenes rather than something each one sets. Every scene that
# needs a bunny in scope says so, and pays two taps for the privilege of not depending on its
# neighbours. "Bijou" is the debug sample data's first bunny.
SELECT_BUNNY = [("tap", "Choose which bunny"), ("tap", "Bijou")]

# The weight form, reached through the weigh-in care reminder rather than the Weight tab's own
# button. Same screen either way, and this route is the one that survives landscape: the Weight tab
# puts a year of weighings below that button, so in a 1220px-tall viewport finding it means paging
# through the list, while Care's *Record a weight* sits on a screen with five rows on it.
OPEN_WEIGHT_FORM = [("tap", "Care"), ("tap", "Record a weight")]

# The order matters only in that each scene starts from a relaunch, so they are independent.
SCENES = [
    # --- the screen with no Scaffold at all, reachable only by lying to ADR-0007's guard --------
    Scene(
        "schema-mismatch",
        "chrome-free",
        [("fake_schema_mismatch", "")],
        suite="mismatch",
        keeps_watch_prompt=True,  # nothing composes above it; there is no shell to host a prompt
    ),
    # Its consent button is below the fold on a 1220px-tall landscape screen, and this screen is the
    # one an owner cannot get past without pressing it — so the end of its scroll is load-bearing.
    Scene(
        "schema-mismatch-bottom",
        "chrome-free",
        [("fake_schema_mismatch", ""), ("swipe_end", "")],
        suite="mismatch",
        keeps_watch_prompt=True,
    ),
    # --- a wiped install: the wizard, which is chrome-free by design, and the empty states -----
    Scene("setup-bunny", "chrome-free", [("wipe", "")], suite="empty"),
    Scene("setup-backup", "chrome-free", [("wipe", ""), ("tap", "Skip for now")], suite="empty"),
    Scene(
        "setup-backup-scrolled",
        "chrome-free",
        [("wipe", ""), ("tap", "Skip for now"), ("swipe_up", "")],
        suite="empty",
    ),
    Scene(
        "setup-reminders",
        "chrome-free",
        [("wipe", ""), ("tap", "Skip for now"), ("tap", "Continue")],
        suite="empty",
    ),
    Scene(
        "home-empty",
        "tab",
        [("wipe", ""), ("tap", "Skip for now"), ("tap", "Continue"), ("tap", "Finish setup")],
        suite="empty",
    ),
    # --- top-level tabs: the shell's TopAppBar above and NavigationBar below -------------------
    Scene("home", "tab", [*SELECT_BUNNY]),
    Scene("weight", "tab", [*SELECT_BUNNY, ("tap", "Weight")]),
    Scene("weight-scrolled", "tab", [*SELECT_BUNNY, ("tap", "Weight"), ("swipe_up", "")]),
    Scene("observations", "tab", [*SELECT_BUNNY, ("tap", "Observations")]),
    Scene(
        "observations-scrolled",
        "tab",
        [*SELECT_BUNNY, ("tap", "Observations"), ("swipe_up", "")],
    ),
    Scene("care", "tab", [*SELECT_BUNNY, ("tap", "Care")]),
    Scene("more", "tab", [*SELECT_BUNNY, ("tap", "More")]),
    # --- detail routes: own TopAppBar with back, shell chrome hidden --------------------------
    Scene("settings", "detail", [("tap", "More"), ("tap", "Settings")]),
    Scene("settings-scrolled", "detail", [("tap", "More"), ("tap", "Settings"), ("swipe_up", "")]),
    Scene("backup", "detail", [("tap", "More"), ("tap", "Settings"), ("tap", "Backup")]),
    Scene(
        "backup-scrolled",
        "detail",
        [("tap", "More"), ("tap", "Settings"), ("tap", "Backup"), ("swipe_up", "")],
    ),
    Scene("archived", "detail", [("tap", "More"), ("tap", "Archived")]),
    Scene("care-reminder", "detail", [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Every")]),
    # Reached without SELECT_BUNNY on purpose: Support is the one More row that stays live with no
    # bunny in scope, and the scene is worth more exercising that path than the ordinary one.
    Scene("support", "detail", [("tap", "More"), ("tap", "Support")]),
    # --- full-bleed content, which has no inner padding of its own to hide behind -------------
    Scene("photos", "full-bleed", [*SELECT_BUNNY, ("tap", "More"), ("tap", "Photos")]),
    # --- forms: a TopAppBar with a save action, fields down to the bottom edge -----------------
    Scene("bunny-editor", "form", [*SELECT_BUNNY, ("tap", "Edit")]),
    Scene("bunny-editor-scrolled", "form", [*SELECT_BUNNY, ("tap", "Edit"), ("swipe_up", "")]),
    Scene("weight-entry", "form", [*SELECT_BUNNY, *OPEN_WEIGHT_FORM]),
    Scene("observation-entry", "form", [*SELECT_BUNNY, ("tap", "Record an observation")]),
    Scene(
        "observation-entry-scrolled",
        "form",
        [*SELECT_BUNNY, ("tap", "Record an observation"), ("swipe_up", "")],
    ),
    Scene("care-reminder-editor", "form", [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a reminder")]),
    # --- the IME, which is the case a still screen never shows --------------------------------
    Scene(
        "weight-entry-ime",
        "ime",
        [*SELECT_BUNNY, *OPEN_WEIGHT_FORM, ("tap", "Weight in grams"), ("wait", "1.5")],
        note="keyboard up; in landscape it eats most of the screen",
    ),
    Scene(
        "observation-entry-ime",
        "ime",
        [
            *SELECT_BUNNY,
            ("tap", "Record an observation"),
            ("swipe_up", ""),
            ("tap", "Anything else"),
            ("wait", "1.5"),
        ],
        note="the free-text note, which is the field nearest the bottom of that form",
    ),
    # --- the end of every scroll, which is the only position that proves anything --------------
    # A list running under the navigation bar mid-scroll is edge-to-edge working. A list whose last
    # row is still under it with nowhere left to scroll is the defect. Only these tell them apart.
    Scene("settings-bottom", "detail", [("tap", "More"), ("tap", "Settings"), ("swipe_end", "")]),
    # Owed because landscape puts the last three rows below the fold — checked by looking, not
    # assumed: at 1220px tall the screen ends on the address, leaving Rate, Privacy policy and the
    # version row unrendered, and the version row is the one that sits nearest the navigation bar.
    Scene("support-bottom", "detail", [("tap", "More"), ("tap", "Support"), ("swipe_end", "")]),
    Scene(
        "backup-bottom",
        "detail",
        [("tap", "More"), ("tap", "Settings"), ("tap", "Backup"), ("swipe_end", "")],
    ),
    Scene("weight-bottom", "tab", [*SELECT_BUNNY, ("tap", "Weight"), ("swipe_end", "")]),
    Scene(
        "observations-bottom",
        "tab",
        [*SELECT_BUNNY, ("tap", "Observations"), ("swipe_end", "")],
    ),
    Scene("care-bottom", "tab", [*SELECT_BUNNY, ("tap", "Care"), ("swipe_end", "")]),
    Scene("photos-bottom", "full-bleed", [*SELECT_BUNNY, ("tap", "More"), ("tap", "Photos"), ("swipe_end", "")]),
    Scene("bunny-editor-bottom", "form", [*SELECT_BUNNY, ("tap", "Edit"), ("swipe_end", "")]),
    Scene(
        "observation-entry-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Record an observation"), ("swipe_end", "")],
    ),
    # --- dialogs and sheets, drawn over content with their own inset behaviour -----------------
    # Run this one on its own, before the rest: answering it is what makes it go away.
    Scene("watch-expiry", "overlay", [], keeps_watch_prompt=True),
    Scene("switcher-menu", "overlay", [("tap", "Choose which bunny")]),
    Scene(
        "date-picker",
        "overlay",
        [*SELECT_BUNNY, *OPEN_WEIGHT_FORM, ("tap", "on the scale"), ("wait", "1.0")],
    ),
    Scene(
        "photo-add-menu",
        "overlay",
        [*SELECT_BUNNY, ("tap", "More"), ("tap", "Photos"), ("tap", "Add photos"), ("wait", "1.0")],
    ),
    # The app's only `ModalBottomSheet`, and the inset case a dialog does not cover: a sheet is
    # anchored to the bottom edge, which is where the navigation bar is. Reached through the debug
    # section of Settings, but it is ADR-0006's point-of-use reminders opt-in — the same composable
    # the wizard's third step hosts, so this and `setup-reminders` are two views of one screen.
    Scene(
        "reminders-sheet",
        "overlay",
        [
            ("tap", "More"),
            ("tap", "Settings"),
            ("swipe_end", ""),
            ("tap", "Reminder settings"),
            ("wait", "1.5"),
        ],
    ),
    # The sheet's own end of scroll. Its content is taller than any phone screen, so the opening
    # frame necessarily has text passing under the navigation bar — that is a list scrolling, not a
    # defect. What had to be proved is that the end exists and clears the bar.
    Scene(
        "reminders-sheet-bottom",
        "overlay",
        [
            ("tap", "More"),
            ("tap", "Settings"),
            ("swipe_end", ""),
            ("tap", "Reminder settings"),
            ("wait", "1.5"),
            ("swipe_end", ""),
        ],
    ),
    # --- Phase 5: medications, visits, vets and documents (PLAN 5i) ---------------------------
    # The Care tab gained two whole sections above and below the reminders it used to be, so its
    # middle is now content no earlier capture ever saw: `care` shows the medications at the top and
    # `care-bottom` the end of the visits, and this is the reminders that used to be the whole tab.
    Scene("care-scrolled", "tab", [*SELECT_BUNNY, ("tap", "Care"), ("swipe_up", "")]),
    # The dose history is the longest list the app builds — one row per dose per day — so its end is
    # the scroll most likely to leave a row under the navigation bar.
    Scene("medication-course", "detail", [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Open")]),
    Scene(
        "medication-course-bottom",
        "detail",
        [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Open"), ("swipe_end", "")],
    ),
    Scene("course-editor", "form", [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a course")]),
    # The times list grows downwards from a button, so the save action and the last time added are
    # the two controls competing for the bottom edge.
    Scene(
        "course-editor-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a course"), ("swipe_end", "")],
    ),
    Scene(
        "course-editor-ime",
        "ime",
        [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a course"), ("tap", "What is it?"), ("wait", "1.5")],
        note="the first field of the phase's longest form; in landscape the IME leaves two rows",
    ),
    Scene("visit-editor", "form", [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a visit")]),
    Scene(
        "visit-editor-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Care"), ("tap", "Add a visit"), ("swipe_end", "")],
    ),
    Scene("vets", "detail", [("tap", "More"), ("tap", "Vets")]),
    Scene("vet-editor", "form", [("tap", "More"), ("tap", "Vets"), ("tap", "Add a vet")]),
    # Documents are a grid of scanned pages with no chrome of their own, which is the photo gallery's
    # inset problem on content the owner cannot re-take.
    Scene("documents", "detail", [*SELECT_BUNNY, ("tap", "More"), ("tap", "Documents")]),
    Scene(
        "document-viewer",
        "full-bleed",
        [*SELECT_BUNNY, ("tap", "More"), ("tap", "Documents"), ("tap", "Vaccination record")],
        note="a page drawn to every edge, with the page counter over it",
    ),
    # A dialog and a menu, which is the overlay pair this phase adds: one anchored centrally, one to
    # the top bar's action.
    Scene(
        "record-dose",
        "overlay",
        [
            *SELECT_BUNNY,
            ("tap", "Care"),
            ("tap", "Open"),
            ("tap", "Record a dose"),
            ("wait", "1.0"),
        ],
    ),
    Scene(
        "document-actions",
        "overlay",
        [
            *SELECT_BUNNY,
            ("tap", "More"),
            ("tap", "Documents"),
            ("tap", "Vaccination record"),
            ("tap", "Document actions"),
            ("wait", "1.0"),
        ],
    ),
    # The empty states this phase adds, on the same wiped install as the wizard: three sections that
    # each have to say "nothing here yet" without implying anything is wrong (ADR-0001).
    Scene(
        "care-empty",
        "tab",
        [
            ("wipe", ""),
            ("tap", "Skip for now"),
            ("tap", "Continue"),
            ("tap", "Finish setup"),
            ("tap", "Care"),
        ],
        suite="empty",
    ),
    # Last, because it leaves the persisted selection on "All bunnies" rather than a bunny.
    Scene(
        "home-all-bunnies",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "All bunnies")],
    ),
]


# --------------------------------------------------------------------------------------------
# The check
# --------------------------------------------------------------------------------------------

# A node the app owns that carries a label or takes a tap. Backgrounds are not semantics nodes, so
# anything reported here is content, and content is what must not sit under a system bar.
def content_nodes(nodes: list[Node]) -> list[Node]:
    return [
        node
        for node in nodes
        if node.package == PACKAGE
        and node.bounds.area > 0
        and (node.text or node.desc or node.clickable)
    ]


def check(nodes: list[Node], insets: dict[str, Rect]) -> list[dict]:
    """Every app-owned label or control that lands inside a system inset, in two tiers.

    The tiers exist because the rectangles Compose publishes to accessibility are **touch** bounds,
    not drawn bounds: `Modifier.minimumInteractiveComponentSize` grows a small control's hit area to
    48dp, and the result is not clipped to the scroll viewport it lives in. So an unlabelled
    clickable node poking into the navigation bar is routinely an artifact of that expansion, while
    a node carrying *text* is something a person can actually read in the wrong place.

    - `drawn`: the node has a label, so there is something legible under a system bar.
    - `touch`: the node has none, so this is a hit area, and only its at-rest position tells you
      whether it is a defect — which is what the `-bottom` scenes are for.
    """
    findings = []
    for node in content_nodes(nodes):
        for kind, rect in insets.items():
            overlap = node.bounds.overlap(rect)
            if overlap is None or overlap.area == 0:
                continue
            # A modal scrim swallows a system bar whole — covering everything is what makes it
            # modal, and the dismiss target is *supposed* to reach under the bars. Content never
            # does: a row that runs under the navigation bar overlaps part of it, not all of it.
            # Matched on that shape rather than on the scrim's label, which is a translated
            # Material string ("Close sheet") and would stop matching in Polish.
            if overlap == rect:
                continue
            findings.append(
                {
                    "tier": "drawn" if (node.text or node.desc) else "touch",
                    "inset": kind,
                    "label": node.label[:60],
                    "class": node.cls.rsplit(".", 1)[-1],
                    "clickable": node.clickable,
                    "bounds": node.bounds.as_list(),
                    "overlap": overlap.as_list(),
                    "overlap_px": overlap.area,
                }
            )
    return findings


def run_scene(scene: Scene, config: Config, out_dir: Path) -> dict:
    relaunch()
    try:
        if not scene.keeps_watch_prompt:
            # The prompt is hosted above the shell and so composes a beat after it; asking before
            # that is asking too early, and an optional tap does not wait around to be told no.
            settle(1.2)
            tap("Close it", optional=True)
        for kind, arg in scene.steps:
            STEP_RUNNERS[kind](arg)
    except StepFailed as error:
        return {"scene": scene.name, "family": scene.family, "error": str(error)}

    settle(0.8)
    shot = out_dir / f"{scene.name}.png"
    shot.write_bytes(adb("exec-out", "screencap", "-p", binary=True))

    insets = read_insets()
    findings = check(dump_ui(), insets)
    return {
        "scene": scene.name,
        "family": scene.family,
        "note": scene.note,
        "screenshot": str(shot.relative_to(out_dir.parent)),
        "insets": {kind: rect.as_list() for kind, rect in insets.items()},
        "findings": findings,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, help="directory for screenshots and the report")
    parser.add_argument("--config", help="comma-separated config names, default all")
    parser.add_argument("--scene", help="comma-separated scene names, default all in the suite")
    parser.add_argument(
        "--suite",
        default="full",
        choices=["full", "empty", "mismatch"],
        help=(
            "'full' needs the debug sample data seeded; 'empty' WIPES the app to reach the wizard; "
            "'mismatch' corrupts the database's version field and puts it back afterwards"
        ),
    )
    parser.add_argument("--restore", action="store_true", help="undo the pinned rotation and nav mode")
    args = parser.parse_args()

    if args.restore:
        restore_device()
        print("rotation and navigation mode handed back to the phone")
        return 0

    if not args.out:
        parser.error("--out is required unless --restore")

    configs = CONFIGS
    if args.config:
        wanted = set(args.config.split(","))
        configs = [config for config in CONFIGS if config.name in wanted]
    scenes = [scene for scene in SCENES if scene.suite == args.suite]
    if args.scene:
        wanted = set(args.scene.split(","))
        scenes = [scene for scene in SCENES if scene.name in wanted]

    report = {"configs": []}
    try:
        run_matrix(report, configs, scenes, args.out)
    finally:
        # The mismatch suite leaves a deliberately corrupted database behind, so putting it back is
        # the one piece of cleanup that must happen even when a scene throws — a run that crashed
        # halfway is exactly when an app left unopenable is hardest to explain.
        if args.suite == "mismatch":
            restore_schema_version()

    report_path = args.out / f"report-{args.suite}.json"
    report_path.write_text(json.dumps(report, indent=2))
    print(f"\nreport: {report_path}")
    return 0


def run_matrix(report: dict, configs: list[Config], scenes: list[Scene], out: Path) -> None:
    for config in configs:
        apply_config(config)
        out_dir = out / config.name
        out_dir.mkdir(parents=True, exist_ok=True)
        # The app has to be in front before the insets mean anything: `user_rotation` only rotates
        # an app that permits it, and the launcher is portrait-locked, so reading them with the
        # launcher on screen reports portrait geometry for a landscape cell. Each scene re-reads
        # them for itself; this is only so the header is not a lie.
        relaunch()
        insets = read_insets()
        print(f"\n=== {config.name}  insets={ {k: v.as_list() for k, v in insets.items()} }")
        results = []
        for scene in scenes:
            result = run_scene(scene, config, out_dir)
            results.append(result)
            if "error" in result:
                print(f"  {scene.name:28s} SKIPPED  {result['error'][:90]}")
            else:
                hits = result["findings"]
                drawn = [hit for hit in hits if hit["tier"] == "drawn"]
                touch = [hit for hit in hits if hit["tier"] == "touch"]
                mark = "clean" if not hits else f"drawn={len(drawn)} touch={len(touch)} " + str(
                    sorted({hit["inset"] for hit in hits})
                )
                print(f"  {scene.name:28s} {mark}")
        report["configs"].append(
            {
                "config": config.name,
                "insets": {kind: rect.as_list() for kind, rect in insets.items()},
                "scenes": results,
            }
        )


if __name__ == "__main__":
    sys.exit(main())
