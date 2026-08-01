package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The unit half of a care interval. **A calendar unit, never a day count** (ADR-0018's Phase 4b
 * amendment): "yearly" has to mean the same date next year, or the app and the `FREQ=YEARLY` event it
 * hands the owner's calendar (ADR-0014) drift a day apart every four years.
 *
 * Stored by name, never ordinal — the house rule, and here it matters twice over, since inserting a
 * `FORTNIGHT` between `WEEK` and `MONTH` would otherwise rewrite every stored schedule.
 */
enum class CareIntervalUnit { DAY, WEEK, MONTH, YEAR }

/**
 * The **known** kinds of care, and nothing more (ADR-0018). Its job is to give a reminder an icon, a
 * translated label and a calendar `RRULE`; a reminder with no type is normal and not a data error,
 * which is why [CareReminderEntity.type] is nullable.
 *
 * The default intervals live here rather than in the form because they are facts about the care, not
 * about the screen that offers them: a nail trim is six weeks whoever asks.
 *
 * [WEIGH_IN] is the one entry that carries behaviour beyond display — see [CareReminderEntity.type]
 * and `CareRepository`.
 */
enum class CareType(
    val defaultIntervalCount: Int,
    val defaultIntervalUnit: CareIntervalUnit,
) {
    NAIL_TRIM(6, CareIntervalUnit.WEEK),
    VACCINATION(1, CareIntervalUnit.YEAR),
    WEIGH_IN(1, CareIntervalUnit.WEEK),
}

/**
 * A `{count, unit}` calendar interval, carried together so the pure schedule functions take one
 * argument that cannot be half-supplied.
 *
 * Kotlin note: not `@Embedded` on the entity. Room would happily flatten it into the same two
 * columns, but the entity's column names are load-bearing here — they appear verbatim in
 * `MIGRATION_4_5` — and two plain fields keep that mapping something you can read off the class.
 */
data class CareInterval(
    val count: Int,
    val unit: CareIntervalUnit,
) {
    init {
        require(count > 0) { "A care interval repeats a positive number of times" }
    }
}

/**
 * Adds one interval to a date.
 *
 * **The day-of-month is allowed to walk.** `java.time` clamps `31 January + 1 MONTH` to 28 February,
 * and since the next occurrence is scheduled from the recorded completion, a monthly reminder
 * anchored late in the month settles onto the 28th and stays there. Preserving an intended
 * day-of-month would mean storing an intention beside the completion history — two facts that can
 * disagree, which is what ADR-0002 and ADR-0001 push this project away from, to save three days on a
 * hay reorder.
 */
fun LocalDate.plus(interval: CareInterval): LocalDate {
    val count = interval.count.toLong()
    return when (interval.unit) {
        CareIntervalUnit.DAY -> plusDays(count)
        CareIntervalUnit.WEEK -> plusWeeks(count)
        CareIntervalUnit.MONTH -> plusMonths(count)
        CareIntervalUnit.YEAR -> plusYears(count)
    }
}

/**
 * One recurring care task for one bunny: `{label, interval, optional type}` (ADR-0018).
 *
 * **[label] and [type] are the built-in/owner-added split**, the same shape `SymptomEntity` draws
 * between a seeded `key` and an owner's literal text. A preset stores its [type] and leaves [label]
 * `null`, so its display text resolves through `strings.xml` and therefore translates (ADR-0013); a
 * custom reminder stores literal text and no type, and is untranslatable as expected. The one case
 * that carries both is a preset the owner renamed — *Front claws* on a `NAIL_TRIM` — where the label
 * wins for display and the type still supplies the icon and the `RRULE`. Storing an English "Nail
 * trim" in [label] for every preset would be the second fact that can disagree with the first.
 *
 * **Care dates are [LocalDate], not [Instant]**, and this is the first place in the app where the
 * distinction is real: a weighing happens at a moment, a nail trim happens on a *day*. "I did it
 * yesterday" stored as an instant becomes a different day the first time the owner opens the app in
 * another timezone. ADR-0003 already calls care reminders day-granularity; the column type is that,
 * enforced rather than remembered.
 *
 * **There is no `dueOn` column.** The due date is derived — latest completion plus the interval, else
 * [firstDueOn] unmodified — for ADR-0002's reason: a stored due date has to be rewritten by every
 * path that moves it, and a missed rewrite fails silently. [firstDueOn] is an *anchor*, the answer to
 * "when is this next due?", which an owner can read off a vet card; "when did you last vaccinate?" is
 * a subtraction they often cannot do. An owner who does know records it as a real [CareEventEntity],
 * where it is visible and correctable.
 *
 * [notifiedForDueOn] holds **the due date a notification was posted for**, not when it was posted.
 * Compared against the derived due date, it needs clearing on no path at all: a completion, an edited
 * or deleted care event, an edited interval, or — for a weigh-in — a back-dated weight that writes
 * nothing here all move the derived date, and the comparison goes stale on its own. Storing *when* it
 * notified would need a clear on every one of those, and a missed clear silences a real reminder.
 *
 * [calendarHandedOffAt] records only that the hand-off happened (ADR-0014). The app does not own the
 * event — no event id, editing in-app changes nothing out there — it exists so the button can read
 * "Added to calendar" instead of minting a second event on a second tap.
 */
@Entity(
    tableName = "care_reminders",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            // A nail trim is for one bunny; it goes when they do.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bunnyId")],
)
data class CareReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    /** Non-null for a custom reminder, or for a preset the owner renamed. Stored literally. */
    val label: String? = null,
    /** Non-null for one of the known kinds. Resolves the display label when [label] is `null`. */
    val type: CareType? = null,
    val intervalCount: Int,
    val intervalUnit: CareIntervalUnit,
    /** The anchor: when this is next due, before any completion has moved it. */
    val firstDueOn: LocalDate,
    /** The due date a notification has already been posted for, if any. */
    val notifiedForDueOn: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
    val calendarHandedOffAt: Instant? = null,
)

/** The interval as one value, for the pure functions in `CareSchedule.kt`. */
val CareReminderEntity.interval: CareInterval
    get() = CareInterval(intervalCount, intervalUnit)

/**
 * One completion of a care task.
 *
 * Recording this is what schedules the next occurrence — there is no OS periodic trigger anywhere in
 * this design. **Overdue does not drift:** the next date comes from [completedOn], so a nail trim done
 * three weeks late resets the six weeks rather than owing them.
 *
 * Back-datable on the same terms as Phase 2 entry, which is why [completedOn] and [createdAt] are two
 * columns: the first is the day the trim happened, the second the moment it was typed, and only the
 * first moves the schedule.
 */
@Entity(
    tableName = "care_events",
    foreignKeys = [
        ForeignKey(
            entity = CareReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Every read is "this reminder's completions, newest first".
    indices = [Index(value = ["reminderId", "completedOn"])],
)
data class CareEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val reminderId: String,
    val completedOn: LocalDate,
    val note: String? = null,
    val createdAt: Instant = Instant.now(),
)
