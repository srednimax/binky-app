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
import java.time.LocalDate
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
 * **1.1 adds two more owner-facing settings and three dates behind them** (PLAN 4e): the remembered
 * export folder, the recurring export reminder's interval — and, so that reminder can be derived
 * rather than guessed, when it was switched on, when the owner last exported, and which due date was
 * last notified about. The three dates are bookkeeping, never shown as settings; they are here
 * rather than in the database for the same reason as everything else in this class, and because a
 * backup reminder hangs off the app and not off any bunny.
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

    /**
     * The document tree the owner picked for exports, or `null` for "ask the share sheet every
     * time" (ADR-0005).
     *
     * A `String` rather than a `Uri`: this file is a plain key-value store that travels inside every
     * backup, and `Uri` is an Android type with no place in one. Whether the *grant* behind it still
     * holds is a different question, answered against `ContentResolver.persistedUriPermissions` at
     * read time — a remembered folder can be revoked in Android's settings, or land on a phone that
     * never granted it, and this preference cannot know either.
     */
    val exportFolder: Flow<String?> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> preferences[EXPORT_FOLDER] }

    suspend fun setExportFolder(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(EXPORT_FOLDER) else preferences[EXPORT_FOLDER] = uri
        }
    }

    /**
     * The recurring export reminder, read as the one shape the sweep and the screen both want —
     * see [ExportReminder], which is where the derivation lives.
     *
     * Four keys behind one flow, because no single one of them answers anything on its own.
     */
    val exportReminder: Flow<ExportReminder> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences ->
                ExportReminder(
                    every = decodeExportInterval(preferences[EXPORT_REMINDER_EVERY]),
                    enabledOn = decodeDate(preferences[EXPORT_REMINDER_SINCE]),
                    lastExportedOn = decodeDate(preferences[EXPORT_LAST_ON]),
                    notifiedForDueOn = decodeDate(preferences[EXPORT_REMINDER_NOTIFIED_FOR]),
                )
            }

    /**
     * Switches the reminder on at [interval] from [today], or off.
     *
     * **Turning it on rewrites the anchor**, so off-and-on-again restarts the interval rather than
     * resuming a due date the owner may have forgotten about. Turning it off keeps every other key:
     * an owner who switches it back on next week has not lost their last export date, and the
     * notified-for watermark is compared against a derived due date that will have moved anyway.
     */
    suspend fun setExportReminder(
        interval: ExportInterval?,
        today: LocalDate,
    ) {
        dataStore.edit { preferences ->
            if (interval == null) {
                preferences.remove(EXPORT_REMINDER_EVERY)
            } else {
                preferences[EXPORT_REMINDER_EVERY] = interval.name
                preferences[EXPORT_REMINDER_SINCE] = today.toString()
            }
        }
    }

    /**
     * Records that an export was made, whichever path made it — the share sheet or the remembered
     * folder. This is the reminder's completion, so it is written by *both*, or the reminder would
     * prompt an owner who has just exported to a folder.
     */
    suspend fun markExported(on: LocalDate) {
        dataStore.edit { preferences -> preferences[EXPORT_LAST_ON] = on.toString() }
    }

    /** The due date the sweep has posted a prompt for — ADR-0024's "notifies once", recorded. */
    suspend fun markExportReminderNotified(dueOn: LocalDate) {
        dataStore.edit { preferences -> preferences[EXPORT_REMINDER_NOTIFIED_FOR] = dueOn.toString() }
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
        val EXPORT_FOLDER = stringPreferencesKey("export_folder")
        val EXPORT_REMINDER_EVERY = stringPreferencesKey("export_reminder_every")
        val EXPORT_REMINDER_SINCE = stringPreferencesKey("export_reminder_since")
        val EXPORT_LAST_ON = stringPreferencesKey("export_last_on")
        val EXPORT_REMINDER_NOTIFIED_FOR = stringPreferencesKey("export_reminder_notified_for")

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

        // Absent *and* unrecognised both read as off. A phone downgraded from a build with more
        // presets would otherwise hold a name this one cannot map, and inventing an interval for it
        // is how an owner gets notifications on a schedule they never chose.
        fun decodeExportInterval(value: String?): ExportInterval? =
            ExportInterval.entries.firstOrNull { it.name == value }

        // ISO-8601 (`2026-08-02`), which is what `LocalDate.toString` writes — readable in a
        // `.preferences_pb` dump and in a backup, like the reminder time above.
        fun decodeDate(value: String?): LocalDate? =
            value?.let { stored -> runCatching { LocalDate.parse(stored) }.getOrNull() }
    }
}
