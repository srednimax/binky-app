package app.binky.tracker.data

import android.net.Uri
import androidx.room.withTransaction
import app.binky.tracker.media.MediaFiles
import app.binky.tracker.media.MediaKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A bunny's paperwork, and the files behind it.
 *
 * **Both directions are ordered against a crash** (ADR-0020), exactly as [PhotoRepository] is —
 * every page image is written before any row that points at it, and a delete takes the rows first
 * and the files after. An interrupted scan leaves invisible orphan files; the other order would
 * leave a document whose pages render as placeholders, which to the owner is a lost vaccination
 * record.
 *
 * **Every image goes through [MediaFiles.persist] with [MediaKind.Document]** (house rule). That is
 * what puts scans in `documents/` — their own export scope (ADR-0005) — and downsamples them at the
 * document spec rather than the gallery's, which would make small print unreadable.
 */
class DocumentRepository(
    private val database: BunnyDatabase,
    private val media: MediaFiles,
) {
    private val documentDao = database.documentDao()
    private val pageDao = database.documentPageDao()

    fun documents(bunnyId: String): Flow<List<DocumentSummary>> = documentDao.forBunny(bunnyId)

    fun documentsOfVisit(visitId: String): Flow<List<DocumentSummary>> = documentDao.forVisit(visitId)

    /** What a visit's attach picker may offer — see [DocumentDao.unattachedForBunny]. */
    fun unattached(bunnyId: String): Flow<List<DocumentSummary>> = documentDao.unattachedForBunny(bunnyId)

    fun document(id: String): Flow<DocumentEntity?> = documentDao.byId(id)

    fun pages(documentId: String): Flow<List<DocumentPageEntity>> = pageDao.pagesOf(documentId)

    suspend fun documentNow(id: String): DocumentEntity? = documentDao.byIdNow(id)

    /**
     * Records one scan: every [pages] image through the media helper, then the document and its
     * page rows in a single transaction.
     *
     * **Files first, rows second, and the rows together** — a half-inserted document would be one
     * with pages missing from the middle, which is worse than none. Returns the new document's id.
     *
     * **[capturedAt] is not taken from the images and never will be.** The pipeline reads a capture
     * instant off every source and it is the right answer for a photo; here it is the moment the
     * *scan* was taken, and this column is the date printed on the page (see [DocumentEntity]).
     * Filling one from the other would date a two-year-old vaccination card to this afternoon.
     *
     * Throws whatever [MediaFiles.persist] throws, and writes no rows when it does — the caller is
     * the only one who knows whether that is one page or the third of five.
     */
    suspend fun add(
        bunnyId: String,
        title: String,
        pages: List<Uri>,
        visitId: String? = null,
        capturedAt: Instant? = null,
    ): String {
        require(pages.isNotEmpty()) { "A document needs at least one page" }
        val cleaned = title.trim()
        // The default title is the screen's to supply, not this file's: it is written into the
        // database, and a constant here would have to be English while the rest of the app is not
        // (ADR-0013). What the owner saw when they named it is what gets stored.
        require(cleaned.isNotEmpty()) { "A document needs a title" }
        val stored = pages.map { media.persist(it, MediaKind.Document) }
        val document =
            DocumentEntity(
                bunnyId = bunnyId,
                visitId = visitId,
                title = cleaned,
                capturedAt = capturedAt,
            )
        database.withTransaction {
            documentDao.insert(document)
            stored.forEachIndexed { index, page ->
                pageDao.insert(DocumentPageEntity(documentId = document.id, path = page.path, position = index))
            }
        }
        return document.id
    }

    /**
     * Appends pages to a document that already exists — a second sheet found in the same envelope.
     *
     * Positions continue from the last one rather than restarting, so an append never lands in the
     * middle of the order the owner arranged.
     */
    suspend fun addPages(
        documentId: String,
        pages: List<Uri>,
    ) {
        if (pages.isEmpty()) return
        val stored = pages.map { media.persist(it, MediaKind.Document) }
        database.withTransaction {
            var position = pageDao.lastPosition(documentId)
            stored.forEach { page ->
                position += 1
                pageDao.insert(DocumentPageEntity(documentId = documentId, path = page.path, position = position))
            }
        }
    }

    /** Blank is refused rather than stored: a document with no title is a row nobody can find again. */
    suspend fun setTitle(
        id: String,
        title: String,
    ) {
        val cleaned = title.trim()
        if (cleaned.isEmpty()) return
        documentDao.setTitle(id, cleaned)
    }

    /** The date printed on the page. Null is the honest answer when the owner does not know it. */
    suspend fun setCapturedAt(
        id: String,
        capturedAt: Instant?,
    ) = documentDao.setCapturedAt(id, capturedAt)

    /**
     * Attaches to a visit, or detaches with null.
     *
     * **Detaching leaves the document with its bunny.** The paperwork is the health record and the
     * visit is where it came from — the same survival rule a visit gets from its vet (ADR-0017).
     */
    suspend fun attachToVisit(
        id: String,
        visitId: String?,
    ) = documentDao.setVisitId(id, visitId)

    /**
     * Swaps a page with its neighbour in [direction] — `-1` earlier, `+1` later.
     *
     * A swap rather than a renumber, because positions may legitimately have gaps after a delete
     * and rewriting the whole list to close them would touch every row to move one.
     */
    suspend fun movePage(
        pageId: String,
        direction: Int,
    ) {
        database.withTransaction {
            val page = pageDao.pageNow(pageId) ?: return@withTransaction
            val ordered = pageDao.pagesOfNow(page.documentId)
            val index = ordered.indexOfFirst { it.id == pageId }
            val neighbour = ordered.getOrNull(index + direction) ?: return@withTransaction
            pageDao.setPosition(page.id, neighbour.position)
            pageDao.setPosition(neighbour.id, page.position)
        }
    }

    /**
     * Removes one page: the row, then the file.
     *
     * **The last page does not delete the document.** A document with no pages still carries its
     * title, its date and the visit it belongs to, and silently destroying all of that because the
     * owner deleted a bad scan would be a delete they never asked for — the empty state offers
     * *add a page* and *delete the document* side by side instead.
     */
    suspend fun deletePage(pageId: String) {
        val page = pageDao.pageNow(pageId) ?: return
        pageDao.deleteById(pageId)
        media.delete(page.path)
    }

    /**
     * Removes a document and every page file behind it.
     *
     * Paths are read **inside** the transaction, because the cascade is about to take the rows and
     * after that there is nothing left to ask where the files were; the files go **after** it
     * commits, so a rolled-back delete cannot leave live rows pointing at deleted images. That is
     * `BunnyRepository.delete`'s ordering, for the same reason.
     */
    suspend fun delete(id: String) {
        var paths: List<String> = emptyList()
        database.withTransaction {
            paths = pageDao.pathsOfDocument(id)
            documentDao.deleteById(id)
        }
        paths.forEach(media::delete)
    }
}
