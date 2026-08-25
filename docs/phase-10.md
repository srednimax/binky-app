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
whole instrumented suite green at 235 (2026-08-25). What is still owed is the part no test can hold: the
timeline read on a real database, a notification landing on the day, and the hand-off opening a calendar.

## §6

Not yet built. The reasoning is in [`DOD.md`](DOD.md)'s box while it is live; it moves here when it
closes.

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
