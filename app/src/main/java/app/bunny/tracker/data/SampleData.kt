package app.bunny.tracker.data

import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
 *
 * Checkpoint 2f adds the observation half: a shared observation across these two bunnies, and
 * symptom links.
 */
private const val SAMPLE_SEED = 20260726L

/** Debug fixture names, deliberately not in `strings.xml`: no owner ever sees this action. */
private const val SAMPLE_BUNNY = "Bijou"
private const val SAMPLE_HOUSEMATE = "Nugget"

/** Every weighing is stamped at the same clock time, so a tie has to be made deliberately. */
private val WEIGHING_TIME: LocalTime = LocalTime.of(8, 30)

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
    now: Instant = Instant.now(),
): Boolean {
    val existing = bunnies.activeBunnies.first()
    if (existing.any { it.name == SAMPLE_BUNNY || it.name == SAMPLE_HOUSEMATE }) return false

    val bijou = bunnies.add(BunnyEntity(name = SAMPLE_BUNNY, sex = Sex.FEMALE, neutered = NeuterStatus.YES))
    val nugget = bunnies.add(BunnyEntity(name = SAMPLE_HOUSEMATE, sex = Sex.MALE, neutered = NeuterStatus.YES))
    // A bonded pair, so 2f's shared observation has somewhere to land (ADR-0008).
    fluffles.livesWith(bijou, nugget)

    for ((daysAgo, grams) in bijouSeries()) {
        weights.add(WeightEntity(bunnyId = bijou, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }
    // Nugget stays steady, so the two cards on Home show a flagged bunny beside an unflagged one.
    for ((daysAgo, grams) in listOf(28L to 1780, 21L to 1795, 14L to 1785, 7L to 1790, 1L to 1788)) {
        weights.add(WeightEntity(bunnyId = nugget, grams = grams, recordedAt = now.daysAgo(daysAgo)))
    }
    return true
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

private fun Instant.daysAgo(days: Long): Instant =
    atZone(ZoneId.systemDefault())
        .minusDays(days)
        .with(WEIGHING_TIME)
        .toInstant()
        .truncatedTo(ChronoUnit.MINUTES)
