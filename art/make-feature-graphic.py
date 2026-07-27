#!/usr/bin/env python3
"""Render the 1024x500 Play feature graphic to art/play-feature-graphic.png.

Original art only, on purpose: the rabbit here is built from ellipses defined in this
file, not traced from Noto Emoji like the launcher icon is. That keeps this asset clear
of the OFL notice obligation that currently blocks the icon (see README.md). Text is
*rendered* with Noto Sans, which the OFL explicitly permits without any notice — what it
restricts is redistributing glyph outlines as art, which is exactly what the icon does
and this does not.

    python3 art/make-feature-graphic.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent / "play-feature-graphic.png"
W, H = 1024, 500
S = 4  # supersample factor; everything below is in final pixels, scaled by S at draw time

# The icon's green is the one original colour decision in the identity (README.md), so the
# graphic is built around it rather than introducing a second palette.
GREEN = (0x3D, 0x7A, 0x4F)
GREEN_DARK = (0x1E, 0x40, 0x2B)
CREAM = (0xF6, 0xF3, 0xEA)  # the rabbit, and the wordmark
TAGLINE = (0xC8, 0xDD, 0xCD)
SUBTLE = (0x9D, 0xC0, 0xA8)

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


def ellipse(draw, cx, cy, rx, ry, fill):
    draw.ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=fill)


def rotated_ellipse(base, cx, cy, rx, ry, angle, fill):
    """An ellipse PIL cannot draw directly: drawn upright on its own layer, then rotated.

    The layer is sized to the ellipse's bounding box plus slack so rotation cannot clip it.
    """
    pad = int(max(rx, ry) * S * 2)
    layer = Image.new("RGBA", (pad * 2, pad * 2), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.ellipse(
        [pad - rx * S, pad - ry * S, pad + rx * S, pad + ry * S],
        fill=fill,
    )
    layer = layer.rotate(angle, resample=Image.BICUBIC, center=(pad, pad))
    base.alpha_composite(layer, (int(cx * S) - pad, int(cy * S) - pad))


def draw_rabbit(base, ox, oy):
    """A sitting rabbit facing right, from six ellipses. Coordinates are relative to (ox, oy).

    Deliberately simple and geometric rather than illustrative — this is a placeholder-grade
    mark like the icon it sits beside, and something drawn to look hand-illustrated would
    overpromise what the identity currently is.
    """

    def at(x, y):
        return ox + x, oy + y

    d = ImageDraw.Draw(base)

    # ears first, so the head overlaps their bases and hides the joins
    for dx, dy, angle in ((2, -106, 13), (36, -101, -9)):
        cx, cy = at(dx, dy)
        rotated_ellipse(base, cx, cy, 13, 46, angle, CREAM + (255,))

    # The head is set high and well forward of the body's centre, and the two overlap by only
    # about a quarter of the head. Sized closer together they merge into a single blob with ears.
    ellipse(d, *at(-58, 76), 19, 19, CREAM + (255,))  # tail, at the back
    ellipse(d, *at(0, 58), 62, 74, CREAM + (255,))  # body
    ellipse(d, *at(18, -30), 42, 42, CREAM + (255,))  # head
    ellipse(d, *at(37, -37), 7, 7, GREEN_DARK + (255,))  # eye, cut back to the ground colour


def draw_trend(base, points, colour, width):
    """The weight chart's own motif: irregular x spacing, because that is the honest shape
    of real weighings and the one thing the app refuses to fake (see CLAUDE.md)."""
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    scaled = [(x * S, y * S) for x, y in points]
    ld.line(scaled, fill=colour, width=width * S, joint="curve")
    for x, y in scaled:
        r = width * S * 1.9
        ld.ellipse([x - r, y - r, x + r, y + r], fill=colour)
    base.alpha_composite(layer)


def main():
    base = gradient((W, H), GREEN, GREEN_DARK).convert("RGBA")
    base = base.resize((W * S, H * S), Image.BICUBIC)

    # Background trend line, low contrast so it reads as texture rather than a chart.
    draw_trend(
        base,
        [(72, 432), (152, 417), (198, 423), (300, 400), (356, 406), (470, 384),
         (598, 374), (666, 381), (788, 358), (886, 351), (958, 342)],
        (0xFF, 0xFF, 0xFF, 26),
        3,
    )

    draw_rabbit(base, ox=852, oy=252)

    d = ImageDraw.Draw(base)
    wordmark = ImageFont.truetype(str(FONT_BOLD), 116 * S)
    tagline = ImageFont.truetype(str(FONT_REG), 38 * S)
    features = ImageFont.truetype(str(FONT_REG), 27 * S)

    x = 74 * S
    d.text((x, 150 * S), "Binky", font=wordmark, fill=CREAM, anchor="ls")
    d.text((x, 212 * S), "A health record for your rabbit", font=tagline, fill=TAGLINE, anchor="ls")
    d.text((x, 300 * S), "Private  ·  Offline  ·  No ads  ·  Free", font=features, fill=SUBTLE, anchor="ls")

    out = base.resize((W, H), Image.LANCZOS).convert("RGB")  # RGB: Play rejects alpha
    out.save(OUT, "PNG", optimize=True)
    print(f"{OUT}  {out.size[0]}x{out.size[1]}  {OUT.stat().st_size / 1024:.0f} KiB  mode={out.mode}")


if __name__ == "__main__":
    main()
