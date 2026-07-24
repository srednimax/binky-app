# Symptoms are a seeded table, not an enum

Droppings, mood and care type are closed vocabularies and stay Kotlin enums. Symptoms are not: bunnies
develop quirks no built-in list anticipates, and the ones an owner bothers to write down are usually the
recurring ones. A symptom that can only be typed into a note can never answer "how often has this
happened?", which is the reason symptoms are a field at all rather than prose.

Symptoms therefore live in a table, seeded on first run with the built-in list — head tilt, drooling or wet
chin, sneezing or nasal discharge, eye discharge, dirty bottom, loud teeth grinding, hunched posture,
laboured breathing, not drinking, limping, ear scratching, blood in urine, hiding more than usual — with
owner-added rows alongside, indistinguishable in use.

*Loud* teeth grinding is named deliberately: soft tooth purring means a content bunny and loud grinding
means pain, so an unqualified "teeth grinding" would invert the meaning.

## Consequences

Built-in symptoms carry a **stable key**, not an English display string, and are rendered through
`strings.xml` so they translate like all other UI text (ADR-0013). Owner-added symptoms store their
**literal text** plus a flag marking them owner-created; those are untranslatable, as expected. An
observation references a symptom's **stable id**, and the "how often has this happened?" count keys off
that id — never the display text — so a symptom can be renamed or translated without orphaning its
history. (The English strings the seed list uses above are the *labels* behind those keys, not the stored
identity.)

Removing a symptom hides it from the picker and never deletes it from historical observations.

A symptom attaches to an observation as a **binary tick** — present at that noticed moment — and nothing
more. **Severity is carried by the symptom's identity, not a field:** "*loud* teeth grinding" is already a
distinct symptom from soft tooth-purring, so grading is chosen by picking the right symptom. There is no
duration or "resolved" state: an observation is a snapshot ("noticed at a specific moment", CONTEXT.md), and
a persistent symptom is expressed by re-ticking it on later observations — each tick honestly "one time I
noticed it", which is exactly what the count claims to be. The accepted limitation is that a chronic symptom
logged once undercounts its persistence; ADR-0007 leaves room to add a stateful model later if that ever
proves necessary.

The free-text note on an observation remains, for one-off detail that is not a symptom.
