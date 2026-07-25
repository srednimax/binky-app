package app.bunny.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** ADR-0004: the two-stage ceremony is calibrated to destroying history, and nothing else. */
class DeleteConfirmationTest {
    @Test
    fun `a bunny with no records is one confirmation`() {
        // The empty duplicate created by mistake — and every Phase 1 bunny, since no record type
        // exists yet. An avatar and the profile fields deliberately do not count.
        assertEquals(
            DeleteConfirmation.SINGLE,
            deleteConfirmationFor(RecordCounts(soleOwnedRecords = 0, sharedRecords = 0)),
        )
    }

    @Test
    fun `records of its own earn the second confirmation`() {
        assertEquals(
            DeleteConfirmation.TWO_STAGE,
            deleteConfirmationFor(RecordCounts(soleOwnedRecords = 12, sharedRecords = 0)),
        )
    }

    @Test
    fun `shared participation alone earns it too`() {
        // Deleting the bunny leaves those observations standing for the others, but it is still a
        // side effect on a different bunny, which is exactly what the second dialog states.
        assertEquals(
            DeleteConfirmation.TWO_STAGE,
            deleteConfirmationFor(RecordCounts(soleOwnedRecords = 0, sharedRecords = 3)),
        )
    }
}
