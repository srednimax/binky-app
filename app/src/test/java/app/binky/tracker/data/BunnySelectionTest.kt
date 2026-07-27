package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR-0015's self-healing selection, as `(persisted selection, active bunnies) -> resolved
 * selection`. Every state is reachable here with no Android involved, which is why the resolver is
 * a pure function in the first place.
 */
class BunnySelectionTest {
    private val thumper = "thumper-id"
    private val clover = "clover-id"
    private val hazel = "hazel-id"

    @Test
    fun `an active stored bunny is honoured`() {
        assertEquals(
            BunnySelection.Single(thumper),
            resolveSelection(StoredSelection.Bunny(thumper), listOf(thumper, clover)),
        )
    }

    @Test
    fun `no active bunnies is the add-a-bunny empty state`() {
        assertEquals(BunnySelection.Empty, resolveSelection(StoredSelection.Bunny(thumper), emptyList()))
        assertEquals(BunnySelection.Empty, resolveSelection(StoredSelection.All, emptyList()))
        assertEquals(BunnySelection.Empty, resolveSelection(StoredSelection.None, emptyList()))
    }

    @Test
    fun `archiving the selected bunny heals to the sole survivor`() {
        // Thumper was archived, so she is gone from the active list; Clover is all that is left.
        assertEquals(
            BunnySelection.Single(clover),
            resolveSelection(StoredSelection.Bunny(thumper), listOf(clover)),
        )
    }

    @Test
    fun `archiving the selected bunny heals to All when several remain`() {
        // Never silently auto-attribute to an arbitrary one of the survivors.
        assertEquals(
            BunnySelection.All,
            resolveSelection(StoredSelection.Bunny(thumper), listOf(clover, hazel)),
        )
    }

    @Test
    fun `unarchiving restores the stored choice`() {
        val stored = StoredSelection.Bunny(thumper)
        // While archived: healed away from Thumper...
        assertEquals(BunnySelection.All, resolveSelection(stored, listOf(clover, hazel)))
        // ...and back again a week later, because healing never wrote over the stored choice.
        assertEquals(
            BunnySelection.Single(thumper),
            resolveSelection(stored, listOf(thumper, clover, hazel)),
        )
    }

    @Test
    fun `deleting the selected bunny heals the same way`() {
        // A delete additionally clears the id from DataStore, so the stored choice arrives as None.
        assertEquals(BunnySelection.All, resolveSelection(StoredSelection.None, listOf(clover, hazel)))
        assertEquals(BunnySelection.Single(clover), resolveSelection(StoredSelection.None, listOf(clover)))
    }

    @Test
    fun `a single-bunny owner never lands on All`() {
        // Including when "All" is what was stored: for one bunny it is a Home that is a one-card
        // dashboard and a Weight screen that refuses to render (ADR-0015).
        assertEquals(BunnySelection.Single(clover), resolveSelection(StoredSelection.All, listOf(clover)))
        assertEquals(BunnySelection.Single(clover), resolveSelection(StoredSelection.None, listOf(clover)))
    }

    @Test
    fun `All is honoured once two bunnies exist`() {
        assertEquals(BunnySelection.All, resolveSelection(StoredSelection.All, listOf(thumper, clover)))
    }

    @Test
    fun `the archived scope wins outright and does not need the bunny to be active`() {
        assertEquals(
            BunnySelection.Archived(hazel),
            resolveSelection(StoredSelection.Bunny(thumper), listOf(thumper, clover), archivedScope = hazel),
        )
        assertEquals(
            BunnySelection.Archived(hazel),
            resolveSelection(StoredSelection.None, emptyList(), archivedScope = hazel),
        )
    }

    @Test
    fun `leaving the archived scope returns to the stored choice`() {
        val stored = StoredSelection.Bunny(thumper)
        assertEquals(BunnySelection.Archived(hazel), resolveSelection(stored, listOf(thumper), hazel))
        assertEquals(BunnySelection.Single(thumper), resolveSelection(stored, listOf(thumper), null))
    }
}
