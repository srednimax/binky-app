package app.binky.tracker

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ADR-0015's visibility rule, and the one migration it creates.
 *
 * The flip itself is asserted rather than merely made: 1.0 ships with Care & Meds hidden, and a
 * checkpoint that quietly un-hid it while adding a tab would otherwise put a stub in front of real
 * owners, which is the exact outcome [DestinationVisibility] exists to prevent.
 */
class NavigationVisibilityTest {
    @Test
    fun `care and meds is the only hidden tab at 1_0`() {
        val hidden = TopLevelDestination.entries.filter { it.visibility == DestinationVisibility.Hidden }
        assertEquals(listOf(TopLevelDestination.CARE), hidden)
    }

    @Test
    fun `home is never hidden`() {
        // Home is the bottom of every back stack in the app, and `withoutHiddenDestinations` falls
        // back to it. Hiding it would make that fallback a contradiction.
        assertTrue(TopLevelDestination.HOME.visibility != DestinationVisibility.Hidden)
    }

    @Test
    fun `a restored stack naming a hidden tab resolves to home`() {
        // The upgrade case: a stack saved by a build where Care & Meds was still live.
        val restored: List<NavKey> = listOf(Home, CareAndMeds)
        assertEquals(listOf(Home), restored.withoutHiddenDestinations())
    }

    @Test
    fun `a detail screen above a hidden tab survives`() {
        // Detail routes are not top-level destinations and have no visibility of their own, so the
        // filter must not take them out with the tab they happened to sit above.
        val restored: List<NavKey> = listOf(Home, CareAndMeds, Settings)
        assertEquals(listOf(Home, Settings), restored.withoutHiddenDestinations())
    }

    @Test
    fun `an ordinary stack is left exactly as it was`() {
        val restored: List<NavKey> = listOf(Home, Weight, WeightEntry(bunnyId = "bunny-1"))
        assertEquals(restored, restored.withoutHiddenDestinations())
    }
}
