package app.binky.tracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.binky.tracker.data.BUNNY_SCHEMA_VERSION
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.buildBunnyDatabase
import app.binky.tracker.data.countRows
import app.binky.tracker.data.hasTable
import app.binky.tracker.data.readUserVersion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * **Stage, migrate, swap** (ADR-0023) — instrumented, because it is Room opening a real file on a
 * real device and there is no honest way to reach it from the JVM.
 *
 * Three claims, and the third is the expensive one: a staged file this build cannot open must not be
 * *emptied*. Left to inherit `BuildConfig.DEBUG`, a debug build's destructive fallback would wipe the
 * copy and then swap the empty result in — an owner would watch a restore report success and produce
 * an empty app. That is why `BackupRestorer` pins its own configuration rather than taking the
 * default, and why the pin is asserted here rather than trusted.
 *
 * At 1.0 the older-schema migration path was deliberately absent — no older *released* schema
 * existed, so there was nothing to migrate from. 1.1 is where it becomes real, and
 * [aSchemaFourBackupWrittenBy101RestoresAndMigrates] is that test.
 */
@RunWith(AndroidJUnit4::class)
class RestoreStageMigrateSwapTest {
    /** Committed under `app/src/androidTest/assets/`, produced once by the tagged 1.0.1 build. */
    private val schemaFourFixture = "bunny-schema-4-fixture.zip"

    /** The same, produced once by the tagged 1.1.0 build — see [aSchemaFiveBackupWrittenBy110MigratesTheLastStep]. */
    private val schemaFiveFixture = "bunny-schema-5-fixture.zip"

    /**
     * Committed under `app/src/androidTest/assets/`, produced once by the tagged 1.4.0 build — the
     * first fixture whose schema-6 tables are *full* rather than present-and-empty.
     */
    private val schemaSixFixture = "bunny-schema-6-fixture.zip"

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var filesDir: File
    private lateinit var scratchDir: File
    private lateinit var preservedDir: File
    private lateinit var outDir: File
    private val databaseFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        val root = File(context.cacheDir, "restore-test-${UUID.randomUUID()}")
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
        File(context.cacheDir, "restore-test").deleteRecursively()
        context.getDatabasePath(STAGED_DATABASE_FILE).delete()
    }

    /** A throwaway database name, registered for cleanup. */
    private fun throwawayName(prefix: String): String {
        val name = "$prefix-${UUID.randomUUID()}.db"
        databaseFiles += context.getDatabasePath(name)
        return name
    }

    private suspend fun databaseHolding(bunnyName: String): String {
        val name = throwawayName("restore-source")
        val database = buildBunnyDatabase(context, name)
        try {
            database.bunnyDao().insert(BunnyEntity(name = bunnyName))
        } finally {
            database.close()
        }
        return name
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

    private fun rowsIn(
        databaseName: String,
        table: String,
    ): Int {
        val database = buildBunnyDatabase(context, databaseName)
        return try {
            database.countRows(table)
        } finally {
            database.close()
        }
    }

    /**
     * Rows in [table] satisfying [where] — for asserting what a migration put in a *column*, where
     * [rowsIn] can only say how many rows survived it.
     */
    private fun rowsIn(
        databaseName: String,
        table: String,
        where: String,
    ): Int {
        val database = buildBunnyDatabase(context, databaseName)
        return try {
            database.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM `$table` WHERE $where")
                .use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
        } finally {
            database.close()
        }
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

    /**
     * The values in one of the droppings join tables, value by value rather than counted.
     *
     * A row count alone would be satisfied by a migration that wrote the *wrong* value into the right
     * number of rows, and these enums are stored by name — so the names are the assertion.
     */
    private fun droppingsIn(
        databaseName: String,
        table: String,
    ): List<String> {
        val database = buildBunnyDatabase(context, databaseName)
        return try {
            database.openHelper.readableDatabase
                .query("SELECT `value` FROM `$table` ORDER BY `value`")
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        } finally {
            database.close()
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val target = File(outDir, "hand-built-${UUID.randomUUID()}.zip")
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    private fun manifestBytes(schemaVersion: Int = BUNNY_SCHEMA_VERSION): ByteArray =
        encodeManifest(
            BackupManifest(
                scope = BackupScope.Essential,
                schemaVersion = schemaVersion,
                createdAtEpochMilli = 1_800_000_000_000,
            ),
        ).toByteArray()

    /** A real SQLite file at [userVersion], carrying a table this build knows nothing about. */
    private fun databaseFromAnOlderSchema(userVersion: Int): File {
        val file = File(outDir, "older-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE a_table_from_an_older_schema (id TEXT PRIMARY KEY)")
            database.execSQL("INSERT INTO a_table_from_an_older_schema VALUES ('a year of weighings')")
            database.execSQL("PRAGMA user_version = $userVersion")
        }
        return file
    }

    @Test
    fun aStagedFileAtTheCurrentSchemaOpensAndSwapsIn() =
        runTest {
            val sourceName = databaseHolding("Restored")
            val archive = exporterFor(sourceName).exportTo(File(outDir, "archive.zip"), BackupScope.Essential)

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            assertTrue(outcome.toString(), outcome is RestoreOutcome.Restored)
            // A **full replace**, not a merge: the row that was here is gone, the archive's is not.
            assertEquals(listOf("Restored"), bunnyNamesIn(liveName))
        }

    /** The way back, built out of the ordinary export path, before the swap. */
    @Test
    fun aRestoreLeavesASnapshotOfWhatItReplaced() =
        runTest {
            val sourceName = databaseHolding("Restored")
            val archive = exporterFor(sourceName).exportTo(File(outDir, "archive.zip"), BackupScope.Essential)

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            val snapshot = (outcome as RestoreOutcome.Restored).snapshot
            assertTrue(snapshot.name, snapshot.isFile && snapshot.length() > 0)
            assertEquals(preservedDir, snapshot.parentFile)
        }

    /**
     * No migration runs backwards. The **file's own header** is the authority here, not the
     * manifest's claim about it — the manifest below says the current version and is wrong.
     */
    @Test
    fun aStagedFileAtANewerVersionIsRefusedAndTheLiveDatabaseIsByteIdentical() =
        runTest {
            val newer = databaseFromAnOlderSchema(userVersion = BUNNY_SCHEMA_VERSION + 1)
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(),
                    BACKUP_DATABASE_ENTRY to newer.readBytes(),
                )

            val liveName = databaseHolding("Already here")
            val liveFile = context.getDatabasePath(liveName)
            val before = liveFile.readBytes()

            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            // Both numbers, not just the fact of a refusal: the owner is told how far ahead the file
            // is and how far this build reaches, which is what makes "find the newer Binky"
            // actionable rather than a dead end.
            assertEquals(
                RestoreOutcome.Refused(
                    RestoreRefusal.MadeByANewerBinky(
                        fileVersion = BUNNY_SCHEMA_VERSION + 1,
                        readableVersion = BUNNY_SCHEMA_VERSION,
                    ),
                ),
                outcome,
            )
            assertArrayEquals(before, liveFile.readBytes())
            assertEquals(listOf("Already here"), bunnyNamesIn(liveName))
        }

    /**
     * The pinned-configuration trap, from the outside: this build cannot open the staged file, so the
     * restore refuses. Unpinned, the debug fallback would have wiped the copy, reported success and
     * swapped an empty database in over the owner's history.
     */
    @Test
    fun aStagedFileThisBuildCannotOpenIsRefusedRatherThanEmptied() =
        runTest {
            val unopenable = databaseFromAnOlderSchema(userVersion = 1)
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(schemaVersion = 1),
                    BACKUP_DATABASE_ENTRY to unopenable.readBytes(),
                )

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.Unreadable), outcome)
            assertEquals(listOf("Already here"), bunnyNamesIn(liveName))
        }

    /**
     * The same claim from the inside, on the one call that decides it: opening a file this build
     * cannot migrate must **throw and leave the file alone**, in a debug build, where the default
     * would have destroyed it.
     */
    @Test
    fun thePinnedConfigurationRefusesToEmptyAFileItCannotOpen() {
        val name = throwawayName("restore-pinned")
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        databaseFromAnOlderSchema(userVersion = 1).copyTo(file, overwrite = true)

        val database =
            buildBunnyDatabase(context, databaseName = name, allowDestructiveMigration = false)
        val threw =
            try {
                database.openHelper.writableDatabase
                false
            } catch (e: RuntimeException) {
                true
            } finally {
                database.close()
            }

        assertTrue("opening a file with no migration should throw", threw)
        assertEquals(1, readUserVersion(file))

        // And the old table is still in there, which is what "not emptied" actually means.
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw
                .rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf("a_table_from_an_older_schema"),
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
        }
    }

    /** Refused **by name**: no manifest means the file is not a backup, whatever else is inside. */
    @Test
    fun anArchiveWithNoManifestIsNotABackup() =
        runTest {
            val sourceName = databaseHolding("Restored")
            val archive =
                zipOf(BACKUP_DATABASE_ENTRY to context.getDatabasePath(sourceName).readBytes())

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.NotABinkyBackup), outcome)
            assertEquals(listOf("Already here"), bunnyNamesIn(liveName))
        }

    @Test
    fun anArchiveWithNoDatabaseIsNotABackup() =
        runTest {
            val archive = zipOf(BACKUP_MANIFEST_ENTRY to manifestBytes())

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.NotABinkyBackup), outcome)
            assertEquals(listOf("Already here"), bunnyNamesIn(liveName))
        }

    /**
     * Zip-slip, and the reason this one stays instrumented: the two Android versions refuse this file
     * for *different reasons*, and only a device shows both. From Android 14 the platform validates
     * entry names inside `getNextEntry` itself and throws `ZipException` on a `../` path, *before*
     * [archiveEntryFor]'s allowlist gets to skip it. Uncaught, that killed the process mid-restore
     * rather than refusing the file — on a device, while the owner watched. Below 14 there is no
     * validator, and [isPathTraversal] is what refuses it.
     *
     * That second half is why this test earns its place twice over. It passed on API 34 and **failed
     * on API 26** the first time the emulator matrix ran, because the refusal was borrowed from the
     * platform: below 14 the entry was skipped like any unknown name and the archive restored without
     * it. Nothing escaped — that is the allowlist's job and it did it — but "restored, minus the part
     * we quietly dropped" is the wrong answer to a hostile file, and it was the answer across most of
     * this app's supported range.
     *
     * The traversal entry is written **last, after a valid manifest and database**, which is the
     * ordering that makes the difference load-bearing: a walk that failed *quietly* at that point
     * would have left a manifest and a staged database behind and looked like a complete archive.
     */
    @Test
    fun anArchiveCarryingATraversalEntryIsRefusedAndNothingEscapes() =
        runTest {
            val sourceName = databaseHolding("Restored")
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(),
                    BACKUP_DATABASE_ENTRY to context.getDatabasePath(sourceName).readBytes(),
                    "../escaped.txt" to "this must never be written".toByteArray(),
                )

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.NotABinkyBackup), outcome)
            assertEquals(listOf("Already here"), bunnyNamesIn(liveName))
            // Nothing written anywhere under the test root, not merely "not in the media directory".
            val root = outDir.parentFile!!
            assertTrue(
                root
                    .walkTopDown()
                    .filter { it.name == "escaped.txt" }
                    .toList()
                    .toString(),
                root.walkTopDown().none { it.name == "escaped.txt" },
            )
        }

    /**
     * The media merge, end to end: an Essential restore keeps a photo that was already on the phone,
     * because its scope never claimed to know about `photos/`.
     */
    @Test
    fun anEssentialRestoreKeepsPhotosAlreadyOnThePhone() =
        runTest {
            val photo = File(filesDir, "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg")
            photo.parentFile?.mkdirs()
            photo.writeText("a bunny in a cardboard box")

            val sourceName = databaseHolding("Restored")
            val archive = exporterFor(sourceName).exportTo(File(outDir, "archive.zip"), BackupScope.Essential)

            val liveName = databaseHolding("Already here")
            val outcome = restorerFor(liveName).restore(open = { archive.inputStream() })

            val restored = outcome as RestoreOutcome.Restored
            assertEquals(listOf("photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"), restored.merge.kept)
            assertTrue(photo.isFile)
            assertEquals("a bunny in a cardboard box", photo.readText())
        }

    /**
     * **The real migration, against a real artifact.**
     *
     * `Migration4To5Test` proves `MIGRATION_4_5` consistent with `4.json` — but that file is the
     * app's own *description* of version 4, so a database built from it can only contain what this
     * build believes 1.0.1 wrote. `bunny-schema-4-fixture.zip` is what 1.0.1 actually wrote: exported
     * by the tagged build's own container, seeder, Room and exporter, running on the test phone,
     * carrying the sample-data bunnies and never a line of real history. Same trick as
     * `rotated_quadrants.jpg`, for the same reason — the thing under test is precisely the
     * discrepancy a synthesised input cannot contain.
     *
     * It runs the whole owner-facing path, not just the migration: read the archive, stage the
     * database, migrate it, snapshot what was there, swap it in. Regenerating it is a device chore
     * (build the v1.0.1 tag in a worktree, seed, export, pull), which is the argument for committing
     * the artifact rather than a script that recreates it.
     */
    @Test
    fun aSchemaFourBackupWrittenBy101RestoresAndMigrates() =
        runTest {
            val liveName = databaseHolding("Already here")
            val liveFile = context.getDatabasePath(liveName)

            val outcome =
                restorerFor(liveName).restore(
                    // The instrumentation context, not the target's: the asset ships in the test APK.
                    open = {
                        InstrumentationRegistry
                            .getInstrumentation()
                            .context.assets
                            .open(schemaFourFixture)
                    },
                )

            val restored = outcome as? RestoreOutcome.Restored
            assertTrue(outcome.toString(), restored != null)
            assertEquals("the archive really is an older one", 4, restored!!.manifest.schemaVersion)

            // Migrated on the way in, and the file's own header says so.
            assertEquals(BUNNY_SCHEMA_VERSION, readUserVersion(liveFile))

            // Data survival, table by table — the assertion is that an owner's history arrived, not
            // that nothing threw. The counts are the seeder's, fixed by the pinned `now` it ran with.
            assertEquals(listOf("Bijou", "Nugget"), bunnyNamesIn(liveName))
            assertEquals(43, rowsIn(liveName, "weights"))
            assertEquals(5, rowsIn(liveName, "observations"))
            assertEquals(5, rowsIn(liveName, "photos"))
            assertEquals(2, rowsIn(liveName, "observation_symptoms"))

            // And the tables the migration is *for*, present and empty: 1.0.1 had no care reminders
            // to carry, so an empty pair of tables is the correct outcome rather than a missing one.
            assertEquals(0, rowsIn(liveName, "care_reminders"))
            assertEquals(0, rowsIn(liveName, "care_events"))

            // The droppings, which `MIGRATION_6_7` moved out of two columns and into two join tables.
            // Four of the five observations carried a form and a size; the fifth carried neither and
            // contributes no rows, which is the half a count could not distinguish from a migration
            // that defaulted the empty one. This is what "restores with its droppings intact" means.
            assertEquals(
                listOf("ROUND", "ROUND", "ROUND", "ROUND"),
                droppingsIn(liveName, "observation_droppings_appearance"),
            )
            assertEquals(
                listOf("NORMAL", "NORMAL", "SMALL", "SMALL"),
                droppingsIn(liveName, "observation_droppings_sizes"),
            )
        }

    /**
     * **The single 5 → 6 step, against an artifact this build did not describe** (PLAN 5b/5j).
     *
     * The schema-4 fixture above already proves 4 → 5 → 6 through the staged path, so the
     * skipped-version upgrade is covered. What this one adds is the step that fixture cannot isolate:
     * a database written by the shipped **1.1.0** build, migrated by `MIGRATION_5_6` alone. It cannot
     * be synthesised for the same reason the schema-4 one could not — a fixture built from `5.json`
     * contains only what *this* build believes 1.1.0 wrote, and that discrepancy is the thing under
     * test.
     *
     * Regenerating it is a device chore: build the `v1.1.0` tag in a worktree, `pm clear`, install,
     * and run the seeder and `AppContainer.backupExporter` against it with a pinned `now`
     * (`2026-08-05T12:00:00Z`, which is what fixes the counts below). The owner-facing route would
     * export through the SAF picker, but the picker only chooses where the bytes land — `exportTo`
     * has already built the archive by then — and `adb` can drive neither the picker nor, on this
     * phone, taps at all.
     */
    @Test
    fun aSchemaFiveBackupWrittenBy110MigratesTheLastStep() =
        runTest {
            val liveName = databaseHolding("Already here")
            val liveFile = context.getDatabasePath(liveName)

            val outcome =
                restorerFor(liveName).restore(
                    open = {
                        InstrumentationRegistry
                            .getInstrumentation()
                            .context.assets
                            .open(schemaFiveFixture)
                    },
                )

            val restored = outcome as? RestoreOutcome.Restored
            assertTrue(outcome.toString(), restored != null)
            assertEquals("the archive really is a 1.1.0 one", 5, restored!!.manifest.schemaVersion)
            assertEquals(BUNNY_SCHEMA_VERSION, readUserVersion(liveFile))

            // Everything 1.0.1 already had, unchanged by a second migration — the counts match the
            // schema-4 fixture's exactly, because the seeder and the pinned `now` are the same.
            assertEquals(listOf("Bijou", "Nugget"), bunnyNamesIn(liveName))
            assertEquals(43, rowsIn(liveName, "weights"))
            assertEquals(5, rowsIn(liveName, "observations"))
            assertEquals(5, rowsIn(liveName, "photos"))
            assertEquals(2, rowsIn(liveName, "observation_symptoms"))

            // And what 1.1 added, which the schema-4 fixture could only ever show as empty: these
            // arrived through `MIGRATION_4_5` in the shipped build and must survive `MIGRATION_5_6`.
            assertEquals(4, rowsIn(liveName, "care_reminders"))
            assertEquals(2, rowsIn(liveName, "care_events"))
            assertEquals(2, rowsIn(liveName, "watches"))

            // The tables `MIGRATION_5_6` is *for*: present and empty, because 1.1.0 had no vet
            // visits, courses or documents to carry. Present-and-empty is the correct outcome.
            assertEquals(0, rowsIn(liveName, "vets"))
            assertEquals(0, rowsIn(liveName, "visits"))
            assertEquals(0, rowsIn(liveName, "medication_courses"))
            assertEquals(0, rowsIn(liveName, "documents"))

            // The droppings across `MIGRATION_6_7`, identical to the schema-4 fixture's for the same
            // reason the counts above are: one seeder, one pinned `now`. Asserted from both artifacts
            // because they reach the rebuild by different routes — this one arrives at 6 having been
            // written there, where the schema-4 file is migrated into it two steps earlier.
            assertEquals(
                listOf("ROUND", "ROUND", "ROUND", "ROUND"),
                droppingsIn(liveName, "observation_droppings_appearance"),
            )
            assertEquals(
                listOf("NORMAL", "NORMAL", "SMALL", "SMALL"),
                droppingsIn(liveName, "observation_droppings_sizes"),
            )
        }

    /**
     * **The 6 → 7 step, against the first artifact whose schema-6 tables are not empty** (7.5 §7).
     *
     * The schema-4 and schema-5 fixtures both reach 7 already, so `MIGRATION_6_7` is not unproven.
     * What neither of them can show is the migration running with the schema-6 graph *populated*:
     * 1.0.1 and 1.1.0 had no vets, visits, courses, doses or documents to carry, so those tables are
     * present-and-empty in both. This file was written by 1.4.0, which seeds all of them — and
     * `MIGRATION_6_7` rebuilds `observations` and drops `observation_symptoms` outright before
     * putting it back, with foreign keys and their cascades live throughout. A rebuild that took
     * something else down with it has, until this fixture, had nothing to take.
     *
     * Regenerating it is the same device chore as the other two: build the `v1.4.0` tag in a
     * worktree, `pm clear`, install, and run the seeder and `AppContainer.backupExporter` against
     * the real container with the pinned `now` (`2026-08-05T12:00:00Z`, which is what fixes every
     * count below), then pull the archive out of `cache/exports`.
     */
    @Test
    fun aSchemaSixBackupWrittenBy140MigratesTheLastStep() =
        runTest {
            val liveName = databaseHolding("Already here")
            val liveFile = context.getDatabasePath(liveName)

            val outcome =
                restorerFor(liveName).restore(
                    open = {
                        InstrumentationRegistry
                            .getInstrumentation()
                            .context.assets
                            .open(schemaSixFixture)
                    },
                )

            val restored = outcome as? RestoreOutcome.Restored
            assertTrue(outcome.toString(), restored != null)
            assertEquals("the archive really is a 1.4.0 one", 6, restored!!.manifest.schemaVersion)
            assertEquals(BUNNY_SCHEMA_VERSION, readUserVersion(liveFile))

            // The 1.0.1-era counts, unchanged by three schema versions. Weights are 44 rather than
            // the other two fixtures' 43 because 1.2 seeds a vet visit that records its own weighing
            // — the counts are read off this artifact, never assumed to match the older ones.
            assertEquals(listOf("Bijou", "Nugget"), bunnyNamesIn(liveName))
            assertEquals(44, rowsIn(liveName, "weights"))
            assertEquals(5, rowsIn(liveName, "observations"))
            assertEquals(5, rowsIn(liveName, "photos"))
            assertEquals(2, rowsIn(liveName, "observation_symptoms"))
            assertEquals(4, rowsIn(liveName, "care_reminders"))
            assertEquals(2, rowsIn(liveName, "care_events"))
            assertEquals(2, rowsIn(liveName, "watches"))

            // What only this fixture carries: the schema-6 tables with rows in them, through a
            // migration that rebuilds a table they are joined to.
            assertEquals(2, rowsIn(liveName, "vets"))
            assertEquals(3, rowsIn(liveName, "visits"))
            assertEquals(3, rowsIn(liveName, "medication_courses"))
            assertEquals(3, rowsIn(liveName, "medication_times"))
            assertEquals(14, rowsIn(liveName, "doses"))
            assertEquals(4, rowsIn(liveName, "documents"))
            assertEquals(7, rowsIn(liveName, "document_pages"))

            // The droppings across `MIGRATION_6_7`, by value: four observations carried both columns
            // and the fifth carried neither, which is the half a row count cannot tell apart from a
            // migration that defaulted it.
            assertEquals(
                listOf("ROUND", "ROUND", "ROUND", "ROUND"),
                droppingsIn(liveName, "observation_droppings_appearance"),
            )
            assertEquals(
                listOf("NORMAL", "NORMAL", "SMALL", "SMALL"),
                droppingsIn(liveName, "observation_droppings_sizes"),
            )

            // Schema 7's other half: the tray photo link exists and is empty on every row, because
            // `MIGRATION_6_7` writes it as NULL — 1.4.0 had nowhere to have put one.
            assertEquals(0, rowsIn(liveName, "observations", "`trayPhotoPath` IS NOT NULL"))

            // And the media the older fixtures could not carry: the document pages arrive beside the
            // photos. `overlaid` rather than `kept` — the archive's own files are the ones written
            // over the disk, where `kept` is the disk's own files in a kind this scope never carried.
            assertEquals(5, restored.merge.overlaid.count { it.startsWith("photos/") })
            assertEquals(7, restored.merge.overlaid.count { it.startsWith("documents/") })
            // Planned is not written: the pages have to be on disk under their relative paths, which
            // is what makes a restored document open rather than render as seven placeholders.
            restored.merge.overlaid.forEach { path ->
                assertTrue("$path missing on disk", File(filesDir, path).isFile)
            }
        }

    /** A sanity check that the throwaway live database really is a Binky one. */
    @Test
    fun theFixtureBuildsARealDatabase() {
        val name = throwawayName("restore-fixture")
        val database = buildBunnyDatabase(context, name)
        try {
            assertTrue(database.hasTable("bunnies"))
            assertEquals(0, database.countRows("bunnies"))
        } finally {
            database.close()
        }
    }
}
