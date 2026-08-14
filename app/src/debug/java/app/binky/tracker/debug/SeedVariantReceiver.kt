package app.binky.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.binky.tracker.AppContainer
import app.binky.tracker.BinkyApplication
import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.NeuterStatus
import app.binky.tracker.data.Sex
import app.binky.tracker.data.WeightEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * **Seed variants: how a capture scene reaches a state the sample data hides.**
 *
 * The default fixture is load-bearing — 61 matrix scenes, the before/after comparison and the Play
 * listing screenshots all rest on it — so a state it does not contain cannot be reached by changing
 * it. Phase 7 paid that cost three times over, writing a throwaway seed patch for the chart's
 * single-point states, for an expired watch and for the bunny editor's absent fields; each patch was
 * used once and thrown away, so each state was seen once and never again. This is the third rewrite
 * of that patch, which is the project's own signal that it belongs in the repository.
 *
 * **A variant is additive.** It runs *after* the sample data and adds to it; nothing here edits or
 * removes what the default seed wrote, so a scene that does not ask for a variant sees exactly the
 * fixture it always saw.
 *
 * **Why a broadcast rather than a debug row in Settings.** The Settings screen is itself captured —
 * `settings`, `settings-scrolled` and `settings-bottom` — so a new row there would change three
 * screenshots to make a fourth one possible. A receiver costs no pixels, no strings and no taps, and
 * it writes **through the repositories** exactly as `SampleData.kt` does, which is what keeps it
 * working across a schema change rather than being a SQL patch that rots the moment a table moves.
 *
 * **Debug source set only.** This file is compiled into the debug APK and does not exist in the
 * release build, which is why `exported="true"` in the debug manifest is affordable: the install it
 * can reach is `…tracker.debug`, the throwaway one, and the Play install holding real bunny history
 * is a different package entirely (ADR-0023).
 *
 * Driven by `scripts/edge-to-edge.py`:
 * ```
 * adb shell am broadcast -n binky.bunny.and.rabbit.tracker.debug/app.binky.tracker.debug.SeedVariantReceiver \
 *     -f 0x00000020 --es variant crowded
 * ```
 *
 * **Variants are additive and independent**, so two of them can sit on one install — `crowded` and
 * `gaining` add different bunnies and neither touches the other's.
 */
class SeedVariantReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val variant = intent.getStringExtra(EXTRA_VARIANT).orEmpty()
        val container = (context.applicationContext as BinkyApplication).container

        // Kotlin note: `goAsync()` is the closest thing a receiver has to returning a promise. A
        // plain `onReceive` must finish on the main thread before it returns, and seeding is
        // database work — so this asks the framework to keep the process alive until `finish()`,
        // which is also what makes `am broadcast` print a result the driver can check rather than
        // returning while the writes are still in flight.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val outcome = runCatching { seedVariant(variant, container) }
            pending.resultCode = if (outcome.isSuccess) 0 else 1
            pending.resultData =
                outcome.getOrElse { error -> "${error::class.simpleName}: ${error.message}" }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_VARIANT = "variant"
    }
}

/** Reported back through the broadcast result, so a driver never has to guess what happened. */
private suspend fun seedVariant(
    variant: String,
    container: AppContainer,
): String =
    when (variant) {
        "crowded" -> seedCrowded(container)
        "gaining" -> seedGaining(container)
        else -> error("unknown variant '$variant'; known: crowded, gaining")
    }

/**
 * **Seven bunnies: a fluffle of five, and a bonded pair with names nobody can fit on a line.**
 *
 * One variant for both halves of Phase 7.5 §8, because they are two ways into the same defect and a
 * capture is cheaper when one seed serves both. Home renders the housemates line at two sites — the
 * profile card and the switcher-style row — and neither bounds it, so:
 *
 * - **Bijou gains three housemates**, taking the fluffle to five. Four housemates is where the count
 *   cap fires ("Thumper, Clover & 3 others"), and *Pumpkin* is archived, because archived housemates
 *   render longer (*"Pumpkin (archived)"*) and are the first names the cap is meant to fold away.
 * - **A second, separate group carries the long names**, which is the case a count cap cannot fix.
 *   *Pip* lives with both of them, so its line is §8's own example word for word — *"Lives with
 *   Bartholomew-Maximilian & Wolfgang-Ferdinand"*: two names, no cap fires, and it still wraps.
 *   Keeping them out of Bijou's fluffle is the whole point — inside it they would be folded into
 *   the count and prove nothing. **Two long housemates and not one**, which the first capture
 *   settled: a single long name fits the row site on one line, so a pair is what the defect needs.
 *
 * The long names are the ones §8 argues with, kept verbatim so the screenshot and the reasoning say
 * the same thing.
 *
 * Each bunny gets **one weighing**, which is not decoration: a bunny with no weight history draws an
 * empty state on the card instead of a value row, and the thing under test is how tall the card
 * grows.
 */
private suspend fun seedCrowded(container: AppContainer): String {
    val bunnies = container.bunnyRepository
    val existing = bunnies.activeBunnies.first()
    // The variant is additive, so it needs something to add *to*. Failing loudly here is what stops
    // a scene shooting a two-bunny Home under a five-bunny name.
    val bijou = existing.firstOrNull { it.name == "Bijou" } ?: error("seed the sample data first")
    if (existing.any { it.name == LONG_NAMES.first }) return "already present"

    val now = Instant.now()

    suspend fun add(
        name: String,
        grams: Int,
        livesWith: String,
    ): String {
        val id = bunnies.add(BunnyEntity(name = name, sex = Sex.UNKNOWN, neutered = NeuterStatus.YES))
        container.fluffleRepository.livesWith(id, livesWith)
        container.weightRepository.add(
            WeightEntity(
                bunnyId = id,
                grams = grams,
                recordedAt =
                    now
                        .atZone(ZoneId.systemDefault())
                        .minusDays(2)
                        .with(LocalTime.of(8, 30))
                        .toInstant()
                        .truncatedTo(ChronoUnit.MINUTES),
            ),
        )
        return id
    }

    add("Clover", 1950, livesWith = bijou.id)
    add("Thistle", 2120, livesWith = bijou.id)
    val pumpkin = add("Pumpkin", 1840, livesWith = bijou.id)
    // Archived *after* the bond, because archiving deliberately leaves fluffle membership alone —
    // the survivor of a pair keeps having lived with them (ADR-0004, ADR-0008), which is exactly the
    // longer label the cap has to fold first.
    bunnies.archive(pumpkin)

    val bartholomew =
        bunnies.add(BunnyEntity(name = LONG_NAMES.first, sex = Sex.MALE, neutered = NeuterStatus.YES))
    container.weightRepository.add(
        WeightEntity(bunnyId = bartholomew, grams = 2340, recordedAt = now.truncatedTo(ChronoUnit.MINUTES)),
    )
    add(LONG_NAMES.second, 2260, livesWith = bartholomew)
    // The short name in the long-named group, and the one whose card is the actual evidence: *Pip*
    // has exactly two housemates, so no count cap can fire, and its own name takes up nothing — so
    // whatever wraps is the two names and not the subject.
    add("Pip", 1730, livesWith = bartholomew)

    return "seeded"
}

/** Phase 7.5 §8's own example, kept word for word so the evidence matches the argument. */
private val LONG_NAMES = "Bartholomew-Maximilian" to "Wolfgang-Ferdinand"

/**
 * **Two bunnies putting on weight: the gain flag's two states** (ADR-0028, Phase 7.5 §1).
 *
 * `SampleData.kt` has no rising series — Phase 7 records that it pairs the trend flag with the
 * running watch and the steady series with the expired one — so without this the gain card would be
 * verified by hand once and then be invisible to the harness forever: no matrix cell, no screenshot,
 * no regression coverage for a permanent feature. It is the first customer §4 argued seed variants
 * for, and the reason they are not a throwaway patch.
 *
 * **The states are the two the rule can produce, and they differ by one field.**
 *
 * - **Rosemary has a birthday**, three years back, so the card is the plain gain: *up 260 g since*
 *   the anchor's date, with the vet-advised-gain line where a loss carries the vet-diet one.
 * - **Juniper has none**, which is the case ADR-0028 calls load-bearing: an absent field may not be
 *   read as adulthood, so the flag fires **and the card asks how old the bunny is**. That question is the
 *   only visual difference between the two cards, which is exactly why both are photographed.
 *
 * **The dates are chosen against the rule rather than eyeballed.** The anchor is the reading nearest
 * six months back inside a 4–8 month window, so the 190-day reading is it and the 120-day one is
 * deliberately *outside* the window — a series where several readings qualify would still capture,
 * but it would stop proving which one the card names. The three recent readings keep the trailing
 * median close to today's number, so the loss trigger cannot fire and steal precedence.
 */
private suspend fun seedGaining(container: AppContainer): String {
    val bunnies = container.bunnyRepository
    val existing = bunnies.activeBunnies.first()
    // Additive, like `crowded`: it needs the sample data to add to, and failing here is what stops a
    // scene shooting an ordinary Home under the gain card's name.
    if (existing.none { it.name == "Bijou" }) error("seed the sample data first")
    if (existing.any { it.name == RISING.first }) return "already present"

    val today = LocalDate.now()

    suspend fun add(
        name: String,
        birthDate: LocalDate?,
        series: List<Pair<Long, Int>>,
    ) {
        val id =
            bunnies.add(
                BunnyEntity(
                    name = name,
                    sex = Sex.FEMALE,
                    neutered = NeuterStatus.YES,
                    birthDate = birthDate,
                    birthDateApproximate = false,
                ),
            )
        series.forEach { (daysAgo, grams) ->
            container.weightRepository.add(
                WeightEntity(
                    bunnyId = id,
                    grams = grams,
                    recordedAt =
                        Instant
                            .now()
                            .atZone(ZoneId.systemDefault())
                            .minusDays(daysAgo)
                            .with(LocalTime.of(8, 30))
                            .toInstant()
                            .truncatedTo(ChronoUnit.MINUTES),
                ),
            )
        }
    }

    // 2000 g at the anchor, 2260 g now: +13 %, comfortably over the +10 % step and not so far over
    // that the number stops looking like a real rabbit's.
    add(
        RISING.first,
        birthDate = today.minusYears(3),
        series =
            listOf(
                190L to 2000,
                120L to 2080,
                60L to 2150,
                2L to 2260,
            ),
    )
    add(RISING.second, birthDate = null, series = listOf(190L to 1800, 120L to 1850, 60L to 1900, 2L to 2030))

    return "seeded"
}

/** The pair, in the order the states are argued: birthday known, then absent. */
private val RISING = "Rosemary" to "Juniper"
