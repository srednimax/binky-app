package app.binky.tracker.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * SQLite has no date or enum types, so Room needs a mapping in each direction. `java.time` is
 * available unconditionally at `minSdk` 26, so no desugaring is involved.
 *
 * Enums are stored by **name, never ordinal** (house rule): an ordinal column silently rewrites
 * history the day a value is inserted into the middle of an enum.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun instantFromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /**
     * A calendar day with no time zone, stored as an epoch *day*.
     *
     * A birthdate was the first of these; Phase 4's care dates are the rest, and they share this one
     * converter because Room picks a converter by *type* — there can be exactly one mapping for
     * `LocalDate` in the whole database, so "ISO text for care dates, epoch day for birthdates" was
     * never actually available. An integer is the better of the two anyway: `MAX(completedOn)` and
     * `ORDER BY completedOn` are then real comparisons rather than a lexicographic accident that
     * happens to work while every year has four digits.
     */
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun localDateFromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    /**
     * A wall-clock time with no date and no zone, stored as **seconds since local midnight**.
     *
     * An integer for the same reason [localDateToEpochDay] is one: `ORDER BY time` and the unique
     * index on `(courseId, time)` are then real comparisons rather than a lexicographic accident, and
     * "08:00" and "8:00" cannot be two different rows.
     *
     * Sub-second precision is dropped, which costs nothing here — every [LocalTime] in this app comes
     * from a clock picker at minute granularity (ADR-0002's dose slots).
     */
    @TypeConverter
    fun localTimeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()

    // `ofSecondOfDay` takes a `Long`; the column is an `Int` because a day has 86 400 seconds.
    @TypeConverter
    fun localTimeFromSecondOfDay(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun doseStatusToName(value: DoseStatus): String = value.name

    /**
     * Falls back to [DoseStatus.GIVEN] rather than throwing, on the same reasoning as [sexFromName]:
     * a name written by a future build must not crash every read of the row.
     *
     * **`GIVEN` rather than `SKIPPED`, and the choice is not arbitrary.** Both are wrong answers if
     * this is ever reached, but the recorded row exists *because a person answered the slot* — so the
     * honest fallback is the one that keeps the slot answered. Reading it back as `SKIPPED` would
     * additionally assert something about the rabbit's treatment that nobody said (ADR-0026).
     */
    @TypeConverter
    fun doseStatusFromName(value: String): DoseStatus =
        DoseStatus.entries.firstOrNull { it.name == value } ?: DoseStatus.GIVEN

    @TypeConverter
    fun sexToName(value: Sex): String = value.name

    // Falls back rather than throwing: a name written by a future build that this one no longer
    // knows would otherwise crash every read of the row. "Unknown" is a truthful answer here.
    @TypeConverter
    fun sexFromName(value: String): Sex = Sex.entries.firstOrNull { it.name == value } ?: Sex.UNKNOWN

    @TypeConverter
    fun neuterStatusToName(value: NeuterStatus): String = value.name

    @TypeConverter
    fun neuterStatusFromName(value: String): NeuterStatus =
        NeuterStatus.entries.firstOrNull { it.name == value } ?: NeuterStatus.UNKNOWN

    /*
     * The observation vocabularies, every one of them **nullable**, because `null` is a real value
     * there: it means "not checked" (ADR-0001). So an unrecognised name falls back to `null` rather
     * than to a substitute member — a row written by a future build that added a droppings form reads
     * back as *not checked* on this build, which is the honest answer and never a crash. That is the
     * same reasoning as [sexFromName] above, landing on a different answer because absence is
     * representable here and is not for [Sex].
     */

    @TypeConverter
    fun droppingsAmountToName(value: DroppingsAmount?): String? = value?.name

    @TypeConverter
    fun droppingsAmountFromName(value: String?): DroppingsAmount? = enumByName(value, DroppingsAmount.entries)

    @TypeConverter
    fun droppingsSizeToName(value: DroppingsSize?): String? = value?.name

    @TypeConverter
    fun droppingsSizeFromName(value: String?): DroppingsSize? = enumByName(value, DroppingsSize.entries)

    @TypeConverter
    fun droppingsFormToName(value: DroppingsForm?): String? = value?.name

    @TypeConverter
    fun droppingsFormFromName(value: String?): DroppingsForm? = enumByName(value, DroppingsForm.entries)

    @TypeConverter
    fun cecotropesToName(value: Cecotropes?): String? = value?.name

    @TypeConverter
    fun cecotropesFromName(value: String?): Cecotropes? = enumByName(value, Cecotropes.entries)

    @TypeConverter
    fun appetiteToName(value: Appetite?): String? = value?.name

    @TypeConverter
    fun appetiteFromName(value: String?): Appetite? = enumByName(value, Appetite.entries)

    @TypeConverter
    fun moodToName(value: Mood?): String? = value?.name

    @TypeConverter
    fun moodFromName(value: String?): Mood? = enumByName(value, Mood.entries)

    @TypeConverter
    fun activityLevelToName(value: ActivityLevel?): String? = value?.name

    @TypeConverter
    fun activityLevelFromName(value: String?): ActivityLevel? = enumByName(value, ActivityLevel.entries)

    @TypeConverter
    fun waterIntakeToName(value: WaterIntake?): String? = value?.name

    @TypeConverter
    fun waterIntakeFromName(value: String?): WaterIntake? = enumByName(value, WaterIntake.entries)

    /**
     * Care type is nullable for a reason of its own: `null` means a custom reminder, which ADR-0018
     * calls normal and not a data error. So an unrecognised name reading back as `null` degrades a
     * future preset into a custom reminder — it keeps its label, its interval and its history, and
     * loses only an icon.
     */
    @TypeConverter
    fun careTypeToName(value: CareType?): String? = value?.name

    @TypeConverter
    fun careTypeFromName(value: String?): CareType? = enumByName(value, CareType.entries)

    @TypeConverter
    fun careIntervalUnitToName(value: CareIntervalUnit): String = value.name

    /**
     * Unreachable in practice — ADR-0023's guard refuses to open a database file written by a build
     * this one does not know, so a unit added in a later version never reaches this method. The
     * fallback is here so that if it somehow does, a read cannot crash.
     *
     * `YEAR` rather than `DAY`, and the choice is not arbitrary: a wrong unit that stretches the
     * interval shows a wrong date on a screen the owner can correct, where a wrong unit that
     * shortens it notifies every morning about a nail trim. Between two wrong answers, take the quiet
     * one — a daily false alarm is the wallpaper failure ADR-0001 rejects.
     */
    @TypeConverter
    fun careIntervalUnitFromName(value: String): CareIntervalUnit =
        CareIntervalUnit.entries.firstOrNull { it.name == value } ?: CareIntervalUnit.YEAR
}

/**
 * Shared body for the nullable-enum converters above, so the fallback rule is written once instead of
 * eight times — eight copies being eight chances for one of them to throw instead.
 *
 * Kotlin note: Room needs each `@TypeConverter` to be a concrete, separately typed method (it reads
 * the signatures to pick a converter), so this stays a plain helper the one-liners delegate to rather
 * than a generic converter.
 */
private fun <T : Enum<T>> enumByName(
    value: String?,
    entries: List<T>,
): T? = value?.let { name -> entries.firstOrNull { it.name == name } }
