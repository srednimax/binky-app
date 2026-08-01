package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * The watch table, which holds at most one row per bunny and usually none.
 *
 * **Nothing here returns whether a watch is active**, because that is a function of the clock rather
 * than of the row — see [watchState]. What the DAO supplies is the row; the derivation lives in
 * `WatchEntity.kt` where it can be tested without a device.
 */
@Dao
interface WatchDao {
    @Query("SELECT * FROM watches WHERE bunnyId = :bunnyId")
    fun watch(bunnyId: String): Flow<WatchEntity?>

    /**
     * Every watch there is, oldest ending first.
     *
     * One flow for the whole table rather than one per bunny: there are almost never more than a
     * couple of rows, the expiry prompt has to ask about all of them, and the healthy day's
     * exclusion needs the set rather than any one member.
     */
    @Query("SELECT * FROM watches ORDER BY endsAt, bunnyId")
    fun watches(): Flow<List<WatchEntity>>

    /** The same read, once, for the sweep — which has no screen and collects nothing. */
    @Query("SELECT * FROM watches ORDER BY endsAt, bunnyId")
    suspend fun watchesNow(): List<WatchEntity>

    /**
     * Starts or restarts a watch.
     *
     * `@Upsert` rather than `@Insert`, and deliberately: the start path must never be blocked by a
     * stale row. An expired watch nobody answered still occupies the only slot that bunny has, and
     * "starting a new watch is the same single tap as extending" only holds if starting overwrites.
     */
    @Upsert
    suspend fun upsert(watch: WatchEntity)

    @Query("DELETE FROM watches WHERE bunnyId = :bunnyId")
    suspend fun deleteByBunnyId(bunnyId: String)

    /**
     * Extends a watch and **clears the nag watermark in the same statement**.
     *
     * Two columns, one write, because they cannot be allowed to disagree: an extension that left
     * `lastNaggedOn` at today would silently skip the first morning of the extended watch, which is
     * the morning the owner just said they were still worried about.
     */
    @Query("UPDATE watches SET endsAt = :endsAt, lastNaggedOn = NULL WHERE bunnyId = :bunnyId")
    suspend fun extend(
        bunnyId: String,
        endsAt: Instant,
    )

    /** Called by the sweep after posting, with the day it posted on. */
    @Query("UPDATE watches SET lastNaggedOn = :on WHERE bunnyId = :bunnyId")
    suspend fun setLastNaggedOn(
        bunnyId: String,
        on: LocalDate,
    )

    /**
     * When this bunny was last observed.
     *
     * An observations query on the watch DAO, deliberately — the same seam `CareDao.latestWeighing`
     * draws for a weigh-in: the nag exists only to ask whether anybody has looked, so the fact that
     * settles it belongs where the code that needs it can see it.
     *
     * **`recordedAt`, not `createdAt`.** The question is whether somebody looked at the rabbit in
     * the last day, not whether they typed something today: an owner catching up on a week of notes
     * this evening has not thereby checked on anyone, and silencing the nag for that would be
     * inferring a fact from a proxy (ADR-0001). `MAX` over an empty table is one `NULL` row, which
     * is why this is nullable.
     */
    @Query("SELECT MAX(recordedAt) FROM observations WHERE bunnyId = :bunnyId")
    suspend fun latestObservationNow(bunnyId: String): Instant?
}
