package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Embedded
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

/**
 * A visit as every screen needs it: the row, the vet's name, and the weighing recorded at it.
 *
 * A projection rather than three reads, because the two extras are exactly what makes a visit row
 * readable and neither is on the visit itself. **At most one weighing can appear**, and that is the
 * unique index on `weights.visitId` doing it (ADR-0017) rather than a `LIMIT` here hiding a second.
 *
 * Kotlin note: `@Embedded` flattens the entity into the same result row, so `visits.*` fills
 * [visit] while the aliased columns fill the rest — the SQL equivalent of destructuring a joined row
 * into a nested object.
 */
data class VisitDetails(
    @Embedded val visit: VisitEntity,
    /** Null when the visit names no vet, **and** when the vet it named has since been deleted. */
    val vetName: String?,
    val weightId: String?,
    val weightGrams: Int?,
)

/** One bunny's vet visits. */
@Dao
interface VisitDao {
    @Query("SELECT * FROM visits WHERE bunnyId = :bunnyId ORDER BY visitedOn DESC, createdAt DESC")
    fun forBunny(bunnyId: String): Flow<List<VisitEntity>>

    /**
     * The same list, joined. Room re-emits it on a write to **any** of the three tables, so renaming
     * a vet or correcting the weighing on the Weight screen moves this list with nothing telling it
     * to (house rule: DAOs return `Flow`).
     */
    @Query(
        "SELECT visits.*, vets.name AS vetName, weights.id AS weightId, weights.grams AS weightGrams " +
            "FROM visits " +
            "LEFT JOIN vets ON vets.id = visits.vetId " +
            "LEFT JOIN weights ON weights.visitId = visits.id " +
            "WHERE visits.bunnyId = :bunnyId " +
            "ORDER BY visits.visitedOn DESC, visits.createdAt DESC",
    )
    fun detailsForBunny(bunnyId: String): Flow<List<VisitDetails>>

    @Query(
        "SELECT visits.*, vets.name AS vetName, weights.id AS weightId, weights.grams AS weightGrams " +
            "FROM visits " +
            "LEFT JOIN vets ON vets.id = visits.vetId " +
            "LEFT JOIN weights ON weights.visitId = visits.id " +
            "WHERE visits.id = :id",
    )
    fun details(id: String): Flow<VisitDetails?>

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
