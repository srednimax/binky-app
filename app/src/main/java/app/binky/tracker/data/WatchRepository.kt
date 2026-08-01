package app.binky.tracker.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Starts, extends, closes and nags about watches (ADR-0001).
 *
 * **Every resolution disposes of the row**, and that is the whole of "prompts once" without a column
 * recording it: extending rewrites `endsAt` and clears the nag watermark, while closing, dismissing
 * or swiping the expiry prompt away deletes. Nothing is lost by treating a dismissal as a close,
 * because starting a new watch is the same single tap as extending — and leaving an unanswered
 * expired row in place would occupy the only slot that bunny has.
 *
 * The derivation is not here: `watchState` in `WatchEntity.kt` is pure over `(row, now)` and
 * JVM-tested, so this class only owns the plumbing.
 */
class WatchRepository(
    database: BunnyDatabase,
) {
    private val watchDao = database.watchDao()

    fun watch(bunnyId: String): Flow<WatchEntity?> = watchDao.watch(bunnyId)

    val watches: Flow<List<WatchEntity>> = watchDao.watches()

    /**
     * Who is under a **running** watch right now, for the pre-selection's exclusion (ADR-0008).
     *
     * A one-shot read and deliberately no `Flow` twin: both callers ask once, when a form opens or
     * a one-tap write lands, and a flow would only invite a screen to hold a set whose truth expires
     * without anything being written. [now] is resolved against the clock at the moment of asking,
     * because "active" ends on its own.
     */
    suspend fun activelyWatchedIdsNow(now: Instant = Instant.now()): Set<String> =
        watchDao
            .watchesNow()
            .filter { watchState(it, now).isActive() }
            .map { it.bunnyId }
            .toSet()

    suspend fun watchesNow(): List<WatchEntity> = watchDao.watchesNow()

    suspend fun latestObservationNow(bunnyId: String): Instant? = watchDao.latestObservationNow(bunnyId)

    /**
     * Starts a watch, or restarts one over whatever was there.
     *
     * An upsert on purpose — see [WatchDao.upsert]. A fresh row also means a fresh `lastNaggedOn`,
     * so the first morning of a new watch always asks.
     */
    suspend fun start(
        bunnyId: String,
        duration: WatchDuration = WatchDuration.Default,
        now: Instant = Instant.now(),
    ) {
        watchDao.upsert(
            WatchEntity(
                bunnyId = bunnyId,
                startedAt = now,
                endsAt = watchEndsAt(now, duration),
                lastNaggedOn = null,
            ),
        )
    }

    /**
     * Extends an expired watch by a full duration **from now**, not from the date it ran out.
     *
     * An owner answering the prompt three days late is saying they are still watching *today*;
     * extending from `endsAt` would hand them a watch that had already spent half of itself.
     */
    suspend fun extend(
        bunnyId: String,
        duration: WatchDuration = WatchDuration.Default,
        now: Instant = Instant.now(),
    ) {
        watchDao.extend(bunnyId, watchEndsAt(now, duration))
    }

    /** Close, dismiss and swipe-away all land here: the row goes, and with it the watch. */
    suspend fun close(bunnyId: String) {
        watchDao.deleteByBunnyId(bunnyId)
    }

    /** Called by the sweep after posting, with the calendar day it posted on. */
    suspend fun markNagged(
        bunnyId: String,
        on: LocalDate,
    ) {
        watchDao.setLastNaggedOn(bunnyId, on)
    }
}
