package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** The newest completion of one reminder, from the grouped read below. */
data class LatestCompletion(
    val reminderId: String,
    val completedOn: LocalDate,
)

/**
 * Care reminders and their completions.
 *
 * **Nothing here returns a due date**, because none is stored (ADR-0002). What the DAO supplies is the
 * two facts a due date is derived from — the reminder and its latest completion — and the derivation
 * lives in `CareSchedule.kt` where it can be tested without a device.
 *
 * The completions read is **grouped rather than per-reminder**: a Care screen showing six reminders
 * would otherwise open six flows and recombine them, and the group-by is one query SQLite answers off
 * the `(reminderId, completedOn)` index.
 */
@Dao
interface CareDao {
    @Query("SELECT * FROM care_reminders WHERE bunnyId = :bunnyId ORDER BY createdAt, id")
    fun reminders(bunnyId: String): Flow<List<CareReminderEntity>>

    /** One-shot read, for the write paths and the sweep. */
    @Query("SELECT * FROM care_reminders WHERE bunnyId = :bunnyId ORDER BY createdAt, id")
    suspend fun remindersNow(bunnyId: String): List<CareReminderEntity>

    @Query("SELECT * FROM care_reminders WHERE id = :id")
    suspend fun reminderNow(id: String): CareReminderEntity?

    /**
     * One reminder, watched. Nullable because the row can be **deleted while its own screen is
     * open** — the emission is what tells that screen to leave, rather than an exception it would
     * otherwise have to catch.
     */
    @Query("SELECT * FROM care_reminders WHERE id = :id")
    fun reminder(id: String): Flow<CareReminderEntity?>

    /**
     * The latest completion of each of this bunny's reminders, for the ones that have any.
     *
     * A reminder with no completions is simply absent from the result rather than present with a
     * `NULL` — the caller has the reminder list already, and an absent row and a null date mean the
     * same thing to [scheduleFor].
     */
    @Query(
        """
        SELECT e.reminderId AS reminderId, MAX(e.completedOn) AS completedOn
        FROM care_events e
        JOIN care_reminders r ON r.id = e.reminderId
        WHERE r.bunnyId = :bunnyId
        GROUP BY e.reminderId
        """,
    )
    fun latestCompletions(bunnyId: String): Flow<List<LatestCompletion>>

    @Query(
        """
        SELECT e.reminderId AS reminderId, MAX(e.completedOn) AS completedOn
        FROM care_events e
        JOIN care_reminders r ON r.id = e.reminderId
        WHERE r.bunnyId = :bunnyId
        GROUP BY e.reminderId
        """,
    )
    suspend fun latestCompletionsNow(bunnyId: String): List<LatestCompletion>

    /**
     * The bunny's most recent weighing, as an instant.
     *
     * A weights query on the care DAO, deliberately: it exists only because `WEIGH_IN` resolves its
     * last completion against the weight series (ADR-0018's amendment), and putting it here keeps
     * that seam visible from the code that needs it rather than hiding it among the chart's reads.
     * `MAX` over an empty series is one `NULL` row, which is why the flow is nullable.
     */
    @Query("SELECT MAX(recordedAt) FROM weights WHERE bunnyId = :bunnyId")
    fun latestWeighing(bunnyId: String): Flow<Instant?>

    @Query("SELECT MAX(recordedAt) FROM weights WHERE bunnyId = :bunnyId")
    suspend fun latestWeighingNow(bunnyId: String): Instant?

    /** Newest first, with `createdAt` and `id` breaking ties on a day with two completions. */
    @Query(
        "SELECT * FROM care_events WHERE reminderId = :reminderId " +
            "ORDER BY completedOn DESC, createdAt DESC, id",
    )
    fun events(reminderId: String): Flow<List<CareEventEntity>>

    @Query("SELECT * FROM care_events WHERE id = :id")
    suspend fun eventNow(id: String): CareEventEntity?

    @Insert
    suspend fun insertReminder(reminder: CareReminderEntity)

    @Update
    suspend fun updateReminder(reminder: CareReminderEntity)

    @Query("DELETE FROM care_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: String)

    /**
     * Records the due date a notification was posted **for** — not when it was posted, which would
     * have to be cleared on every path that moves a due date (see [CareReminderEntity]).
     */
    @Query("UPDATE care_reminders SET notifiedForDueOn = :dueOn WHERE id = :id")
    suspend fun setNotifiedForDueOn(
        id: String,
        dueOn: LocalDate,
    )

    @Query("UPDATE care_reminders SET calendarHandedOffAt = :handedOffAt WHERE id = :id")
    suspend fun setCalendarHandedOffAt(
        id: String,
        handedOffAt: Instant?,
    )

    @Insert
    suspend fun insertEvent(event: CareEventEntity)

    @Update
    suspend fun updateEvent(event: CareEventEntity)

    @Query("DELETE FROM care_events WHERE id = :id")
    suspend fun deleteEventById(id: String)
}
