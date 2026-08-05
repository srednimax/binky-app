package app.binky.tracker.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * The debug-only sample-data action, behind `BuildConfig.DEBUG` at its call site.
 *
 * It writes **through the repositories**, never through the DAOs, so it cannot seed rows the app
 * itself could not produce — and so a year of weighings exercises 2a's insert-time acknowledgment
 * discard a few hundred times on a real device rather than in a test.
 *
 * It exists because 2d's chart review needs a year of uneven, back-dated history and hand-typing
 * that through a date picker is the toil that gets skimmed. The fixture is **deterministic** — a
 * fixed seed and fixed offsets — because 2d and 2f are meant to be reviewing the same chart.
 */
private const val SAMPLE_SEED = 20260726L

/** Debug fixture names, deliberately not in `strings.xml`: no owner ever sees this action. */
private const val SAMPLE_BUNNY = "Bijou"
private const val SAMPLE_HOUSEMATE = "Nugget"

/** Every weighing is stamped at the same clock time, so a tie has to be made deliberately. */
private val WEIGHING_TIME: LocalTime = LocalTime.of(8, 30)

/**
 * Observations land in the evening, well clear of [WEIGHING_TIME].
 *
 * Not cosmetic: it keeps a seeded observation and a seeded weighing on the same day from sharing a
 * minute, so anything that reads "the latest record on this day" cannot pass by coincidence.
 */
private val OBSERVATION_TIME: LocalTime = LocalTime.of(18, 0)

/**
 * Seeds the fixture, or returns false if it is already there.
 *
 * Not idempotent by merging — it simply declines to run twice, because "duplicates allowed" is a
 * real rule for real bunnies (ADR-0016) and the seeder must not be the thing that quietly weakens it.
 */
suspend fun seedSampleData(
    bunnies: BunnyRepository,
    fluffles: FluffleRepository,
    weights: WeightRepository,
    observations: ObservationRepository,
    symptoms: SymptomRepository,
    photos: PhotoRepository,
    care: CareRepository,
    watches: WatchRepository,
    vets: VetRepository,
    visits: VisitRepository,
    medications: MedicationRepository,
    cacheDir: File,
    now: Instant = Instant.now(),
): Boolean {
    val existing = bunnies.activeBunnies.first()
    if (existing.any { it.name == SAMPLE_BUNNY || it.name == SAMPLE_HOUSEMATE }) return false

    val bijou = bunnies.add(BunnyEntity(name = SAMPLE_BUNNY, sex = Sex.FEMALE, neutered = NeuterStatus.YES))
    val nugget = bunnies.add(BunnyEntity(name = SAMPLE_HOUSEMATE, sex = Sex.MALE, neutered = NeuterStatus.YES))
    // A bonded pair, so the shared observation below has somewhere to land (ADR-0008).
    fluffles.livesWith(bijou, nugget)

    for ((daysAgo, grams) in bijouSeries()) {
        weights.add(WeightEntity(bunnyId = bijou, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }
    // Nugget stays steady, so the two cards on Home show a flagged bunny beside an unflagged one.
    for ((daysAgo, grams) in listOf(28L to 1780, 21L to 1795, 14L to 1785, 7L to 1790, 1L to 1788)) {
        weights.add(WeightEntity(bunnyId = nugget, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }

    seedObservations(observations, symptoms, bijou, nugget, now)
    seedPhotos(photos, cacheDir, bijou, nugget, now)
    seedCare(care, bijou, nugget, now)
    seedWatches(watches, bijou, nugget, now)
    seedVisits(vets, visits, bijou, nugget, now)
    seedMedications(medications, bijou, now)
    return true
}

/**
 * The medication half: **three courses, all on Bijou**, chosen so every state 5e draws is on screen
 * without waiting a week for one — and so 5f has something real to arm tonight.
 *
 * - **An open twice-daily course with a partial history**, which is the one that has to be looked at
 *   hardest: it puts unanswered slots in today's list, answered ones behind it, and a deliberate
 *   *skip* among them, so "skipped is a recorded fact, not an absence" is visible rather than
 *   argued from a test. It is also the only course here with reminders on, which makes it the one
 *   the delivery line is gated on and the one 5f's alarm will be rebuilt from.
 * - **A course that ended last week**, so the active-then-ended ordering has something to order and
 *   the *Ended* row can be read beside a running one.
 * - **A course with no schedule at all**, because scheduling is optional (ADR-0002): no times means
 *   no slots ever, no reminder switch in the editor, and an ad-hoc dose as the only way to record
 *   anything — three branches nothing else here exercises.
 *
 * **Nugget deliberately gets none**, which puts the medications empty state on screen in the same
 * fixture. The per-bunny scoping is already proven by the visits above.
 *
 * Written **through the repository**, like everything else here. The past days go in as hand-built
 * [DueDose] keys — a slot is a local day and a local time and nothing else, so this writes exactly
 * what the screen writes when the owner answers on the day — while **today's** slots are read back
 * through [MedicationRepository.scheduleNow] and answered for real, because a fixture whose current
 * day was assembled by hand would prove nothing about the derivation it is meant to demonstrate.
 */
private suspend fun seedMedications(
    medications: MedicationRepository,
    bijou: String,
    now: Instant,
) {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()
    val morning = LocalTime.of(8, 0)
    val evening = LocalTime.of(20, 0)

    val metacam =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bijou,
                name = "Metacam",
                doseAmount = "0.3 ml",
                startOn = today.minusDays(6),
                notes = "After food. Back to the vet if she stops eating.",
            ),
            times = listOf(morning, evening),
        )

    for (daysAgo in 5L downTo 1L) {
        val day = today.minusDays(daysAgo)
        for (time in listOf(morning, evening)) {
            // One deliberate skip in the middle of an otherwise compliant week: a skip is a
            // recorded decision, and the screen has to show it in the same voice as a dose given.
            val skipped = daysAgo == 3L && time == evening
            medications.answer(
                slot =
                    DueDose(
                        courseId = metacam,
                        scheduledOn = day,
                        scheduledTime = time,
                        at = day.atTime(time).atZone(zone).toInstant(),
                    ),
                status = if (skipped) DoseStatus.SKIPPED else DoseStatus.GIVEN,
                note = if (skipped) "Spat it straight out. Tried again in the morning." else null,
                // A few minutes after the slot, which is what a real answer looks like and keeps the
                // history's clock times from being suspiciously exact.
                recordedAt =
                    day
                        .atTime(time)
                        .plusMinutes(4)
                        .atZone(zone)
                        .toInstant(),
            )
        }
    }

    // Today's earliest slot answered and the rest left open — so the tab opens on one of each, and
    // 5f has an unanswered slot to arm tonight.
    medications
        .scheduleNow(bijou, zone = zone, today = today)
        .firstOrNull { it.course.id == metacam }
        ?.let { medications.answer(slot = it.due, status = DoseStatus.GIVEN, recordedAt = now) }

    val panacur =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bijou,
                name = "Panacur",
                doseAmount = "1 ml",
                startOn = today.minusDays(20),
                endOn = today.minusDays(7),
                notes = "Nine days for E. cuniculi. Finished.",
                // Off, because the course is over — and because a course with reminders off is a
                // state the editor's switch has to be able to show.
                remindersEnabled = false,
            ),
            times = listOf(LocalTime.of(9, 0)),
        )
    for (daysAgo in listOf(9L, 8L)) {
        val day = today.minusDays(daysAgo)
        medications.answer(
            slot =
                DueDose(
                    courseId = panacur,
                    scheduledOn = day,
                    scheduledTime = LocalTime.of(9, 0),
                    at = day.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant(),
                ),
            status = DoseStatus.GIVEN,
            recordedAt = day.atTime(LocalTime.of(9, 5)).atZone(zone).toInstant(),
        )
    }

    val recovery =
        medications.add(
            MedicationCourseEntity(
                bunnyId = bijou,
                name = "Recovery food",
                // The case the free-text amount exists for: nobody was given a number to type.
                doseAmount = "one syringe, as often as she will take it",
                startOn = today.minusDays(2),
            ),
        )
    medications.recordAdHoc(
        courseId = recovery,
        status = DoseStatus.GIVEN,
        note = "Took most of it.",
        recordedAt = now.daysAgo(1, LocalTime.of(22, 30)),
        now = now,
    )
}

/**
 * The vet half: **two directory entries and three visits**, chosen so every branch 5c draws is on
 * screen without waiting for a real appointment.
 *
 * - **A visit with a weighing**, which is the one that has to be looked at hardest: it puts a
 *   visit-tagged row in Bijou's weight history, where *Edit* and *Delete* are replaced by *Open the
 *   visit*, and it is the row the entry form refuses to overwrite (ADR-0021's amendment).
 * - **A visit with no vet**, because `vetId` is nullable and a visit that names nobody still has to
 *   render — an owner who saw an out-of-hours locum has no directory entry for them.
 * - **A visit on the housemate**, so deleting one bunny can be seen not to take the other's, and so
 *   the second directory entry is in use rather than decorative.
 *
 * Written **through the repositories**, like everything else here, so the weighing lands through the
 * same one transaction an owner's does rather than as two rows a seeder arranged by hand.
 */
private suspend fun seedVisits(
    vets: VetRepository,
    visits: VisitRepository,
    bijou: String,
    nugget: String,
    now: Instant,
) {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

    val kowalska =
        vets.add(
            VetEntity(
                name = "Dr Kowalska",
                clinic = "Klinika Ada",
                phone = "+48 22 000 00 00",
                notes = "Exotics on Tuesdays and Thursdays.",
            ),
        )
    val nowak = vets.add(VetEntity(name = "Dr Nowak", clinic = "Przychodnia Mokotów"))

    // The weight is deliberately a little under the series around it, so the visit's number is
    // recognisable in the chart rather than lost among the seeded weighings.
    visits.add(
        VisitEntity(
            bunnyId = bijou,
            vetId = kowalska,
            visitedOn = today.minusDays(9),
            reason = "Molar check",
            notes = "Spurs filed. Back in six months unless she goes off her food.",
        ),
        grams = 2380,
        now = now,
        zone = zone,
    )
    visits.add(
        VisitEntity(
            bunnyId = bijou,
            visitedOn = today.minusMonths(7),
            reason = "Vaccination",
        ),
        now = now,
        zone = zone,
    )
    visits.add(
        VisitEntity(
            bunnyId = nugget,
            vetId = nowak,
            visitedOn = today.minusDays(40),
            reason = "Scratched eye",
            notes = "Drops for five days.",
        ),
        now = now,
        zone = zone,
    )
}

/**
 * The watch half: **one running and one already run out**, which is both of the states 4d has to
 * show and neither of which can otherwise be looked at without waiting days for it.
 *
 * - **Bijou's is running**, four days in of seven, so Home's card carries *"Watch active · 3 days
 *   left"* with close-early beside it, the flag stops offering *Start a watch*, and Bijou is the
 *   bunny the next morning's sweep nags about.
 * - **Nugget's ran out yesterday**, so the auto-expiry prompt is on screen the moment the fixture
 *   lands rather than a week later — and because Nugget's series is deliberately steady, it is also
 *   the prompt's *no live flag* branch, where it has to report the record without letting the
 *   absence of a flag read as reassurance (ADR-0001).
 *
 * That pairing is the point: it puts Bijou under a running watch and Nugget under an expired one, so
 * the healthy day excludes exactly one of them, with the reason shown, and the difference between
 * "active" and "expired" is visible in the same tap rather than argued about from a test.
 */
private suspend fun seedWatches(
    watches: WatchRepository,
    bijou: String,
    nugget: String,
    now: Instant,
) {
    watches.start(bijou, WatchDuration.DAYS_7, now.daysAgo(4))
    watches.start(nugget, WatchDuration.DAYS_3, now.daysAgo(4))
}

/**
 * The care half: one of each state the Care screen has to render, so 4f reviews a full list and 4g
 * has something genuinely overdue to look at.
 *
 * - **An overdue nail trim** — a preset whose anchor is in the past and which has never been
 *   completed, which is the one path where `firstDueOn` is returned unmodified. It is also what the
 *   sweep posts about on the first run after seeding.
 * - **A vaccination due in months**, so a yearly interval and the `FREQ=YEARLY` hand-off can be seen
 *   without waiting a year, and so the list has something that is plainly *not* urgent.
 * - **A weigh-in with a completion history**, on the bunny who already has a year of weighings —
 *   which is the two-source completion (ADR-0018's amendment) with both sources non-empty, the only
 *   arrangement where the `max` of the two can be seen to be doing anything.
 * - **A custom reminder on the housemate**, because the free-text path has no type, no icon of its
 *   own and no preset interval, and every one of those is a branch nothing else here exercises.
 */
private suspend fun seedCare(
    care: CareRepository,
    bijou: String,
    nugget: String,
    now: Instant,
) {
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()

    care.add(
        CareReminderEntity(
            bunnyId = bijou,
            type = CareType.NAIL_TRIM,
            intervalCount = 6,
            intervalUnit = CareIntervalUnit.WEEK,
            firstDueOn = today.minusDays(11),
        ),
    )

    care.add(
        CareReminderEntity(
            bunnyId = bijou,
            type = CareType.VACCINATION,
            intervalCount = 1,
            intervalUnit = CareIntervalUnit.YEAR,
            firstDueOn = today.plusMonths(4),
        ),
    )

    val weighIn =
        care.add(
            CareReminderEntity(
                bunnyId = bijou,
                type = CareType.WEIGH_IN,
                intervalCount = 1,
                intervalUnit = CareIntervalUnit.WEEK,
                firstDueOn = today.minusWeeks(6),
            ),
        )
    // Two typed completions well behind the weight series, so the weigh-in's due date visibly comes
    // from the *later* of the two sources rather than from these.
    care.complete(weighIn, today.minusWeeks(5), today = today)
    care.complete(weighIn, today.minusWeeks(4), note = "Wriggled off the scale twice.", today = today)

    care.add(
        CareReminderEntity(
            bunnyId = nugget,
            label = "Hay order",
            intervalCount = 2,
            intervalUnit = CareIntervalUnit.MONTH,
            firstDueOn = today.plusDays(3),
        ),
    )
}

/**
 * A handful of pictures, generated rather than shipped as assets — an APK does not need to carry
 * fake rabbits, and going through [PhotoRepository.add] means these land the same way an owner's do:
 * downsampled, stripped, file before row (ADR-0020).
 *
 * The set is chosen for what it makes visible in the gallery:
 *
 * - **two EXIF dates, on the two *newest-added* photos**, back-dated by months. Ordering by
 *   `COALESCE(capturedAt, createdAt)` puts them in the middle of the grid; ordering by insertion
 *   would put them first, so a regression there is visible at a glance rather than by reading rows.
 * - **both orientations**, because the grid crops to squares and the pager fits the whole frame —
 *   a landscape photo is where those two disagree.
 * - **captions on some and not others**, since the pager renders the two cases differently.
 * - **a photo for the housemate too**, so deleting one bunny can be seen not to take the other's.
 */
private suspend fun seedPhotos(
    photos: PhotoRepository,
    cacheDir: File,
    bijou: String,
    nugget: String,
    now: Instant,
) {
    val samples =
        listOf(
            SamplePhoto(bijou, 1600, 1200, 0xFF8D6E63.toInt(), takenDaysAgo = null, caption = "Flopped on the rug"),
            SamplePhoto(bijou, 1200, 1600, 0xFF6D4C41.toInt(), takenDaysAgo = null),
            SamplePhoto(nugget, 1600, 1200, 0xFF9E9D24.toInt(), takenDaysAgo = null),
            // Added last, taken first: the two rows that prove the gallery orders by capture date.
            SamplePhoto(bijou, 1600, 1200, 0xFF00796B.toInt(), takenDaysAgo = 200, caption = "First week home"),
            SamplePhoto(bijou, 1200, 1600, 0xFF5D4037.toInt(), takenDaysAgo = 320),
        )

    samples.forEach { sample ->
        val source = writeSampleJpeg(cacheDir, sample, now)
        val id = photos.add(sample.bunnyId, source)
        sample.caption?.let { photos.setCaption(id, it) }
        // The original is rubbish the moment the pipeline has re-encoded it.
        source.path?.let { File(it).delete() }
    }
}

private data class SamplePhoto(
    val bunnyId: String,
    val width: Int,
    val height: Int,
    val colour: Int,
    val takenDaysAgo: Long?,
    val caption: String? = null,
)

/**
 * One generated JPEG: a flat colour with a lighter band down one side, which is enough to tell the
 * square grid crop apart from the pager's whole frame. A real `DateTimeOriginal` is written with
 * `saveAttributes` when the sample carries one, so the media pipeline reads the same tag a camera
 * would rather than a value handed to it directly.
 */
private fun writeSampleJpeg(
    cacheDir: File,
    sample: SamplePhoto,
    now: Instant,
): Uri {
    val bitmap = createBitmap(sample.width, sample.height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(sample.colour)
    canvas.drawRect(
        0f,
        0f,
        sample.width / 4f,
        sample.height.toFloat(),
        Paint().apply { color = 0x33FFFFFF },
    )

    val file = File(cacheDir, "sample-${UUID.randomUUID()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

    sample.takenDaysAgo?.let { days ->
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, EXIF_DATE.format(now.daysAgo(days)))
            saveAttributes()
        }
    }
    return Uri.fromFile(file)
}

/** EXIF's own format: local wall time, no zone — which is why the pipeline has to assume one. */
private val EXIF_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * The observation half: three entries carrying the cases the timeline is reviewed against.
 *
 * - **A shared observation with individual facts that differ** — which is the whole tray/individual
 *   split on one card: the droppings appear once, and only Bijou is subdued and hunched. Reviewing
 *   the collapse needs a group whose participants genuinely disagree, or a bug that copied one
 *   bunny's mood onto the other would look correct.
 * - **A healthy day**, written through exactly the shortcut's own field set, so what the button
 *   claims and what the timeline shows can be compared without tapping it.
 * - **A solo observation recording "looked, none seen"** — `symptomsChecked` with no links, the one
 *   state the join table cannot express (ADR-0010), and the only way to see that it renders as an
 *   affirmative rather than as a blank.
 */
private suspend fun seedObservations(
    observations: ObservationRepository,
    symptoms: SymptomRepository,
    bijou: String,
    nugget: String,
    now: Instant,
) {
    // A built-in's id is minted by the seed callback, so it can only be looked up by key here.
    val symptomIdsByKey = symptoms.allNow().filter { it.key != null }.associate { it.key!! to it.id }

    val shared =
        observations.add(
            participants = listOf(bijou, nugget),
            recordedAt = now.daysAgo(1, OBSERVATION_TIME),
            facts =
                ObservationFacts(
                    // One tray, one real-world fact — and a worrying one, matching the weight drop.
                    tray =
                        TrayFacts(
                            droppingsAmount = DroppingsAmount.FEW,
                            droppingsSize = DroppingsSize.SMALL,
                            droppingsForm = DroppingsForm.ROUND,
                            cecotropes = Cecotropes.LEFT_UNEATEN,
                        ),
                ),
        )
    // Individual facts for Bijou alone, through the path that touches exactly one row.
    observations.updateIndividual(
        observationId = shared.first(),
        individual =
            IndividualFacts(
                appetite = Appetite.REDUCED,
                mood = Mood.SUBDUED,
                activity = ActivityLevel.QUIET,
                water = WaterIntake.LESS,
                note = "Sitting hunched in the corner, not interested in greens.",
                symptomIds = setOfNotNull(symptomIdsByKey["hunched_posture"], symptomIdsByKey["loud_teeth_grinding"]),
            ),
    )

    observations.add(
        participants = listOf(bijou, nugget),
        recordedAt = now.daysAgo(3, OBSERVATION_TIME),
        facts = healthyDayFacts(),
    )

    observations.add(
        participants = listOf(nugget),
        recordedAt = now.daysAgo(5, OBSERVATION_TIME),
        facts =
            ObservationFacts(
                tray = TrayFacts(droppingsAmount = DroppingsAmount.NORMAL),
                // Looked, none seen — affirmative, and distinguishable in the database from never
                // having opened the picker.
                individual = IndividualFacts(mood = Mood.BRIGHT, symptomsChecked = true),
            ),
    )
}

/**
 * A year of uneven, back-dated weighings carrying the four cases the chart and the flag are reviewed
 * against:
 *
 * - **uneven intervals** — roughly weekly with jitter, so the chart cannot be plotted by list index
 *   without visibly lying (house rule);
 * - **a fat-fingered entry** — `250` typed for `2500`, which the trailing *median* must resist
 *   (ADR-0021);
 * - **a tied `recordedAt`** — two rows at the same minute, which only the stated total order
 *   separates;
 * - **a long gap before an acute drop**, which must still fire: damping by elapsed time is the one
 *   thing ADR-0001 says must never silence this signal.
 */
private fun bijouSeries(): List<Pair<Long, Int>> {
    val random = Random(SAMPLE_SEED)
    val series = mutableListOf<Pair<Long, Int>>()

    // Roughly weekly from a year ago until five months ago, wobbling around 2 500 g.
    var daysAgo = 365L
    while (daysAgo > 150L) {
        series += daysAgo to 2500 + random.nextInt(-40, 41)
        daysAgo -= random.nextInt(5, 10).toLong()
    }

    series += 203L to 2495 // the tie: same day, same clock time as the row below
    series += 203L to 2510
    series += 180L to 250 // the fat-fingered one — a 2 500 g bunny cannot weigh 250 g

    // Then nothing at all for five months: the owner stopped weighing, which is a period nobody
    // recorded rather than evidence of anything (ADR-0001).
    series += 21L to 2490
    series += 14L to 2470
    series += 7L to 2480
    series += 2L to 2300 // ~7.6 % below the 2 480 g baseline: the flag fires

    return series
}

/**
 * `n` days back, stamped at a fixed clock time in the phone's own zone.
 *
 * Kotlin note: `at` is a *defaulted* parameter, so every weighing call site stays `daysAgo(21)` and
 * only the observations pass a second argument — no overload, and no call site to keep in step.
 */
private fun Instant.daysAgo(
    days: Long,
    at: LocalTime = WEIGHING_TIME,
): Instant =
    atZone(ZoneId.systemDefault())
        .minusDays(days)
        .with(at)
        .toInstant()
        .truncatedTo(ChronoUnit.MINUTES)
