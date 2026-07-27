package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The symptom vocabulary.
 *
 * **Nothing here is ordered.** A built-in stores no label — only a key that resolves through
 * `strings.xml` (ADR-0010, ADR-0013) — so there is no column to sort on that would put the list in
 * the owner's language. Ordering happens after resolution, in the picker.
 *
 * There is no delete: retiring a symptom sets [setHiddenAt] and historical observations go on
 * resolving it. `observation_symptoms` has no cascade from this side to enforce that.
 */
@Dao
interface SymptomDao {
    /** The picker's list: everything not retired, built-ins and owner-added alike, indistinguishable in use. */
    @Query("SELECT * FROM symptoms WHERE hiddenAt IS NULL")
    fun visible(): Flow<List<SymptomEntity>>

    /**
     * Hidden ones included, for the timeline: retiring a symptom must never blank it out of the
     * observations that recorded it (ADR-0010), and the timeline resolves ticks by id off this list.
     */
    @Query("SELECT * FROM symptoms")
    fun all(): Flow<List<SymptomEntity>>

    /** Hidden ones included — the add-time duplicate check needs them, since a match unhides (ADR-0010). */
    @Query("SELECT * FROM symptoms")
    suspend fun allNow(): List<SymptomEntity>

    @Query("SELECT * FROM symptoms WHERE id = :id")
    suspend fun symptomNow(id: String): SymptomEntity?

    /**
     * What was ticked on one observation, hidden symptoms included.
     *
     * The join is the point: retiring a symptom must not blank out the observations that recorded it,
     * so this deliberately does **not** filter on `hiddenAt`.
     */
    @Query(
        "SELECT s.* FROM symptoms s " +
            "JOIN observation_symptoms os ON os.symptomId = s.id " +
            "WHERE os.observationId = :observationId",
    )
    fun symptomsFor(observationId: String): Flow<List<SymptomEntity>>

    @Query(
        "SELECT s.* FROM symptoms s " +
            "JOIN observation_symptoms os ON os.symptomId = s.id " +
            "WHERE os.observationId = :observationId",
    )
    suspend fun symptomsForNow(observationId: String): List<SymptomEntity>

    @Insert
    suspend fun insert(symptom: SymptomEntity)

    /** Retires a symptom, or brings it back — `null` unhides. */
    @Query("UPDATE symptoms SET hiddenAt = :hiddenAt WHERE id = :id")
    suspend fun setHiddenAt(
        id: String,
        hiddenAt: Instant?,
    )
}
