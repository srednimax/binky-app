package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * **A two-case table, and its triviality is the point** (ADR-0017).
 *
 * The assertion is not that an `if` works. It is that there is *nothing else to get wrong* — no
 * second column to keep in step, no repository that has to remember to clear a `source` when a visit
 * goes, no backup written by an older build carrying a `source` that disagrees with its `visitId`.
 * One stored fact, one derivation, and a test that can only ever have two rows in it.
 *
 * On the JVM rather than instrumented because nothing here touches SQLite: `SET NULL` is the
 * database's half of the same claim and it is asserted in `VisitDaoTest`.
 */
class WeightSourceTest {
    private val weighing =
        WeightEntity(
            id = "weight-1",
            bunnyId = "bunny-1",
            grams = 2380,
            recordedAt = Instant.parse("2026-05-20T12:00:00Z"),
        )

    @Test
    fun aWeighingWithNoVisitIsManual() {
        assertEquals(WeightSource.MANUAL, weighing.copy(visitId = null).source)
    }

    @Test
    fun aWeighingCarryingAVisitIdCameFromThatVisit() {
        // Kotlin note: `copy()` on a data class is the object-spread idiom — same values, one field
        // replaced, original untouched.
        assertEquals(WeightSource.VISIT, weighing.copy(visitId = "visit-1").source)
    }
}
