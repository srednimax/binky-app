package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The vet directory. App-wide, so nothing here takes a `bunnyId` (ADR-0017).
 *
 * Kotlin note: `Flow` reads are cold streams Room re-emits on every write to the tables they touch —
 * the screen collects one and never asks for a refresh (house rule). The `…Now` suffix marks the
 * `suspend` one-shot variants, used where a single value is needed inside a write path rather than
 * observed.
 */
@Dao
interface VetDao {
    @Query("SELECT * FROM vets ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<VetEntity>>

    @Query("SELECT * FROM vets WHERE id = :id")
    fun byId(id: String): Flow<VetEntity?>

    @Query("SELECT * FROM vets WHERE id = :id")
    suspend fun byIdNow(id: String): VetEntity?

    @Insert
    suspend fun insert(vet: VetEntity)

    @Update
    suspend fun update(vet: VetEntity)

    /** The visits keep their rows and lose only the name — `SET NULL`, not `CASCADE`. */
    @Query("DELETE FROM vets WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** One bunny's vet visits. */
@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE bunnyId = :bunnyId ORDER BY visitedOn DESC, createdAt DESC")
    fun forBunny(bunnyId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE id = :id")
    fun byId(id: String): Flow<VisitEntity?>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun byIdNow(id: String): VisitEntity?

    @Insert
    suspend fun insert(visit: VisitEntity)

    @Update
    suspend fun update(visit: VisitEntity)

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun deleteById(id: String)
}
