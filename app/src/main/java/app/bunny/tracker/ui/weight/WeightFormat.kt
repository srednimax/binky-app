package app.bunny.tracker.ui.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.WeightUnit
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

/**
 * Kotlin note: `LocalConfiguration`, not `LocalContext.resources.configuration` — only the former
 * recomposes when the system locale changes, so a formatter built from the latter would keep its old
 * grouping and decimal separator until the screen was rebuilt for some other reason.
 */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

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
