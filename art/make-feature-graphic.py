#!/usr/bin/env python3
"""Render the 1024x500 Play feature graphic to art/play-feature-graphic.png.

Original art only: the rabbit comes from `rabbit.py`, the same declaration the launcher
icon is generated from, so the two assets cannot drift into being different rabbits. Text
is *rendered* with Noto Sans, which the OFL explicitly permits without any notice — what
it restricts is redistributing glyph outlines as art.

    python3 art/make-feature-graphic.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import rabbit

OUT = Path(__file__).parent / "play-feature-graphic.png"
W, H = 1024, 500
S = 4  # supersample factor; everything below is in final pixels, scaled by S at draw time

# Play does not show this graphic at the size it demands. On the store's search results the
# 2.048:1 asset is dropped into a **16:9** box with `object-fit: cover` — measured on the live
# listing, not guessed — so the sides are cropped away and only the middle 86.8% survives. The
# first cut of this file put the wordmark 74px in and the rabbit's nose at 968, which left 6px
# of visible left margin and sliced 12px off the nose.
#
# So the layout is declared against the *cropped* frame and derived from there. MARGIN is the
# gap from the canvas edge, sized to leave a real margin inside the crop as well; main()
# asserts both properties rather than trusting this note.
CROP = round(W * (1 - (16 / 9) / (W / H)) / 2)  # 68px lost from each side
VISIBLE_MARGIN = 40  # what a viewer of the cropped graphic should actually see
MARGIN = CROP + VISIBLE_MARGIN  # 108px from the canvas edge to any content, both sides

# Shared with the launcher icon, so the two read as one identity.
ROSE, ROSE_DARK, CREAM = rabbit.ROSE, rabbit.ROSE_DARK, rabbit.CREAM
# Two steps down from CREAM towards the ground, so the three text weights read as one family
# against rose. Tinted rather than grey: a neutral grey on a warm ground looks like a mistake.
TAGLINE = (0xE4, 0xC6, 0xC9)
SUBTLE = (0xC0, 0x99, 0x9E)

FONT_DIR = Path("/usr/share/fonts/truetype/noto")
FONT_BOLD = FONT_DIR / "NotoSans-Bold.ttf"
FONT_REG = FONT_DIR / "NotoSans-Regular.ttf"


def gradient(size, top_left, bottom_right):
    """Diagonal two-stop gradient. Built at 1px per step along the diagonal, then resized."""
    w, h = size
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            # normalised distance along the top-left -> bottom-right diagonal
            t = (x / w + y / h) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(top_left, bottom_right))
    return img


def draw_rabbit(base, ox, oy):
    """The shared mark from `rabbit.py`, drawn at (ox, oy) in final pixels.

    Painted onto its own layer so the eye can be punched to full transparency rather than
    filled with a guess at the ground colour — the background here is a gradient, so a
    painted eye would only match at one height. The launcher icon gets the same hole from
    the same declaration, by winding rather than by alpha.
    """
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def tf(p):
        return ((ox + p[0]) * S, (oy + p[1]) * S)

    for e in rabbit.PARTS:
        d.polygon(rabbit.outline(e, tf), fill=(0, 0, 0, 0) if e.hole else CREAM + (255,))
    base.alpha_composite(layer)


def draw_trend(base, points, colour, width):
    """The weight chart's own motif: irregular x spacing, because that is the honest shape
    of real weighings and the one thing the app refuses to fake (see CLAUDE.md).

    The line runs off both edges on purpose — a stroke that bleeds reads as "this continues"
    under any crop, where a stroke that stops just short of one reads as a mistake. The dots
    are the opposite case: a half-cut circle is unmistakably a clipping bug, so only points
    inside MARGIN get one and the bleeding ends stay bare.
    """
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    scaled = [(x * S, y * S) for x, y in points]
    ld.line(scaled, fill=colour, width=width * S, joint="curve")
    for (x, y), (sx, sy) in zip(points, scaled):
        if not MARGIN <= x <= W - MARGIN:
            continue
        r = width * S * 1.9
        ld.ellipse([sx - r, sy - r, sx + r, sy + r], fill=colour)
    base.alpha_composite(layer)


def main():
    base = gradient((W, H), ROSE, ROSE_DARK).convert("RGBA")
    base = base.resize((W * S, H * S), Image.BICUBIC)

    # Background trend line, low contrast so it reads as texture rather than a chart.
    draw_trend(
        base,
        [(-40, 453), (72, 432), (152, 417), (198, 423), (300, 400), (356, 406), (470, 384),
         (598, 374), (666, 381), (788, 358), (886, 351), (958, 342), (1064, 329)],
        (0xFF, 0xFF, 0xFF, 26),
        3,
    )

    # Right-aligned against MARGIN rather than positioned by eye, so the nose cannot drift
    # back over the crop line if the mark in rabbit.py is ever redrawn a different width.
    draw_rabbit(base, ox=round(W - MARGIN - rabbit.bounds()[2]), oy=252)

    d = ImageDraw.Draw(base)
    wordmark = ImageFont.truetype(str(FONT_BOLD), 116 * S)
    tagline = ImageFont.truetype(str(FONT_REG), 38 * S)
    features = ImageFont.truetype(str(FONT_REG), 27 * S)

    x = MARGIN * S
    d.text((x, 150 * S), "Binky", font=wordmark, fill=CREAM, anchor="ls")
    d.text((x, 212 * S), "A health record for your rabbit", font=tagline, fill=TAGLINE, anchor="ls")
    d.text((x, 300 * S), "Private  ·  Offline  ·  No ads  ·  Free", font=features, fill=SUBTLE, anchor="ls")

    out = base.resize((W, H), Image.LANCZOS).convert("RGB")  # RGB: Play rejects alpha
    out.save(OUT, "PNG", optimize=True)
    print(f"{OUT}  {out.size[0]}x{out.size[1]}  {OUT.stat().st_size / 1024:.0f} KiB  mode={out.mode}")

    # Measure the rendered result, not the intent: a font metric or a redrawn mark can push
    # ink past the crop line without any of the constants above changing.
    px = out.load()
    cols = [x for x in range(W)
            if any(px[x, y][0] > 150 and px[x, y][1] > 120 and px[x, y][2] > 110 for y in range(H))]
    left, right = min(cols), max(cols)
    print(f"crop takes {CROP}px per side -> Play shows x {CROP}..{W - CROP}")
    print(f"ink spans x {left}..{right}; visible margins {left - CROP}px left, "
          f"{(W - CROP) - right}px right"
          f" — {'ok' if left >= MARGIN and right <= W - MARGIN else 'CLIPPED by the 16:9 crop'}")


if __name__ == "__main__":
    main()
