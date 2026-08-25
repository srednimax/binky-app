package app.binky.tracker.ui.weight

import app.binky.tracker.data.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

/**
 * The number half of the formatter — pure, so the rules that matter can be pinned here rather than
 * squinted at on a phone. The unit suffix is a string resource and belongs to the Compose layer.
 */
class WeightFormatTest {
    @Test
    fun `kilograms keep every stored gram`() {
        // Storage is whole grams; at 2 dp this would read "2.50 kg" and the owner could not read
        // back the number they typed into the scale field.
        assertEquals("2.495", weightNumber(2495, WeightUnit.KILOGRAMS, Locale.UK))
    }

    @Test
    fun `a forty gram difference stays visible in kilograms`() {
        val before = weightNumber(2500, WeightUnit.KILOGRAMS, Locale.UK)
        val after = weightNumber(2460, WeightUnit.KILOGRAMS, Locale.UK)

        assertEquals("2.500", before)
        assertEquals("2.460", after)
        assertNotEquals(before, after)
    }

    @Test
    fun `kilograms are padded to a fixed width so a column scans`() {
        assertEquals("2.000", weightNumber(2000, WeightUnit.KILOGRAMS, Locale.UK))
        assertEquals("0.900", weightNumber(900, WeightUnit.KILOGRAMS, Locale.UK))
    }

    @Test
    fun `grams are whole numbers under either display preference`() {
        assertEquals("2,495", weightNumber(2495, WeightUnit.GRAMS, Locale.UK))
        assertEquals("2,495", gramsNumber(2495, Locale.UK))
        // gramsNumber ignores the preference entirely — it is what the change copy uses, and
        // "changes are always shown in grams".
        assertEquals("40", gramsNumber(40, Locale.UK))
    }

    @Test
    fun `separators follow the reader's locale`() {
        // German swaps the two: "." groups thousands and "," is the decimal point. The same 2 495 g
        // therefore renders as "2,495" in kilograms and "2.495" in grams — the exact inverse of UK,
        // which is why neither string may be hard-coded anywhere.
        assertEquals("2,495", weightNumber(2495, WeightUnit.KILOGRAMS, Locale.GERMANY))
        assertEquals("2.495", gramsNumber(2495, Locale.GERMANY))
    }

    // ---------------------------------------------------------------------
    // Entry: the kilogram toggle. Storage is still `Int` grams, so every one of these is really a
    // question about what the app does with text before it ever reaches the database.

    @Test
    fun `grams entry is unchanged by the toggle existing`() {
        assertEquals(2495, parseWeightGrams("2495", WeightUnit.GRAMS))
    }

    @Test
    fun `kilograms parse to whole grams`() {
        assertEquals(1200, parseWeightGrams("1.2", WeightUnit.KILOGRAMS))
        assertEquals(1234, parseWeightGrams("1.234", WeightUnit.KILOGRAMS))
        assertEquals(2000, parseWeightGrams("2", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `a comma is a decimal separator too`() {
        // Which separator arrives is decided by the owner's keyboard, not by the app's locale — a
        // Polish phone offers a comma. Rejecting it would fail the ordinary case.
        assertEquals(1200, parseWeightGrams("1,2", WeightUnit.KILOGRAMS))
        assertEquals(parseWeightGrams("1.234", WeightUnit.KILOGRAMS), parseWeightGrams("1,234", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `a half-typed number is not a weight yet`() {
        // An empty box is not an error to shout about, and neither is nonsense: both simply have no
        // weight in them yet, which is what `null` says.
        assertEquals(null, parseWeightGrams("", WeightUnit.KILOGRAMS))
        assertEquals(null, parseWeightGrams("1.2.3", WeightUnit.KILOGRAMS))
        assertEquals(null, parseWeightGrams("kg", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `a trailing separator reads as the whole number`() {
        // "1." is Java's `parseDouble` answering 1.0, and it is kept deliberately rather than
        // special-cased to null. "1." is what "1.2" looks like one keystroke earlier, so the echo
        // under the field holds steady at "1000 g" instead of blinking out and back while the owner
        // is still typing.
        assertEquals(1000, parseWeightGrams("1.", WeightUnit.KILOGRAMS))
        assertEquals(1000, parseWeightGrams("1", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `zero and below are not weights`() {
        assertEquals(null, parseWeightGrams("0", WeightUnit.KILOGRAMS))
        assertEquals(null, parseWeightGrams("-1.2", WeightUnit.KILOGRAMS))
        assertEquals(null, parseWeightGrams("0", WeightUnit.GRAMS))
    }

    @Test
    fun `a fourth decimal rounds to the nearest gram`() {
        // The field caps typing at three, but the parser is total and has to answer anyway.
        assertEquals(1235, parseWeightGrams("1.2345", WeightUnit.KILOGRAMS))
        assertEquals(1234, parseWeightGrams("1.2344", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `the toggle round-trips a weight`() {
        // The behaviour the owner actually sees: 1200 g becomes "1.2", and back again unchanged.
        val asKilograms = weightEntryText(1200, WeightUnit.KILOGRAMS, Locale.UK)
        assertEquals("1.2", asKilograms)
        assertEquals(1200, parseWeightGrams(asKilograms, WeightUnit.KILOGRAMS))
        assertEquals("1200", weightEntryText(1200, WeightUnit.GRAMS, Locale.UK))
    }

    @Test
    fun `entry text never carries a grouping separator`() {
        // It goes back into the field: "2 495" re-parsed is a different number, or none. This is
        // the one place `weightEntryText` must differ from `gramsNumber`, which does group.
        assertEquals("2495", weightEntryText(2495, WeightUnit.GRAMS, Locale.GERMANY))
        assertEquals("2,495", weightEntryText(2495, WeightUnit.KILOGRAMS, Locale.GERMANY))
        assertEquals(
            2495,
            parseWeightGrams(weightEntryText(2495, WeightUnit.KILOGRAMS, Locale.GERMANY), WeightUnit.KILOGRAMS),
        )
    }

    @Test
    fun `entry text trims trailing zeros`() {
        // Unlike the history column, which is fixed at three so it scans straight down.
        assertEquals("2", weightEntryText(2000, WeightUnit.KILOGRAMS, Locale.UK))
        assertEquals("2.495", weightNumber(2495, WeightUnit.KILOGRAMS, Locale.UK))
        assertEquals("2.000", weightNumber(2000, WeightUnit.KILOGRAMS, Locale.UK))
    }

    @Test
    fun `the field refuses what cannot become a weight`() {
        assertEquals("2495", filterWeightInput("2a4b9c5", WeightUnit.GRAMS))
        assertEquals("12", filterWeightInput("1.2", WeightUnit.GRAMS))
        assertEquals("1.2", filterWeightInput("1.2", WeightUnit.KILOGRAMS))
        // One separator only, and the second is dropped rather than replacing the first.
        assertEquals("1.23", filterWeightInput("1.2.3", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `the field caps kilograms at three decimals`() {
        // A fourth digit typed and then silently rounded away would show a number the app did not
        // keep. Refusing the keystroke says so at the moment it happens.
        assertEquals("1.234", filterWeightInput("1.2345", WeightUnit.KILOGRAMS))
    }

    @Test
    fun `half a kilogram can be typed without a leading zero`() {
        assertEquals(".5", filterWeightInput(".5", WeightUnit.KILOGRAMS))
        assertEquals(500, parseWeightGrams(".5", WeightUnit.KILOGRAMS))
    }
}
