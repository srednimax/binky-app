package app.bunny.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The schema version this build expects. The wipe guard reads the *file's* version before Room
 * opens it and compares it against this (see `DatabasePreserve.kt`), so the two must stay in step —
 * which is why it is one constant used in both places.
 */
const val BUNNY_SCHEMA_VERSION = 3

/** The database file name, under the app's standard databases directory. */
const val BUNNY_DATABASE_FILE = "bunny.db"

/**
 * Until Phase 3 a schema change is allowed to destroy the database rather than carry a migration
 * for every field added to a still-unsettled model (ADR-0007) — but never silently: the file is
 * copied aside first, by the guard that runs before this is opened.
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
}

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
): BunnyDatabase =
    Room
        .databaseBuilder(context, BunnyDatabase::class.java, databaseName)
        // dropAllTables so a wipe leaves nothing behind, including tables Room did not create.
        .fallbackToDestructiveMigration(dropAllTables = true)
        // Seeds the built-in symptoms on create and reconciles them on open (ADR-0010). It belongs
        // here rather than in a repository because it has to have run before the picker's first read,
        // and because a wipe must land an owner on a full list rather than an empty one.
        .addCallback(builtInSymptomSeedCallback())
        .build()
