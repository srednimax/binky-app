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

## Consequences

A "no droppings" warning is weaker for a shared observation, since an empty tray means neither bunny
produced anything. The app must say so rather than implying it is about one bunny.

Separating a bunny for treatment needs no model change: stop covering both, and individual observations
resume immediately.

**Deleting** one bunny in a shared observation (ADR-0004) removes only that bunny's row. The surviving
rows **keep their observed-together marker** and are still rendered as shared ("observed together") even
when only one member still resolves — never silently downgraded to an individual observation, which would
recreate exactly the false attribution this ADR exists to prevent. No tombstone of the deleted bunny is
needed: the marker alone keeps the record honest. The delete confirmation counts sole-owned and
shared-participation observations separately (ADR-0004).
