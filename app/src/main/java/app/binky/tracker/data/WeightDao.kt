package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The weight series and the trend acknowledgment watermark.
 *
 * Every read here is ordered by ADR-0021's **stated total order** — `recordedAt` desc, `createdAt`
 * desc, `id` — so the chart, the history list and the trend math all see the same sequence.
 *
 * There is deliberately **no "the N weighings prior to an instant" query.** Windowing belongs to the
 * pure function in `WeightTrend.kt` and is defined in exactly one place; if a query had already
 * chosen the three priors, the heaviest tests in the project — the back-dating cases especially —
 * would be measuring a stub (ADR-0021). Loading the whole series costs nothing: five years of
 * weekly weighings is 260 rows.
 */
@Dao
interface WeightDao {
    @Query(
        "SELECT * FROM weights WHERE bunnyId = :bunnyId " +
            "ORDER BY recordedAt DESC, createdAt DESC, id",
    )
    fun series(bunnyId: String): Flow<List<WeightEntity>>

    /** One-shot read, for the write paths that must re-evaluate the trigger before committing. */
    @Query(
        "SELECT * FROM weights WHERE bunnyId = :bunnyId " +
            "ORDER BY recordedAt DESC, createdAt DESC, id",
    )
    suspend fun seriesNow(bunnyId: String): List<WeightEntity>

    @Query("SELECT * FROM weights WHERE id = :id")
    suspend fun weightNow(id: String): WeightEntity?

    /**
     * Weighings this bunny already has at **exactly** this instant.
     *
     * The entry form's collision prompt (ADR-0021): re-typing `2500` over a fat-fingered `250` at
     * the same minute would otherwise add a second row that *displaces* a real prior out of the
     * three-wide baseline window. Deliberately **not** a `UNIQUE(bunnyId, recordedAt)` constraint —
     * that would reject legitimate double-weighings and the seeder, and the total order exists
     * precisely to handle ties the owner chose to keep. Exact match only; a fuzzy window would need
     * its own tuning constant and would nag on a genuine re-weigh.
     */
    @Query("SELECT * FROM weights WHERE bunnyId = :bunnyId AND recordedAt = :recordedAt")
    suspend fun weightsAt(
        bunnyId: String,
        recordedAt: Instant,
    ): List<WeightEntity>

    @Insert
    suspend fun insert(weight: WeightEntity)

    @Update
    suspend fun update(weight: WeightEntity)

    @Query("DELETE FROM weights WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM trend_acknowledgments WHERE bunnyId = :bunnyId")
    fun acknowledgment(bunnyId: String): Flow<TrendAcknowledgmentEntity?>

    /**
     * At most one row per bunny — `bunnyId` is the primary key — so acknowledging a second time
     * replaces the watermark rather than failing on the constraint.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAcknowledgment(acknowledgment: TrendAcknowledgmentEntity)

    @Query("DELETE FROM trend_acknowledgments WHERE bunnyId = :bunnyId")
    suspend fun deleteAcknowledgment(bunnyId: String)
}
