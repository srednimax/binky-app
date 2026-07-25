package app.bunny.tracker.data

/**
 * The owner's last *explicit* choice, as persisted. Deliberately smaller than [BunnySelection]:
 * only a real decision is stored, never a fallback the app computed on their behalf.
 *
 * Kotlin note: a `sealed interface` is the equivalent of a TypeScript discriminated union — the
 * compiler knows the complete set of cases, so a `when` over one needs no `else` branch and starts
 * failing to compile the day a case is added. `data object` is the singleton case (no fields);
 * `data class` the one that carries a payload.
 */
sealed interface StoredSelection {
    /** Nothing has been chosen yet — a fresh install. */
    data object None : StoredSelection

    data object All : StoredSelection

    data class Bunny(
        val id: String,
    ) : StoredSelection
}

/** What the app should actually show, once the stored choice is read against reality. */
sealed interface BunnySelection {
    /** Before the first database and preferences emission. Never returned by [resolveSelection]. */
    data object Loading : BunnySelection

    /** No active bunnies exist: the add-a-bunny empty state. */
    data object Empty : BunnySelection

    /** Fluffle-wide scope. Offered only once two active bunnies exist (ADR-0015). */
    data object All : BunnySelection

    data class Single(
        val id: String,
    ) : BunnySelection

    /**
     * A read-only scope onto an archived bunny, entered only from the archived list and **never
     * persisted** — a background kill must not reopen the app into a memorial (ADR-0015).
     */
    data class Archived(
        val id: String,
    ) : BunnySelection
}

/**
 * Resolves what to show from what was chosen and what still exists.
 *
 * This is the whole of ADR-0015's self-healing, and it is a pure function on purpose: every state
 * — archiving the selected bunny, deleting it, unarchiving it a week later — is reachable in a JVM
 * test with no Android involved.
 *
 * Healing is **resolve-on-read with no write-back**. [stored] keeps the owner's last explicit
 * choice even while it cannot be honoured, so unarchiving a bunny restores the selection rather
 * than leaving them wherever the fallback put them — and a write hidden inside a read path is how
 * a `Flow` graph acquires feedback loops.
 *
 * @param activeBunnyIds the live list of non-archived bunnies, in display order.
 * @param archivedScope set only while the owner has deliberately opened an archived bunny. It
 *   wins outright, which is what separates the two cases the fallbacks would otherwise conflate:
 *   the selected bunny having *vanished* (heal) versus one having been *deliberately opened*.
 */
fun resolveSelection(
    stored: StoredSelection,
    activeBunnyIds: List<String>,
    archivedScope: String? = null,
): BunnySelection {
    if (archivedScope != null) return BunnySelection.Archived(archivedScope)
    val soleBunny = activeBunnyIds.singleOrNull()
    return when {
        activeBunnyIds.isEmpty() -> BunnySelection.Empty
        // The honoured case: the chosen bunny is still active.
        stored is StoredSelection.Bunny && stored.id in activeBunnyIds -> BunnySelection.Single(stored.id)
        // One bunny left — including a stored "All", because for a single-bunny owner "All bunnies"
        // is a Home that is a one-card dashboard and a Weight screen that refuses to render.
        soleBunny != null -> BunnySelection.Single(soleBunny)
        // Several bunnies and no usable choice: never silently auto-attribute to an arbitrary one.
        else -> BunnySelection.All
    }
}
