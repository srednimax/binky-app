package app.binky.tracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `MIGRATION_5_6`, and **the first migration in this project that touches a table an owner already
 * has data in** (PLAN 5b, ADR-0007).
 *
 * `MIGRATION_4_5` only created tables, so "nothing was lost" was nearly free. This one runs
 * `ALTER TABLE weights ADD COLUMN`, on the one table the app makes a safety claim about — so the
 * weights assertions below are not ceremony, and they check the *values*, not just the row count.
 *
 * Four claims, in the order they can fail:
 *
 * 1. [migratesEveryTablesRowsForward] — the schema-5 rows all arrive, the seven new tables exist and
 *    take rows, and Room's own validator agrees the result matches `6.json`. That last part is what
 *    keeps the hand-transcribed SQL honest: a missing index or a foreign key with a different
 *    `ON DELETE` fails here rather than on a phone.
 * 2. [existingWeighingsArriveUnclaimedByAnyVisit] — the added column defaults to null, so no shipped
 *    weighing comes out of the migration claiming a visit that never happened.
 * 3. [theUniqueIndexOnVisitIdSurvivesTheMigration] — ADR-0017's "one row, never a copy" is the
 *    separate `CREATE UNIQUE INDEX`, not the `ALTER`, and this is the assertion that it ran. Room's
 *    validator would notice the index missing; only this notices it not *enforcing*.
 * 4. [aReleaseShapedOpenOfASchemaFiveFileSucceeds] — the migration is registered, proven through the
 *    app's real builder. A migration that exists but is never wired up passes the first three tests
 *    and crashes every 1.1.0 phone on launch.
 *
 * The 4 → 5 → 6 chain is `Migration4To5Test`'s release-shaped open, which now runs both migrations
 * end to end: that is the skipped-version upgrade, asserted per pull request instead of once by hand.
 */
@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BunnyDatabase::class.java,
        )

    @Test
    fun migratesEveryTablesRowsForward() {
        helper.createDatabase(TEST_DB, 5).use { it.seedSchemaFive() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, *BUNNY_MIGRATIONS)

        migrated.assertSchemaFiveDataSurvived()

        // The point of the version bump: all seven tables exist and take rows, in foreign-key order.
        migrated.execSQL(
            "INSERT INTO vets (id, name, clinic, phone, notes, createdAt) " +
                "VALUES ('vet-1', 'Dr Kowalska', 'Klinika Ada', '+48 22 000 00 00', NULL, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO visits (id, bunnyId, vetId, visitedOn, reason, notes, createdAt) " +
                "VALUES ('visit-1', 'bunny-1', 'vet-1', 20600, 'Molar check', NULL, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO medication_courses " +
                "(id, bunnyId, name, doseAmount, startOn, endOn, notes, remindersEnabled, createdAt) " +
                "VALUES ('course-1', 'bunny-1', 'Metacam', '0.3 ml', 20600, NULL, NULL, 1, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO medication_times (id, courseId, time) VALUES ('time-1', 'course-1', 28800)",
        )
        migrated.execSQL(
            "INSERT INTO doses (id, courseId, scheduledOn, scheduledTime, recordedAt, status, note) " +
                "VALUES ('dose-1', 'course-1', 20600, 28800, 1700000000000, 'GIVEN', NULL)",
        )
        migrated.execSQL(
            "INSERT INTO documents (id, bunnyId, visitId, title, capturedAt, createdAt) " +
                "VALUES ('document-1', 'bunny-1', 'visit-1', 'Dental x-ray', NULL, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO document_pages (id, documentId, path, position) " +
                "VALUES ('page-1', 'document-1', 'documents/a.jpg', 0)",
        )
        for (table in NEW_TABLES) {
            assertEquals("$table did not take its row", 1, migrated.countOf(table))
        }
    }

    /**
     * The added column's default, which is the whole reason SQLite accepts a foreign-keyed
     * `ADD COLUMN` at all — and which decides what every already-shipped weighing means afterwards.
     * A non-null default here would silently tag 43 sample weighings as vet-recorded.
     */
    @Test
    fun existingWeighingsArriveUnclaimedByAnyVisit() {
        helper.createDatabase(TEST_DB, 5).use { it.seedSchemaFive() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, *BUNNY_MIGRATIONS)

        migrated.query("SELECT grams, visitId FROM weights WHERE id = 'weight-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2500, cursor.getInt(0))
            assertTrue("a migrated weighing must claim no visit", cursor.isNull(1))
        }
    }

    /** ADR-0017's claim, enforced by the schema rather than by the editor being careful. */
    @Test
    fun theUniqueIndexOnVisitIdSurvivesTheMigration() {
        helper.createDatabase(TEST_DB, 5).use { it.seedSchemaFive() }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, *BUNNY_MIGRATIONS)
        migrated.execSQL(
            "INSERT INTO visits (id, bunnyId, vetId, visitedOn, reason, notes, createdAt) " +
                "VALUES ('visit-1', 'bunny-1', NULL, 20600, 'Molar check', NULL, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
                "VALUES ('weight-2', 'bunny-1', 2380, 1700000000000, 1700000000000, 'visit-1')",
        )

        assertThrows(SQLiteConstraintException::class.java) {
            migrated.execSQL(
                "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
                    "VALUES ('weight-3', 'bunny-1', 2390, 1700000100000, 1700000100000, 'visit-1')",
            )
        }

        // NULLs are distinct in SQLite, so the constraint leaves manual weighings alone — asserted,
        // because a plain `UNIQUE` that also caught those would break every ordinary entry.
        migrated.execSQL(
            "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
                "VALUES ('weight-4', 'bunny-1', 2400, 1700000200000, 1700000200000, NULL)",
        )
        migrated.execSQL(
            "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt, visitId) " +
                "VALUES ('weight-5', 'bunny-1', 2410, 1700000300000, 1700000300000, NULL)",
        )
        // Four rows, not five: the migrated one, the visit's, and two manual — `weight-3` is the
        // one the index refused.
        assertEquals(4, migrated.countOf("weights"))
    }

    /**
     * A **release-shaped** open of a schema-5 file: the app's own builder with the destructive
     * fallback off, which is what 1.1.0's owners get on upgrade day.
     */
    @Test
    fun aReleaseShapedOpenOfASchemaFiveFileSucceeds() {
        helper.createDatabase(TEST_DB, 5).use { it.seedSchemaFive() }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = buildBunnyDatabase(context, TEST_DB, allowDestructiveMigration = false)
        try {
            // Opening is what runs the migration — the builder only assembles the object.
            database.openHelper.writableDatabase.assertSchemaFiveDataSurvived()
            assertEquals(BUNNY_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-5-6-test.db"

        val NEW_TABLES =
            listOf(
                "vets",
                "visits",
                "medication_courses",
                "medication_times",
                "doses",
                "documents",
                "document_pages",
            )
    }
}

/**
 * A row in **every** table version 5 has, in foreign-key order — the eight version 4 carried plus
 * Phase 4's three.
 *
 * Literal SQL rather than entities, for the reason `Migration4To5Test` gives: the entities describe
 * version *6*, and a test that built its "old" database out of today's classes would be testing the
 * migration against itself. `weights` here has no `visitId` column at all, which is the point.
 */
private fun SupportSQLiteDatabase.seedSchemaFive() {
    execSQL("INSERT INTO fluffles (id, name) VALUES ('fluffle-1', 'The pair')")
    execSQL(
        "INSERT INTO bunnies " +
            "(id, name, avatarPath, birthDate, birthDateApproximate, sex, neutered, breed, colour, " +
            "fluffleId, archivedAt, createdAt) " +
            "VALUES ('bunny-1', 'Thumper', 'avatars/a.jpg', 18000, 0, 'FEMALE', 'NEUTERED', " +
            "'Mini Lop', 'Sooty', 'fluffle-1', NULL, 1700000000000)",
    )
    execSQL(
        "INSERT INTO weights (id, bunnyId, grams, recordedAt, createdAt) " +
            "VALUES ('weight-1', 'bunny-1', 2500, 1700000000000, 1700000000000)",
    )
    execSQL(
        "INSERT INTO trend_acknowledgments (bunnyId, weightId, grams, acknowledgedAt) " +
            "VALUES ('bunny-1', 'weight-1', 2500, 1700000000000)",
    )
    execSQL(
        "INSERT INTO observations " +
            "(id, bunnyId, groupId, recordedAt, createdAt, droppingsAmount, droppingsSize, " +
            "droppingsForm, cecotropes, appetite, mood, activity, water, note, symptomsChecked) " +
            "VALUES ('observation-1', 'bunny-1', NULL, 1700000000000, 1700000000000, 'NORMAL', " +
            "NULL, NULL, NULL, 'NORMAL', NULL, NULL, NULL, 'Ate all the greens', 1)",
    )
    execSQL("INSERT INTO symptoms (id, `key`, label, hiddenAt) VALUES ('symptom-1', NULL, 'Chewing the rug', NULL)")
    execSQL("INSERT INTO observation_symptoms (observationId, symptomId) VALUES ('observation-1', 'symptom-1')")
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
}

/** Every schema-5 table read back, plus the values most likely to be quietly mangled by a rewrite. */
private fun SupportSQLiteDatabase.assertSchemaFiveDataSurvived() {
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
    )) {
        assertTrue("$table lost its row in the migration", countOf(table) >= 1)
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
    // Phase 4's tables carry `LocalDate`s as epoch days; a rewrite that lost the affinity would
    // read back as something other than the integer that went in.
    query("SELECT firstDueOn, intervalUnit FROM care_reminders WHERE id = 'care-1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(20000L, cursor.getLong(0))
        assertEquals("WEEK", cursor.getString(1))
    }
    query("SELECT notifiedForDueOn FROM care_reminders WHERE id = 'care-1'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertNull(cursor.getString(0))
    }
}

private fun SupportSQLiteDatabase.countOf(table: String): Int =
    query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
