package app.binky.tracker.data

import androidx.room.withTransaction
import app.binky.tracker.media.MediaFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val media: MediaFiles,
) {
    private val observationDao = database.observationDao()

    fun forBunny(bunnyId: String): Flow<List<ObservationEntity>> = observationDao.forBunny(bunnyId)

    /** One bunny's timeline, whole groups included so a shared entry can name who it covered (ADR-0008). */
    fun timelineForBunny(bunnyId: String): Flow<List<ObservationEntity>> = observationDao.timelineForBunny(bunnyId)

    /** The combined timeline under "All bunnies" — active bunnies only, rows not yet collapsed by group. */
    val forActiveBunnies: Flow<List<ObservationEntity>> = observationDao.forActiveBunnies()

    /** Every symptom tick, for the timeline to index by observation. See [ObservationDao.allSymptomLinks]. */
    val symptomLinks: Flow<List<ObservationSymptomEntity>> = observationDao.allSymptomLinks()

    /**
     * The two multi-valued droppings fields, indexed by observation for the timeline — the same shape
     * and the same reason as [symptomLinks].
     *
     * The mapping from stored name to enum happens **here and nowhere else**, and it drops a name this
     * build does not recognise rather than substituting a member for it (ADR-0029): with a join table,
     * "not there" is a reading the app can honestly act on, which is what the nullable columns spell
     * as `null`.
     */
    val droppingsAppearance: Flow<Map<String, Set<DroppingsAppearance>>> =
        observationDao.allDroppingsAppearance().map { it.byObservation(DroppingsAppearance.entries) }

    val droppingsSizes: Flow<Map<String, Set<DroppingsSize>>> =
        observationDao.allDroppingsSizes().map { it.byObservation(DroppingsSize.entries) }

    /**
     * Every tray photo, indexed by observation and in the owner's order — the third of these
     * whole-table reads, and the one the timeline draws rather than counts.
     *
     * No name-to-enum mapping here, unlike the two above: a path is a path. What it shares with them
     * is that a row pointing at a file that is gone is not an error — a restore may legitimately lack
     * photos, and the placeholder is the house rule (never a crash).
     */
    val trayPhotos: Flow<Map<String, List<String>>> =
        observationDao.allTrayPhotos().map { rows ->
            rows.groupBy { it.observationId }.mapValues { (_, links) -> links.map { it.path } }
        }

    /** One-shot read, for the editor: a form fed by a `Flow` would fight the owner's typing. */
    suspend fun observationNow(id: String): ObservationEntity? = observationDao.observationNow(id)

    /**
     * The whole tray fact for one row — the columns *and* the three join-table halves, which no longer
     * come back together from a single read (ADR-0029, and its schema-8 amendment).
     *
     * One method rather than four call sites remembering to make four reads, because a tray fact
     * assembled with one of them silently missing is the easiest bug this change makes possible.
     */
    suspend fun trayFactsNow(observationId: String): TrayFacts? {
        val row = observationDao.observationNow(observationId) ?: return null
        return row.trayFacts(
            droppingsSizes = observationDao.droppingsSizeNamesNow(row.id).toEnums(DroppingsSize.entries),
            droppingsAppearance =
                observationDao.droppingsAppearanceNamesNow(row.id).toEnums(DroppingsAppearance.entries),
            trayPhotoPaths = observationDao.trayPhotosNow(row.id),
        )
    }

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
                // The tray's multi-valued half, written per participant inside the same transaction:
                // "identical across every row" is join rows now, not a `copy()` (ADR-0029).
                writeTraySets(row.id, facts.tray)
                ids += row.id
            }
        }
        return ids
    }

    /**
     * Replaces one row's multi-valued tray facts — the two droppings sets and the photos.
     * **Replaced, not merged**, on the same grounds as the symptom links: the form's state is the
     * whole answer, so a value the owner unticked, or a photo they removed, has to disappear.
     *
     * The photos carry their index as [ObservationPhotoEntity.position], so the strip comes back in the
     * order the owner left it in rather than in whatever order the rows happen to be read.
     */
    private suspend fun writeTraySets(
        observationId: String,
        tray: TrayFacts,
    ) {
        observationDao.clearDroppingsAppearance(observationId)
        observationDao.clearDroppingsSizes(observationId)
        observationDao.clearTrayPhotos(observationId)
        observationDao.linkDroppingsAppearance(
            tray.droppingsAppearance.map { ObservationDroppingsAppearanceEntity(observationId, it) },
        )
        observationDao.linkDroppingsSizes(
            tray.droppingsSizes.map { ObservationDroppingsSizeEntity(observationId, it) },
        )
        observationDao.linkTrayPhotos(
            // `distinct()` before indexing, so a path offered twice cannot claim two positions and
            // then lose one to the composite key's IGNORE — the surviving row would keep whichever
            // index it happened to be inserted with.
            tray.trayPhotoPaths.distinct().mapIndexed { index, path ->
                ObservationPhotoEntity(observationId, path, index)
            },
        )
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
        var orphanedPhotos: List<String> = emptyList()
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            // Read before the rewrite drops them: photos the owner has just removed are about to stop
            // being referenced by anything. A set now rather than one path, so this is a difference
            // rather than an inequality.
            val previousPhotos = observationDao.trayPhotosNow(row.id)
            val groupId = row.groupId
            if (groupId != null) {
                observationDao.updateTrayForGroup(
                    groupId = groupId,
                    droppingsAmount = tray.droppingsAmount,
                    cecotropes = tray.cecotropes,
                )
            } else {
                observationDao.updateTrayForObservation(
                    id = row.id,
                    droppingsAmount = tray.droppingsAmount,
                    cecotropes = tray.cecotropes,
                )
            }
            // Every participant's, because the sets are tray-level like the columns beside them. The
            // group is a fluffle, so the loop is over a handful of rows at most.
            val rows = if (groupId != null) observationDao.groupNow(groupId) else listOf(row)
            for (member in rows) writeTraySets(member.id, tray)

            // Asked after every participant's rows are rewritten, so the count is the truth about the
            // whole group rather than about the row that happened to be on screen.
            orphanedPhotos =
                (previousPhotos - tray.trayPhotoPaths.toSet())
                    .filter { observationDao.countWithTrayPhoto(it) == 0 }
        }
        // Outside the transaction, so a rollback cannot leave live rows pointing at a deleted file.
        orphanedPhotos.forEach(media::delete)
    }

    /**
     * Corrects **when** it was noticed — every participant's row, like the tray facts and for the
     * same reason: one real-world moment, however many bunnies were in the room.
     *
     * Nothing here rejects a future [Instant]; that check belongs to the form, on the same terms as
     * weight (see the class doc).
     */
    suspend fun updateRecordedAt(
        observationId: String,
        recordedAt: Instant,
    ) {
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId
            if (groupId != null) {
                observationDao.setRecordedAtForGroup(groupId, recordedAt)
            } else {
                observationDao.setRecordedAtForObservation(row.id, recordedAt)
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

            // What is *stored*, sets included — the tray fact was always about both bunnies, which is
            // the whole reason the correction is being made.
            val tray =
                row.trayFacts(
                    droppingsSizes = observationDao.droppingsSizeNamesNow(row.id).toEnums(DroppingsSize.entries),
                    droppingsAppearance =
                        observationDao.droppingsAppearanceNamesNow(row.id).toEnums(DroppingsAppearance.entries),
                    trayPhotoPaths = observationDao.trayPhotosNow(row.id),
                )
            val added =
                ObservationEntity(
                    bunnyId = bunnyId,
                    groupId = groupId,
                    recordedAt = row.recordedAt,
                    createdAt = row.createdAt,
                ).withTrayFacts(tray)
            observationDao.insert(added)
            writeTraySets(added.id, tray)
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
        var orphanedPhotos: List<String> = emptyList()
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId ?: return@withTransaction
            val members = observationDao.groupNow(groupId)
            val leaving = members.firstOrNull { it.bunnyId == bunnyId } ?: return@withTransaction

            // Read while the rows still exist: the delete cascades this bunny's join rows away, and
            // afterwards there is nothing left to ask what it was pointing at.
            val leavingPhotos = observationDao.trayPhotosNow(leaving.id)
            observationDao.deleteById(leaving.id)
            val remaining = members.filter { it.id != leaving.id }
            if (remaining.size == 1) observationDao.setGroupId(remaining.single().id, null)
            orphanedPhotos = leavingPhotos.filter { observationDao.countWithTrayPhoto(it) == 0 }
        }
        orphanedPhotos.forEach(media::delete)
    }

    /**
     * Deletes the observation — **all of it**, every participant's row.
     *
     * This is "that observation was wrong or never happened", which is one event about one real-world
     * moment however many bunnies it covered. The narrower "this bunny wasn't in it" is
     * [removeParticipant], and keeping the two apart is what stops a confirmation dialog having to
     * guess which the owner meant. The confirmation names who it affects (ADR-0008).
     *
     * Symptom links, droppings values and photo *rows* go with the rows through the cascade; the
     * symptoms themselves never do. **The photo files are the exception a cascade cannot express**:
     * each path is duplicated onto every participant, so a file goes only once nothing references it
     * (ADR-0029).
     */
    suspend fun delete(observationId: String) {
        var orphanedPhotos: List<String> = emptyList()
        database.withTransaction {
            val row = observationDao.observationNow(observationId) ?: return@withTransaction
            val groupId = row.groupId
            // Read while the rows still exist, because the delete cascades the join rows away with
            // them. The whole group's, not this row's: every participant carries the same set, but a
            // group whose rows were written before schema 8 is the case where "the same" is worth not
            // assuming.
            val photos =
                if (groupId != null) {
                    observationDao.groupNow(groupId).flatMap { observationDao.trayPhotosNow(it.id) }.distinct()
                } else {
                    observationDao.trayPhotosNow(row.id)
                }
            if (groupId != null) observationDao.deleteByGroup(groupId) else observationDao.deleteById(row.id)
            // Asked *after* the delete, so the answer is who is left rather than who was there.
            orphanedPhotos = photos.filter { observationDao.countWithTrayPhoto(it) == 0 }
        }
        // Rows first, then the files, best-effort — the same ordering `PhotoRepository.delete` takes,
        // and outside the transaction so a rollback cannot strand a live row on a deleted file.
        orphanedPhotos.forEach(media::delete)
    }
}

/**
 * Stored names to values, **dropping the ones this build does not know**.
 *
 * The join table's answer to the nullable columns' `null` fallback (ADR-0029): a value written by a
 * later build reads as *not there*, which is a thing the app can honestly show, rather than as some
 * substitute member it would then render as a fact the owner never recorded.
 */
private fun <T : Enum<T>> List<String>.toEnums(entries: List<T>): Set<T> =
    mapNotNullTo(mutableSetOf()) { name -> entries.firstOrNull { it.name == name } }

/** The same mapping for the whole-table reads, indexed by observation for the timeline. */
private fun <T : Enum<T>> List<DroppingsValueLink>.byObservation(entries: List<T>): Map<String, Set<T>> =
    groupBy { it.observationId }
        .mapValues { (_, links) -> links.map { it.value }.toEnums(entries) }
