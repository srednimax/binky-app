package app.binky.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads and writes weighings, and **owns the acknowledgment watermark's lifecycle**.
 *
 * "Derived on read" describes the *flag*; discarding the watermark is a **write**, and it lives here
 * rather than anywhere else on purpose (ADR-0001). Not the read path — under "All bunnies" every
 * vitals card evaluates the flag, so N cards would race to delete the same row, and *derived on
 * read* must not come to mean *writes on read*. Not the `ViewModel` — the sample-data seeder writes
 * through this repository, and so will Phase 5's visit-recorded weight, and both would miss it.
 *
 * The invariant every write path here maintains: **a stored acknowledgment row implies the raw
 * trigger was true as of the last weight write.** That is what lets the flag stay a pure function of
 * `(series, acknowledgment)` with no walk back through history.
 */
class WeightRepository(
    private val database: BunnyDatabase,
) {
    private val weightDao = database.weightDao()

    /** Only ever read from, and only for the birthday: the gain rule's growth gate (ADR-0028). */
    private val bunnyDao = database.bunnyDao()

    /** In ADR-0021's stated total order, newest first. */
    fun series(bunnyId: String): Flow<List<WeightEntity>> = weightDao.series(bunnyId)

    fun acknowledgment(bunnyId: String): Flow<TrendAcknowledgmentEntity?> = weightDao.acknowledgment(bunnyId)

    /**
     * What this bunny already has at exactly [recordedAt] — the entry form's collision check
     * (ADR-0021).
     *
     * A read, and the *only* thing this repository does about collisions: the prompt is UI-level, so
     * writes through here still produce the tied rows the trend tests want.
     */
    suspend fun existingAt(
        bunnyId: String,
        recordedAt: Instant,
    ): List<WeightEntity> = weightDao.weightsAt(bunnyId, recordedAt)

    /**
     * Adds a weighing and returns its id.
     *
     * An insert **re-reads the series and discards the acknowledgment only if it has gone stale** —
     * because the flag clears exactly when the trigger does, "discard on first non-trip" *is* "the
     * episode ended". Without it a months-old acknowledgment of a since-recovered episode silences a
     * genuinely new drop: `2500, 2500, 2500` establishes a baseline, `2300` fires and is
     * acknowledged, `2500` takes the trigger false, and the next `2300` is not below the 2300
     * watermark by more than the floor. Inserts do not discard unconditionally — a new reading either
     * trips or does not, which the trigger and 2b's re-raise bar already handle (ADR-0001).
     *
     * **Staleness is [evaluateTrend]'s own judgement, not a second copy of it here.** Since ADR-0028
     * the flag has two directions, and a watermark can be stale in a second way: taken for a gain and
     * now facing a loss, or the reverse. Asking the evaluation is what stops the two definitions
     * drifting apart — and it is why this reads the bunny's birthday, which is the gain rule's growth
     * gate.
     *
     * One transaction, so no reader can observe the row inserted and the watermark not yet judged.
     */
    suspend fun add(weight: WeightEntity): String {
        require(weight.grams > 0) { "A weighing is a positive number of grams" }
        database.withTransaction {
            weightDao.insert(weight)
            val evaluation =
                evaluateTrend(
                    series = weightDao.seriesNow(weight.bunnyId).map { it.toWeighing() },
                    acknowledgment = weightDao.acknowledgmentNow(weight.bunnyId)?.toAcknowledgment(),
                    growth = growthStageNow(bunnyDao.bunnyNow(weight.bunnyId)?.birthDate),
                )
            if (evaluation.watermarkIsStale) weightDao.deleteAcknowledgment(weight.bunnyId)
        }
        return weight.id
    }

    /**
     * Edits a weighing — **value as well as timestamp** — and discards the watermark
     * **unconditionally** (ADR-0001).
     *
     * Unconditional, and deliberately wider than "the weight it was acknowledged against", because
     * editing a weight in the *baseline* can deepen the real drop while leaving the current reading
     * — and therefore the watermark comparison — untouched, so a narrower rule stays silent on a drop
     * that just got worse. The wider rule is also the simpler one to build: it needs no "was this the
     * acknowledged row?" test, so it cannot be right only in the case someone thought of. The cost is
     * one extra re-acknowledge when an owner corrects an old unrelated typo, which is rare,
     * deliberate, and one tap.
     */
    suspend fun update(weight: WeightEntity) {
        require(weight.grams > 0) { "A weighing is a positive number of grams" }
        database.withTransaction {
            weightDao.update(weight)
            weightDao.deleteAcknowledgment(weight.bunnyId)
        }
    }

    /** Deletes a weighing, discarding the watermark unconditionally for the same reason as [update]. */
    suspend fun delete(id: String) {
        database.withTransaction {
            val weight = weightDao.weightNow(id) ?: return@withTransaction
            weightDao.deleteById(id)
            weightDao.deleteAcknowledgment(weight.bunnyId)
        }
    }

    /**
     * Records that the owner has seen the current flag, against the weighing that raised it.
     *
     * Episode-scoped: it stores the weight it was acknowledged at, and 2b's re-raise bar lets a
     * later reading break back through when it falls below that watermark by more than the noise
     * floor — a tighter bar than the 5 % trigger, because a bunny already flagged *and* acknowledged
     * must not be allowed to slide a further 5 % in silence.
     *
     * Does nothing for a bunny with no weighings: there is no flag to have seen.
     */
    suspend fun acknowledgeTrend(
        bunnyId: String,
        at: Instant = Instant.now(),
    ) {
        database.withTransaction {
            val current = weightDao.seriesNow(bunnyId).firstOrNull() ?: return@withTransaction
            weightDao.upsertAcknowledgment(
                TrendAcknowledgmentEntity(
                    bunnyId = bunnyId,
                    weightId = current.id,
                    grams = current.grams,
                    acknowledgedAt = at,
                ),
            )
        }
    }
}
