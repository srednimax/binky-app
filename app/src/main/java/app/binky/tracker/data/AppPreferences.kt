package app.binky.tracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.work.DEFAULT_REMINDER_TIME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
 * Six keys: which bunny is selected, the weight display unit, the export scope, whether first-run
 * setup has been through, whether the battery-optimisation exemption has been offered once, and what
 * time of day reminders arrive. The unit's toggle lands in 2c with the Settings screen — a preference
 * with no setter is a constant with a DataStore round-trip, so the setter is here from the start even
 * though nothing calls it yet.
 *
 * These **travel in every export scope, from Essential upward** (ADR-0005). They are a few hundred
 * bytes, and a restored phone that has forgotten its display unit, its selected bunny and its chosen
 * backup scope does not read as missing data — it reads as bugs.
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

    /**
     * What a manual export defaults to. **Records**, per ADR-0005: everything the owner may need
     * again, without the gallery that makes an export large enough to put someone off running one.
     *
     * Chosen during first-run setup rather than hidden here, and changed in Backup settings later.
     */
    val backupScope: Flow<BackupScope> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> decodeScope(preferences[BACKUP_SCOPE]) }

    suspend fun setBackupScope(scope: BackupScope) {
        dataStore.edit { preferences -> preferences[BACKUP_SCOPE] = scope.name }
    }

    /**
     * How far first-run setup has got on this phone (ADR-0006) — **absent until it is first shown**.
     *
     * The absent case is deliberately not answered here. It is resolved against whether a bunny
     * already exists by [resolveSetupState], which is where the reasoning lives; this flow only
     * reports what was written, `null` included.
     *
     * Kotlin note: `Flow<SetupProgress?>` rather than a defaulted value — the nullable is
     * load-bearing, and collapsing it here would throw away the only distinction that matters.
     */
    val setupProgress: Flow<SetupProgress?> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> decodeProgress(preferences[SETUP_PROGRESS]) }

    /**
     * Recorded when the wizard is first put on screen, **not when it is finished**.
     *
     * Without this the wizard ends itself: its first step adds a bunny, an unrecorded install with
     * a bunny resolves to complete, and the owner is dropped into the app before the backup step
     * ADR-0006 exists to deliver. Writing it down at the start is also what carries the wizard
     * across a process death halfway through it.
     */
    suspend fun markSetupStarted() {
        dataStore.edit { preferences -> preferences[SETUP_PROGRESS] = SetupProgress.Started.name }
    }

    /** Written by the wizard's last step, and the only thing that ends it. */
    suspend fun markSetupComplete() {
        dataStore.edit { preferences -> preferences[SETUP_PROGRESS] = SetupProgress.Complete.name }
    }

    /**
     * Whether the battery-optimisation exemption has been **offered** — not whether it was granted,
     * which the OS answers for itself through `PowerManager.isIgnoringBatteryOptimizations`
     * (ADR-0003's Phase 4a amendment).
     *
     * Two facts, and only one of them is ours to remember. The exemption's state can change in
     * Android's settings at any moment and is read fresh every time; what the app has to remember is
     * that it already asked, because an unprompted ask that reappears on every visit is the nag
     * ADR-0001 rejects. Declining leaves the fix on the delivery-state line, where the owner can
     * take it when they want it.
     */
    val batteryExemptionAsked: Flow<Boolean> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> preferences[BATTERY_EXEMPTION_ASKED] == true }

    /** Recorded on the way *into* the system screen, and on "Not now" — both are having asked. */
    suspend fun markBatteryExemptionAsked() {
        dataStore.edit { preferences -> preferences[BATTERY_EXEMPTION_ASKED] = true }
    }

    /**
     * **One app-wide time of day** for every care reminder, defaulting to 09:00.
     *
     * Not per reminder, and that is a decision rather than an omission: per-reminder clock times
     * would promise a precision ADR-0003 deliberately reserves for medication doses, and would need
     * the exact-alarm path to mean anything at all. This *is* the sweep's time (ADR-0024) — one
     * number behind both — so changing it has to re-enqueue the sweep, or the next run still fires at
     * the old hour.
     *
     * A preference rather than a column for the usual reason: it must survive ADR-0007's wipes.
     */
    val reminderTime: Flow<LocalTime> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> decodeReminderTime(preferences[REMINDER_TIME]) }

    /** The caller is responsible for re-enqueuing the sweep — see `rescheduleSweep`. */
    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { preferences -> preferences[REMINDER_TIME] = time.format(REMINDER_TIME_FORMAT) }
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
        val BACKUP_SCOPE = stringPreferencesKey("backup_scope")
        val SETUP_PROGRESS = stringPreferencesKey("setup_progress")
        val BATTERY_EXEMPTION_ASKED = booleanPreferencesKey("battery_exemption_asked")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")

        /** `HH:mm`, so a stored time is readable in a `.preferences_pb` dump and in a backup. */
        val REMINDER_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

        fun decodeScope(value: String?): BackupScope =
            BackupScope.entries.firstOrNull { it.name == value } ?: BackupScope.Records

        // Null rather than a default, because "nothing recorded" is a real state here and not a
        // missing value — see resolveSetupState. An unrecognised name reads as nothing recorded,
        // which for a phone downgraded from a build with more states is the safe direction: at
        // worst the owner is offered a two-step wizard again.
        fun decodeProgress(value: String?): SetupProgress? = SetupProgress.entries.firstOrNull { it.name == value }

        // An unparseable time falls back to the default rather than throwing — the same rule as
        // every decode above. A reminder time nobody can read is not a reason to stop reminding.
        fun decodeReminderTime(value: String?): LocalTime =
            value?.let { stored ->
                runCatching { LocalTime.parse(stored, REMINDER_TIME_FORMAT) }.getOrNull()
            } ?: DEFAULT_REMINDER_TIME
    }
}
