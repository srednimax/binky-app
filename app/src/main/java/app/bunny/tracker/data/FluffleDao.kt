package app.bunny.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FluffleDao {
    @Insert
    suspend fun insert(fluffle: FluffleEntity)

    @Query("SELECT * FROM fluffles WHERE id = :id")
    fun fluffle(id: String): Flow<FluffleEntity?>

    @Query("SELECT * FROM fluffles WHERE id = :id")
    suspend fun fluffleNow(id: String): FluffleEntity?

    @Query("UPDATE fluffles SET name = :name WHERE id = :id")
    suspend fun setName(
        id: String,
        name: String?,
    )

    @Query("DELETE FROM fluffles WHERE id = :id")
    suspend fun deleteById(id: String)
}
