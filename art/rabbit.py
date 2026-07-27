"""The Binky rabbit: the single definition of the mark.

Both generators import this — the launcher icon emits it as `VectorDrawable` path data,
the feature graphic rasterises it with PIL — so the two can't drift into being different
rabbits. It is built from ellipses declared here and nothing else, which is what keeps it
original work and clear of the OFL notice the old Noto-traced icon carried.

Coordinates are in arbitrary units around an origin near the rabbit's middle; callers fit
them to their own canvas. y grows downward, as it does in both SVG and PIL.
"""

import math
from dataclasses import dataclass

# The identity's colours. The rabbit is near-white, so the ground carries all the colour and
# recolouring the mark is the one GREEN value.
CREAM = (0xF6, 0xF3, 0xEA)
GREEN = (0x3D, 0x7A, 0x4F)
GREEN_DARK = (0x1E, 0x40, 0x2B)

# Magic constant for approximating a quarter-ellipse with a cubic Bézier. Standard value:
# (4/3)·(√2 − 1). Four of these is an ellipse indistinguishable from the real thing.
KAPPA = 0.5522847498307936


@dataclass(frozen=True)
class Ellipse:
    cx: float
    cy: float
    rx: float
    ry: float
    rot: float = 0.0  # degrees, positive = clockwise on screen
    hole: bool = False  # punched out of the silhouette rather than added to it


# A sitting rabbit facing right. The head is set high and well forward of the body's centre
# and the two overlap by only about a quarter of the head — sized closer together they merge
# into a single blob with ears. Ears are declared before the head so a painter's-algorithm
# renderer draws the head over their bases and hides the joins.
PARTS = (
    Ellipse(2, -106, 13, 46, rot=-13),  # ear, near
    Ellipse(36, -101, 13, 46, rot=9),  # ear, far
    Ellipse(-58, 76, 19, 19),  # tail
    Ellipse(0, 58, 62, 74),  # body
    Ellipse(18, -30, 42, 42),  # head
    Ellipse(37, -37, 7, 7, hole=True),  # eye
)


def _nodes(e):
    """The ellipse as a start point plus four cubic segments: 13 points, unrotated.

    Runs left -> top -> right -> bottom, which with y downward is clockwise. Winding
    direction is what makes `hole` work: a subpath wound against the others is subtracted
    by the non-zero fill rule that both SVG and VectorDrawable use by default, so the eye
    becomes a real hole rather than a shape painted in the background colour. That matters
    for the monochrome layer, where everything is tinted one colour and a painted eye would
    simply vanish.
    """
    rx, ry, k = e.rx, e.ry, KAPPA
    return [
        (-rx, 0),
        (-rx, -ry * k), (-rx * k, -ry), (0, -ry),
        (rx * k, -ry), (rx, -ry * k), (rx, 0),
        (rx, ry * k), (rx * k, ry), (0, ry),
        (-rx * k, ry), (-rx, ry * k), (-rx, 0),
    ]


def nodes(e, tf=lambda p: p):
    """Rotated, translated and caller-transformed control points, wound for fill or hole."""
    a = math.radians(e.rot)
    cos, sin = math.cos(a), math.sin(a)
    pts = [(e.cx + x * cos - y * sin, e.cy + x * sin + y * cos) for x, y in _nodes(e)]
    if e.hole:
        pts.reverse()
    return [tf(p) for p in pts]


def path_data(e, tf=lambda p: p, precision=2):
    """SVG/VectorDrawable path data for one ellipse."""
    n = nodes(e, tf)

    def f(p):
        return f"{round(p[0], precision):g},{round(p[1], precision):g}"

    out = [f"M{f(n[0])}"]
    for i in range(1, 13, 3):
        out.append("C" + " ".join(f(n[i + j]) for j in range(3)))
    return "".join(out) + "Z"


def outline(e, tf=lambda p: p, steps=48):
    """The same ellipse flattened to a polygon, for renderers that can't draw curves."""
    n = nodes(e, tf)
    pts = []
    for i in range(1, 13, 3):
        (x0, y0), (x1, y1), (x2, y2), (x3, y3) = n[i - 1], n[i], n[i + 1], n[i + 2]
        for s in range(steps):
            t = s / steps
            u = 1 - t
            pts.append((
                u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
            ))
    return pts


def bounds(parts=PARTS):
    """Bounding box of the silhouette. Holes are ignored — the eye cannot extend the mark."""
    xs, ys = [], []
    for e in parts:
        if e.hole:
            continue
        for x, y in outline(e):
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def fit(canvas, art_size, parts=PARTS):
    """A transform placing the mark centred on a square canvas at a given longest-side size.

    Returns a function mapping mark coordinates to canvas coordinates.
    """
    x0, y0, x1, y1 = bounds(parts)
    scale = art_size / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    return lambda p: (canvas / 2 + (p[0] - cx) * scale, canvas / 2 + (p[1] - cy) * scale)
