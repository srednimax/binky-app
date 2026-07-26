package app.bunny.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The symptom vocabulary, and the duplicate check the schema cannot make.
 *
 * Seeding and reconciliation happen in the database callback (see `builtInSymptomSeedCallback`), so by
 * the time anything reads through here the built-in list is already present and topped up.
 */
class SymptomRepository(
    private val database: BunnyDatabase,
) {
    private val symptomDao = database.symptomDao()

    /**
     * The picker's list, retired symptoms excluded. **Unordered by design** — a built-in has no stored
     * label, so it can only be sorted after its `strings.xml` label is resolved in the owner's
     * language (ADR-0010, ADR-0013).
     */
    val visibleSymptoms: Flow<List<SymptomEntity>> = symptomDao.visible()

    /** What was ticked on one observation, including symptoms since retired. */
    fun symptomsFor(observationId: String): Flow<List<SymptomEntity>> = symptomDao.symptomsFor(observationId)

    /**
     * Adds an owner's symptom, or returns the existing one it duplicates.
     *
     * Nothing in the schema can catch this, because built-in labels are deliberately not stored: there
     * is no column holding "Head tilt" for an index to collide with. So the check is made **once, here,
     * at add time** (ADR-0010) — trim, then compare case-insensitively against the owner's stored
     * labels *and* the built-in labels **as currently resolved**, which is why [builtInLabels] is a
     * parameter rather than a lookup. Resolving a `strings.xml` value needs a `Context` and the
     * owner's locale, both of which belong to the UI; passing them in keeps this layer free of
     * resources and makes the test's fixture the same shape as the real caller's.
     *
     * A match on a **hidden** symptom unhides it: an owner typing in a symptom they previously retired
     * is asking for it back, and the alternative is a duplicate shadow of a symptom that already
     * exists — with the history attached to the wrong one of the pair.
     *
     * Accepted limitation, stated in the ADR: the comparison sees labels resolved in the *current*
     * locale, so a later language switch can surface a duplicate-looking pair.
     *
     * @param builtInLabels every built-in symptom's key mapped to its label in the owner's language.
     * @return the id of the symptom to tick, whether it was just created or already existed.
     */
    suspend fun add(
        label: String,
        builtInLabels: Map<String, String> = emptyMap(),
    ): String {
        val trimmed = label.trim()
        require(trimmed.isNotEmpty()) { "A symptom needs a label" }

        return database.withTransaction {
            val existing = symptomDao.allNow()

            // A built-in first: its label lives in the map, not in the row.
            val matchingKey = builtInLabels.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }?.key
            val match =
                existing.firstOrNull { it.key != null && it.key == matchingKey }
                    ?: existing.firstOrNull { it.label?.equals(trimmed, ignoreCase = true) == true }

            if (match != null) {
                if (match.hiddenAt != null) symptomDao.setHiddenAt(match.id, null)
                return@withTransaction match.id
            }

            val created = SymptomEntity(label = trimmed)
            symptomDao.insert(created)
            created.id
        }
    }

    /**
     * Retires a symptom from the picker. **Not a delete**: historical observations go on resolving it,
     * and the join table has no cascade from this side to make that a database guarantee rather than a
     * promise (ADR-0010).
     */
    suspend fun hide(
        id: String,
        at: Instant = Instant.now(),
    ) = symptomDao.setHiddenAt(id, at)

    suspend fun unhide(id: String) = symptomDao.setHiddenAt(id, null)
}
