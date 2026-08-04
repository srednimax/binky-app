# The app records doses and never advises on them

Every other feature in this app **observes**: a weight, a dropping, a mood, a photo. Medications are
different in kind — the app is recording what a vet told the owner to give, and then reminding them to give
it. That is the closest Binky ever comes to medical advice, and the line is drawn here rather than left to
whoever writes the next string.

**The app never reasons about a medication.** No interaction checks. No dosage validation, no plausibility
check on the amount, no warning that two courses overlap. No "you missed a dose". No colour that codes a gap
as a problem. It stores what the owner recorded and shows it back.

A derived slot with nothing recorded against it is displayed as **unanswered** — a fact about the record,
not about the rabbit. That is ADR-0001's *never infer a health problem from missing data* in a second
domain: silence means nobody wrote it down. The word "missed" belongs to the owner, not to the app.

## The rule is also on screen, because the owner cannot read ADRs

An ADR binds our copy. It does not tell the person holding the phone anything. From 1.2 the app holds
numbers like "0.3 ml twice daily" that the owner did not invent and will act on, and nothing in the UI has
ever said which record is authoritative.

So the medication screen carries **one quiet permanent line** under the course list, in both locales:
*what your vet prescribed, as you recorded it; Binky never checks doses or interactions.* Not a dialog — a
modal is dismissed once and never seen again, and ADR-0006 keeps that path clear for permissions. Not a
warning either; it states what the record **is**, in the same voice as the rest of the app (ADR-0012).

It is also the cheapest possible answer to a Play reviewer. Binky is listed under **Lifestyle**, and Play's
Health apps policy and its declaration are written for human health — `docs/play-app-content.md` §10 already
takes that position for an app recording an **animal's** health. A disclaimer visible in the listing
screenshot answers the question before it is asked.

## Consequences

**Strings are constrained, permanently.** No "overdue", no "missed", no exclamation, no red for an
unanswered slot. Reviewing a new medication string means checking it against this ADR, and the constraint
outlives the phase that introduced it.

**Free-text dose amounts (ADR-0002) stop looking like a shortcut.** The app cannot validate an amount it
refuses to reason about, so structure would buy nothing and imply competence the app does not have.

**Reminders are the one thing the app does assert**, and even then only about time: this is when you said to
give it. ADR-0003's honest delivery states exist so that assertion is never stronger than the mechanism
behind it — a reminder that presents as armed when it is best-effort would be the app claiming something it
cannot back.

**Nothing here is a hedge against liability.** It is the same posture as the rest of the app: report what
was observed, never diagnose. A feature that would require the app to have an opinion about a medication is
out of scope, not merely unbuilt.
