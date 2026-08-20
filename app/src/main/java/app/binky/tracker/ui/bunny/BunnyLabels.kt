package app.binky.tracker.ui.bunny

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.NeuterStatus
import app.binky.tracker.data.Sex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The age in words, or null when there is no birthdate — the profile then simply omits the line
 * rather than saying "age unknown", which is noise on a screen the owner filled in themselves.
 *
 * An **approximate birthdate renders as an age and never as a date** (ADR-0016), which is why the
 * caller gets a string from here rather than a date to format.
 */
@Composable
fun ageLabel(
    birthDate: LocalDate?,
    approximate: Boolean,
): String? {
    val age = birthDate?.let { ageOn(it, LocalDate.now()) } ?: return null
    val exact =
        when (age) {
            is Age.Years -> pluralStringResource(R.plurals.age_years, age.years, age.years)
            is Age.Months -> pluralStringResource(R.plurals.age_months, age.months, age.months)
            is Age.Weeks -> pluralStringResource(R.plurals.age_weeks, age.weeks, age.weeks)
        }
    return if (approximate) stringResource(R.string.age_approximate, exact) else exact
}

/** A date in the reader's own locale. Only ever shown for an *exact* birthdate (ADR-0016). */
@Composable
fun dateLabel(date: LocalDate): String {
    // LocalConfiguration, not LocalContext.resources.configuration: only the former recomposes when
    // the system locale changes, so the date would otherwise keep its old formatting until the
    // screen was rebuilt for some other reason.
    val locale = LocalConfiguration.current.locales[0]
    // Kotlin note: `remember(keys)` is `useMemo` — the formatter is rebuilt only when the date or
    // the locale actually changes, not on every recomposition.
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    return remember(date, formatter) { date.format(formatter) }
}

/**
 * "Lives with Thumper &amp; Clover", "Lives with Thumper, Clover &amp; 3 others", or null for a solo
 * bunny.
 *
 * The count is the **last item of the list** rather than a suffix glued to it, so it goes through
 * [joinNames] with the names and every locale punctuates it its own way.
 */
@Composable
fun housematesLabel(housemates: List<Housemate>): String? {
    if (housemates.isEmpty()) return null
    val resources = LocalResources.current
    val capped = capHousemates(housemates)
    val names =
        capped.named.map { housemate ->
            if (housemate.archived) {
                stringResource(R.string.bunny_archived_name, housemate.name)
            } else {
                housemate.name
            }
        }
    val parts =
        if (capped.others == 0) {
            names
        } else {
            names + pluralStringResource(R.plurals.bunny_lives_with_others, capped.others, capped.others)
        }
    return stringResource(R.string.bunny_lives_with_value, joinNames(resources, parts))
}

/** How many housemates [housematesLabel] names before the rest become a count. */
private const val NAMED_HOUSEMATES = 2

/** [capHousemates]' answer: who the line names, and how many it folds into "&amp; N others". */
internal data class CappedHousemates(
    val named: List<Housemate>,
    /** Zero when everyone is named, and **never one** — see [capHousemates]. */
    val others: Int,
)

/**
 * Which housemates the line names, and how many it counts instead.
 *
 * **Two named, then "&amp; N others", from four housemates up.** At three, "A, B &amp; C" is the
 * shorter string as well as the better one, so the fold would only trade a name for the word
 * "other" — which is why [CappedHousemates.others] is never 1.
 *
 * **Archived housemates fold first**: they render longer ("Hazel (archived)") and are the least
 * relevant names on a line about who a bunny lives with *now*.
 *
 * Kept out of the composable so it can be a JVM table — a string function is the half a test can
 * hold, and how wide the result draws is the half only the device can (Phase 7.5 §8).
 */
internal fun capHousemates(housemates: List<Housemate>): CappedHousemates {
    if (housemates.size <= NAMED_HOUSEMATES + 1) return CappedHousemates(housemates, others = 0)
    // Kotlin note: `sortedBy` is stable and `false` sorts before `true`, so this is "active ones
    // first, each group in the order it arrived" — not a reordering of the line in general.
    val named = housemates.sortedBy { it.archived }.take(NAMED_HOUSEMATES)
    return CappedHousemates(named, others = housemates.size - NAMED_HOUSEMATES)
}

/**
 * Who the **sheet** lists, against [capHousemates]' who the **line** names: everyone, in the order
 * the profile gave them.
 *
 * It is a function rather than the raw list at the call site so that the difference between the two
 * is a claim a JVM test can hold. The line has a cap and must keep it — it grew the card without
 * bound before it had one — and the sheet exists precisely because that cap leaves names the owner
 * can reach nowhere else, so reusing [capHousemates] here would quietly re-cap the one place that
 * must not be capped (Phase 9f).
 *
 * The archived-first fold is [capHousemates]' alone for the same reason: the sheet has no count to
 * spend the preference on, so it marks archived housemates rather than sinking them.
 */
internal fun housematesInSheet(housemates: List<Housemate>): List<Housemate> = housemates

/**
 * Joins names through string resources, never with a hardcoded `" & "` — other languages punctuate
 * lists differently, and this label appears in the switcher, on the profile and in Phase 2's
 * healthy-day snackbar (ADR-0008, ADR-0013).
 *
 * Builds from the right, so three names come out "Thumper, Clover &amp; Hazel": the last two go
 * through the pair format, and every name before them wraps what has been built so far.
 */
fun joinNames(
    resources: Resources,
    names: List<String>,
): String {
    if (names.size == 1) return names.first()
    var joined = resources.getString(R.string.list_join_pair, names[names.size - 2], names[names.size - 1])
    for (index in names.size - 3 downTo 0) {
        joined = resources.getString(R.string.list_join_more, names[index], joined)
    }
    return joined
}

@Composable
fun sexLabel(sex: Sex): String =
    stringResource(
        when (sex) {
            Sex.MALE -> R.string.sex_male
            Sex.FEMALE -> R.string.sex_female
            Sex.UNKNOWN -> R.string.sex_unknown
        },
    )

@Composable
fun neuterLabel(status: NeuterStatus): String =
    stringResource(
        when (status) {
            NeuterStatus.YES -> R.string.neutered_yes
            NeuterStatus.NO -> R.string.neutered_no
            NeuterStatus.UNKNOWN -> R.string.neutered_unknown
        },
    )
