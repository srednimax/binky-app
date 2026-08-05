package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A document as every list needs it: the row, how many pages it has, and the page to show a
 * thumbnail of.
 *
 * A projection rather than a second read per row, for the reason [VisitDetails] is one: the two
 * extras are what make the row readable and neither is on the document itself. Room re-emits the
 * `Flow` on a write to either table, so adding a page moves the count with nothing telling it to.
 */
data class DocumentSummary(
    @Embedded val document: DocumentEntity,
    val pageCount: Int,
    /** Relative, and null for a document whose pages have all been deleted. */
    val firstPagePath: String?,
)

/**
 * A bunny's paperwork. Reads return [Flow], writes are `suspend` (house rule).
 *
 * There is no blanket `@Update`, for the reason [PhotoDao] has none: the columns an owner can change
 * are the title, the date on the page and which visit it belongs to, and each says so by name. A
 * whole-row update would let a caller rewrite ids the pages hang off.
 */
@Dao
interface DocumentDao {
    /**
     * Newest first, by the date on the page where the owner has said what it is.
     *
     * `COALESCE(capturedAt, createdAt)` is [PhotoDao.photosOf]'s rule, and it means the same thing
     * here: a vaccination card dated two years ago sorts where it belongs rather than at the top
     * because it was scanned this afternoon. `createdAt` and `id` break ties, because a multi-page
     * import stamps every document with the same millisecond.
     */
    @Query(
        "SELECT documents.*, " +
            "(SELECT COUNT(*) FROM document_pages WHERE documentId = documents.id) AS pageCount, " +
            "(SELECT path FROM document_pages WHERE documentId = documents.id " +
            "  ORDER BY position, id LIMIT 1) AS firstPagePath " +
            "FROM documents WHERE bunnyId = :bunnyId " +
            "ORDER BY COALESCE(capturedAt, createdAt) DESC, createdAt DESC, id",
    )
    fun forBunny(bunnyId: String): Flow<List<DocumentSummary>>

    /** The paperwork a visit produced. Ordered as [forBunny] is, so one rule governs both lists. */
    @Query(
        "SELECT documents.*, " +
            "(SELECT COUNT(*) FROM document_pages WHERE documentId = documents.id) AS pageCount, " +
            "(SELECT path FROM document_pages WHERE documentId = documents.id " +
            "  ORDER BY position, id LIMIT 1) AS firstPagePath " +
            "FROM documents WHERE visitId = :visitId " +
            "ORDER BY COALESCE(capturedAt, createdAt) DESC, createdAt DESC, id",
    )
    fun forVisit(visitId: String): Flow<List<DocumentSummary>>

    /**
     * What the attach picker may offer: this bunny's documents that **no visit has claimed**.
     *
     * `visitId` is single-valued, so attaching a document already attached elsewhere would silently
     * detach it from the other visit. Offering only the free ones makes that impossible rather than
     * making it a surprise — a document that belongs to the wrong visit is detached there first.
     */
    @Query(
        "SELECT documents.*, " +
            "(SELECT COUNT(*) FROM document_pages WHERE documentId = documents.id) AS pageCount, " +
            "(SELECT path FROM document_pages WHERE documentId = documents.id " +
            "  ORDER BY position, id LIMIT 1) AS firstPagePath " +
            "FROM documents WHERE bunnyId = :bunnyId AND visitId IS NULL " +
            "ORDER BY COALESCE(capturedAt, createdAt) DESC, createdAt DESC, id",
    )
    fun unattachedForBunny(bunnyId: String): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun byId(id: String): Flow<DocumentEntity?>

    /** One-shot read, for the write paths that need the row before deciding. */
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byIdNow(id: String): DocumentEntity?

    @Insert
    suspend fun insert(document: DocumentEntity)

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun setTitle(
        id: String,
        title: String,
    )

    /** Null clears it: an owner who mistyped the date on the page can say they do not know it. */
    @Query("UPDATE documents SET capturedAt = :capturedAt WHERE id = :id")
    suspend fun setCapturedAt(
        id: String,
        capturedAt: Instant?,
    )

    /** Detaching passes null. The document keeps its bunny — that is what `SET NULL` means here. */
    @Query("UPDATE documents SET visitId = :visitId WHERE id = :id")
    suspend fun setVisitId(
        id: String,
        visitId: String?,
    )

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * The images a document is made of, in the order the owner put them in.
 *
 * Split from [DocumentDao] rather than folded into it because the two answer different questions —
 * one about paperwork, one about files — and because the delete path needs [pathsOfBunny] without
 * caring about a single document at all.
 */
@Dao
interface DocumentPageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY position, id")
    fun pagesOf(documentId: String): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY position, id")
    suspend fun pagesOfNow(documentId: String): List<DocumentPageEntity>

    @Query("SELECT * FROM document_pages WHERE id = :id")
    suspend fun pageNow(id: String): DocumentPageEntity?

    /**
     * **The files a bunny's cascade would leave behind** — read inside the transaction, before the
     * rows are taken, and deleted after it commits (ADR-0020, in reverse).
     *
     * These are the app's largest files. Missing this call orphans every scanned page of every
     * document with nothing left on disk pointing at them.
     */
    @Query(
        "SELECT p.path FROM document_pages p " +
            "JOIN documents d ON d.id = p.documentId " +
            "WHERE d.bunnyId = :bunnyId",
    )
    suspend fun pathsOfBunny(bunnyId: String): List<String>

    /** The same, for one document's own delete. */
    @Query("SELECT path FROM document_pages WHERE documentId = :documentId")
    suspend fun pathsOfDocument(documentId: String): List<String>

    /**
     * Where the next page goes. `-1` for a document with none, so the first page lands at 0.
     *
     * Kotlin note: Room maps a scalar `SELECT` straight to the return type, so `COALESCE` here is
     * what keeps this an `Int` rather than an `Int?` nobody would remember to handle.
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM document_pages WHERE documentId = :documentId")
    suspend fun lastPosition(documentId: String): Int

    @Insert
    suspend fun insert(page: DocumentPageEntity)

    @Query("UPDATE document_pages SET position = :position WHERE id = :id")
    suspend fun setPosition(
        id: String,
        position: Int,
    )

    @Query("DELETE FROM document_pages WHERE id = :id")
    suspend fun deleteById(id: String)
}
