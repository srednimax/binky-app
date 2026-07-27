package app.binky.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Who lives with whom. "Lives with" is the on-screen label; Fluffle is the code word.
 *
 * Every membership change runs through [dissolveIfBelowTwo] — **one predicate shared by editing,
 * deleting and archiving** rather than a rule per path (ADR-0008). That is what makes the awkward
 * cases fall out instead of needing exceptions: archiving a member changes nothing because the
 * archived bunny is still a member, and deleting from a trio whose third member is archived leaves
 * the row standing because the count does not drop below two.
 */
class FluffleRepository(
    private val database: BunnyDatabase,
) {
    private val bunnyDao = database.bunnyDao()
    private val fluffleDao = database.fluffleDao()

    fun fluffle(fluffleId: String): Flow<FluffleEntity?> = fluffleDao.fluffle(fluffleId)

    /** Members in display order, archived ones included — the caller decides how to show them. */
    fun members(fluffleId: String): Flow<List<BunnyEntity>> = bunnyDao.membersOf(fluffleId)

    /** A blank name is stored as null, so the label falls back to the members' names. */
    suspend fun rename(
        fluffleId: String,
        name: String?,
    ) = fluffleDao.setName(fluffleId, name?.trim()?.takeIf { it.isNotEmpty() })

    /**
     * Declares that [bunnyId] and [otherBunnyId] share a space and litter tray.
     *
     * The join is **symmetric**: both end up on one `fluffleId` row. If [otherBunnyId] already
     * lives with someone, [bunnyId] joins that existing group rather than forming a rival pair —
     * a bunny has exactly one fluffle, matching the biology. A group either of them leaves is put
     * through the dissolve predicate.
     */
    suspend fun livesWith(
        bunnyId: String,
        otherBunnyId: String,
    ) {
        require(bunnyId != otherBunnyId) { "A bunny cannot live with itself" }
        database.withTransaction {
            val bunny = bunnyDao.bunnyNow(bunnyId) ?: return@withTransaction
            val other = bunnyDao.bunnyNow(otherBunnyId) ?: return@withTransaction
            if (bunny.fluffleId != null && bunny.fluffleId == other.fluffleId) return@withTransaction

            // An existing household wins over creating one, so nobody is moved out of a group
            // they are already in unnecessarily. The other bunny's is preferred, which is what
            // makes "Thumper now lives with Clover" put Thumper into Clover and Hazel's trio.
            val target = other.fluffleId ?: bunny.fluffleId ?: FluffleEntity().also { fluffleDao.insert(it) }.id

            for (member in listOf(bunny, other)) {
                if (member.fluffleId == target) continue
                bunnyDao.setFluffleId(member.id, target)
                member.fluffleId?.let { dissolveIfBelowTwo(it) }
            }
        }
    }

    /** The ordinary bond break: this bunny lives alone from now on. */
    suspend fun leaveFluffle(bunnyId: String) {
        database.withTransaction {
            val previous = bunnyDao.bunnyNow(bunnyId)?.fluffleId ?: return@withTransaction
            bunnyDao.setFluffleId(bunnyId, null)
            dissolveIfBelowTwo(previous)
        }
    }

    /**
     * A fluffle dissolves when it would be left with one member, **counting archived ones** — one
     * bunny shares a tray with nobody (ADR-0008). The survivor reverts to solo and the row goes, in
     * the caller's transaction.
     *
     * A consequence to accept: a custom name is therefore ephemeral. Dissolving discards it, and
     * re-bonding later means naming afresh — which is correct, because the group genuinely
     * dissolved.
     *
     * Must be called from inside a transaction; every caller here does.
     */
    suspend fun dissolveIfBelowTwo(fluffleId: String) {
        if (bunnyDao.membersOfNow(fluffleId).size >= 2) return
        bunnyDao.clearFluffle(fluffleId)
        fluffleDao.deleteById(fluffleId)
    }
}
