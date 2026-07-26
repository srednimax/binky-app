package app.bunny.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Reads and writes observations, and **owns the shared write** (ADR-0008).
 *
 * The rules a DAO cannot own all live here, and they are the ones the model is for: one group id per
 * observation, tray-level facts written identically onto every participant, the tray/individual split
 * respected by both edit paths, and the difference between *deleting a bunny* and *correcting the
 * participant list*. Each is one transaction, so no reader can catch an observation half-shared.
 *
 * Nothing here validates a future [Instant]. That check belongs to the form, on the same terms as
 * weight: a repository that silently refused a timestamp would leave the seeder and any later import
 * path unable to write history the app itself can legitimately hold.
 */
class ObservationRepository(
    private val database: BunnyDatabase,
) {
    private val observationDao = database.observationDao()

    fun forBunny(bunnyId: String): Flow<List<ObservationEntity>> = observationDao.forBunny(bunnyId)

    /** The combined timeline under "All bunnies" — active bunnies only, rows not yet collapsed by group. */
    val forActiveBunnies: Flow<List<ObservationEntity>> = observationDao.forActiveBunnies()

    /** One-shot read, for the editor: a form fed by a `Flow` would fight the owner's typing. */
    suspend fun observationNow(id: String): ObservationEntity? = observationDao.observationNow(id)

    /** Which symptoms are ticked on this observation. Hidden ones included — retiring one is not unticking it. */
    suspend fun symptomIdsNow(observationId: String): Set<String> = observationDao.symptomIdsNow(observationId).toSet()

    /** Every bunny a shared observation covered, this one included. Empty for a solo observation. */
    suspend fun participantsNow(observationId: String): List<String> {
        val groupId = observationDao.observationNow(observationId)?.groupId ?: return emptyList()
        return observationDao.groupNow(groupId).map { it.bunnyId }
    }

    /**
     * Records one observation covering [participants] — one row each, and returns their ids.
     *
     * A group id is minted **only** for more than one participant, because sharedness *is*
     * `groupId IS NOT NULL` (ADR-0008): stamping one on a solo observation would make it read
     * "observed together" with nobody.
     *
     * [facts]`.tray` lands identically on every row, which is what makes the tray fact single per
     * group by construction rather than by convention. [facts]`.individual` lands on every row too,
     * and the reason is the healthy day: *looked, no symptoms seen* is an individual fact read from the
     * same glance as the tray, so a shortcut covering a bonded pair has to be able to claim it for
     * both. Anything genuinely per-bunny — one hunched while the other is bouncing around — is set
     * afterwards through [updateIndividual], which touches exactly one row.
     *
     * Returns one id per participant, in the order given. The healthy day's snackbar needs one of them
     * for its Undo, and [delete] on any of them removes the whole observation.
     */
    suspend fun add(
        participants: List<String>,
        recordedAt: Instant,
        facts: ObservationFacts = ObservationFacts(),
        createdAt: Instant = Instant.now(),
    ): List<String> {
        val bunnyIds = participants.distinct()
        require(bunnyIds.isNotEmpty()) { "An observation covers at least one bunny" }
        val individual = facts.individual.normalised()
        // One group id for the whole write, minted outside the loop — the shared fact is the group,
        // not something each row decides for itself.
        val groupId = if (bunnyIds.size > 1) UUID.randomUUID().toString() else null

        val ids = mutableListOf<String>()
        database.withTransaction {
            for (bunnyId in bunnyIds) {
                val row =
                    ObservationEntity(
                        bunnyId = bunnyId,
                        groupId = groupId,
                        recordedAt = recordedAt,
                        createdAt = createdAt,
                    ).withTrayFacts(facts.tray)
                        .withIndividualFacts(individual)
                observationDao.insert(row)
                observationDao.linkSymptoms(individual.symptomIds.map { ObservationSymptomEntity(row.id, it) })
                ids += row.id
            }
        }
        return ids
    }

    /**
     * Edits the tray-level facts — **every participant's row, or the one row if it is solo**
     * (ADR-0008).
     *
     * [observationId] may be any row in the group: they are interchangeable for this purpose, which is
     * exactly what "single per group" means.
     */
    suspend fun updateTray(
        observationId: String,
        tray: TrayFacts,
    ) {
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId
            if (groupId != null) {
                observationDao.updateTrayForGroup(
                    groupId = groupId,
                    droppingsAmount = tray.droppingsAmount,
                    droppingsSize = tray.droppingsSize,
                    droppingsForm = tray.droppingsForm,
                    cecotropes = tray.cecotropes,
                )
            } else {
                observationDao.updateTrayForObservation(
                    id = row.id,
                    droppingsAmount = tray.droppingsAmount,
                    droppingsSize = tray.droppingsSize,
                    droppingsForm = tray.droppingsForm,
                    cecotropes = tray.cecotropes,
                )
            }
        }
    }

    /**
     * Edits **one bunny's** individual facts, symptom links included. Never touches another
     * participant's row, however shared the observation is.
     *
     * The links are replaced rather than merged: the picker's state is the whole answer, so a symptom
     * the owner unticked has to disappear.
     */
    suspend fun updateIndividual(
        observationId: String,
        individual: IndividualFacts,
    ) {
        val facts = individual.normalised()
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            observationDao.update(row.withIndividualFacts(facts))
            observationDao.clearSymptoms(row.id)
            observationDao.linkSymptoms(facts.symptomIds.map { ObservationSymptomEntity(row.id, it) })
        }
    }

    /**
     * Adds a bunny to an observation, **converting a solo one to shared** if that is what it takes
     * (ADR-0008): a group id is minted and back-filled onto the existing row, inside the transaction
     * that is already here.
     *
     * The new row inherits the group's tray-level facts — the tray fact was always about both bunnies,
     * which is the whole reason the correction is being made — and starts with blank individual fields,
     * because nobody has assessed this bunny's appetite yet and the app must not invent it (ADR-0001).
     *
     * Re-adding a bunny already covered does nothing, so a double tap cannot mint a second row for it.
     */
    suspend fun addParticipant(
        observationId: String,
        bunnyId: String,
    ) {
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val existing = row.groupId?.let { observationDao.groupNow(it) } ?: listOf(row)
            if (existing.any { it.bunnyId == bunnyId }) return@withTransaction

            // Checked for membership *before* minting, so an id is never stamped on a group of one.
            val groupId = row.groupId ?: UUID.randomUUID().toString().also { observationDao.setGroupId(row.id, it) }

            observationDao.insert(
                ObservationEntity(
                    bunnyId = bunnyId,
                    groupId = groupId,
                    recordedAt = row.recordedAt,
                    createdAt = row.createdAt,
                ).withTrayFacts(row.trayFacts()),
            )
        }
    }

    /**
     * Removes a bunny from an observation — **a correction, and deliberately not the same event as
     * deleting a bunny** (ADR-0008).
     *
     * Deletion *preserves* history: they were observed together and one of them is gone, so the
     * survivor keeps its group id and goes on reading "observed together". Correction *amends* it: the
     * owner is saying the observation never covered that bunny at all, so a correction leaving a single
     * row **clears that row's group id** and the observation becomes solo again — the exact inverse of
     * [addParticipant]'s conversion. Left as one rule, correcting *"Nugget wasn't in the room"* would
     * leave Bijou's row asserting a shared observation with nobody.
     *
     * This path exists because the healthy day's review *is* a snackbar: a few seconds of Undo is not a
     * durable review, and an owner who missed it must be able to remove a wrongly-covered bunny
     * without destroying the observation for the rest.
     */
    suspend fun removeParticipant(
        observationId: String,
        bunnyId: String,
    ) {
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId ?: return@withTransaction
            val members = observationDao.groupNow(groupId)
            val leaving = members.firstOrNull { it.bunnyId == bunnyId } ?: return@withTransaction

            observationDao.deleteById(leaving.id)
            val remaining = members.filter { it.id != leaving.id }
            if (remaining.size == 1) observationDao.setGroupId(remaining.single().id, null)
        }
    }

    /**
     * Deletes the observation — **all of it**, every participant's row.
     *
     * This is "that observation was wrong or never happened", which is one event about one real-world
     * moment however many bunnies it covered. The narrower "this bunny wasn't in it" is
     * [removeParticipant], and keeping the two apart is what stops a confirmation dialog having to
     * guess which the owner meant. The confirmation names who it affects (ADR-0008).
     *
     * Symptom links go with the rows through the cascade; the symptoms themselves never do.
     */
    suspend fun delete(observationId: String) {
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId
            if (groupId != null) observationDao.deleteByGroup(groupId) else observationDao.deleteById(row.id)
        }
    }
}
