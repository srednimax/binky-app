package app.bunny.tracker.data

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
}
