package app.binky.tracker.ui.care

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.CareInterval
import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareReminderEntity
import app.binky.tracker.data.CareType

/*
 * How a care reminder says what it is.
 *
 * **The preset/custom split is the whole of this file** (ADR-0018). A preset carries a [CareType] and
 * resolves its name through `strings.xml`, so it translates; a custom reminder carries literal text
 * the owner typed, and does not. The one case with both is a preset the owner renamed — *Front
 * claws* on a `NAIL_TRIM` — where the label wins for the name and the type still supplies the icon
 * and the calendar's `RRULE`.
 */

/** The translated name of a known kind. */
@StringRes
fun careTypeLabelRes(type: CareType): Int =
    when (type) {
        CareType.NAIL_TRIM -> R.string.care_type_nail_trim
        CareType.VACCINATION -> R.string.care_type_vaccination
        CareType.WEIGH_IN -> R.string.care_type_weigh_in
    }

/**
 * What a reminder is called, resolved from a [Resources] rather than from composition.
 *
 * The plain-function shape is [app.binky.tracker.ui.bunny.joinNames]' precedent, and it exists for
 * the same reason: the daily sweep has to write this same name into a notification, and a
 * `@Composable` cannot be called from a worker.
 */
fun careReminderLabel(
    resources: Resources,
    reminder: CareReminderEntity,
): String =
    reminder.label
        ?: reminder.type?.let { resources.getString(careTypeLabelRes(it)) }
        // Unreachable: `CareRepository` refuses a reminder with neither. Rendering an empty row
        // beats crashing on a database somebody hand-edited.
        ?: ""

@Composable
fun careReminderLabel(reminder: CareReminderEntity): String =
    reminder.label ?: reminder.type?.let { stringResource(careTypeLabelRes(it)) } ?: ""

/**
 * A placeholder icon per kind, and a bell for a reminder with no kind at all.
 *
 * ADR-0012 defers iconography to the visual pass at the end, so these only have to be
 * distinguishable from one another — and they come from the small core icon set, which is the whole
 * reason `material-icons-extended` is not a dependency. The weigh-in deliberately borrows the Weight
 * tab's star, because tapping *Done* on one is where it sends you.
 */
fun careTypeIcon(type: CareType?): ImageVector =
    when (type) {
        CareType.NAIL_TRIM -> Icons.Filled.Build
        CareType.VACCINATION -> Icons.Filled.AddCircle
        CareType.WEIGH_IN -> Icons.Filled.Star
        null -> Icons.Filled.Notifications
    }

/** "Due in 3 days", "Due today", "2 weeks overdue" — the row's date, in words (see [CareDue]). */
@Composable
fun careDueLabel(due: CareDue): String =
    when (due) {
        CareDue.Today -> stringResource(R.string.care_due_today)
        CareDue.Tomorrow -> stringResource(R.string.care_due_tomorrow)
        CareDue.Yesterday -> stringResource(R.string.care_due_yesterday)
        is CareDue.In -> stringResource(R.string.care_due_in, careGapLabel(due.gap))
        is CareDue.Overdue -> stringResource(R.string.care_due_overdue, careGapLabel(due.gap))
    }

/**
 * A gap in the one unit worth saying.
 *
 * Reuses `gap_weeks` / `gap_months` / `gap_years` from the trend flag rather than minting a parallel
 * set: "3 weeks" is the same phrase whether it separates two weighings or stands between today and a
 * nail trim, and one copy is one thing to translate.
 */
@Composable
private fun careGapLabel(gap: CareGap): String =
    when (gap) {
        is CareGap.Days -> pluralStringResource(R.plurals.gap_days, gap.days, gap.days)
        is CareGap.Weeks -> pluralStringResource(R.plurals.gap_weeks, gap.weeks, gap.weeks)
        is CareGap.Months -> pluralStringResource(R.plurals.gap_months, gap.months, gap.months)
        is CareGap.Years -> pluralStringResource(R.plurals.gap_years, gap.years, gap.years)
    }

/**
 * "Every week", "Every 6 weeks".
 *
 * **A count of one drops the number**, which is why the amount is substituted into one sentence
 * rather than spelled out in four more plurals: two of the three presets repeat once a unit, and
 * *Every 1 year* on a vaccination row would be the first thing anyone noticed about this screen.
 */
@Composable
fun careIntervalLabel(interval: CareInterval): String =
    stringResource(
        R.string.care_every,
        if (interval.count == 1) {
            careIntervalUnitLabel(interval.unit, 1)
        } else {
            careGapLabel(interval.asGap())
        },
    )

/** The interval as a gap, so "6 weeks" comes from the same plurals a due date's "6 weeks" does. */
private fun CareInterval.asGap(): CareGap =
    when (unit) {
        CareIntervalUnit.DAY -> CareGap.Days(count)
        CareIntervalUnit.WEEK -> CareGap.Weeks(count)
        CareIntervalUnit.MONTH -> CareGap.Months(count)
        CareIntervalUnit.YEAR -> CareGap.Years(count)
    }

/** The unit's own name, for the interval picker where the count is a separate field. */
@Composable
fun careIntervalUnitLabel(
    unit: CareIntervalUnit,
    count: Int,
): String =
    when (unit) {
        CareIntervalUnit.DAY -> pluralStringResource(R.plurals.care_unit_days, count)
        CareIntervalUnit.WEEK -> pluralStringResource(R.plurals.care_unit_weeks, count)
        CareIntervalUnit.MONTH -> pluralStringResource(R.plurals.care_unit_months, count)
        CareIntervalUnit.YEAR -> pluralStringResource(R.plurals.care_unit_years, count)
    }
