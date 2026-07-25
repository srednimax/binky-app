# The media pipeline is kind-aware, writes the file before the row, and never sweeps

`MediaFiles.kt` is the single path for persisting images (house rule), and three things about it are policy
rather than implementation detail, because every later phase adds a caller: what it produces, in which order
it writes, and what it is allowed to delete.

## One entry point, aware of the kind

`persist(source: Uri, kind: MediaKind): String` returns the relative path, and `MediaKind`
(`Avatar` / `Photo` / `Document`) selects **both the subdirectory and the downsample spec**. The kinds have
genuinely different needs — an avatar wants a small square, a gallery photo a large long-edge cap, and a
**scanned document downsampled to gallery dimensions can make small print unreadable**, which matters
because a document is evidence a vet may need to read (ADR-0017, CONTEXT.md).

Fixing the shape before the second caller exists is the point: Phase 3's photo gallery and Phase 5's scanner
extend the spec table rather than forking the pipeline, which is the failure the house rule exists to
prevent. It also makes ADR-0005's export scopes a list of `MediaKind`s rather than a list of magic strings.

**Avatars** are a **blind centre crop, 512², JPEG q85** — the avatar renders small and circular in the
switcher, on Home, in list rows and on the fluffle dashboard, so cropping once at write time beats every
render site re-deriving it. No crop-and-zoom UI: re-picking the photo is an acceptable workaround and
ADR-0012 puts visual work last. JPEG because these are photographs and avatars are in Auto Backup's
unconditional set, where every kilobyte competes with scanned documents under the quota (ADR-0005).

**EXIF orientation is applied to the pixels, then all metadata is stripped.** Cameras commonly leave pixels
unrotated and record an orientation tag instead; Coil honours that tag on an untouched file, but a crop and
re-encode **discards the tag and keeps the pixels sideways**, so camera-taken avatars come out rotated while
album-picked ones do not. Stripping the rest is a bonus: camera EXIF carries GPS, and this app has no
business copying the owner's home location into a backup that leaves the device.

## The file is written before the row

The filesystem cannot join a Room transaction, so every write has a window where the two disagree, and which
way it fails is a choice. **File first, then the row** leaves an **orphan file** on a crash: wasted bytes,
invisible, nothing broken. Row first leaves a **dangling path**, which renders the missing-media placeholder
and to the owner looks exactly like losing their bunny's photo.

Always prefer the failure the owner never sees. Concretely:

- **Replacing** an avatar is write-new, update-row, delete-old, so every intermediate state renders.
- **Deleting** is the mirror: cascade the rows, commit, then remove files best-effort. If file removal fails
  the record is still gone, which is what the owner asked for.

This also keeps the placeholder honest. ADR-0005 wants it for a restore that legitimately lacks media; a
row-first pipeline would make it a routine artifact of the app's own writes.

## There is no orphan sweep

A "list the media directory and delete anything no row references" pass is easy to write and genuinely
dangerous: it deletes user files on the strength of a query being correct, and it would have to run near
**restore**, where ADR-0005 deliberately *merges* media and leaves files on disk the current database may
not reference. Reclaiming a few kilobytes is a bad trade against the chance it one day removes something
real.

Deterministic deletion on replace and on delete covers every normal path. If evidence of a real leak ever
appears, the fix is a Settings action showing a count and requiring a tap — never a background pass.

## Consequences

Crash-window orphans can accumulate over years and are carried into Auto Backup's unconditional set. At 512²
JPEG that is tens of kilobytes against a ~25 MB quota, competing only with documents, which are already
admitted newest-first under a ceiling (ADR-0005).

`MediaFiles` tests are **instrumented, not JVM** — `Bitmap` and `ExifInterface` decoding are framework, and
there is no Robolectric in this project. The orientation behaviour is covered by a fixture JPEG carrying
orientation tag 6 in `androidTest/assets`, asserting the written pixels come out upright. That is the test
that catches the one bug in here which is otherwise invisible until a real camera photo hits a real device.
