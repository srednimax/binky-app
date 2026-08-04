package app.binky.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The vet directory — **app-wide**, so nothing here takes a bunny (ADR-0017).
 *
 * Thin on purpose: a vet is an address-book entry with no derivation behind it. The one rule worth
 * a class is that a **vet outlives its visits**, and that is enforced by `SET NULL` in the schema
 * rather than by anything written here.
 */
class VetRepository(
    private val database: BunnyDatabase,
) {
    private val vetDao = database.vetDao()

    /** Sorted by name, case-insensitively, which is the only order a directory can be read in. */
    val vets: Flow<List<VetEntity>> = vetDao.all()

    fun vet(id: String): Flow<VetEntity?> = vetDao.byId(id)

    suspend fun vetNow(id: String): VetEntity? = vetDao.byIdNow(id)

    /** Returns the id, so the visit editor's inline "add a new vet" can select what it just made. */
    suspend fun add(vet: VetEntity): String {
        val row = vet.validated()
        vetDao.insert(row)
        return row.id
    }

    suspend fun update(vet: VetEntity) {
        vetDao.update(vet.validated())
    }

    /**
     * Removes the directory entry. **Every visit that named it stays**, with a null `vetId`
     * (ADR-0017): a clinic closing is not a reason to lose a health record.
     */
    suspend fun delete(id: String) {
        vetDao.deleteById(id)
    }
}

/**
 * Vet visits, and the **one** weighing a visit may carry (ADR-0017).
 *
 * The whole point of this class is that a visit and its weighing are written **in one transaction**
 * or not at all. Two calls from a `ViewModel` would leave a crash between them showing a visit whose
 * weight was never recorded, or worse a weight nothing points at.
 *
 * The weight side goes **through [WeightRepository]** rather than through the DAO, which is what
 * `WeightRepository`'s own comment predicted: it owns ADR-0001's acknowledgment watermark, and a
 * visit weighing is a weighing — it can raise or clear a trend episode exactly as a typed one does.
 *
 * Kotlin note: Room's `withTransaction` is re-entrant. The nested one inside each `WeightRepository`
 * write joins this transaction rather than opening a second, so "the visit row and the weight row,
 * or neither" survives the indirection.
 */
class VisitRepository(
    private val database: BunnyDatabase,
    private val weights: WeightRepository,
) {
    private val visitDao = database.visitDao()
    private val weightDao = database.weightDao()

    /** This bunny's visits, newest first, each with its vet's name and its weighing. */
    fun visits(bunnyId: String): Flow<List<VisitDetails>> = visitDao.detailsForBunny(bunnyId)

    /** One visit, watched — null once the row is gone, which is how its editor learns to close. */
    fun visit(id: String): Flow<VisitDetails?> = visitDao.details(id)

    /**
     * Records a visit and, if the vet weighed the bunny, **one** weight row tagged with it.
     *
     * [grams] is null when no weighing was taken, which is the ordinary case for a consultation.
     * [now] and [zone] are parameters so `visitWeighingAt`'s clamp is testable rather than
     * whatever the device's clock happened to say.
     */
    suspend fun add(
        visit: VisitEntity,
        grams: Int? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val row = visit.validated(today = LocalDate.now(zone))
        database.withTransaction {
            visitDao.insert(row)
            if (grams != null) {
                weights.add(
                    WeightEntity(
                        bunnyId = row.bunnyId,
                        grams = grams,
                        recordedAt = visitWeighingAt(row.visitedOn, now, zone),
                        visitId = row.id,
                    ),
                )
            }
        }
        return row.id
    }

    /**
     * Edits a visit, and with it the weighing recorded at it — **that row**, never a second one.
     *
     * The three cases are the whole of "there is no path that produces two numbers": a weight typed
     * where there was none inserts, a weight cleared deletes the row it had, and a weight changed
     * updates in place. Moving the visit's **date** re-derives the weighing's instant in the same
     * step, because the two are one fact and a stale timestamp would put the vet's number on the
     * wrong day of the chart.
     */
    suspend fun update(
        visit: VisitEntity,
        grams: Int? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val row = visit.validated(today = LocalDate.now(zone))
        database.withTransaction {
            visitDao.update(row)
            val existing = weightDao.weightForVisitNow(row.id)
            val recordedAt = visitWeighingAt(row.visitedOn, now, zone)
            when {
                grams == null -> existing?.let { weights.delete(it.id) }
                existing == null ->
                    weights.add(
                        WeightEntity(
                            bunnyId = row.bunnyId,
                            grams = grams,
                            recordedAt = recordedAt,
                            visitId = row.id,
                        ),
                    )
                else -> weights.update(existing.copy(grams = grams, recordedAt = recordedAt))
            }
        }
    }

    /**
     * Deletes a visit, having **stated** what happens to its weighing rather than guessing.
     *
     * [keepWeighing] true needs no statement of its own: the `SET NULL` foreign key clears the tag
     * and leaves the row standing, which is the schema making "keep it" correct by construction. The
     * destructive half is the one that has to be asked for.
     */
    suspend fun delete(
        id: String,
        keepWeighing: Boolean,
    ) {
        database.withTransaction {
            if (!keepWeighing) {
                weightDao.weightForVisitNow(id)?.let { weights.delete(it.id) }
            }
            visitDao.deleteById(id)
        }
    }
}

/**
 * What a visit row has to satisfy whichever screen wrote it — the same shape as the care reminder's
 * `validated()`, and for the same reason: the place to catch it is before the database, not in the
 * composable that shrugs at it.
 *
 * The future is refused rather than clamped, on the terms every other entry in the app uses: a day
 * the owner has not reached cannot be something that happened.
 */
private fun VisitEntity.validated(today: LocalDate): VisitEntity {
    val trimmedReason = reason.trim()
    require(trimmedReason.isNotEmpty()) { "A visit needs a reason" }
    require(!visitedOn.isAfter(today)) { "A visit cannot have happened in the future" }
    return copy(reason = trimmedReason, notes = notes?.trim()?.ifEmpty { null })
}

/** A directory entry with no name would render as a blank row nobody can identify or find again. */
private fun VetEntity.validated(): VetEntity {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "A vet needs a name" }
    return copy(
        name = trimmedName,
        clinic = clinic?.trim()?.ifEmpty { null },
        phone = phone?.trim()?.ifEmpty { null },
        notes = notes?.trim()?.ifEmpty { null },
    )
}
