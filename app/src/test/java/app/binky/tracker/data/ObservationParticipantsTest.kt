package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Who the form ticks when it opens, and who it says nothing about.
 *
 * A JVM test because [preSelectParticipants] is a pure function over plain entities — the rule is
 * the claim, and Phase 4's Watch predicate landed in it as one more `when` branch with none of 2f's
 * expectations moving, which is what writing it as a filter bought.
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
    fun aHousemateUnderAWatchIsExcludedWithAStatedReason() {
        val clover = bunny("clover", "Clover")

        val selection =
            preSelectParticipants(
                subject = bijou,
                fluffleMembers = listOf(bijou, nugget, clover),
                activelyWatchedIds = setOf("clover"),
            )

        // The one-tap healthy day is the unreviewed write path, and it must not sweep a separated,
        // ill bunny into a shared tray fact (ADR-0008). Stated, not silent: *"Clover is under a
        // watch — log for them separately."*
        assertEquals(listOf("bijou", "nugget"), selection.bunnyIds)
        val excluded = selection.excluded.single()
        assertEquals("clover", excluded.bunnyId)
        assertEquals(ParticipantExclusion.UNDER_WATCH, excluded.reason)
    }

    @Test
    fun aWatchedSubjectIsStillTheSubject() {
        val selection =
            preSelectParticipants(
                subject = bijou,
                fluffleMembers = listOf(bijou, nugget),
                activelyWatchedIds = setOf("bijou"),
            )

        // Starting a watch on Bijou and then logging for Bijou is the ordinary case — indeed it is
        // the point of the watch. The exclusion is about housemates, and the subject rule is what
        // keeps a watched bunny from producing an observation covering nobody.
        assertEquals(listOf("bijou", "nugget"), selection.bunnyIds)
        assertTrue(selection.excluded.isEmpty())
    }

    @Test
    fun archivedOutranksWatchedWhenSomehowBothAreTrue() {
        val clover = bunny("clover", "Clover", archivedAt = Instant.parse("2026-01-01T00:00:00Z"))

        val selection =
            preSelectParticipants(
                subject = bijou,
                fluffleMembers = listOf(bijou, clover),
                activelyWatchedIds = setOf("clover"),
            )

        // Archiving closes any watch, so this pair cannot arise from a live database — the test
        // exists to pin which answer wins if it ever does. "Archived" explains more, and the
        // ordering being deliberate is the difference between a decision and an accident.
        assertEquals(ParticipantExclusion.ARCHIVED, selection.excluded.single().reason)
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
