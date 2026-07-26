package app.bunny.tracker.ui.observations

import app.bunny.tracker.data.BUILT_IN_SYMPTOM_KEYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-in symptom keys and their labels, kept in step.
 *
 * This is the only mechanism that can catch a key shipped without a label. Adding a key seeds a row
 * on the next launch (ADR-0010), and the missing label would render as a blank line in the picker —
 * an invisible failure, on a list whose whole point is that an owner can tick what they saw.
 *
 * A JVM test despite naming `R.string`: generated resource ids are plain `Int` constants, so
 * comparing which keys have one needs no Android at all. Whether the *text* is right is a
 * translation question, and no test can answer it.
 */
class ObservationLabelsTest {
    @Test
    fun everyBuiltInKeyHasALabel() {
        val missing = BUILT_IN_SYMPTOM_KEYS.filterNot { it in BUILT_IN_SYMPTOM_LABELS }

        assertTrue("Built-in symptom keys with no label: $missing", missing.isEmpty())
    }

    @Test
    fun noLabelIsLeftBehindByARemovedKey() {
        // Removing a key deliberately leaves its rows resolving in existing history, but the label
        // map is code — a stale entry here is only a name nothing can ever reach.
        val orphaned = BUILT_IN_SYMPTOM_LABELS.keys.filterNot { it in BUILT_IN_SYMPTOM_KEYS }

        assertTrue("Symptom labels with no key: $orphaned", orphaned.isEmpty())
    }

    @Test
    fun everyKeyResolvesToADistinctResource() {
        // Two keys pointing at one string is the copy-paste slip this map invites, and it would show
        // as two identically-named symptoms in the picker rather than as any kind of error.
        assertEquals(BUILT_IN_SYMPTOM_LABELS.size, BUILT_IN_SYMPTOM_LABELS.values.toSet().size)
    }
}
