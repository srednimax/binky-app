package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * **A dated label, and nothing more** (ADR-0031).
 *
 * An owner asked for two things in one sentence (2026-08-23): *when was the last vet visit or nail
 * trim*, and *other events the user would like to remember*. The first is history the app already
 * stores with nowhere to see it together — that is the timeline, and it stores nothing. This entity is
 * the second: the appointments and keepsakes the model could not represent at all, because
 * `visits.visitedOn` is recorded after the fact and ADR-0018's care reminders are recurring by
 * construction.
 *
 * Three omissions, each deliberate:
 *
 * - **No type enum.** ADR-0018 gives a care reminder `{label, interval, optional type}` because the
 *   known kinds of care genuinely are a closed list. There is no closed list of things an owner wants
 *   to remember, so the label is the whole record — the same free-text precedent a custom care
 *   reminder already sets.
 * - **No recurrence.** Care reminders own repetition. An event that repeats *is* a care reminder, and
 *   two spellings of one fact is what this codebase keeps refusing.
 * - **Per bunny, [bunnyId] non-null**, the shape `care_reminders` has. An event covering two bunnies
 *   is two rows. ADR-0008's group machinery exists where the *attribution* would otherwise be a lie —
 *   a shared tray — and here it would buy only convenience, at the price of a second sharing model.
 *
 * [occursOn] is a [LocalDate] and not an [Instant]: an anniversary or an appointment is a day, and
 * storing a moment would invent a time of day nobody entered. It is freely in the past or the future,
 * which is what lets one record both halves of the request.
 *
 * [notifiedAt] is what stops the daily sweep posting the same event twice (ADR-0024). It records
 * *when the notification went out* rather than which date it was for, and that is the difference from
 * [CareReminderEntity.notifiedForDueOn]: a care reminder comes due again and again, so it has to
 * remember *which* occurrence it announced, while an event has exactly one date in its life.
 *
 * [calendarHandedOffAt] records only that ADR-0014's hand-off happened. The app does not own the
 * calendar entry, cannot see whether it still exists, and must not claim to.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            // An event about a bunny is meaningless without the bunny, like every other per-bunny row.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Every read is "this bunny's events, by date" — the timeline's and the sweep's shapes both.
    indices = [Index(value = ["bunnyId", "occursOn"])],
)
data class EventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    /** Free text, trimmed and never blank — enforced on write in [EventRepository]. */
    val label: String,
    val occursOn: LocalDate,
    val note: String? = null,
    val notifiedAt: Instant? = null,
    val calendarHandedOffAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
