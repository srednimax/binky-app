package app.binky.tracker.ui.weight

import app.binky.tracker.data.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The entry field's state machine, shared by *Record a weighing* and the visit editor.
 *
 * It is tested here rather than through either screen because it is the part they have in common:
 * if the two ever disagree about what switching the unit does to a half-typed number, this is where
 * that disagreement would show up first.
 */
class WeightAmountTest {
    @Test
    fun `the field starts empty and in grams`() {
        // Grams, not the display default of kilograms. An owner who never opens the toggle keeps the
        // behaviour the app had before it existed.
        val amount = WeightAmount()

        assertEquals(WeightUnit.GRAMS, amount.unit)
        assertTrue(amount.isBlank)
        assertEquals(null, amount.grams)
        assertFalse(amount.unparseable)
    }

    @Test
    fun `blank is not unparseable`() {
        // The distinction the visit editor rests on: its weight is optional, so "nothing typed" has
        // to be a valid state while "typed nonsense" is not.
        val blank = WeightAmount().typed("")
        val nonsense = WeightAmount().typed("abc")

        assertTrue(blank.isBlank)
        assertFalse(blank.unparseable)
        assertTrue(nonsense.isBlank) // the filter dropped every character
        assertFalse(nonsense.unparseable)

        // What is actually reachable by typing, since the filter keeps outright junk out of the box:
        // a zero, and a separator with no number around it. Both are "not a weight" rather than
        // "nothing yet", which is the distinction a save has to act on.
        val zero = WeightAmount().typed("0")
        assertFalse(zero.isBlank)
        assertTrue(zero.unparseable)

        val separatorOnly = WeightAmount(unit = WeightUnit.KILOGRAMS).typed(".")
        assertFalse(separatorOnly.isBlank)
        assertTrue(separatorOnly.unparseable)

        // And a second separator is dropped rather than poisoning the number: "1.2.3" is "1.23".
        val twoSeparators = WeightAmount(unit = WeightUnit.KILOGRAMS).typed("1.2.3")
        assertEquals("1.23", twoSeparators.text)
        assertFalse(twoSeparators.unparseable)
    }

    @Test
    fun `switching carries the number across`() {
        val grams = WeightAmount().typed("1200")

        val kilograms = grams.switchedTo(WeightUnit.KILOGRAMS, Locale.UK)
        assertEquals("1.2", kilograms.text)
        assertEquals(1200, kilograms.grams)

        val back = kilograms.switchedTo(WeightUnit.GRAMS, Locale.UK)
        assertEquals("1200", back.text)
        assertEquals(1200, back.grams)
    }

    @Test
    fun `switching to the unit it is already in changes nothing`() {
        val amount = WeightAmount().typed("1200")

        assertSame(amount, amount.switchedTo(WeightUnit.GRAMS, Locale.UK))
    }

    @Test
    fun `a half-typed number survives a switch unmangled`() {
        // There is no weight in "." to convert. Leaving it alone is better than inventing one.
        val amount = WeightAmount(unit = WeightUnit.KILOGRAMS).typed(".")

        val switched = amount.switchedTo(WeightUnit.GRAMS, Locale.UK)

        assertEquals("", switched.text) // the grams filter drops the separator
        assertEquals(WeightUnit.GRAMS, switched.unit)
    }

    @Test
    fun `switching clears a rejection`() {
        // The owner has just done something about it; shouting again would be wrong.
        val rejected = WeightAmount().typed("").invalidated()

        assertTrue(rejected.invalid)
        assertFalse(rejected.switchedTo(WeightUnit.KILOGRAMS, Locale.UK).invalid)
        assertFalse(rejected.typed("12").invalid)
    }

    @Test
    fun `a stored weight opens in the owner's entry unit`() {
        assertEquals("2.495", WeightAmount.of(2495, WeightUnit.KILOGRAMS, Locale.UK).text)
        assertEquals("2495", WeightAmount.of(2495, WeightUnit.GRAMS, Locale.UK).text)
        // No weighing recorded at this visit — an empty box, not "0".
        assertTrue(WeightAmount.of(null, WeightUnit.GRAMS, Locale.UK).isBlank)
    }

    @Test
    fun `the echo always names the other entry unit`() {
        // Deliberately not the display preference: the question it answers is "did you mean this in
        // the other unit", which has nothing to do with how history is drawn.
        assertEquals(WeightUnit.KILOGRAMS, WeightAmount(unit = WeightUnit.GRAMS).echoUnit)
        assertEquals(WeightUnit.GRAMS, WeightAmount(unit = WeightUnit.KILOGRAMS).echoUnit)
    }

    @Test
    fun `typing is filtered by the unit it is typed in`() {
        assertEquals("12", WeightAmount(unit = WeightUnit.GRAMS).typed("1.2").text)
        assertEquals("1.2", WeightAmount(unit = WeightUnit.KILOGRAMS).typed("1.2").text)
    }
}
