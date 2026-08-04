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
 * **Phase 5's whole schema, in one migration** (PLAN 5b, ADR-0007).
 *
 * Seven new tables and — for the first time in this project — a **column added to a table that has
 * already shipped with real data in it**. That is the part worth reading twice: `MIGRATION_4_5` only
 * created things, so no owner's row was so much as read. This one touches `weights`.
 *
 * **Five of the seven tables are created here and left empty**: medications are 5d's and documents are
 * 5g's, and neither has a screen until then. Creating them now costs one `execSQL` each and buys a
 * single migration to test instead of six, none of which would correspond to a shape any shipped
 * build ever held.
 *
 * **The `weights` change is two statements and the second one is the load-bearing one.** SQLite's
 * `ADD COLUMN` cannot carry a `UNIQUE` constraint, and it accepts a foreign-keyed column at all only
 * because the default is null — so the unique index is a separate `CREATE UNIQUE INDEX`, and it is the
 * statement ADR-0017's "one row, never a copy" claim actually rests on. The `ALTER` on its own
 * enforces nothing.
 *
 * **The SQL is a transcription of `schemas/6.json`, not a paraphrase of the entities** — same rule as
 * above, and the instrumented `runMigrationsAndValidate` is what keeps it honest: Room compares the
 * migrated database against that JSON and fails on a missing index, a wrong affinity, a foreign key
 * with a different `ON DELETE`. As the shape churns across 5b–5g both are regenerated together, and
 * neither is patched by hand.
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Vets before visits, visits before everything that references them: the order is the
            // foreign-key graph, so the statements read top-down the way the schema does.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vets` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`clinic` TEXT, " +
                    "`phone` TEXT, " +
                    "`notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `visits` (" +
                    "`id` TEXT NOT NULL, " +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`vetId` TEXT, " +
                    "`visitedOn` INTEGER NOT NULL, " +
                    "`reason` TEXT NOT NULL, " +
                    "`notes` TEXT, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`vetId`) REFERENCES `vets`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_visits_bunnyId_visitedOn` " +
                    "ON `visits` (`bunnyId`, `visitedOn`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_visits_vetId` ON `visits` (`vetId`)",
            )

            // 5d's three tables, empty until then.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medication_courses` (" +
                    "`id` TEXT NOT NULL, " +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`doseAmount` TEXT NOT NULL, " +
                    "`startOn` INTEGER NOT NULL, " +
                    "`endOn` INTEGER, " +
                    "`notes` TEXT, " +
                    "`remindersEnabled` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_medication_courses_bunnyId` " +
                    "ON `medication_courses` (`bunnyId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `medication_times` (" +
                    "`id` TEXT NOT NULL, " +
                    "`courseId` TEXT NOT NULL, " +
                    "`time` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`courseId`) REFERENCES `medication_courses`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_times_courseId_time` " +
                    "ON `medication_times` (`courseId`, `time`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `doses` (" +
                    "`id` TEXT NOT NULL, " +
                    "`courseId` TEXT NOT NULL, " +
                    "`scheduledOn` INTEGER, " +
                    "`scheduledTime` INTEGER, " +
                    "`recordedAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`note` TEXT, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`courseId`) REFERENCES `medication_courses`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_doses_courseId_scheduledOn_scheduledTime` " +
                    "ON `doses` (`courseId`, `scheduledOn`, `scheduledTime`)",
            )

            // 5g's two, likewise.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `documents` (" +
                    "`id` TEXT NOT NULL, " +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`visitId` TEXT, " +
                    "`title` TEXT NOT NULL, " +
                    "`capturedAt` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`visitId`) REFERENCES `visits`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_documents_bunnyId` ON `documents` (`bunnyId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_documents_visitId` ON `documents` (`visitId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `document_pages` (" +
                    "`id` TEXT NOT NULL, " +
                    "`documentId` TEXT NOT NULL, " +
                    "`path` TEXT NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_document_pages_documentId_position` " +
                    "ON `document_pages` (`documentId`, `position`)",
            )

            // The one statement in this migration that touches a table holding an owner's data.
            // `ADD COLUMN` with a foreign key is accepted by SQLite **only** because the default is
            // null; it cannot carry `UNIQUE`, which is why the index below is its own statement.
            db.execSQL(
                "ALTER TABLE `weights` ADD COLUMN `visitId` TEXT " +
                    "REFERENCES `visits`(`id`) ON DELETE SET NULL DEFAULT NULL",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_weights_visitId` ON `weights` (`visitId`)",
            )
        }
    }

/**
 * Every migration this app has, in one array so `buildBunnyDatabase` and the instrumented tests
 * register the same set — a test that listed its own would be proving a configuration nothing ships.
 */
val BUNNY_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6)
