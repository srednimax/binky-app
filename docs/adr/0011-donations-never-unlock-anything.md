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
