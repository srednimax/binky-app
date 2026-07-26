package app.bunny.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
            droppingsSize = :droppingsSize,
            droppingsForm = :droppingsForm,
            cecotropes = :cecotropes
        WHERE groupId = :groupId
        """,
    )
    suspend fun updateTrayForGroup(
        groupId: String,
        droppingsAmount: DroppingsAmount?,
        droppingsSize: DroppingsSize?,
        droppingsForm: DroppingsForm?,
        cecotropes: Cecotropes?,
    )

    /** The solo case of [updateTrayForGroup] — one row, because there is no group. */
    @Query(
        """
        UPDATE observations SET
            droppingsAmount = :droppingsAmount,
            droppingsSize = :droppingsSize,
            droppingsForm = :droppingsForm,
            cecotropes = :cecotropes
        WHERE id = :id
        """,
    )
    suspend fun updateTrayForObservation(
        id: String,
        droppingsAmount: DroppingsAmount?,
        droppingsSize: DroppingsSize?,
        droppingsForm: DroppingsForm?,
        cecotropes: Cecotropes?,
    )

    @Query("SELECT symptomId FROM observation_symptoms WHERE observationId = :observationId")
    suspend fun symptomIdsNow(observationId: String): List<String>

    /**
     * `IGNORE` rather than `REPLACE`: the composite primary key already makes a symptom ticked twice
     * impossible, so a re-tick is a no-op rather than an error to handle at every call site.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkSymptoms(links: List<ObservationSymptomEntity>)

    @Query("DELETE FROM observation_symptoms WHERE observationId = :observationId")
    suspend fun clearSymptoms(observationId: String)
}
