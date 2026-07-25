package app.bunny.tracker.ui.shell

import app.bunny.tracker.data.BunnySelection

/** Just enough of a bunny to identify it in the switcher and on a stub. */
data class BunnySummary(
    val id: String,
    val name: String,
    /** Relative, `avatars/<uuid>.jpg` (house rule). Rendered from checkpoint 1d, when Coil lands. */
    val avatarPath: String?,
)

/**
 * State for the app shell — the switcher, the bottom bar, and the scope line every stub renders.
 *
 * One immutable data class per screen is the house rule; the shell is the one "screen" that spans
 * all five destinations, so the stubs read it rather than each owning a ViewModel that Phases 2-3
 * would immediately rewrite.
 */
data class ShellUiState(
    val selection: BunnySelection = BunnySelection.Loading,
    /** Active bunnies, in display order — the switcher's menu. */
    val activeBunnies: List<BunnySummary> = emptyList(),
    /** The bunny [selection] points at, when it points at one. Null under All / Empty / Loading. */
    val scopedBunny: BunnySummary? = null,
) {
    /**
     * "All bunnies" is offered **only once two active bunnies exist** (ADR-0015): for a one-bunny
     * owner it is otherwise a two-tap path to a one-card Home and a Weight screen that refuses to
     * render, in exchange for nothing.
     */
    val offersAllBunnies: Boolean get() = activeBunnies.size >= 2

    /** An archived bunny is a read-only scope: a banner, and no write actions (ADR-0015). */
    val readOnly: Boolean get() = selection is BunnySelection.Archived
}
