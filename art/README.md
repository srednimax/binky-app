# Identity assets

Source art for the launcher icon and the Play listing. Everything in `app/src/main/res` that
draws a rabbit is **generated from here** — edit the source and regenerate, don't hand-edit the
path data in the `VectorDrawable`s.

| Asset | Source | State |
| --- | --- | --- |
| `drawable/ic_launcher_{foreground,monochrome}.xml`, `mipmap-*`, `play-icon-512.png` | `rabbit.py` → `make-launcher-icon.py` | final |
| `play-feature-graphic.png` (1024×500) | `rabbit.py` → `make-feature-graphic.py` | final |
| `play-screenshot-{1-home,2-weight}.png` (1526×2713) | device capture → `pad-screenshot.py` | placeholder until 3g |

**No third-party art is involved, and nothing here carries a licence obligation.** That is a
deliberate property, not a coincidence — see below.

## The mark

`rabbit.py` is the single definition: a sitting rabbit facing right, built from **six ellipses**
and nothing else. Both generators import it, so the icon and the feature graphic cannot drift into
being different rabbits.

- The **green** (`#3D7A4F`, in `drawable/ic_launcher_background.xml`) carries all the colour,
  because the rabbit is near-white. Recolouring the identity is that one value.
- The **eye is a hole in the path**, not a shape painted in the background colour. It is wound
  against the other subpaths, so the non-zero fill rule both SVG and `VectorDrawable` use by
  default subtracts it. This matters for the **monochrome** layer, where Android tints everything
  one colour and a painted eye would simply disappear.
- The head sits high and well forward of the body's centre, overlapping it by about a quarter.
  Closer together and the two merge into a single blob with ears.

## Why it is original art

The icon was previously Noto Emoji's rabbit (U+1F407) traced into `VectorDrawable`s. That is
permitted by the **SIL Open Font License 1.1**, but on a condition that bit: the copyright notice
and licence text must reach whoever receives a copy, and a traced outline redistributed as art is
the font software, not merely output produced with it. Satisfying that needs an in-app licences
screen; the app has none, so the placeholder **blocked the Play upload**.

Drawing the mark ourselves removed the obligation and the placeholder in one move, which is why
`docs/PLAN.md` 3a preferred it over adding a screen for this alone. The Noto SVG and its licence
text are gone from the repo along with the traced paths.

One distinction worth keeping straight, because it looks like a contradiction: the feature
graphic's wordmark is *rendered* with Noto Sans, which is also OFL, and that is fine. The OFL
restricts redistributing **glyph outlines as art**; it explicitly does not restrict documents and
images produced by rendering text. Tracing the rabbit was the former. Setting a word in a typeface
is the latter.

## The feature graphic

1024×500, RGB with no alpha — Play rejects transparency. It reuses the icon's ground so the two
read as one identity, and carries a low-contrast weight-trend line with **irregular x spacing**,
the same honesty the chart itself is held to.

## Screenshots

Two, from the debug build on the Xiaomi with its sample-data fixture: All-bunnies Home showing the
trend flag, and the weight chart over 90 days. Placeholders, replaced at 3g once the app has
stopped changing, and Polish needs its own set — each locale's screenshots upload separately.

The 90-day range is deliberate. The fixture seeds one 250 g typo among 2.4 kg weighings, on
purpose, so the chart and trend flag can be reviewed against bad data — and over 1 year it
dominates the axis and squashes everything else flat. The chart is right to draw it; a store
listing is just the wrong place to show it.

The device is 1220×2712 (2.22:1), taller than the 9:16 Play documents for phone screenshots, so
`pad-screenshot.py` centres each on a 9:16 canvas filled with the screenshot's own edge colour. It
pads rather than crops because cropping a 2.22:1 shot to 9:16 would cut a fifth of the screen off.

## Regenerating

```bash
python3 art/make-launcher-icon.py      # VectorDrawables, ten mipmaps, play-icon-512.png
python3 art/make-feature-graphic.py    # play-feature-graphic.png
python3 art/pad-screenshot.py in.png out.png
```

Unlike the icon's previous two scripts, these are **committed** — they are what gets re-run when
the identity changes or 3g retakes the screenshots, and a generator you have to reconstruct from a
README is a generator nobody re-runs.

The layout numbers:

- The mark's longest side is scaled to **60dp** on the 108dp canvas and centred on its bounding
  box. A launcher may mask down to the **66dp safe circle**, and the bounding box's half-diagonal
  is 33.4dp against that 33dp radius — the corners fall marginally outside it, but the art in them
  does not: the top of the box holds two narrow ears near the centre line and the bottom a rounded
  body. Fitting the *box* inside the circle would shrink the rabbit to about 45dp for nothing.
- The flat assets are cropped to the **central 72dp**, because that is all a launcher ever shows
  of an adaptive icon. Exporting the full 108dp square instead would make the listing icon look
  like the rabbit shrank.
- `ic_launcher_round.webp` gets a circular alpha mask; `play-icon-512.png` is flattened to RGB.
