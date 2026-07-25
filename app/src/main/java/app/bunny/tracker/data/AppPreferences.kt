package app.bunny.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Owner preferences held outside the database, because ADR-0007 lets the database be wiped and
 * these must survive that.
 *
 * Phase 1 stores **exactly one key**: which bunny is selected. The weight display unit arrives in
 * Phase 2, with the screens that read it.
 */
class AppPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    /** The owner's last explicit choice. Emits again on every write, like every read in this app. */
    val selection: Flow<StoredSelection> =
        dataStore.data
            // A corrupt or unreadable preferences file must not take the app down with it; an
            // unreadable choice is indistinguishable from never having made one.
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> decode(preferences[SELECTED_BUNNY]) }

    suspend fun setSelection(selection: StoredSelection) {
        dataStore.edit { preferences ->
            when (selection) {
                is StoredSelection.None -> preferences.remove(SELECTED_BUNNY)
                is StoredSelection.All -> preferences[SELECTED_BUNNY] = ALL
                is StoredSelection.Bunny -> preferences[SELECTED_BUNNY] = selection.id
            }
        }
    }

    /**
     * Clears the stored choice if it names [bunnyId]. Called when that bunny is *deleted* — an
     * archived bunny's id is deliberately left in place so unarchiving restores it (ADR-0015), but
     * a deleted one is never coming back and would dangle forever.
     */
    suspend fun clearSelectionIfSet(bunnyId: String) {
        dataStore.edit { preferences ->
            if (preferences[SELECTED_BUNNY] == bunnyId) preferences.remove(SELECTED_BUNNY)
        }
    }

    private companion object {
        val SELECTED_BUNNY = stringPreferencesKey("selected_bunny")

        /** Bunny ids are UUIDs, so this sentinel cannot collide with one. */
        const val ALL = "all"

        fun decode(value: String?): StoredSelection =
            when (value) {
                null -> StoredSelection.None
                ALL -> StoredSelection.All
                else -> StoredSelection.Bunny(value)
            }
    }
}
