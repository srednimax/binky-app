package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * The dated labels an owner wrote down (ADR-0031).
 *
 * **Nothing here derives a timeline.** The timeline is four sources merged and stores nothing
 * (`ui/events/Timeline.kt`); this DAO owns exactly one of them, and the merge is a pure function so
 * that "upcoming above past" is a JVM assertion rather than something to squint at on a phone.
 *
 * Kotlin note: the `…Now` suffix marks the `suspend` one-shot variants — used inside write paths
 * and by the sweep, which has no screen and collects nothing. The plain ones return `Flow`, which
 * Room re-emits on every write to `events` (house rule).
 */
@Dao
interface EventDao {
    /**
     * One bunny's events, **soonest first across the whole span**.
     *
     * Ascending rather than newest-first, unlike every other list in this app: an event list is an
     * agenda and not a history, so the natural reading order runs forwards. Which end the *screen*
     * starts at is the timeline's decision, not this one's.
     */
    @Query("SELECT * FROM events WHERE bunnyId = :bunnyId ORDER BY occursOn, createdAt, id")
    fun forBunny(bunnyId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE bunnyId = :bunnyId ORDER BY occursOn, createdAt, id")
    suspend fun forBunnyNow(bunnyId: String): List<EventEntity>

    /**
     * Everything this bunny has on one day — the sweep's read, and the only query with a `WHERE` on
     * [EventEntity.occursOn] alone.
     *
     * **It does not filter [EventEntity.notifiedAt], deliberately.** "Posts once and never again" is
     * a rule ADR-0001 cares about, so it lives in `eventsDueForNotifying` where it is a case-table
     * assertion, next to the archived-bunny exclusion it belongs with. The index on
     * `(bunnyId, occursOn)` answers this one row-for-row anyway, so narrowing it further in SQL
     * would buy nothing but a second spelling of the rule.
     */
    @Query("SELECT * FROM events WHERE bunnyId = :bunnyId AND occursOn = :day ORDER BY createdAt, id")
    suspend fun onDayNow(
        bunnyId: String,
        day: LocalDate,
    ): List<EventEntity>

    /**
     * One event, watched. Nullable because the row can be **deleted while its own screen is open** —
     * the emission is what tells that screen to leave.
     */
    @Query("SELECT * FROM events WHERE id = :id")
    fun byId(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byIdNow(id: String): EventEntity?

    @Insert
    suspend fun insert(event: EventEntity)

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Stamps *when* the notification went out.
     *
     * The difference from `CareDao.setNotifiedForDueOn` is the whole difference between the two
     * records: a care reminder comes due again and again and has to remember **which** occurrence it
     * announced, while an event has one date in its life and so only has to remember **that** it was
     * announced.
     */
    @Query("UPDATE events SET notifiedAt = :notifiedAt WHERE id = :id")
    suspend fun setNotifiedAt(
        id: String,
        notifiedAt: Instant?,
    )

    @Query("UPDATE events SET calendarHandedOffAt = :handedOffAt WHERE id = :id")
    suspend fun setCalendarHandedOffAt(
        id: String,
        handedOffAt: Instant?,
    )
}
