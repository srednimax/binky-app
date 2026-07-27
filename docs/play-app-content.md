# Play Console — App content answers

Paste-ready answers for every **App content** section. These gate publishing to *any* track,
internal included, which is why `docs/PLAN.md` puts them in 3a rather than at 3g.

Two rules for keeping this honest:

- **Play cross-checks the Data safety form against the privacy policy.** A mismatch between the two
  is a rejection reason on its own, so [`privacy-policy.md`](privacy-policy.md) and the answers below
  have to move together. If one changes, change the other in the same commit.
- **Every factual claim below was verified against the built release artifact**, not against
  intent. Where a claim is a judgement call rather than a fact, it is marked ⚠ and says why.

Verified on the `app-release.apk` built at `versionCode` 88:

| Claim | How it was checked |
| --- | --- |
| No user-facing permissions | `aapt2 dump badging` lists exactly one, `app.binky.tracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a signature-level permission AndroidX defines for its own non-exported receivers. Not user-visible, not a Play sensitive permission, nothing to declare. |
| No advertising ID | No `com.google.android.gms.permission.AD_ID` in the artifact. |
| No network code of our own | No `INTERNET` permission is declared. |

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

Play defines *collection* as transmitting data off the device. Binky's own code makes no network
requests — it declares no `INTERNET` permission — so nothing the owner enters leaves the phone.
Everything is in the app's private storage, readable only by the app.

That single answer collapses most of the form. What may still be asked:

| Question | Answer |
| --- | --- |
| Is all user data encrypted in transit? | **N/A** — no data is ever in transit. If the form forces a choice, the honest reading is Yes-by-vacuity; prefer N/A where offered. |
| Do you provide a way for users to request their data be deleted? | Records are deletable individually in the app, and uninstalling removes everything. Because nothing is ever received, there is no deletion request to send anyone. |
| Does your app use an advertising ID? | **No.** |

### ⚠ Android Auto Backup — the one a reviewer may query

`android:allowBackup="true"`, so on a device with backup switched on and a Google account signed
in, Android may copy the app's data to the owner's **own** Google account, encrypted.

This is **not** collection by the app: it is a service between the user and Google, the app neither
sees nor receives it, and Play's guidance treats it that way. It is disclosed in the privacy policy
already, which is the right posture — disclosed and correctly categorised beats undisclosed.

### ⚠ A discrepancy to fix before 3c, not before the upload

The privacy policy says *"Your photo gallery in the app is deliberately excluded from it."*
**That is not implemented.** The manifest sets `allowBackup="true"` but references neither
`android:dataExtractionRules` nor `android:fullBackupContent`, and `res/xml/backup_rules.xml` and
`data_extraction_rules.xml` are still the AGP template stubs with every rule commented out. Auto
Backup therefore takes everything in `filesDir`, `avatars/` included.

It is not a false statement *today* — the photo gallery does not exist until 3c, so there is nothing
to exclude and nothing to contradict. It becomes false the moment photos land. Either wire the rules
up at 3c or change that sentence; the gallery is exactly the kind of large, replaceable data Auto
Backup should skip.

**Carried as an explicit item in [`PLAN.md`](PLAN.md) 3c**, including the detail that
`fullBackupContent` and `dataExtractionRules` split at API 31 and both are needed at `minSdk` 26.
Nothing here blocks the 3a upload.

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
