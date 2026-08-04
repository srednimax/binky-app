package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One weighing.
 *
 * **Grams as `Int`, never a float** (house rule): a scale reads out in whole grams, and a float
 * invites accumulated rounding into the one series the app makes a safety claim about. The display
 * unit is a preference (see [WeightUnit]); changes are always shown in grams.
 *
 * Two timestamps, because they answer different questions. [recordedAt] is *the moment on the
 * scale* — back-datable, since weighing in the morning and logging in the evening is the normal
 * case — and it is what the chart and the trend baseline order by. [createdAt] is when the row was
 * typed, and its only job is breaking ties in that order (ADR-0021).
 *
 * **[visitId] is the whole origin tag, and there is deliberately no `source` column** (ADR-0017).
 * [WeightSource] is derived from this one field, because two stored facts that can disagree is
 * exactly what 4b refused for the intended day-of-month. `SET NULL` then makes "keep the weighing
 * when the visit goes" correct *by construction* rather than by a repository remembering to clear a
 * second field.
 */
@Entity(
    tableName = "weights",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            // A weighing is meaningless without the bunny it belongs to, so it goes with them.
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            // Deleting the visit must not delete the number the vet read off the scale — it only
            // stops claiming a visit it no longer has.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    // Every read is "this bunny's series, in time order" — the composite index serves both halves.
    //
    // The second index is **unique**, and that is load-bearing rather than tidy: nothing else stops
    // two rows claiming one visit, and "one row, never a copy" (ADR-0017) has to be a property of the
    // schema rather than of the editor being careful, or of a backup having been written by a build
    // where it was. NULLs are distinct in SQLite, so every manual weighing stays unconstrained.
    indices = [Index(value = ["bunnyId", "recordedAt"]), Index(value = ["visitId"], unique = true)],
)
data class WeightEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    val grams: Int,
    val recordedAt: Instant,
    val createdAt: Instant = Instant.now(),
    // Declared last because that is where `ALTER TABLE ... ADD COLUMN` physically puts it, and
    // `MIGRATION_5_6` is a transcription of the schema JSON — keeping the two in the same order is
    // one less difference to explain when reading them side by side.
    val visitId: String? = null,
)

/**
 * Where a weighing came from. **Derived, never stored** (ADR-0017) — see [WeightEntity.visitId].
 *
 * Kotlin note: an extension property rather than a field, so there is no way to construct a
 * `WeightEntity` whose source disagrees with its `visitId`. This is the closed-vocabulary equivalent
 * of a getter on a JS object that reads another field, except the compiler enforces that nobody
 * assigns to it.
 */
enum class WeightSource { MANUAL, VISIT }

val WeightEntity.source: WeightSource
    get() = if (visitId != null) WeightSource.VISIT else WeightSource.MANUAL

/**
 * The trend flag's **only** persisted piece: the episode-scoped acknowledgment watermark (ADR-0001).
 * The flag itself is derived on read and never stored.
 *
 * `bunnyId` is the primary key, so a bunny can have at most one live episode — the constraint states
 * the rule rather than leaving it to whoever writes the next insert. It is **also** a foreign key to
 * `bunnies`: reaching the bunny only through `weights` would work by accident, and a two-hop cascade
 * is a rule someone has to remember rather than one the database enforces. Being the primary key
 * gives that FK its index for free; `weightId` needs its own.
 *
 * Both value columns earn their place. [grams] is the re-raise bar's comparison point, and reading
 * it back through [weightId] would be a join onto a row that an edit may have moved — the watermark
 * has to remember the number it was taken against. [acknowledgedAt] is what the flag's copy dates.
 *
 * A table rather than columns on `bunnies`, so discard-on-delete is a database constraint rather
 * than a rule someone has to remember.
 */
@Entity(
    tableName = "trend_acknowledgments",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WeightEntity::class,
            parentColumns = ["id"],
            childColumns = ["weightId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weightId")],
)
data class TrendAcknowledgmentEntity(
    @PrimaryKey val bunnyId: String,
    val weightId: String,
    val grams: Int,
    val acknowledgedAt: Instant,
)
