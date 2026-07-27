# Identity assets

Source art for the launcher icon and the Play listing, plus the licence it carries. Everything
in `app/src/main/res` that draws a rabbit is **generated from here** — edit the source and
regenerate, don't hand-edit the path data in the `VectorDrawable`s.

| Asset | Source | State |
| --- | --- | --- |
| `play-icon-512.png`, `mipmap-*`, `drawable/ic_launcher_*` | `noto-emoji-rabbit-1f407.svg` | placeholder, **blocks the upload** — see below |
| `play-feature-graphic.png` (1024×500) | `make-feature-graphic.py` | usable; original art, unblocked |
| `play-screenshot-{1-home,2-weight}.png` (1526×2713) | device capture + `pad-screenshot.py` | placeholder until 3g |

Both scripts are committed, unlike the icon's two, because they are the ones that will be re-run:
the feature graphic when the identity changes, and the padding when 3g retakes the screenshots.

## What the icon is

`noto-emoji-rabbit-1f407.svg` — the rabbit (U+1F407) from Google's [Noto Emoji]
(https://github.com/googlefonts/noto-emoji), unmodified. Placeholder-grade on purpose: it reads
as an emoji, which is not what a shipped app icon should look like. It is here so 3a can prove
the release path with a real icon instead of the template's green robot, and it is expected to be
replaced before 1.0.

The **green is the only original decision** in the icon (`#3D7A4F`, in
`drawable/ic_launcher_background.xml`). The rabbit is near-white, so the ground carries all the
colour — recolouring the icon is that one value.

## Licence, and what it obliges us to do

Noto Emoji is under the **SIL Open Font License 1.1** (`LICENSE-noto-emoji.txt`). It permits use,
modification and redistribution, including as an application's identity. Two conditions bite:

- **The notice ships with the app.** The OFL requires the copyright notice and licence text to be
  included in all copies. A `VectorDrawable` traced from the source is a derivative of it, so the
  notice has to reach the user — an in-app licences screen, not just this file.
- **"Noto" is a Reserved Font Name.** Nothing derived from it may be presented under that name.
  Not a constraint we come near, but it is why the icon is never described as "the Noto rabbit"
  anywhere user-facing.

> **Open, and blocking the Play upload:** the app has no licences screen yet, so the notice does
> not currently reach anyone. That has to exist before this icon ships, or be sidestepped by
> replacing the art with something original.

## The feature graphic, and why it is not blocked

`play-feature-graphic.png` is built by `make-feature-graphic.py` from **ellipses defined in that
file** — not traced from Noto Emoji like the icon is. That is the whole point: it keeps the one
asset that was still outstanding clear of the OFL obligation above, so replacing the icon is the
only thing standing between here and an upload, rather than two things.

The wordmark is *rendered* with Noto Sans, which is a different act from what the icon does. The
OFL restricts redistributing glyph outlines as art; it explicitly does not restrict documents and
images produced by rendering text. Tracing the rabbit into a `VectorDrawable` is the former.

It reuses the icon's `#3D7A4F` ground so the two read as one identity, and carries a low-contrast
weight-trend line with **irregular x spacing** — the same honesty the chart itself is held to.

## Screenshots

Two, from the debug build on the Xiaomi with its sample-data fixture: All-bunnies Home showing the
trend flag, and the weight chart over 90 days. Placeholders, replaced at 3g once the app has stopped
changing.

The 90-day range is deliberate. The fixture seeds one 250 g typo among 2.4 kg weighings, on purpose,
so the chart and trend flag can be reviewed against bad data — and over 1 year it dominates the axis
and squashes everything else flat. The chart is right to draw it; a store listing is just the wrong
place to show it.

The device is 1220×2712 (2.22:1), taller than the 9:16 Play documents for phone screenshots, so
`pad-screenshot.py` centres each on a 9:16 canvas filled with the screenshot's own edge colour. It
pads rather than crops because cropping a 2.22:1 shot to 9:16 would cut a fifth of the screen off.

## Regenerating

Two scripts, neither committed — they are throwaway build steps recorded here so the numbers are
not folklore:

| Output | How |
| --- | --- |
| `drawable/ic_launcher_{foreground,monochrome,background}.xml` | parse the SVG, emit `VectorDrawable`s |
| `mipmap-*/ic_launcher{,_round}.webp`, `play-icon-512.png` | render the composition, crop, resize |

The layout numbers both scripts share:

- The art is scaled to **0.55** and translated to `(17.975, 19.075)` on the 108dp canvas. That
  fits its longest side into the **66dp safe circle**, centred — derived from the art's measured
  bounding box, not eyeballed.
- The flat assets are cropped to the **central 72dp**, because that is all a launcher ever shows
  of an adaptive icon. Exporting the full 108dp square instead would make the listing icon look
  like the rabbit shrank.
