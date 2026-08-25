package app.binky.tracker.ui.weight

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.FormChip
import java.util.Locale

/**
 * One weight **entry field**, as state: the text an owner has typed and the unit it is read in.
 *
 * Every screen that takes a weight uses this, so the awkward parts are settled once — which
 * separators are accepted, what a half-typed number means, and what happens to the number already in
 * the box when the unit is switched under it. There are two such screens (*Record a weighing* and
 * the visit editor) and they look nothing alike, so what is shared is the **state machine and the
 * chips**, not the field: the weighing form's box is `6e`'s oversized hero, the visit editor's is an
 * ordinary optional row, and forcing one composable to be both would be worse than either.
 *
 * ## ⚠️ This is not the display preference, and the two must never be conflated
 *
 * The app has **two** weight-unit preferences and they answer different questions:
 *
 * | | preference | default | what it decides |
 * | --- | --- | --- | --- |
 * | **Entry** | `AppPreferences.weightEntryUnit` | **grams** | how the text *typed into a field* is read |
 * | **Display** | `AppPreferences.weightUnit` | **kilograms** | how a *stored* weight is rendered back |
 *
 * The display preference is **read-only as far as this file is concerned**: it never reaches
 * [WeightAmount], and nothing here writes it. Settings' *Show weights in* changes chart labels,
 * history rows, the vitals card and the delete confirmation — and **not** what any entry field
 * accepts. That is why the two are separate keys rather than one: they default differently on
 * purpose, because reusing the display preference for entry would have moved every existing owner's
 * field to kilograms the day the toggle shipped, and `2495` typed into a kilogram field is exactly
 * the fat-fingered reading the *recent weighings* line exists to catch.
 *
 * The one place they meet is the **echo** under the field, which deliberately shows the *other*
 * entry unit ([echoUnit]) rather than the display preference — the question it answers is "did you
 * mean this number in the other unit", which has nothing to do with how history is drawn.
 *
 * Storage is untouched by all of it: weight is `Int` grams on disk whichever way either preference
 * points (house rule).
 *
 * Kotlin note: a `data class` with `copy()`-returning transitions rather than mutable fields — the
 * JS analogue is returning `{...state, text}` from a reducer instead of assigning to `state.text`.
 * Each transition hands back a new value, so a ViewModel's `update {}` stays a pure expression.
 */
data class WeightAmount(
    /** Exactly what the owner has typed, including "" and "1." on the way to "1.2". */
    val text: String = "",
    /** The unit [text] is read in — an owner preference, not the display one. */
    val unit: WeightUnit = WeightUnit.GRAMS,
    /** Set only after a rejected save, so a form does not shout while it is still being filled in. */
    val invalid: Boolean = false,
) {
    /** The typed text as whole grams, or `null` when it is not a weight yet. Storage's unit. */
    val grams: Int? get() = parseWeightGrams(text, unit)

    /** Nothing typed at all — which is a *valid* state where the weight is optional. */
    val isBlank: Boolean get() = text.isBlank()

    /** Typed something that is not a weight, as opposed to having typed nothing at all. */
    val unparseable: Boolean get() = text.isNotBlank() && grams == null

    /** The unit the echo line names: always the one the field is *not* in. */
    val echoUnit: WeightUnit
        get() = if (unit == WeightUnit.GRAMS) WeightUnit.KILOGRAMS else WeightUnit.GRAMS

    /** A keystroke. Filtering here is what stops a fourth decimal ever reaching [grams]. */
    fun typed(input: String): WeightAmount = copy(text = filterWeightInput(input, unit), invalid = false)

    /**
     * Switch the field's unit, **carrying the number across** rather than clearing it: 1200 becomes
     * 1.2, and back.
     *
     * Converting from the *parsed* value and not from the text is what makes the round trip stable.
     * Half-typed input has no weight in it to convert, so it is re-filtered and otherwise left
     * exactly as it is — mangling it into something the owner did not type would be worse than
     * leaving it alone.
     */
    fun switchedTo(
        newUnit: WeightUnit,
        locale: Locale = Locale.getDefault(),
    ): WeightAmount {
        if (newUnit == unit) return this
        val carried = grams
        return copy(
            unit = newUnit,
            text = carried?.let { weightEntryText(it, newUnit, locale) } ?: filterWeightInput(text, newUnit),
            invalid = false,
        )
    }

    /** A save was refused because of this field. */
    fun invalidated(): WeightAmount = copy(invalid = true)

    companion object {
        /** A stored weight opened for editing, rendered in the owner's entry unit. */
        fun of(
            grams: Int?,
            unit: WeightUnit,
            locale: Locale = Locale.getDefault(),
        ): WeightAmount =
            WeightAmount(
                text = grams?.let { weightEntryText(it, unit, locale) }.orEmpty(),
                unit = unit,
            )
    }
}

/**
 * The grams/kilograms toggle, shared by every screen that takes a weight so the two cannot drift
 * apart in wording or behaviour.
 *
 * It reuses Settings' own option labels rather than declaring its own: "Grams" and "Kilograms" are
 * the same two words in the same app, and two sets of them would be two things for a translator to
 * keep in step for no reason.
 */
@Composable
fun WeightUnitChips(
    selected: WeightUnit,
    onSelected: (WeightUnit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ChipRow(modifier = modifier) {
        FormChip(
            selected = selected == WeightUnit.GRAMS,
            onClick = { onSelected(WeightUnit.GRAMS) },
            label = stringResource(R.string.settings_unit_grams),
            enabled = enabled,
        )
        FormChip(
            selected = selected == WeightUnit.KILOGRAMS,
            onClick = { onSelected(WeightUnit.KILOGRAMS) },
            label = stringResource(R.string.settings_unit_kilograms),
            enabled = enabled,
        )
    }
}
