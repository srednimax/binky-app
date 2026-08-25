package app.binky.tracker.ui.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * **One weight formatter, in one place.**
 *
 * kg-vs-grams and "changes are always shown in grams" are expressed here and nowhere else, so the
 * axis, the history row, the flag and the vitals card cannot each re-derive them slightly
 * differently. Phase 2d's chart labels come through here too.
 *
 * The split is deliberate: the **number** is a pure function (JVM-testable, locale in, string out)
 * and the **unit** comes from `strings.xml`, because "kg" is user-facing text like any other
 * (ADR-0013) and a translator may need to move or space it.
 */
private const val GRAMS_PER_KILOGRAM = 1000.0

/**
 * **Three decimal places, not two.** Storage is whole grams (house rule), and 3 dp is the shortest
 * kilogram rendering that loses none of them — at 2 dp a 2 495 g reading displays as "2.50 kg" and
 * the owner cannot read back the number they typed. Fixed width also makes a history list scan
 * cleanly down the column.
 */
private const val KILOGRAM_DECIMALS = 3

/** The number alone, in the display unit — no unit suffix, which is a string resource. */
fun weightNumber(
    grams: Int,
    unit: WeightUnit,
    locale: Locale,
): String =
    when (unit) {
        WeightUnit.GRAMS -> gramsNumber(grams, locale)
        WeightUnit.KILOGRAMS ->
            NumberFormat
                .getNumberInstance(locale)
                .apply {
                    minimumFractionDigits = KILOGRAM_DECIMALS
                    maximumFractionDigits = KILOGRAM_DECIMALS
                }.format(grams / GRAMS_PER_KILOGRAM)
    }

/** Always a whole number of grams, whatever the display preference says. */
fun gramsNumber(
    grams: Int,
    locale: Locale,
): String = NumberFormat.getIntegerInstance(locale).format(grams)

// ---------------------------------------------------------------------------
// Entry. Everything below reads or writes the *text in the field*, which is a different job from
// rendering a stored weight: the output goes back into an editable box, so it carries no unit
// suffix and — the part that is easy to get wrong — **no grouping separators**. A French or Polish
// rendering of 2495 g is "2 495", and putting that back in the field makes the next parse read a
// different number, or none.

/**
 * Read the typed text as whole grams, or `null` if it is not a weight yet.
 *
 * `null` covers "" and "1." on the way to "1.2" as much as it covers nonsense — a form that could
 * not hold an incomplete number would fight the keyboard.
 *
 * **Both `.` and `,` are accepted as the decimal separator, always, in either direction.** Which one
 * an owner types is decided by their keyboard, not by the app's locale: a Polish phone offers a
 * comma, and a value converted *into* the field is rendered with the locale's separator, so the two
 * have to agree without anyone choosing. Rejecting the "wrong" one would fail the ordinary case.
 *
 * Kilograms are rounded to the nearest whole gram, half away from zero. Storage is `Int` grams
 * (house rule), so a third decimal is the finest reading that survives — `1.2345` kg is not a whole
 * number of grams and something has to give.
 */
fun parseWeightGrams(
    text: String,
    unit: WeightUnit,
): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return when (unit) {
        WeightUnit.GRAMS -> trimmed.toIntOrNull()?.takeIf { it > 0 }
        WeightUnit.KILOGRAMS -> {
            val normalised = trimmed.replace(',', '.')
            if (normalised.count { it == '.' } > 1) return null
            val kilograms = normalised.toDoubleOrNull() ?: return null
            if (!kilograms.isFinite() || kilograms <= 0) return null
            Math.round(kilograms * GRAMS_PER_KILOGRAM).toInt().takeIf { it > 0 }
        }
    }
}

/**
 * A stored weight as the text the field should hold in [unit] — what the toggle converts *to*.
 *
 * Trailing zeros are trimmed, because this is a box someone is about to type in: 1200 g reads back
 * as "1.2", not "1.200". That is the opposite of [weightNumber]'s fixed three decimals, which exist
 * so a *history column* scans straight down, and the two must not be confused for each other.
 */
fun weightEntryText(
    grams: Int,
    unit: WeightUnit,
    locale: Locale,
): String =
    when (unit) {
        // Deliberately not `gramsNumber`: that one groups, and this string goes back into the field.
        WeightUnit.GRAMS -> grams.toString()
        WeightUnit.KILOGRAMS ->
            NumberFormat
                .getNumberInstance(locale)
                .apply {
                    minimumFractionDigits = 0
                    maximumFractionDigits = KILOGRAM_DECIMALS
                    isGroupingUsed = false
                }.format(grams / GRAMS_PER_KILOGRAM)
    }

/**
 * Keep only what can still become a weight in [unit], applied on every keystroke.
 *
 * Grams stay digits-only, exactly as before. Kilograms additionally allow **one** separator and at
 * most [KILOGRAM_DECIMALS] digits after it — the cap is what stops a fourth decimal being typed and
 * then silently rounded away at save time, which would show the owner a number the app did not keep.
 *
 * A leading separator is allowed through: ".5" is a real way to type half a kilogram, and it parses.
 */
fun filterWeightInput(
    text: String,
    unit: WeightUnit,
): String {
    if (unit == WeightUnit.GRAMS) return text.filter(Char::isDigit)
    val kept = StringBuilder()
    var separatorSeen = false
    var decimals = 0
    for (character in text) {
        when {
            character.isDigit() && separatorSeen && decimals < KILOGRAM_DECIMALS -> {
                kept.append(character)
                decimals++
            }
            character.isDigit() && !separatorSeen -> kept.append(character)
            (character == '.' || character == ',') && !separatorSeen -> {
                kept.append(character)
                separatorSeen = true
            }
        }
    }
    return kept.toString()
}

/**
 * Kotlin note: `LocalConfiguration`, not `LocalContext.resources.configuration` — only the former
 * recomposes when the system locale changes, so a formatter built from the latter would keep its old
 * grouping and decimal separator until the screen was rebuilt for some other reason.
 */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** A stored weight in the owner's chosen display unit — "2.495 kg" or "2,495 g". */
@Composable
fun weightLabel(
    grams: Int,
    unit: WeightUnit,
): String {
    val locale = currentLocale()
    val number = remember(grams, unit, locale) { weightNumber(grams, unit, locale) }
    return stringResource(
        when (unit) {
            WeightUnit.KILOGRAMS -> R.string.weight_in_kilograms
            WeightUnit.GRAMS -> R.string.weight_in_grams
        },
        number,
    )
}

/**
 * A magnitude that is **always grams**, whatever the display unit — the house rule's "changes are
 * always shown in grams", because `−0.04 kg` hides the signal that `−40 g` makes obvious. Used for
 * the trend flag's drop, which is a change even though it carries no sign of its own.
 */
@Composable
fun gramsLabel(grams: Int): String {
    val locale = currentLocale()
    return stringResource(R.string.weight_in_grams, remember(grams, locale) { gramsNumber(grams, locale) })
}

/**
 * A signed change between two weighings, always in grams. The sign lives in `strings.xml` with the
 * rest of the copy — it is a typographic minus, not a hyphen, and that is a translator's call.
 */
@Composable
fun weightChangeLabel(deltaGrams: Int): String {
    val locale = currentLocale()
    if (deltaGrams == 0) return stringResource(R.string.weight_change_none)
    val magnitude = remember(deltaGrams, locale) { gramsNumber(kotlin.math.abs(deltaGrams), locale) }
    return stringResource(
        if (deltaGrams < 0) R.string.weight_change_down else R.string.weight_change_up,
        magnitude,
    )
}

/**
 * **Two** decimal places on a kilogram axis tick, not [KILOGRAM_DECIMALS]' three.
 *
 * A tick is a scale marker, never a number the owner reads back and retypes, so the third decimal
 * is width spent on nothing. Two still resolves 10 g — comfortably finer than the ~125 g drop that
 * turns the trend flag on for a 2.5 kg rabbit — so no gridline can round the signal away.
 */
private const val AXIS_KILOGRAM_DECIMALS = 2

/**
 * Takes a [Double] rather than [Int] grams because an axis tick is a position on a scale, not a
 * stored weighing: the chart picks round values, and they need not land on a whole gram.
 */
private fun axisWeightNumber(
    grams: Double,
    unit: WeightUnit,
    locale: Locale,
): String =
    when (unit) {
        WeightUnit.GRAMS -> NumberFormat.getIntegerInstance(locale).format(grams)
        WeightUnit.KILOGRAMS ->
            NumberFormat
                .getNumberInstance(locale)
                .apply {
                    minimumFractionDigits = AXIS_KILOGRAM_DECIMALS
                    maximumFractionDigits = AXIS_KILOGRAM_DECIMALS
                }.format(grams / GRAMS_PER_KILOGRAM)
    }

/**
 * A y-axis tick in the display unit, as a plain function the chart can call while drawing.
 *
 * It is built here rather than in the chart so kg-vs-grams stays decided in this one file. The
 * shape is a composable that *returns* a non-composable lambda, because the charting library
 * formats ticks from its own draw pass, where `stringResource` cannot be called — so the locale and
 * the unit template are read during composition and captured.
 */
@Composable
fun rememberAxisWeightFormatter(unit: WeightUnit): (Double) -> String {
    val locale = currentLocale()
    // The bare template ("%1$s kg"), resolved with the value once the axis supplies one.
    val template =
        stringResource(
            when (unit) {
                WeightUnit.KILOGRAMS -> R.string.weight_in_kilograms
                WeightUnit.GRAMS -> R.string.weight_in_grams
            },
        )
    return remember(unit, locale, template) {
        { grams -> template.format(locale, axisWeightNumber(grams, unit, locale)) }
    }
}

/**
 * An x-axis tick: the date a plotted weighing falls on, captured for the same draw-pass reason as
 * [rememberAxisWeightFormatter]. The short localized form — the axis carries a handful of labels
 * and the owner is reading the shape, not the minute.
 */
@Composable
fun rememberAxisDateFormatter(): (Instant) -> String {
    val locale = currentLocale()
    return remember(locale) {
        val formatter =
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
        ({ instant -> formatter.format(instant) })
    }
}

/** When a weighing was taken, in the reader's own locale. Date and time — back-dating is normal. */
@Composable
fun dateTimeLabel(instant: Instant): String {
    val locale = currentLocale()
    val formatter =
        remember(locale) {
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
        }
    return remember(instant, formatter) { formatter.format(instant) }
}

/** The date alone, for copy that dates a reading inside a sentence — "down 240 g since 3 June". */
@Composable
fun instantDateLabel(instant: Instant): String {
    val locale = currentLocale()
    val formatter =
        remember(locale) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
        }
    return remember(instant, formatter) { formatter.format(instant) }
}

/** A clock time in the reader's own locale — 12- or 24-hour as their system is set. */
@Composable
fun timeLabel(time: LocalTime): String {
    val locale = currentLocale()
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    return remember(time, formatter) { formatter.format(time) }
}

/** The calendar day a weighing falls on, in the device's zone — what the entry form's picker edits. */
fun Instant.toLocalDateHere(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()
