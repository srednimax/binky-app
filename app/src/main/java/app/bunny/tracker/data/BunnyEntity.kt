package app.bunny.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Biological sex. `UNKNOWN` is a real, common value — a rescue of unknown history is the normal
 * case — not a missing one, which is why this is not nullable (ADR-0016).
 *
 * Kotlin note: an enum is closer to a TypeScript `enum` than to a union of string literals. It is
 * stored by [name], never by ordinal (house rule), so adding a value later cannot rewrite history.
 */
enum class Sex { MALE, FEMALE, UNKNOWN }

/**
 * Neuter status. Its own enum rather than a `Boolean?` for the same reason as [Sex]: "unknown" is
 * an answer, not an absence, and an unspayed female's cancer risk is health context a vet wants
 * (ADR-0016).
 */
enum class NeuterStatus { YES, NO, UNKNOWN }

/**
 * One bunny. `name` is the only required field — trimmed, empty rejected, and duplicates allowed
 * (ADR-0016). There is deliberately no target weight: the trend flag is relative to the bunny's own
 * baseline, so an absolute ideal would only invite the thinness judgement ADR-0001 avoids.
 *
 * Kotlin note: the defaults below mean `BunnyEntity(name = "Thumper")` is a complete row — Kotlin
 * evaluates a default expression per call, so each construction gets its own id and timestamp
 * (unlike a JS default object literal shared by reference).
 */
@Entity(
    tableName = "bunnies",
    foreignKeys = [
        ForeignKey(
            entity = FluffleEntity::class,
            parentColumns = ["id"],
            childColumns = ["fluffleId"],
            // Deleting a fluffle row must never delete a bunny — it only ends the living
            // arrangement (ADR-0008).
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("fluffleId")],
)
data class BunnyEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Relative, `avatars/<uuid>.jpg`, resolved against `filesDir` at read time (house rule). */
    val avatarPath: String? = null,
    val birthDate: LocalDate? = null,
    /** An approximate birthdate renders as "~2 years old", never as a date (ADR-0016). */
    val birthDateApproximate: Boolean = false,
    val sex: Sex = Sex.UNKNOWN,
    val neutered: NeuterStatus = NeuterStatus.UNKNOWN,
    val breed: String? = null,
    val colour: String? = null,
    /** Who this bunny lives with. Null for a solo bunny. See [FluffleEntity]. */
    val fluffleId: String? = null,
    /** Set when archived; archiving is not deleting (ADR-0004). */
    val archivedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
