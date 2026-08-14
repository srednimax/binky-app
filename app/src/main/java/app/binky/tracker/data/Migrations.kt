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
 * **The first migration in this project that rewrites a table full of an owner's history** (ADR-0029,
 * PLAN 7.5 §7).
 *
 * `MIGRATION_4_5` only created tables. `MIGRATION_5_6` added a column to `weights`. This one takes
 * two columns *off* `observations` — the droppings appearance and size go multi-valued into join
 * tables — and adds `trayPhotoPath` in their place.
 *
 * **Why a rebuild rather than three `ALTER`s.** SQLite gained `ALTER TABLE … DROP COLUMN` in 3.35 and
 * `minSdk` is 26, so it is not available; and Room validates the migrated database against
 * `schemas/7.json`, so leaving the two columns behind as vestigial is not available either. That
 * leaves create-copy-drop-rename.
 *
 * **The trap, and it is the reason this migration is written out at length.**
 * `DROP TABLE observations` performs an implicit delete of every row, which fires
 * `observation_symptoms`' `ON DELETE CASCADE`. Foreign keys are enforced on the connection and
 * `PRAGMA foreign_keys = OFF` is a no-op inside the transaction Room has already begun, so the
 * cascade cannot be switched off — it has to be *survived*. **`runMigrationsAndValidate` would pass
 * happily on the wreckage**: it compares the schema, and a database whose every symptom tick has been
 * cascaded away has exactly the right schema. So the instrumented test counts rows, and the recipe
 * below stages the links and puts them back.
 *
 * `observation_symptoms` is **dropped and recreated** rather than merely emptied and refilled, which
 * is one step more than ADR-0029's recipe asks for and buys the rename a schema with no dangling
 * reference in it at all: at the moment `observations_new` takes the name `observations`, nothing
 * anywhere references that name. SQLite 3.25 changed what `ALTER TABLE … RENAME` does to other
 * objects' definitions and the app spans API 26 to 36; this is the one migration that must not be
 * clever about it.
 *
 * **The two old values migrate as themselves.** `DroppingsForm` was renamed to `DroppingsAppearance`
 * and gained five values, and `DroppingsSize` gained none — but only value *names* are ever stored
 * (house rule), and every old name is still a member. So the copy is a copy, with no translation
 * table to get wrong, and a row that recorded `SOFT` in 1.4 goes on meaning `SOFT`.
 *
 * **The SQL is a transcription of `schemas/7.json`, not a paraphrase of the entities** — same rule as
 * the two migrations above, and re-transcribed rather than patched if the shape churns before Phase
 * 7.5 closes (ADR-0007's pending-migration rule).
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Stage what the rebuild is about to destroy. `CREATE TABLE … AS SELECT` makes a
            //    constraint-free copy, which is the point: a staged table with the original's foreign
            //    keys on it would cascade away with the original.
            db.execSQL(
                "CREATE TABLE `observation_symptoms_backup` AS " +
                    "SELECT `observationId`, `symptomId` FROM `observation_symptoms`",
            )
            db.execSQL(
                "CREATE TABLE `droppings_backup` AS " +
                    "SELECT `id`, `droppingsSize`, `droppingsForm` FROM `observations`",
            )

            // 2. Take the child table out of the schema entirely, so the rename in step 4 happens
            //    with nothing referencing the name it is about to claim.
            db.execSQL("DROP TABLE `observation_symptoms`")

            // 3. The new shape, then the rows, then the old table.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `observations_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`bunnyId` TEXT NOT NULL, " +
                    "`groupId` TEXT, " +
                    "`recordedAt` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`droppingsAmount` TEXT, " +
                    "`cecotropes` TEXT, " +
                    "`trayPhotoPath` TEXT, " +
                    "`appetite` TEXT, " +
                    "`mood` TEXT, " +
                    "`activity` TEXT, " +
                    "`water` TEXT, " +
                    "`note` TEXT, " +
                    "`symptomsChecked` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`bunnyId`) REFERENCES `bunnies`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            // Columns named on both sides rather than `SELECT *`: the shapes differ by three columns,
            // which is exactly the case a positional copy gets silently wrong.
            db.execSQL(
                "INSERT INTO `observations_new` (" +
                    "`id`, `bunnyId`, `groupId`, `recordedAt`, `createdAt`, `droppingsAmount`, " +
                    "`cecotropes`, `trayPhotoPath`, `appetite`, `mood`, `activity`, `water`, " +
                    "`note`, `symptomsChecked`) " +
                    "SELECT `id`, `bunnyId`, `groupId`, `recordedAt`, `createdAt`, `droppingsAmount`, " +
                    "`cecotropes`, NULL, `appetite`, `mood`, `activity`, `water`, " +
                    "`note`, `symptomsChecked` FROM `observations`",
            )
            db.execSQL("DROP TABLE `observations`")

            // 4. The rename, and the two indices the old table carried.
            db.execSQL("ALTER TABLE `observations_new` RENAME TO `observations`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_observations_bunnyId_recordedAt` " +
                    "ON `observations` (`bunnyId`, `recordedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_observations_groupId` ON `observations` (`groupId`)",
            )

            // 5. Put the symptom links back, exactly as they were. This is the step whose absence
            //    `runMigrationsAndValidate` cannot see.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `observation_symptoms` (" +
                    "`observationId` TEXT NOT NULL, " +
                    "`symptomId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`observationId`, `symptomId`), " +
                    "FOREIGN KEY(`observationId`) REFERENCES `observations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`symptomId`) REFERENCES `symptoms`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_observation_symptoms_symptomId` " +
                    "ON `observation_symptoms` (`symptomId`)",
            )
            db.execSQL(
                "INSERT INTO `observation_symptoms` (`observationId`, `symptomId`) " +
                    "SELECT `observationId`, `symptomId` FROM `observation_symptoms_backup`",
            )

            // 6. The two new tables, created after the rename so their foreign key names a table that
            //    exists, and filled one row per non-null old value.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `observation_droppings_appearance` (" +
                    "`observationId` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "PRIMARY KEY(`observationId`, `value`), " +
                    "FOREIGN KEY(`observationId`) REFERENCES `observations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `observation_droppings_sizes` (" +
                    "`observationId` TEXT NOT NULL, " +
                    "`value` TEXT NOT NULL, " +
                    "PRIMARY KEY(`observationId`, `value`), " +
                    "FOREIGN KEY(`observationId`) REFERENCES `observations`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO `observation_droppings_appearance` (`observationId`, `value`) " +
                    "SELECT `id`, `droppingsForm` FROM `droppings_backup` WHERE `droppingsForm` IS NOT NULL",
            )
            db.execSQL(
                "INSERT INTO `observation_droppings_sizes` (`observationId`, `value`) " +
                    "SELECT `id`, `droppingsSize` FROM `droppings_backup` WHERE `droppingsSize` IS NOT NULL",
            )

            // 7. The staging tables are not part of any schema and must not survive the migration.
            db.execSQL("DROP TABLE `observation_symptoms_backup`")
            db.execSQL("DROP TABLE `droppings_backup`")
        }
    }

/**
 * Every migration this app has, in one array so `buildBunnyDatabase` and the instrumented tests
 * register the same set — a test that listed its own would be proving a configuration nothing ships.
 */
val BUNNY_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
