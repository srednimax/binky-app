package app.binky.tracker.ui.observations

import app.binky.tracker.data.DroppingsAppearance
import app.binky.tracker.data.DroppingsSize
import app.binky.tracker.data.IndividualFacts
import app.binky.tracker.data.ObservationEntity
import app.binky.tracker.data.TrayFacts
import app.binky.tracker.data.individualFacts
import app.binky.tracker.data.trayFacts
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The timeline, as a pure function over rows (ADR-0008).
 *
 * Two rules live here and nowhere else: rows sharing a group id **collapse into one entry**, and
 * entries are **grouped by day for display only**. Both are display concerns — the storage stays one
 * row per bunny, which is what keeps every per-bunny query, warning and count a plain
 * `WHERE bunnyId = ?`.
 *
 * Collapsing in SQL was the alternative and is rejected: it would hide the join the display rule is
 * about, and it would put the rule somewhere a JVM test cannot reach. No Room and no Android in this
 * file, for the same reason `WeightTrend.kt` and `ObservationFacts.kt` keep them out.
 */

/** One bunny's share of an entry — the facts that legitimately differ between rabbits sharing a tray. */
data class TimelineParticipant(
    /** This bunny's own row. What [ObservationRepository.updateIndividual] and the edit form act on. */
    val observationId: String,
    val bunnyId: String,
    val name: String,
    /** Individual facts, with the symptom ticks filled in from the join table. */
    val facts: IndividualFacts,
)

/** One real-world moment, however many bunnies it covered. */
data class TimelineEntry(
    /**
     * The row an Edit or Delete acts on.
     *
     * Under a single bunny that is *their* row, so editing a mood edits the right rabbit's. Under
     * "All bunnies" there is no focused bunny, so it is the first participant's and the form names
     * whose individual facts it is showing. Tray facts and the participant list are group-wide either
     * way, so only the individual half depends on this at all.
     */
    val id: String,
    val groupId: String?,
    val recordedAt: Instant,
    /** Identical across every participant by construction, so the entry holds one copy. */
    val tray: TrayFacts,
    val participants: List<TimelineParticipant>,
    /**
     * [TrayFacts.trayPhotoPath] resolved to a file to draw, or null when there is no photo.
     *
     * **Filled in by the ViewModel afterwards, not here.** The path on the row is relative and only
     * `MediaFiles` knows what it is relative to — and this file deliberately has no Android in it, so
     * that the collapse rule stays a JVM test rather than an instrumented one.
     */
    val trayPhoto: File? = null,
) {
    /**
     * **`groupId != null`, never `participants.size > 1`** (ADR-0008).
     *
     * The lone survivor of a deleted housemate keeps its group id and goes on reading "observed
     * together", which a count could never do — and silently downgrading it to an individual
     * observation is the exact false attribution the shared model exists to prevent.
     */
    val shared: Boolean get() = groupId != null
}

/** A day's worth of entries, newest first within the day. Display only — nothing is stored per day. */
data class TimelineDay(
    val date: LocalDate,
    val entries: List<TimelineEntry>,
)

/**
 * Collapses [rows] into entries and groups them by calendar day.
 *
 * [rows] arrive in the DAO's stated total order (newest first), and that order is preserved
 * throughout — days come out newest first, and so do the entries inside each day.
 *
 * @param names every bunny this timeline is about, by id. A row whose bunny is **not** in this map is
 *   not rendered, which is where "the combined feed is the *active* fluffle's" is expressed: the
 *   archived housemate's row simply is not offered. The entry still reads as shared, because
 *   [TimelineEntry.shared] asks the group id and not the participant count.
 * @param symptomIds every observation's symptom ticks, by observation id. Absent means no ticks —
 *   which is **not** the same as "nobody looked", a claim only [IndividualFacts.symptomsChecked]
 *   makes (ADR-0010).
 * @param droppingsSizes every observation's recorded sizes, by observation id — a join table since
 *   schema 7, so it arrives beside the rows rather than on them (ADR-0029). Absent means nothing was
 *   recorded, and the timeline prints no line at all rather than "not checked".
 * @param droppingsAppearance the same, for what the droppings looked like.
 * @param focusBunnyId whose row an entry's [TimelineEntry.id] should point at, under a single-bunny
 *   scope. Null under "All bunnies".
 */
fun buildTimeline(
    rows: List<ObservationEntity>,
    names: Map<String, String>,
    symptomIds: Map<String, Set<String>>,
    droppingsSizes: Map<String, Set<DroppingsSize>> = emptyMap(),
    droppingsAppearance: Map<String, Set<DroppingsAppearance>> = emptyMap(),
    focusBunnyId: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineDay> {
    // Kotlin note: LinkedHashMap keeps insertion order, so grouping never disturbs the total order
    // the query already established. `groupBy` returns one of these, which is why the day grouping
    // below can lean on it too.
    val grouped = LinkedHashMap<String, MutableList<ObservationEntity>>()
    for (row in rows) {
        if (row.bunnyId !in names) continue
        // A solo observation is its own group of one. Keying on the id rather than special-casing
        // null keeps the collapse a single pass.
        grouped.getOrPut(row.groupId ?: row.id) { mutableListOf() } += row
    }

    val entries =
        grouped.values.mapNotNull { members ->
            val first = members.first()
            val participants =
                members
                    .map { row ->
                        TimelineParticipant(
                            observationId = row.id,
                            bunnyId = row.bunnyId,
                            name = names.getValue(row.bunnyId),
                            facts = row.individualFacts().copy(symptomIds = symptomIds[row.id] ?: emptySet()),
                        )
                    }
                    // By name, so a shared entry reads the same way every time it is drawn. The id
                    // breaks a tie, because two bunnies really can share a name (ADR-0016).
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.observationId }))

            if (participants.isEmpty()) return@mapNotNull null
            TimelineEntry(
                id =
                    participants.firstOrNull { it.bunnyId == focusBunnyId }?.observationId
                        ?: participants.first().observationId,
                groupId = first.groupId,
                recordedAt = first.recordedAt,
                // Identical on every row in the group by construction, so any of them will do —
                // including for the two sets, which are written per participant precisely so this
                // stays true (ADR-0029).
                tray =
                    first.trayFacts(
                        droppingsSizes = droppingsSizes[first.id].orEmpty(),
                        droppingsAppearance = droppingsAppearance[first.id].orEmpty(),
                    ),
                participants = participants,
            )
        }

    return entries
        .groupBy { it.recordedAt.atZone(zone).toLocalDate() }
        .map { (date, dayEntries) -> TimelineDay(date = date, entries = dayEntries) }
}
