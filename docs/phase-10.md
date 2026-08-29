# Phase 10 — What owners report, and what Play's Console says — ships as 1.9.0

The first phase whose contents this plan did not choose.

`DOD.md`'s closing line predicted it: *"what is open stops being a release checklist and starts being
whatever owners report."* Phase 9 closed on 2026-08-24 with **1.8.0 live in production in nine
languages** — every phase in the project closed, and for the first time the build owners have is the
build `main` holds. Within a day the input stopped coming from the roadmap and started coming from
outside it.

**Six inputs, none of them planned:**

| Source | What |
| --- | --- |
| Owner, 2026-08-23 | Several photos on a litter tray, not one — *"wielu zdjęć do kuwety (incydent kałowy itd)"* |
| Owner, 2026-08-23 | A calendar or event list — *"żeby widzieć kiedy była ostatnia wizyta u weta albo ostatnie obcinanie pazurków"* |
| Play, release 386 | `Window.setStatusBarColor` / `setNavigationBarColor` deprecated in Android 15 |
| Play, release 386 | `GmsDocumentScanningDelegateActivity` is `screenOrientation="portrait"` |
| Play, release 386 | The app is not optimised — R8 is off |
| Settings request | An in-app light/dark override, defaulting to the phone |

Boxes lived in [`DOD.md`](DOD.md) while the phase was open; they are this file's **appendix** now.
The sections below hold the reasoning.

⚠️ **Seven boxes closed, not six.** 10g — kilogram entry — arrived from the same owner while 10c's
restore proof was being driven, and was taken rather than deferred, which is what a phase defined by
what owners report is for. It has no numbered section here: the request was small enough that its
whole record is the checklist entry in the appendix, and copying it up would be two spellings of one
fact.

## One release, and why that is the cheaper shape rather than only the faster one

Everything ships as **1.9.0**. That was a decision, not a default, and the argument for it is not
impatience.

**Both features share one migration.** ADR-0007's pending-migration rule folds changes inside a phase
into a single migration until the schema is frozen, so §4's table rebuild and §5's `CREATE TABLE` are
one `MIGRATION_7_8`, one `schemas/8.json`, one `schema-8` tag, one fixture, and — the expensive one —
**one on-phone upgrade proof**. Splitting the features across two releases pays that five-item standing
gate twice to learn the same thing.

**The cost, stated once rather than discovered later.** R8 and a table rebuild land in the same
artifact, so a release-only failure has two suspects instead of one. Two things answer that:

1. **R8 goes on before the schema work** (the phase's one ordering constraint), so every artifact check
   after it — `aab-permissions.py`, `aab-locale.py`, the upgrade proof — runs against a minified build
   rather than proving something about a build nobody ships.
2. **The upgrade proof runs on a minified build.** This is an addition to the standing gate's rule 5,
   and it matters: a proof over an unminified artifact does not cover the artifact owners install.

## §1 — Edge-to-edge ✅ built 2026-08-24

**The finding was that the fix could not be a dependency bump**, and that is the part worth keeping.
Play named `androidx.activity.EdgeToEdgeApi35.setUp`, which reads like "upgrade androidx.activity".
Disassembling `activity-1.13.0.aar` says otherwise: `EdgeToEdgeApi23`, `Api26`, `Api29` **and** `Api35`
all reach `Window.setStatusBarColor` and `setNavigationBarColor`. There is no path through
`enableEdgeToEdge()` that avoids the deprecated setters, so the call itself had to go.

What it did splits cleanly in two:

- `WindowCompat.setDecorFitsSystemWindows(window, false)` — the half Compose depends on. Stays in
  `MainActivity`.
- The bar colours — moved to `themes.xml`, because **a theme attribute is not a deprecated method**.

### The scrim is a colour across four qualifiers, and the fourth file is the whole point

Below API 29 the bars keep a scrim rather than going transparent: content scrolls under the status bar,
and without one the owner's text slides behind the clock. The values are androidx's own
`DefaultLightScrim` and `DefaultDarkScrim`, copied so that removing the call changed no pixel — this is
a deprecation fix, not a redesign. From API 29 the platform enforces bar contrast itself
(`enforceStatusBarContrast` defaults false, `enforceNavigationBarContrast` true — exactly what an
edge-to-edge app wants), so the scrim goes transparent and the system takes over. From 35 the attribute
is ignored entirely.

That is four values across two dimensions, and Android's **qualifier precedence is why it is a colour
resource and not three items redefined per qualified theme**: `night` outranks `vN`, so a
`values-night/` scrim would beat a `values-v29/` transparent on an API 29+ phone in dark mode — handing
the newest half of the supported range a scrim it should not have, on the one configuration nobody
screenshots. `values-night-v29/` settles it. A `<style>` cannot merge across qualified files the way one
`<color>` reference can, so the variation has to live in the colour.

`SystemBarsTest` holds the four files to agreement, because "delete `values-night-v29`, it duplicates
`values-v29`" is the reasonable-sounding edit that silently breaks it.

### Icon appearance is runtime, and that is not a style preference

Dark icons under a light scheme and light icons under a dark one cannot be a static theme attribute,
because the answer follows **Binky's own palette** (ADR-0027) rather than the system's. So `BinkyTheme`
writes it from a `DisposableEffect` keyed on `darkTheme`. This turns out to be what makes §6 free: the
override changes `BinkyTheme`'s parameter, and the bars follow with no further change.

### What the test deliberately does not assert

That no source file calls `enableEdgeToEdge()` again — which is the obvious regression to guard, and it
is left unguarded on purpose. Only `src/main/res` and `translations/` are registered as inputs to the
test tasks (`app/build.gradle.kts`), so a `File("src/main/java/…")` opened in a test body is invisible to
Gradle's up-to-date checking. It would report the previous run's verdict, and **a check that passes
because it did not run is worse than no check** — Phase 8 found that the hard way.

### Evidence, and its limit

8/8 matrix cells clean across `portrait-gesture` and `landscape-threebutton`; dark mode gives light
icons, light mode dark ones (status-band median luminance 249 with icon pixels down to 62). The full
75-scene × 4-configuration run is owed against 9c's 300-cell baseline.

⚠️ **API 26–28 is argued from theme XML and never observed.** The phone is the only device and a local
emulator needs `usermod -aG kvm` and a re-login. This is the strongest argument for the CI question
below — stronger than parallelism is.

## §2 — The ML Kit delegate ✅ built 2026-08-24

The AAR pins `GmsDocumentScanningDelegateActivity` to portrait, and its own manifest comment says why:
*"an invisible delegate activity to start scanner activity and receive result, so it's unnecessary to
support screen orientation and we can avoid any side effect from activity recreation in any case."*

**That comment is a real concern, not an excuse**, and it is why lifting the restriction alone would
have been the wrong fix — a rotation mid-scan would recreate the activity holding the pending result. So
the restriction goes **and the recreation is prevented**: `configChanges` hands the activity a
configuration change instead of destroying it, and `Activity.onConfigurationChanged` does nothing by
default, which is correct for something that draws no pixels. The delegate now survives a rotation
rather than being forbidden from seeing one.

`tools:remove` rather than `tools:replace` with a value, and the reason is lint: `DiscouragedApi` flags
any fixed `screenOrientation` **without reading the value**, so writing `"unspecified"` out explicitly
costs a warning against a gate that is 0 warnings. Nothing declared beats the right value declared.

⚠️ **Verify against the compiled manifest, not the text one.** `aapt2 dump xmltree` over the APK shows
zero `screenOrientation` attributes and `configChanges=0x0fa0`. The *text* merged manifest keeps XML
comments, so a grep there hits this override's own explanation and reads like a surviving restriction —
which is exactly the false positive a naive check would produce.

### Rotated mid-scan, twice, 2026-08-25 ✅

Done on the phone, and the second run on the **minified** artifact so the claim covers what ships. The
guided scanner was opened, rotated to landscape mid-scan (the screenshot comes back 2712×1220, so the
activity really did re-lay out), a page was captured in landscape, and the phone was rotated back to
portrait. `dumpsys` reports the **same `ActivityRecord` id** across all three states — the delegate was
never recreated, which is the concern the library's own manifest comment states — and the captured page
was still sitting on *Adjust corners* afterwards. Finishing the scan returned the page to the app, where
it saved as *Scanned document · 1 page*. Nothing was lost, so the override stays out.

## §3 — R8 ✅ built 2026-08-24

Turning it on was one line. The work was **deciding what to write in `proguard-rules.pro`**, and the
answer was *nothing* — which is a conclusion that had to be earned, because the alternative is a pile of
keeps nobody can ever remove.

### The file is empty on purpose, and the method is why that is safe

R8 writes down everything it did. Three files under `app/build/outputs/mapping/release/` answer every
question a keep rule would have been guessing at: `configuration.txt` is every rule it actually ran with,
consumer rules from all 69 AAR `proguard.txt` sections included; `mapping.txt` is what got renamed;
`usage.txt` is what got removed. Every hazard below was settled by reading those rather than by adding a
rule and hoping.

The dependencies turned out to be uniformly well-behaved. `androidx.work` ships `-keepnames class *
extends androidx.work.ListenableWorker`; Room ships `-keep class * extends androidx.room.RoomDatabase`;
kotlinx.serialization ships rules preserving `Companion` and `serializer()`, the reflective path
`serializer(KClass)` takes. And AGP generates `aapt_rules.txt` from the merged manifest, which is why the
`-keep` DOD had pencilled in for `BinkyBackupAgent` turned out to be unnecessary: `android:backupAgent` is
one of the attributes aapt reads, and the agent survives under its own name with its overrides intact.

That last one is the shape of the whole exercise. The rule would have done nothing, would have looked
prudent, and could never have been deleted afterwards — because nobody can prove a keep rule is a no-op
without removing it and shipping. Checking cost one grep.

### The one that could have rewritten history

**Enum names are the database's storage format** (house rule: by name, never ordinal), so the question
that mattered was whether R8 touches them. It renames the constant *fields* — `DoseStatus.GIVEN` becomes
`e` — which reads alarmingly in `mapping.txt` and is in fact harmless: `.name` returns the string handed
to the enum constructor, not the field's name, and R8 never rewrites that string because `Enum.valueOf`
depends on it. Grepping the compiled dex confirms it: `WITHDRAWN`, `LEFT_UNEATEN`, `KILOGRAMS` are all
there verbatim.

⚠️ **This cannot be pinned by a rule.** The `-keepclassmembers enum *` that everyone reaches for keeps
*field* names, which is not what `.name` returns — it would be a rule that looks like it addresses the
hazard and does not. The guarantee is a property of how R8 works, so it stays on the device list as a
behaviour check rather than being closed by the dex reading.

⚠️ **And one false alarm worth recording, because it cost time.** The first dex check used
`strings classes.dex | grep -x WITHDRAWN` and reported it missing. `strings` concatenates adjacent dex
entries onto one output line, so `-x` — whole-line match — is the wrong tool and reports absence for
something present. `grep -a -o` on the raw file is the honest reading.

### What it bought, and what is still owed

The AAB goes **12.3 MB → 8.1 MB**, a third off, with `isShrinkResources` still **false** — one variable
at a time, and resource shrinking argues with nine locales. `mapping.txt` rides inside the bundle at
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`, so Play deobfuscates crashes with no
upload step.

> **Taken later, after 1.9.1 had shipped R8 to production.** `isShrinkResources = true` costs about
> **about 55 KB of per-device download — roughly 1.8%**, so the size argument the paragraph above makes
> for R8 does *not* transfer;
>
> ⚠️ **The denominator is not the 8.1 MB AAB, and using that number understates this by 2.5×.**
> **59% of the bundle file is `BUNDLE-METADATA/…/proguard.map`** — 4.77 MB compressed of `mapping.txt`,
> which stays with Play to deobfuscate crashes and never reaches a device. What ships as app content is
> **~3.2 MB**, less again after Play's locale and density splits. Quote the shipped figure, not the file
> on disk; what it buys is that 110 dead resources (Play-services sign-in UI, AppCompat
> theming, Fragment animators — none of which a Compose app uses) stop riding along, and stop accruing
> as dependencies change. The nine-locale worry was measured rather than argued away: all 811 strings
> of all eight shipped locales survive in `base/resources.pb`, which is `aab-locale.py`'s answer and
> it runs on the release path. The reasoning now lives in `app/build.gradle.kts`.

Both artifact scripts were re-run against the minified bundle, which is the entire reason §3 goes first:
`aab-permissions.py` still reads 8 permissions and 0 `<uses-feature>`, and §2's compiled-manifest check
still finds zero `screenOrientation`.

### The behaviour half, 2026-08-25 — and it found what the static half could not ✅

Three of the four claims came back clean on the minified build:

- **Enums round-trip by name.** An observation recorded *by the minified build* stored `MANY`, `EATEN`,
  `BRIGHT`, `MORE`, with `LARGE` and `ROUND` in the join rows — read back out of `bunny.db` afterwards.
  Not one of them became an ordinal. This is the claim no rule can pin, so it had to be watched.
- **The daily sweep fires**, and with it the cross-version worry: `WM-WorkerWrapper: Starting work for
  app.binky.tracker.work.ReminderSweepWorker`, result SUCCESS. WorkManager resolved the worker from the
  class name it had persisted, under R8, which is what `-keepnames` was being trusted for.
- **Export writes.** An export produced `bunny-records-20260825T153931Z.zip`, so kotlinx.serialization's
  write path survives minification.

✅ **The restore half, completed 2026-08-25 on the minified build.** It had been left open on the belief
that SAF's roots drawer was in the way — this ROM's document picker will not open it to
`input touchscreen tap`. **The drawer was never on the path.** What was missing was a file somewhere the
picker already looked. Setting an **export folder** supplies exactly that: `OpenDocumentTree` opens on
DocumentsUI's last-used location with *USE THIS FOLDER* already on screen, so the grant costs two ordinary
taps and no drawer; and once a folder is set, the restore's `OpenDocument` reopens in that same location
with the export sitting in it. The general lesson is worth more than the workaround: **a picker that will
not navigate can still be driven, if the file is put where it already is.**

The round trip, in the order that makes it a proof rather than a screenshot:

1. An **Everything** export written straight to the folder — `bunny-everything-20260825T162128Z.zip`,
   4.48 MB, so the media half is in it and not just the rows.
2. A **1234 g weighing recorded after the export**, deliberately. Without a change between export and
   restore, a restore that quietly did nothing looks exactly like one that worked.
3. The restore. The confirmation dialog read *"Everything backup from Aug 25, 2026, 6:21 PM"* — the
   manifest was parsed **before** anything was replaced, which is kotlinx.serialization's *read* path
   surviving R8, observed rather than argued. It finished with *13 images came from the backup* and wrote
   its pre-restore snapshot to `preserved/`.
4. Verified by installing a plain `assembleDebug` over the top and reading the database directly: **all 22
   tables back to their baseline counts, and the 1234 g row gone.** Media landed too — 5 photo files
   against 5 `photos` rows, 8 document pages against 8 `document_pages` rows, which is the 13 the app
   claimed.

⚠️ **One honest limit.** The intermediate state — the database *with* the 1234 g row in it — was read from
the **UI**, not from a pulled file. The release-shaped build is not debuggable, so `run-as` is unavailable
for as long as it is the installed build, and installing the readable one first would have ended the very
code path being tested. The app reported the row (*"2.380 kg then, 1.234 kg now."*) and the app is the
process that wrote it, so the gap is narrow — but it is a gap, and pretending otherwise would make this
write-up worth less than the run.

### 🔴 R8 silently disabled the guided document scanner

The one that justifies the whole "prove by behaviour" rule, because every static check had passed.

Under R8 the guided scanner never opened. **It did not crash**: `MlKitDocumentScanner.start` catches every
exception and falls back to the plain camera by design (ADR-0009), so a feature owners have simply stopped
existing, and said so in one `I BinkyScanner: Guided scanner unavailable` line nobody would read. An
unminified build of the same commit opened `com.google.android.gms/.mlkit.docscan.ui.DocumentScanningActivity`
on the same phone, which is what isolated it to R8 rather than to Play services or this device.

**The cause is a class name that lives in a manifest meta-data *key*.** ML Kit discovers its components the
Firebase way, and the merged manifest declares

```xml
<service android:name="com.google.mlkit.common.internal.MlKitComponentDiscoveryService">
  <meta-data android:name="com.google.firebase.components:com.google.mlkit.common.internal.CommonComponentRegistrar"
             android:value="com.google.firebase.components.ComponentRegistrar" />
```

AGP's generated `aapt_rules.txt` reads `android:name` on *components* — it does not go looking for class
names spliced into meta-data keys. So R8 saw no caller for the registrar's no-arg constructor and removed
it, while keeping the class: `mapping.txt` shows `CommonComponentRegistrar -> CommonComponentRegistrar`
**with no members under it**, which is exactly what a plain `-keep class` leaves behind. The runtime said
the same thing in one line — `NoSuchMethodException: CommonComponentRegistrar.<init> []` — and with
discovery broken, building the scanner client died on a `requireNonNull` deep inside
`mlkit_vision_document_scanner.zztp.<init>`. That NPE is what reached the log, retraced through this
build's own mapping.

**The fix is one rule**, and it is `-keepclassmembers` rather than `-keep` because the class was never the
problem:

```
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
```

Scoped to the interface, not to `com.google.mlkit.**`: the contract with the hole in it is Firebase's
component discovery, and any registrar added later has the same hole. Verified both ways — `usage.txt` no
longer lists the constructor among what was removed, and on the phone the minified build now opens
`DocumentScanningActivity` with **zero** fallback lines.

`app/proguard-rules.pro` said the next keep must arrive with the mapping or usage output that justifies it.
This one did, and the file's opening claim — that it holds no keep rules — is amended rather than deleted,
because *why* it held none for a day is still the useful part.

### The baseline profile question, asked on the phone and closed — 2026-08-29

The last item on the app-optimization list was whether Binky should ship an **app-specific baseline
profile**. It should not, and the reason is a measurement rather than a preference.

**Binky already ships a baseline profile.** `compileDebugArtProfile` / `compileReleaseArtProfile` merge
the `baseline-prof.txt` that AndroidX artifacts carry into `assets/dexopt/baseline.prof` — 6.8 KB in the
APK — and the installer acts on it: `dumpsys package dexopt` reads
`[status=speed-profile] [reason=baseline]`. So the question was never "profile or no profile". It was
whether adding *app* classes to a profile that is already there and already working buys anything.

**Measured with `adb` alone, on the Xiaomi, against a `-PreleaseShapedDebug` build** — minified and
non-debuggable, which is load-bearing twice over: a debuggable app is not AOT-compiled at all, so the
whole experiment would have read as noise. Ten `am force-stop` + `am start -W` launches per state, first
discarded, all confirming `LaunchState: COLD`; the figure is `TotalTime`, milliseconds, median of ten.

| ART compilation state | how it was set | median | span |
| --- | --- | ---: | --- |
| `verify` — no AOT at all, the floor | `compile -m verify -f` | **259.5** | 246–277 |
| `speed-profile` / `reason=baseline` — **what ships today** | reinstall, then let it settle | **241.5** | 226–264 |
| `speed` — every method AOT, an absolute ceiling | `compile -m speed -f` | **267.5** | 243–276 |

**The shipped state is already the fastest of the three, and compiling *everything* is 26 ms slower than
it.** That is the whole answer. A baseline profile is a *subset* of what `speed` compiles, so `speed` is
the strict upper bound on anything any profile could ever deliver — and that bound is **below** where the
app already sits. The headroom an app-specific profile could capture is not small, it is negative. The
bundled AndroidX profile is worth its 6.8 KB (18 ms, 6.9% against no AOT); a hand-generated one on top of
it has nothing left to win.

Cold start is **~242 ms**, comfortably inside Google's 500 ms "good" mark, on a mid-range phone, on the
minified artifact owners actually install.

⚠️ **Three ways this measurement lies if taken carelessly**, all three hit here before the numbers above
were trusted:

- **`compile -m speed-profile -f` silently degrades to `verify`** when no profile is present. It exits 0
  and reports success. The state has to be read back out of `dumpsys package dexopt` — never assumed from
  the command having run.
- **`compile --reset` does not restore the baseline state.** It drops the app to
  `status=verify [reason=install]`, which is the *pre*-dexopt state, not the post-install one.
- **Baseline dexopt is deferred to the background after install.** Straight after `adb install` the app
  reads `verify [reason=install]`; it only becomes `speed-profile [reason=baseline]` once the app has run.
  A run started immediately after installing straddles the transition and measures neither state.

**No `:baselineprofile` module was added, and `settings.gradle.kts` stays `include(":app")`.** The
macrobenchmark route would have bought frame-accurate numbers at the price of the repo's first multi-module
build, a **third** build shape beside `release` and `releaseShapedDebug` (generation needs `-dontobfuscate`
and `-dontoptimize`), profile generation on a ROM whose family Google's own docs list as hostile, and a
standing obligation to keep the profile from going stale — a failure that degrades silently. `am start -W`
is coarser, and coarse was sufficient: the gate question was go/no-go, and the gap it had to resolve turned
out to be *inverted*, not merely small. A sharper instrument would have measured the same nothing.

**What would reopen this:** a startup path that grows materially — a heavier `Application`, work moved
ahead of first frame, a large dependency — or a cold start that a person can feel. Re-run the three rows
above before building anything; the recipe is four `adb` commands and needs no module.

## §4 — Several photos on a tray ✅ built 2026-08-24

The feature is three lines of shape: the column becomes a join table, the join table is written per
participant, and the refcount that already existed now guards a set. Everything interesting is in the
migration.

### The rebuild had grown two children since the recipe was written

ADR-0029 wrote the create-copy-drop-rename recipe and named its trap once: `DROP TABLE observations`
performs an implicit delete of every row, which fires `ON DELETE CASCADE`, and `PRAGMA foreign_keys = OFF`
is a no-op inside the transaction Room has already begun — so the cascade cannot be switched off, only
survived. What that ADR could not know is that **it was itself about to add two more children**.
`observation_droppings_appearance` and `observation_droppings_sizes` arrived with schema 7, so schema 8's
rebuild has three tables to stage where the recipe stages one.

⚠️ **`runMigrationsAndValidate` cannot see the difference.** A database whose every droppings value has
been cascaded away has exactly the right *shape*. A migration that staged two children out of three would
have been green in CI and would have deleted an owner's history on upgrade day. `Migration7To8Test` counts
rows for all three, with values spread across three observations so a partial restore passes nothing, and
a bonded pair sharing one photo path so a migration inserting per-*path* rather than per-*row* fails.

### The SQL was checked against the exported schema by machine

The house rule is that a migration's SQL is a **transcription of `schemas/8.json`, not a paraphrase of the
entities**. This time that was verified rather than intended: a short script pulled every `execSQL` out of
`MIGRATION_7_8`, stitched its concatenated string fragments back into statements, and compared each against
the `createSql` Room exported. Every `CREATE TABLE` and `CREATE INDEX` matches byte for byte. It is a cheap
check for a class of bug — a missing `NOT NULL`, a foreign key with the wrong `ON DELETE` — that otherwise
surfaces as a validation failure on a phone rather than on a laptop.

### `position` is there and `createdAt` is not

The plan proposed both columns; only one earned its place. `position` has to be a column — the composite
key `(observationId, path)` cannot carry order, and without it the strip comes back in whatever order the
rows happen to sit in, which an owner who arranged their frames would notice. `createdAt` would have been
read by nothing: `observation_symptoms` and both droppings tables carry no timestamp, and the observation
the row hangs off already records when it was made. A column nobody queries is a fact the app has to keep
true for nothing.

That is also why `TrayFacts.trayPhotoPaths` is a `List` where the two droppings fields are `Set`s: order is
part of this fact and is not part of theirs. Duplicates are excluded by the table's key rather than by the
type, which is where the same rule already lived.

### The refcount rule survives with its wording intact

ADR-0029's one extra rule — *a file goes only when no other row references the path* — reads the same and
runs against a different table. What changed is that "the photo changed" became "these two went and these
three stayed", so each edit diffs the previous set against the new one and checks each departing path on
its own. The check is made **after** every participant's rows are rewritten, so the count is the truth
about the whole group rather than about the row that happened to be on screen.

The cap is **6**, and it is arithmetic rather than a round number: ~0.5 MB a frame at `MediaKind.Observation`'s
2048px/q88, against Auto Backup's 20 MB newest-first queue shared with document pages, is about 3 MB for one
thorough tray. Past the cap the form stops offering *add* — hidden rather than disabled, since a dead button
invites tapping and explains nothing — and the exclusion notice ADR-0029 already built is what reports what
did not reach the cloud.

### Evidence

**226 instrumented tests on the phone**, all passing, including `Migration7To8Test`'s six and two new ones
covering propagation across a bonded group and the refcount on removing one photo of several. 407 JVM tests.
`translation-gate.py`: 683 resources × 8 locales complete.

**And the screens seen running**, which needed a fourth seed variant — `tray_photos`, because the default
seed records no tray photo at all and both states are unreachable without one. Four photos rather than one
or six: one would not show that the strip is a strip, and six is the cap, where a scene photographs the
*absence* of the add buttons — a real state, but a different question. Four wraps onto a second line and
leaves the buttons on screen, which is the layout question the strip actually raises.

The timeline reads as intended: one photo, a `+3` badge legible against its own scrim, and the entry still
about the bunny rather than about the tray. The form wraps 3 + 1 with a remove control on each thumbnail,
both add buttons and the help line below them.

⚠️ **One inset finding, and it is not a regression.** `observation-entry-tray-photos` reports
`drawn=0 touch=3`. The *unmodified* `observation-entry` scene reports `touch=2` with a **larger** overlap
(21 264 px against 1 605 px) — a different scroll position on the same screen. phase-7.5.md settled how to
read this: a `touch` node has no label, so it is a hit area inflated by `minimumInteractiveComponentSize`
and its overlap says nothing on its own; `drawn` is the tier that means something legible sits under a bar,
and it is zero here. Checked rather than assumed, because "my change added a finding" and "my change moved
the scroll position" look identical in the report.

⚠️ **The HyperOS split-install prompt bit twice** and the documented two-plain-installs fallback hit the
same wall, because Gradle uninstalls the test package after every run — so each run is a *first* install of
`…debug.test`, which is the case CLAUDE.md records as an outright refusal. What worked was installing both
APKs plain and then running `am instrument` directly, which skips Gradle's install cycle entirely.


### The upgrade watched on the phone, 2026-08-25 — the standing gate's rule 5 ✅

The fixture had to be built, because nothing on the phone was at schema 7 any more: the `schema-7` tag
(`ddb430a`) was built in a worktree, installed over the debug package with `-r -d`, and seeded through the
app itself — Bijou and Nugget, 44 weighings, the whole medication and document graph, four droppings rows
in each join table and **two symptom ticks**, which are the cascade canary.

⚠️ **One value had to be injected, and it is the important one.** The schema-7 seeder never wrote a
`trayPhotoPath` — the column MIGRATION_7_8 exists to move — so a seed alone would have proven the move
against zero rows. Two observations were given paths pointing at real orphan files already in
`files/photos/`, the database pushed back over `run-as` and checked byte-for-byte by md5 before the
upgrade.

Then the release-shaped build over the top, launched, and read:

- The app **opened**. No refusal screen, no crash, and the seeded watch-expiry prompt still standing on
  Nugget afterwards.
- `user_version` **7 → 8**.
- `scripts/upgrade-diff.py`: **nothing lost.** All 20 tables equal on shared columns, both tray photo
  paths landed in `observation_photos` at `position` 0, `events` arrived present-and-empty, 24/24 media
  files.

**`upgrade-diff.py` had to be taught this migration first.** Its check 4 — the one for columns that *move*
rather than vanish — knew only about MIGRATION_6_7's two droppings columns. `trayPhotoPath` is the same
shape of blind spot: dropped from `observations`, so check 3 cannot compare it, and landing in a table
check 3 has never seen. It now carries a general `COLUMN_MOVES` table and picks the moves this particular
upgrade is on the hook for by asking the before-image which columns it has, so a 6 → 7 run and a 7 → 8 run
each get exactly their own checks. Tested both ways against synthetic archives — a deliberately dropped
path is reported and names `MIGRATION_7_8` — before it was ever pointed at the phone.

### 🔴 "Release-shaped" has to mean `BuildConfig.DEBUG == false`, or it proves nothing

The first attempt at this ran on a build that was minified and still debuggable, and it met the
wipe-consent screen: *"This update has to clear the records on this phone. Records on this phone: format
7. This version: format 8."*

**That is correct behaviour, and that is what makes it dangerous.** `destructiveMigrationAllowed()` is
`BuildConfig.DEBUG` (`BunnyDatabase.kt`), so a debug build registers **no migrations at all** and honestly
offers to wipe. At a glance it is indistinguishable from the refusal screen 1.5 nearly shipped — and an
upgrade proof run on a debuggable build is not a weaker proof, it is a proof of the wrong code path.

So `-PreleaseShapedDebug` (`app/build.gradle.kts`) now sets `isMinifyEnabled = true` **and**
`isDebuggable = false`, keeping the debug build's identity — the `.debug` suffix and the local signing key
— while borrowing the release build's behaviour. It costs `run-as`, so the migrated database is read by
installing an ordinary `assembleDebug` over the top afterwards: same package, same key, and an install
never touches the data directory, so the rows that come back are the ones the release-shaped build
migrated. The flag is off by default and no CI job passes it.
## §5 — Events and the timeline ✅ built 2026-08-25

**ADR-0031** carries the decision. The owner's sentence was two requests wearing one coat — *"when was
the last vet visit, the last nail trim"* asks for a **read** of things the app already knows, and
*"other events the user would like to remember"* asks for a table that does not exist. Building one
thing for both would have got both wrong.

`events` was already in **schema 8**, folded into `MIGRATION_7_8` alongside §4's tray photos, so this
section wrote **no migration and did not touch `BUNNY_SCHEMA_VERSION`**. What it added is the DAO, a
thin repository, the pure merge, two screens, the sweep's third branch and 31 strings × 9.

### The timeline stores nothing, and that is where the tests are

`ui/events/Timeline.kt` is a pure function from four lists to month sections. Everything hard about it
is arithmetic on dates, so `TimelineTest` is a case table rather than a phone: upcoming above past, the
today boundary, an overdue reminder dragging a *past* month above the fold, a day holding all four kinds
sorting the same way twice, month grouping order, id uniqueness across four tables, and the Home card's
slice.

Two of those are worth naming because they look like bugs until the reason is read.

- **`TimelineEntry.CareDue` is always outstanding**, including when its date is in the past. An overdue
  nail trim belongs above the fold on every one of the twenty-one days it has been overdue, which is the
  reading the Care screen already takes. The consequence is that `TimelineSection` **carries** which
  side it is on rather than deriving it from the month — the two genuinely disagree.
- **The screen heads the two halves out loud** (*Coming up* / *Already happened*) rather than relying on
  order. Because of the rule above, the upcoming half can end on a past month, so scrolling from one
  "February 2026" into another one is otherwise a mystery.

### The day query does not filter on `notifiedAt`, and the plan's sketch said it should

`EventDao.onDayNow` returns everything dated today, announced or not. Both of the sweep's rules —
*"announces once"* and *"archived bunnies are never notified"* — live in the pure
`eventsDueForNotifying`, matching `careDueForNotifying`, so both are JVM-assertable rather than facts
about a `WHERE` clause. The `(bunnyId, occursOn)` index answers the query row for row either way.
`EventRepositoryTest` pins the deviation directly: an already-notified event still comes back.

### Its own notification channel, and the fifth one broke a test that was doing its job

Care is a job the app is asking for; an event is a day the owner asked to be reminded of. Android's
per-channel switch is the only place that distinction can be acted on, so `ReminderChannel.Event` is a
fifth channel rather than a second sender on care's.

`ReminderChannelsTest` failed on both counts and was right to. It pins the channel set at exactly the
ones this release has behind them — an addition has to be a deliberate act — and it derives the expected
resource names as `channel_${id}_*`, which caught `channel_event_name` sitting under an id of `events`.
The strings were renamed to `channel_events_*`; the id is the half that is permanent once shipped.

### What the timeline may not do

Only event rows are created, edited or deleted from these screens. A vet visit, a completion and a
derived due date each tap through to the screen that owns them, and in the archived scope the rows stop
being navigable at all — every destination they lead to is an editor. A derived list that could destroy
its sources would be a second place to delete every one of them.

Delete lives on the **editor**, not the row (`1d`'s finding), because an event has no detail screen of
its own — the editor is that screen, and it is also where the calendar hand-off sits. Both act on a
stored row, so neither is offered while adding.

### Tests

`TimelineTest` and `EventSweepTest` on the JVM, `EventRepositoryTest` on the phone — 9 green, and the
whole instrumented suite green at 235 (2026-08-25).

**The part no test can hold, done by hand the same day** ✅, all three on the minified build:

- **The timeline on a real database.** Sznycel's timeline drew *Coming up → August 2026 → Hay order, due
  in 3 days* over *Already happened → July 2026 → Scratched eye, Dr Nowak · Jul 16, 2026* — a care
  schedule and a vet visit, two different derived sources, grouped by month with upcoming above past.
- **A notification landing on the day.** An event *Nail trim* dated today, then the sweep forced: a
  notification on `channel=events`, titled *Nail trim*, reading *Today, for Sznycel.* The run also
  produced, by coincidence, the argument for the fifth channel — the seed's own **care** reminder is also
  called "Nail trim", so the shade held two notifications with the same title on two different channels,
  one mutable without the other. That is the design point, observed rather than asserted.
- **The calendar hand-off opening something.** `ACTION_INSERT` with a
  `content://com.android.calendar/…` URI reached the chooser, and Google Calendar opened with **New
  event: Nail trim, August 25** already filled in.

ℹ️ One thing worth knowing: the editor flips to *Added to your calendar* when the intent is **launched**,
not when the calendar saves — cancelling in Calendar still leaves `calendarHandedOffAt` stamped. That is
the only honest option, since Binky never hears the outcome, and the copy beneath already says the two are
not kept in step.

## §6 — A light/dark override in Settings ✅ built 2026-08-25

**ADR-0027's amendment** carries the decision: the ADR that chose *which* colours Binky uses now also
answers *when each scheme applies*. Settings gains **System / Light / Dark**, defaulting to System.

Four strings, one new file, and edits to six. No schema change and no migration — it is a DataStore key
beside the weight unit.

### The warning held, and it was the whole design

The advance note said `AppCompatDelegate.setDefaultNightMode`, not a Compose flag, and building it made
clear that is not a preference between two mechanisms — **it is the only one that reaches all three
things an owner sees**:

| What | Painted by | Reached by |
| --- | --- | --- |
| The colour scheme | `BinkyTheme` → `LightColors` / `DarkColors` | Compose |
| The window background | `Theme.Binky`, a `DayNight` theme, **before Compose composes** | the configuration |
| The system-bar scrim | `values-night/colors.xml`, resolved at inflation | the configuration |

A Compose-only override moves the first and leaves the other two following the phone. That is precisely
the mismatch §1's four `colors.xml` qualifiers exist to prevent, and it is worst on **API 26–28**, where
there is no system dark mode that might have agreed by accident.

`theme/NightMode.kt` holds the one call. `MODE_NIGHT_FOLLOW_SYSTEM` rather than `MODE_NIGHT_UNSPECIFIED`
for *System*: "unspecified" is the per-Activity value meaning *defer to the default*, and setting it as
the default is the one combination AppCompat treats as a no-op.

### The language switcher's shape does not transfer, and the reason is worth writing down

ADR-0013's switcher stores **nothing** — `AppCompatDelegate.setApplicationLocales` persists itself, so
`AppLanguage.currentAppLanguage()` reads back from the delegate rather than from a preference. The
obvious move here was to copy that.

It does not work: **AppCompat persists a locale and does not persist a night mode.** Night mode is
process state, and a fresh process comes up following the system. So this one needs a DataStore key
*and* something that re-applies it on every cold start.

That something is `BinkyApplication.onCreate`, and the read is `runBlocking` — a deliberate blocking read
on the main thread. The night mode has to be applied before the first Activity exists, and a flow's first
emission does not arrive until after the first frame; collecting one instead means a **light flash on
every cold start** of a phone set to Dark, which is the thing the setting exists to remove. It is also
cheap next to its neighbours: the same method already reads a database header and copies the whole
database file synchronously. The value is kept as `startupThemeMode` and handed to `MainActivity` as the
flow's initial value, so the disk is read once and the first composition is already the right colour.

### Two paths move the theme, and that is not redundancy

`applyThemeMode` moves the window; `MainActivity` collects the same preference and hands it to
`BinkyTheme`, which moves the scheme. Neither can reach what the other does. `SettingsViewModel` applies
first and persists second — the window turns on the tap, and nothing on screen waits for a disk
round-trip.

The tap does **not** restart the app, unlike the language row two sections below it: the manifest already
lists `uiMode` in `configChanges`, so AppCompat hands the running Activity an `onConfigurationChanged`
and the app repaints in place.

**`SystemBarAppearance` needed no change**, as §1 predicted — it keys off `BinkyTheme`'s resolved
`darkTheme`, which is now the override's answer. That it needed no edit is evidence for where §1 put the
runtime half.

### The UI is the weight unit's shape

`FormSection` + `ChipRow` + three `FormChip`s, above the card and below the unit — the two
header-and-chips sections together, the card left as rows. One difference: **no help text**. *Light* and
*Dark* need no gloss.

Not folded in beside Material You, though both are about appearance: that switch answers *which colours*,
this answers *light or dark*, and the switch is not drawn at all below Android 12 while this always is.

`settings_theme_system` says the same thing as `settings_language_system` in all nine languages, on
purpose — the same promise about the same phone, and two wordings would read as two behaviours.

### Proven on the phone, 2026-08-25 ✅

By pinning the phone's own night mode with `cmd uimode night` and then **sampling pixels** rather than
eyeballing screenshots, so "the scrim moved too" is a number:

| phone | app | background | status bar | nav bar |
| --- | --- | --- | --- | --- |
| Light | Dark | `(22,19,13)` | `(22,19,13)` | `(22,19,13)` |
| Dark | Light | `(255,248,239)` | `(255,248,239)` | `(246,237,227)` |
| Dark | System | `(22,19,13)` | `(22,19,13)` | `(22,19,13)` |

Both divergent directions, so neither result can be the phone leaking through. The middle row is the
one that matters: it survived a `force-stop` and a cold start, which is what the DataStore key plus the
re-apply in `BinkyApplication.onCreate` exist for. All three chips also repaint **in place**, with no
restart, from both entry paths.

⚠️ **A measurement trap worth writing down.** *Same as this phone* takes about 2.5 s to settle, where
*Light* and *Dark* are repainted inside 0.6 s — FOLLOW_SYSTEM goes through a real configuration recompute.
A screenshot taken at 0.6 s catches the old frame and reads exactly like "switching to System does not
repaint". It was recorded as a defect here for several minutes before a longer settle disproved it twice
over.

ℹ️ And the light-grey frame at the very start of a cold start is **HyperOS's window-open transition, not
the app** — `(241,241,241)`, which matches neither the app's light `(255,248,239)` nor its dark
`(22,19,13)`. Worth knowing before someone reports it as the flash this design exists to prevent.

The API 26–28 claim is the one this phone cannot check — it is the same gap §1 ships with, and the same
emulator matrix would close it.

## Standing decisions changed this phase

- **Play screenshots are light-only** (2026-08-24), reversing *dark is the set to upload*. 1.8.0 went up
  under the old rule. This is a decision about the Console alone: the app ships both themes,
  `screenshots.py` captures both cells, and §6 adds an in-app override.
- **A screenshot filename carries its locale** — `home-pl.png`. Nine locales of one screen were nine
  files distinguished only by their folder, and a PNG loses its folder the moment anyone moves it,
  renames it, or pastes it into the Console. The language cannot be recovered from the pixels.
- **The fixture bunnies are Lily and Sznycel.** ⚠️ The rename could not stop at the seeder:
  `edge-to-edge.py` and `alarm-gate.py` **tap the bunny by name**, so a code-only rename would have left
  every driver run failing on its first tap — and failing as a missing needle, which points nowhere near
  the cause. **Reseed before the next driver run.**

## Deferred — can the four configurations run in CI?

Raised 2026-08-24, to be decided when the code is ready rather than now. ✅ **Answered yes and built,
2026-08-25** — the decision, and what the emulator seam cost, are in the appendix under *Decided at the
end of the phase*. The research below is what it was decided from, and every prediction in it held.

**The real prize is coverage, not parallelism.** §1 ships with a stated gap at API 26–28 that the one
phone cannot close; an emulator matrix at 26/29/34/36 closes exactly it.

**What cannot move.** The field upgrade proof crosses a real **Play-signed 1.0.0 install** that refuses a
locally-signed APK on signature mismatch, so nothing can stand in for it. Same for anything about
HyperOS itself — autostart, Doze, the battery-optimisation exemption.

**The cost that is invisible until you try.** `apply_config` flips navigation mode through MIUI's own
`force_fsg_nav_bar` global *because* the AOSP `com.android.internal.systemui.navbar.*` overlays are all
present and all disabled on this phone. An emulator needs the overlay path instead — so this is a
device-family seam inside `edge-to-edge.py`, not a CI config file. The `settle()` timings are tuned to
real hardware too, and an emulator is where they go flaky rather than fail.

**Likely split**: emulators take the API-level coverage; the phone keeps the upgrade proof, the
scan-and-rotate and the minified build.

## How it closed, 2026-08-26

Two artifact checks, both written at the close and both proven in **both** directions rather than only
the passing one — because a check that has only ever been green is indistinguishable from a check that
cannot fail:

- **`scripts/aab-reflection.py`** is the gate that was missing when §3 broke the scanner. It reads the
  classes to check *out of the manifest* — every `<meta-data>` whose value marks one for reflection —
  and looks each up in the artifact's **dex**, requiring a public no-arg constructor. Five classes, and
  removing the keep rule and rebuilding makes it name the missing one and exit 1. ⚠️ **`mapping.txt`
  cannot do this job**: R8 writes a bare `Foo -> Foo:` line with no members for a class it kept
  unrenamed, so absence from the mapping is not absence from the artifact.
- **`aab-permissions.py` gained §2's half** — no `android:screenOrientation` reaches the artifact, so a
  dependency bump cannot quietly re-lock the screen. The primitive decoder that box was written around
  **was not needed**, and that was checked rather than assumed: a fixture compiled with `aapt2 link
  --proto-format` shows the attribute keeping its source string beside the compiled int, unlike
  `android:required`, which has no text form at all.

⚠️ **The phase closes on the build, not on the release.** 1.9.0 has not gone up. The three workflows
that would take it there — internal, production, and the nightly matrix — have **never run**, and none
of them can until this merges, because GitHub reads `release`, `schedule` and `workflow_dispatch`
triggers from the default branch only. [`DOD.md`](DOD.md) keeps that as an open item; it is not a box
this phase could have ticked.

## Appendix — the live checklist as it stood when Phase 10 closed

Moved here verbatim from [`DOD.md`](DOD.md) on 2026-08-26, when Phase 10 was ticked and that file
was emptied back down to the standing schema gate — the same move Phase 9's appendix records, and
for the same reason. The sections above are the build record; this is the *checklist*, which is a
different document: it says what each box had to satisfy and what was read to decide it had, box by
box, including the ones that closed by finding nothing.

Headings are demoted one level from the originals and nothing else is edited — which is why 10g's
section sits *after* "Decided at the end of the phase" rather than beside 10f. It was appended when
the request arrived mid-phase, and it is left where it sat.

---
### Phase 10 — the boxes

| | What | State |
| --- | --- | --- |
| **10a** | Edge-to-edge off the deprecated bar setters | ✅ **built 2026-08-24** |
| **10b** | The ML Kit delegate stops being portrait-locked | ✅ **done**, rotated mid-scan 2026-08-25 |
| **10c** | R8 on | ✅ **done** — found and fixed a silent ML Kit break; export *and* restore proven on the minified build 2026-08-25 |
| **10d** | Several photos on a tray — **schema 8** | ✅ **done**, upgrade watched on the phone 2026-08-25 |
| **10e** | Events: a timeline, and dated events an owner writes — **same schema 8** | ✅ **done**, driven on the phone 2026-08-25 |
| **10f** | A light/dark override in Settings | ✅ **done**, proved on the phone 2026-08-25 |
| **10g** | Weight entry gains a kg/g toggle — **both** weight fields | ✅ **done**, driven on the phone 2026-08-25 |

**One edge must not be reordered**: **10c before 10d/10e**, so every artifact check after R8 goes on
runs against a minified build rather than proving something about a build nobody ships. Everything else
is free.

#### 10a — Edge-to-edge ✅ built 2026-08-24

`enableEdgeToEdge()` is gone. Verified in the AAR's bytecode that every path in androidx.activity
1.13.0 reaches `Window.setStatusBarColor`/`setNavigationBarColor`, so no version of the call avoids the
deprecation — the fix could not be a dependency bump. `WindowCompat.setDecorFitsSystemWindows` keeps the
half Compose needs; the colours are theme attributes across four qualified `colors.xml` files, and
`SystemBarsTest` holds those four to agreement.

- [x] Built, `spotless`/`assembleDebug`/`test`/`lint` green — lint 0 errors, and the 2 remaining
      `IconXmlAndPng` warnings are pre-existing on the launcher icon.
- [x] Smoke-checked on the phone: 8/8 matrix cells clean, dark mode → light icons, light mode → dark.
- [x] ⚠️ **API 26–28 was argued from theme XML and never observed** ✅ recorded 2026-08-25 — the phone
      is the only physical device and it is modern, and a local emulator needs `usermod -aG kvm` and a
      re-login. **This is the limit, stated:** every claim about how the app draws under the system bars
      below API 29 rested on the four qualified `colors.xml` files and `SystemBarsTest`, not on a
      screenshot. It is the reason the CI answer below is *yes* — the nightly matrix observes it on an
      API 26 emulator. Until a nightly has run green, the limit stands as written.
- [x] **The full 4-configuration matrix** ✅ 2026-08-25 — **276 cells (69 scenes × 4), 0 skipped,
      no defect.** Evidence in `~/binky-screenshots/1.9.0-e2e/` (report + every PNG). 7 `drawn`
      findings were raised and **all seven are explained, none is a bug**:

  - `reminders-sheet` (3 cells) and `care-reminder-editor` (1 cell) are the **opening frame of a
    scroll taller than the screen** — the case `edge-to-edge.py` already calls "a list scrolling, not
    a defect". Both `-bottom` companions came back clean, which is the proof that design asks for.
  - `document-viewer` (3 cells) is **13 px of line leading** inside the TextView's node box. ⚠️ The
    number that settles it: the overlap is **constant at 13 px while the navigation bar varies 48 →
    142 px**, and content sits exactly 94 px higher in three-button than in gesture — the difference
    between the two bars. An unpadded screen's overlap tracks the bar; this one does not, so the
    shell's `Scaffold` was padding correctly all along. Confirmed against the PNG: the glyphs clear
    the pill.
  - ⚠️ **A `drawn` finding is arithmetic, not a verdict.** A fix was written for `document-viewer`
    and reverted: `navigationBarsPadding()` under a `Scaffold` that already calls
    `consumeWindowInsets(insets)` adds nothing, and the re-shot bounds came back byte-identical.
    Read the screenshot and check whether the overlap scales with the inset **before** changing code.
  - Worth doing sometime: the `drawn` tier could ignore sub-4dp overlaps, which cannot be a real
    collision — and `document-viewer` has no `-bottom` companion, which is why it alone lacked the
    disambiguating evidence the other two had.

#### 10b — The ML Kit delegate ✅ built 2026-08-24

`tools:remove` on `android:screenOrientation`, plus `configChanges` so the invisible delegate survives
a rotation instead of being recreated — which is the concern the library's own manifest comment states.
`tools:remove` rather than `tools:replace` with a value because lint's `DiscouragedApi` flags any fixed
`screenOrientation` without reading it.

- [x] Verified against the artifact: `aapt2 dump xmltree` over the APK shows **zero** `screenOrientation`
      attributes and `configChanges=0x0fa0` on the delegate. ⚠️ Read the **compiled** manifest, not the
      text one — the text merged manifest keeps XML comments, so a grep there hits our own explanation.
- [x] **Rotated mid-scan** ✅ 2026-08-25, and the second run on the **minified** artifact: landscape
      mid-scan, a page captured, rotated back — same `ActivityRecord` id throughout, page intact, and the
      finished scan saved into the app. Nothing lost, so the override stays out.
- [x] **`scripts/aab-permissions.py` now asserts it** ✅ 2026-08-26, so a dependency bump cannot quietly
      re-lock the screen. ⚠️ **The primitive decoder this box was written around turned out not to be
      needed, and that was checked rather than assumed**: a fixture manifest compiled with `aapt2 link
      --proto-format` shows `screenOrientation` keeping its source string (`portrait`) *alongside* the
      compiled int, unlike `android:required`, which has no text form at all. The int path is still
      there — for a value set by resource reference, which does lose its literal — but detection never
      reaches it. Proven both ways: green on the real bundle, and exit 1 naming both locked activities
      on the fixture. The delegate's `configChanges` prints as context beside the permission guards.

#### 10c — R8 ✅ built 2026-08-24

The comment in `app/build.gradle.kts` set the condition: *"turn it on when there is a shipped build to
turn it on against, and watch that build run."* **1.8.0 is live in production.** The condition is met.

- [x] `isMinifyEnabled = true`; the comment **rewritten**, not deleted — it now records the condition
      being met, and why it took from 3a to here to meet it.
- [x] `app/proguard-rules.pro` created. It holds **no keep rules**, and that is the finding: every
      reflection-shaped thing here is already covered by a rule a dependency ships. The file records
      what was checked against `mapping.txt` / `usage.txt` / `configuration.txt`, so the next person
      adds a keep with evidence rather than on suspicion.
- [x] `isShrinkResources` left **false**. One variable at a time; `aab-locale.py` still counts 737
      base strings and all 8 shipped locales in the minified bundle.
- [x] `-keep` for `BinkyBackupAgent` — **not needed, and that was checked rather than assumed.** AGP
      generates `aapt_rules.txt` from the merged manifest and `android:backupAgent` is one of the
      attributes it reads, so the agent survives under its own name with `onFullBackup` and
      `onRestoreFinished` intact. A rule here would have been a no-op nobody could later prove was one.
- [x] `mapping.txt` rides inside the AAB — `BUNDLE-METADATA/com.android.tools.build.obfuscation/
      proguard.map`, 65 MB uncompressed. Play deobfuscates crashes without an upload step.
- [x] **The artifact checks re-run on the minified bundle**, which is the whole reason 10c goes first:
      `aab-permissions.py` still reads 8 permissions and 0 `<uses-feature>`, and 10b's compiled-manifest
      check still finds zero `screenOrientation` with `configChanges` intact.
- [x] **Proved by behaviour** ✅ 2026-08-25 on the minified build — and it found a defect every static
      check had passed. **Enums round-trip by name**: an observation written *by the minified build*
      stored `MANY`, `EATEN`, `BRIGHT`, `MORE`, `LARGE`, `ROUND`, none of them an ordinal. **The daily
      sweep fires**: `WM-WorkerWrapper: Starting work for …ReminderSweepWorker`, SUCCESS — WorkManager
      resolved the worker from its persisted class name under R8. **Export writes** a zip, so
      kotlinx.serialization's write path survives.
- [x] ✅ **The restore half is done** 2026-08-25, on the minified build, and the roots drawer turned out
      not to be on the path at all. **Choosing an export folder is what unblocks it**: `OpenDocumentTree`
      opens on DocumentsUI's last-used location with a *USE THIS FOLDER* button already on screen, and
      once a folder is set, `OpenDocument` reopens in that same location with the export sitting in it.
      The drawer only ever needed opening because nothing had put a file somewhere the picker already
      looked. Round trip: an *Everything* export written straight to the folder (4.48 MB), a 1234 g
      weighing recorded **after** it so a no-op restore could not pass, then the restore — manifest
      parsed (*"Everything backup from Aug 25, 2026, 6:21 PM"*, so kotlinx.serialization's read path
      survives R8), *13 images came from the backup*, and a pre-restore snapshot preserved. Verified by
      installing a plain `assembleDebug` over the top and reading the database: **all 22 tables back to
      their baseline counts, the 1234 g row gone**, 5 photo files / 5 rows and 8 document pages / 8 rows
      on disk. ⚠️ The intermediate state was read from the **UI**, not a pulled database — the
      release-shaped build is not debuggable, so `run-as` is unavailable while it is the installed one.
- [x] 🔴 **R8 silently disabled the guided document scanner, and it is fixed** ✅ 2026-08-25. It did not
      crash: `MlKitDocumentScanner` catches everything and falls back to the plain camera by design, so a
      feature owners have simply stopped existing behind one log line. ML Kit's registrar is named inside
      a manifest **meta-data key**, which `aapt_rules.txt` does not read, so R8 kept the class and shrank
      away its no-arg constructor — `NoSuchMethodException: CommonComponentRegistrar.<init> []`, then an
      NPE building the client. Fixed by one evidence-backed rule in `proguard-rules.pro`
      (`-keepclassmembers class * implements …ComponentRegistrar { <init>(); }`); the minified build now
      opens `DocumentScanningActivity` with zero fallback lines. **`proguard-rules.pro` no longer holds
      zero keep rules**, and §3 of `phase-10.md` says why that changed.

- [x] **`scripts/aab-reflection.py`** ✅ 2026-08-26 — the gate that was missing when this broke. Every
      class named in an `<meta-data>` for reflection is looked up in the artifact's dex and must have a
      public no-arg constructor. It reads the class names **out of the manifest** (any `<meta-data>`
      whose value is one of three markers: Firebase's registrar sentinel, `androidx.startup`, and
      datatransport's `backend:` namespace) rather than from a list that would go stale, and finds five.
      ⚠️ **`mapping.txt` cannot answer this** — R8 writes a bare `CommonComponentRegistrar ->
      CommonComponentRegistrar:` line with **no members under it** for a class it kept unrenamed, so
      absence from the mapping is not absence from the artifact. The dex is what gets read: class_defs
      for "this artifact *defines* it", then the class's own direct methods, because a method_id alone
      could be satisfied by a caller elsewhere. **Proven by removing the keep rule and rebuilding**: the
      check reports `GONE CommonComponentRegistrar()` and exits 1 on that bundle, and green on the one
      with the rule. Wired into both publish workflows, which now run four `aab-*.py` checks.

**Size:** the AAB goes **12.3 MB → 8.1 MB**, a third off, with resource shrinking still switched off.

**What was verified, and what it rules out.** The static half is genuinely done, because R8 writes down
what it did and the answers were read out of that rather than guessed:

- **Enum names survive.** R8 renames the constant *fields* (`DoseStatus.GIVEN -> e`) but never the name
  string passed to the enum constructor, because `Enum.valueOf` reads it — so `.name`, which is what the
  converters write to the database, is unchanged. Confirmed by grepping the compiled dex: `WITHDRAWN`,
  `LEFT_UNEATEN`, `KILOGRAMS` and the rest are all present verbatim. ⚠️ **No rule pins this** — the usual
  `-keepclassmembers enum *` keeps *field* names, which is not what `.name` returns. That is why the
  behaviour proof above stays open rather than being closed by the dex reading.
- **Worker class names survive**, which is the cross-version one: WorkManager persists the worker's class
  name in its own database, so a sweep enqueued by 1.9.0 has to still resolve after the update to 1.10.
  `androidx.work` ships `-keepnames class * extends androidx.work.ListenableWorker` for exactly this, and
  `ReminderSweepWorker` and `UpdateCatchUpWorker` are both unrenamed in `mapping.txt`.
- **`BunnyDatabase_Impl` is unrenamed**; the DAOs are renamed, which is fine — nothing looks those up by
  name.
- **`@Serializable` survives**: `Companion -> Companion` and the `$$serializer` INSTANCE fields are kept
  by kotlinx.serialization's own rules. Renaming the classes is harmless — a `serialName` is a compile-time
  string literal, so an owner's archive does not change shape when R8 renames the class that reads it.
- **`WeightSource` was removed entirely** and that is correct, not a loss: it is derived from
  `visitId != null` and never stored, and nothing in the release variant reads it.

#### 10d — Several photos on a tray (schema 8) ✅ built 2026-08-24

Owner request, 2026-08-23. `observations.trayPhotoPath` became `observation_photos`, following ADR-0029's
own shape for the multi-valued droppings fields. Amendment on **ADR-0029**, not a new ADR.

- [x] Entity + join table, tray-level, denormalised per participant, **replaced not merged**. It carries a
      `position` the droppings tables do not — order is part of *this* fact and not of theirs — and no
      `createdAt`, because nothing would read one.
- [x] `MIGRATION_7_8` — the create-copy-drop-rename rebuild, with **all three** cascade-carrying children
      staged and restored, and the old path read into the new table before anything drops.
- [x] The refcount rule survives, its wording intact and its query moved to `observation_photos`. Every
      path that leaves an edit is diffed against what was there and each orphan checked on its own.
- [x] Cap at **6**, checked against `AutoBackup`'s budget rather than picked: ~0.5 MB a frame against a
      20 MB newest-first queue shared with documents makes six about 3 MB for one thorough tray.
- [x] Copy ×9 — three new keys and one reworded; `translation-gate.py` reports 683 × 8 complete.
- [x] **Proven on the phone**: `Migration7To8Test` (6 tests) and the full instrumented suite, **226 tests**,
      all passing on the device. The migration test counts rows for all three children with values spread
      across three observations, so a recipe that staged two out of three fails.
- [x] **The screens seen running**, on a new `tray_photos` seed variant — the default seed records no
      tray photo at all, so both states were unreachable without one. The timeline draws the first photo
      with a **+3** badge; the form wraps four thumbnails onto two lines with their remove controls and
      leaves both add buttons on screen. `observations-tray-photos` reports **clean**;
      `observation-entry-tray-photos` reports `drawn=0 touch=3`, which is **not** a regression — the
      unmodified `observation-entry` scene already reports two such findings with a *larger* overlap, and
      phase-7.5.md's rule is that an unlabelled `touch` hit area says nothing on its own.
- [x] **The other three configurations** ✅ in 10a's matrix. `observations-tray-photos` is clean in all
      four; `observation-entry-tray-photos` reports **no `drawn` finding in any of them** and only
      `touch`-tier hit areas, tracking the unmodified `observation-entry` baseline scene cell for cell.

**The SQL was verified against `schemas/8.json` mechanically, not by eye**: every `CREATE TABLE` and
`CREATE INDEX` in `MIGRATION_7_8` is a byte-for-byte transcription of the exported shape. That is the
house rule stated in the migration's own doc, and now the way it was actually checked.

#### 10e — Events ✅ built 2026-08-25 (same schema 8)

Owner request, 2026-08-23: *"a calendar or event list — when was the last vet visit, the last nail trim,
or other events the user would like to remember."* An agenda derived from records that already exist,
plus a new dated record an owner writes. **[ADR-0031](adr/0031-an-event-is-a-dated-label-and-the-timeline-is-derived.md)**
carries it; the build record is [`phase-10.md`](phase-10.md) §5.

- [x] `events` table — per bunny, free label, **no type enum and no recurrence** (care reminders own
      repetition; two spellings of one fact is what this codebase keeps refusing). Folded into the same
      `MIGRATION_7_8`, so **no second migration and no `BUNNY_SCHEMA_VERSION` change**.
- [x] The timeline **stores nothing** — `ui/events/Timeline.kt` merges `EventDao`, `VisitRepository`,
      a new `CareDao.completionsForBunny` join and `CareSchedule`. Weighings, observations and doses
      stay out of the default set; each already owns a screen.
- [x] Entry points: the **first** row in `MoreScreen` and a compact card on Home from
      `timelineHighlights(sections, 1, 2)`. **No sixth bottom tab** (ADR-0015).
- [x] Reminding via the one daily sweep (ADR-0024), never an exact alarm (ADR-0003) — a **fifth
      notification channel**, because muting weekly care nagging must not mute next Thursday's
      neutering.
- [x] ADR-0014's calendar hand-off extends to an event for free — same `ACTION_INSERT`, no `RRULE`.
- [x] Copy ×9 — 31 new resources, gate green.
- [x] JVM: `TimelineTest`, `EventSweepTest`. Instrumented: `EventRepositoryTest` (round-trip, the day
      query, both stamps, the cascade on bunny delete) — **9 green on the phone, 2026-08-25**, and the
      whole instrumented suite green at 235.
- [x] **Device proof by hand** ✅ 2026-08-25, all three on the minified build: the timeline on a real
      database (a care schedule and a vet visit, two derived sources, upcoming above past, grouped by
      month); an event dated today notified by the forced sweep on `channel=events` — *Nail trim ·
      Today, for Sznycel.*, sitting in the shade beside the seed's own **care** reminder of the same
      name, which is the fifth channel's argument observed rather than asserted; and the hand-off opening
      Google Calendar with *New event: Nail trim, August 25* prefilled.

#### 10f — A light/dark override in Settings ✅ built 2026-08-25

Default stays *follow the phone*; Settings gains *System / Light / Dark*. The build record is
[`phase-10.md`](phase-10.md) §6.

- [x] ⚠️ **`AppCompatDelegate.setDefaultNightMode`, not a Compose flag alone.** A Compose-only override
      leaves the window background (painted before Compose composes) and the `values-night/` scrim from
      10a following the *system* while the app follows the override — the exact mismatch 10a exists to
      prevent, and it shows on API 26–28. `theme/NightMode.kt` is the one call site, reached from
      `BinkyApplication.onCreate` and from `SettingsViewModel.setThemeMode`.
- [x] 10a's `SystemBarAppearance` needs no change: it already keys off `BinkyTheme`'s `darkTheme`.
      It needed none.
- [x] Amendment to **ADR-0027** — that decision gaining a lever, not a new one.
- [x] Copy ×9 (section label + three options) — 4 new resources, gate green at 718.
- [x] **Device proof by hand** ✅ 2026-08-25, by pinning `cmd uimode night` and sampling pixels rather
      than eyeballing: phone Light + app Dark → background and both bar strips `(22,19,13)`; phone Dark +
      app Light → `(255,248,239)`, **across a cold start**; phone Dark + app System → dark. Both
      divergent directions, so neither can be the phone leaking through, and all three chips repaint in
      place with no restart. ⚠️ *Same as this phone* needs ~2.5 s to settle where the other two take
      0.6 s — a screenshot taken too early reads exactly like "it did not repaint", and briefly did.

---

### Standing decisions changed this phase

- **Play screenshots are light-only** (2026-08-24). This reverses the old rule that *dark is the set to
  upload*. The app keeps both themes and `screenshots.py` keeps both cells — it is a decision about what
  goes in the Console, nothing else. Correct it in `store-listing.md` where that file names the set.
- **A screenshot filename carries its locale** — `home-pl.png`, not `home.png` ✅ done 2026-08-24. Nine
  locales of one screen were otherwise nine files distinguished only by their folder, and a PNG loses
  its folder the moment anyone moves it.
- **The fixture bunnies are Lily and Sznycel** ✅ done 2026-08-24. ⚠️ The drivers tap the bunny **by
  name**, so `edge-to-edge.py` and `alarm-gate.py` moved with the seeder. **Reseed before the next
  driver run** — a phone still holding the old seed will fail on its first tap.

### Decided at the end of the phase

- **Can the four configurations run in CI instead of serially on the one phone?** **Yes — decided
  2026-08-25, and built.** The full four-config matrix runs on emulators at **API 26 / 34 / 36**, the
  same three levels `instrumented` already covers. `ci.yml` was already running emulators with KVM, so
  this extended an existing pattern rather than starting a project.

  - **Nightly and `workflow_dispatch`, never `pull_request`**, and deliberately *not* wired into
    `instrumented-gate`. `edge-to-edge.py` walks ~75 scenes a cell through uiautomator taps whose
    `settle()` timings are tuned to real hardware, and an emulator is where those go flaky rather than
    fail. A flaky required check is one people learn to re-run without reading. Promote it to
    `pull_request` after a few weeks of steady nightlies, not before.
  - **The device-family seam landed where the research said it would**: `set_nav_mode` in
    `edge-to-edge.py`. HyperOS drives navigation mode from `force_fsg_nav_bar` *because* the AOSP
    `com.android.internal.systemui.navbar.*` overlays are present-and-disabled on it; everything else
    takes `cmd overlay enable-exclusive --category`. ⚠️ **The overlay path is a no-op that reports
    success on the phone**, which is why the family is *detected* and never passed in — a cell driven
    the wrong way still captures, still checks and still says "clean" against an inset that never moved.
  - ⚠️ **API 26–28 has no gesture navigation at all**, so that leg runs **two** configurations, not
    four. `usable_configs` drops the gesture cells **by name** into the report rather than capturing
    them under a label the device never matched — the same class of lie the `_PINNED` rotation guard
    exists to prevent, and worse in CI where nobody is watching the screen.
  - `--assert-clean` is what makes it a check: non-zero on any `drawn`-tier finding **or any SKIPPED
    scene**, because a driver that could not reach a screen has not shown it to be clean. `touch`-tier
    findings stay advisory.
  - **What still cannot move**, unchanged: the **field upgrade proof** (it crosses a Play-signed 1.0.0
    install that refuses a locally-signed APK), and anything about HyperOS itself — autostart, Doze, the
    battery-optimisation exemption.
  - ⚠️ **Written against emulators that have never run it.** There is no local KVM, so the seam, the
    two-config API 26 leg and the job itself are unproven until the first nightly. Read that run before
    trusting this box.

#### 10g — Weight entry gains a kg/g toggle ✅ built 2026-08-25

Owner request, made while the 10c restore proof was being driven: *"you can only specify in grams, so a
simple switch gram/kg when providing new / updating old data"*. Folded into Phase 10 rather than deferred,
because Phase 10 is explicitly the phase that takes whatever owners report — 10d and 10e arrived the same
way.

⚠️ **It reverses a stated house rule, so it is a decision and not a tweak.** `CLAUDE.md` said *"entry is
in grams"*, and the app said so to the owner in two places. All three moved together.

- [x] **Two preferences, not one.** `weightEntryUnit` defaults to **grams**; the display preference
      defaults to **kilograms** and is untouched. Reusing the display one would have moved every existing
      owner's field to kilograms at a stroke — and `2495` typed into a kilogram field is exactly the
      fat-fingered reading the *recent weighings* line exists to catch. Making it form-only instead would
      make an owner who thinks in kilograms re-choose on every weighing.
- [x] **Storage does not move.** `Int` grams on disk, verified on the phone: `1,2` typed as kilograms
      landed as `(1200, 'integer')`.
- [x] **Both separators, both directions.** `.` and `,` are accepted on input and the locale's own is used
      on output, because which one arrives is decided by the keyboard rather than the app's locale. A
      Polish phone offers a comma; refusing it would fail the ordinary case.
- [x] **The echo became the safety net and is now unconditional** — whichever unit the field is in, the
      other is spelled out underneath. `2495` entered as kilograms reads back *"That is 2 495 000 g."*,
      which is unmissable in a way a silently-accepted number is not.
- [x] The field caps kilograms at three decimals rather than rounding a fourth away silently, and
      *recent weighings* renders in the **entry** unit so the magnitude comparison stays like-for-like.
- [x] Entry text carries **no grouping separator** — it goes back into the box, and "2 495" re-parses as
      a different number. That is the one place `weightEntryText` must differ from `gramsNumber`.
- [x] Copy ×9 — 2 new strings, and `settings_weight_unit_help` reworded because it asserted the old rule.
      Gate green at **720 × 8**.
- [x] 13 JVM tests in `WeightFormatTest`. One pinned a behaviour worth keeping: `"1."` parses as 1000 g
      rather than null, so the echo holds steady mid-typing instead of blinking out and back.
- [x] **Shared, not copied.** `ui/weight/WeightAmount.kt` holds the field's whole state machine —
      the text, its unit, and the transitions — plus the `WeightUnitChips` composable. Both weight
      fields in the app use it: *Record a weighing* and the **visit editor**, which is the one that
      needed it most, since that number is usually copied off a vet's note. What is deliberately
      *not* shared is the field itself: the weighing form's box is `6e`'s oversized hero and the
      visit editor's is an ordinary optional row, and one composable forced to be both would be
      worse than either.
- [x] **The two preferences are proven not to collide** ✅ on the phone: with display set to
      **Grams** and entry to **Kilograms**, the flag banner read *"2,380 g then, 1,200 g now."*
      while the form still read *"Weight in kilograms"* with its recent-weighings row in kilograms.
      Neither preference moves the other, and Settings' own help line now says so.
- [x] **Driven on the phone**: chips default to Grams, `2495` → toggle → `2.495` with the help line,
      echo and recent-weighings row all following; `1,2` saved and stored as 1200. The visit editor
      does the same, opening in the unit the weighing form was left in — one preference, two
      screens.
