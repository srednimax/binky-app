# Symptoms are a seeded table, not an enum

Droppings, mood and care type are closed vocabularies and stay Kotlin enums. Symptoms are not: rabbits
develop quirks no built-in list anticipates, and the ones an owner bothers to write down are usually the
recurring ones. A symptom that can only be typed into a note can never answer "how often has this
happened?", which is the reason symptoms are a field at all rather than prose.

Symptoms therefore live in a table, seeded on first run with the built-in list — head tilt, drooling or wet
chin, sneezing or nasal discharge, eye discharge, dirty bottom, loud teeth grinding, hunched posture,
laboured breathing, not drinking, limping, ear scratching, blood in urine, hiding more than usual — with
owner-added rows alongside, indistinguishable in use.

*Loud* teeth grinding is named deliberately: soft tooth purring means a content rabbit and loud grinding
means pain, so an unqualified "teeth grinding" would invert the meaning.

## Consequences

Removing a symptom hides it from the picker and never deletes it from historical observations.

The free-text note on an observation remains, for one-off detail that is not a symptom.
