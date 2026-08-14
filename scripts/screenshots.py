#!/usr/bin/env python3
"""Capture every screen in light and dark, as the before/after evidence for Phase 7's redesign.

Phase 7 changes how the app looks and says nothing about what it does, which makes "is this better?"
the only question that matters and the hardest one to answer honestly. The answer is a *before* set:
every screen, shot before a line changes, so the comparison at the end is against a record rather
than against a memory of what the old one looked like.

This is not `edge-to-edge.py` with different flags — it is the same walk with a different axis and a
different output. That script's matrix is **rotation x navigation mode** and its deliverable is the
inset arithmetic; this one's matrix is **theme x locale** and its deliverable is the PNG. So the tap
sequences are imported from it rather than copied: [SCENES] is the expensive asset in this repo and
two drifting copies of it would both keep producing screenshots, just of the wrong screens.

Run it before the redesign starts and again at the gate, same scenes, same cells:

    scripts/screenshots.py --out docs/screenshots/before
    scripts/screenshots.py --out docs/screenshots/after
    scripts/screenshots.py --out DIR --theme light          # one cell
    scripts/screenshots.py --out DIR --scene home,weight    # one screen, while iterating
    scripts/screenshots.py --restore                        # hand the phone back

Each cell runs all three suites in the one order that works: `full` against the seeded sample data,
then `mismatch`, then `empty` — which wipes the install and is therefore last. Each cell then reseeds,
so the next one starts from the same place and the phone is left usable rather than blank.

**It wipes the debug install** (`binky.bunny.and.rabbit.tracker.debug`). That is not the Play build
holding real bunny history — different `applicationId`, separate install, untouched by this.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import time
from pathlib import Path

# `edge-to-edge.py` is not an importable module name — the hyphen makes it un-`import`-able by the
# ordinary statement, so it is loaded by path instead. Renaming it was the alternative and it is
# referenced by name in DOD.md, PLAN.md and every 4f note; a loader stanza is the cheaper edge.
_SPEC = importlib.util.spec_from_file_location("edge_to_edge", Path(__file__).parent / "edge-to-edge.py")
e2e = importlib.util.module_from_spec(_SPEC)
# Registered before it executes, not after: `@dataclass` resolves `cls.__module__` through
# `sys.modules` while the class body runs (Python 3.12+), and `Config` is decorated at import time.
# Without this line the import dies on a `NoneType has no attribute '__dict__'` from dataclasses.
sys.modules["edge_to_edge"] = e2e
_SPEC.loader.exec_module(e2e)


# --------------------------------------------------------------------------------------------
# The cells
# --------------------------------------------------------------------------------------------

# Portrait + gesture only, and that is a deliberate narrowing rather than an oversight. Orientation
# and navigation mode are what `edge-to-edge.py` exists to cover, and Phase 7's gate re-runs that
# matrix in full; repeating those four cells here would shoot the same design four times to learn
# nothing about the design.
CONFIG = e2e.Config("portrait-gesture", 0, "gesture")

# Dark is not a variant of light and does not review as one. Contrast, elevation and the surface
# roles all land differently, and a palette settled in light alone is a palette re-settled in dark
# later — which is the redesign done twice.
THEMES = {"light": "no", "dark": "yes"}

# The order is the whole point. `empty` wipes, so it goes last or it takes the sample data the `full`
# suite needs out from under it. `mismatch` corrupts the schema version and puts it back, which is
# survivable in the middle; it is second because it is cheap and because running it after a wipe
# would corrupt a database with nothing in it.
SUITES = ("full", "mismatch", "empty")


def set_theme(theme: str) -> None:
    """Flip the system dark theme. `BinkyTheme` reads `isSystemInDarkTheme()`, so this is the lever.

    There is no in-app theme preference to drive instead — `MainActivity` calls `BinkyTheme {}` with
    no arguments — which is why this is a device setting and not a tap sequence.
    """
    e2e.shell(f"cmd uimode night {THEMES[theme]}")
    # The mode change restarts activities out of process. Nothing publishes a "the new configuration
    # has landed" signal that arrives before the recomposition does, so this is a wait; every scene
    # force-stops and relaunches anyway, which is the real guarantee.
    e2e.settle(2.5)


def set_locale(locale: str | None) -> None:
    """Pin the app's language, or hand it back to the device default when `locale` is None.

    Per-app locales are system state keyed by package, which is why this is `cmd locale` and not the
    in-app Settings picker: same mechanism the picker drives, reachable without eight taps.

    **It does not survive `pm clear`.** Every wipe drops it back to the device default, so this is
    re-applied per suite rather than once per cell — the `empty` suite would otherwise shoot the
    setup wizard in English inside a Polish run and label it Polish.
    """
    if locale is None:
        e2e.shell(f"cmd locale set-app-locales {e2e.PACKAGE} --locales")
    else:
        e2e.shell(f"cmd locale set-app-locales {e2e.PACKAGE} --locales {locale}")
    e2e.settle(1.5)


# --------------------------------------------------------------------------------------------
# Capture
# --------------------------------------------------------------------------------------------


def capture(scene, out_dir: Path) -> dict:
    """Walk to one scene and shoot it. No inset checking — that is `edge-to-edge.py`'s job."""
    error = e2e.reach_scene(scene)
    if error is not None:
        return {"scene": scene.name, "family": scene.family, "error": error}

    e2e.settle(0.8)
    shot = out_dir / f"{scene.name}.png"
    shot.write_bytes(e2e.adb("exec-out", "screencap", "-p", binary=True))
    return {
        "scene": scene.name,
        "family": scene.family,
        "note": scene.note,
        "screenshot": str(shot.relative_to(out_dir.parent)),
        "bytes": shot.stat().st_size,
    }


def run_cell(theme: str, locale: str | None, scenes: list, out: Path, reseed: bool) -> dict:
    """One theme, every suite, in [SUITES] order.

    The reseed is at the *start* rather than the end, and that is the load-bearing detail of the
    whole script. A cell answers the watch-expiry prompt on its very first scene — the `Close it`
    tap in `reach_scene` — and answering it is permanent, so a second cell inheriting the first
    one's install finds the prompt already gone and shoots an ordinary Home screen under the name
    `watch-expiry`. Starting each cell from a fresh seed is what makes light and dark comparable at
    all, rather than a pair that quietly diverges after scene one.
    """
    out_dir = out / theme
    out_dir.mkdir(parents=True, exist_ok=True)

    if reseed:
        # Invalidated first, so a cell always reseeds even when the previous one left the same seed
        # on the phone — the watch-expiry prompt is the reason (see below), and only a fresh seed
        # brings it back. `min` picks the seed the *first* scene will want: scenes are sorted by
        # seed, "" sorts first, so this is "" unless every scene here is a variant one.
        e2e.invalidate_seed()
        e2e.ensure_seed(min((scene.seed for scene in scenes if scene.suite == "full"), default=""))
    set_theme(theme)

    results = []
    # Set while the database on disk claims a schema this build cannot open, and cleared the moment
    # it is put back. Not "did the mismatch suite run?" — that question has the wrong answer by the
    # end of a cell, because `empty` runs afterwards and its `pm clear` deletes the backup file the
    # restore reads. Restoring then fails on a missing file and takes the whole run down with it,
    # which is what killed the dark cell on the first attempt.
    schema_dirty = False
    try:
        for suite in SUITES:
            wanted = [scene for scene in scenes if scene.suite == suite]
            if not wanted:
                continue
            # `keeps_watch_prompt` scenes go first, and this is a fix rather than a preference. The
            # seed leaves exactly one expired watch (Nugget's 3-day, started 4 days ago; Bijou's
            # 7-day is still running), and every other scene opens by tapping `Close it` — which
            # *deletes the row*, per WatchExpiry.kt's "close, dismiss and swipe-away are one
            # action". In SCENES order `home` runs ~20 scenes before `watch-expiry`, so the prompt
            # is long gone by then and `watch-expiry.png` is a plain Home screen wearing the name of
            # a dialog. Sorting is stable, so everything else keeps its declared order.
            # Seed group first, then the watch prompt inside it — the same order and the same
            # reasons as `edge-to-edge.py`'s [run_matrix].
            wanted.sort(key=lambda scene: (scene.seed, not scene.keeps_watch_prompt))
            schema_dirty = schema_dirty or suite == "mismatch"
            # Re-applied per suite because `empty`'s wipe drops it — see [set_locale].
            set_locale(locale)
            print(f"  -- {suite} ({len(wanted)} scenes)")
            for scene in wanted:
                if suite == "full" and reseed:
                    e2e.ensure_seed(scene.seed)
                elif scene.seed:
                    # **Skipped rather than shot.** `--no-reseed` is the iterate-on-one-screen flag,
                    # and a scene whose whole point is a state the default fixture hides would come
                    # back as an ordinary screenshot under a name claiming otherwise. A cell that
                    # cannot fail is not evidence; a scene that says it did not run is.
                    results.append(
                        {"scene": scene.name, "family": scene.family, "error": f"needs seed {scene.seed!r}; --no-reseed"},
                    )
                    print(f"     {scene.name:28s} SKIPPED  needs seed {scene.seed!r}")
                    continue
                result = capture(scene, out_dir)
                results.append(result)
                if "error" in result:
                    print(f"     {scene.name:28s} SKIPPED  {result['error'][:80]}")
                else:
                    print(f"     {scene.name:28s} {result['bytes'] // 1024:>5d} KB")
            if schema_dirty:
                # Immediately, while the backup still exists — not deferred to the `finally`.
                e2e.restore_schema_version()
                schema_dirty = False
    finally:
        # Only reachable when a scene threw mid-mismatch, which is exactly when a phone left claiming
        # a schema this build cannot open is hardest to explain.
        if schema_dirty:
            e2e.restore_schema_version()

    return {"theme": theme, "locale": locale or "device default", "scenes": results}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, help="directory for the screenshots and the manifest")
    parser.add_argument("--theme", help="comma-separated: light,dark. Default both")
    parser.add_argument("--scene", help="comma-separated scene names. Default every scene, all suites")
    parser.add_argument(
        "--locale",
        help="BCP-47 tag pinned as the app's language, e.g. pl. Default leaves the device default alone",
    )
    parser.add_argument("--restore", action="store_true", help="undo the pinned rotation, nav mode and locale")
    parser.add_argument(
        "--no-reseed",
        action="store_true",
        help="skip the wipe-and-seed each cell starts with. For iterating on one screen, not for a full set",
    )
    args = parser.parse_args()

    if args.restore:
        e2e.restore_device()
        set_locale(None)
        e2e.shell("cmd uimode night auto")
        print("rotation, navigation mode, locale, theme and Do Not Disturb handed back to the phone")
        return 0

    if not args.out:
        parser.error("--out is required unless --restore")

    themes = list(THEMES)
    if args.theme:
        wanted = set(args.theme.split(","))
        unknown = wanted - set(THEMES)
        if unknown:
            parser.error(f"unknown theme(s): {', '.join(sorted(unknown))}")
        themes = [theme for theme in THEMES if theme in wanted]

    scenes = list(e2e.SCENES)
    if args.scene:
        wanted = set(args.scene.split(","))
        unknown = wanted - {scene.name for scene in scenes}
        if unknown:
            parser.error(f"unknown scene(s): {', '.join(sorted(unknown))}")
        scenes = [scene for scene in scenes if scene.name in wanted]

    e2e.apply_config(CONFIG)
    started = time.time()
    manifest = {
        "config": CONFIG.name,
        "themes": [],
        # Binky's own generated scheme since Phase 7's theme commit: `dynamicColor` defaults **off**
        # (ADR-0027) and every cell here starts from a wipe, so the Material You toggle is at its
        # default and the colours are reproducible from `theme/Color.kt` alone.
        #
        # The *before* set is not, and the difference is the point rather than a caveat: it was shot
        # while `dynamicColor = true`, so its colours are this phone's wallpaper on that day. Compare
        # the two sets on structure, density and copy — which is what the set is read for — and never
        # on hue, where the before half is not a fixed target.
        "dynamic_color": "off — Binky's own scheme (ADR-0027); the before set was wallpaper-derived",
    }
    # Do Not Disturb for the length of the run, off again whatever happens — the seed's 20:00 dose
    # posts a heads-up banner over Home a minute after every reseed, and this script reseeds once
    # per cell. See [e2e.set_dnd]; the `finally` is because it is a phone-wide setting.
    e2e.set_dnd(True)
    try:
        for theme in themes:
            print(f"\n=== {theme}")
            manifest["themes"].append(run_cell(theme, args.locale, scenes, args.out, not args.no_reseed))

        # The `empty` suite ends with the install wiped, so without this the phone is handed back
        # blank — which is how the 5 Aug matrix run left it (DOD §1). Only owed when a wipe happened.
        if any(scene.suite == "empty" for scene in scenes) and not args.no_reseed:
            print("\n-- reseeding, so the phone is left usable")
            e2e.reset_to_seeded()
    finally:
        e2e.set_dnd(False)

    manifest["seconds"] = round(time.time() - started)
    manifest_path = args.out / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2))

    shot_count = sum(1 for cell in manifest["themes"] for scene in cell["scenes"] if "error" not in scene)
    failed = [scene["scene"] for cell in manifest["themes"] for scene in cell["scenes"] if "error" in scene]
    print(f"\n{shot_count} screenshots in {manifest['seconds']}s -> {args.out}")
    if failed:
        print(f"{len(failed)} scene(s) never reached: {', '.join(sorted(set(failed)))}")
    print(f"manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
