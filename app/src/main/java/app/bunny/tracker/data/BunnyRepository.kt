package app.bunny.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads and writes bunnies. Thin over the DAO, and the owner of the things a DAO cannot own: the
 * fluffle dissolve predicate, and clearing a deleted bunny out of the persisted selection.
 */
class BunnyRepository(
    private val database: BunnyDatabase,
    private val fluffles: FluffleRepository,
    private val preferences: AppPreferences,
) {
    private val bunnyDao = database.bunnyDao()

    val activeBunnies: Flow<List<BunnyEntity>> = bunnyDao.activeBunnies()

    val archivedBunnies: Flow<List<BunnyEntity>> = bunnyDao.archivedBunnies()

    fun bunny(id: String): Flow<BunnyEntity?> = bunnyDao.bunny(id)

    /**
     * Adds a bunny and returns its id. `name` is the only required field — trimmed, empty
     * rejected, **duplicates allowed**: owners really do have two bunnies with near-identical
     * names, and the avatar is what disambiguates them in the switcher (ADR-0016).
     */
    suspend fun add(bunny: BunnyEntity): String {
        val trimmed = bunny.copy(name = bunny.name.trim())
        require(trimmed.name.isNotEmpty()) { "A bunny needs a name" }
        bunnyDao.insert(trimmed)
        return trimmed.id
    }

    /**
     * Kotlin note: `copy()` on a data class is the object-spread equivalent — `{...bunny, name}` —
     * returning a new instance rather than mutating the one passed in.
     */
    suspend fun update(bunny: BunnyEntity) {
        val trimmed = bunny.copy(name = bunny.name.trim())
        require(trimmed.name.isNotEmpty()) { "A bunny needs a name" }
        bunnyDao.update(trimmed)
    }

    /**
     * Archiving destroys nothing and changes no living arrangement: the archived bunny is still a
     * fluffle member, so the survivor of a bonded pair keeps having lived with it (ADR-0004,
     * ADR-0008). The selection is left alone too — the resolver heals on read, so unarchiving
     * restores the owner's choice (ADR-0015).
     */
    suspend fun archive(
        id: String,
        at: Instant = Instant.now(),
    ) = bunnyDao.setArchivedAt(id, at)

    suspend fun unarchive(id: String) = bunnyDao.setArchivedAt(id, null)

    /**
     * The destructive, explicit path (ADR-0004). Removes the bunny, puts any fluffle it left
     * through the dissolve predicate, and clears it from the persisted selection so no id dangles.
     *
     * The avatar *file* is removed here too once the media helper exists (checkpoint 1b) — Room's
     * cascade deletes rows, never files.
     */
    suspend fun delete(id: String) {
        database.withTransaction {
            val bunny = bunnyDao.bunnyNow(id) ?: return@withTransaction
            bunnyDao.deleteById(id)
            bunny.fluffleId?.let { fluffles.dissolveIfBelowTwo(it) }
        }
        preferences.clearSelectionIfSet(id)
    }

    /** What deleting this bunny would destroy, for the confirmation (ADR-0004). */
    suspend fun recordCounts(id: String): RecordCounts =
        bunnyDao.recordCounts(id) ?: RecordCounts(soleOwnedRecords = 0, sharedRecords = 0)
}
