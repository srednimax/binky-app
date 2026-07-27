package app.binky.tracker.data

import androidx.room.withTransaction
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads and writes bunnies. Thin over the DAO, and the owner of the things a DAO cannot own: the
 * fluffle dissolve predicate, clearing a deleted bunny out of the persisted selection, and removing
 * the avatar file a cascade cannot reach.
 */
class BunnyRepository(
    private val database: BunnyDatabase,
    private val fluffles: FluffleRepository,
    private val preferences: AppPreferences,
    private val media: MediaFiles,
) {
    private val bunnyDao = database.bunnyDao()

    val activeBunnies: Flow<List<BunnyEntity>> = bunnyDao.activeBunnies()

    val archivedBunnies: Flow<List<BunnyEntity>> = bunnyDao.archivedBunnies()

    fun bunny(id: String): Flow<BunnyEntity?> = bunnyDao.bunny(id)

    /** The breed picker's stored half — see [BunnyDao.breeds] for why this is a query and not a table. */
    val breeds: Flow<List<String>> = bunnyDao.breeds()

    /**
     * One-shot read, for the editor: a form fed by a `Flow` would fight the owner's typing every
     * time the row it is editing emits again.
     */
    suspend fun bunnyNow(id: String): BunnyEntity? = bunnyDao.bunnyNow(id)

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
     * through the dissolve predicate, clears it from the persisted selection so no id dangles, and
     * deletes the avatar file — Room's cascade deletes rows, never files.
     *
     * Kotlin note: `avatarPath` is assigned inside the lambda below and read after it. Kotlin
     * closures capture `var`s by reference, unlike Java's effectively-final rule, so this needs no
     * holder object.
     */
    suspend fun delete(id: String) {
        var avatarPath: String? = null
        database.withTransaction {
            val bunny = bunnyDao.bunnyNow(id) ?: return@withTransaction
            avatarPath = bunny.avatarPath
            bunnyDao.deleteById(id)
            bunny.fluffleId?.let { fluffles.dissolveIfBelowTwo(it) }
        }
        // Deliberately after the transaction: a rolled-back delete would otherwise leave a live row
        // pointing at a file that is already gone, which is the failure ADR-0020's file-first rule
        // exists to avoid, in reverse.
        avatarPath?.let { media.delete(it) }
        preferences.clearSelectionIfSet(id)
    }

    /** What deleting this bunny would destroy, for the confirmation (ADR-0004). */
    suspend fun recordCounts(id: String): RecordCounts =
        bunnyDao.recordCounts(id) ?: RecordCounts(soleOwnedRecords = 0, sharedRecords = 0)
}
