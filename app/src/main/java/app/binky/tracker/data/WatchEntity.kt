package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * How long a watch runs.
 *
 * **Preset chips, not a free-form number** (ADR-0001). A number field invites a 90-day watch, and a
 * watch that never ends is the silent wallpaper the time box exists to prevent: the nag stops being
 * information and becomes something the owner mutes at the channel, taking its channel-mate's
 * reliability with it.
 *
 * Kotlin note: enum entries carrying a constructor argument make this a lookup table rather than the
 * bare constants a JS enum gives you — the same shape as `CareType` and `ReminderChannel`.
 */
enum class WatchDuration(
    val days: Long,
) {
    DAYS_3(3),
    DAYS_7(7),
    DAYS_14(14),
    ;

    companion object {
        /** What the start dialog and the flag's *Start a watch* action open on. */
        val Default = DAYS_7
    }
}

/**
 * A bunny the owner is currently keeping an eye on.
 *
 * **A watch is a present-tense state, not a record.** Closing deletes the row; there is no watch
 * history anywhere in the app. That is accepted rather than overlooked — it is the same family as
 * the trend flag being derived on read (ADR-0001), and a history nothing reads is a table that has
 * to be migrated forever.
 *
 * [bunnyId] is the primary key **and** a foreign key, which is `TrendAcknowledgmentEntity`'s
 * precedent (2a): at most one live watch per bunny, stated as a constraint rather than left to
 * whoever writes the next insert, and discard-on-delete enforced by the database rather than
 * remembered. Being the primary key gives the foreign key its index for free.
 *
 * [lastNaggedOn] is **why "once daily" is true**. The sweep can run more than once in a day — a
 * retry, a reboot, a Doze window closing late — so "already chased today" has to be a recorded fact
 * rather than an assumption about the scheduler. A [LocalDate] and not an [Instant], because the
 * question is about the calendar day; the *other* half of the nag decision, whether anybody has
 * looked recently, is a rolling 24 hours instead. The two answer different questions and are
 * asserted separately.
 */
@Entity(
    tableName = "watches",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatchEntity(
    @PrimaryKey val bunnyId: String,
    val startedAt: Instant,
    val endsAt: Instant,
    /** The last calendar day this watch's nag was posted, if it ever has been. */
    val lastNaggedOn: LocalDate? = null,
)

/**
 * A watch as everything that reads one sees it — resolved from `(row, now)` and stored nowhere.
 *
 * Kotlin note: a sealed interface is the discriminated union of this codebase. `when` over one is
 * exhaustive without an `else`, so a fourth state would stop every reader compiling, which is the
 * point.
 */
sealed interface WatchState {
    /** No watch, or one already resolved. The overwhelmingly common answer. */
    data object None : WatchState

    /** Running, and nagging. */
    data class Active(
        val watch: WatchEntity,
        /** Rounded **up**, so the last few hours read "1 day left" rather than "0 days left". */
        val daysLeft: Int,
    ) : WatchState

    /**
     * Past [WatchEntity.endsAt] and not yet answered.
     *
     * **Expiry is what stops the nagging; the prompt is only about re-arming**, so an expired watch
     * is deliberately not an active one — it nags nobody, and it excludes nobody from the healthy
     * day's pre-selection. The row survives only until the owner answers the prompt.
     */
    data class Expired(
        val watch: WatchEntity,
    ) : WatchState
}

/**
 * Where a watch stands right now. Pure over `(row, now)`, which is what makes active / expired /
 * absent a case table rather than something to catch on a phone a week later.
 */
fun watchState(
    watch: WatchEntity?,
    now: Instant,
): WatchState =
    when {
        watch == null -> WatchState.None
        !now.isBefore(watch.endsAt) -> WatchState.Expired(watch)
        else -> WatchState.Active(watch, daysLeft(now, watch.endsAt))
    }

/** Whether this bunny is under a *running* watch — the one question the exclusions ask. */
fun WatchState.isActive(): Boolean = this is WatchState.Active

/**
 * Whole days remaining, **rounded up**.
 *
 * A 7-day watch reads "7 days left" the moment it starts, and its final afternoon reads "1 day
 * left". Rounding down would spend the last day of every watch claiming there is none, which reads
 * as a watch that has already ended while it is still nagging.
 */
private fun daysLeft(
    now: Instant,
    endsAt: Instant,
): Int {
    val seconds = Duration.between(now, endsAt).seconds
    val perDay = Duration.ofDays(1).seconds
    return ((seconds + perDay - 1) / perDay).toInt()
}

/** Where a watch of [duration] started at [now] would end. */
fun watchEndsAt(
    now: Instant,
    duration: WatchDuration,
): Instant = now.plus(Duration.ofDays(duration.days))
