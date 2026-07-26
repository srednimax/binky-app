package app.bunny.tracker.ui.bunny

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.bunny.tracker.R
import app.bunny.tracker.data.NeuterStatus
import app.bunny.tracker.data.Sex
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

/** "Lives with Thumper &amp; Clover", or null for a solo bunny. */
@Composable
fun housematesLabel(housemates: List<Housemate>): String? {
    if (housemates.isEmpty()) return null
    val resources = LocalResources.current
    val names =
        housemates.map { housemate ->
            if (housemate.archived) {
                stringResource(R.string.bunny_archived_name, housemate.name)
            } else {
                housemate.name
            }
        }
    return stringResource(R.string.bunny_lives_with_value, joinNames(resources, names))
}

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
