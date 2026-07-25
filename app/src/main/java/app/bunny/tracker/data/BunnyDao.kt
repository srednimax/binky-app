package app.bunny.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * What deleting a bunny would destroy, in the two buckets ADR-0004 requires: records **solely**
 * this bunny's, which are destroyed, versus **shared** observations it merely took part in, which
 * survive for the other bunnies. Lumping them together would overstate the loss and hide a side
 * effect on a different bunny.
 *
 * An avatar and the profile fields deliberately do not count — see [deleteConfirmationFor].
 */
data class RecordCounts(
    val soleOwnedRecords: Int,
    val sharedRecords: Int,
)

/**
 * Reads return [Flow] and writes are `suspend`; screens collect, nothing hand-rolls a refresh.
 *
 * Kotlin note: a `Flow` is a **cold** stream — nothing runs until something collects it, unlike a
 * Promise, which is already in flight the moment you hold it. Room re-runs the query and re-emits
 * whenever the underlying table changes, so a screen that collects one is always current.
 */
@Dao
interface BunnyDao {
    @Query("SELECT * FROM bunnies WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE, createdAt")
    fun activeBunnies(): Flow<List<BunnyEntity>>

    @Query("SELECT * FROM bunnies WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun archivedBunnies(): Flow<List<BunnyEntity>>

    @Query("SELECT * FROM bunnies WHERE id = :id")
    fun bunny(id: String): Flow<BunnyEntity?>

    /** One-shot read, for the write paths that need the current row before deciding. */
    @Query("SELECT * FROM bunnies WHERE id = :id")
    suspend fun bunnyNow(id: String): BunnyEntity?

    @Query("SELECT * FROM bunnies WHERE fluffleId = :fluffleId ORDER BY name COLLATE NOCASE")
    fun membersOf(fluffleId: String): Flow<List<BunnyEntity>>

    /**
     * Counts archived members too — the dissolve predicate depends on it (ADR-0008): deleting one
     * bunny from a trio whose third member is archived must leave the fluffle standing.
     */
    @Query("SELECT * FROM bunnies WHERE fluffleId = :fluffleId")
    suspend fun membersOfNow(fluffleId: String): List<BunnyEntity>

    /**
     * What deleting this bunny would destroy. Null when the bunny no longer exists.
     *
     * Weighings are **sole-owned** — a weight belongs to exactly one bunny and cascades with it —
     * so this is the first bucket's first real contributor, and it is what makes 1d's structurally
     * built two-stage ceremony reachable for the first time. The shared bucket stays zero until
     * observations land in 2e, where this reaches its final form: bucketed by **survivorship, not
     * provenance** (ADR-0004).
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM weights WHERE bunnyId = :bunnyId) AS soleOwnedRecords,
            0 AS sharedRecords
        FROM bunnies WHERE id = :bunnyId
        """,
    )
    suspend fun recordCounts(bunnyId: String): RecordCounts?

    @Insert
    suspend fun insert(bunny: BunnyEntity)

    @Update
    suspend fun update(bunny: BunnyEntity)

    @Query("DELETE FROM bunnies WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE bunnies SET fluffleId = :fluffleId WHERE id = :id")
    suspend fun setFluffleId(
        id: String,
        fluffleId: String?,
    )

    @Query("UPDATE bunnies SET fluffleId = NULL WHERE fluffleId = :fluffleId")
    suspend fun clearFluffle(fluffleId: String)

    @Query("UPDATE bunnies SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun setArchivedAt(
        id: String,
        archivedAt: Instant?,
    )
}
