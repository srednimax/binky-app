package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the one-tap shortcut commits on the owner's behalf.
 *
 * A JVM test, because [healthyDayFacts] is a pure function over plain data classes — the field set is
 * the claim, and it needs no database to state. The write itself is covered instrumented, in
 * `ObservationRepositoryTest`.
 */
class HealthyDayTest {
    private val facts = healthyDayFacts()

    @Test
    fun recordsAllThreeDroppingsFieldsPlusCecotropes() {
        // All three, because they are read from the same glance at the same tray: an owner who can see
        // the tray is empty can see the pellets are small. Recording only the amount would make the
        // shortcut claim less than the glance it stands for.
        assertEquals(DroppingsAmount.NORMAL, facts.tray.droppingsAmount)
        assertEquals(DroppingsSize.NORMAL, facts.tray.droppingsSize)
        assertEquals(DroppingsForm.ROUND, facts.tray.droppingsForm)
        assertEquals(Cecotropes.EATEN, facts.tray.cecotropes)
    }

    @Test
    fun leavesTheGradedFieldsNotChecked() {
        // One tap cannot honestly claim to have assessed these, and an app-supplied "normal" would be
        // exactly the unverified "fine" ADR-0001 forbids — manufactured by the app, not the owner.
        assertNull(facts.individual.appetite)
        assertNull(facts.individual.mood)
        assertNull(facts.individual.activity)
        assertNull(facts.individual.water)
        assertNull(facts.individual.note)
    }

    @Test
    fun recordsNoSymptomsSeenAsAnAffirmativeFact() {
        // The one state the join table cannot express: *looked, none seen*, distinguishable from never
        // having looked (ADR-0010). No links, and `symptomsChecked` true anyway.
        assertTrue(facts.individual.symptomsChecked)
        assertTrue(facts.individual.symptomIds.isEmpty())
    }

    @Test
    fun anObservationWithNoSymptomsTickedAndNoTickDefaultsToNotChecked() {
        // The other side of the same coin, and why the boolean is not a second spelling of "count of
        // links > 0": the ordinary un-opened picker must read as *never looked*.
        assertEquals(false, IndividualFacts().normalised().symptomsChecked)
    }

    @Test
    fun anySymptomLinkImpliesTheOwnerLooked() {
        // Enforced rather than expected (ADR-0010) — the repository normalises through this on every
        // write, so no caller can store the impossible pair.
        val ticked = IndividualFacts(symptomsChecked = false, symptomIds = setOf("symptom-1"))
        assertTrue(ticked.normalised().symptomsChecked)
    }

    @Test
    fun aBlankNoteIsStoredAsNothing() {
        // So "" and null do not become two spellings of "nothing written".
        assertNull(IndividualFacts(note = "   ").normalised().note)
        assertEquals("hunched all morning", IndividualFacts(note = " hunched all morning ").normalised().note)
    }
}
