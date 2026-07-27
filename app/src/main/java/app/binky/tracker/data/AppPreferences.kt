package app.binky.tracker.data

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
 * How weights are shown. **Entry is always in grams** — that is what a scale reads out — and
 * **changes are always shown in grams** whichever this is set to, because `−0.04 kg` hides the
 * signal that `−40 g` makes obvious (house rule). This only decides how a stored weight is rendered.
 *
 * A preference rather than a column: it is a display choice about the whole app, and it has to
 * survive ADR-0007's wipes.
 */
enum class WeightUnit { KILOGRAMS, GRAMS }

/**
 * Owner preferences held outside the database, because ADR-0007 lets the database be wiped and
 * these must survive that.
 *
 * Two keys: which bunny is selected, and the weight display unit. The unit's toggle lands in 2c with
 * the Settings screen — a preference with no setter is a constant with a DataStore round-trip, so
 * the setter is here from the start even though nothing calls it yet.
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

    /** Kilograms by default: it is what an owner says out loud about a rabbit. */
    val weightUnit: Flow<WeightUnit> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> decodeUnit(preferences[WEIGHT_UNIT]) }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { preferences -> preferences[WEIGHT_UNIT] = unit.name }
    }

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
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")

        /** Bunny ids are UUIDs, so this sentinel cannot collide with one. */
        const val ALL = "all"

        fun decode(value: String?): StoredSelection =
            when (value) {
                null -> StoredSelection.None
                ALL -> StoredSelection.All
                else -> StoredSelection.Bunny(value)
            }

        // Stored by name, never ordinal, and falling back rather than throwing — the same rule the
        // database's enums follow, for the same reason.
        fun decodeUnit(value: String?): WeightUnit =
            WeightUnit.entries.firstOrNull { it.name == value } ?: WeightUnit.KILOGRAMS
    }
}
