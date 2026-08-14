package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one house rule the observation vocabularies cannot afford to lose: **stored by name, never
 * ordinal**.
 *
 * A JVM test, because [Converters] has no Android in it. It is written now because ADR-0029 inserted
 * five values into the middle of one enum and one into another, which is precisely the change an
 * ordinal column would have used to rewrite five years of history — every `SOFT` silently becoming
 * something else. Nothing in the app would have reported that; the rows would simply have started
 * meaning different things.
 */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun droppingsAppearanceIsStoredAsItsName() {
        // Not an index. `BLOOD` is the tenth member and stores ten characters, not "9".
        assertEquals("BLOOD", converters.droppingsAppearanceToName(DroppingsAppearance.BLOOD))
        assertEquals("ROUND", converters.droppingsAppearanceToName(DroppingsAppearance.ROUND))
    }

    @Test
    fun everyDroppingsAppearanceRoundTrips() {
        for (value in DroppingsAppearance.entries) {
            assertEquals(value, converters.droppingsAppearanceFromName(converters.droppingsAppearanceToName(value)))
        }
        for (value in DroppingsSize.entries) {
            assertEquals(value, converters.droppingsSizeFromName(converters.droppingsSizeToName(value)))
        }
    }

    @Test
    fun theFiveValuesThatShippedBeforeSchemaSevenStillStoreTheSameNames() {
        // These are the names sitting in real databases written by 1.0 through 1.4, under the old
        // `DroppingsForm` type and the old `droppingsForm` column. `MIGRATION_6_7` copies them into
        // the join table **verbatim**, with no translation table, and this is the assertion that
        // makes that safe: the rename was free precisely because only value names are ever stored.
        val shipped = listOf("ROUND", "MISSHAPEN", "STRUNG_TOGETHER", "SOFT", "DIARRHOEA")
        for (name in shipped) {
            val value = converters.droppingsAppearanceFromName(name)
            assertEquals(name, converters.droppingsAppearanceToName(value))
        }
    }

    @Test
    fun addingCecotropesExcessDidNotMoveTheTwoValuesBeforeIt() {
        assertEquals(Cecotropes.EATEN, converters.cecotropesFromName("EATEN"))
        assertEquals(Cecotropes.LEFT_UNEATEN, converters.cecotropesFromName("LEFT_UNEATEN"))
        assertEquals("EXCESS", converters.cecotropesToName(Cecotropes.EXCESS))
    }

    @Test
    fun anUnknownNameOnANullableVocabularyReadsAsNotChecked() {
        // A row written by a later build reads back as *nobody looked* rather than as a substitute
        // value the owner never chose (ADR-0001). Absence is representable here, so it is the honest
        // answer — and unlike a crash, it is one the owner can act on.
        assertNull(converters.cecotropesFromName("SOMETHING_LATER"))
        assertNull(converters.droppingsAmountFromName("SOMETHING_LATER"))
        assertNull(converters.droppingsAmountFromName(null))
    }
}
