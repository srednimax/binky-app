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
 * `MIGRATION_6_7`, and **the first migration in this project that rewrites a table full of an owner's
 * history** (ADR-0029, PLAN 7.5 §7).
 *
 * `MIGRATION_4_5` only created tables. `MIGRATION_5_6` added a column. This one drops two columns off
 * `observations`, which at `minSdk` 26 means create-copy-drop-rename — and `DROP TABLE observations`
 * performs an implicit delete of every row, which fires `observation_symptoms`' `ON DELETE CASCADE`.
 *
 * **The reason this test asserts rows rather than shape.** `runMigrationsAndValidate` compares the
 * migrated database against `7.json` and passes; a database whose every symptom tick has been
 * cascaded away has *exactly* the right schema. So the assertion that matters most here —
 * [everySymptomTickSurvivesTheRebuild] — is one Room's own validator structurally cannot make, and
 * without it the phase's riskiest migration would have shipped green.
 *
 * Four claims, in the order they can fail:
 *
 * 1. [migratesEveryTablesRowsForward] — every schema-6 row arrives, the two join tables exist and
 *    take rows, and Room agrees the result matches `7.json`.
 * 2. [theSingleDroppingsValueBecomesOneRowInEachJoinTable] — the whole point of the version bump. A
 *    migration that quietly dropped the old value would erase exactly the history ADR-0023 stopped
 *    the database being disposable for, and it would look like a clean upgrade.
 * 3. [everySymptomTickSurvivesTheRebuild] — the cascade, described above.
 * 4. [aReleaseShapedOpenOfASchemaSixFileSucceeds] — the migration is registered, proven through the
 *    app's real builder. A migration that exists but is never wired up passes the first three and
 *    crashes every 1.4 phone on launch.
 */
@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BunnyDatabase::class.java,
        )

    @Test
    fun migratesEveryTablesRowsForward() {
        helper.createDatabase(TEST_DB, 6).use { it.seedSchemaSix() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, *BUNNY_MIGRATIONS)

        migrated.assertSchemaSixDataSurvived()

        // The staging tables are not part of any schema and must not outlive the migration.
        for (staged in listOf("observation_symptoms_backup", "droppings_backup", "observations_new")) {
            assertEquals("$staged should not exist after the migration", 0, migrated.countOfTablesNamed(staged))
        }
    }

    /**
     * The value carried forward, by name, into one row of the join table — and **absence carried
     * forward as absence**.
     *
     * `observation-2` recorded nothing about size or appearance in 1.4, and it must come out of the
     * migration recording nothing: an empty set, not a guess (ADR-0001). The names are asserted
     * literally because `MIGRATION_6_7` copies them verbatim — `DroppingsForm` became
     * `DroppingsAppearance` and gained five values, and that rename was only free because nothing but
     * the value name is ever stored.
     */
    @Test
    fun theSingleDroppingsValueBecomesOneRowInEachJoinTable() {
        helper.createDatabase(TEST_DB, 6).use { it.seedSchemaSix() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, *BUNNY_MIGRATIONS)

        assertEquals(
            listOf("SOFT"),
            migrated.valuesIn("observation_droppings_appearance", "observation-1"),
        )
        assertEquals(
            listOf("SMALL"),
            migrated.valuesIn("observation_droppings_sizes", "observation-1"),
        )

        // The unrecorded observation stays unrecorded — zero rows, which is the join table's spelling
        // of the old column's null.
        assertTrue(migrated.valuesIn("observation_droppings_appearance", "observation-2").isEmpty())
        assertTrue(migrated.valuesIn("observation_droppings_sizes", "observation-2").isEmpty())

        // And exactly two rows in total across both tables: one observation's two values, no more.
        assertEquals(1, migrated.countOf("observation_droppings_appearance"))
        assertEquals(1, migrated.countOf("observation_droppings_sizes"))
    }

    /**
     * The cascade `DROP TABLE observations` fires, survived.
     *
     * Two ticks on two different observations, so a recipe that happened to restore only the first
     * row, or only the links of the row it was looking at, fails here. `PRAGMA foreign_keys = OFF` is
     * a no-op inside the transaction Room has already begun, so the cascade cannot be switched off —
     * only staged around, which is what the migration does.
     */
    @Test
    fun everySymptomTickSurvivesTheRebuild() {
        helper.createDatabase(TEST_DB, 6).use { it.seedSchemaSix() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, *BUNNY_MIGRATIONS)

        assertEquals(3, migrated.countOf("observation_symptoms"))
        assertEquals(
            listOf("symptom-1", "symptom-2"),
            migrated.symptomIdsOf("observation-1"),
        )
        assertEquals(listOf("symptom-1"), migrated.symptomIdsOf("observation-2"))
        // The symptoms themselves were never at risk — asserted anyway, because a recipe that
        // recreated the link table with the wrong `ON DELETE` on the symptom side would only show up
        // as a missing row much later, when a symptom was retired.
        assertEquals(2, migrated.countOf("symptoms"))
    }

    /** No migrated observation comes out claiming a photo nobody took. */
    @Test
    fun migratedObservationsCarryNoTrayPhoto() {
        helper.createDatabase(TEST_DB, 6).use { it.seedSchemaSix() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 7, true, *BUNNY_MIGRATIONS)

        assertEquals(0, migrated.countWhere("observations", "trayPhotoPath IS NOT NULL"))
    }

    /**
     * A **release-shaped** open of a schema-6 file: the app's own builder with the destructive
     * fallback off, which is what 1.4's owners get on upgrade day.
     */
    @Test
    fun aReleaseShapedOpenOfASchemaSixFileSucceeds() {
        helper.createDatabase(TEST_DB, 6).use { it.seedSchemaSix() }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = buildBunnyDatabase(context, TEST_DB, allowDestructiveMigration = false)
        try {
            // Opening is what runs the migration — the builder only assembles the object.
            database.openHelper.writableDatabase.assertSchemaSixDataSurvived()
            assertEquals(BUNNY_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-6-7-test.db"
    }
}

/**
 * A row in **every** table version 6 has, in foreign-key order.
 *
 * Literal SQL rather than entities, for the reason the earlier migration tests give: the entities
 * describe version *7*, and a test that built its "old" database out of today's classes would be
 * testing the migration against itself. `observations` here still has `droppingsSize` and
 * `droppingsForm` columns and no `trayPhotoPath`, which is the point.
 *
 * Two observations and three symptom links, deliberately: one observation that recorded both
 * droppings fields and one that recorded neither, and ticks spread across both rows so a partial
 * restore cannot pass.
 */
private fun SupportSQLiteDatabase.seedSchemaSix() {
    execSQL("INSERT INTO fluffles (id, name) VALUES ('fluffle-1', 'The pair')")
    execSQL(
        "INSERT INTO bunnies " +
            "(id, name, avatarPath, birthDate, birthDateApproximate, sex, neutered, breed, colour, " +
            "fluffleId, archivedAt, createdAt) " +
            "VALUES ('bunny-1', 'Thumper', 'avatars/a.jpg', 18000, 0, 'FEMALE', 'NEUTERED', " +
            "'Mini Lop', 'Sooty', 'fluffle-1', NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
            "VALUES ('weight-1', 'bunny-1', 2500, 1700000000000, 1700000000000, NULL)",
    )
    execSQL(
        "INSERT INTO trend_acknowledgments (bunnyId, weightId, grams, acknowledgedAt) " +
            "VALUES ('bunny-1', 'weight-1', 2500, 1700000000000)",
    )
    execSQL(
        "INSERT INTO observations " +
            "(id, bunnyId, groupId, recordedAt, createdAt, droppingsAmount, droppingsSize, " +
            "droppingsForm, cecotropes, appetite, mood, activity, water, note, symptomsChecked) " +
            "VALUES ('observation-1', 'bunny-1', NULL, 1700000000000, 1700000000000, 'FEW', " +
            "'SMALL', 'SOFT', 'LEFT_UNEATEN', 'REDUCED', 'SUBDUED', NULL, NULL, 'Hunched', 1)",
    )
    execSQL(
        "INSERT INTO observations " +
            "(id, bunnyId, groupId, recordedAt, createdAt, droppingsAmount, droppingsSize, " +
            "droppingsForm, cecotropes, appetite, mood, activity, water, note, symptomsChecked) " +
            "VALUES ('observation-2', 'bunny-1', NULL, 1700000100000, 1700000100000, NULL, " +
            "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)",
    )
    execSQL("INSERT INTO symptoms (id, `key`, label, hiddenAt) VALUES ('symptom-1', 'head_tilt', NULL, NULL)")
    execSQL("INSERT INTO symptoms (id, `key`, label, hiddenAt) VALUES ('symptom-2', NULL, 'Chewing the rug', NULL)")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-1', 'symptom-1')")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-1', 'symptom-2')")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-2', 'symptom-1')")
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
            "VALUES ('event-1', 'care-1', 20000, NULL, 1700000000000)",
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
 * Every schema-6 table read back, plus the values the rebuild is most likely to mangle quietly.
 *
 * The `observations` reads are the load-bearing ones: a positional `INSERT … SELECT` across two
 * shapes that differ by three columns would come out looking plausible and be wrong about which
 * value landed where, and a note reading back as a mood is not something a row count would catch.
 */
private fun SupportSQLiteDatabase.assertSchemaSixDataSurvived() {
    for (table in listOf(
        "fluffles",
        "bunnies",
        "weights",
        "trend_acknowledgments",
        "observations",
        "symptoms",
        "observation_symptoms",
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

    assertEquals(2, countOf("observations"))
    query(
        "SELECT bunnyId, groupId, recordedAt, createdAt, droppingsAmount, cecotropes, appetite, " +
            "mood, activity, water, note, symptomsChecked FROM observations WHERE id = 'observation-1'",
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("bunny-1", cursor.getString(0))
        assertTrue("a solo observation must not gain a group id", cursor.isNull(1))
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

/** Whether a table exists at all — how the staging tables are proved gone. */
private fun SupportSQLiteDatabase.countOfTablesNamed(name: String): Int =
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
