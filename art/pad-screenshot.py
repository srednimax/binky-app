#!/usr/bin/env python3
"""Pad a device screenshot out to Play's 9:16 phone-screenshot ratio.

The test Xiaomi is 1220x2712, which is 2.22:1 — taller than the 9:16 Play documents for
phone screenshots. Rather than crop the UI or squash it, this centres the shot on a 9:16
canvas filled with the screenshot's own edge colour, so the padding reads as background
rather than as letterboxing.

    python3 art/pad-screenshot.py <in.png> <out.png>
"""

import sys
from collections import Counter

from PIL import Image

RATIO_W, RATIO_H = 9, 16


def edge_colour(img):
    """Most common pixel down the left and right edges — the app's background, not the
    status bar or the nav bar, both of which only touch the top and bottom."""
    px = img.load()
    w, h = img.size
    counts = Counter()
    for y in range(h // 8, h - h // 8):
        counts[px[0, y]] += 1
        counts[px[w - 1, y]] += 1
    return counts.most_common(1)[0][0]


def main():
    src, dst = sys.argv[1], sys.argv[2]
    img = Image.open(src).convert("RGB")
    w, h = img.size

    target_w = max(w, round(h * RATIO_W / RATIO_H))
    target_h = max(h, round(w * RATIO_H / RATIO_W))
    # Only one dimension actually grows; the other is already at or past the ratio.
    if target_w / target_h > RATIO_W / RATIO_H:
        target_h = round(target_w * RATIO_H / RATIO_W)
    else:
        target_w = round(target_h * RATIO_W / RATIO_H)

    canvas = Image.new("RGB", (target_w, target_h), edge_colour(img))
    canvas.paste(img, ((target_w - w) // 2, (target_h - h) // 2))
    canvas.save(dst, "PNG", optimize=True)

    ratio = target_w / target_h
    print(f"{dst}  {w}x{h} -> {target_w}x{target_h}  ({ratio:.4f}, 9:16 = {RATIO_W / RATIO_H:.4f})")


if __name__ == "__main__":
    main()
