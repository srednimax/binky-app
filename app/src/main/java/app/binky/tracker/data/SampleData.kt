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
    return true
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
