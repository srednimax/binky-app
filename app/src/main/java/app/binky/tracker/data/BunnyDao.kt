package app.binky.tracker.data

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
     * Every breed any bunny carries — the picker's "add your own" half, as a **query rather than a
     * table**.
     *
     * ADR-0010's reason a vocabulary earns a table is that a "how often has this happened?" count
     * must key off a stable id. Breed is asked no such question: it is a profile fact on Home's card,
     * counted by nothing. So it stays a text column and the suggestions are this.
     *
     * **Archived bunnies included**, deliberately — a breed the owner typed once should still be
     * offered after that bunny is archived. The accepted cost is the other direction: a breed drops
     * out of the suggestions once no bunny at all carries it. The reuse that matters — a second bunny
     * of the same breed — still works, because the first one is still carrying the string.
     */
    @Query(
        "SELECT DISTINCT breed FROM bunnies " +
            "WHERE breed IS NOT NULL AND TRIM(breed) <> '' " +
            "ORDER BY breed COLLATE NOCASE",
    )
    fun breeds(): Flow<List<String>>

    /**
     * Counts archived members too — the dissolve predicate depends on it (ADR-0008): deleting one
     * bunny from a trio whose third member is archived must leave the fluffle standing.
     */
    @Query("SELECT * FROM bunnies WHERE fluffleId = :fluffleId")
    suspend fun membersOfNow(fluffleId: String): List<BunnyEntity>

    /**
     * What deleting this bunny would destroy. Null when the bunny no longer exists.
     *
     * Bucketed by **survivorship, not provenance** (ADR-0004) — the question the confirmation asks is
     * "what is lost?", not "where did this come from?". So:
     *
     * - Weighings are always sole-owned: a weight belongs to exactly one bunny and cascades with it.
     * - Photos likewise: a photo is of one bunny, and deleting the bunny destroys it — the row by
     *   cascade and the file by [BunnyRepository.delete], which is the only way a file goes.
     * - Care reminders and their completions the same, in two hops: a reminder cascades from the
     *   bunny and its events cascade from the reminder, so both land in the destroyed bucket. The
     *   events are counted separately rather than folded into the reminder, because a weigh-in with
     *   two years of history loses more than the one row the reminder is.
     * - Visits, medication courses, their doses and documents likewise — all sole-owned, all
     *   destroyed. Doses reach the bunny through their course, the same two-hop shape as care events,
     *   and are counted separately for the same reason: a fortnight of a sick rabbit's antibiotics is
     *   the most clinically meaningful history this app holds after the weights.
     * - **Vets are not counted, and that is the one exception here.** The directory is app-wide, a
     *   vet outlives its visits by `SET NULL` (ADR-0017), and a bunny's deletion must not read as
     *   taking the clinic's phone number with it — because it does not.
     * - Medication times and document pages are not counted either: they are parts of a row already
     *   counted, not records of their own. "3 documents" is what the owner recognises; "3 documents
     *   and 7 pages" is the same loss, stated twice.
     *
     * The four new counts are honestly zero until 5d and 5g fill their tables, which is why they can
     * land here with the schema rather than waiting for the screens.
     * - A grouped observation counts as **shared** only while at least one row belongs to a *different*
     *   bunny, because those rows survive the delete.
     * - A grouped observation where this bunny is the **last participant** is destroyed by the delete,
     *   so it counts as sole-owned. Calling it "shared" would promise a survivor that does not exist.
     *
     * The `EXISTS` deliberately does **not** filter on `archivedAt`: an archived housemate is a
     * survivor, and its copy of the observation stays readable in its own scope (ADR-0004).
     *
     * A solo observation has a `NULL` groupId, which no equality can match, so `NOT EXISTS` puts it in
     * the sole-owned bucket without needing a branch of its own.
     *
     * [deleteConfirmationFor] is untouched by this: either bucket being non-zero still yields
     * `TWO_STAGE`, so only the numbers get honest.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM weights WHERE bunnyId = :bunnyId)
            + (SELECT COUNT(*) FROM photos WHERE bunnyId = :bunnyId)
            + (SELECT COUNT(*) FROM care_reminders WHERE bunnyId = :bunnyId)
            + (
                SELECT COUNT(*) FROM care_events e
                JOIN care_reminders r ON r.id = e.reminderId
                WHERE r.bunnyId = :bunnyId
            )
            + (SELECT COUNT(*) FROM visits WHERE bunnyId = :bunnyId)
            + (SELECT COUNT(*) FROM medication_courses WHERE bunnyId = :bunnyId)
            + (
                SELECT COUNT(*) FROM doses d
                JOIN medication_courses c ON c.id = d.courseId
                WHERE c.bunnyId = :bunnyId
            )
            + (SELECT COUNT(*) FROM documents WHERE bunnyId = :bunnyId)
            + (
                SELECT COUNT(*) FROM observations o
                WHERE o.bunnyId = :bunnyId
                  AND NOT EXISTS (
                      SELECT 1 FROM observations other
                      WHERE other.groupId = o.groupId AND other.bunnyId <> :bunnyId
                  )
            ) AS soleOwnedRecords,
            (
                SELECT COUNT(*) FROM observations o
                WHERE o.bunnyId = :bunnyId
                  AND EXISTS (
                      SELECT 1 FROM observations other
                      WHERE other.groupId = o.groupId AND other.bunnyId <> :bunnyId
                  )
            ) AS sharedRecords
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
