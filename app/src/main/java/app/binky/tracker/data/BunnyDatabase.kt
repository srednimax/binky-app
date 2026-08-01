package app.binky.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.binky.tracker.BuildConfig

/**
 * The schema version this build expects. The wipe guard reads the *file's* version before Room
 * opens it and compares it against this (see `DatabasePreserve.kt`), so the two must stay in step —
 * which is why it is one constant used in both places.
 */
const val BUNNY_SCHEMA_VERSION = 5

/** The database file name, under the app's standard databases directory. */
const val BUNNY_DATABASE_FILE = "bunny.db"

/**
 * Version 4 was **the last planned wipe** (ADR-0007, ADR-0023). Until there a schema change was
 * allowed to destroy the database rather than carry a migration for every field added to a
 * still-unsettled model — never silently: the file is copied aside first, by the guard that runs
 * before this is opened. From 1.0 the fallback below is debug-only, and every schema version that
 * reaches a device carries a tested forward migration.
 *
 * **Version 5 is the first one that does.** 1.0.1 is installed from Play on a phone holding real
 * bunny history, which is exactly the dogfood case ADR-0007 names, so the care tables arrive by
 * [MIGRATION_4_5] and not by a wipe. While Phase 4 is in flight that migration is **rewritten in
 * place** as the shape churns and `5.json` regenerated with it — the version does not climb per
 * checkpoint. What must stay true throughout, and is asserted by test, is that a release-shaped open
 * of a schema-4 file succeeds.
 */
@Database(
    entities = [
        BunnyEntity::class,
        FluffleEntity::class,
        WeightEntity::class,
        TrendAcknowledgmentEntity::class,
        ObservationEntity::class,
        SymptomEntity::class,
        ObservationSymptomEntity::class,
        PhotoEntity::class,
        CareReminderEntity::class,
        CareEventEntity::class,
    ],
    version = BUNNY_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BunnyDatabase : RoomDatabase() {
    abstract fun bunnyDao(): BunnyDao

    abstract fun fluffleDao(): FluffleDao

    abstract fun weightDao(): WeightDao

    abstract fun observationDao(): ObservationDao

    abstract fun symptomDao(): SymptomDao

    abstract fun photoDao(): PhotoDao

    abstract fun careDao(): CareDao
}

/**
 * Whether this build may destroy a database it cannot open (ADR-0023).
 *
 * A forgotten migration used to cost test data. From 1.0 it would delete a real owner's history,
 * so a release build **throws when the file is opened** instead: the app fails to launch, loudly,
 * and lands in Play Console crash vitals. That is a bad day for the owner and the only *recoverable*
 * outcome on offer — a silent wipe is not.
 *
 * A function of the flag rather than a read of it, so both branches are reachable from a JVM test.
 * What the debug variant cannot prove is that the default is not hardcoded `true`; what it does
 * pin is that the app has exactly one answer to this question and that the answer is *no* for a
 * build that says it is not a debug build.
 */
fun destructiveMigrationAllowed(isDebugBuild: Boolean = BuildConfig.DEBUG): Boolean = isDebugBuild

/**
 * The **one** place the database is configured, so a test can pin the real configuration rather
 * than a copy of it that drifts.
 *
 * **Do not add `fallbackToDestructiveMigrationOnDowngrade` here.** It looks like a harmless
 * belt-and-braces companion to the call above and it is the opposite: in Room 2.8 the general
 * `fallbackToDestructiveMigration` sets `requireMigration = false` *and* allows the destructive
 * downgrade, while `fallbackToDestructiveMigrationOnDowngrade` sets `requireMigration` back to
 * **true** — so chaining it re-arms "a migration from N to M was required but not found" on every
 * *upgrade*. That combination shipped through Phase 1 and was invisible there, because the database
 * had only ever gone from not existing to version 1; the first real bump crashed the app at launch
 * instead of wiping, which is ADR-0007's entire premise failing silently.
 */
fun buildBunnyDatabase(
    context: Context,
    databaseName: String = BUNNY_DATABASE_FILE,
    allowDestructiveMigration: Boolean = destructiveMigrationAllowed(),
): BunnyDatabase =
    Room
        .databaseBuilder(context, BunnyDatabase::class.java, databaseName)
        // dropAllTables so a wipe leaves nothing behind, including tables Room did not create.
        // Debug builds only from 1.0 (ADR-0023): without this call Room throws "A migration from N
        // to M was required but not found" instead, which is the loud failure a release wants.
        //
        // `apply` rather than a chained call, because a release build must never so much as touch
        // the builder with the fallback — chaining a call that takes the flag as an argument would
        // still arm something. The parameter is also 3d's hook: the staged restore database pins its
        // own configuration, or in a debug build this fallback would quietly empty the very file it
        // was asked to test.
        //
        // **A build takes one answer or the other, never both**, and that is what the `else` is for.
        // Room prefers a registered migration over the fallback, so adding [BUNNY_MIGRATIONS]
        // unconditionally would quietly retire the wipe — and while a phase is in flight its pending
        // migration is *rewritten in place* as the shape churns (ADR-0007). A debug database that had
        // already migrated to the old shape of version 5 could not be migrated again: same version,
        // different identity hash, which is Room's "cannot verify the data integrity" crash rather
        // than a wipe. So the debug build keeps wiping through the consent screen, which is also what
        // makes that screen's promise true.
        //
        // The migration is therefore proven by test rather than by the author's phone: the
        // instrumented `MigrationTestHelper` run, and 3c's `allowDestructiveMigration = false`
        // parameter, which is what lets an instrumented test open a **release-shaped** database
        // without a release build. CI runs both on every pull request.
        .apply {
            if (allowDestructiveMigration) {
                fallbackToDestructiveMigration(dropAllTables = true)
            } else {
                addMigrations(*BUNNY_MIGRATIONS)
            }
        }
        // Seeds the built-in symptoms on create and reconciles them on open (ADR-0010). It belongs
        // here rather than in a repository because it has to have run before the picker's first read,
        // and because a wipe must land an owner on a full list rather than an empty one.
        .addCallback(builtInSymptomSeedCallback())
        .build()
