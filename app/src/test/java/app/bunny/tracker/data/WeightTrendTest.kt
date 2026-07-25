package app.bunny.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The trend flag is the app's single load-bearing safety signal, so this reads as a **table of
 * cases** rather than as a few examples: ADR-0001 fixes the trigger and the constants, ADR-0021 the
 * baseline estimator and the total order, and each clause of both has a case here that fails if it
 * is quietly changed.
 *
 * Deliberately plain arithmetic — no Room, no Android, no coroutines. That is why the trend math
 * takes [Weighing] rather than `WeightEntity`.
 */
class WeightTrendTest {
    // ---- The firing gate: two priors, never one (ADR-0021) ----------------------------------

    @Test
    fun `one prior can never fire, and two can`() {
        // Two points do not describe a trend, so a bunny's first two weighings are silent however
        // steep the fall between them. Accepted deliberately.
        assertEquals(
            TrendFlag.NotEnoughHistory,
            flagFor(series(0 to 2500, 1 to 2000)),
        )

        assertFires(
            "a third weighing gives the baseline two priors, which is enough",
            series(0 to 2500, 1 to 2500, 2 to 2000),
        )
    }

    @Test
    fun `at exactly two priors the baseline is the higher, so a low typo cannot suppress`() {
        // ADR-0021's silent failure: priors of 2500, 250 average to 1375 g, which would put a
        // healthy bunny permanently "above baseline" and suppress every later drop. Higher-of-two
        // gives 2500 and the drop fires.
        val drop =
            assertFires(
                "a 250 typed for 2500 must not become a 1375 g baseline",
                series(0 to 250, 1 to 2500, 2 to 2300),
            )
        assertEquals(2500, drop.baselineGrams)
        assertEquals(200, drop.dropGrams)
    }

    // ---- Interval independence (ADR-0001) ---------------------------------------------------

    @Test
    fun `an acute drop after a long gap still fires`() {
        // Three weeklies, then nothing for a year, then a 300 g fall. The trigger is a level
        // trigger and asks nothing about rate: damping by elapsed time is the one thing ADR-0001
        // says must never silence this signal.
        val drop =
            assertFires(
                "a year's gap does not dampen an acute drop",
                series(0 to 2500, 7 to 2500, 14 to 2500, 400 to 2200),
            )
        assertEquals(300, drop.dropGrams)
        assertEquals(day(400), drop.currentAt)
    }

    // ---- The stated total order (ADR-0021) --------------------------------------------------

    @Test
    fun `rows arriving out of order window correctly`() {
        // The DAO returns the series already ordered, but the pure function sorts anyway — one that
        // silently required its caller to have sorted would be a stub wearing this test's name.
        val jumbled =
            listOf(
                weighing(on = 400, grams = 2200),
                weighing(on = 0, grams = 2500),
                weighing(on = 14, grams = 2500),
                weighing(on = 7, grams = 2500),
            )

        val drop = assertFires("the same readings in any order give the same answer", jumbled)
        assertEquals(300, drop.dropGrams)
        assertEquals(day(400), drop.currentAt)
    }

    @Test
    fun `a re-typed weight for the same minute becomes the prior, not the current reading`() {
        // ADR-0021's commonest correction: enter 250, watch the flag fire, enter 2500 for the same
        // minute. Under `recordedAt` desc then `createdAt` desc the later-typed row is *current*,
        // so the bunny reads as steady. Had the tie resolved the other way the typo would be
        // current against a 2500 baseline and this would fire — so Steady is what proves the
        // direction of the tie-break.
        val typo = weighing(on = 3, grams = 250, typedOn = 3, id = "typo")
        val correction = weighing(on = 3, grams = 2500, typedOn = 4, id = "correction")

        assertEquals(
            TrendFlag.Steady,
            flagFor(series(0 to 2500, 1 to 2500, 2 to 2500) + typo + correction),
        )
    }

    @Test
    fun `a full tie on both timestamps resolves by id, whatever order the rows arrive in`() {
        // The seeder and a double-weighing can both produce rows identical in `recordedAt` *and*
        // `createdAt`. `id` ascending is arbitrary, but it is stated, so the baseline can never
        // depend silently on SQLite's row order.
        val a = weighing(on = 3, grams = 2000, id = "a")
        val b = weighing(on = 3, grams = 2400, id = "b")
        val priors = series(0 to 2500, 1 to 2500, 2 to 2500)

        val forwards = assertFires("id ascending makes `a` the current reading", priors + a + b)
        val backwards = assertFires("and input order cannot change that", priors + b + a)

        assertEquals(500, forwards.dropGrams)
        assertEquals(forwards, backwards)
    }

    // ---- The noise floor, at both ends of the range (ADR-0001) ------------------------------

    @Test
    fun `across the range this app serves the 5 percent does all the work`() {
        // Netherland dwarf: 5 % of 1100 g is 55 g, the floor is 22 g, so the threshold is 55.
        assertFires("1045 g is exactly 5 % below", series(0 to 1100, 1 to 1100, 2 to 1100, 3 to 1045))
        assertEquals(
            TrendFlag.Steady,
            flagFor(series(0 to 1100, 1 to 1100, 2 to 1100, 3 to 1046)),
        )

        // Flemish giant: 5 % of 6500 g is 325 g, the floor is 130 g. Same rule, 6× the bunny.
        assertFires("6175 g is exactly 5 % below", series(0 to 6500, 1 to 6500, 2 to 6500, 3 to 6175))
        assertEquals(
            TrendFlag.Steady,
            flagFor(series(0 to 6500, 1 to 6500, 2 to 6500, 3 to 6176)),
        )
    }

    @Test
    fun `the floor binds on a kit, so the max cannot be simplified away`() {
        // The one case in the whole app where `max(5 % of baseline, noise floor)` resolves to the
        // floor. On a 300 g four-week-old kit 5 % is 15 g, inside real scale noise; the 20 g
        // absolute is what keeps that from being a signal.
        assertEquals(
            "285 g is 5 % below 300 g and would fire without the floor",
            TrendFlag.Steady,
            flagFor(series(0 to 300, 1 to 300, 2 to 300, 3 to 285)),
        )

        assertFires("20 g below still fires", series(0 to 300, 1 to 300, 2 to 300, 3 to 280))
    }

    // ---- Auto-clearing (ADR-0001) -----------------------------------------------------------

    @Test
    fun `a stabilized-low bunny auto-clears as the baseline catches up`() {
        val dropped = series(0 to 2500, 1 to 2500, 2 to 2500, 3 to 2300)
        assertFires("the fall to 2300 g fires", dropped)

        // Still firing one reading later — two of the three priors are the old 2500s.
        assertFires("holding at 2300 g against a 2500 g baseline still fires", dropped + weighing(4, 2300))

        // And clears on the next, with the baseline itself now 2300. Nothing was dismissed; the
        // signal went away because the claim it made stopped being true.
        assertEquals(
            TrendFlag.Steady,
            flagFor(dropped + weighing(4, 2300) + weighing(5, 2300)),
        )
    }

    // ---- The acknowledgment watermark and the re-raise bar (ADR-0001) -----------------------

    @Test
    fun `acknowledging silences a wobble within the floor`() {
        // Baseline 2500 g, so the re-raise bar is 50 g below the acknowledged 2300 g. A 20 g
        // further dip is gut and bladder, not news.
        val flag =
            flagFor(
                acknowledgedAt2300 + weighing(4, 2280),
                acknowledgment = TrendAcknowledgment(grams = 2300, acknowledgedAt = day(3)),
            )

        assertTrue("a wobble inside the floor stays quiet — got $flag", flag is TrendFlag.Acknowledged)
        assertEquals(day(3), (flag as TrendFlag.Acknowledged).acknowledgedAt)
        // The drop is still reported, because it is still true. It just is not a fresh signal.
        assertEquals(220, flag.drop.dropGrams)
    }

    @Test
    fun `acknowledging does not silence a further slide`() {
        // A bunny already flagged *and* acknowledged must not be allowed to slide a further 5 % in
        // silence, which is why the re-raise bar is the floor and not the trigger.
        val drop =
            assertFires(
                "100 g below the watermark breaks back through",
                acknowledgedAt2300 + weighing(4, 2200),
                acknowledgment = TrendAcknowledgment(grams = 2300, acknowledgedAt = day(3)),
            )
        assertEquals(2200, drop.currentGrams)
    }

    @Test
    fun `the re-raise bar is strictly more than the floor`() {
        // "Falls below the watermark by more than the noise floor" — 50 g exactly is not more than
        // 50 g. The boundary is pinned so it cannot drift with a refactor.
        val ack = TrendAcknowledgment(grams = 2300, acknowledgedAt = day(3))

        assertTrue(flagFor(acknowledgedAt2300 + weighing(4, 2250), ack) is TrendFlag.Acknowledged)
        assertTrue(flagFor(acknowledgedAt2300 + weighing(4, 2249), ack) is TrendFlag.WorthACloserLook)
    }

    @Test
    fun `a trigger going false reports the watermark stale, so the next episode fires from scratch`() {
        val ack = TrendAcknowledgment(grams = 2300, acknowledgedAt = day(3))
        val recovered = acknowledgedAt2300 + weighing(4, 2500)

        // Back to 2500 g: the trigger is false, so the episode is over and the row on disk is now
        // stale. The evaluation only reports that — `WeightRepository` does the deleting, because
        // under "All bunnies" every vitals card evaluates the flag and N cards would race.
        val evaluation = evaluateTrend(recovered, ack)
        assertEquals(TrendFlag.Steady, evaluation.flag)
        assertTrue("the watermark is stale once the trigger goes false", evaluation.watermarkIsStale)

        // A genuinely new drop, months later. With the watermark discarded it fires from scratch...
        val newEpisode = recovered + weighing(5, 2300)
        assertFires("a new episode is not silenced by a recovered one", newEpisode)

        // ...and this is what it costs if the repository does not discard: 2300 g is not below the
        // 2300 g watermark by more than the floor, so a months-old acknowledgment of a recovered
        // episode silences a real drop. The pure function cannot fix this alone — it would have to
        // re-walk every intermediate state, which is the history audit ADR-0001 rejects.
        assertTrue(
            "the stale watermark is exactly what would suppress it",
            flagFor(newEpisode, ack) is TrendFlag.Acknowledged,
        )
    }

    @Test
    fun `an acknowledgment is stale when there is no longer enough history to judge`() {
        // The owner deleted their way back down to two weighings while a flag stood acknowledged.
        val evaluation =
            evaluateTrend(
                series(0 to 2500, 1 to 2000),
                TrendAcknowledgment(grams = 2000, acknowledgedAt = day(1)),
            )

        assertEquals(TrendFlag.NotEnoughHistory, evaluation.flag)
        assertTrue(evaluation.watermarkIsStale)
    }

    // ---- Present tense: back-dating (ADR-0001) ----------------------------------------------

    @Test
    fun `a back-dated reading changes the current flag`() {
        // 2350 g sits just inside the bar against a 2450 g baseline.
        val before = series(0 to 2000, 2 to 2450, 4 to 2500, 6 to 2350)
        assertEquals(TrendFlag.Steady, flagFor(before))

        // Remembering a 2600 g weighing from day 3 pushes the median baseline up to 2500 g, and the
        // same current reading is now a drop. The flag is a live claim about the latest weighing,
        // so it recomputes rather than being frozen at entry time.
        val drop = assertFires("the baseline moved beneath it", before + weighing(3, 2600))
        assertEquals(2500, drop.baselineGrams)
        assertEquals(day(6), drop.currentAt)
    }

    @Test
    fun `a dip already recovered from is never resurrected`() {
        val dipped = series(0 to 2500, 1 to 2500, 2 to 2500, 3 to 2200)
        assertFires("it fired at the time", dipped)

        // Two normal weighings later the bunny is fine, and the flag is evaluated only against the
        // latest reading and its trailing baseline. A dip already recovered from is not news — the
        // app never announces it retrospectively, however recently it was entered.
        assertEquals(
            TrendFlag.Steady,
            flagFor(dipped + weighing(4, 2500) + weighing(5, 2500)),
        )
    }

    // ---- The accepted limitation, pinned green (ADR-0021) -----------------------------------

    @Test
    fun `the gap blind spot is a known limitation, not a discovery`() {
        // ADR-0021's table: a kit grows up unlogged, then a year later the drops begin. Two of the
        // three priors are still stale, so the first post-gap drop is silent.
        val grewUpUnlogged = series(0 to 300, 31 to 500, 61 to 800, 456 to 2200)

        assertEquals(
            "a 150 g / 6.8 % fall against a stale 800 g baseline — silent, and accepted",
            TrendFlag.Steady,
            flagFor(grewUpUnlogged + weighing(486, 2050)),
        )

        // The blind window is one reading and self-heals on the next. That it is temporary is
        // precisely why the median's weakness was accepted where the two-element mean's — which was
        // permanent — was not. Do **not** "fix" this by ignoring priors older than N days: that
        // breaks interval-independence, and with all three priors discarded there is no baseline at
        // all, so the app goes silent on an acute drop after a long gap.
        val drop =
            assertFires(
                "the third post-gap reading has a live baseline and fires",
                grewUpUnlogged + weighing(486, 2050) + weighing(516, 1900),
            )
        assertEquals(2050, drop.baselineGrams)
        assertEquals(150, drop.dropGrams)
    }

    // ---- Degenerate input --------------------------------------------------------------------

    @Test
    fun `an empty series makes no claim either way`() {
        // Not "steady": absence of a flag is never evidence of health (ADR-0001).
        val evaluation = evaluateTrend(emptyList(), acknowledgment = null)
        assertEquals(TrendFlag.NotEnoughHistory, evaluation.flag)
        assertFalse(evaluation.watermarkIsStale)
    }

    /** The shared setup for the watermark cases: fires at 2300 g on day 3 against a 2500 g baseline. */
    private val acknowledgedAt2300 = series(0 to 2500, 1 to 2500, 2 to 2500, 3 to 2300)
}

// ---- Case-table helpers ----------------------------------------------------------------------

private val epoch: Instant = Instant.parse("2026-01-01T09:00:00Z")

private fun day(n: Int): Instant = epoch.plus(n.toLong(), ChronoUnit.DAYS)

/**
 * One weighing on a given day. [typedOn] defaults to the same day — back-dating and re-typing are
 * the cases that need it apart, and they say so explicitly.
 */
private fun weighing(
    on: Int,
    grams: Int,
    typedOn: Int = on,
    id: String = "d$on-${grams}g",
) = Weighing(id = id, grams = grams, recordedAt = day(on), createdAt = day(typedOn))

/** A series written the way an owner logs it — oldest first — as `day to grams` pairs. */
private fun series(vararg readings: Pair<Int, Int>): List<Weighing> =
    readings.map { (on, grams) -> weighing(on, grams) }

private fun flagFor(
    series: List<Weighing>,
    acknowledgment: TrendAcknowledgment? = null,
): TrendFlag = evaluateTrend(series, acknowledgment).flag

private fun assertFires(
    message: String,
    series: List<Weighing>,
    acknowledgment: TrendAcknowledgment? = null,
): TrendDrop {
    val flag = flagFor(series, acknowledgment)
    assertTrue("$message — got $flag", flag is TrendFlag.WorthACloserLook)
    return (flag as TrendFlag.WorthACloserLook).drop
}
