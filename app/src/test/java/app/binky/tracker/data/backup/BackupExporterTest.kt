package app.binky.tracker.data.backup

import app.binky.tracker.media.MediaKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * What comes out of an export, at each scope — the zip round-trip and the manifest that describes it.
 *
 * Scopes are the reason the photo gallery had to land before backup did: with `documents/` empty
 * until Phase 5, photos are the only thing that makes the three scopes distinguishable at 1.0, and a
 * scope design nothing can tell apart is a scope design nothing has tested.
 *
 * The WAL checkpoint is substituted here with a plain copy: `android.database.sqlite` does not exist
 * on the JVM, and the claim under test is the archive's *layout*, not SQLite's. The checkpoint has
 * its own place on a device.
 */
class BackupExporterTest {
    @get:Rule val temp = TemporaryFolder()

    private val avatar = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val document = "3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"
    private val photo = "3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"

    private lateinit var filesDir: File
    private lateinit var databaseFile: File
    private lateinit var scratchDir: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        scratchDir = temp.newFolder("cache")
        databaseFile = temp.newFile("bunny.db").apply { writeText("a year of weighings") }

        write(File(filesDir, "${MediaKind.Avatar.directory}/$avatar"), "avatar")
        write(File(filesDir, "${MediaKind.Document.directory}/$document"), "document")
        write(File(filesDir, "${MediaKind.Photo.directory}/$photo"), "photo")
        write(File(filesDir, PREFERENCES_FILE_PATH), "preferences")
        // Something a media directory should never hold. The export skips it rather than shipping a
        // file the far end would refuse anyway.
        write(File(filesDir, "${MediaKind.Photo.directory}/notes.txt"), "stray")
    }

    private fun exporter(): BackupExporter =
        BackupExporter(
            databaseFile = databaseFile,
            filesDir = filesDir,
            scratchDir = scratchDir,
            schemaVersion = 4,
            checkpoint = { source, target -> source.copyTo(target, overwrite = true) },
        )

    private fun write(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun entriesOf(archive: File): Map<String, String> =
        buildMap {
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().decodeToString())
                    zip.closeEntry()
                }
            }
        }

    private suspend fun exportAt(scope: BackupScope): Map<String, String> {
        val target = File(temp.newFolder("out-${scope.name}"), "archive.zip")
        return entriesOf(exporter().exportTo(target, scope, Instant.ofEpochMilli(1_800_000_000_000)))
    }

    @Test
    fun essentialCarriesTheDatabaseThePreferencesAndAvatarsOnly() =
        runTest {
            val entries = exportAt(BackupScope.Essential)

            assertEquals(
                setOf(
                    BACKUP_MANIFEST_ENTRY,
                    BACKUP_DATABASE_ENTRY,
                    BACKUP_PREFERENCES_ENTRY,
                    "avatars/$avatar",
                ),
                entries.keys,
            )
            assertEquals("a year of weighings", entries[BACKUP_DATABASE_ENTRY])
        }

    @Test
    fun recordsAddsDocuments() =
        runTest {
            assertEquals(
                setOf(
                    BACKUP_MANIFEST_ENTRY,
                    BACKUP_DATABASE_ENTRY,
                    BACKUP_PREFERENCES_ENTRY,
                    "avatars/$avatar",
                    "documents/$document",
                ),
                exportAt(BackupScope.Records).keys,
            )
        }

    @Test
    fun everythingAddsThePhotoGallery() =
        runTest {
            assertEquals(
                setOf(
                    BACKUP_MANIFEST_ENTRY,
                    BACKUP_DATABASE_ENTRY,
                    BACKUP_PREFERENCES_ENTRY,
                    "avatars/$avatar",
                    "documents/$document",
                    "photos/$photo",
                ),
                exportAt(BackupScope.Everything).keys,
            )
        }

    /** ADR-0005: preferences ride from Essential upward, or a restored phone reads as buggy. */
    @Test
    fun everyScopeCarriesThePreferences() =
        runTest {
            BackupScope.entries.forEach { scope ->
                assertTrue(scope.name, BACKUP_PREFERENCES_ENTRY in exportAt(scope))
            }
        }

    @Test
    fun theManifestCountsWhatTheScopeActuallyCarries() =
        runTest {
            val manifest = decodeManifest(exportAt(BackupScope.Records).getValue(BACKUP_MANIFEST_ENTRY))!!

            assertEquals(BackupScope.Records, manifest.scope)
            assertEquals(4, manifest.schemaVersion)
            assertEquals(Instant.ofEpochMilli(1_800_000_000_000), manifest.createdAt)
            assertEquals(1, manifest.countFor(MediaKind.Avatar))
            assertEquals(1, manifest.countFor(MediaKind.Document))
            // Not "0 photos on this phone" — Records never looked, and the manifest says so by
            // carrying no count for a kind outside its scope.
            assertEquals(0, manifest.countFor(MediaKind.Photo))
        }

    /** The scope is in the filename for humans; the manifest is what restore reads. */
    @Test
    fun theFilenameNamesTheScope() {
        assertEquals(
            "bunny-everything-20260101T000000Z.zip",
            exportFileName(BackupScope.Everything, Instant.parse("2026-01-01T00:00:00Z")),
        )
    }

    @Test
    fun aFileAMediaDirectoryShouldNotHoldIsNotShipped() =
        runTest {
            assertTrue(exportAt(BackupScope.Everything).keys.none { it.endsWith("notes.txt") })
        }

    /** An interrupted export must leave nothing that looks shareable and is not. */
    @Test
    fun noPartFileSurvivesASuccessfulExport() =
        runTest {
            val out = temp.newFolder("out-part")
            val target = File(out, "archive.zip")
            exporter().exportTo(target, BackupScope.Essential)

            assertEquals(listOf("archive.zip"), out.list()?.toList())
        }
}
