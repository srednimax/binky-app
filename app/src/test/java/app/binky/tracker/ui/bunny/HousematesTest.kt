package app.binky.tracker.ui.bunny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    ) = (1..size).map {
        Housemate(id = "id-$it", name = "Bunny $it", avatar = null, archived = it in archived)
    }

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
    fun theSheetListsEveryHousemateTheLineFolds() {
        // The claim the line cannot make (Phase 9f). Five housemates, two of them archived: the
        // line names two and says "& 3 others", and those three exist nowhere else in the app —
        // this is the list that has them, archived ones included and in the order they arrived.
        val fluffle = fluffle(5, archived = setOf(2, 4))
        val capped = capHousemates(fluffle)

        // What the line does: names the two active ones it reaches first, counts the other three.
        assertEquals(listOf("Bunny 1", "Bunny 3"), capped.named.map { it.name })
        assertEquals(3, capped.others)

        // What the sheet does: everyone, in the order they arrived — so the archived ones the line
        // sinks to the back of the fold are back where the profile put them, and marked, not sunk.
        assertEquals(fluffle, housematesInSheet(fluffle))
        // Stated the other way round as well, because this is the claim the feature exists to make:
        // nobody the line folded away is missing from the sheet.
        assertTrue(housematesInSheet(fluffle).containsAll(fluffle - capped.named.toSet()))
    }

    @Test
    fun theSheetHasNoCapAtAnySize() {
        // Not a cap that is merely wide: there is no size at which the sheet stops listing. Nine is
        // past every fold the line performs, and the archived ones are marked, never dropped.
        (1..9).forEach { size ->
            val fluffle = fluffle(size, archived = setOf(1, size))
            assertEquals("$size housemates", fluffle, housematesInSheet(fluffle))
        }
    }

    @Test
    fun anUncappedLineKeepsTheOrderItWasGiven() {
        // Below the cap nothing is reordered — an archived housemate stays where the profile put it,
        // because there is no count for the preference above to serve.
        val housemates = fluffle(3, archived = setOf(1))
        assertEquals(housemates, capHousemates(housemates).named)
    }
}
