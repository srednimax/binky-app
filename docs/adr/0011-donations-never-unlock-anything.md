# Donations are one-off, external, and unlock nothing

The app is free and ad-free with no running costs, so there is nothing to fund — support is a thank-you,
not a business model. Supporters are directed to a Buy Me a Coffee page for a **one-off** payment. Patreon
was rejected: recurring membership suits ongoing creative output, not a quiet utility someone uses for
years, and it commits the author to maintaining a patron relationship.

**A donation must never unlock anything.** No features behind it, no supporter tier, no reminder that you
have not paid. This keeps the app honestly free and keeps it clear of digital-goods payment policy
entirely.

The supporter credits list is **baked into each release**, not fetched. The app's **own code** makes no
network requests at all — no analytics, no telemetry, no fetched content — which is a real property worth
protecting for something holding a pet's medical records; trading it away so a thank-you list updates
sooner would be a bad deal. Names are added by hand from the Buy Me a Coffee supporters export at release
time, and only where the supporter opted into being named; everyone else is a count of anonymous
supporters.

The one qualification is not our code: the **optional** ML Kit document scanner is delivered by Google
Play services, which may download its module over the network on first use and processes the scan in
Google's process (ADR-0009). So the honest claim is "the app's own code makes no network requests", not
"the app makes none at all". An owner who wants zero Google involvement can decline scanning and use the
plain-camera fallback, which ADR-0009 already provides.

## The one ask

The app interrupts once, and only for someone already familiar with it: after roughly 30 days *and* a
meaningful amount of data entered. Elapsed time alone would fire at someone who installed it, added
nothing and forgot.

It is shown **once**. Dismissing is permanent — no "remind me later", no second attempt, and it does not
return after an app update. It is never shown while a bunny is under a Watch or on an active medication
course: someone who opened the app because their bunny is ill should not be asked for money.

A permanent "Support this app" entry in settings covers everyone who dismissed it or grew grateful later.
The wording is thanks, not need — there are no bills to plead, and saying otherwise would be a lie.

## Consequences

The dismissal flag lives in preferences, **not in the database** — ADR-0007 allows the database to be
wiped by a schema change, which must never resurrect the prompt.


Google Play's rules on linking to external payment have shifted repeatedly. Re-read the current policy
before publishing rather than assuming this arrangement is still permitted; an in-app "tip" purchase
through Play Billing is the fallback if it is not.

## Amendment (Phase 6): the re-read happened, and the arrangement is withdrawn

**The paragraph immediately above is the instruction that closed this ADR.** The policy was re-read while
planning the Support screen, and the arrangement it describes is not permitted. Nothing here was ever
built — no prompt, no settings entry, no supporter list, no link — so this is a plan withdrawn rather than
code removed, and the app has never shown a donation ask of any kind.

Play's **Payments** policy §3 exempts only **tax-exempt** donations from Play Billing, and §4 forbids an
app leading users to any other payment method "including through in-app user interface flows". A Buy Me a
Coffee page paying an individual is income, not a tax-exempt donation, so neither half of the policy helps
— the exemption does not apply and the prohibition does. This is not a cautious reading:
**StreetComplete** — free, open source, no ads — was **rejected** under this policy for in-app Patreon,
Liberapay and GitHub Sponsors links, and the flagged item was a link to a project page that merely
*contained* donation information. The 2026 external-payment-links program does not rescue it either: it is
region-limited and scoped to purchases of in-app digital items, not donations.

The fallback this ADR named is real but costs more than it buys. A Play Billing tip jar would flip the
Console's in-app-purchase declaration, put an **In-app purchases** badge on a listing whose whole pitch is
that it is free, contradict two answers already recorded in [`play-app-content.md`](../play-app-content.md),
and add a billing library to a manifest that two release audits were spent keeping clean.

So the specifics above are dead: the Buy Me a Coffee link, the one 30-day ask, the permanent *Support this
app* entry in settings, the baked-in supporter credits, and the dismissal flag in preferences that existed
to serve them. **What replaces the ask is a rating** — Phase 6's Support screen links to the Play listing
instead, which costs nothing on the Console, adds no dependency, and is worth more to a listing with no
reviews than a tip would be. See [`phase-6.md`](../phase-6.md) for the full argument and for why it is a
plain store link rather than the In-App Review API.

Two things in this ADR survive it, both stated more strongly than before:

- **The app is free with nothing behind a payment**, which is now unconditional rather than a rule about
  what a donation may not unlock. There is no payment surface anywhere in the app to attach a condition to.
- **The app's own code makes no network requests.** That was the reason the supporter list was to be baked
  into each release; the list is gone, but the property it was protecting is untouched and is what the
  privacy policy and the Data safety answer both rest on. Its one qualification is still ML Kit's, exactly
  as described above.
