# Phase 6 — Support contact — ships as 1.3

**Status: planned, not started.** The boxes to tick are in [`DOD.md`](DOD.md) §5; this file is the
reasoning behind them. The finished phases live in [`PLAN.md`](PLAN.md), which is 3 000 lines of record
and is deliberately not loaded to work on this one — read it only for a phase this file names.

**Prerequisites:** none in code. Nothing here touches the schema, the alarms, the manifest's permissions
or the dependency list, which is why it is safe to *write* while Phase 5's overnight run and Play's
testing count are still outstanding.

🔴 **But never `installDebug` while a dose alarm is armed.** Replacing the package force-stops it, and a
force-stop cancels every alarm the app has placed — the same failure [`DOD.md`](DOD.md) §1 warns about in
bold for `connectedAndroidTest`, arriving through a different door. An overnight run killed this way is
indistinguishable from a Doze failure, which is the most expensive wrong conclusion available here: it is
the one that rewrites ADR-0003. **6a needs no device at all**, so it is the right checkpoint to write
during an armed night; 6b and 6c both install, and queue behind the morning read.

**Decisions it leans on:** ADR-0001 (never be silent about what is missing), ADR-0009 (Play listing and
dependencies), ADR-0013 (localisation, and the one exception below), ADR-0015 ("coming soon" rows),
ADR-0023 (the debug build's `applicationId` suffix, which is what makes the store link a trap).

## What ships

The smallest phase in the plan, and the first one past the original roadmap. More's **Support** row goes
live, carrying four actions and one fact:

1. **Report a bug** → mail to `binky.support@gmail.com`, subject tagged `#bug`, body prefilled.
2. **Request a feature** → the same address, tagged `#feature`, body empty.
3. **Rate Binky on Google Play** → the store listing.
4. **Privacy policy** → the hosted page the listing already points at,
   `https://srednimax.github.io/binky-app/privacy-policy.html`.
5. The **app's version**, which nothing in the app shows today — so a bug report currently cannot name
   the build it is about.

**The inbox is 6a's first step, before any file names it.** The address is a Kotlin constant, sixteen
strings, a Console field and a line in the privacy policy, and all of them resolve to one mailbox that
has to exist and be able to send. Gmail also ignores dots, so `binkysupport@` and `binky.support@` are
one account and a near-miss registration collides. Confirmed live before `SupportHandoff.kt` is written.

**There is no donation link, and that is a decision rather than an omission.** Play's Payments policy
§3 exempts only **tax-exempt** donations from Play Billing, and §4 forbids an app leading users to any
other payment method "including through in-app user interface flows". A Buy Me a Coffee page paying an
individual is income, not a tax-exempt donation, so neither half helps. This is not a reading of the
tea leaves: StreetComplete — free, open source, no ads — was **rejected** under this policy for in-app
Patreon, Liberapay and GitHub Sponsors links, and the flagged item was a link to a project page that
merely *contained* donation information. The 2026 external-payment-links program does not rescue it
either: it is region-limited and scoped to purchases of in-app digital items, not donations. The
sanctioned in-app route is a Play Billing tip jar, which would flip the Console's in-app-purchase
declaration, put an **In-app purchases** badge on the listing, contradict two answers already recorded
in [`play-app-content.md`](play-app-content.md), and add a billing library to the manifest the last two
phases spent their audits keeping clean. **A rating costs none of that and is worth more to a listing
with no reviews.**

- **`ACTION_SENDTO` with a `mailto:` Uri, never `ACTION_SEND`.** `ACTION_SEND` with `text/plain` opens
  the full share sheet — Drive, WhatsApp, Bluetooth — and a bug report that lands in a WhatsApp draft
  never arrives. `SENDTO` + `mailto` resolves to a far narrower set — **but not, as this file first
  claimed, to mail apps only.** On the test phone `mailto:` resolves to Gmail *and* to PayPal, which
  registers a deep link on the scheme. The chooser is therefore not always avoidable, and that is
  accepted: the platform's own *just once / always* dialog settles it, and forcing `createChooser` would
  override a Gmail default the owner set deliberately, costing a tap on every report forever to insure
  against a case close to hypothetical. What the correction really costs is a **gate item** — see 6c,
  where "no mail app installed" can no longer be produced by disabling Gmail alone.
- **The tag travels in `EXTRA_SUBJECT`, not in the mailto query string**, and this is a trap rather than
  a preference. `#` is the URI *fragment* delimiter: `mailto:…?subject=#bug` parses `#bug` as the
  fragment and the subject arrives **empty**. It would have to be written `%23bug`, which is exactly the
  kind of escaping the next edit unescapes without knowing why. Putting the recipient in the Uri and the
  subject and body in intent extras removes the encoding question instead of answering it.
- **The subject is a constant tag followed by localised copy**, and the split is the whole point:

  ```
  EN   #bug — Bug report — Binky 1.3.0 (214)
  PL   #bug — Zgłoszenie błędu — Binky 1.3.0 (214)
  ```

  **The tag alone is a Kotlin constant**, a deliberate exception to ADR-0013, because it is addressed to
  the maintainer rather than to the sender, and the inbox is read in one language whatever language the
  report was written in. Translating it would need one filter rule per locale, and the failure when a new
  language ships without its rule is invisible: it looks exactly like nobody reporting anything. There is
  **no technical obstacle** to `#błąd` — `EXTRA_SUBJECT` is an ordinary string and the mail app
  MIME-encodes the diacritics — so this is an inbox decision, and it is recorded as one rather than
  dressed up as an encoding constraint.

  **The working filter is `subject:bug`, not `subject:#bug`, and the difference is Gmail's not ours.**
  Gmail's search index does not recognise special characters — hash marks named explicitly in Google's
  own documentation, alongside brackets, parentheses, commas and ampersands — so the `#` is invisible to
  the rule and the token doing the work is the English **word**. That does not weaken the decision above:
  `bug` is exactly the part a Polish description (`Zgłoszenie błędu`) does not contain, which is why the
  constant has to stay unlocalised. It does mean the earlier draft of this file defended the `#` with a
  filter argument the filter cannot see. The `#` stays because it is glanceable in an inbox list, and
  because matching whole tokens is Gmail's rule — so `subject:bug` cannot be tripped by the `-debug`
  suffix below, which tokenises separately.
  **Everything after the tag is localised** — the description, the button labels, the screen's
  explanation, the *(describe the bug here)* line — so a Polish sender is not left looking at an
  all-English subject in their own draft. The reporter's language is recoverable anyway: the diagnostics
  block carries the app locale, and a feature request's body says it in the plainest way there is.
- **A debug build says so in the subject.** `applicationIdSuffix = ".debug"` (ADR-0023) never reaches
  `versionName`, so without this a report from the developer's own phone is byte-identical to a real
  one — and the debug build's `versionCode` is a live git commit count, so it does not even collide
  usefully. `Binky 1.3.0-debug (214)` costs one boolean into a pure builder and one assertion.
- **The diagnostics block goes on the bug mail only, and it is a draft the owner reads.** Version,
  `versionCode`, Android release and API level, device model, and the **app's** locale rather than the
  system's, because that is the one the bug was seen in. No bunny data, no identifier, and **nothing
  leaves the phone unless they tap send** — the app itself still sends nothing, which is what keeps this
  on the right side of *no backend, ever*. The screen says what the block contains **before** the button
  is tapped, which is ADR-0001's rule against silence pointed at outgoing data for the first time.
  A feature request gets an empty body: an idea does not need a build number, and prefilled text is
  friction in the way of the sentence they came to write.
- **The block itself is not localised, and the separator is never `-- `.** Every line in it is a number
  or an identifier, addressed to the same reader as the tag — so it is the tag's argument again, and
  translating `Android 15 (API 35)` would be translating a fact. The separator matters for a reason
  that only shows up in the inbox: `-- ` on its own line is the RFC 3676 signature delimiter, and Gmail
  collapses everything below it behind a `…`. A block written that way is present, correct, and unread.
  A blank line separates it instead.
- **Rate is a link to the store listing, not the In-App Review API — and that is Google's own
  instruction**, not a shortcut taken to save a dependency. The In-App Review guide says it plainly:

  > you should not have a call-to-action option (such as a button) to trigger the API, as a user might
  > have already hit their quota and the flow won't be shown, presenting a broken experience to the
  > user. For this use case, redirect the user to the Play Store instead.

  The flow is quota-throttled (roughly monthly per user, and the number is explicitly not contractual),
  no-ops silently when the quota is spent, and reports nothing back about whether anything appeared. A
  button that sometimes does nothing is worse than no button. Avoiding the Play Core dependency is the
  second benefit rather than the reason: it keeps a **second** Play-services-dependent library out of a
  project that quarantines its first one behind an interface (ADR-0009), and it keeps the merged
  manifest — the thing 4h and 5g each found a surprise in — unchanged.

  **The button ships knowingly ahead of production, and that is written down rather than discovered.**
  Play does not offer the star widget to testers on an internal or closed track — they get a private
  feedback path instead — and production has never been available for this app (`DOD.md` §4). So for
  every one of the twelve people who will hold 1.3, Rate opens Binky's own listing with nothing on it to
  rate: the same shape of emptiness the paragraph above rejects the In-App Review API for. It ships
  anyway, because the page it opens is the app's real listing rather than an error, and because the day
  production goes live it becomes correct with no code change and no second release. The alternative —
  holding 1.3 until Play's counter clears — hands the release schedule to the thing this whole phase was
  scoped to work *around*.
- **`market://details?id=…` first, `https://play.google.com/store/apps/details?id=…` second**, then the
  same degradation the mail buttons use. Two catches, not one: no Play Store app falls through to the
  browser, and no browser either falls through to the message.
- **The `market://` intent is pinned with `setPackage("com.android.vending")`, and without that pin the
  chain above is unreachable.** `market://` is not a Play-only scheme: on the test phone it resolves to
  **Xiaomi's GetApps (`com.xiaomi.mipicks`) first** and to Play second. Unpinned, `startActivity` either
  raises a disambiguation sheet or — with a default ever set — opens GetApps, whose catalogue does not
  contain Binky. `ActivityNotFoundException` never throws, because something always resolves, so the
  `https` fallback is dead code and 6c's Play-disabled step proves nothing. `setPackage` needs no
  `<queries>` entry to *launch* (visibility never blocked `startActivity`); it makes the intent resolve to
  Play or to nothing, and "or to nothing" is exactly what the second catch was written for.
- **The package id is the release `applicationId`, written as a constant — never `packageName` or
  `BuildConfig.APPLICATION_ID`.** The debug build carries ADR-0023's `.debug` suffix, so a derived link
  opens *item not found* on **the developer's own phone**, which is the one device that will ever test
  it. The constant is `binky.bunny.and.rabbit.tracker`, with that reason written beside it, and a unit
  test asserts the built URL does not end in `.debug`.
- **No incentive, no gating, and no "do you like Binky?" pre-question.** Play forbids filtering for
  happy reviewers, and the In-App Review guidance forbids the pre-question outright even though this
  path does not use that API. The button is neutral, always enabled, and sits **below** the two mail
  buttons — the screen is for reaching a person first and the listing second.
- **The address is selectable text on the screen, and that is the fallback.** A phone with no mail app is
  unusual and entirely legal. `CalendarHandoff` already sets the pattern — try, catch
  `ActivityNotFoundException`, return false, let the caller speak — but here a snackbar alone is a dead
  end, so the address is rendered regardless and the failed launch points at it. The feature degrades
  rather than stopping.
- **`<queries>` gains `mailto`, `market` and `https` entries**, beside the `com.miui.securitycenter` one
  already there. Package visibility on API 30+ does **not** block `startActivity`, so all three launches
  work without it; what it blocks is `resolveActivity`, which returns null and makes an "is there a mail
  app?" pre-check answer *no* on every phone. So there is **no pre-check anywhere** — the try/catch above
  is the mechanism — *and* the queries entries, so that a pre-check added later cannot silently lie. The
  `https` one has a **real user** rather than a hypothetical one now that the privacy policy row ships in
  the same screen: it is both that row's launch and the Rate button's fallback's fallback.
- **Support is the app's last "coming soon".** Promoting the row empties the block below the
  `HorizontalDivider` in `MoreScreen.kt`, so the divider and `more_coming_soon` go with it, in both
  locales. ADR-0015's escape hatch stops being used, which is the state it was always meant to reach.
  **`MoreRow`'s nullable `onClick` stays** — Photos and Documents still use it to be inert while no
  bunny is in scope — so its KDoc has to stop saying a null `onClick` means "coming soon", or the file
  documents a policy the app no longer has.
- **Deleting the "coming soon" vocabulary would otherwise create the silence ADR-0001 forbids.** Today a
  dimmed, untappable Photos row is legible *because* the screen has a word for dimmed: the block under
  the divider says "Coming soon". Delete that and the greyness explains nothing — and the failing case is
  not first run but an owner who archived their last bunny, since the inert condition is
  `hasBunnyInScope == false`. So the two inert rows **swap their subtitle** for `more_needs_bunny` while
  that is false. `MoreRow` gains no parameter; the caller picks the subtitle, and the KDoc's new rule is
  one that stays true for the next inert row somebody adds: a null `onClick` means unavailable, **and the
  subtitle says why**.
- **`Support` is a new `NavKey`** — the second Phase-1 omission closed after 5c's `WeightEntry`. That
  file's promise that every route exists from Phase 1 has now been corrected twice, so it says so rather
  than quietly gaining a third entry.
- **One address, four places.** [`store-listing.md`](store-listing.md) and
  [`play-app-content.md`](play-app-content.md) both name a per-app contact email set in *Store settings*,
  and [`privacy-policy.md`](privacy-policy.md)'s Contact section defers to "the developer email address
  listed on the app's Google Play listing". Setting `binky.support@gmail.com` there makes all three
  agree; skipping it ships an app pointing at an inbox the listing does not name. The fourth place is the
  screen itself, where the address is rendered as selectable text.
- **The privacy policy gains two sentences, and one of its existing ones stops being true.** The Contact
  section is not the part this phase disturbs. These are:
  - *What you choose to send* — today it names the backup export and attached photos. A support mail is
    the third thing, and the first addressed **to the developer**; it says what the block contains, that
    it is a draft the owner reviews, and that it reaches an inbox a person reads.
  - *Deleting your data* — "Because we never receive your data, there is nothing for us to delete on your
    behalf and no request you need to send us." The moment someone taps send you hold their address,
    their phone model, their Android version and their app locale. It narrows to "we never receive **your
    records**" and gains a line offering erasure of correspondence.

  The app's behaviour is unchanged and still impeccable: it transmits nothing, the mail client does, and
  the screen says so *before* the button is tapped. This is documentation debt the phase creates, so the
  phase pays it — with the date bumped and the page republished before 1.3 goes up, because the listing
  links it. **The Data safety declaration stays "collects nothing"**, and the reason is recorded in
  `play-app-content.md` rather than left as a triviality: Play scopes collection to what the *app*
  transmits off the device, and a user-composed mail from their own client is not the app transmitting.
  An unwritten judgement is one that gets re-litigated at the next audit.

## The files it touches

Nine, and the shape of the list is the phase's real claim: one new package, one new test, no schema, no
dependency, no `AppContainer` wiring.

| File | What happens to it |
| --- | --- |
| `ui/support/SupportHandoff.kt` | **new.** Pure builders + two `Context` extensions. Named to echo `ui/care/CalendarHandoff.kt` — both hand a draft to another app and own nothing afterwards |
| `ui/support/SupportScreen.kt` | **new.** The screen. No `ViewModel` — see below |
| `NavigationKeys.kt` | `@Serializable data object Support : NavKey`, after `Settings`; the "closed twice now" note on `WeightEntry`'s KDoc |
| `Navigation.kt` | `entry<Support> { SupportScreen(onBack = …) }`; `MoreScreen(onOpenSupport = { backStack.add(Support) })` |
| `ui/more/MoreScreen.kt` | new `onOpenSupport` parameter, real summary on the row, **delete** the `HorizontalDivider` and the `more_coming_soon` row, `more_needs_bunny` as the inert subtitle for Photos and Documents, reword `MoreRow`'s KDoc |
| `AndroidManifest.xml` | three `<intent>` blocks inside the existing `<queries>` |
| `res/values/strings.xml` | +19, −1 |
| `res/values-pl/strings.xml` | +19, −1 |
| `test/…/ui/support/SupportHandoffTest.kt` | **new.** The only test file the phase adds |
| `scripts/edge-to-edge.py` | one `Scene`, maybe two — 59 becomes 60 |

**`SupportScreen` has no `ViewModel`, and that is an exception with a reason.** The house rule is one per
screen because a screen normally owns a `Flow` from a DAO and state that must survive rotation. This one
reads three compile-time constants and the resolved configuration; its only mutable state is a
`SnackbarHostState`, which is `remember`ed exactly as `CareReminderScreen` does it. A `ViewModel` here
would be an empty class whose `uiState` never changes — ceremony that makes the next reader look for the
data it implies.

## Checkpoints

Four, and each is one commit. The order is the usual one for this repo — the part that can be proven on
the JVM first, the part that needs a phone last.

### 6a — the hand-off, pure and tested

**First, the inbox** — `binky.support@gmail.com` confirmed live and able to send, because everything below
hardcodes it. Then `SupportHandoff.kt` and `SupportHandoffTest.kt`, with no UI anywhere. Everything the
phase can get wrong in a way a test can catch is decided here, before a screen exists to hide it.

This is also the checkpoint that is **safe during an armed night**: it is pure JVM, it installs nothing,
and it cannot cancel an alarm by accident.

```kotlin
/** Which of the two mails, and the inbox filter token that tags it (ADR-0013 exception). */
enum class SupportRequest(val tag: String) { BUG("#bug"), FEATURE("#feature") }

/** Read at the call site, passed in as data — which is what keeps the builders JVM-testable. */
data class SupportDiagnostics(
    val versionName: String,   // BuildConfig.VERSION_NAME
    val versionCode: Int,      // BuildConfig.VERSION_CODE
    val isDebugBuild: Boolean, // BuildConfig.DEBUG
    val androidRelease: String, // Build.VERSION.RELEASE
    val apiLevel: Int,          // Build.VERSION.SDK_INT
    val device: String,         // "${Build.MANUFACTURER} ${Build.MODEL}"
    val appLocale: String,      // the *resolved* locale — see below
)

fun supportSubject(request: SupportRequest, description: String, diagnostics: SupportDiagnostics): String
fun supportBody(request: SupportRequest, prompt: String, diagnostics: SupportDiagnostics): String

const val SUPPORT_EMAIL = "binky.support@gmail.com"
const val PLAY_PACKAGE = "binky.bunny.and.rabbit.tracker" // NOT packageName — ADR-0023's .debug suffix
const val PLAY_STORE_PACKAGE = "com.android.vending"      // market:// is not a Play-only scheme
const val PRIVACY_POLICY_URL = "https://srednimax.github.io/binky-app/privacy-policy.html"
fun playMarketUri(): String = "market://details?id=$PLAY_PACKAGE"
fun playWebUrl(): String = "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"

fun Context.sendSupportMail(subject: String, body: String): Boolean
fun Context.openPlayListing(): Boolean   // market:// pinned to Play, then https://, false if neither
fun Context.openUrl(url: String): Boolean // the privacy policy row, and openPlayListing's second step
```

`description` and `prompt` arrive as already-resolved strings from `stringResource` at the call site.
That is the whole trick: the builders never see a `Context`, so the same function is asserted with an
English description and a Polish one in a `src/test` unit test with no Android framework under it —
the same reason `careRrule` and `careCalendarBeginMillis` are separate from `addCareToCalendar`.

The subject is `"${tag} — $description — Binky $versionName${if (isDebugBuild) "-debug" else ""} ($versionCode)"`.
The bug body is the localised prompt, a blank line, then the block:

```
(describe what happened here)

Binky 1.3.0 (214)
Android 15 (API 35)
Xiaomi 2312DRA50G
locale pl
```

A feature request's body is `""` — the empty string, not a prompt with nothing under it.

**`appLocale` is read from the resolved configuration, not from `currentAppLanguage()`.** That function
returns `null` for "follow the phone", which is the ordinary state and would put a blank in the block
for most senders. What the report needs is the locale the strings were actually drawn from:
`LocalResources.current.configuration.locales[0].toLanguageTag()` in the composable that builds the
draft. It agrees with `currentAppLanguage()` whenever an override is set, and says something useful
when one is not.

Gate for the checkpoint: `spotlessApply`, `assembleDebug`, `test`. Commit `feat: support mail and Play
listing hand-offs`.

### 6b — the screen, the route and the strings

`Support` as a `NavKey`, the `Navigation.kt` entry, `SupportScreen`, all 19 strings in both locales, the
More row promoted, the divider and `more_coming_soon` deleted, `more_needs_bunny` on the two inert rows,
and the manifest's `<queries>`.

The screen follows the detail-route pattern the app already has: `Scaffold` with its own `TopAppBar`,
back arrow, `windowInsets = WindowInsets(0, 0, 0, 0)` because the shell has already padded past the
status bar, and its own `SnackbarHost` — `CareReminderScreen.kt` is the closest model, `SettingsScreen.kt`
the closest layout. Top to bottom:

1. Intro line.
2. **Report a bug** button, and under it the sentence saying what the diagnostics block contains.
3. **Request a feature** button.
4. The address, as selectable text — `Modifier` with `SelectionContainer` around it, so a failed launch
   leaves something to copy rather than something to retype.
5. `HorizontalDivider`.
6. **Rate Binky on Google Play**, with its one-line help.
7. **Privacy policy** → `openUrl(PRIVACY_POLICY_URL)`, `support_no_browser` on false. It is one row on
   the app's only About-shaped surface, it reuses the `https` launch already built for Rate's fallback,
   and it saves a reader going back to the Play listing to find out what the app does with their
   rabbit's photos.
8. `HorizontalDivider`.
9. **Version** row: label + `BuildConfig.VERSION_NAME` and `VERSION_CODE`, the running build's own
   numbers — which in a debug build is a git commit count, and is meant to be. It lives **here rather
   than in Settings** because the version exists for the bug report, and separating a number from the
   screen that needs it is how it goes stale unnoticed.

Each button calls the matching `Context` extension and shows the corresponding snackbar on `false`.
No `resolveActivity`, no enablement logic, no pre-check: the buttons are always live and the failure is
reported after the attempt, because the attempt is the only honest test.

The manifest gains, inside the existing `<queries>`:

```xml
<intent>
    <action android:name="android.intent.action.SENDTO" />
    <data android:scheme="mailto" />
</intent>
<intent>
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="market" />
</intent>
<intent>
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="https" />
</intent>
```

with a comment saying they exist so a pre-check added later cannot lie, and that nothing in the app
performs one today.

Gate: `spotlessApply`, `assembleDebug`, `test` (`PolishTranslationTest` is what catches a string added
to one locale and not the other), `lint` at 0/0. Commit `feat: support screen with bug, feature and
rating hand-offs`.

### 6c — the device pass

Everything in the gate below that needs a phone, in one sitting — and **not while a dose alarm is
armed**, because this checkpoint installs. Some of it needs a package turned off and back on, which is
the only way to see the fallback paths on a phone that has everything installed.

**Enumerate before disabling.** "No mail app" is decided by the phone, not by this file: `mailto:`
resolves to Gmail *and* PayPal here, so disabling Gmail alone leaves the intent resolvable,
`ActivityNotFoundException` never throws, and the step reports a pass it never earned.

```bash
adb shell pm query-activities -a android.intent.action.SENDTO -d "mailto:test@example.com"
adb shell pm disable-user --user 0 com.google.android.gm       # every resolver the query named,
adb shell pm disable-user --user 0 com.paypal.android.p2pmobile # not just the mail one
# → both mail buttons say so, and the address is still selectable
adb shell pm enable com.google.android.gm
adb shell pm enable com.paypal.android.p2pmobile

adb shell pm disable-user --user 0 com.android.vending          # → Rate falls through to the browser
adb shell pm enable com.android.vending
```

The Play half is only a real test **because the intent is pinned** to `com.android.vending`: unpinned,
GetApps answers `market://` and the fallback never runs. Re-enable each package immediately; a disabled
Play Store on the test phone is a confusing thing to rediscover a week later. The Polish half is checked by switching the app's language in Settings, not
the phone's — the block is supposed to report the *app's* locale, and setting them separately is what
proves it does.

Then the edge-to-edge scene: `Scene("support", "detail", [("tap", "More"), ("tap", "Support")])` added
to `SCENES`, plus a `support-bottom` variant with `("swipe_end", "")` **only if** the landscape run puts
the Rate button below the fold — decided by looking, not assumed. Run it alone first:

```bash
scripts/edge-to-edge.py --scene support
```

🔴 **This inherits `DOD.md` §2's blocker.** The Xiaomi is dropping synthetic taps, and every scene in
that script is tap-driven. The hand-driven gate checks above are unaffected — a finger works — so 6c
splits cleanly into the part that can be done now and the one scene that waits with the other 59.

#### Result, run 2026-08-06 — one defect found and fixed, and the tap blocker solved

**The checkpoint earned its place.** It found a defect that every JVM test passed and every screen hid,
and it retired `DOD.md` §2's blocker on the way.

- 🔴 **`EXTRA_SUBJECT` and `EXTRA_TEXT` are silently ignored by Gmail for `ACTION_SENDTO`** — the
  central decision of this phase, and it was backwards. The first bug draft opened with the recipient
  filled and **the subject and body empty**. Same result with the chooser and with the intent pinned
  straight at `com.google.android.gm`, so it is Gmail and not the resolver. Nothing on the screen says
  so: the button works, the mail app opens, and only the arriving mail is wrong.

  Fixed by moving both into the `mailto:` query string, percent-encoded with `Uri.encode` — which is
  the answer to the `#`-fragment trap the extras were chosen to dodge, because the platform does the
  escaping instead of a hand-typed `%23`. Verified on the device: subject
  `#bug — Bug report — Binky 1.2.0-debug (218)`, and in Polish
  `#bug — Zgłoszenie błędu — Binky 1.2.0-debug (218)` — hash, em-dashes and diacritics all intact,
  the body's six lines with their newlines preserved. The extras are still set, now as belt-and-braces
  for clients that read them instead. Four new JVM tests guard the assembly, including that no bare
  `#` survives into the URI.
- 🟢 **`input touchscreen tap` lands where bare `input tap` is dropped**, which retires §2's blocker.
  Proved by A/B on one screen at one coordinate: bare `tap` never moved the selection, `touchscreen tap`
  moved it every time. `input keyevent` and `input swipe` were never affected — it is the source
  inference `input` makes when none is named that HyperOS stopped honouring. One-line fix in
  `edge-to-edge.py`; the existing three-try retry stays, because taps still land intermittently.
- 🟠 **`pm disable-user` is refused for system packages on HyperOS** — `SecurityException: Cannot
  disable system packages` for both `com.android.vending` and `com.google.android.gm`, so the two
  disable-and-look steps this section prescribes cannot be run as written. PayPal disables fine.
  Substituted by temporarily repointing a constant and rebuilding — the pin at an absent package, and
  the `mailto:` scheme at one nothing claims — which exercises the same `catch` from the same call
  site. Both reverted immediately.
- **The Play fallback's catch fires, and "falls through to *the browser*" is unprovable on this phone.**
  With the pin unreachable, `openUrl(playWebUrl())` runs — and Play claims that `https` URL as an app
  link, so it opens Play again rather than Chrome. The chain is proven in two halves instead: the catch
  is reached (nothing else could have opened the listing), and `openUrl` reaches a browser, which the
  **Privacy policy** row shows directly — Chrome, on the hosted page.
- **Resolvers were as this file predicted.** `mailto:` → Gmail *and* PayPal; `market://` → Play *and*
  Xiaomi GetApps; `https` → Chrome. Rate opened `com.android.vending`, never GetApps, from the build
  whose own id ends `.debug` — so both the `setPackage` pin and the hardcoded `PLAY_PACKAGE` are
  carrying real weight, not insurance.
- **The Rate button's ahead-of-production caveat is confirmed in the field**, not just reasoned:
  the listing opens on *Binky: Bunny & Rabbit Tracker (Early Access)* and says **"Only the developer
  can see this feedback"** — exactly the private-feedback path this file predicted testers would get.
- **The app's locale, not the phone's.** With the app switched to Polish on an `en-US` phone the block
  read `locale pl`; in English it read `locale en-US`. The resolved-configuration read is doing what
  `currentAppLanguage()` could not.
- **The feature body is exactly empty** in the draft, and the no-mail-app path shows
  *Brak aplikacji pocztowej na tym telefonie. Adres powyżej można skopiować.* with the address still
  rendered above it.
- **More has no "coming soon" left**, no divider under Settings (row spacing is a uniform 198 px), and
  Photos and Documents both read *Najpierw dodaj królika.* — checked the way this file asks, with both
  seeded bunnies **archived** rather than on a fresh install, then restored.
- **The scene count is 61, not the 60 predicted.** Landscape ends the screen on the address, leaving
  Rate, Privacy policy and the version row below the fold — so the conditional `support-bottom` variant
  was owed after all, decided by looking. Both scenes clean in all four configurations.
- **The test device is a `Xiaomi 24115RA8EG` on Android 16 (API 36)**, not the `2312DRA50G` / Android 15
  (API 35) this file's examples assume. The examples are illustrative and were left alone.

**Still owed, and it needs a person:** the gate's *"the block is visible in the received mail, not
collapsed behind Gmail's `…`"*. That one cannot be read from a draft — it needs a mail actually sent to
`binky.support@gmail.com` and then read in the inbox. Everything up to the send is verified.

### 6d — the docs, the Console and the release

Results into this file, §5 of `DOD.md` emptied, **Phase 6** ticked in `PLAN.md`'s status list, and
`binky.support@gmail.com` set as the per-app contact email in *Store settings*. That last one is
**not** blocked by Play's testing count — Store settings is editable today — so it is the one Console
item in the whole plan that does not wait.

Two documents change in the same commit, and one of them is published:

- **[`privacy-policy.md`](privacy-policy.md)** — the support mail added to *What you choose to send*,
  *Deleting your data* narrowed to "your records" plus an erasure line, `_Last updated_` moved off
  5 August, and the page **republished** before 1.3 goes up, because the listing links it.
- **[`play-app-content.md`](play-app-content.md)** — why Data safety stays "collects nothing" with a
  support inbox in the app, written as a judgement rather than left as an assumption.

Then `release-please` cuts **1.3.0** from the `feat:` commits above.

#### Result, run 2026-08-06 — the documents are paid, two hand items remain

The documentation debt this phase created is settled in one commit, which is what the file's own rule at
the top of [`play-app-content.md`](play-app-content.md) requires: the privacy policy and the Data safety
answers move together or Play's cross-check catches the gap.

- **[`privacy-policy.md`](privacy-policy.md) amended and dated 6 August 2026.** *What you choose to send*
  gains the Support screen as the third thing and the first addressed to the developer — it names the
  address, lists the block's contents, says **nothing about your rabbits**, and says the app hands over a
  draft that goes nowhere until the sender taps send. *Deleting your data* narrows from "we never receive
  your data" to "we never receive your **records**" and gains the erasure line for correspondence, which
  is the sentence that stopped being true the moment the first button shipped.
- **[`play-app-content.md`](play-app-content.md) records the judgement rather than the conclusion.** §7
  gains a `⚠` subsection: Play scopes collection to what the *app* transmits, `ACTION_SENDTO` hands a
  draft to a client the user chose, and the three properties that keep that reading honest are named as
  constraints on the code — six facts and no records, the golden-string test that stops a seventh being
  added, and the screen saying so before the tap. The deletion row in the same section is reworded, and
  the *Contact email* bullet now carries the address and why it has to be that one.
- **The Data safety declaration stays "collects nothing"** and the form gains no data type. No Play
  Billing, no In-App Review, no donation link ships either, so §3 *Ads*, §9 *Financial features* and §4's
  purchase question are untouched.
- **`release-please` had already opened the 1.3.0 PR** off 6a–6c's three commits (#93,
  `chore(main): release 1.3.0`) — the cut is a merge, not a step to perform.

**Still owed, and both need a person rather than a build:**

1. **Play's per-app contact email** set to `binky.support@gmail.com` in *Store settings* — the one Console
   item in the plan not blocked by the testing count, and the thing that makes the app, the listing and
   the privacy policy's *Contact* section name one inbox.
2. **The gate's received-mail read**, carried from 6c: send a bug report to the inbox and confirm the
   block is *visible* and not collapsed behind Gmail's signature `…`. Everything up to the send is
   verified; this cannot be read from a draft.

Phase 6 is therefore ticked in [`PLAN.md`](PLAN.md) **on the build and the documents**, with those two
carried in [`DOD.md`](DOD.md) — the same shape as Phase 4's close, where the Console half and one night's
evidence outlived the code.

## The strings, by key

Nineteen new, one deleted, both locales. Polish is drafted here rather than left to the commit, so the
translation is reviewed as copy instead of as a diff. `%1$s` appears in none of them — the version
segment is assembled in Kotlin precisely so no locale can drop a placeholder.

| Key | EN | PL (draft) |
| --- | --- | --- |
| `more_support_summary` | Report a bug, suggest a feature, rate the app. | Zgłoś błąd, zaproponuj funkcję, oceń aplikację. |
| `more_needs_bunny` | Add a bunny first. | Najpierw dodaj królika. |
| `support_title` | Support | Wsparcie |
| `support_intro` | Binky is made by one person. A bug report or an idea reaches them directly. | Binky robi jedna osoba. Zgłoszenie błędu albo pomysł trafia prosto do niej. |
| `support_bug_button` | Report a bug | Zgłoś błąd |
| `support_bug_subject` | Bug report | Zgłoszenie błędu |
| `support_bug_prompt` | (describe what happened here) | (opisz tutaj, co się stało) |
| `support_diagnostics_title` | What a bug report carries | Co trafia do zgłoszenia błędu |
| `support_diagnostics_explain` | The app version, your Android version, the phone model and the app's language are added to the message. Nothing about your bunnies, and nothing is sent until you tap send in your mail app. | Do wiadomości dopisujemy wersję aplikacji, wersję Androida, model telefonu i język aplikacji. Nic o Twoich królikach — i nic nie wychodzi, dopóki nie wyślesz wiadomości w swojej poczcie. |
| `support_feature_button` | Request a feature | Zaproponuj funkcję |
| `support_feature_subject` | Feature request | Propozycja funkcji |
| `support_email_label` | Or write to: | Albo napisz na: |
| `support_no_mail_app` | No mail app on this phone. The address above can be copied. | Brak aplikacji pocztowej na tym telefonie. Adres powyżej można skopiować. |
| `support_rate_button` | Rate Binky on Google Play | Oceń Binky w Google Play |
| `support_rate_help` | Binky is free and ad-free. A rating is the only thing it asks for. | Binky jest darmowe i bez reklam. Ocena to jedyna rzecz, o którą prosi. |
| `support_no_store_app` | Google Play could not be opened on this phone. | Nie udało się otworzyć Google Play na tym telefonie. |
| `support_privacy_button` | Privacy policy | Polityka prywatności |
| `support_no_browser` | No browser on this phone to open the page. | Brak przeglądarki na tym telefonie, aby otworzyć stronę. |
| `support_version_label` | Version | Wersja |
| ~~`more_coming_soon`~~ | *deleted from both* | *deleted from both* |

`support_no_mail_app` names the address's position on screen rather than repeating the address, so the
snackbar and the text cannot disagree after an edit to one of them.

## Tests

`SupportHandoffTest`, JVM, one file. The builders are pure over
`(request, description, prompt, diagnostics)`, so these are ordinary string assertions:

- the subject **starts with exactly** `#bug`, and with `#feature` for the other kind;
- it **still** starts with `#bug` when the description is the Polish one — the case a frozen whole-subject
  implementation passes and a filter rule fails on;
- the description after the tag **does** change with the locale — the half that rots silently if someone
  "simplifies" the split;
- a debug build's subject carries `-debug` and a release-shaped one does not;
- the feature body is **exactly** `""`;
- the bug body equals a **golden string** built from the diagnostics record. Asserting equality rather
  than `contains` is what proves nothing else can be in it — no bunny name, no id, no path;
- `playMarketUri()` and `playWebUrl()` both name `binky.bunny.and.rabbit.tracker`, **neither ends in
  `.debug`**, and the web one is `https`. This is the test that catches a `packageName`-derived
  implementation on the one phone that ever runs it.

`PolishTranslationTest` picks up the nineteen new strings with no change to it. The intent construction
itself is framework — including `setPackage` on the `market://` one, which no JVM test can observe — and
is verified by hand at 6c.

No `connectedAndroidTest` is owed: no schema change, no DAO, no media path.

## Gate

- Both buttons open a mail app with the recipient filled, the right tag in the subject, and the bug one
  carrying the diagnostics block.
- The **Polish build's subject still starts with `#bug`**, with the description after it in Polish — one
  Gmail filter (`subject:#bug`) catches both locales.
- The block reports the **app's** language, checked with the app set to Polish on an English phone.
- The block is **visible in the received mail**, not collapsed behind Gmail's signature `…`.
- With **every `mailto:` resolver disabled** — enumerated first with `pm query-activities`, not assumed
  to be the mail app — the screen says so and the address is selectable and copyable. Disabling Gmail
  alone does not produce this state on the test phone.
- **Rate opens the listing for `binky.bunny.and.rabbit.tracker` from the debug build too** — the build
  whose own id ends in `.debug`. Checked there specifically, because that is where a derived id fails.
- **Rate opens Play and never GetApps**, which also claims `market://` on this phone and does not have
  Binky in its catalogue. This is what `setPackage` buys, and it is checked by looking at which app opens.
- With **no Play Store app**, Rate falls through to the browser; with neither, the screen says so.
- **Privacy policy** opens the hosted page; with no browser, the screen says so.
- **No donation link, tip jar or payment prompt exists anywhere in the app** (Play Payments §3/§4).
- The version on screen matches the installed build.
- More has **no "coming soon" row left**, no divider under Settings, and `more_coming_soon` is gone from
  both locales. Photos and Documents are **still inert with no bunny in scope** — the nullable `onClick`
  survived the deletion — and they now **say `more_needs_bunny` while inert**, checked with every bunny
  archived rather than on a fresh install.
- The **privacy policy is republished with a new date** before 1.3 goes up, and its *Deleting your data*
  section no longer claims nothing is ever received.
- Every new string exists in both locales and `PolishTranslationTest` is green.
- The Support screen renders edge-to-edge in both orientations under both navigation modes — one new
  scene in 4f's matrix, which now runs 60.
- Play's per-app contact email is `binky.support@gmail.com` before 1.3 goes up.

`spotlessApply`, `assembleDebug` and `test` at each checkpoint; `lint` at the gate, holding at **0 errors
and 0 warnings**.

## What this phase does not do

Written down because each one is a plausible next thought, and none of them is in scope:

- **No crash reporting, no analytics, no log capture.** The diagnostics block is six facts the owner can
  read in their own draft. Anything that collects on its own is a backend by another name.
- **No in-app FAQ, changelog or "what's new".** `CHANGELOG.md` is generated and lives in the repo.
- **No contact form, no attachment picker, no screenshot attach.** The mail app already has one.
- **No In-App Review, no Play Core, no billing library** — the three dependencies this phase's whole
  argument is about not adding.
- **No copy-to-clipboard button** beside the address. Selectable text is the platform's own affordance
  and needs no string, no snackbar and no permission.
- **No open-source licence screen**, and this one is *owed* rather than declined. The app ships Room,
  Compose, Coil 3, Vico and ML Kit and contains no attribution of any kind; Apache-2.0 §4 asks for the
  licence and NOTICE to travel with the binary. It is not done here because the off-the-shelf route is
  Google's `play-services-oss-licenses` plugin — precisely the **second Play-dependent library** this
  phase's whole argument refuses — and a hand-typed list is wrong one dependency bump later with nobody
  watching. So it is a dependency decision wearing a UI costume, and it is **booked in `DOD.md` as its
  own item** to answer before production launch, which is when the exposure stops being theoretical.

## When it closes

Write the results into this file, tick **Phase 6** in `PLAN.md`'s status list, and empty §5 of `DOD.md`.
Once the phase is done this file joins `PLAN.md` as record rather than worklist — either by being moved
into it, or by being left here and simply not read.

**One coordination note for `DOD.md` §4.** 1.2.0 is tagged and artifact-verified but has never been
uploaded — Play's 12-testers / 14-day count was still running. If 1.3 is ready before that count clears,
**do not upload both to preserve the plan's wording.** 1.3 carries the same schema 6 and the same two
hand-written migrations, so §4's field-upgrade proof simply retargets: **1.0.0 → 1.3**, crossing
`MIGRATION_4_5` and `MIGRATION_5_6` exactly as it was written to. Uploading 1.2.0 first buys a second
release cycle and proves nothing 1.3 would not.
