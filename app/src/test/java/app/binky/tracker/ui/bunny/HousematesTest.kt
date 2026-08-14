package app.binky.tracker.ui.bunny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The housemates line's count cap (Phase 7.5 §8) — the half a JVM test can hold.
 *
 * A five-bunny fluffle grew the profile card by two lines and nobody had ever seen it, because the
 * sample data has exactly two bonded bunnies with short names. This is the rule that folds the extra
 * names into a count; **how wide the result draws is not testable here** and is seen on the device
 * through the `crowded` seed variant, which is what the seed variants exist for.
 */
class HousematesTest {
    private fun fluffle(
        size: Int,
        archived: Set<Int> = emptySet(),
    ) = (1..size).map { Housemate(id = "id-$it", name = "Bunny $it", archived = it in archived) }

    @Test
    fun namesEveryHousemateUpToThree() {
        // Three is the last size where naming everyone is also the *shorter* string: "A, B & C"
        // against "A, B & 1 other".
        (1..3).forEach { size ->
            val capped = capHousemates(fluffle(size))
            assertEquals("$size housemates", fluffle(size), capped.named)
            assertEquals("$size housemates", 0, capped.others)
        }
    }

    @Test
    fun foldsFromFourHousematesUp() {
        val four = capHousemates(fluffle(4))
        assertEquals(listOf("Bunny 1", "Bunny 2"), four.named.map { it.name })
        assertEquals(2, four.others)

        assertEquals(3, capHousemates(fluffle(5)).others)
        assertEquals(7, capHousemates(fluffle(9)).others)
    }

    @Test
    fun neverFoldsExactlyOneHousemateIntoTheCount() {
        // "& 1 other" would name a bunny by not naming it, and would be the longer string as well.
        // The rule that prevents it is the fold starting at four rather than at three, so this is
        // asserted over the whole range rather than at the one size it could go wrong.
        (1..9).forEach { size ->
            assertNotEquals("$size housemates", 1, capHousemates(fluffle(size)).others)
        }
    }

    @Test
    fun archivedHousematesAreTheOnesTheCountAbsorbs() {
        // They render longer — "Hazel (archived)" — and are the least relevant names on a line about
        // who the bunny lives with now.
        val capped = capHousemates(fluffle(5, archived = setOf(1, 2)))
        assertEquals(listOf("Bunny 3", "Bunny 4"), capped.named.map { it.name })
        assertEquals(3, capped.others)
    }

    @Test
    fun namesArchivedHousematesWhenThereIsNobodyElse() {
        // The fold is a preference, not a filter: five archived housemates still name two.
        val capped = capHousemates(fluffle(5, archived = setOf(1, 2, 3, 4, 5)))
        assertEquals(listOf("Bunny 1", "Bunny 2"), capped.named.map { it.name })
        assertEquals(3, capped.others)
    }

    @Test
    fun anUncappedLineKeepsTheOrderItWasGiven() {
        // Below the cap nothing is reordered — an archived housemate stays where the profile put it,
        // because there is no count for the preference above to serve.
        val housemates = fluffle(3, archived = setOf(1))
        assertEquals(housemates, capHousemates(housemates).named)
    }
}
