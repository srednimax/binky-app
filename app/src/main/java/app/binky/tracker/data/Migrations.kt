package app.binky.tracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The **first hand-written migration this project has ever had** (ADR-0007, ADR-0023).
 *
 * Versions 1 through 4 were reached by wiping, which was free while the model was unsettled and
 * nothing but test data existed. It stopped being free at 1.0: the app is installed from Play on a
 * phone holding real bunny history, so version 5 arrives by carrying the data forward.
 *
 * **The SQL is a transcription of `schemas/5.json`, not a paraphrase of the entities.** Room compares
 * the migrated database against that JSON and fails on any difference — a missing index, a column
 * declared `TEXT` where the entity says `INTEGER`, a foreign key with a different `ON DELETE`. So the
 * statements below are copied from the generated schema rather than typed from memory, and when the
 * shape churns across the rest of Phase 4 they are re-copied rather than patched.
 *
 * Phase 4 adds only tables, which is the cheapest migration there is: nothing existing is read,
 * rewritten or dropped, so no owner's data is touched at all. That is worth stating because it will
 * not be true of the next one, and the test that proves it — reading **every table's rows back**
 * after migrating, rather than merely asserting nothing threw — is written to survive that.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `care_reminders` (" +
                    "`id` TEXT NOT NULL, " +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`label` TEXT, " +
                    "`type` TEXT, " +
                    "`intervalCount` INTEGER NOT NULL, " +
                    "`intervalUnit` TEXT NOT NULL, " +
                    "`firstDueOn` INTEGER NOT NULL, " +
                    "`notifiedForDueOn` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`calendarHandedOffAt` INTEGER, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_reminders_bunnyId` " +
                    "ON `care_reminders` (`bunnyId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `care_events` (" +
                    "`id` TEXT NOT NULL, " +
                    "`reminderId` TEXT NOT NULL, " +
                    "`completedOn` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`reminderId`) REFERENCES `care_reminders`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_care_events_reminderId_completedOn` " +
                    "ON `care_events` (`reminderId`, `completedOn`)",
            )
            // 4d's table, added to this migration rather than to a sixth version — the
            // pending-migration rule doing exactly what ADR-0007 grants it for. No index on
            // `bunnyId`: it is the primary key, so SQLite has already indexed it.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `watches` (" +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, " +
                    "`endsAt` INTEGER NOT NULL, " +
                    "`lastNaggedOn` INTEGER, " +
                    "PRIMARY KEY(`bunnyId`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

/**
 * Every migration this app has, in one array so `buildBunnyDatabase` and the instrumented tests
 * register the same set — a test that listed its own would be proving a configuration nothing ships.
 */
val BUNNY_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5)
