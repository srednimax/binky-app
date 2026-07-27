package app.binky.tracker.data

import android.net.Uri
import app.binky.tracker.media.MediaFiles
import app.binky.tracker.media.MediaKind
import kotlinx.coroutines.flow.Flow

/**
 * A bunny's photo gallery. Thin over the DAO, and the owner of the half a DAO cannot own: the file
 * beside every row.
 *
 * **Both directions are ordered against a crash** (ADR-0020). Adding writes the file first and the
 * row second, so an interrupted add leaves an invisible orphan rather than a row whose photo is
 * missing. Deleting is the mirror — row first, then the file, best-effort — so an interrupted
 * delete leaves the same harmless orphan instead of a gallery tile that can never load.
 */
class PhotoRepository(
    private val database: BunnyDatabase,
    private val media: MediaFiles,
) {
    private val photoDao = database.photoDao()

    fun photos(bunnyId: String): Flow<List<PhotoEntity>> = photoDao.photosOf(bunnyId)

    /**
     * Downsamples [source] through the media helper, then records it. Returns the new row's id.
     *
     * Throws whatever [MediaFiles.persist] throws — an unreadable file is the caller's to report,
     * because only the caller knows whether it is one photo or the third of twenty (the import
     * skips it, counts it, and says so once at the end).
     */
    suspend fun add(
        bunnyId: String,
        source: Uri,
    ): String {
        val stored = media.persist(source, MediaKind.Photo)
        val photo = PhotoEntity(bunnyId = bunnyId, path = stored.path, capturedAt = stored.capturedAt)
        photoDao.insert(photo)
        return photo.id
    }

    /** Blank is stored as no caption at all, so an emptied field does not read back as an empty one. */
    suspend fun setCaption(
        id: String,
        caption: String,
    ) = photoDao.setCaption(id, caption.trim().ifEmpty { null })

    /**
     * Removes the row, then the file. A file that will not delete is not an error worth surfacing:
     * the photo is already gone from the gallery, which is what the owner asked for.
     */
    suspend fun delete(id: String) {
        val photo = photoDao.photoNow(id) ?: return
        photoDao.deleteById(id)
        media.delete(photo.path)
    }
}
