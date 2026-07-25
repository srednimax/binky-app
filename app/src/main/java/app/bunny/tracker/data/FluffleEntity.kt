package app.bunny.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * The set of bunnies that live together, sharing a space and litter tray. Membership is the
 * `bunny.fluffleId` FK, so this row carries only the group's own identity.
 *
 * [name] is optional: named, it shows as the owner's label ("The Girls"); unnamed, it renders by
 * its members ("Thumper & Clover") — joined through a string resource, never concatenated
 * (ADR-0008, ADR-0013).
 *
 * This is *not* the observation group. Who lives together is mutable current state; who a given
 * shared observation covered is an immutable historical fact stamped as its own `groupId` in
 * Phase 2. Conflating them would let re-bonding rewrite history (ADR-0008).
 */
@Entity(tableName = "fluffles")
data class FluffleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
)
