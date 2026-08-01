package app.binky.tracker

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0015's visibility rule, and the migration it creates.
 *
 * **The flip is asserted rather than merely made**, in both directions. At 1.0 this file held that
 * Care & Meds was the one hidden tab, because a checkpoint that quietly un-hid it while adding a tab
 * would have put a stub in front of real owners. At 1.1 it holds the opposite: 4c gave the tab a real
 * screen, so nothing is hidden, and a later checkpoint hiding something without saying so would fail
 * here rather than in the bottom bar.
 */
class NavigationVisibilityTest {
    @Test
    fun `nothing is hidden at 1_1`() {
        // Care & Meds was the only entry ever hidden, and 4c flipped it back — one value, exactly as
        // the enum was built for. A new hidden tab is a decision, and it belongs in this list.
        val hidden = TopLevelDestination.entries.filter { it.visibility == DestinationVisibility.Hidden }
        assertEquals(emptyList<TopLevelDestination>(), hidden)
    }

    @Test
    fun `home is never hidden`() {
        // Home is the bottom of every back stack in the app, and `withoutHiddenDestinations` falls
        // back to it. Hiding it would make that fallback a contradiction.
        assertTrue(TopLevelDestination.HOME.visibility != DestinationVisibility.Hidden)
    }

    @Test
    fun `a stack saved by 1_0 comes back with its Care tab intact`() {
        // The upgrade case, now running the other way: 1.0 wrote stacks with Care & Meds already
        // filtered out, and 1.1 must not filter anything. The key kept its name precisely so a
        // saved stack stays resolvable across the flip.
        val restored: List<NavKey> = listOf(Home, CareAndMeds)
        assertEquals(restored, restored.withoutHiddenDestinations())
    }

    @Test
    fun `an ordinary stack is left exactly as it was`() {
        val restored: List<NavKey> = listOf(Home, Weight, WeightEntry(bunnyId = "bunny-1"))
        assertEquals(restored, restored.withoutHiddenDestinations())
    }

    @Test
    fun `an empty stack falls back to home`() {
        // The defensive branch: it can only fire if Home itself is ever hidden, at which point
        // landing nowhere is the worse failure.
        assertEquals(listOf(Home), emptyList<NavKey>().withoutHiddenDestinations())
    }
}
