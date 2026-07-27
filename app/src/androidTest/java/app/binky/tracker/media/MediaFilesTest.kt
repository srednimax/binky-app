package app.binky.tracker.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlin.math.abs

/**
 * Instrumented, not JVM: `Bitmap` and `ExifInterface` decoding are framework, and there is no
 * Robolectric in this project (ADR-0020).
 */
@RunWith(AndroidJUnit4::class)
class MediaFilesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** The app under test — what `filesDir` and the content resolver would be in production. */
    private val targetContext = instrumentation.targetContext

    private lateinit var root: File
    private lateinit var mediaFiles: MediaFiles

    @Before
    fun setUp() {
        root = File(targetContext.cacheDir, "media-test-${UUID.randomUUID()}")
        root.mkdirs()
        mediaFiles = MediaFiles(targetContext, root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun anAvatarIsStoredAsASquareJpegUnderItsOwnDirectory() =
        runTest {
            val path = mediaFiles.persist(rotatedQuadrants(), MediaKind.Avatar)

            assertTrue("expected avatars/<uuid>.jpg but was $path", path.matches(AVATAR_PATH))
            val written = mediaFiles.resolve(path)
            assertTrue("$path was not written", written.exists())

            val avatar = decode(written)
            assertEquals(512, avatar.width)
            assertEquals(512, avatar.height)
        }

    /**
     * The one bug in this pipeline that is otherwise invisible until a real camera photo hits a
     * real device: the fixture's pixels are stored sideways with orientation tag 6, and a crop plus
     * re-encode drops the tag. If the rotation is not baked into the pixels, these quadrants land
     * in the wrong corners.
     */
    @Test
    fun aCameraOrientationTagIsBakedIntoThePixels() =
        runTest {
            val avatar = decode(mediaFiles.resolve(mediaFiles.persist(rotatedQuadrants(), MediaKind.Avatar)))

            // The upright fixture is 1200x800 in four flat quadrants; the centred square crop keeps
            // all four, so each quarter-point of the 512² result sits well inside one of them.
            assertQuadrant(RED, avatar, 128, 128, "top-left")
            assertQuadrant(GREEN, avatar, 384, 128, "top-right")
            assertQuadrant(BLUE, avatar, 128, 384, "bottom-left")
            assertQuadrant(WHITE, avatar, 384, 384, "bottom-right")
        }

    @Test
    fun everyMetadataTagIsStrippedIncludingLocation() =
        runTest {
            val source = ExifInterface(assetCopy(FIXTURE).path)
            assertEquals(
                "fixture is not carrying an orientation tag; the test proves nothing",
                ExifInterface.ORIENTATION_ROTATE_90,
                source.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
            assertTrue("fixture is not carrying GPS", source.hasAttribute(ExifInterface.TAG_GPS_LATITUDE))

            val written = mediaFiles.resolve(mediaFiles.persist(rotatedQuadrants(), MediaKind.Avatar))

            val stored = ExifInterface(written.path)
            // The platform JPEG encoder emits an orientation tag of its own, as UNDEFINED. Harmless:
            // what must not survive is the source's *rotation*, which a renderer would apply a
            // second time on top of pixels that are already the right way up.
            val storedOrientation =
                stored.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            assertTrue(
                "stored file still asks to be transformed (orientation $storedOrientation)",
                storedOrientation == ExifInterface.ORIENTATION_NORMAL ||
                    storedOrientation == ExifInterface.ORIENTATION_UNDEFINED,
            )
            assertNull("GPS latitude survived the re-encode", stored.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull("GPS longitude survived the re-encode", stored.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull("camera make survived the re-encode", stored.getAttribute(ExifInterface.TAG_MAKE))
        }

    /** A small album thumbnail is stored at its own size, not blown up to 512² with invented pixels. */
    @Test
    fun aSourceSmallerThanTheSpecIsNotUpscaled() =
        runTest {
            val small = solidColourJpeg(width = 300, height = 200)

            val avatar = decode(mediaFiles.resolve(mediaFiles.persist(small, MediaKind.Avatar)))

            assertEquals(200, avatar.width)
            assertEquals(200, avatar.height)
        }

    @Test
    fun eachKindGetsItsOwnDirectory() =
        runTest {
            val avatar = mediaFiles.persist(rotatedQuadrants(), MediaKind.Avatar)

            assertTrue(avatar.startsWith("avatars/"))
            assertEquals(File(root, "avatars"), mediaFiles.directoryFor(MediaKind.Avatar))
            assertEquals(File(root, "photos"), mediaFiles.directoryFor(MediaKind.Photo))
            assertEquals(File(root, "documents"), mediaFiles.directoryFor(MediaKind.Document))
        }

    /** The delete-old half of a replace, and the cascade behind a deleted row (ADR-0020). */
    @Test
    fun deleteRemovesTheFileAndToleratesAMissingOne() =
        runTest {
            val path = mediaFiles.persist(rotatedQuadrants(), MediaKind.Avatar)

            assertTrue(mediaFiles.delete(path))
            assertFalse(mediaFiles.resolve(path).exists())
            assertFalse("deleting twice must not throw", mediaFiles.delete(path))
        }

    // -- fixtures ----------------------------------------------------------------------------

    /** Stored 800x1200 sideways with orientation 6; upright it is 1200x800 in four quadrants. */
    private fun rotatedQuadrants(): Uri = Uri.fromFile(assetCopy(FIXTURE))

    /**
     * Assets live in the *test* APK, so they are read through the instrumentation context — while
     * [mediaFiles] writes through the app under test's context.
     */
    private fun assetCopy(asset: String): File {
        val copy = File(targetContext.cacheDir, asset)
        instrumentation.context.assets.open(asset).use { input ->
            copy.outputStream().use { input.copyTo(it) }
        }
        return copy
    }

    private fun solidColourJpeg(
        width: Int,
        height: Int,
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(RED)
        val file = File(targetContext.cacheDir, "solid-${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private fun decode(file: File): Bitmap =
        checkNotNull(BitmapFactory.decodeFile(file.path)) { "${file.path} did not decode" }

    private fun assertQuadrant(
        expected: Int,
        bitmap: Bitmap,
        x: Int,
        y: Int,
        corner: String,
    ) {
        val actual = bitmap.getPixel(x, y)
        val drift =
            maxOf(
                abs(Color.red(expected) - Color.red(actual)),
                abs(Color.green(expected) - Color.green(actual)),
                abs(Color.blue(expected) - Color.blue(actual)),
            )
        assertTrue(
            "$corner should be #${hex(expected)} but was #${hex(actual)} — the image came out rotated",
            // Flat colour survives q85 almost exactly; the slack is for chroma subsampling only.
            drift <= 24,
        )
    }

    private fun hex(colour: Int) = Integer.toHexString(colour).takeLast(6)

    private companion object {
        const val FIXTURE = "rotated_quadrants.jpg"
        val AVATAR_PATH = Regex("""avatars/[0-9a-f-]{36}\.jpg""")

        val RED = Color.rgb(220, 50, 50)
        val GREEN = Color.rgb(50, 180, 80)
        val BLUE = Color.rgb(60, 90, 220)
        val WHITE = Color.rgb(245, 245, 245)
    }
}
