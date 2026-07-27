package app.binky.tracker.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

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

    /** A birthdate is a calendar day with no time zone, so it is stored as an epoch *day*. */
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun localDateFromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

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
