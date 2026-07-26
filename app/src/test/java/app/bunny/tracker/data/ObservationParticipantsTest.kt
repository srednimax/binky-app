package app.bunny.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Who the form ticks when it opens, and who it says nothing about.
 *
 * A JVM test because [preSelectParticipants] is a pure function over plain entities — the rule is
 * the claim, and Phase 4's Watch predicate has to land in it as one more `when` branch without any
 * of these expectations moving.
 */
class ObservationParticipantsTest {
    private fun bunny(
        id: String,
        name: String,
        archivedAt: Instant? = null,
    ) = BunnyEntity(id = id, name = name, archivedAt = archivedAt)

    private val bijou = bunny("bijou", "Bijou")
    private val nugget = bunny("nugget", "Nugget")

    @Test
    fun theSubjectIsAlwaysACandidate() {
        val selection = preSelectParticipants(subject = bijou, fluffleMembers = emptyList())

        // The owner asked to log for this bunny. A rule that could drop them would mint an
        // observation covering nobody.
        assertEquals(listOf("bijou"), selection.bunnyIds)
        assertTrue(selection.excluded.isEmpty())
    }

    @Test
    fun everyoneTheyLiveWithIsTickedAlready() {
        val selection = preSelectParticipants(subject = bijou, fluffleMembers = listOf(bijou, nugget))

        // Bonded bunnies share a tray, so the common case is one observation covering both — the
        // owner unticks rather than tediously ticking (ADR-0008).
        assertEquals(listOf("bijou", "nugget"), selection.bunnyIds)
    }

    @Test
    fun theSubjectIsNotOfferedTwiceWhenTheyAreInTheirOwnFluffle() {
        // `fluffleMembers` is the whole fluffle as the DAO returns it, subject included.
        val selection = preSelectParticipants(subject = bijou, fluffleMembers = listOf(bijou, nugget))

        assertEquals(1, selection.candidates.count { it.bunnyId == "bijou" })
    }

    @Test
    fun anArchivedHousemateIsExcludedWithAStatedReason() {
        val clover = bunny("clover", "Clover", archivedAt = Instant.parse("2026-01-01T00:00:00Z"))

        val selection = preSelectParticipants(subject = bijou, fluffleMembers = listOf(bijou, nugget, clover))

        // Not a silent absence. Archiving stops new writes and leaves past shared observations
        // intact (ADR-0004, ADR-0008), so the form can say why rather than let the owner wonder.
        assertEquals(listOf("bijou", "nugget"), selection.bunnyIds)
        val excluded = selection.excluded.single()
        assertEquals("clover", excluded.bunnyId)
        assertEquals("Clover", excluded.name)
        assertEquals(ParticipantExclusion.ARCHIVED, excluded.reason)
    }

    @Test
    fun anArchivedSubjectIsStillTheSubject() {
        val archivedBijou = bunny("bijou", "Bijou", archivedAt = Instant.parse("2026-01-01T00:00:00Z"))

        val selection = preSelectParticipants(subject = archivedBijou, fluffleMembers = listOf(archivedBijou))

        // The read-only scope is what stops an archived bunny being written to; this function is not
        // a second gate, and making it one would leave the caller with an empty participant list.
        assertEquals(listOf("bijou"), selection.bunnyIds)
        assertTrue(selection.excluded.isEmpty())
    }

    @Test
    fun housematesAreOfferedByNameWithTheSubjectFirst() {
        val ash = bunny("ash", "ash")
        val clover = bunny("clover", "Clover")

        val selection = preSelectParticipants(subject = nugget, fluffleMembers = listOf(clover, ash, nugget))

        // Subject first because the form is about them; the rest by name, case-insensitively, so the
        // order does not shuffle between two draws of the same fluffle.
        assertEquals(listOf("Nugget", "ash", "Clover"), selection.candidates.map { it.name })
    }
}
