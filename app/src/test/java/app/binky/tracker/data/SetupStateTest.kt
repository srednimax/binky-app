package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole of ADR-0006's "resolved on read, not merely stored" rule, as a truth table.
 *
 * Two of the unrecorded rows are the reason the rule exists at all, and both are about an owner who
 * must never meet the wizard: the debug install that predates it, and the phone that has just
 * restored a backup. The [SetupProgress.Started] rows are the reason the rule is not *only* that —
 * they were added after the wizard ended itself halfway through, on the phone.
 */
class SetupStateTest {
    @Test
    fun `a fresh install owes the owner setup`() {
        assertEquals(SetupState.Required, resolveSetupState(progress = null, hasBunny = false))
    }

    @Test
    fun `an install that already has a bunny is treated as set up`() {
        // The author's debug install, and a phone that restored a backup made before the wizard
        // existed. Neither has a record; both plainly have a history.
        assertEquals(SetupState.Complete, resolveSetupState(progress = null, hasBunny = true))
    }

    @Test
    fun `a wizard in progress survives its own first step`() {
        // The bug hand-verification found: step one adds a bunny, and without a recorded start the
        // row above fires mid-wizard and drops the owner into the app — taking the backup step with
        // it, which is the one thing ADR-0006 puts in setup rather than in settings.
        assertEquals(SetupState.Required, resolveSetupState(progress = SetupProgress.Started, hasBunny = true))
    }

    @Test
    fun `a wizard in progress survives a process death`() {
        // Same record, read on a cold start rather than a recomposition. It is written to a
        // preference and not held in composition precisely so these two rows are the same row.
        assertEquals(SetupState.Required, resolveSetupState(progress = SetupProgress.Started, hasBunny = false))
    }

    @Test
    fun `finishing the wizard ends it whether or not a bunny was added`() {
        // Skipping the bunny step is allowed, so "set up" and "has a bunny" are independent.
        assertEquals(SetupState.Complete, resolveSetupState(progress = SetupProgress.Complete, hasBunny = false))
        assertEquals(SetupState.Complete, resolveSetupState(progress = SetupProgress.Complete, hasBunny = true))
    }
}
