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

Boxes live in [`DOD.md`](DOD.md). This file holds the reasoning.

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

Still owed: rotate the phone mid-scan. If the page is lost the override comes back out and the notice is
recorded as accepted, since Android 16 ignores the restriction on large screens regardless.

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

Both artifact scripts were re-run against the minified bundle, which is the entire reason §3 goes first:
`aab-permissions.py` still reads 8 permissions and 0 `<uses-feature>`, and §2's compiled-manifest check
still finds zero `screenOrientation`. What is owed is behaviour on the phone — enums round-tripping, an
export→restore, the daily sweep firing — batched with the rest of the phase's device work.

## §4–§6

Not yet built. The reasoning for each is in [`DOD.md`](DOD.md)'s boxes while they are live; it moves here
as each closes. The two worth flagging in advance:

- **§4's migration is the risk in the phase.** ADR-0029 wrote the create-copy-drop-rename recipe once
  and named its trap: `DROP TABLE observations` implicitly deletes every row, firing `ON DELETE CASCADE`
  on the children, and Room emits `PRAGMA defer_foreign_keys` only inside `clearAllTables`, never around
  a migration. **Since 1.5 there are three children, not the one that ADR staged.** The test has to
  assert rows, not shape — `runMigrationsAndValidate` passes happily on a database whose every symptom
  tick has been cascaded away.
- **§6 needs `AppCompatDelegate.setDefaultNightMode`, not a Compose flag.** A Compose-only override
  leaves the window background (painted before Compose composes) and §1's `values-night/` scrim following
  the *system* while the app follows the override — the exact mismatch §1 exists to prevent, visible on
  API 26–28.

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

Raised 2026-08-24, to be decided when the code is ready rather than now.

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
