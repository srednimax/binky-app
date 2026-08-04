package app.binky.tracker.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The grace window, as a case table (ADR-0025, PLAN 5a).
 *
 * This is the one piece of 5a that **no device test can catch when it is wrong**. The debug
 * two-minute action fires promptly on a screen-on phone and so does every emulator; the failure only
 * appears on the default configuration — no `SCHEDULE_EXACT_ALARM`, an alarm delivered a few minutes
 * into its Doze window — and it appears as silence, which is also what a correctly idle app looks
 * like. So the arithmetic is pinned here, where it can be read.
 */
class DoseGraceTest {
    private val slot: Instant = Instant.parse("2026-08-04T08:00:00Z")

    private fun at(minutes: Long): Instant = slot.plus(Duration.ofMinutes(minutes))

    @Test
    fun `the constant is thirty minutes`() {
        // Not a style assertion. Below the Doze delivery window the honest-degradation path delivers
        // nothing at all; well above it the app starts answering for slots the owner has long since
        // stopped thinking about. The number is the decision, so it is written down twice.
        assertEquals(Duration.ofMinutes(30), DOSE_GRACE)
    }

    @Test
    fun `a slot delivered four minutes late is still answerable and still fires`() {
        // The exact case ADR-0025 was written for: an alarm placed for 08:00, delivered at 08:04
        // because `setAndAllowWhileIdle` chose the moment. Under the naive rule this posts nothing
        // and re-arms for the next slot — which, repeated every slot, is a course of medication that
        // reminds the owner **never**, on the path built to degrade honestly.
        assertTrue(doseSlotAnswerable(slot, at(4)))
        assertTrue(doseSlotDueNow(slot, at(4)))
    }

    @Test
    fun `the boundary is inclusive at exactly thirty minutes and closed after it`() {
        assertTrue(doseSlotAnswerable(slot, at(30)))
        assertFalse(doseSlotAnswerable(slot, at(31)))
        assertTrue(doseSlotDueNow(slot, at(30)))
        assertFalse(doseSlotDueNow(slot, at(31)))
    }

    @Test
    fun `a slot the phone slept through is not fired retroactively`() {
        // Eleven hours late is the other end of the same rule rather than a second one: a stack of
        // 3 a.m. notifications at breakfast is a lie about when the app knew.
        assertFalse(doseSlotAnswerable(slot, at(11 * 60)))
        assertFalse(doseSlotDueNow(slot, at(11 * 60)))
    }

    @Test
    fun `a future slot is answerable but not yet due`() {
        // The two halves the two callers need. Rescheduling arms the earliest *answerable* slot, so
        // a slot an hour out has to pass the first predicate; firing must not announce it early,
        // which the OS is permitted to do in both directions.
        assertTrue(doseSlotAnswerable(slot, at(-60)))
        assertFalse(doseSlotDueNow(slot, at(-60)))
    }

    @Test
    fun `a slot exactly now fires`() {
        // Strictly-after would drop the well-behaved case: an exact alarm delivered on the second is
        // the whole point of asking for the permission.
        assertTrue(doseSlotDueNow(slot, slot))
    }

    @Test
    fun `a rebuild after a long sleep finds nothing answerable, which is how the alarm gets cancelled`() {
        // `nextAnswerableDoseSlot` filters on this predicate, so a stale slot answers null and
        // `rescheduleDoseAlarm` cancels rather than arming an alarm that would fire and post
        // nothing. That is what keeps "at most one pending dose alarm, and none when nothing is
        // armed" true after the phone has been off overnight.
        assertFalse(doseSlotAnswerable(slot, at(12 * 60)))
    }
}
