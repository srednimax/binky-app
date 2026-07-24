# Bunny Health Tracker

Keeps a trustworthy record of one or more pet bunnies' health — weight, droppings, appetite, symptoms,
vet visits — so their owner and their vet can see changes over time. It is a record, not a diagnosis: its
one genuine early signal is the **weight trend**, which moves whether or not anyone logs a mood; everything
else helps interpret what was actually observed, and the app never infers trouble from silence (ADR-0001).

## Language

**Bunny**:
One animal the owner tracks. The app's central noun — every weight, observation, photo and document
hangs off one. Used in code, in the UI and in these docs, with no synonyms.
_Avoid_: rabbit, pet, animal

**Observation**:
Something the owner noticed about a bunny at a specific moment — droppings, appetite, mood, a
symptom. Recorded when noticed, not on a schedule; several may exist for one day, or none.
_Avoid_: health log, daily log, diary entry, check-in

**Archive**:
To hide a bunny from everyday use while keeping all of its records. The opposite of deleting, which
destroys them. An archived bunny has usually died or been rehomed.
_Avoid_: delete, remove, hide, deactivate

**Document**:
A scan or photo of paperwork from a vet — results, prescriptions, vaccination records — attached to a
bunny and optionally to a visit. A record, not a memory: it is evidence the owner may need again.
_Avoid_: file, attachment, scan, paper

**Photo**:
A picture of a bunny kept for its own sake, in that bunny's gallery. Sentimental rather than
evidential, and the bulk of the app's storage. Not an avatar — they are stored and backed up separately.
_Avoid_: image, picture, media

**Avatar**:
The small picture that identifies a bunny across the app. Its own field and its own file, kept apart from
the gallery because ADR-0005 backs avatars up as Essential and photos only as Everything.
_Avoid_: profile picture, thumbnail, icon, photo

**Medication Course**:
A medicine a bunny is meant to take from a start date, with an optional end (an open course is ongoing),
optionally on a daily schedule of clock times. The prescribed dose amount is free text. Scheduling can be
switched off, leaving a course the owner simply records doses against.
_Avoid_: prescription, treatment, med

**Dose**:
One administration of a medication course. A *due dose* is derived from the course schedule and does
not exist until acted on; a *recorded dose* is a stored fact that the owner gave it or deliberately
skipped it. A dose can be recorded without any schedule.
_Avoid_: intake, administration, pill

**Care Reminder**:
A prompt for recurring husbandry due around a date — nail trim, vaccination, routine weigh-in. Distinct
from a dose reminder, which belongs to a medication course and is time-critical.
_Avoid_: task, todo, alert, notification

**Watch**:
A **time-boxed** period during which the owner has declared a bunny needs closer attention: the owner sets
a duration when starting it and it auto-expires with a prompt to extend or close. Only while a watch is
active does the app chase the owner for fresh observations — once daily, and satisfied by logging. Off by
default.
_Avoid_: alert mode, monitoring, sick mode, observation period

**Droppings**:
The ordinary hard round pellets a bunny produces. How many, and how big, are the earliest signals that
something is wrong. Amount, size, form and cecotropes are all optional and **mean "not checked" when
untouched** — the earliest health signal is never auto-filled "normal", because a "fine" nobody verified is
a false reassurance (ADR-0001). A healthy day stays one tap through an explicit **"Log a healthy day"**
shortcut that *affirmatively* records the glance-level facts — normal droppings, cecotropes eaten, no
symptoms — while leaving graded fields (appetite, mood, activity, water) "not checked", distinct from
opening the full observation form.
_Avoid_: poop, faeces, stool, pellets (ambiguous with food pellets)

**Symptom**:
A named sign of illness the owner can tick on an observation, drawn from a list that ships with the app
and that the owner can extend. Distinct from free-text notes, because a symptom can be counted over time.
_Avoid_: condition, issue, ailment, problem

**Together**:
Bunnies that share a living space and litter tray, as bonded bunnies normally do. Droppings from a shared
tray cannot be attributed to one bunny, so an observation may cover several bunnies at once and is shown
as such.
_Avoid_: bonded pair, group, herd, cage mates

**Fluffle**:
The set of bunnies that live **Together**, sharing a space and litter tray. Declared when adding or
editing a bunny, not inferred. The code and glossary word; the on-screen label is "Lives with". "Group"
is reserved for the link joining one observation across several bunnies.
_Avoid_: group, warren, household, cage, hutch

**Cecotrope**:
A soft nutrient-rich dropping a bunny normally eats directly. Seeing them left uneaten is a signal
worth recording, and they are not the same thing as ordinary droppings.
_Avoid_: caecotroph, night faeces, soft poop
