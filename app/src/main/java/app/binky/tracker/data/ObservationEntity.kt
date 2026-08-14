package app.binky.tracker.data

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
 * **Two of them are multi-valued and live in join tables instead** (ADR-0029): a tray genuinely holds
 * round pellets *and* soft ones, which is the commonest early sign of a gut going wrong. There,
 * absence is **zero rows** — and no `droppingsChecked` flag is owed to disambiguate it, unlike
 * symptoms, because there is no affirmative "I looked and the pellets had no appearance": if the
 * owner looked, at least one value is true. The one real edge, an empty tray, is already
 * [DroppingsAmount.NONE].
 *
 * Kotlin note: these read like TypeScript string-literal unions in use, but they are real types —
 * `when` over one is exhaustive without a default branch, which is what makes adding a value show
 * up as a compile error at every place that renders it.
 */

/** How much was in the tray. `NONE` is a real, alarming observation — an empty tray, not a blank field. */
enum class DroppingsAmount { NONE, FEW, NORMAL, MANY }

/**
 * Pellet size. Small pellets are one of the earliest signs of a slowing gut.
 *
 * **Multi-valued** (ADR-0029): *small and normal* is a thing an owner can actually see in one tray,
 * where `FEW` and `MANY` would be a contradiction about it — which is why [DroppingsAmount] stayed
 * single. Stored in [ObservationDroppingsSizeEntity].
 */
enum class DroppingsSize { SMALL, NORMAL, LARGE }

/**
 * What the droppings **looked like** — also multi-valued, and also its own table
 * ([ObservationDroppingsAppearanceEntity]).
 *
 * Named for appearance rather than shape, because only [DOUBLED] is a shape: [BLOOD] and [MUCUS] are
 * contents, [VERY_DARK] is colour and [DRY] is moisture (ADR-0029). `DroppingsForm.BLOOD` would read
 * as a claim that blood is a shape, at every call site, forever.
 *
 * Three of the values close gaps that mattered:
 *
 * - [MUCUS] exists because [STRUNG_TOGETHER] was a **trap for it**, not merely missing. That value
 *   means strung *with fur* — moulting — and mucus presents identically: thick pale goop strung
 *   between the pellets. An owner seeing gut irritation would reasonably have recorded moulting, so
 *   the fur label was reworded to name the fur at the same time.
 * - [BLOOD], because the app seeded `symptom_blood_in_urine` — usually harmless porphyrins — and had
 *   nowhere at all to record the one that is always serious. It cannot be a symptom: symptoms are
 *   individual and a shared tray is not attributable to a bunny (ADR-0008).
 * - [DOUBLED] rather than folding fused pellets into [MISSHAPEN], because they specifically mean
 *   motility is slowing.
 *
 * *Pale* and *greenish* were considered and left out on triage: they are the weakest signals in the
 * set, and a vocabulary that records everything nameable trains the owner to record nothing
 * carefully. **The app records every one of these without commenting on it** — no "see a vet now"
 * attached to [BLOOD], because that is advice (ADR-0026).
 */
enum class DroppingsAppearance {
    ROUND,
    MISSHAPEN,
    DOUBLED,
    DRY,
    STRUNG_TOGETHER,
    MUCUS,
    SOFT,
    DIARRHOEA,
    VERY_DARK,
    BLOOD,
}

/**
 * Cecotropes — the soft nutrient-rich droppings a bunny normally eats directly, and **not the same
 * thing as ordinary droppings** (CONTEXT.md), which is why they are their own field.
 *
 * Single-valued: like [DroppingsAmount] this describes a quantity rather than a mixture. [EXCESS] is
 * the value the two-value version's own doc anticipated — being an enum rather than a `Boolean?` is
 * exactly what let it arrive without rewriting history, because only names are ever stored.
 */
enum class Cecotropes { EATEN, LEFT_UNEATEN, EXCESS }

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
 * - **Tray-level** — the droppings fields, [cecotropes] and [trayPhotoPath]. One tray, one real-world
 *   fact: identical across every row in a group by construction, and editing one propagates to all of
 *   them. Letting them drift would reintroduce the false attribution through editing rather than
 *   tapping. Two of these facts are now sets rather than columns, so "identical across every row"
 *   costs join rows written per participant rather than a `copy()` — see [ObservationRepository].
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
    // Tray-level: single per group, identical across every row, edited group-wide. The multi-valued
    // two live in the join tables below, keyed on this row's id and written for every participant.
    val droppingsAmount: DroppingsAmount? = null,
    val cecotropes: Cecotropes? = null,
    /**
     * One photo of the tray, relative to `filesDir` under `observations/` (house rule, ADR-0029).
     *
     * **Duplicated across every row in the group**, like every other tray fact, which buys one rule
     * the app did not need before: deleting one bonded bunny cascades a row that still references the
     * survivor's file, so the file goes only when **no other row references the path**. That check
     * lives on the delete path in [ObservationRepository], and it is the whole reason a duplicated
     * path was acceptable instead of a group table.
     *
     * It takes the ordinary photo capture path and never the document scanner: ML Kit's filter clips
     * highlights and edge-enhances, which destroys pellet outlines exactly where the light was good.
     */
    val trayPhotoPath: String? = null,
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

/*
 * The two multi-valued droppings fields (ADR-0029).
 *
 * **Keyed on `observationId`, not on a group**, because there is no group *table* — `groupId` is a
 * bare nullable column and ADR-0008 forbids stamping one on a solo observation. So a tray-level set
 * is denormalised onto every participant's row exactly as the tray columns already are, and
 * [ObservationRepository] writes them inside the transaction that writes the rows.
 *
 * **Two typed tables rather than one with a `kind` discriminator.** The generic table saves one
 * `CREATE TABLE` and buys a database that can hold `kind = SIZE, value = ROUND`; a representable
 * nonsense state is worse than a duplicated table definition, and the Kotlin side loses with it —
 * two typed tables stay exhaustive under `when` where a discriminator means casting at every read.
 *
 * Each mirrors `observation_symptoms`: the composite primary key makes a double-tick impossible and
 * gives the foreign key its index for free. Neither needs a second index, because neither value is
 * ever looked up on its own.
 */

@Entity(
    tableName = "observation_droppings_appearance",
    primaryKeys = ["observationId", "value"],
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ObservationDroppingsAppearanceEntity(
    val observationId: String,
    val value: DroppingsAppearance,
)

@Entity(
    tableName = "observation_droppings_sizes",
    primaryKeys = ["observationId", "value"],
    foreignKeys = [
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ObservationDroppingsSizeEntity(
    val observationId: String,
    val value: DroppingsSize,
)
