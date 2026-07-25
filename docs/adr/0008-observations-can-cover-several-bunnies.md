# An observation can cover several bunnies at once

Bonded bunnies share a litter tray, so droppings frequently cannot be attributed to an individual. Forcing
every observation onto exactly one bunny would record something untrue and then feed it to the health
warnings — one bunny appearing to have stopped producing entirely, purely because of which name was
tapped.

An observation may therefore cover several bunnies. It is stored as one row per bunny, linked by a shared
group id and marked as observed together, so every per-bunny query, chart and warning stays simple while
the attribution remains honest. The app surfaces which bunnies an observation covered rather than implying
individual attribution.

Weight is unaffected — a bunny is always weighed individually.

Bunnies that live together are put in a group, declared when adding or editing a bunny rather than
inferred from habit. The group pre-selects them when logging and is shown as a visual cue, so the living
arrangement is visible rather than implied.

## Shared fields versus individual fields

A shared observation is not uniformly shared; its fields fall into two classes, and the edit path has to
respect the split or it reintroduces exactly the false attribution this ADR prevents.

- **Tray-level facts — droppings and cecotropes.** These are the *reason* the observation is shared: one
  litter tray, one real-world fact. They are **single per group and identical across every row**, and
  editing them **propagates to all participating bunnies**. Storing an independent copy per row would let
  them drift — bunny A's row reading "few" while bunny B's reads "normal" for the same tray — which is the
  false attribution reintroduced through editing rather than tapping.
- **Individual facts — appetite, mood, activity, water, symptoms, note.** These legitimately differ per
  bunny (one hunched and lethargic while the other is bouncing around), so they stay **per row**; editing
  one bunny's mood never touches another's. Forcing these to be shared would be its own lie.

Participants can be changed after creation without delete-and-recreate (which would lose the timestamp).
**Adding** a bunny inserts a row that inherits the group's tray-level facts with blank individual fields;
**removing** one follows the deletion rule below — drop that row, keep the observed-together marker on the
rest.

## The fluffle and the observation group are different columns

Two groupings are easy to conflate and must not be. The **fluffle** — who lives together *now* — is
**mutable current state**: rabbit bonds break, and a survivor is re-bonded with a new bunny after a death.
It is a first-class table with a nullable `bunny.fluffleId` FK, set when adding or editing a bunny (the
on-screen label is "Lives with"); a solo bunny has none. The **observation group** — who a given shared
observation covered — is an **immutable historical fact**, stamped as its own `groupId` on the
one-row-per-bunny observation records at creation.

The group is **never derived from the current fluffle at read time.** If it were, re-bonding would silently
rewrite history — a bunny appearing to have co-observed with one it did not even live with then, the exact
false attribution this ADR exists to prevent. The fluffle only **pre-selects participants** when logging,
and only its **current, non-archived** members. So a bond breaking is just an edit to "Lives with", and
archiving a member drops it from future pre-selection while leaving both its `fluffleId` and its past
shared observations intact — neither touches recorded history.

### The fluffle's own identity and lifecycle

A fluffle carries an **optional custom name** (`Fluffle.name`, nullable): named, it shows as the owner's
label ("The Girls"); unnamed, it renders by its members ("Thumper & Clover"). That fallback is a list of
names joined **through a string resource**, never concatenated with `" & "` — Polish joins lists differently,
and this label appears in the switcher, on the profile and in the healthy-day snackbar (ADR-0013).
"Lives with" is a **symmetric join** — putting Thumper with Clover writes *both* onto one `fluffleId` row,
and if Clover already lives with Hazel, Thumper joins the existing trio rather than forming a rival pair. A
bunny has exactly one `fluffleId`, matching the biology: one bonded group, one shared space and tray.

A fluffle **dissolves when it would be left with one member, counting archived ones** — a single predicate
shared by every path that can change membership, rather than a rule per path:

- **Archival** changes nothing, now as a consequence rather than an exception: the archived bunny is still a
  member, so the count does not move. `fluffleId`, the name and history all stay, because the survivor of a
  bonded pair genuinely *did* live with the archived one.
- **Editing "Lives with" to none** — the ordinary bond break, which this ADR names as a real event and
  previously left unhandled — dissolves a pair: the bunny that left is solo, and so is the one that stayed,
  because both are active and neither shares a tray any more. Editing one member out of a trio simply
  leaves the remaining two a fluffle.
- **Deletion** (the destructive, explicit path, ADR-0004) uses the same predicate, which is exactly why it
  counts archived members. Deleting Thumper from {Thumper, Clover, Hazel-archived} leaves **two** members,
  so the row stands and Clover keeps having lived with Hazel. An active-only rule would have dissolved it
  and erased the very fact the archival clause above exists to protect.

Dissolving sets the survivor's `fluffleId` to null and cleans up the now-single-member fluffle row in the
same transaction. A consequence to accept: a custom name is therefore **ephemeral** — dissolving a group
discards the name, and re-bonding later means naming afresh. That is correct: the group genuinely dissolved.
A second: a fluffle whose only other member is archived still renders "Lives with", so that member has to be
shown distinguishably — *"Lives with Hazel (archived)"* — rather than as a current roommate.

## Consequences

A "no droppings" warning is weaker for a shared observation, since an empty tray means neither bunny
produced anything. The app must say so rather than implying it is about one bunny.

Separating a bunny for treatment needs no model change: stop covering both, and individual observations
resume immediately.

### "Log a healthy day" is the one path that commits participants unreviewed

Every other write shows the participants and lets the owner change them. The one-tap healthy-day shortcut
cannot, because not asking is the entire feature — and it writes the field this ADR most exists to protect.
Left alone it defeats the separation guidance above at the worst possible moment: an owner whose bunny is
separated and ill taps it out of habit and **affirmatively records normal droppings and no symptoms for the
sick bunny**, from a tray it is not using. "Lives with" will not save them; it is declared living
arrangement, and nobody edits it for a fortnight of critical care.

Two requirements follow, and they keep the shortcut at one tap:

- **It names who it covered**, as a snackbar with undo — *"Healthy day logged for Thumper & Clover · Undo"*.
  The attribution is visible immediately and a wrong one is reversible, without a dialog standing between
  the owner and the tap.
- **A bunny under an active Watch is excluded from its pre-selection**, with the reason stated — *"Clover
  is under a watch — log for her separately."* A Watch is the owner's own declaration that this bunny needs
  individual attention, which makes it the signal that already exists for "do not sweep this one into a
  group fact."

The second lands with Watch (1.1); the first ships with the shortcut itself.

**Deleting** one bunny in a shared observation (ADR-0004) removes only that bunny's row. The surviving
rows **keep their observed-together marker** and are still rendered as shared ("observed together") even
when only one member still resolves — never silently downgraded to an individual observation, which would
recreate exactly the false attribution this ADR exists to prevent. No tombstone of the deleted bunny is
needed: the marker alone keeps the record honest. The delete confirmation counts sole-owned and
shared-participation observations separately (ADR-0004).
