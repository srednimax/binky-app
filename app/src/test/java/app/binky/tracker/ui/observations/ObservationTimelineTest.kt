package app.binky.tracker.ui.observations

import app.binky.tracker.data.Cecotropes
import app.binky.tracker.data.DroppingsAmount
import app.binky.tracker.data.DroppingsAppearance
import app.binky.tracker.data.DroppingsSize
import app.binky.tracker.data.Mood
import app.binky.tracker.data.ObservationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The collapse rule, as a JVM test — which is the whole reason [buildTimeline] is a pure function
 * over rows rather than a `GROUP BY` in the DAO.
 *
 * Storage is one row per bunny (ADR-0008). Every case below is about the gap between that and what
 * an owner should see: one card per real-world moment, the tray read once, the bunnies told apart.
 */
class ObservationTimelineTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(
        day: Int,
        hour: Int,
    ): Instant = LocalDateTime.of(2026, 7, day, hour, 0).atZone(zone).toInstant()

    private val names = mapOf("bijou" to "Bijou", "nugget" to "Nugget")

    /** A shared pair: two rows, one group id, identical tray facts — exactly what the repository writes. */
    private fun sharedPair(
        recordedAt: Instant,
        groupId: String = "g1",
        bijouMood: Mood? = Mood.SUBDUED,
        nuggetMood: Mood? = Mood.BRIGHT,
    ) = listOf(
        ObservationEntity(
            id = "row-bijou",
            bunnyId = "bijou",
            groupId = groupId,
            recordedAt = recordedAt,
            droppingsAmount = DroppingsAmount.FEW,
            cecotropes = Cecotropes.LEFT_UNEATEN,
            mood = bijouMood,
        ),
        ObservationEntity(
            id = "row-nugget",
            bunnyId = "nugget",
            groupId = groupId,
            recordedAt = recordedAt,
            droppingsAmount = DroppingsAmount.FEW,
            cecotropes = Cecotropes.LEFT_UNEATEN,
            mood = nuggetMood,
        ),
    )

    @Test
    fun aSharedObservationAppearsOnceNamingBothBunnies() {
        val days = buildTimeline(sharedPair(at(20, 18)), names, emptyMap(), zone = zone)

        // One card, not two. Two rows in the database is an implementation fact about attribution;
        // showing it twice would tell the owner they looked twice.
        assertEquals(1, days.size)
        assertEquals(1, days.single().entries.size)
        val entry = days.single().entries.single()
        assertEquals(listOf("Bijou", "Nugget"), entry.participants.map { it.name })
    }

    @Test
    fun trayFactsAreReadOnceAndIndividualFactsStayPerBunny() {
        val entry = buildTimeline(sharedPair(at(20, 18)), names, emptyMap(), zone = zone).single().entries.single()

        // One tray, one reading of it — the entry holds a single copy rather than one per bunny.
        assertEquals(DroppingsAmount.FEW, entry.tray.droppingsAmount)
        assertEquals(Cecotropes.LEFT_UNEATEN, entry.tray.cecotropes)
        // ...while the moods stay apart. A bug that copied one bunny's mood onto the other is exactly
        // the false attribution the tray/individual split exists to prevent.
        assertEquals(
            Mood.SUBDUED,
            entry.participants
                .first { it.name == "Bijou" }
                .facts.mood,
        )
        assertEquals(
            Mood.BRIGHT,
            entry.participants
                .first { it.name == "Nugget" }
                .facts.mood,
        )
    }

    @Test
    fun theMultiValuedTrayFieldsArriveWholeFromTheJoinTables() {
        // Two appearance values at once is the case ADR-0029 exists for: a tray really can hold round
        // pellets *and* soft ones, and before schema 7 the owner had to pick one and file the rest as
        // prose. The links come in keyed by row id, because that is where they hang (there is no
        // group table), and the entry has to end up holding one copy of the tray either way.
        val rows = sharedPair(at(20, 18))
        val days =
            buildTimeline(
                rows = rows,
                names = names,
                symptomIds = emptyMap(),
                droppingsSizes = mapOf("row-bijou" to setOf(DroppingsSize.SMALL, DroppingsSize.NORMAL)),
                droppingsAppearance =
                    mapOf("row-bijou" to setOf(DroppingsAppearance.ROUND, DroppingsAppearance.SOFT)),
                zone = zone,
            )

        val tray =
            days
                .single()
                .entries
                .single()
                .tray
        assertEquals(setOf(DroppingsSize.SMALL, DroppingsSize.NORMAL), tray.droppingsSizes)
        assertEquals(setOf(DroppingsAppearance.ROUND, DroppingsAppearance.SOFT), tray.droppingsAppearance)
    }

    @Test
    fun anObservationWithNoDroppingsLinksReadsAsNothingRecorded() {
        // Empty rather than absent, and never "normal": zero rows is the join table's spelling of the
        // nullable columns' null, and the app must not read it as a healthy tray (ADR-0001).
        val tray =
            buildTimeline(sharedPair(at(20, 18)), names, emptyMap(), zone = zone)
                .single()
                .entries
                .single()
                .tray

        assertTrue(tray.droppingsSizes.isEmpty())
        assertTrue(tray.droppingsAppearance.isEmpty())
    }

    @Test
    fun sharednessSurvivesWhenOnlyOneParticipantResolves() {
        // The housemate was deleted, or is archived and out of scope: one row of the group is left.
        val survivor = sharedPair(at(20, 18)).take(1)

        val entry = buildTimeline(survivor, names, emptyMap(), zone = zone).single().entries.single()

        assertEquals(1, entry.participants.size)
        // Still "observed together" (ADR-0008). Reading sharedness off the participant count would
        // silently downgrade this to an individual observation and rewrite what was recorded.
        assertTrue(entry.shared)
    }

    @Test
    fun aSoloObservationIsNotShared() {
        val solo =
            listOf(ObservationEntity(id = "solo", bunnyId = "bijou", recordedAt = at(20, 18), mood = Mood.NORMAL))

        val entry = buildTimeline(solo, names, emptyMap(), zone = zone).single().entries.single()

        assertFalse(entry.shared)
        assertEquals(1, entry.participants.size)
    }

    @Test
    fun rowsOutsideTheNamesMapAreNotRendered() {
        val rows = sharedPair(at(20, 18)) + ObservationEntity(bunnyId = "clover", recordedAt = at(20, 9))

        // "The combined feed is the *active* fluffle's": a bunny the caller did not pass simply is
        // not in it. One rule, one place — no second filter in the ViewModel.
        val entries = buildTimeline(rows, names, emptyMap(), zone = zone).flatMap { it.entries }
        assertEquals(1, entries.size)
        assertTrue(entries.single().participants.none { it.bunnyId == "clover" })
    }

    @Test
    fun entriesAreGroupedByCalendarDayNewestFirst() {
        // The DAO's stated order: newest first. Two moments on the 20th, one on the 18th.
        val rows =
            listOf(
                ObservationEntity(id = "a", bunnyId = "bijou", recordedAt = at(20, 18)),
                ObservationEntity(id = "b", bunnyId = "bijou", recordedAt = at(20, 8)),
                ObservationEntity(id = "c", bunnyId = "nugget", recordedAt = at(18, 12)),
            )

        val days = buildTimeline(rows, names, emptyMap(), zone = zone)

        assertEquals(2, days.size)
        assertEquals(listOf(20, 18), days.map { it.date.dayOfMonth })
        // Order is preserved rather than re-sorted: grouping must not disturb what the query decided.
        assertEquals(listOf("a", "b"), days.first().entries.map { it.id })
        assertEquals(listOf("c"), days.last().entries.map { it.id })
    }

    @Test
    fun theFocusedBunnysRowIsWhatEditActsOn() {
        val rows = sharedPair(at(20, 18))

        // Under a single bunny, editing a mood has to edit *that* rabbit's row.
        val focused = buildTimeline(rows, names, emptyMap(), focusBunnyId = "nugget", zone = zone)
        assertEquals(
            "row-nugget",
            focused
                .single()
                .entries
                .single()
                .id,
        )

        // Under "All bunnies" there is no focused bunny, so the first participant's row stands in and
        // the form names whose individual facts it is showing.
        val unfocused = buildTimeline(rows, names, emptyMap(), zone = zone)
        assertEquals(
            "row-bijou",
            unfocused
                .single()
                .entries
                .single()
                .id,
        )
    }

    @Test
    fun symptomTicksAreFilledInPerRowFromTheJoinTable() {
        val rows = sharedPair(at(20, 18))

        val entry =
            buildTimeline(
                rows,
                names,
                symptomIds = mapOf("row-bijou" to setOf("s-hunched", "s-grinding")),
                zone = zone,
            ).single().entries.single()

        assertEquals(
            setOf("s-hunched", "s-grinding"),
            entry.participants
                .first { it.name == "Bijou" }
                .facts.symptomIds,
        )
        // Absent from the map means no ticks — which is not the same claim as "nobody looked", and
        // only `symptomsChecked` makes that one (ADR-0010).
        val nugget = entry.participants.first { it.name == "Nugget" }
        assertEquals(emptySet<String>(), nugget.facts.symptomIds)
        assertFalse(nugget.facts.symptomsChecked)
    }

    @Test
    fun twoSeparateGroupsAtTheSameMomentStayTwoEntries() {
        // Two bonded pairs observed in the same minute: collapsing keys on the group id, never on the
        // timestamp, or one household's tray would be attributed to another's.
        val rows = sharedPair(at(20, 18)) + sharedPair(at(20, 18), groupId = "g2").map { it.copy(id = it.id + "-2") }

        val entries = buildTimeline(rows, names, emptyMap(), zone = zone).single().entries

        assertEquals(2, entries.size)
        assertEquals(listOf("g1", "g2"), entries.map { it.groupId })
    }
}
