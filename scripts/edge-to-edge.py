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
    scripts/edge-to-edge.py --out DIR --locale pl         # the same walk, in Polish
    scripts/edge-to-edge.py --restore                     # hand the phone back to auto-rotate

The phone is left in whatever configuration the last cell used; `--restore` puts it back.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from xml.etree import ElementTree

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


# The config the phone is currently pinned to, so a wipe can put the rotation back. `pm clear` kills
# the app, which hands the foreground to the portrait-locked launcher, and HyperOS writes
# `user_rotation` back to 0 when it does — silently turning a landscape cell into a second portrait
# one that still captures, still checks, and still reports "clean".
_PINNED: "Config | None" = None


def apply_config(config: Config) -> None:
    """Pin the rotation and the navigation mode, then wait for the window to settle.

    HyperOS does not use the AOSP `com.android.internal.systemui.navbar.*` overlays — they are all
    present and all disabled — so the navigation mode is flipped through MIUI's own
    `force_fsg_nav_bar` global instead. Verified against the navigation bar's reported inset, which
    is the thing being tested and cannot be faked by the setting alone.
    """
    global _PINNED
    _PINNED = config
    shell("settings put system accelerometer_rotation 0")
    shell(f"settings put system user_rotation {config.rotation}")
    shell(f"settings put global force_fsg_nav_bar {1 if config.nav == 'gesture' else 0}")
    # SystemUI rebuilds the navigation bar out of process; there is nothing to poll that is ready
    # before the new inset is published, so this is a wait rather than a check.
    settle(3.0)


def restore_device() -> None:
    shell("settings put global force_fsg_nav_bar 1")
    shell("settings put system accelerometer_rotation 1")
    set_dnd(False)


def set_dnd(on: bool) -> None:
    """Silence heads-up notifications for the run, and hand them back afterwards.

    **Setup and teardown, not a note in a document.** [reset_to_seeded] recreates the Metacam course
    whose 20:00 dose is minutes in the past, so a heads-up banner posts a minute or so after *every*
    seed — over Home, exactly where `SELECT_BUNNY` taps. The tap opens the course instead, and
    `AUTO_CANCEL` clears the banner on the way, so the evidence afterwards looks impossible: a scene
    that walked somewhere nobody asked it to, and no notification anywhere to explain it. Two runs
    were wrecked and a third crippled before it was pinned.

    `set_dnd` and not a revoked `POST_NOTIFICATIONS`: the scenes photograph reminder copy, and an
    app that cannot post notifications draws a blocked-state banner instead — which would make the
    screenshots lie about a different thing. Zen suppresses the *presentation* and leaves the app's
    own state alone (verified: `zen_mode` reads 2 with it on and 0 with it off).

    It is **phone-wide**, which is why every caller's `off` belongs in a `finally`. A crashed run
    must not leave somebody's phone silent.
    """
    shell(f"cmd notification set_dnd {'on' if on else 'off'}")
    settle(0.5)


# The app language every wipe has to put back, since `pm clear` drops it. None is the device default.
_LOCALE: "str | None" = None


def set_locale(locale: str | None) -> None:
    """Pin the app's language, or hand it back to the device default.

    Per-app locales are system state keyed by package, which is why this is `cmd locale` and not the
    in-app Settings picker: the same mechanism the picker drives, reachable without eight taps.

    **It does not survive `pm clear`**, so the chosen locale is remembered here and re-applied by
    [wipe] — the one place this driver clears the app. Doing it there rather than at the top of each
    suite is what keeps the `empty` suite honest: its scenes wipe as their *first step*, so a locale
    applied only once per cell would shoot the setup wizard in English inside a Polish run.
    """
    global _LOCALE
    _LOCALE = locale
    if locale is None:
        shell(f"cmd locale set-app-locales {PACKAGE} --locales")
    else:
        shell(f"cmd locale set-app-locales {PACKAGE} --locales {locale}")
    settle(1.5)


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
    # Compose publishes `Modifier.selectable`'s state as the accessibility node's `isSelected`, and
    # the navigation bar is the only place this app uses it — so exactly one node in a top-level
    # dump carries it, and it is the current tab. Verified in a dump rather than assumed: the item
    # itself carries no text (the label is a child `TextView`), which is why [showing_home] compares
    # rectangles instead of reading it off the labelled node.
    selected: bool = False

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
        # **Unescape, because this reads XML with a regex and the labels are English prose.** The
        # dump writes `Care &amp; Meds`, so a needle spelled with a real ampersand — `"Care & Meds"`,
        # `"Backup & restore"` — matched nothing at all, and those two sit at the head of roughly
        # twenty scenes. Found 2026-08-16 on the first English cell run since the needles were
        # lengthened on 2026-08-14; **the 146/146 Polish run could not see it**, because *"Opieka i
        # leki"* and *"Kopia zapasowa i przywracanie"* carry no ampersand. Third defect this phase that is
        # unreachable in one locale and fatal in the other, and the first one that English is the
        # broken half of.
        attrs = {name: html.unescape(value) for name, value in ATTR_RE.findall(match.group(1))}
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
                selected=attrs.get("selected") == "true",
            )
        )
    return nodes


def screen_signature(nodes: list[Node]) -> str:
    """What the app's screen looks like, as a string, for "did anything happen?" comparisons."""
    return "|".join(f"{n.label}{n.bounds.as_list()}" for n in nodes if n.package == PACKAGE)


# What each English needle means in the locale under capture, filled in by [resolve_needles] before
# the first tap and empty for an English run — where every lookup is the identity.
_TRANSLATED: dict[str, str] = {}


def find(nodes: list[Node], needle: str, *, text_only: bool = False) -> Node | None:
    """The smallest node whose text or description contains `needle`, case-insensitively.

    Smallest, because Compose reports a merged semantics node for a whole row as well as the leaf
    inside it, and the leaf is the one whose centre is unambiguously on the thing named.

    `text_only` drops content descriptions from the match, which is how a **label** is told apart
    from an **icon that carries the same words**. The "+" describes itself as *Record an
    observation* and so does one row of the sheet it opens (Phase 7.5 §6 reuses the string rather
    than spending a new one in nine languages), and the FAB is the smaller of the two — so a plain
    needle would tap the button that is already open and dismiss the sheet on the scrim. The row has
    text and no description; the FAB has a description and no text. Structure, not copy, again.

    **Every needle is translated here**, which is the one place it can be: `tap`, `return_to_home`
    and `showing_home` all arrive through this function, so a locale run needs no second table and
    no scene rewritten. See [resolve_needles].
    """
    needle = _TRANSLATED.get(needle, needle).casefold()
    matches = [
        node
        for node in nodes
        if node.package == PACKAGE
        and (needle in node.text.casefold() or (not text_only and needle in node.desc.casefold()))
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

    **`force-stop` is not enough on its own, and the gap cost a whole cell.** It kills the process but
    leaves the task record, so Android may restore the saved-instance bundle on the next `am start` —
    the app comes back on whatever screen it was last on rather than at Home. One stray tap is then
    permanent: on 2026-08-12 a dose notification posted mid-run (`importance=4`, two actions, drawn
    over Home exactly where `SELECT_BUNNY` taps), the tap landed on the banner instead of the app, and
    every scene afterwards relaunched into the record-dose screen and failed on a needle that was
    never wrong. `-S` force-stops, and `0x10008000` is `FLAG_ACTIVITY_CLEAR_TASK | NEW_TASK`, which
    drops the record the restore reads from. **A scene must not be able to inherit the last one's
    screen** — that is what makes 61 scenes independent rather than a sequence.

    **Everything above is what the intent *asks* for; [return_to_home] is what checks it.** Measured
    on 2026-08-14, from a detail route (More → Settings) and from a top-level tab that is not Home
    (Weight): both relaunches landed on Home with the tab bar up, so on this phone the flags do
    clear the restored Nav3 stack. That makes the step after this one a verification rather than a
    repair — which is worth one dump per scene, because the alternative is trusting an argument.
    """
    shell(f"am start -S -n {ACTIVITY} -f 0x10008000")
    wait_for_app()


# The two needles the isolation step is built on, both from the shell rather than from any screen.
# `switcher_open` is drawn inside the TopAppBar the shell shows only while `onDetailScreen` is false
# (Navigation.kt), so it is exactly the question "is a top-level tab on screen?" — and `Home` is the
# navigation bar's first item. Naming them here rather than inline is what lets the locale work
# translate them with the rest of the table.
TAB_BAR = "Choose which bunny to show"
HOME_TAB = "Home"

# How many times [return_to_home] may press back before it gives up. Six covers the deepest route in
# the app (More → Settings → Backup, plus a dialog and an IME) with room to spare; it is a bound
# rather than a target, and hitting it is a failure and not a retry budget.
BACK_BOUND = 6


def showing_home(nodes: list[Node]) -> bool:
    """Is the *Home* tab the selected one, rather than merely some top-level tab?

    The distinction is the whole reason this exists. A restored back stack does not have to be a
    detail route — it can be the Observations tab, which has the tab bar, passes every "are we at
    the shell?" check, and is still the wrong screen for `home`, `home-bottom` and every scene that
    taps *Edit* on a profile card.

    The selected navigation item carries no text of its own, so this is geometry: the one node in
    the dump with `selected="true"` is the current tab, and the *Home* label is inside it exactly
    when Home is that tab.
    """
    label = find(nodes, HOME_TAB)
    if label is None:
        return False
    selected = next((node for node in nodes if node.package == PACKAGE and node.selected), None)
    return selected is not None and selected.bounds.intersects(label.bounds)


def return_to_home() -> None:
    """Put the app on the Home tab, or fail the scene saying so.

    **What it found, which is not what it was written to fix.** Phase 7 left this owed on the
    reading that `am start -S -f 0x10008000` does not clear a restored Nav3 back stack. Checked
    directly on 2026-08-14 — walk to More → Settings, relaunch; walk to the Weight tab, relaunch —
    and **both came back on Home**. So the flags do their job on this phone, and the 2026-08-12 cell
    that relaunched into the record-dose screen over and over is better explained by the banner than
    by the stack: the missed 20:00 dose re-arms at process start (ADR-0025's self-heal), fires
    immediately because it is already past, and posts a fresh heads-up over *every* scene rather
    than poisoning one. [set_dnd] is the fix for that, and this is the check that says so — it
    prints when it has to correct anything, so a run that never prints is evidence about the
    relaunch and not merely the absence of a complaint.

    **`KEYCODE_BACK` alone is not the fix**, which is why this is bounded and checks first. Backing
    past Home exits to the launcher and makes every following scene worse, so the loop presses back
    only while the shell is *not* on screen, and stops the moment it is.

    Failing loudly is the other half. A driver that cannot find Home must not tap into whatever is
    open — that produces a screenshot of the wrong screen under the right name, which is worse than
    a skipped scene because it is evidence that looks like evidence.
    """
    for _ in range(BACK_BOUND + 1):
        nodes = dump_ui()
        if find(nodes, TAB_BAR) is not None:
            break
        back()
    else:
        visible = ", ".join(sorted({n.label for n in dump_ui() if n.package == PACKAGE and n.label})[:20])
        raise StepFailed(f"never reached a top-level tab in {BACK_BOUND} backs; on screen: {visible}")

    if not showing_home(nodes):
        # Only paid when it is actually owed. `tap` verifies by asking the screen whether anything
        # moved, and on the common path — already at Home — that is three taps and three dumps for
        # nothing, which is minutes across a four-cell matrix.
        print("     (restored off Home; corrected)")
        tap(HOME_TAB)


# How far [tap] will scroll looking for a needle, and how many dumps it takes before an unchanged
# screen is believed. The cap matches [swipe_to_end]'s for the same reason — the observation
# timeline holds a year of rows — and the floor is the composition grace the old fixed budget was
# really providing.
TAP_SCROLL_CAP = 16
MIN_TAP_TRIES = 4


def tap(needle: str, *, optional: bool = False, text_only: bool = False) -> None:
    # Retried rather than waited on a fixed delay: a screen that is still composing dumps as an
    # empty ComposeView, and how long that takes depends on the screen, not on the driver. An
    # *optional* tap is not retried — it is asking whether something is there, and four rounds of
    # waiting to be told "no" is most of the run time of the matrix.
    # **Scroll while the screen is still moving, rather than a fixed number of times.** A landscape
    # swipe covers 70%→32% of 1220px — about 464px, against roughly 1030px of a portrait screen — so
    # a budget of four tries reaches ~1856px sideways where it reaches ~4120px upright. The Care
    # screen is several thousand pixels long in landscape (it shows two and a half rows at a time),
    # which is how `visit-editor`, `weight-entry` and `home-crowded-all` came back unreachable on
    # 2026-08-16 for controls that are plainly reachable: `care-bottom` scrolls clean past them to
    # the banner at the end. **A fixed budget is a portrait-shaped constant**, the same shape of bug
    # as the rotation a wipe used to cost, and it fails only where the viewport is short.
    #
    # The signature check is what keeps this from being slower: a screen that cannot scroll stops
    # changing after one swipe and gives up sooner than the old budget did. [MIN_TAP_TRIES] is the
    # floor underneath it, because a screen still composing dumps as an empty `ComposeView` and two
    # identical empty dumps must not be read as "nowhere left to go".
    attempts = 1 if optional else TAP_SCROLL_CAP
    node = None
    previous = ""
    for attempt in range(attempts):
        nodes = dump_ui()
        node = find(nodes, needle, text_only=text_only)
        if node is not None:
            break
        if optional:
            settle(1.0)
            continue
        current = screen_signature(nodes)
        if attempt >= MIN_TAP_TRIES - 1 and current == previous:
            break
        previous = current
        swipe_up()
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


def tap_field(index: str) -> None:
    """Tap the *n*-th editable field on screen, top to bottom. The IME scenes' way in.

    **A needle cannot name a field that has no text.** The redesign's hero grams field carries no
    label and no placeholder — the number is the whole control — so the only string containing
    "Weight in grams" is the help line *underneath* it, which is a plain `Text`. Tapping it focuses
    nothing, and `weight-entry-ime` has been shooting a form with no keyboard ever since: the same
    trap `observation-entry-ime` and `course-editor-ime` were caught in, and the one nobody spotted
    because a scene with no IME still produces a perfectly good screenshot.

    Structure rather than copy is the fix, and it is the right one twice over: an `EditText` is an
    `EditText` in every language, so this is the one needle a locale run cannot break.
    """
    wanted = int(index)
    fields = sorted(
        (node for node in dump_ui() if node.package == PACKAGE and node.cls.endswith("EditText")),
        key=lambda node: (node.bounds.top, node.bounds.left),
    )
    if wanted >= len(fields):
        raise StepFailed(f"asked for editable field {wanted}, found {len(fields)}")
    node = fields[wanted]
    shell(f"input touchscreen tap {(node.bounds.left + node.bounds.right) // 2} "
          f"{(node.bounds.top + node.bounds.bottom) // 2}")
    settle(1.2)


def type_text(value: str) -> None:
    shell(f"input text {value}")
    settle(1.2)


def wipe() -> None:
    """Back to a first run: no preferences, no database, so the setup wizard shows.

    Destructive, and deliberately only reachable from the `empty` suite — the `full` suite runs
    against seeded sample data and a wipe in the middle of it would quietly capture empty screens
    under populated names.
    """
    global _SEEDED
    _SEEDED = None
    shell(f"pm clear {PACKAGE}")
    settle(1.0)
    # Before the launch, not after: `pm clear` drops the per-app locale, and re-applying it to a
    # package that is not running costs nothing, where doing it afterwards restarts the activity.
    if _LOCALE is not None:
        shell(f"cmd locale set-app-locales {PACKAGE} --locales {_LOCALE}")
    shell(f"am start -n {ACTIVITY}")
    wait_for_app()
    # Re-pin the rotation the wipe just cost us. Only the rotation: the navigation mode is a global
    # and survives, and re-writing it would buy another 3s settle per wiping scene for nothing.
    # Verified by the failure it exists to stop — `mRotation=ROTATION_0` and 1220x2712 PNGs in a
    # cell named `landscape-gesture`.
    if _PINNED is not None:
        shell("settings put system accelerometer_rotation 0")
        shell(f"settings put system user_rotation {_PINNED.rotation}")
        settle(1.5)


def reset_to_seeded() -> None:
    """Wipe, skip the wizard, and seed the debug sample data — a known state, not just a clean one.

    The inverse of [wipe], and the step this file has been missing: the `empty` suite ends with the
    install wiped, so a matrix run leaves the phone with no sample data and no media directories at
    all. That is exactly what DOD §1 recorded after the 5 Aug run, where the wipe took the armed
    medication course with it and nothing put it back.

    It matters more than tidiness for repeat runs. `seedWatches` back-dates `startedAt`, so a fresh
    seed restores the *unanswered* watch-expiry prompt — and answering that prompt is permanent, so
    without this a second capture cell would find it already gone and quietly shoot the wrong screen.
    """
    wipe()
    for label in SEED_WALK:
        # `tap` scrolls when it cannot find its target, which is what reaches *Add the sample data*:
        # the sample-data block is the debug-only tail of a scrolling Settings column.
        tap(label)
    settle(1.0)

    # Last, because `pm grant` can kill the process and everything above is a tap sequence that
    # would die with it. Every caller relaunches before its next screenshot, so the app picks the
    # permission up cleanly.
    #
    # `pm clear` revokes POST_NOTIFICATIONS, and a denied notification permission is *visible*: the
    # Care screen grows a "notifications are off" banner whose button is labelled `action_open` —
    # the same "Open" the medication-course row uses. `tap("Open")` then matches the banner first
    # and launches HyperOS's notification settings, so `medication-course` screenshotted the system
    # Settings app and `record-dose` failed with an empty node list, the foreground no longer being
    # this package. Granting it back is also the honest state: a seeded install stands in for an app
    # in use, not for one whose permission was just refused. The `empty` suite is the deliberate
    # exception and keeps the denied state, because there it is the truth of a first run.
    shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS")
    settle(0.5)
    global _SEEDED
    _SEEDED = ""


# Which seed the phone is currently carrying: "" for the plain sample data, a variant name for one
# of [SeedVariantReceiver]'s, and None for "unknown" — which is what a wipe leaves and what the
# start of every cell asserts, so the first scene of a cell always reseeds exactly as it always did.
_SEEDED: "str | None" = None

SEED_RECEIVER = f"{PACKAGE}/app.binky.tracker.debug.SeedVariantReceiver"


def seed_variant(variant: str) -> None:
    """Add a variant on top of the sample data, through the debug build's own receiver.

    **The default seed is never changed, and that is the constraint rather than a nicety.** Sixty-one
    scenes, the before/after comparison and the Play listing screenshots all rest on it, so a third
    bunny or a repurposed series would move evidence that is already banked. A variant is additive
    and asked for by name.

    `-f 0x00000020` is `FLAG_INCLUDE_STOPPED_PACKAGES`: a package is in the stopped state after
    `pm clear` until something launches it, and a broadcast to a stopped package is dropped in
    silence — which would look exactly like a variant that seeded nothing.

    The receiver reports through the broadcast result, so this can fail on what actually happened
    rather than on a timeout: `result=0` and the data string it set, or a loud failure naming the
    exception. See [SeedVariantReceiver] for why it is a broadcast at all.
    """
    global _SEEDED
    output = shell(f"am broadcast -n {SEED_RECEIVER} -f 0x00000020 --es variant {variant}")
    if "result=0" not in output:
        raise StepFailed(f"seeding variant {variant!r} failed: {output.strip()[:200]}")
    _SEEDED = variant
    settle(1.0)


# Whether every scene runs with a dose reminder trying to post over it. Off unless `--live-dose`
# asks for it, because it changes what every screenshot contains — see [arm_live_dose].
_LIVE_DOSE = False


def arm_live_dose() -> None:
    """Put an unanswered dose slot a minute in the past, so the reminder banner posts over this scene.

    **The hazard this reproduces is the one that wrecked two runs and crippled a third.** A missed
    dose is re-armed at every process start (ADR-0025's self-heal), fires immediately because its
    trigger is behind us, and posts an `importance=4` heads-up over Home — exactly where
    `SELECT_BUNNY` taps. The tap lands on the banner, `AUTO_CANCEL` clears it, and every scene after
    that relaunches into the record-dose screen and fails on a needle that was never wrong. That is
    the 2026-08-12 cell, and [set_dnd] is the fix; **this is what makes a clean run mean anything**,
    because DND suppressing a banner nobody posted is not evidence of DND.

    **Why per scene rather than per seed.** The default seed's live dose is Metacam's 20:00, so an
    evening run faces this for real — but `DOSE_GRACE` is thirty minutes and a cell is closer to
    fifty, so the second half of every cell would be quiet, and a reseed happens only a handful of
    times per cell. Re-arming per scene keeps the banner live end to end, and it makes the case
    reachable at any hour: Phase 8 runs nine locales, and nine evenings is not a plan.

    Idempotent on the app's side — one course, its single time replaced — so this can run 146 times
    without accumulating anything. See [SeedVariantReceiver.seedDueDose].
    """
    output = shell(f"am broadcast -n {SEED_RECEIVER} -f 0x00000020 --es variant due_dose")
    if "result=0" not in output:
        raise StepFailed(f"arming the live dose failed: {output.strip()[:200]}")
    # Long enough for AlarmManager to deliver a trigger that is already in the past and for the
    # notification to be posted, so the scene walks *into* the banner rather than ahead of it.
    settle(1.0)


def invalidate_seed() -> None:
    """Forget what the phone is carrying, so the next [ensure_seed] reseeds whatever it asks for."""
    global _SEEDED
    _SEEDED = None


def ensure_seed(variant: str) -> None:
    """Put the phone on the seed this scene asked for, reseeding only when it is not already there.

    Reseeding costs a wipe, a wizard and the sample data — the better part of a minute — so scenes
    are sorted by the seed they want and this is a no-op for every scene but the first of each
    group. Variant scenes therefore cost one reseed each per cell rather than one per scene.
    """
    if _SEEDED == variant:
        return
    print(f"  -- seeding{f' + {variant}' if variant else ''}")
    reset_to_seeded()
    if variant:
        seed_variant(variant)


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
    # Tap a **label**, never an icon's description — see [find]. The one place it is needed so far
    # is the sheet behind the "+", whose row and whose FAB deliberately say the same words.
    "tap_text": lambda arg: tap(arg, text_only=True),
    "back": lambda arg: back(),
    "swipe_up": lambda arg: swipe_up(),
    "swipe_end": lambda arg: swipe_to_end(),
    "tap_field": tap_field,
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
    # The seed this scene needs, "" being the plain sample data. Anything else is a variant name the
    # debug build's SeedVariantReceiver knows, added *on top of* the sample data — the default seed
    # is never edited, because 61 scenes and the listing screenshots rest on it. Scenes are grouped
    # by this, so a variant costs one reseed per cell rather than one per scene.
    seed: str = ""


# The app-wide bunny selection is a preference, so it survives the relaunch each scene starts with
# — which makes it shared state between scenes rather than something each one sets. Every scene that
# needs a bunny in scope says so, and pays two taps for the privilege of not depending on its
# neighbours. "Bijou" is the debug sample data's first bunny.
SELECT_BUNNY = [("tap", "Choose which bunny"), ("tap", "Bijou")]

# The weight form, reached through the weigh-in care reminder rather than the Weight tab's own
# button. Same screen either way, and this route is the one that survives landscape: the Weight tab
# puts a year of weighings below that button, so in a 1220px-tall viewport finding it means paging
# through the list, while Care's rows sit on a screen with five of them on it.
#
# **Via the reminder's own screen since Phase 7's `3a`, and that is a fix rather than an extra tap.**
# The Care list used to carry *Record a weight* on every weigh-in row; the redrawn 64dp row carries
# it only while the reminder is actually *due*, because a row that is telling you a date in November
# carries a chevron instead. Whether the seeded weigh-in is due on the day the matrix runs depends on
# the latest seeded weighing — so the old needle was a coin flip, and the button on the reminder's
# own screen is always there. Same lesson as MEDICATION_COURSE below: name the thing you mean.
OPEN_WEIGHT_FORM = [("tap", "Care & Meds"), ("tap", "Weigh-in"), ("tap", "Record a weight")]

# The full observation form, which is **two taps now rather than one**: the "+" opens a chooser
# (Phase 7.5 §6) and the long form is one row of it, beside the one-tap healthy day. The second tap
# is `tap_text` because that row and the FAB above it deliberately carry the same words — reusing
# the string is what keeps this change free in nine languages — and the FAB is the smaller node, so
# a plain needle would tap it again and dismiss the sheet on its own scrim. See [find].
OPEN_OBSERVATION_FORM = [("tap", "Record an observation"), ("tap_text", "Record an observation")]

# The medication course is opened **by name**, never by its `Open` button, and that is a fix rather
# than a style choice. `find` is a case-insensitive substring match, and the Care screen grows a
# blocked-state banner for each of two permissions — notifications off, and exact alarms not
# permitted — whose buttons are both `action_open`, the same word. Tapping "Open" matched a banner
# and launched HyperOS's Settings, so `medication-course` screenshotted the system Settings app and
# `record-dose` failed with an empty node list because the foreground had left this package.
#
# Granting both permissions would also clear it, and is the wrong lever: `SCHEDULE_EXACT_ALARM` is
# denied by default on Android 14+, so the banner is a state real users genuinely see, and DOD §1
# wants that permission granted through the app's own deep link because that path is under test.
# A needle that names the thing it means is robust to all of it. "Metacam" is the sample data's
# first course (`SampleData.kt`), alongside "Panacur" and "Recovery food".
MEDICATION_COURSE = "Metacam"

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
    Scene("care", "tab", [*SELECT_BUNNY, ("tap", "Care & Meds")]),
    Scene("more", "tab", [*SELECT_BUNNY, ("tap", "More")]),
    # --- detail routes: own TopAppBar with back, shell chrome hidden --------------------------
    Scene("settings", "detail", [("tap", "More"), ("tap", "Settings")]),
    Scene("settings-scrolled", "detail", [("tap", "More"), ("tap", "Settings"), ("swipe_up", "")]),
    # The language picker, which is also the translation-report path: seven of the nine languages
    # ship without a native read-through, so this dialog is the mechanism that replaces one. It is
    # the one scene that must be legible in *every* locale, since an owner who has landed somewhere
    # they cannot read is exactly who needs it — which is why the needle is the row rather than the
    # dialog title, the two being the same string.
    Scene(
        "language-picker",
        "detail",
        [("tap", "More"), ("tap", "Settings"), ("tap_text", "Language")],
    ),
    Scene("backup", "detail", [("tap", "More"), ("tap", "Settings"), ("tap", "Backup & restore")]),
    Scene(
        "backup-scrolled",
        "detail",
        [("tap", "More"), ("tap", "Settings"), ("tap", "Backup & restore"), ("swipe_up", "")],
    ),
    Scene("archived", "detail", [("tap", "More"), ("tap", "Archived bunnies")]),
    # By name since Phase 7's `3a`, not by "Every". `find` returns the *smallest* matching node, and
    # a course row and a reminder row are now both exactly 64dp of full-width merged semantics — an
    # exact tie, broken by list order, which puts the course first. "Every" also appears in a course's
    # own schedule line ("…every day"). "Nail trim" is Bijou's first seeded reminder and names only
    # itself.
    Scene("care-reminder", "detail", [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", "Nail trim")]),
    # Reached without SELECT_BUNNY on purpose: Support is the one More row that stays live with no
    # bunny in scope, and the scene is worth more exercising that path than the ordinary one.
    Scene("support", "detail", [("tap", "More"), ("tap", "Support")]),
    # Attribution (Phase 7.5 §3). "Open-source licences" is deliberately two resources with one
    # value — the Support row and the screen it opens — which the resolver reports as a needle
    # naming several strings and then resolves anyway, because both translate to the same words.
    # That is the benign half of the ambiguity check working as designed.
    Scene(
        "licences",
        "detail",
        [("tap", "More"), ("tap", "Support"), ("tap", "Open-source licences")],
    ),
    # "Read the licence" labels every group whose text ships in the APK — Apache-2.0 and
    # BSD-3-Clause both — but only Apache's is on screen at the top of the list, with 195 artifacts
    # between it and the next one. The needle is unambiguous *where the tap happens*, which is the
    # only place it has to be.
    Scene(
        "licence-text",
        "detail",
        [
            ("tap", "More"),
            ("tap", "Support"),
            ("tap", "Open-source licences"),
            ("tap", "Read the licence"),
        ],
    ),
    # --- full-bleed content, which has no inner padding of its own to hide behind -------------
    #
    # **Photos is `tap_text`, and English cannot show why.** The app bar's avatar carries
    # `bunny_avatar_placeholder` as its content description, and in Polish that is *"Nie ma jeszcze
    # zdjęcia"* while the More row is *"Zdjęcia"* — one word, two grammatical numbers, the same
    # form. English keeps them apart for free: *"No photo yet"* is singular, so the needle *Photos*
    # is not a substring of it. The avatar is 60x60 against the row's 170x59, so `find`'s
    # smallest-wins rule takes the avatar, and tapping it opens the bunny switcher rather than
    # navigating. `photo-add-menu` failed loudly on the next tap; these two shot the dropdown over
    # the More screen and reported clean. The avatar has a description and no text, the row has text
    # and no description — so `tap_text` separates them, as it does for the "+" in [find].
    Scene("photos", "full-bleed", [*SELECT_BUNNY, ("tap", "More"), ("tap_text", "Photos")]),
    # --- forms: a TopAppBar with a save action, fields down to the bottom edge -----------------
    Scene("bunny-editor", "form", [*SELECT_BUNNY, ("tap", "Edit")]),
    Scene("bunny-editor-scrolled", "form", [*SELECT_BUNNY, ("tap", "Edit"), ("swipe_up", "")]),
    Scene("weight-entry", "form", [*SELECT_BUNNY, *OPEN_WEIGHT_FORM]),
    Scene("observation-entry", "form", [*SELECT_BUNNY, *OPEN_OBSERVATION_FORM]),
    Scene(
        "observation-entry-scrolled",
        "form",
        [*SELECT_BUNNY, *OPEN_OBSERVATION_FORM, ("swipe_up", "")],
    ),
    Scene(
        "care-reminder-editor",
        "form",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_up", ""), ("tap", "Add a reminder")],
    ),
    # Owed, and its absence cost a gate. In landscape the opening frame puts the interval `EditText`
    # 27px under the navigation bar, which reads as a defect until something proves the *end* of the
    # scroll clears — and with no `-bottom` companion nothing could. Checked by hand on 2026-08-12
    # (`drawn=0 touch=0`), then written down here so the next run answers it without a person.
    # **A route whose opening frame can trip the check owes a `-bottom`**, or every run re-litigates it.
    Scene(
        "care-reminder-editor-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_up", ""), ("tap", "Add a reminder"), ("swipe_end", "")],
    ),
    # --- the IME, which is the case a still screen never shows --------------------------------
    Scene(
        "weight-entry-ime",
        "ime",
        # By structure, not by copy — see [tap_field]. The needle used to be "Weight in grams",
        # which is the *help line* under the field: the tap focused nothing and every shot of this
        # scene, including Phase 7's banked after set, has no keyboard in it.
        [*SELECT_BUNNY, *OPEN_WEIGHT_FORM, ("tap_field", "0"), ("wait", "1.5")],
        note="keyboard up; in landscape it eats most of the screen",
    ),
    Scene(
        "observation-entry-ime",
        "ime",
        [
            *SELECT_BUNNY,
            *OPEN_OBSERVATION_FORM,
            # `swipe_end`, not one `swipe_up`: the note is the *last* field, so one swipe reaches it
            # only on a screen tall enough — which is why this scene skipped in both landscape cells
            # on 2026-08-12 and passed in both portrait ones. Scrolling to the end asks for the
            # field's position rather than guessing at it, and the end is where this field lives in
            # every geometry.
            ("swipe_end", ""),
            # The field's *placeholder*, not "Anything else". Phase 7 moved that label out of the
            # box and above it, so tapping it now lands on a plain Text with nothing to focus and
            # the keyboard never comes up — a scene that still passes and shoots the wrong frame.
            ("tap", "What you noticed"),
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
        [("tap", "More"), ("tap", "Settings"), ("tap", "Backup & restore"), ("swipe_end", "")],
    ),
    # Home is the route FabClearance was written for: Scaffold pads content for the bars it owns but
    # not for the FAB floating over it, so Edit/Archive/Delete sat underneath it. The `home` scene
    # cannot show that — the FAB only collides at the *end* of the scroll — so the redesign's after
    # set had no frame proving the fix. Same rule as care-reminder-editor-bottom: a route whose last
    # row can end up under something owes a -bottom, or every future gate re-litigates it.
    Scene("home-bottom", "tab", [*SELECT_BUNNY, ("swipe_end", "")]),
    Scene("weight-bottom", "tab", [*SELECT_BUNNY, ("tap", "Weight"), ("swipe_end", "")]),
    Scene(
        "observations-bottom",
        "tab",
        [*SELECT_BUNNY, ("tap", "Observations"), ("swipe_end", "")],
    ),
    Scene("care-bottom", "tab", [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_end", "")]),
    Scene("photos-bottom", "full-bleed", [*SELECT_BUNNY, ("tap", "More"), ("tap_text", "Photos"), ("swipe_end", "")]),
    Scene("bunny-editor-bottom", "form", [*SELECT_BUNNY, ("tap", "Edit"), ("swipe_end", "")]),
    Scene(
        "observation-entry-bottom",
        "form",
        [*SELECT_BUNNY, *OPEN_OBSERVATION_FORM, ("swipe_end", "")],
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
        [*SELECT_BUNNY, ("tap", "More"), ("tap_text", "Photos"), ("tap", "Add photos"), ("wait", "1.0")],
    ),
    # **The one entry point for recording a day** (Phase 7.5 §6). The healthy day used to be a
    # button inside the Observations timeline while the "+" opened the long form, so the shortcut
    # was the hard one to find; now both are rows of this sheet. Worth a scene of its own because
    # `healthy_day_help` has to travel with the label — one tap commits four facts on the owner's
    # behalf and they are entitled to know which (ADR-0001) — and a subtitle is exactly the part a
    # translation can push onto a third line.
    Scene(
        "record-day-sheet",
        "overlay",
        [*SELECT_BUNNY, ("tap", "Record an observation"), ("wait", "1.0")],
        note="the two ways to record a day, with the healthy day's help line under it",
    ),
    # The app's second `ModalBottomSheet`, and the inset case a dialog does not cover: a sheet is
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
    Scene("care-scrolled", "tab", [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_up", "")]),
    # The dose history is the longest list the app builds — one row per dose per day — so its end is
    # the scroll most likely to leave a row under the navigation bar.
    Scene("medication-course", "detail", [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", MEDICATION_COURSE)]),
    Scene(
        "medication-course-bottom",
        "detail",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", MEDICATION_COURSE), ("swipe_end", "")],
    ),
    Scene("course-editor", "form", [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", "Add a course")]),
    # Phase 7's `3e` moved Save into the app bar, so the bottom edge is now the notes card rather
    # than a button — a text box competing with the navigation bar, which is the worse of the two.
    Scene(
        "course-editor-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", "Add a course"), ("swipe_end", "")],
    ),
    Scene(
        "course-editor-ime",
        "ime",
        # Taps the *placeholder*, not "What is it?" — `3e` made that a label above the box rather
        # than the box's own floating label, so the old needle would land on a plain `Text`, focus
        # nothing, and shoot a form with no keyboard. Same trap as `observation-entry-ime`.
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("tap", "Add a course"), ("tap", "Metacam"), ("wait", "1.5")],
        note="the first field of the phase's longest form; in landscape the IME leaves two rows",
    ),
    # `swipe_up` before the button, because *Add a visit* is the last section of Care & Meds and sits
    # below the fold at 1220px tall — both of these skipped in both landscape cells on 2026-08-12 and
    # passed in both portrait ones. `tap` scrolls when it cannot find its target, but it scrolls the
    # screen it is *on*, and one round is not enough here.
    Scene(
        "visit-editor",
        "form",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_up", ""), ("tap", "Add a visit")],
    ),
    Scene(
        "visit-editor-bottom",
        "form",
        [*SELECT_BUNNY, ("tap", "Care & Meds"), ("swipe_up", ""), ("tap", "Add a visit"), ("swipe_end", "")],
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
            ("tap", "Care & Meds"),
            ("tap", MEDICATION_COURSE),
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
            ("tap", "Care & Meds"),
        ],
        suite="empty",
    ),
    # Last, because it leaves the persisted selection on "All bunnies" rather than a bunny.
    Scene(
        "home-all-bunnies",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "All bunnies")],
    ),
    # --- the housemates line, which no capture has ever contained (Phase 7.5 §8) ---------------
    # The seed has exactly two bonded bunnies with short names, so "Lives with …" has never been
    # long enough to wrap in any screenshot this project holds — and both Home render sites draw it
    # with no `maxLines` and no `overflow`. These three are the states that defect actually has, one
    # per site, and they exist because a seed variant can reach them without touching the fixture 61
    # other scenes rest on.
    Scene(
        "home-crowded",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "Bijou")],
        seed="crowded",
        note="the profile card with four housemates, one of them archived",
    ),
    # The case the count cap cannot fix, and the reason §8 asks for both halves: two housemates, so
    # nothing folds, and the names alone overflow the line. "Pip" is the short-named member of the
    # long-named trio, so what wraps is plainly the two names and not the subject's own.
    Scene(
        "home-long-names",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "Pip")],
        seed="crowded",
        note="two long housemates, no cap: the profile card's own wrap",
    ),
    Scene(
        "home-crowded-all",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "All bunnies")],
        seed="crowded",
        note="the row site: four housemates on one card, two long names on another",
    ),
    # The third site, and the one where the label is longest by construction: an archived bunny's
    # own row renders every housemate it kept (ADR-0004) on a list built for one line.
    Scene(
        "archived-crowded",
        "detail",
        [("tap", "More"), ("tap", "Archived bunnies")],
        seed="crowded",
    ),
    # --- the gain flag, which the default seed cannot produce (Phase 7.5 §1, ADR-0028) ----------
    # `SampleData.kt` has no rising series at all — it pairs the trend flag with the running watch
    # and the steady series with the expired one — so without a variant this card would be checked
    # by hand once and then never seen by the harness again. Two scenes because the rule has two
    # visible states, and they differ by exactly one control.
    Scene(
        "home-gain",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "Rosemary")],
        seed="gaining",
        note="the gain card with a birthday on file: no age question",
    ),
    # The load-bearing half. With no birthday the app may not read the absent field as adulthood, so
    # it fires *and asks* — and that question is the only thing that stops an unknown-age kit raising
    # a caution dot after every weighing for months.
    Scene(
        "home-gain-unknown-age",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "Juniper")],
        seed="gaining",
        note="the same card asking how old the bunny is",
    ),
    # The second host of the same copy. Worth its own cell because the banner sits above the chart
    # here, and the chart is the one place the rise is visible as a shape rather than as a sentence.
    Scene(
        "weight-gain",
        "tab",
        [("tap", "Choose which bunny"), ("tap", "Rosemary"), ("tap", "Weight")],
        seed="gaining",
        note="the gain banner over six months of chart",
    ),
]


# --------------------------------------------------------------------------------------------
# Needles in another language
# --------------------------------------------------------------------------------------------

RES_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

# **The needles that belong to the driver rather than to a scene**, and the reason they are named
# here: [scene_needles] can only see what is in [SCENES], so a literal buried in a function is one a
# locale run does not translate — and the first Polish run failed on exactly that, in
# [reset_to_seeded], which is the step every cell starts with.
WATCH_CLOSE = "Close it"

# The walk from a wiped install to the seeded fixture: past the three wizard steps, into Settings,
# and through the debug-only sample-data block at the end of it.
SEED_WALK = ("Skip for now", "Continue", "Finish setup", "More", "Settings", "Add the sample data", "OK")


def load_strings(locale: str | None) -> dict[str, str]:
    """`name -> value` for `values/` or `values-<locale>/`.

    The escapes matter and the markup does not: `uiautomator` reports what is *on screen*, so `\\'`
    is an apostrophe by the time a node carries it, while `&amp;` has already been resolved by the
    XML parser. `itertext` rather than `.text` so a value wrapped in inline markup still comes back
    whole.
    """
    values = RES_DIR / ("values" if locale is None else f"values-{locale}")
    strings: dict[str, str] = {}
    for element in ElementTree.parse(values / "strings.xml").getroot().findall("string"):
        name = element.get("name")
        if name is None:
            continue
        strings[name] = "".join(element.itertext()).replace("\\'", "'").replace('\\"', '"')
    return strings


def scene_needles() -> set[str]:
    """Every string this driver will look for on screen, across all suites."""
    needles = {TAB_BAR, HOME_TAB, WATCH_CLOSE, *SEED_WALK}
    for scene in SCENES:
        needles.update(arg for kind, arg in scene.steps if kind in ("tap", "tap?", "tap_text"))
    return needles


def resolve_needles(locale: str) -> None:
    """Translate the whole needle table **once, before the first tap** (ADR-0013).

    `--locale` has switched the app for a while; what has never worked is everything after it,
    because the needles are English string literals and `tap("Choose which bunny")` matches nothing
    in Polish. ADR-0013 is what makes the fix small — every user-visible string is a resource in
    every locale, and `PolishTranslationTest` keeps them level — so a needle can resolve *through
    the resource name*.

    Three cases, in order:

    - **an exact match on an English value** → the locale's value for that resource;
    - **the unique resource whose value *contains* the needle** → the locale's *whole* value, which
      still matches because `find` is a substring match against the node's label. That is what
      carries deliberate fragments like `"What you noticed"`;
    - **no match at all** → the literal, unchanged. Sample data is identical in every locale, so
      `Bijou`, `Metacam` and `Nail trim` want exactly this. It is also where a typo lands, which is
      why they are listed rather than passed over.

    **Resolved up front rather than at tap time**, because a run is ~40 s per scene per cell: the
    difference between failing early and failing late is the difference between a minute and an
    evening. An **ambiguous** needle — one whose candidates disagree about the translation — is
    fatal here for the same reason. Picking one would be a coin flip, and the fix is to lengthen the
    needle until it names one thing, which improves the English table too.
    """
    english = load_strings(None)
    translated = load_strings(locale)

    resolved: dict[str, str] = {}
    literals: list[str] = []
    ambiguous: list[str] = []
    by_substring = 0

    for needle in sorted(scene_needles()):
        folded = needle.casefold()
        names = [name for name, value in english.items() if value.casefold() == folded]
        exact = bool(names)
        if not names:
            names = [name for name, value in english.items() if folded in value.casefold()]
        # A resource the target locale does not carry is deliberately invariant — `app_name` is
        # `translatable="false"` on purpose (ADR-0013) — so its English text is already the right
        # needle and its absence is not a gap.
        candidates = {translated[name] for name in names if name in translated}
        if not candidates:
            literals.append(needle)
        elif len(candidates) > 1:
            ambiguous.append(f"    {needle!r} could be any of {sorted(candidates)}")
        else:
            resolved[needle] = candidates.pop()
            by_substring += 0 if exact else 1

    if ambiguous:
        raise SystemExit(
            f"{len(ambiguous)} needle(s) do not name one string in {locale!r}:\n"
            + "\n".join(ambiguous)
            + "\n  Lengthen the needle until it does — a coin flip here is a scene that shoots the "
            "wrong screen.",
        )

    _TRANSLATED.update(resolved)
    print(
        f"needles: {len(resolved)} translated to {locale} "
        f"({by_substring} by substring), {len(literals)} left literal: {', '.join(literals)}",
    )


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


def reach_scene(scene: Scene) -> str | None:
    """Walk to one scene from a cold start. Returns the failure message, or None if it was reached.

    Split out of [run_scene] so `screenshots.py` can reuse the walk without the inset checking that
    follows it. The tap sequences in [SCENES] are this file's expensive asset — a second copy of
    them in another script is a copy that drifts, and the drift is silent because both scripts would
    still produce a screenshot of *something*.
    """
    relaunch()
    # After the relaunch, not before: `am start -S` force-stops, and a force-stop cancels every
    # alarm the app has placed. Arming into a live process is also the honest order — the banner
    # arrives *during* the scene, which is the case the driver has to survive.
    #
    # **`full` only**, for the same reason [return_to_home] is: the variant needs Bijou to hang a
    # course on, and `empty` opens on a wiped install while `mismatch` replaces the whole screen
    # before anything is captured. Arming there would fail every scene in both suites on a seed that
    # is deliberately absent.
    if _LIVE_DOSE and scene.suite == "full":
        arm_live_dose()
    try:
        if not scene.keeps_watch_prompt:
            # The prompt is hosted above the shell and so composes a beat after it; asking before
            # that is asking too early, and an optional tap does not wait around to be told no.
            settle(1.2)
            tap(WATCH_CLOSE, optional=True)
            # After the prompt, never before: the prompt sits over whatever route was restored, and
            # closing it first means [return_to_home] reads the screen underneath rather than a
            # dialog's window.
            #
            # **`full` only, and that is a rule about which suites can be lost rather than a
            # shortcut.** Every `empty` scene opens with its own `wipe`, which clears saved state
            # outright and lands on the wizard — where there is no tab bar and this would fail every
            # scene in the suite. `mismatch` replaces the whole screen with a chrome-free one before
            # anything is captured. Both isolate themselves; only `full` inherits.
            if scene.suite == "full":
                return_to_home()
        # `keeps_watch_prompt` scenes get neither step, deliberately. Back **is** an answer to the
        # expiry prompt — `BinkyDialog`'s `onDismiss` is `onClose`, and closing deletes the row
        # (WatchExpiry.kt: "close, dismiss and swipe-away are one action") — so a driver that backed
        # its way to Home here would destroy the one expired watch the seed leaves, for this cell
        # and every cell after it. They are safe without it: they run first in each cell, directly
        # after the `pm clear` inside [reset_to_seeded], which leaves no saved state to restore.
        for kind, arg in scene.steps:
            STEP_RUNNERS[kind](arg)
    except StepFailed as error:
        return str(error)
    return None


def run_scene(scene: Scene, config: Config, out_dir: Path) -> dict:
    error = reach_scene(scene)
    if error is not None:
        return {"scene": scene.name, "family": scene.family, "error": error}

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
    parser.add_argument(
        "--locale",
        help=(
            "BCP-47 tag pinned as the app's language, e.g. pl. The scene needles are translated "
            "through the resource names before the first tap; default leaves the device alone"
        ),
    )
    parser.add_argument(
        "--live-dose",
        action="store_true",
        help=(
            "re-arm an unanswered dose slot a minute in the past before every scene, so a reminder "
            "banner tries to post over each one — the case DND exists for, without waiting for the "
            "seed's own 20:00 dose. Debug build only; see [arm_live_dose]"
        ),
    )
    parser.add_argument("--restore", action="store_true", help="undo the pinned rotation and nav mode")
    args = parser.parse_args()

    global _LIVE_DOSE
    _LIVE_DOSE = args.live_dose

    if args.restore:
        set_locale(None)
        restore_device()
        print("rotation, navigation mode and Do Not Disturb handed back to the phone")
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

    # Before anything touches the phone: a needle that names two strings, or none, is worth knowing
    # about now rather than 40 seconds into a cell (see [resolve_needles]).
    if args.locale:
        resolve_needles(args.locale)
        set_locale(args.locale)

    report = {"configs": []}
    # Before the run rather than with the first cell's screenshots: the report is written in a
    # `finally`, so a run that dies on its first scene would otherwise lose its own error to a
    # missing directory.
    args.out.mkdir(parents=True, exist_ok=True)
    report_path = args.out / f"report-{args.suite}.json"
    set_dnd(True)
    try:
        run_matrix(report, configs, scenes, args.out, args.suite, report_path)
    finally:
        # First in the block, because it is the one piece of cleanup that is about the *phone*
        # rather than about this run's output.
        set_dnd(False)
        # The mismatch suite leaves a deliberately corrupted database behind, so putting it back is
        # the one piece of cleanup that must happen even when a scene throws — a run that crashed
        # halfway is exactly when an app left unopenable is hardest to explain.
        if args.suite == "mismatch":
            restore_schema_version()
        # Write whatever was reached, always. A full matrix is four cells over about two hours on a
        # phone somebody also owns, and writing the report only at the end means an interruption at
        # 95% produces *nothing* — the screenshots survive on disk but the inset findings, which are
        # the point, do not. Twice on 2026-08-12 a run had to be stopped mid-cell and both times the
        # completed cells were lost. `run_matrix` also writes after each config, so the file is
        # complete for every cell that finished.
        write_report(report_path, report)
    print(f"\nreport: {report_path}")
    return 0


def write_report(report_path: Path, report: dict) -> None:
    """Merge this invocation's cells into whatever the file already holds, keyed by scene name.

    **A partial re-shoot must not delete the run it is repairing.** The report was written per
    invocation, so re-running three scenes replaced a whole matrix with those three — which happened
    to the Polish run on 2026-08-15 and was rebuilt from the directory by hand, with a note that it
    was worth folding in here if it ever happened twice. It happened twice: the 2026-08-16 English
    matrix left seven landscape cells to redo after a driver fix, against 285 that stood.

    Replaced by name and never removed, so the merge cannot lose a cell. The cost of that choice is
    that a *renamed* scene leaves its old entry behind — the directory of screenshots is the truth,
    and a reader comparing the two will find the orphan rather than a silently shortened run.
    """
    merged: dict = {"configs": []}
    if report_path.exists():
        try:
            merged = json.loads(report_path.read_text())
        except json.JSONDecodeError:
            # A run killed mid-write leaves a truncated file, and refusing to start because of it
            # would be the wrong way round: this invocation's cells are the ones in hand.
            merged = {"configs": []}
    by_name = {config["config"]: config for config in merged.setdefault("configs", [])}
    for config in report["configs"]:
        existing = by_name.get(config["config"])
        if existing is None:
            merged["configs"].append(config)
            by_name[config["config"]] = config
            continue
        scenes = {scene["scene"]: scene for scene in existing.get("scenes", [])}
        for scene in config.get("scenes", []):
            scenes[scene["scene"]] = scene
        existing.update({key: value for key, value in config.items() if key != "scenes"})
        existing["scenes"] = list(scenes.values())
    report_path.write_text(json.dumps(merged, indent=2))


def run_matrix(
    report: dict,
    configs: list[Config],
    scenes: list[Scene],
    out: Path,
    suite: str,
    report_path: Path | None = None,
) -> None:
    # `keeps_watch_prompt` scenes go first, exactly as they do in `screenshots.py`, and for the same
    # reason: the seed leaves one expired watch and every other scene opens by tapping `Close it`,
    # which *deletes the row* (WatchExpiry.kt — "close, dismiss and swipe-away are one action"). In
    # declared order `home` runs ~20 scenes ahead of `watch-expiry`, so the prompt is gone by then
    # and the shot is a plain Home screen under a dialog's name. Sorting is stable, so every other
    # scene keeps the order it is written in — which the inset findings are read against.
    #
    # Grouped by seed first, so a variant costs one reseed per cell instead of one per scene, and
    # every default-seed scene runs before any variant touches the install. "" sorts before every
    # variant name, which is what puts them in that order.
    scenes = sorted(scenes, key=lambda scene: (scene.seed, not scene.keeps_watch_prompt))
    for config in configs:
        # Seed *before* pinning the config, never after. Sorting fixes the watch prompt in the first
        # cell only — answering it is permanent, so cell 1 eats the one expired watch the seed
        # leaves and cells 2-4 would shoot a stale screen however they are ordered. But the seed
        # starts with a `pm clear`, and a wipe costs the rotation (see [wipe]), so seeding after
        # `apply_config` silently unpins every landscape cell. `full` only: `empty` wipes on purpose
        # to reach the wizard, and `mismatch` manages its own database surgery.
        if suite == "full":
            # Invalidated rather than compared: a cell must reseed even when the last one left the
            # right variant on the phone, because answering the watch-expiry prompt is permanent and
            # only a fresh seed brings it back. [ensure_seed] then does the work, and asking for the
            # first scene's seed rather than the plain one keeps a variant-only run to one reseed.
            invalidate_seed()
            ensure_seed(scenes[0].seed if scenes else "")
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
            # A no-op for every scene but the first of each seed group — see [ensure_seed]. A wipe
            # costs the rotation, which is why it may run here at all: [wipe] re-pins what
            # `apply_config` set, so a mid-cell reseed cannot silently turn a landscape cell into a
            # second portrait one.
            if suite == "full":
                ensure_seed(scene.seed)
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
        # Land each cell as it finishes rather than banking four of them against a clean exit.
        if report_path is not None:
            write_report(report_path, report)


if __name__ == "__main__":
    sys.exit(main())
