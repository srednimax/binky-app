package app.binky.tracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The gallery's data layer against a real database and a real [MediaFiles].
 *
 * Instrumented rather than JVM for the same reason `MediaFilesTest` is: every claim here is about a
 * row and the **file** beside it, and a mocked media helper that "wrote" nothing would let the
 * file-first ordering of ADR-0020 break silently.
 */
@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var database: BunnyDatabase
    private lateinit var media: MediaFiles
    private lateinit var photos: PhotoRepository
    private lateinit var bunnies: BunnyRepository

    @Before
    fun open() {
        database = inMemoryDatabase()
        media = temporaryMedia()
        photos = PhotoRepository(database, media)
        bunnies = BunnyRepository(database, FluffleRepository(database), temporaryPreferences(), media)
    }

    @After
    fun close() = database.close()

    @Test
    fun addingAPhotoWritesTheFileAndStoresARelativePath() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))

            val id = photos.add(bunnyId, jpeg())

            val photo = checkNotNull(database.photoDao().photoNow(id))
            assertTrue("expected photos/<uuid>.jpg but was ${photo.path}", photo.path.matches(PHOTO_PATH))
            assertTrue("${photo.path} was not written", media.resolve(photo.path).exists())
            assertEquals(listOf(id), photos.photos(bunnyId).first().map { it.id })
        }

    /**
     * The whole reason [PhotoEntity.capturedAt] exists. The oldest picture is added **last**, so
     * insertion order and capture order disagree: ordering by `createdAt` alone would put it first.
     */
    @Test
    fun theGalleryIsOrderedByWhenThePictureWasTaken() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Clover"))

            val lastYear = photos.add(bunnyId, jpeg(takenAt = "2024:01:02 08:00:00"))
            val undated = photos.add(bunnyId, jpeg())
            val thisSpring = photos.add(bunnyId, jpeg(takenAt = "2025:06:14 09:30:00"))

            assertEquals(
                "newest capture first, with the undated photo falling back to createdAt (today)",
                listOf(undated, thisSpring, lastYear),
                photos.photos(bunnyId).first().map { it.id },
            )
        }

    @Test
    fun editingACaptionLeavesTheFileAlone() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val id = photos.add(bunnyId, jpeg())
            val before = checkNotNull(database.photoDao().photoNow(id))

            photos.setCaption(id, "  first binky  ")

            val after = checkNotNull(database.photoDao().photoNow(id))
            assertEquals("first binky", after.caption)
            assertEquals("a caption edit must never repoint the row at another file", before.path, after.path)
            assertTrue(media.resolve(after.path).exists())
        }

    /** An emptied field is *no* caption, not an empty one — otherwise the pager renders a blank line. */
    @Test
    fun aBlankCaptionIsStoredAsNoCaptionAtAll() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Pepper"))
            val id = photos.add(bunnyId, jpeg())
            photos.setCaption(id, "typo")

            photos.setCaption(id, "   ")

            assertNull(checkNotNull(database.photoDao().photoNow(id)).caption)
        }

    @Test
    fun deletingAPhotoRemovesTheRowAndTheFile() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            val id = photos.add(bunnyId, jpeg())
            val file = media.resolve(checkNotNull(database.photoDao().photoNow(id)).path)

            photos.delete(id)

            assertFalse("the file should go with the row", file.exists())
            assertEquals(0, database.countRows("photos"))
            // Deleting what is already gone is a no-op, not a crash: two taps on the same tile.
            photos.delete(id)
        }

    /**
     * Room's cascade takes the rows; only [BunnyRepository.delete] can take the files. The second
     * bunny is here to prove the sweep is scoped — a `photos/` wipe would pass the first assertion.
     */
    @Test
    fun deletingABunnyTakesItsPhotoFilesAndLeavesTheOtherBunnysAlone() =
        runTest {
            val thumper = bunnies.add(BunnyEntity(name = "Thumper"))
            val clover = bunnies.add(BunnyEntity(name = "Clover"))
            val doomed = listOf(fileOf(photos.add(thumper, jpeg())), fileOf(photos.add(thumper, jpeg())))
            val kept = fileOf(photos.add(clover, jpeg()))

            bunnies.delete(thumper)

            doomed.forEach { assertFalse("${it.name} outlived its bunny", it.exists()) }
            assertTrue("another bunny's photo was swept up", kept.exists())
            assertEquals(1, database.countRows("photos"))
        }

    /** Photos are sole-owned, so the delete confirmation has to name them (ADR-0004). */
    @Test
    fun photosCountTowardsWhatDeletingABunnyWouldDestroy() =
        runTest {
            val bunnyId = bunnies.add(BunnyEntity(name = "Thumper"))
            photos.add(bunnyId, jpeg())
            photos.add(bunnyId, jpeg())

            val counts = bunnies.recordCounts(bunnyId)

            assertEquals(2, counts.soleOwnedRecords)
            assertEquals(0, counts.sharedRecords)
        }

    // -- fixtures ----------------------------------------------------------------------------

    private suspend fun fileOf(id: String): File = media.resolve(checkNotNull(database.photoDao().photoNow(id)).path)

    /**
     * A real JPEG, optionally carrying a real `DateTimeOriginal` — `saveAttributes` rewrites the
     * file in place, the way a camera writes it, so the pipeline reads a genuine tag.
     */
    private fun jpeg(takenAt: String? = null): Uri {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(220, 50, 50))
        val file = File(context.cacheDir, "photo-${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (takenAt != null) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, takenAt)
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private companion object {
        val PHOTO_PATH = Regex("""photos/[0-9a-f-]{36}\.jpg""")
    }
}
