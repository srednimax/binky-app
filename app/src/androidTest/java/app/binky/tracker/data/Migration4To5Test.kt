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
 * The **first hand-written migration this project has ever had**, and the test that makes it more
 * than a hope (ADR-0007, ADR-0023).
 *
 * The assertion is **data survival, not that nothing threw**. A migration that silently dropped a
 * table would pass "it did not crash" and lose an owner's history, so every table gets rows before
 * the migration and is read back after it.
 *
 * Two halves, deliberately:
 *
 * 1. [migratesEveryTablesRowsForward] runs `MIGRATION_4_5` through Room's own validator, which
 *    compares the result against `5.json` and fails on any difference — a missing index, a column
 *    with the wrong affinity, a foreign key with a different `ON DELETE`. That is what keeps the
 *    hand-written SQL honest against the entities.
 * 2. [aReleaseShapedOpenOfASchemaFourFileSucceeds] opens the same file through the app's **real**
 *    builder with the destructive fallback off. The first half proves the migration is correct; only
 *    this one proves it is *wired up* — a migration that exists but is never registered passes the
 *    first test and wipes a phone.
 *
 * Instrumented rather than JVM because `MigrationTestHelper` needs a real SQLite and reads the
 * exported schema from the test APK's assets. This project has no Robolectric and is not adding one
 * to hold a single assertion; what makes the guard always-on is CI's per-pull-request matrix.
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            BunnyDatabase::class.java,
        )

    @Test
    fun migratesEveryTablesRowsForward() {
        helper.createDatabase(TEST_DB, 4).use { it.seedSchemaFour() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, *BUNNY_MIGRATIONS)

        migrated.assertSchemaFourDataSurvived()
        // The point of the whole version bump: the new tables exist and take rows.
        migrated.execSQL(
            "INSERT INTO care_reminders " +
                "(id, bunnyId, label, type, intervalCount, intervalUnit, firstDueOn, " +
                "notifiedForDueOn, createdAt, calendarHandedOffAt) " +
                "VALUES ('care-1', 'bunny-1', NULL, 'NAIL_TRIM', 6, 'WEEK', 20000, NULL, 1700000000000, NULL)",
        )
        migrated.execSQL(
            "INSERT INTO care_events (id, reminderId, completedOn, note, createdAt) " +
                "VALUES ('event-1', 'care-1', 20000, NULL, 1700000000000)",
        )
        migrated.execSQL(
            "INSERT INTO watches (bunnyId, startedAt, endsAt, lastNaggedOn) " +
                "VALUES ('bunny-1', 1700000000000, 1700600000000, 20000)",
        )
        assertEquals(1, migrated.countOf("care_reminders"))
        assertEquals(1, migrated.countOf("care_events"))
        assertEquals(1, migrated.countOf("watches"))
    }

    /**
     * A **release-shaped** open: the app's own builder, with `allowDestructiveMigration = false`.
     *
     * 3c added that parameter for 3d's staged restore, and this is the other thing it buys — the
     * release path is testable without a release build. Without a registered migration Room throws
     * "A migration from 4 to 5 was required but not found" here, which is exactly the launch crash
     * ADR-0023 chose over a silent wipe, and exactly what must never reach a phone.
     */
    @Test
    fun aReleaseShapedOpenOfASchemaFourFileSucceeds() {
        helper.createDatabase(TEST_DB, 4).use { it.seedSchemaFour() }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = buildBunnyDatabase(context, TEST_DB, allowDestructiveMigration = false)
        try {
            // Opening is what runs the migration — Room's builder only assembles the object.
            database.openHelper.writableDatabase.assertSchemaFourDataSurvived()
            assertEquals(BUNNY_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}

/**
 * A row in every table version 4 has, in foreign-key order.
 *
 * Fabricated and minimal, but *complete* — the migration's promise is about all of it, and a fixture
 * that covered six tables of eight would leave the other two proven by nobody. The values are
 * literal SQL rather than entities on purpose: entities describe version 5, and a test that built
 * its "old" database out of today's classes would be testing the migration against itself.
 */
private fun SupportSQLiteDatabase.seedSchemaFour() {
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
}

/** Every table read back, plus the two values most likely to be quietly mangled by a rewrite. */
private fun SupportSQLiteDatabase.assertSchemaFourDataSurvived() {
    for (table in listOf(
        "fluffles",
        "bunnies",
        "weights",
        "trend_acknowledgments",
        "observations",
        "symptoms",
        "observation_symptoms",
        "photos",
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
}

private fun SupportSQLiteDatabase.countOf(table: String): Int =
    query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
