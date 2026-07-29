package app.binky.tracker.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.PhotoEntity
import app.binky.tracker.data.buildBunnyDatabase
import app.binky.tracker.media.MediaKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The gate's headline item, automated: **export at each of the three scopes, clear app data, restore,
 * and check that what the scope promised is what came back** (ADR-0005).
 *
 * This is the flow the whole of 1.0 rests on — ADR-0019 gates the release on the data being safe, and
 * this is the only test that watches a file leave the phone and come back onto an empty one. It is
 * deliberately separate from [RestoreStageMigrateSwapTest], which owns the *refusals*: everything
 * there is about a restore correctly declining, and every one of its cases exports at `Essential`.
 * `Records` and `Everything` had no test at all, and nothing anywhere asserted that a media file is
 * written back to disk — the existing photo case proves a file already present is **kept**, which is
 * the merge, not the extraction.
 *
 * "Clear app data" is modelled as it actually is on a phone: `filesDir` emptied, and a **fresh empty
 * database**, rather than a second populated one. A restore onto a clean install is the case an owner
 * reaches for after losing a phone, and it is the one where an entry the exporter forgot to write has
 * nowhere to hide.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var scratchDir: File
    private lateinit var preservedDir: File
    private lateinit var outDir: File
    private val databaseFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        root = File(context.cacheDir, "roundtrip-test-${UUID.randomUUID()}")
        filesDir = File(root, "files").apply { mkdirs() }
        scratchDir = File(root, "cache").apply { mkdirs() }
        preservedDir = File(root, "preserved").apply { mkdirs() }
        outDir = File(root, "out").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        // Throwaway names throughout, never the real database — this test would otherwise scribble
        // over the history on the developer's own phone.
        databaseFiles.forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.path + it).delete() }
        }
        root.deleteRecursively()
        context.getDatabasePath(STAGED_DATABASE_FILE).delete()
    }

    /** What went onto the phone before the export, so the assertions can name it afterwards. */
    private data class Seed(
        val sourceName: String,
        val bunnyName: String,
        val mediaPaths: Map<MediaKind, String>,
        val preferences: ByteArray,
    )

    private fun throwawayName(prefix: String): String {
        val name = "$prefix-${UUID.randomUUID()}.db"
        databaseFiles += context.getDatabasePath(name)
        return name
    }

    /** The bytes a media file is seeded with — distinct per kind, so a mix-up cannot pass. */
    private fun contentsFor(kind: MediaKind) = "the ${kind.name} file, seeded before the export"

    /**
     * A phone with something on it: one bunny, one photo row pointing at a real file, one file of
     * every media kind, and a preferences file.
     */
    private suspend fun seedAPhone(): Seed {
        val mediaPaths =
            MediaKind.entries.associateWith { kind ->
                val path = "${kind.directory}/${UUID.randomUUID()}.jpg"
                File(filesDir, path).apply {
                    parentFile?.mkdirs()
                    writeText(contentsFor(kind))
                }
                path
            }

        val preferences = "display unit: grams; selected bunny: Milo".toByteArray()
        File(filesDir, PREFERENCES_FILE_PATH).apply {
            parentFile?.mkdirs()
            writeBytes(preferences)
        }

        val sourceName = throwawayName("roundtrip-source")
        val database = buildBunnyDatabase(context, sourceName)
        val bunny = BunnyEntity(name = "Milo", avatarPath = mediaPaths.getValue(MediaKind.Avatar))
        try {
            database.bunnyDao().insert(bunny)
            database.photoDao().insert(
                PhotoEntity(bunnyId = bunny.id, path = mediaPaths.getValue(MediaKind.Photo)),
            )
        } finally {
            database.close()
        }

        return Seed(
            sourceName = sourceName,
            bunnyName = "Milo",
            mediaPaths = mediaPaths,
            preferences = preferences,
        )
    }

    private fun exporterFor(databaseName: String) =
        BackupExporter(
            databaseFile = context.getDatabasePath(databaseName),
            filesDir = filesDir,
            scratchDir = scratchDir,
        )

    private fun restorerFor(liveName: String) =
        BackupRestorer(
            context = context,
            filesDir = filesDir,
            preservedDir = preservedDir,
            scratchDir = scratchDir,
            exporter = exporterFor(liveName),
            databaseName = liveName,
        )

    /**
     * A cleared app: nothing in `filesDir`, and an empty database.
     *
     * The database is **opened** and not merely built. Room opens lazily — `build()` alone creates no
     * file — and a restore's pre-restore snapshot checkpoints the live database, which needs one to
     * exist. That matches the phone: nothing reaches the Backup screen without the app having read a
     * bunny first, so by the time a restore runs the file is always there.
     */
    private fun clearAppData(): String {
        filesDir.deleteRecursively()
        filesDir.mkdirs()

        val liveName = throwawayName("roundtrip-live")
        val database = buildBunnyDatabase(context, liveName)
        try {
            database.openHelper.writableDatabase
        } finally {
            database.close()
        }
        return liveName
    }

    /**
     * Seed, export at [scope], clear app data, restore. Returns the outcome and the now-live
     * database's name.
     */
    private suspend fun roundTrip(scope: BackupScope): Triple<Seed, RestoreOutcome, String> {
        val seed = seedAPhone()
        val archive =
            exporterFor(seed.sourceName).exportTo(File(outDir, "archive-${scope.slug}.zip"), scope)

        val liveName = clearAppData()
        val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })
        return Triple(seed, outcome, liveName)
    }

    private fun bunnyNamesIn(databaseName: String): List<String> {
        val database = buildBunnyDatabase(context, databaseName)
        return try {
            database.openHelper.readableDatabase.query("SELECT name FROM bunnies ORDER BY name").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        } finally {
            database.close()
        }
    }

    private fun photoPathsIn(databaseName: String): List<String> {
        val database = buildBunnyDatabase(context, databaseName)
        return try {
            database.openHelper.readableDatabase.query("SELECT path FROM photos ORDER BY path").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        } finally {
            database.close()
        }
    }

    /**
     * Every kind the scope carries is back on disk with its own bytes; every kind it does not is
     * absent. Both halves matter — an export that quietly carried everything would pass the first.
     */
    private fun assertMediaMatchesScope(
        scope: BackupScope,
        seed: Seed,
    ) {
        MediaKind.entries.forEach { kind ->
            val file = File(filesDir, seed.mediaPaths.getValue(kind))
            if (scope.carries(kind)) {
                assertTrue("$scope should have restored ${kind.directory}", file.isFile)
                assertEquals(contentsFor(kind), file.readText())
            } else {
                assertFalse("$scope should not have restored ${kind.directory}", file.exists())
            }
        }
    }

    /** The database comes back whatever the scope — it is the one member every scope carries. */
    private fun assertDatabaseRestored(
        seed: Seed,
        outcome: RestoreOutcome,
        liveName: String,
    ) {
        assertTrue(outcome.toString(), outcome is RestoreOutcome.Restored)
        assertEquals(listOf(seed.bunnyName), bunnyNamesIn(liveName))
    }

    @Test
    fun anEssentialRoundTripBringsBackTheDatabaseAndAvatarsOnly() =
        runTest {
            val (seed, outcome, liveName) = roundTrip(BackupScope.Essential)

            assertDatabaseRestored(seed, outcome, liveName)
            assertMediaMatchesScope(BackupScope.Essential, seed)
        }

    @Test
    fun aRecordsRoundTripBringsBackAvatarsAndDocumentsButNotThePhotoGallery() =
        runTest {
            val (seed, outcome, liveName) = roundTrip(BackupScope.Records)

            assertDatabaseRestored(seed, outcome, liveName)
            assertMediaMatchesScope(BackupScope.Records, seed)
        }

    @Test
    fun anEverythingRoundTripBringsBackEveryMediaKind() =
        runTest {
            val (seed, outcome, liveName) = roundTrip(BackupScope.Everything)

            assertDatabaseRestored(seed, outcome, liveName)
            assertMediaMatchesScope(BackupScope.Everything, seed)
        }

    /**
     * ADR-0005's reason for putting preferences in every scope from Essential upward: a restored phone
     * missing the display unit, the selected bunny and the chosen backup scope reads as bugs rather
     * than as missing data.
     *
     * Asserted **byte-identical** rather than through `AppPreferences`, because bytes are exactly what
     * this layer promises — DataStore's protobuf goes into the archive verbatim and comes back
     * verbatim, with nothing in between reinterpreting it.
     */
    @Test
    fun preferencesSurviveTheRoundTripAtEveryScope() =
        runTest {
            BackupScope.entries.forEach { scope ->
                val (seed, _, _) = roundTrip(scope)

                val restored = File(filesDir, PREFERENCES_FILE_PATH)
                assertTrue("$scope dropped the preferences file", restored.isFile)
                assertArrayEquals("$scope corrupted the preferences file", seed.preferences, restored.readBytes())
            }
        }

    /**
     * The other half of "degrades gracefully": a scope that excluded photos still carries the **rows**,
     * because the rows live in the database that every scope carries. So an Essential restore onto a
     * clean phone lands a photo row whose file is not there.
     *
     * That state is legitimate and the house rule says it must render as a placeholder rather than
     * crash. This asserts the data-layer half — that the dangling row genuinely occurs — which is what
     * makes the UI half worth hand-verifying rather than hypothetical.
     */
    @Test
    fun aScopeThatExcludedPhotosStillRestoresTheirRowsPointingAtNothing() =
        runTest {
            val (seed, _, liveName) = roundTrip(BackupScope.Essential)

            val photoPath = seed.mediaPaths.getValue(MediaKind.Photo)
            assertEquals(listOf(photoPath), photoPathsIn(liveName))
            assertFalse("the file must be the missing half, not the row", File(filesDir, photoPath).exists())
        }
}
