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
}
