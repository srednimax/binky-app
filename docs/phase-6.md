# Phase 6 — Support contact — ships as 1.3

**Status: planned, not started.** The boxes to tick are in [`DOD.md`](DOD.md) §5; this file is the
reasoning behind them. The finished phases live in [`PLAN.md`](PLAN.md), which is 3 000 lines of record
and is deliberately not loaded to work on this one — read it only for a phase this file names.

**Prerequisites:** none. Nothing here touches the schema, the alarms, the manifest's permissions or the
dependency list, which is why it is safe to build while Phase 5's overnight run and Play's testing count
are still outstanding.

**Decisions it leans on:** ADR-0001 (never be silent about what is missing), ADR-0009 (Play listing and
dependencies), ADR-0013 (localisation, and the one exception below), ADR-0015 ("coming soon" rows).

## What ships

The smallest phase in the plan, and the first one past the original roadmap. More's **Support** row goes
live, carrying three actions and one fact:

1. **Report a bug** → mail to `binky.support@gmail.com`, subject tagged `#bug`, body prefilled.
2. **Request a feature** → the same address, tagged `#feature`, body empty.
3. **Rate Binky on Google Play** → the store listing.
4. The **app's version**, which nothing in the app shows today — so a bug report currently cannot name
   the build it is about.

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
  never arrives. `SENDTO` + `mailto` resolves only to things that can send mail.
- **The tag travels in `EXTRA_SUBJECT`, not in the mailto query string**, and this is a trap rather than
  a preference. `#` is the URI *fragment* delimiter: `mailto:…?subject=#bug` parses `#bug` as the
  fragment and the subject arrives **empty**. It would have to be written `%23bug`, which is exactly the
  kind of escaping the next edit unescapes without knowing why. Putting the recipient in the Uri and the
  subject and body in intent extras removes the encoding question instead of answering it.
- **The subject is a constant tag followed by localised copy**, and the split is the whole point:

  ```
  EN   #bug — Bug report — Binky 1.2.0 (211)
  PL   #bug — Zgłoszenie błędu — Binky 1.2.0 (211)
  ```

  **The tag alone is a Kotlin constant**, a deliberate exception to ADR-0013, because it is an inbox
  filter token rather than copy — it is addressed to the maintainer, not to the sender, and the inbox is
  read in one language whatever language the report was written in. Translating it would need one filter
  rule per locale, and the failure when a new language ships without its rule is invisible: it looks
  exactly like nobody reporting anything. There is **no technical obstacle** to `#błąd` — `EXTRA_SUBJECT`
  is an ordinary string and the mail app MIME-encodes the diacritics — so this is an inbox decision, and
  it is recorded as one rather than dressed up as an encoding constraint.
  **Everything after the tag is localised** — the description, the button labels, the screen's
  explanation, the *(describe the bug here)* line — so a Polish sender is not left looking at an
  all-English subject in their own draft. The reporter's language is recoverable anyway: the diagnostics
  block carries the app locale, and a feature request's body says it in the plainest way there is.
- **The diagnostics block goes on the bug mail only, and it is a draft the owner reads.** Version,
  `versionCode`, Android release and API level, device model, and the **app's** locale rather than the
  system's, because that is the one the bug was seen in. No bunny data, no identifier, and **nothing
  leaves the phone unless they tap send** — the app itself still sends nothing, which is what keeps this
  on the right side of *no backend, ever*. The screen says what the block contains **before** the button
  is tapped, which is ADR-0001's rule against silence pointed at outgoing data for the first time.
  A feature request gets an empty body: an idea does not need a build number, and prefilled text is
  friction in the way of the sentence they came to write.
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
- **`market://details?id=…` first, `https://play.google.com/store/apps/details?id=…` second**, then the
  same degradation the mail buttons use. Two catches, not one: no Play Store app falls through to the
  browser, and no browser either falls through to the message.
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
- **`<queries>` gains `mailto` and `market` entries**, beside the `com.miui.securitycenter` one already
  there. Package visibility on API 30+ does **not** block `startActivity`, so both launches work without
  it; what it blocks is `resolveActivity`, which returns null and makes an "is there a mail app?"
  pre-check answer *no* on every phone. So there is **no pre-check anywhere** — the try/catch above is
  the mechanism — *and* the queries entries, so that a pre-check added later cannot silently lie.
- **Support is the app's last "coming soon".** Promoting the row empties the block below the
  `HorizontalDivider` in `MoreScreen.kt`, so the divider and `more_coming_soon` go with it, in both
  locales. ADR-0015's escape hatch stops being used, which is the state it was always meant to reach.
- **`Support` is a new `NavKey`** — the second Phase-1 omission closed after 5c's `WeightEntry`. That
  file's promise that every route exists from Phase 1 has now been corrected twice, so it says so rather
  than quietly gaining a third entry.
- **One address, three places.** [`store-listing.md`](store-listing.md) and
  [`play-app-content.md`](play-app-content.md) both name a per-app contact email set in *Store settings*,
  and [`privacy-policy.md`](privacy-policy.md)'s Contact section defers to "the developer email address
  listed on the app's Google Play listing". Setting `binky.support@gmail.com` there makes all three
  agree; skipping it ships an app pointing at an inbox the listing does not name.

## Tests

JVM: the subject and body are **pure builders** over `(kind, version, build, android, device, locale)`,
so the assertions are ordinary string ones — the subject **starts** with exactly `#bug` / `#feature` and
still does when the app locale is Polish, the description **after** it does change with the locale (the
half that would otherwise rot silently if someone froze the whole subject), and the body carries nothing
from the database. The store URL is the same shape of builder, and its test is the one that catches the
trap above: **the URL names `binky.bunny.and.rabbit.tracker` and never ends in `.debug`** — precisely
what a `packageName`-derived implementation would fail, on the build the developer actually runs. The
intent construction is framework and is verified by hand.

## Gate

- Both buttons open a mail app with the recipient filled, the right tag in the subject, and the bug one
  carrying the diagnostics block.
- The **Polish build's subject still starts with `#bug`**, with the description after it in Polish — one
  Gmail filter (`subject:#bug`) catches both locales.
- With **no mail app installed**, the screen says so and the address is selectable and copyable.
- **Rate opens the listing for `binky.bunny.and.rabbit.tracker` from the debug build too** — the build
  whose own id ends in `.debug`. Checked there specifically, because that is where a derived id fails.
- With **no Play Store app**, Rate falls through to the browser; with neither, the screen says so.
- **No donation link, tip jar or payment prompt exists anywhere in the app** (Play Payments §3/§4).
- The version on screen matches the installed build.
- More has **no "coming soon" row left**, and `more_coming_soon` is gone from both locales.
- Every new string exists in both locales and `PolishTranslationTest` is green.
- The Support screen renders edge-to-edge in both orientations under both navigation modes — one new
  scene in 4f's matrix.
- Play's per-app contact email is `binky.support@gmail.com` before 1.3 goes up.

`spotlessApply`, `assembleDebug` and `test` at the checkpoint; `lint` at the gate, holding at **0 errors
and 0 warnings**. No `connectedAndroidTest` is owed — there is no schema change and no media path.

## When it closes

Write the results into this file, tick **Phase 6** in `PLAN.md`'s status list, and empty §5 of `DOD.md`.
Once the phase is done this file joins `PLAN.md` as record rather than worklist — either by being moved
into it, or by being left here and simply not read.
