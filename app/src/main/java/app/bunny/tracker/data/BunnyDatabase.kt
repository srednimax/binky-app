package app.bunny.tracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The schema version this build expects. The wipe guard reads the *file's* version before Room
 * opens it and compares it against this (see `DatabasePreserve.kt`), so the two must stay in step —
 * which is why it is one constant used in both places.
 */
const val BUNNY_SCHEMA_VERSION = 1

/** The database file name, under the app's standard databases directory. */
const val BUNNY_DATABASE_FILE = "bunny.db"

/**
 * Until Phase 3 a schema change is allowed to destroy the database rather than carry a migration
 * for every field added to a still-unsettled model (ADR-0007) — but never silently: the file is
 * copied aside first, by the guard that runs before this is opened.
 */
@Database(
    entities = [BunnyEntity::class, FluffleEntity::class],
    version = BUNNY_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BunnyDatabase : RoomDatabase() {
    abstract fun bunnyDao(): BunnyDao

    abstract fun fluffleDao(): FluffleDao
}
