# Identity assets

Source art for the launcher icon and the Play listing, plus the licence it carries. Everything
in `app/src/main/res` that draws a rabbit is **generated from here** — edit the source and
regenerate, don't hand-edit the path data in the `VectorDrawable`s.

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
