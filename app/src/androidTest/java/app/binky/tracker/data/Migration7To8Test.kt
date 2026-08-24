package app.binky.tracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MIGRATION_7_8` — the same rebuild `MIGRATION_6_7` did, with **three** cascade-carrying children
 * instead of one.
 *
 * The tray photo becomes a set, which takes `trayPhotoPath` off `observations`; at `minSdk` 26 that
 * means create-copy-drop-rename, and `DROP TABLE observations` performs an implicit delete of every
 * row. Since 1.5 that delete cascades into `observation_symptoms`, `observation_droppings_appearance`
 * **and** `observation_droppings_sizes`, where ADR-0029's recipe was written against one child.
 *
 * **This is why the test asserts rows rather than shape.** `runMigrationsAndValidate` compares the
 * result against `8.json` and passes; a database whose every symptom tick and every droppings value
 * has been cascaded away has *exactly* the right schema. A recipe that staged two children out of
 * three would be green in CI and would silently delete an owner's history on upgrade day.
 *
 * The seed is deliberately awkward in three ways, each of which fails a different plausible mistake:
 *
 * - **A bonded pair sharing one tray photo path.** Two rows, one file — which is how a tray fact is
 *   stored (ADR-0008) — so a migration that inserted per *path* rather than per *row* would come out
 *   with one photo where two rows expect one each.
 * - **Values spread across three observations**, so restoring only the first row's children passes
 *   nothing.
 * - **One observation that recorded nothing at all**, so absence has to survive as absence rather
 *   than as a guess (ADR-0001).
 *
 * Six claims, in the order they can fail:
 *
 * 1. [migratesEverySchemaSevenRowForward] — every row arrives and Room agrees the shape matches.
 * 2. [theTrayPhotoBecomesOneRowPerObservation] — the point of the version bump.
 * 3. [everySymptomTickSurvivesTheRebuild] — the cascade ADR-0029 already knew about.
 * 4. [bothDroppingsSetsSurviveTheRebuild] — **the two it did not**, and the reason this file exists.
 * 5. [theEventsTableArrivesEmpty] — the migration's other half, which must add a table without
 *    inventing a row in it.
 * 6. [aReleaseShapedOpenOfASchemaSevenFileSucceeds] — registered, through the app's real builder. A
 *    migration that exists but is never wired up passes the first five and crashes every 1.8 phone.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BunnyDatabase::class.java,
        )

    @Test
    fun migratesEverySchemaSevenRowForward() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, *BUNNY_MIGRATIONS)

        migrated.assertSchemaSevenDataSurvived()

        // The staging tables are not part of any schema and must not outlive the migration.
        for (staged in listOf(
            "tray_photos_backup",
            "observation_symptoms_backup",
            "droppings_appearance_backup",
            "droppings_sizes_backup",
            "observations_new",
        )) {
            assertEquals("$staged should not exist after the migration", 0, migrated.tablesNamed(staged))
        }
    }

    /**
     * The column becomes the set — **one row per observation that had a path**, at position 0.
     *
     * The bonded pair is the case worth stating: `observation-1` and `observation-2` are one tray
     * seen by two bunnies, so they carry the same path and must come out as two rows. Nothing
     * de-duplicates across observations, because the refcount on the delete path is what makes a
     * duplicated path safe (ADR-0029) and a "clever" migration that collapsed them would strand one
     * bunny's row with no photo.
     */
    @Test
    fun theTrayPhotoBecomesOneRowPerObservation() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, *BUNNY_MIGRATIONS)

        assertEquals(2, migrated.countOf("observation_photos"))
        assertEquals(listOf("observations/tray.jpg"), migrated.photoPathsOf("observation-1"))
        assertEquals(listOf("observations/tray.jpg"), migrated.photoPathsOf("observation-2"))
        // The observation that never had one stays without one: zero rows, which is the join table's
        // spelling of the old column's null.
        assertTrue(migrated.photoPathsOf("observation-3").isEmpty())

        // Every migrated photo is the first of its set — there was only ever one to be first.
        assertEquals(0, migrated.countWhere("observation_photos", "position <> 0"))
    }

    /** The cascade ADR-0029 named, survived a second time. */
    @Test
    fun everySymptomTickSurvivesTheRebuild() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, *BUNNY_MIGRATIONS)

        assertEquals(3, migrated.countOf("observation_symptoms"))
        assertEquals(listOf("symptom-1", "symptom-2"), migrated.symptomIdsOf("observation-1"))
        assertEquals(listOf("symptom-1"), migrated.symptomIdsOf("observation-3"))
        // Never at risk, asserted anyway: a recipe that recreated the link table with the wrong
        // `ON DELETE` on the symptom side would only surface much later, when a symptom was retired.
        assertEquals(2, migrated.countOf("symptoms"))
    }

    /**
     * **The two children ADR-0029's recipe did not know about**, because that ADR is what created
     * them.
     *
     * Values on two different observations, so a recipe that staged only the row it was looking at
     * fails. This is the assertion the whole file is for: without it, losing every droppings value an
     * owner has ever recorded is a green build.
     */
    @Test
    fun bothDroppingsSetsSurviveTheRebuild() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, *BUNNY_MIGRATIONS)

        assertEquals(3, migrated.countOf("observation_droppings_appearance"))
        assertEquals(
            listOf("MUCUS", "SOFT"),
            migrated.valuesIn("observation_droppings_appearance", "observation-1"),
        )
        assertEquals(
            listOf("ROUND"),
            migrated.valuesIn("observation_droppings_appearance", "observation-3"),
        )

        assertEquals(2, migrated.countOf("observation_droppings_sizes"))
        assertEquals(
            listOf("NORMAL", "SMALL"),
            migrated.valuesIn("observation_droppings_sizes", "observation-1"),
        )
        // Recorded nothing about size, and comes out recording nothing (ADR-0001).
        assertTrue(migrated.valuesIn("observation_droppings_sizes", "observation-3").isEmpty())
    }

    /** The migration's cheap half: a table that arrives, and arrives empty. */
    @Test
    fun theEventsTableArrivesEmpty() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, *BUNNY_MIGRATIONS)

        assertEquals(1, migrated.tablesNamed("events"))
        assertEquals(0, migrated.countOf("events"))
    }

    /**
     * A **release-shaped** open of a schema-7 file: the app's own builder with the destructive
     * fallback off, which is what every 1.8 owner gets on upgrade day.
     */
    @Test
    fun aReleaseShapedOpenOfASchemaSevenFileSucceeds() {
        helper.createDatabase(TEST_DB, 7).use { it.seedSchemaSeven() }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = buildBunnyDatabase(context, TEST_DB, allowDestructiveMigration = false)
        try {
            // Opening is what runs the migration — the builder only assembles the object.
            database.openHelper.writableDatabase.assertSchemaSevenDataSurvived()
            assertEquals(BUNNY_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-7-8-test.db"
    }
}

/**
 * A row in **every** table version 7 has, in foreign-key order.
 *
 * Literal SQL rather than entities, for the reason every migration test in this project gives: the
 * entities describe version *8*, and a test that built its "old" database out of today's classes
 * would be testing the migration against itself. `observations` here still has `trayPhotoPath`, which
 * is the point.
 */
private fun SupportSQLiteDatabase.seedSchemaSeven() {
    execSQL("INSERT INTO fluffles (id, name) VALUES ('fluffle-1', 'The pair')")
    for ((id, name) in listOf("bunny-1" to "Thumper", "bunny-2" to "Clover")) {
        execSQL(
            "INSERT INTO bunnies " +
                "(id, name, avatarPath, birthDate, birthDateApproximate, sex, neutered, breed, colour, " +
                "fluffleId, archivedAt, createdAt) " +
                "VALUES ('$id', '$name', 'avatars/a.jpg', 18000, 0, 'FEMALE', 'NEUTERED', " +
                "'Mini Lop', 'Sooty', 'fluffle-1', NULL, 1700000000000)",
        )
    }
    execSQL(
        "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
            "VALUES ('weight-1', 'bunny-1', 2500, 1700000000000, 1700000000000, NULL)",
    )
    execSQL(
        "INSERT INTO trend_acknowledgments (bunnyId, weightId, grams, acknowledgedAt) " +
            "VALUES ('bunny-1', 'weight-1', 2500, 1700000000000)",
    )

    // One tray, two bunnies, one photo path on both rows — a shared observation as ADR-0008 stores it.
    for ((id, bunnyId) in listOf("observation-1" to "bunny-1", "observation-2" to "bunny-2")) {
        execSQL(
            "INSERT INTO observations " +
                "(id, bunnyId, groupId, recordedAt, createdAt, droppingsAmount, cecotropes, " +
                "trayPhotoPath, appetite, mood, activity, water, note, symptomsChecked) " +
                "VALUES ('$id', '$bunnyId', 'group-1', 1700000000000, 1700000000000, 'FEW', " +
                "'LEFT_UNEATEN', 'observations/tray.jpg', 'REDUCED', 'SUBDUED', NULL, NULL, " +
                "'Hunched', 1)",
        )
    }
    // A solo observation with no photo, so absence survives as absence.
    execSQL(
        "INSERT INTO observations " +
            "(id, bunnyId, groupId, recordedAt, createdAt, droppingsAmount, cecotropes, " +
            "trayPhotoPath, appetite, mood, activity, water, note, symptomsChecked) " +
            "VALUES ('observation-3', 'bunny-1', NULL, 1700000100000, 1700000100000, NULL, " +
            "NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)",
    )

    execSQL("INSERT INTO symptoms (id, `key`, label, hiddenAt) VALUES ('symptom-1', 'head_tilt', NULL, NULL)")
    execSQL("INSERT INTO symptoms (id, `key`, label, hiddenAt) VALUES ('symptom-2', NULL, 'Chewing the rug', NULL)")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-1', 'symptom-1')")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-1', 'symptom-2')")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-3', 'symptom-1')")

    // Two values on one observation and one on another: multi-valued, and spread across rows.
    execSQL("INSERT INTO observation_droppings_appearance (observationId, value) VALUES ('observation-1', 'SOFT')")
    execSQL("INSERT INTO observation_droppings_appearance (observationId, value) VALUES ('observation-1', 'MUCUS')")
    execSQL("INSERT INTO observation_droppings_appearance (observationId, value) VALUES ('observation-3', 'ROUND')")
    execSQL("INSERT INTO observation_droppings_sizes (observationId, value) VALUES ('observation-1', 'SMALL')")
    execSQL("INSERT INTO observation_droppings_sizes (observationId, value) VALUES ('observation-1', 'NORMAL')")

    execSQL(
        "INSERT INTO photos (id, bunnyId, path, caption, capturedAt, createdAt) " +
            "VALUES ('photo-1', 'bunny-1', 'photos/p.jpg', 'Loafing', 1700000000000, 1700000000000)",
    )
    execSQL(
        "INSERT INTO care_reminders " +
            "(id, bunnyId, label, type, intervalCount, intervalUnit, firstDueOn, " +
            "notifiedForDueOn, createdAt, calendarHandedOffAt) " +
            "VALUES ('care-1', 'bunny-1', NULL, 'NAIL_TRIM', 6, 'WEEK', 20000, NULL, 1700000000000, NULL)",
    )
    execSQL(
        "INSERT INTO care_events (id, reminderId, completedOn, note, createdAt) " +
            "VALUES ('care-event-1', 'care-1', 20000, NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO watches (bunnyId, startedAt, endsAt, lastNaggedOn) " +
            "VALUES ('bunny-1', 1700000000000, 1700600000000, 20000)",
    )
    execSQL(
        "INSERT INTO vets (id, name, clinic, phone, notes, createdAt) " +
            "VALUES ('vet-1', 'Dr Kowalska', 'Klinika Ada', '+48 22 000 00 00', NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO visits (id, bunnyId, vetId, visitedOn, reason, notes, createdAt) " +
            "VALUES ('visit-1', 'bunny-1', 'vet-1', 20600, 'Molar check', NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO medication_courses " +
            "(id, bunnyId, name, doseAmount, startOn, endOn, notes, remindersEnabled, createdAt) " +
            "VALUES ('course-1', 'bunny-1', 'Metacam', '0.3 ml', 20600, NULL, NULL, 1, 1700000000000)",
    )
    execSQL("INSERT INTO medication_times (id, courseId, time) VALUES ('time-1', 'course-1', 28800)")
    execSQL(
        "INSERT INTO doses (id, courseId, scheduledOn, scheduledTime, recordedAt, status, note) " +
            "VALUES ('dose-1', 'course-1', 20600, 28800, 1700000000000, 'GIVEN', NULL)",
    )
    execSQL(
        "INSERT INTO documents (id, bunnyId, visitId, title, capturedAt, createdAt) " +
            "VALUES ('document-1', 'bunny-1', 'visit-1', 'Dental x-ray', NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO document_pages (id, documentId, path, position) " +
            "VALUES ('page-1', 'document-1', 'documents/a.jpg', 0)",
    )
}

/**
 * Every schema-7 table read back, plus the values the rebuild is most likely to mangle quietly.
 *
 * The `observations` reads are the load-bearing ones: an `INSERT … SELECT` across two shapes that
 * differ by a column in the *middle* is exactly the case a positional copy shifts silently, and a note
 * reading back as a mood is not something a row count would catch.
 */
private fun SupportSQLiteDatabase.assertSchemaSevenDataSurvived() {
    for (table in listOf(
        "fluffles",
        "bunnies",
        "weights",
        "trend_acknowledgments",
        "observations",
        "symptoms",
        "observation_symptoms",
        "observation_droppings_appearance",
        "observation_droppings_sizes",
        "photos",
        "care_reminders",
        "care_events",
        "watches",
        "vets",
        "visits",
        "medication_courses",
        "medication_times",
        "doses",
        "documents",
        "document_pages",
    )) {
        assertTrue("$table lost its row in the migration", countOf(table) >= 1)
    }

    assertEquals(3, countOf("observations"))
    query(
        "SELECT bunnyId, groupId, recordedAt, createdAt, droppingsAmount, cecotropes, appetite, " +
            "mood, activity, water, note, symptomsChecked FROM observations WHERE id = 'observation-1'",
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("bunny-1", cursor.getString(0))
        assertEquals("a shared observation must keep its group id", "group-1", cursor.getString(1))
        assertEquals(1700000000000L, cursor.getLong(2))
        assertEquals(1700000000000L, cursor.getLong(3))
        assertEquals("FEW", cursor.getString(4))
        assertEquals("LEFT_UNEATEN", cursor.getString(5))
        assertEquals("REDUCED", cursor.getString(6))
        assertEquals("SUBDUED", cursor.getString(7))
        assertTrue(cursor.isNull(8))
        assertTrue(cursor.isNull(9))
        assertEquals("Hunched", cursor.getString(10))
        assertEquals(1, cursor.getInt(11))
    }
    query("SELECT groupId FROM observations WHERE id = 'observation-3'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue("a solo observation must not gain a group id", cursor.isNull(0))
    }

    query("SELECT name, birthDate, fluffleId FROM bunnies WHERE id = 'bunny-1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("Thumper", cursor.getString(0))
        assertEquals(18000L, cursor.getLong(1))
        assertEquals("fluffle-1", cursor.getString(2))
    }
    query("SELECT grams FROM weights WHERE id = 'weight-1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(2500, cursor.getInt(0))
    }
}

private fun SupportSQLiteDatabase.countOf(table: String): Int =
    query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

private fun SupportSQLiteDatabase.countWhere(
    table: String,
    predicate: String,
): Int =
    query("SELECT COUNT(*) FROM $table WHERE $predicate").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

/** Whether a table exists at all — how the staging tables are proved gone and `events` proved present. */
private fun SupportSQLiteDatabase.tablesNamed(name: String): Int =
    query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

private fun SupportSQLiteDatabase.valuesIn(
    table: String,
    observationId: String,
): List<String> =
    query("SELECT value FROM $table WHERE observationId = '$observationId' ORDER BY value").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

private fun SupportSQLiteDatabase.symptomIdsOf(observationId: String): List<String> =
    query(
        "SELECT symptomId FROM observation_symptoms WHERE observationId = '$observationId' ORDER BY symptomId",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

private fun SupportSQLiteDatabase.photoPathsOf(observationId: String): List<String> =
    query(
        "SELECT path FROM observation_photos WHERE observationId = '$observationId' ORDER BY position",
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
