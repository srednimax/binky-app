package app.binky.tracker.ui.care

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.bunny.joinNames
import app.binky.tracker.ui.weight.timeLabel
import java.time.LocalDate
import java.time.LocalTime

/*
 * How a medication course says what it is.
 *
 * Everything here is copy over facts the app already holds — a schedule, a next dose, an answer
 * somebody gave. **None of it interprets** (ADR-0026): there is no phrase for a dose that was not
 * given, because the app does not have one to say.
 */

/**
 * A course's daily schedule in words — "08:00 and 20:00 every day".
 *
 * The list is joined through [joinNames] rather than with a comma, so the separator is the one the
 * reader's language uses and the pair case ("A and B") does not become "A, B". That helper takes
 * `Resources` rather than being `@Composable`, which is why they are read here — through
 * `LocalResources` rather than `context.resources`, so a locale change recomposes this.
 */
@Composable
fun courseScheduleLabel(times: List<LocalTime>): String {
    if (times.isEmpty()) return stringResource(R.string.med_schedule_none)
    val labels = times.sorted().map { timeLabel(it) }
    if (labels.size == 1) return stringResource(R.string.med_schedule_once, labels.first())
    val resources = LocalResources.current
    return stringResource(R.string.med_schedule_times, joinNames(resources, labels))
}

/**
 * When the next dose falls, or **null when there is nothing to say**.
 *
 * Null is [DoseNext.NoSchedule]: the schedule line above has already said the course has no times,
 * and a second line repeating it in other words is the kind of padding an owner stops reading.
 */
@Composable
fun nextDoseLabel(next: DoseNext): String? =
    when (next) {
        DoseNext.NoSchedule -> null
        DoseNext.Done -> stringResource(R.string.med_next_done)
        is DoseNext.NotStarted -> stringResource(R.string.med_next_not_started, dateLabel(next.startOn))
        is DoseNext.Ended -> stringResource(R.string.med_next_ended, dateLabel(next.endOn))
        is DoseNext.Today -> stringResource(R.string.med_next_today, timeLabel(next.at))
        is DoseNext.Tomorrow -> stringResource(R.string.med_next_tomorrow, timeLabel(next.at))
        is DoseNext.Later -> stringResource(R.string.med_next_later, dateLabel(next.on), timeLabel(next.at))
    }

/**
 * What the owner said about a dose. Two words, and there is no third.
 *
 * **Skipped is a recorded decision, not a failure** (ADR-0026): an owner who was told to stop after
 * four days, or whose rabbit spat it out, has recorded a fact. It reads in the same voice as
 * *Given*, and neither is coloured.
 */
@Composable
fun doseStatusLabel(status: DoseStatus): String =
    stringResource(
        when (status) {
            DoseStatus.GIVEN -> R.string.dose_status_given
            DoseStatus.SKIPPED -> R.string.dose_status_skipped
        },
    )

/** The span a course covers — "From 28 July", "28 July to 4 August". */
@Composable
fun courseRangeLabel(
    startOn: LocalDate,
    endOn: LocalDate?,
): String =
    if (endOn == null) {
        stringResource(R.string.med_range_open, dateLabel(startOn))
    } else {
        stringResource(R.string.med_range_closed, dateLabel(startOn), dateLabel(endOn))
    }
