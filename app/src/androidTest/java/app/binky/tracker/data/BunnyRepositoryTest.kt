package app.binky.tracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The half of deletion that Room's cascade cannot reach: the avatar **file**.
 *
 * Instrumented rather than JVM because it is the real `MediaFiles` writing to a real directory —
 * a mock that "deleted" a file it never wrote would prove nothing.
 */
@RunWith(AndroidJUnit4::class)
class BunnyRepositoryTest {
    private lateinit var database: BunnyDatabase
    private lateinit var media: MediaFiles
    private lateinit var bunnies: BunnyRepository

    @Before
    fun open() {
        database = inMemoryDatabase()
        media = temporaryMedia()
        bunnies = BunnyRepository(database, FluffleRepository(database), temporaryPreferences(), media)
    }

    @After
    fun close() = database.close()

    /** A stand-in for a persisted avatar: MediaFiles' own encoding is proven in its own test. */
    private fun writeAvatar(): Pair<String, File> {
        val relativePath = "avatars/${UUID.randomUUID()}.jpg"
        val file = media.resolve(relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return relativePath to file
    }

    @Test
    fun deletingABunnyRemovesItsAvatarFile() =
        runTest {
            val (relativePath, file) = writeAvatar()
            val id = bunnies.add(BunnyEntity(name = "Thumper", avatarPath = relativePath))

            bunnies.delete(id)

            assertFalse("the avatar file should be gone with the row", file.exists())
            assertEquals(0, database.countRows("bunnies"))
        }

    /** Archiving destroys nothing — including the photograph (ADR-0004). */
    @Test
    fun archivingABunnyKeepsItsAvatarFile() =
        runTest {
            val (relativePath, file) = writeAvatar()
            val id = bunnies.add(BunnyEntity(name = "Clover", avatarPath = relativePath))

            bunnies.archive(id)

            assertTrue("archiving must not touch the avatar file", file.exists())
            assertEquals(1, bunnies.archivedBunnies.first().size)
            assertEquals(0, bunnies.activeBunnies.first().size)
        }
}
