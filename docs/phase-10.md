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

⚠️ **The restore half was not completed on this phone.** *Restore from a file* opens SAF, and this ROM's
document picker will not open its roots drawer to `input touchscreen tap` — the SAF limitation already on
record. The write path is proven; the read path still rests on the instrumented restore tests, which run
unminified. One hand restore before the release would close it.

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
