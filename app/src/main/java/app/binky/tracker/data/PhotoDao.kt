package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A bunny's photos. Reads return [Flow], writes are `suspend` (house rule).
 *
 * There is no `update` for the whole row on purpose: a caption is the only thing about a photo an
 * owner can change, and [setCaption] says so. A blanket `@Update` would let a future caller rewrite
 * [PhotoEntity.path] — pointing a row at a different file than the one it was written for, which is
 * the one thing ADR-0020's file-first order cannot protect against.
 */
@Dao
interface PhotoDao {
    /**
     * Newest first, by when the picture was *taken* where that is known.
     *
     * `createdAt` breaks ties, because a bulk import stamps a whole camera roll with the same
     * millisecond and SQLite is free to return equal rows in any order it likes.
     */
    @Query(
        "SELECT * FROM photos WHERE bunnyId = :bunnyId " +
            "ORDER BY COALESCE(capturedAt, createdAt) DESC, createdAt DESC, id",
    )
    fun photosOf(bunnyId: String): Flow<List<PhotoEntity>>

    /** One-shot read, for the write paths that need the row before deciding. */
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photoNow(id: String): PhotoEntity?

    /** The files a cascade would leave behind — read before the delete, deleted after it commits. */
    @Query("SELECT path FROM photos WHERE bunnyId = :bunnyId")
    suspend fun pathsOf(bunnyId: String): List<String>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Query("UPDATE photos SET caption = :caption WHERE id = :id")
    suspend fun setCaption(
        id: String,
        caption: String?,
    )

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)
}
