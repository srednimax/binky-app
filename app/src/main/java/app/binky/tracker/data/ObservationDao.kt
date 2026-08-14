package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Observations and their symptom links.
 *
 * Reads are ordered newest first by the same total order the weight series uses — `recordedAt` desc,
 * `createdAt` desc, `id` — so a day holding several observations reads the same way everywhere rather
 * than depending silently on SQLite's row order.
 *
 * The group-wide statements below are the tray/individual split expressed in SQL: [updateTrayForGroup]
 * moves every participant's row at once, and there is deliberately **no** group-wide statement for the
 * individual fields, because there must not be one (ADR-0008).
 */
@Dao
interface ObservationDao {
    @Query(
        "SELECT * FROM observations WHERE bunnyId = :bunnyId " +
            "ORDER BY recordedAt DESC, createdAt DESC, id",
    )
    fun forBunny(bunnyId: String): Flow<List<ObservationEntity>>

    /**
     * The combined timeline under "All bunnies", which means the **active** ones (ADR-0015) — an
     * archived bunny is reached only through its own read-only scope (ADR-0004).
     *
     * Rows sharing a `groupId` come back separately and are collapsed into one entry for display,
     * as a pure function with its own test (ADR-0008). Collapsing in SQL would hide the join the
     * display rule is about.
     */
    @Query(
        "SELECT o.* FROM observations o JOIN bunnies b ON b.id = o.bunnyId " +
            "WHERE b.archivedAt IS NULL ORDER BY o.recordedAt DESC, o.createdAt DESC, o.id",
    )
    fun forActiveBunnies(): Flow<List<ObservationEntity>>

    /**
     * One bunny's timeline, **plus the other rows of any observation it took part in**.
     *
     * [forBunny] answers "what is recorded against this bunny", which is what every warning and count
     * wants. A timeline wants something wider: a shared entry has to be able to *name who it covered*
     * (ADR-0008), and this bunny's row alone cannot say. So the group is pulled back whole and the
     * display function collapses it.
     *
     * Archived housemates are included here and excluded from [forActiveBunnies], and the difference
     * is deliberate: this is one bunny's history, where "observed together with Hazel, since archived"
     * is the true answer, while that one is the *current* fluffle's combined feed.
     */
    @Query(
        """
        SELECT * FROM observations
        WHERE bunnyId = :bunnyId
           OR groupId IN (
               SELECT groupId FROM observations WHERE bunnyId = :bunnyId AND groupId IS NOT NULL
           )
        ORDER BY recordedAt DESC, createdAt DESC, id
        """,
    )
    fun timelineForBunny(bunnyId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE id = :id")
    suspend fun observationNow(id: String): ObservationEntity?

    /** Every row of a shared observation, including this bunny's. Unordered — callers filter by bunny. */
    @Query("SELECT * FROM observations WHERE groupId = :groupId")
    suspend fun groupNow(groupId: String): List<ObservationEntity>

    @Insert
    suspend fun insert(observation: ObservationEntity)

    @Update
    suspend fun update(observation: ObservationEntity)

    @Query("DELETE FROM observations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM observations WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)

    /**
     * Mints or clears the group id on one row — both directions of ADR-0008's conversion: back-filling
     * a new group id onto a solo observation, and clearing it from the survivor when a correction drops
     * the group to one.
     */
    @Query("UPDATE observations SET groupId = :groupId WHERE id = :id")
    suspend fun setGroupId(
        id: String,
        groupId: String?,
    )

    /**
     * Editing a tray-level fact is one statement across the whole group (ADR-0008). One tray, one
     * real-world fact: an update that touched only the row on screen would let bunny A's droppings read
     * "few" while bunny B's read "normal" for the same tray, which is the false attribution this model
     * exists to prevent, reintroduced through editing rather than tapping.
     */
    @Query(
        """
        UPDATE observations SET
            droppingsAmount = :droppingsAmount,
            cecotropes = :cecotropes,
            trayPhotoPath = :trayPhotoPath
        WHERE groupId = :groupId
        """,
    )
    suspend fun updateTrayForGroup(
        groupId: String,
        droppingsAmount: DroppingsAmount?,
        cecotropes: Cecotropes?,
        trayPhotoPath: String?,
    )

    /**
     * When it was noticed is a **group-wide** fact for the same reason the tray is: one real-world
     * moment, however many bunnies were in the room. Correcting it on one row and not the others
     * would split one observation into two at different times.
     */
    @Query("UPDATE observations SET recordedAt = :recordedAt WHERE groupId = :groupId")
    suspend fun setRecordedAtForGroup(
        groupId: String,
        recordedAt: Instant,
    )

    @Query("UPDATE observations SET recordedAt = :recordedAt WHERE id = :id")
    suspend fun setRecordedAtForObservation(
        id: String,
        recordedAt: Instant,
    )

    /** The solo case of [updateTrayForGroup] — one row, because there is no group. */
    @Query(
        """
        UPDATE observations SET
            droppingsAmount = :droppingsAmount,
            cecotropes = :cecotropes,
            trayPhotoPath = :trayPhotoPath
        WHERE id = :id
        """,
    )
    suspend fun updateTrayForObservation(
        id: String,
        droppingsAmount: DroppingsAmount?,
        cecotropes: Cecotropes?,
        trayPhotoPath: String?,
    )

    /**
     * How many rows still point at a tray photo — the whole of ADR-0029's one new rule.
     *
     * The path is duplicated onto every participant, so deleting one bonded bunny cascades a row that
     * still references the survivor's file. [ObservationRepository] deletes the rows first and asks
     * this afterwards, so a non-zero answer means somebody else is still using the file.
     */
    @Query("SELECT COUNT(*) FROM observations WHERE trayPhotoPath = :path")
    suspend fun countWithTrayPhoto(path: String): Int

    /**
     * The tray photos this bunny's observations point at, read **before** a delete takes the rows —
     * the same "ask while the rows still exist" step `BunnyRepository.delete` already makes for
     * avatars, photos and document pages. `DISTINCT` because a bonded pair's two rows carry one path.
     */
    @Query("SELECT DISTINCT trayPhotoPath FROM observations WHERE bunnyId = :bunnyId AND trayPhotoPath IS NOT NULL")
    suspend fun trayPhotoPathsOf(bunnyId: String): List<String>

    @Query("SELECT symptomId FROM observation_symptoms WHERE observationId = :observationId")
    suspend fun symptomIdsNow(observationId: String): List<String>

    /**
     * Every tick in the database, for the timeline to index by observation.
     *
     * The whole table rather than a per-entry query, because the timeline renders many observations
     * at once and one `Flow` per visible row would be a subscription storm on scrolling. The table is
     * one short row per symptom noticed — daily observations for five years put it in the low
     * thousands — so reading it whole is cheaper than the alternative, not a shortcut past it.
     */
    @Query("SELECT * FROM observation_symptoms")
    fun allSymptomLinks(): Flow<List<ObservationSymptomEntity>>

    /**
     * `IGNORE` rather than `REPLACE`: the composite primary key already makes a symptom ticked twice
     * impossible, so a re-tick is a no-op rather than an error to handle at every call site.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkSymptoms(links: List<ObservationSymptomEntity>)

    @Query("DELETE FROM observation_symptoms WHERE observationId = :observationId")
    suspend fun clearSymptoms(observationId: String)

    /*
     * The two multi-valued droppings fields (ADR-0029).
     *
     * **Reads come back as stored names, never as enums**, and that is the point rather than an
     * oversight. A join row is either there or it is not, so a value written by a later build has an
     * honest reading this app can act on — *drop it* — where a non-null converter would have to
     * invent a member instead. [ObservationRepository] does the mapping, in one place, and the
     * converters exist only because Room requires both directions on an entity field.
     *
     * The whole-table `Flow`s are the same trade `allSymptomLinks` makes: the timeline renders many
     * observations at once, and one subscription per visible row would be a storm on scrolling.
     */

    @Query("SELECT observationId, value FROM observation_droppings_appearance")
    fun allDroppingsAppearance(): Flow<List<DroppingsValueLink>>

    @Query("SELECT observationId, value FROM observation_droppings_sizes")
    fun allDroppingsSizes(): Flow<List<DroppingsValueLink>>

    @Query("SELECT value FROM observation_droppings_appearance WHERE observationId = :observationId")
    suspend fun droppingsAppearanceNamesNow(observationId: String): List<String>

    @Query("SELECT value FROM observation_droppings_sizes WHERE observationId = :observationId")
    suspend fun droppingsSizeNamesNow(observationId: String): List<String>

    /** `IGNORE` for the same reason [linkSymptoms] takes it: the composite key already forbids a double-tick. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkDroppingsAppearance(links: List<ObservationDroppingsAppearanceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkDroppingsSizes(links: List<ObservationDroppingsSizeEntity>)

    @Query("DELETE FROM observation_droppings_appearance WHERE observationId = :observationId")
    suspend fun clearDroppingsAppearance(observationId: String)

    @Query("DELETE FROM observation_droppings_sizes WHERE observationId = :observationId")
    suspend fun clearDroppingsSizes(observationId: String)
}

/**
 * One join row as stored — the value as its **name**, so a name this build does not know can be
 * dropped rather than guessed. See the comment above [ObservationDao.allDroppingsAppearance].
 *
 * One type for both tables, because both are `(observationId, value)` and a second identical class
 * would only be a chance for the two to drift.
 */
data class DroppingsValueLink(
    val observationId: String,
    val value: String,
)
