package app.bunny.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.bunny.tracker.media.MediaFiles
import java.io.File
import java.util.UUID

/** An in-memory database, so nothing leaks between tests and nothing touches the device's files. */
fun inMemoryDatabase(): BunnyDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BunnyDatabase::class.java,
        ).build()

/** Real DataStore, on a throwaway file — DataStore allows only one instance per path. */
fun temporaryPreferences(): AppPreferences {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
    return AppPreferences(PreferenceDataStoreFactory.create { file })
}

/** Media rooted in a throwaway directory, so no test writes into the real `filesDir`. */
fun temporaryMedia(): MediaFiles {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return MediaFiles(context, File(context.cacheDir, "media-${UUID.randomUUID()}"))
}

/** Whether the table exists at all — for asserting a destructive wipe left nothing behind. */
fun BunnyDatabase.hasTable(table: String): Boolean =
    openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0) > 0
        }

/** Counts rows without going through a DAO, so the assertion cannot be fooled by one. */
fun BunnyDatabase.countRows(table: String): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
