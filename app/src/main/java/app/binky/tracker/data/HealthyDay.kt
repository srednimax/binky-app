package app.binky.tracker.data

/**
 * Exactly what the one-tap **"Log a healthy day"** shortcut records.
 *
 * Its own named function, in its own file, because it is the only write in the app that commits facts
 * the owner never sees a form for — so what it claims on their behalf has to be readable in one place
 * and testable without a database.
 *
 * The split is the whole point (CONTEXT.md, ADR-0001):
 *
 * - **All three droppings sub-fields**, not just the amount. They are read from the same glance at the
 *   same tray: an owner who can see the tray is empty can see the pellets are small. Recording only
 *   the amount would make the shortcut record less than the glance it stands for.
 * - **Cecotropes eaten** — the normal state, and an affirmative observation rather than an absence.
 * - **Symptoms affirmatively checked, with no links.** This is the claim `symptomsChecked` exists to
 *   make representable: *looked, none seen*, distinguishable in the database from never having looked
 *   (ADR-0010).
 * - **The graded fields left "not checked".** One tap cannot honestly claim to have assessed appetite,
 *   mood, activity and water, and filling them in with "normal" would be exactly the unverified "fine"
 *   ADR-0001 forbids — manufactured by the app rather than by the owner.
 *
 * Who it covers is the caller's business, not this function's: the fluffle pre-selection, the
 * exclusions with a stated reason, and the snackbar that names them all belong to the UI (ADR-0008).
 */
fun healthyDayFacts(): ObservationFacts =
    ObservationFacts(
        tray =
            TrayFacts(
                droppingsAmount = DroppingsAmount.NORMAL,
                droppingsSize = DroppingsSize.NORMAL,
                droppingsForm = DroppingsForm.ROUND,
                cecotropes = Cecotropes.EATEN,
            ),
        individual = IndividualFacts(symptomsChecked = true),
    )
