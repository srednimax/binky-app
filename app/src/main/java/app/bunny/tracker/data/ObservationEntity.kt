package app.bunny.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/*
 * The observation vocabularies.
 *
 * Every one of these is a **closed** list, so each is an enum with a `TypeConverter` rather than a
 * loose string (house rule), stored by **name, never ordinal** — adding a value later must not
 * rewrite what was already recorded. Symptoms are the deliberate exception and live in a table,
 * because owners add their own (ADR-0010).
 *
 * **None of them has a `NOT_CHECKED` entry.** The column is nullable and `null` *is* "not checked"
 * (ADR-0001): absence with two spellings is absence nobody can query. That is why an untouched
 * droppings field records nothing rather than a silent "normal" — a "fine" nobody verified is a
 * false reassurance.
 *
 * Kotlin note: these read like TypeScript string-literal unions in use, but they are real types —
 * `when` over one is exhaustive without a default branch, which is what makes adding a value show
 * up as a compile error at every place that renders it.
 */

/** How much was in the tray. `NONE` is a real, alarming observation — an empty tray, not a blank field. */
enum class DroppingsAmount { NONE, FEW, NORMAL, MANY }

/** Pellet size. Small pellets are one of the earliest signs of a slowing gut. */
enum class DroppingsSize { SMALL, NORMAL, LARGE }

/**
 * Pellet form. `STRUNG_TOGETHER` is its own value rather than a note: pellets strung on fur is a
 * distinct, recognisable sign, and a form that can be counted over time is worth more than prose.
 */
enum class DroppingsForm { ROUND, MISSHAPEN, STRUNG_TOGETHER, SOFT, DIARRHOEA }

/**
 * Cecotropes — the soft nutrient-rich droppings a bunny normally eats directly, and **not the same
 * thing as ordinary droppings** (CONTEXT.md), which is why they are their own field.
 *
 * Two values, because the observable fact is binary: either none are lying about (they were eaten)
 * or some are. Being an enum rather than a `Boolean?` leaves room to distinguish an *excess* later
 * without rewriting history, which is exactly what storing by name buys.
 */
enum class Cecotropes { EATEN, LEFT_UNEATEN }

/** Appetite. A graded field: the one-tap healthy day deliberately leaves it "not checked". */
enum class Appetite { NONE, REDUCED, NORMAL, EAGER }

/** Mood. Graded, and the field the app is most careful never to treat as a health signal on its own. */
enum class Mood { WITHDRAWN, SUBDUED, NORMAL, BRIGHT }

/**
 * Activity level. Named `ActivityLevel` rather than `Activity` on purpose — an `Activity` in a
 * Compose file would collide with Android's own, and the shorter name is not worth the import
 * ceremony at every use.
 */
enum class ActivityLevel { LETHARGIC, QUIET, NORMAL, ACTIVE }

/** Water intake, judged against this bunny's own normal rather than any absolute. */
enum class WaterIntake { NONE, LESS, NORMAL, MORE }

/**
 * One observation, **as it applies to one bunny**.
 *
 * A shared observation is stored as one row per bunny linked by [groupId] (ADR-0008), so every
 * per-bunny query, chart and warning stays a plain `WHERE bunnyId = ?` while the attribution stays
 * honest. Bonded bunnies share a litter tray, so droppings frequently cannot be attributed to an
 * individual, and forcing every observation onto exactly one bunny would record something untrue.
 *
 * **Sharedness is `groupId IS NOT NULL`, never a count of rows sharing it** (ADR-0008), and there is
 * deliberately no `observedTogether` column — a second spelling of the same fact, able to do nothing
 * but drift out of step with the first. The lone survivor of a deleted housemate keeps its group id
 * and goes on reading "observed together", which a count could never do.
 *
 * The fields fall into two classes and the edit paths must respect the split (see
 * [ObservationRepository]):
 *
 * - **Tray-level** — the droppings fields and [cecotropes]. One tray, one real-world fact: identical
 *   across every row in a group by construction, and editing one propagates to all of them. Letting
 *   them drift would reintroduce the false attribution through editing rather than tapping.
 * - **Individual** — [appetite], [mood], [activity], [water], [note], the symptom links and
 *   [symptomsChecked]. These legitimately differ per bunny (one hunched and lethargic while the
 *   other is bouncing around), so editing one bunny's mood never touches another's.
 *
 * Two timestamps for the same reason as [WeightEntity]: [recordedAt] is when it was noticed and is
 * back-datable, [createdAt] is when the row was typed and breaks ties in the display order.
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            // An observation of a bunny is meaningless without the bunny, so it goes with them. For
            // a shared observation this removes only that bunny's row — the survivors keep theirs,
            // and keep their group id (ADR-0008).
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Every timeline read is "this bunny's observations, newest first".
        Index(value = ["bunnyId", "recordedAt"]),
        // Group reads and the group-wide tray update, plus `recordCounts`' survivorship EXISTS.
        Index("groupId"),
    ],
)
data class ObservationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    /** Non-null only when this observation covers more than one bunny. See the class doc. */
    val groupId: String? = null,
    val recordedAt: Instant,
    val createdAt: Instant = Instant.now(),
    // Tray-level: single per group, identical across every row, edited group-wide.
    val droppingsAmount: DroppingsAmount? = null,
    val droppingsSize: DroppingsSize? = null,
    val droppingsForm: DroppingsForm? = null,
    val cecotropes: Cecotropes? = null,
    // Individual: per row, edited one row at a time.
    val appetite: Appetite? = null,
    val mood: Mood? = null,
    val activity: ActivityLevel? = null,
    val water: WaterIntake? = null,
    val note: String? = null,
    /**
     * Whether the owner **looked** for symptoms, independent of whether any were found.
     *
     * Non-nullable deliberately (ADR-0010). No symptom links means either "I looked, nothing wrong"
     * or "I never opened the picker" — indistinguishable, which is ADR-0001's silence failure landing
     * on the one field symptoms exist to make queryable, and it makes the one-tap healthy day's
     * central claim unrepresentable. A `Boolean?` would give "didn't look" two spellings, which is
     * precisely why the graded vocabularies above use `null` for absence and this does not.
     *
     * Not a second spelling of "count of links > 0" either: it carries the one state the join table
     * cannot express. Any link implies `true`, enforced on every write in [ObservationRepository].
     */
    val symptomsChecked: Boolean = false,
)

/**
 * Which symptoms were ticked on an observation — a **binary tick**, present at that noticed moment,
 * and nothing more (ADR-0010). No severity field: severity is carried by the symptom's identity
 * ("*loud* teeth grinding" is already a different symptom from soft tooth-purring), and no duration
 * or "resolved" state, because an observation is a snapshot and a persistent symptom is expressed by
 * re-ticking it later.
 *
 * The composite primary key makes a double-tick impossible and gives the observation-side foreign key
 * its index for free; [symptomId] needs its own.
 */
@Entity(
    tableName = "observation_symptoms",
    primaryKeys = ["observationId", "symptomId"],
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SymptomEntity::class,
            parentColumns = ["id"],
            childColumns = ["symptomId"],
            // **No cascade from the symptom**, on purpose: retiring a symptom hides it from the
            // picker and must never delete it from historical observations (ADR-0010). The app has
            // no delete path for a symptom at all, and this constraint is what says so — a stray
            // `DELETE FROM symptoms` fails rather than quietly editing history.
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("symptomId")],
)
data class ObservationSymptomEntity(
    val observationId: String,
    val symptomId: String,
)
