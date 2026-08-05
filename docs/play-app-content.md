# Play Console — App content answers

Paste-ready answers for every **App content** section. These gate publishing to *any* track,
internal included, which is why `docs/PLAN.md` puts them in 3a rather than at 3g.

Two rules for keeping this honest:

- **Play cross-checks the Data safety form against the privacy policy.** A mismatch between the two
  is a rejection reason on its own, so [`privacy-policy.md`](privacy-policy.md) and the answers below
  have to move together. If one changes, change the other in the same commit.
- **Every factual claim below was verified against the built release artifact**, not against
  intent. Where a claim is a judgement call rather than a fact, it is marked ⚠ and says why.

First verified at `versionCode` 88, and **re-verified at 3h on `versionCode` 136** — the build that
actually goes up. That re-check matters: photos, backup, first-run setup and the language switcher all
landed in between, and any one of them could have pulled in a permission. None did.

| Claim | How it was checked |
| --- | --- |
| No user-facing permissions | `aapt2 dump badging` lists exactly one, `binky.bunny.and.rabbit.tracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a signature-level permission AndroidX defines for its own non-exported receivers. Not user-visible, not a Play sensitive permission, nothing to declare. |
| No notification permission | No `POST_NOTIFICATIONS`, which is 3f's promise surviving into the artifact rather than staying an intention. The Photo Picker and `TakePicture` route means no `CAMERA` and no media permission either. |
| No advertising ID | No `com.google.android.gms.permission.AD_ID` in the artifact. |
| No network code of our own | No `INTERNET` permission is declared. |

### 1.1 declares six permissions, and the table above stops being true for the first two rows

Written at **4a**, when the first two entered the manifest, and **re-verified against the 1.1 artifact
at 4h — `versionCode` 191, `versionName` 1.1.0**. The rows above stay as the record of what 1.0.1
shipped, because "no permissions" is a claim about a build and not about a project.

**Two of the six are ours. The other three arrived on their own**, merged out of WorkManager's manifest
by the manifest merger, and that is the finding 4h existed to produce: the note written at 4a said
*two*, and the artifact says six. Nothing in the app's own source declares them. This is precisely the
hazard the advertising-ID section below warns about — a transitive dependency writing permissions into
the merged manifest — arriving in a place nobody was watching for it.

Read this list from `scripts/aab-permissions.py`, which walks the AAB's protobuf manifest, and not from
`strings | grep`: a grep cannot tell a `<uses-permission>` from a `android:permission` guard on a
service, and three of the strings in this artifact are the latter.

| Permission | Ours? | Why, and what it changes on the Console |
| --- | --- | --- |
| `android.permission.POST_NOTIFICATIONS` | yes | Care reminders and the watch check-in. Runtime on API 33+, install-time below. **Not a Play sensitive permission** and there is no declaration form for it — it needs no justification, only that the Data safety answers and the store listing stop implying the app never notifies. It is never requested from a bare system dialog: ADR-0006 puts our own screen in front of it, in first-run setup and at the point of use. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | yes | Puts the daily sweep back after a restart (ADR-0024). Install-time, invisible to the owner, not sensitive, no declaration form. |
| `android.permission.WAKE_LOCK` | **WorkManager** | Holds the CPU awake for the few seconds a worker runs. Normal, install-time, invisible, not sensitive, no declaration form. |
| `android.permission.ACCESS_NETWORK_STATE` | **WorkManager** | Lets WorkManager evaluate a `NetworkType` constraint. **This is not network access** — it reads connectivity state, it does not open a socket. This app sets no network constraint on any work, so the capability is never exercised either. *(At 1.1 this row also said `INTERNET` was absent from the artifact. That stopped being true at 1.2 — see below.)* |
| `android.permission.FOREGROUND_SERVICE` | **WorkManager** | ⚠ See below — the only one of the three with a Console consequence worth reading twice. |
| `binky.bunny.and.rabbit.tracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | Signature-level, self-defined and self-used, for AndroidX's own non-exported receivers. Not user-visible, not a Play sensitive permission, nothing to declare — unchanged from 1.0.1. |

#### ⚠ `FOREGROUND_SERVICE`, and why it is left in place

`androidx.work.impl.foreground.SystemForegroundService` is in the merged manifest and the permission
comes with it. Play scans the manifest, so the Console may surface a foreground-service question
against an app that **cannot start one**: nothing in `app/src/main/java` calls `setForeground`,
`setExpedited`, `ForegroundInfo` or `OutOfQuotaPolicy`, and the sweep is an ordinary `Worker` under an
ordinary `JobScheduler` job. Verified by grep at 4h and worth re-running whenever work is added.

**No `FOREGROUND_SERVICE_*` typed permission is declared**, which is the load-bearing detail at
`targetSdk` 36: since Android 14 a foreground service must name a type to start at all, and the
Console's foreground-service declaration is keyed to those types. There are none here, so there
should be nothing to declare.

It is **not** stripped with `tools:node="remove"`, deliberately. WorkManager falls back to a foreground
service for expedited work below API 31, and removing the permission would convert a future
`setExpedited` call into a runtime crash on exactly the older devices this app still supports
(`minSdk` 26) — trading a Console question that may never be asked for a defect that only appears in
the field. If a reviewer does ask, the answer above is the answer.

Two permissions this app **deliberately does not declare**, both of which would be easy to reach for:

- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** It would let the app pop its own exemption dialog
  instead of deep-linking into Android's list. Play restricts it to apps whose *core function* is the
  exemption, and this app's core function is a rabbit's weight chart — declaring it invites a review
  rejection to save the owner one tap. The app reads the state (which needs no permission), explains
  it, and opens the screen where the owner decides.
- **`SCHEDULE_EXACT_ALARM`.** Care reminders are day-granularity and use WorkManager (ADR-0003); the
  exact-alarm path is Phase 5's, with medication doses, and this table will need revisiting then.
  *(It did: 1.2 declares it — see below.)*

The `<queries>` element added alongside them names one package, `com.miui.securitycenter`, so the app
can tell whether Xiaomi's autostart screen exists before offering to open it. Package visibility is
not a permission and needs no Console answer; `QUERY_ALL_PACKAGES`, which would, is not used.

### 1.2 declares eight permissions, and one of them changes an answer

Written at **5g**, when the document scanner entered the build, and read out of the artifact with
`scripts/aab-permissions.py` rather than out of the source.

The two additions are **`SCHEDULE_EXACT_ALARM`**, which is ours and was expected, and
**`android.permission.INTERNET`**, which is not ours and was not.

| Permission | Ours? | Why, and what it changes on the Console |
| --- | --- | --- |
| `android.permission.SCHEDULE_EXACT_ALARM` | yes | Medication dose reminders fire at a clock time (ADR-0003). Deliberately **not** `USE_EXACT_ALARM`, which is auto-granted but restricted by Play to apps whose core purpose is alarms or calendars — a pet health tracker is not one (ADR-0009). On Android 14+ it is denied by default and the app deep-links to the system screen; denied, doses still arrive best-effort and the app says so in words. |
| `android.permission.INTERNET` | **no — ML Kit** | ⚠ See below. |

#### ⚠ `INTERNET`, and the claim it forces us to reword

The row at the top of this file said *"No network code of our own — no `INTERNET` permission is
declared."* **The second half of that sentence stopped being true at 1.2, and the first half did
not.** It is reworded here rather than deleted, because what it was actually asserting is still the
case and is still what the Data safety answers rest on.

The permission is merged in by `com.google.android.datatransport:transport-backend-cct:2.3.3`, which
arrives as a transitive of the ML Kit document scanner — Google's own telemetry transport, not the
scanner API. Traced from the manifest merger's blame report, not guessed:

```
uses-permission#android.permission.INTERNET
ADDED from [com.google.android.datatransport:transport-backend-cct:2.3.3]
```

It is the same hazard as 4h's WorkManager finding and the advertising-ID section below, arriving a
third time — and this one is bigger, because it touches a claim rather than only a list.

What is true, and what should be said on the Console:

- **No code in `app/src/main/java` opens a socket.** ADR-0011 forbids it. The app has no backend, no
  analytics, no crash reporter and no update check of its own. That is a property of *this app's*
  code and it is unchanged.
- **A Google library on the device may talk to Google**, as Play services already does for every app
  on the phone. The bunny's records are not what it would carry: nothing in this app hands any record
  to any Google API. The scanner is given a camera session and returns image files; it is never
  passed a name, a weight, a dose or a document.
- **The honest phrasing** is therefore *"Binky's own code makes no network requests"*, not *"the app
  cannot reach the network"*. §7 below says it that way, and so does the privacy policy — the two
  move together, per this file's own rule at the top.

If that trade is ever judged not worth it, the contingency is written down and cheap: ADR-0009 puts
the scanner behind an interface with a plain-camera fallback, so dropping ML Kit is one line in
`AppContainer` and loses auto-crop and page detection only. Documents as *data* bring no dependency
at all. Measured at 5g, the dependency also costs **+534 KB** of AAB (11,404,302 → 11,951,123 bytes).

#### What ML Kit did **not** bring, asserted rather than assumed

- **No `CAMERA` permission.** The scanner's own UI and the `TakePicture` fallback both run on the
  system camera intent, which needs none. Declaring it would make a camera *required at install* and
  change who can see the app on Play. `scripts/aab-permissions.py` now lists `CAMERA` as **forbidden**,
  so a future dependency that merges one fails the check instead of shipping quietly.
- **No `<uses-feature>` at all** — the half of the merged manifest no permission list would show. A
  merged `android.hardware.camera` at `required="true"` (the default when the attribute is omitted)
  would filter the app off every device without a camera. The script grew a `uses-feature` section at
  5g precisely so this is checked; the artifact declares none.
- **No media permission.** `setGalleryImportAllowed(false)` is set on the scanner options for that
  reason as much as for the UX one — importing from the gallery is the Photo Picker's job elsewhere
  in this app.

---

## 1. Privacy policy

```
https://srednimax.github.io/binky-app/privacy-policy.html
```

Returns HTTP 200; the site root 404s deliberately, which is fine — Play checks the policy URL only.

## 2. App access

> **All functionality is available without special access.**

No account, no sign-in, no region lock, no paywall. Nothing for a reviewer to be given credentials
for.

## 3. Ads

> **No, my app does not contain ads.**

No ad SDK, no advertising ID, no paid promotion of any kind.

## 4. Content ratings (IARC questionnaire)

Category: **Utility, Productivity, Communication or Other** — not a game.

Answer **No** to every content question: violence, sexuality, language, controlled substances,
crude humour, horror, gambling and simulated gambling.

The three that are worth reading twice, because they are about *plumbing* rather than content:

| Question | Answer | Why |
| --- | --- | --- |
| Does the app allow users to interact or exchange content with each other? | **No** | There is no server and no other user to interact with. |
| Does the app share the user's location with other users? | **No** | No location is collected at all. |
| Does the app allow users to purchase digital goods? | **No** | Free, no tier, nothing locked. |

The export-a-backup feature is **not** user-to-user sharing: it hands a file to the system share
sheet at the moment the owner asks, to a destination they pick. That is the OS's sharing, not the
app's.

Expected outcome: IARC 3+, PEGI 3, ESRB Everyone, USK 0, ACB G.

## 5. Target audience and content

> **Target age group: 18 and over.**
> **Could your app appeal to children? No.**

⚠ **This is a judgement call, and 13+ would also be defensible.** Selecting any bracket under 18
pulls the app into the Families programme and its extra review, design and disclosure obligations —
for an app whose audience is people who own and take a rabbit to a vet. 18+ also matches the privacy
policy's "not directed at children", which is the consistency Play actually checks.

The icon is a rabbit, which is cute; cute is not the same as child-directed. The listing uses no
child-directed language, characters or play patterns.

## 6. News apps

> **No, my app is not a news app.**

## 7. Data safety

**The headline answer: does your app collect or share any of the required user data types?**

> **No.**

Play defines *collection* as transmitting data off the device. **Binky's own code makes no network
requests**, so nothing the owner enters leaves the phone. Everything is in the app's private storage,
readable only by the app.

Reworded at **1.2**, and the wording is the point. Until 1.1 this paragraph rested on the artifact
declaring no `INTERNET` permission; from 1.2 the artifact declares one, merged in by a transitive of
the ML Kit document scanner (see §1.2 above). The claim that matters is unchanged and is now stated
as what it always was: a claim about **this app's code**, not about what a Google library on the
device is capable of. No record — no name, weight, dose, observation or document — is handed to any
API that could send it anywhere. The scanner is given a camera session and returns image files.

That single answer collapses most of the form. What may still be asked:

| Question | Answer |
| --- | --- |
| Is all user data encrypted in transit? | **N/A** — no data is ever in transit. If the form forces a choice, the honest reading is Yes-by-vacuity; prefer N/A where offered. |
| Do you provide a way for users to request their data be deleted? | Records are deletable individually in the app, and uninstalling removes everything. Because nothing is ever received, there is no deletion request to send anyone. |
| Does your app use an advertising ID? | **No** — see the re-check trigger below. |

#### The advertising-ID answer has an expiry date

Play's own warning on that question is that a transitive SDK can merge
`com.google.android.gms.permission.AD_ID` into the merged manifest without the app ever declaring it.
So it is answered from the **artifact**, and at 3h (`versionCode` 140) the artifact says: no `AD_ID`
string in the merged manifest, no ads SDK in the bundle, and — the load-bearing one — **no Play
Services on `releaseRuntimeClasspath` at all**. There is no SDK present that could merge it.

**Re-verified at 4h on `versionCode` 191**: still no `AD_ID` in the manifest, still zero matches for
`play-services` on `releaseRuntimeClasspath`. The answer holds for 1.1. It is worth noticing *why* it
held, though — not because the hazard is theoretical, but because no dependency capable of it was
added. The same release brought in three permissions from WorkManager by exactly this route
(see the permission table above), which is the proof that the mechanism this section describes is
live rather than hypothetical. `scripts/aab-permissions.py` now asserts `AD_ID`'s absence on every
run, so 1.2's ML Kit dependency cannot land quietly.

That last fact is what makes the answer safe, and it is exactly what **changes at 1.2**: ML Kit's
document scanner needs Play services (ADR-0009). When that dependency lands, GMS enters the classpath
and this answer must be **re-verified against the artifact rather than inherited**:

```bash
unzip -p app/build/outputs/bundle/release/app-release.aab base/manifest/AndroidManifest.xml \
  | strings | grep -i AD_ID
./gradlew -q app:dependencies --configuration releaseRuntimeClasspath | grep -i play-services
```

**Done at 5g, and the answer still holds — for a different reason than before.** GMS is now on the
classpath: `play-services-mlkit-document-scanner`, `play-services-base`, `play-services-basement`,
`play-services-tasks`, `com.google.mlkit:common`, four `firebase-*` encoder artifacts and three
`datatransport` ones. So the old proof — *"there is no SDK present that could merge it"* — is gone,
and it is replaced by the weaker but sufficient one: **the artifact contains no `AD_ID`**, asserted by
`scripts/aab-permissions.py` on every run rather than by a grep somebody remembers to type. That is a
real downgrade in the *kind* of evidence available, and it is written down here rather than glossed,
because the next dependency that touches ads would now have somewhere to hide.

The same dependency did merge `INTERNET` (§1.2) — which is the mechanism this section describes
firing for a third time, and the reason the assertion has to live in a script rather than in prose.

A related non-finding worth writing down so it is not re-investigated: `android.permission.DUMP`
appears in the merged manifest as `android:permission` on `androidx.profileinstaller`'s
`ProfileInstallReceiver` — and, since 1.1, on WorkManager's `DiagnosticsReceiver` as well.
`android.permission.BIND_JOB_SERVICE` appears the same way on WorkManager's `SystemJobService`. All
three are guards on who may *call* the component — shell and system — not permissions the app
requests. `scripts/aab-permissions.py` prints them under a separate marker for exactly this reason,
and lists no `uses-permission` for any of them.

### ⚠ Android Auto Backup — the one a reviewer may query

`android:allowBackup="true"`, so on a device with backup switched on and a Google account signed
in, Android may copy the app's data to the owner's **own** Google account, encrypted.

This is **not** collection by the app: it is a service between the user and Google, the app neither
sees nor receives it, and Play's guidance treats it that way. It is disclosed in the privacy policy
already, which is the right posture — disclosed and correctly categorised beats undisclosed.

### The photo-gallery exclusion — closed, and demonstrated

The privacy policy says *"Your photo gallery in the app is deliberately excluded from it."* When this
file was written at 3a that was a promise about unwritten code: the manifest referenced neither
`android:dataExtractionRules` nor `android:fullBackupContent`, and both XML files were still AGP
template stubs with every rule commented out.

The rules were wired up at 3c/3e — `fullBackupContent` and `dataExtractionRules` split at API 31 and
`minSdk` 26 needs both — and **3g proved it on the phone**: `bmgr backupnow` ran with **60 MB** in
`files/photos` and transferred only the database. The policy sentence is now a demonstrated fact
rather than an intention, which is the state Play's cross-check wants it in.

## 8. Government apps

> **No.**

## 9. Financial features

> **My app doesn't provide any financial features.**

No payments, no lending, no crypto, no IAP.

## 10. Health apps

> **No** — with the caveat below.

⚠ **Read the Console's exact wording before answering.** Play's Health apps policy and its
declaration are written for **human** health: medical devices, patient management, health research,
clinical decision support. Binky records observations about an *animal*, which is why the listing
sits in **Lifestyle** rather than Health & Fitness (see [`store-listing.md`](store-listing.md)).

If a question asks generically whether the app handles "health data" without qualifying it as human,
the accurate answer is that it records pet health observations and makes no diagnostic or medical
claim — ADR-0001 is the standing rule, and the listing's closing paragraph states it to the reader.
Answer to the question actually asked rather than to this heading.

---

## Not App content, but asked in the same sitting

- **Contact email** — the per-app support address, set in *Store settings*, not the account-level
  developer email.
- **App category** — Lifestyle.
- **Store listing copy, graphics and screenshots** — [`store-listing.md`](store-listing.md).
