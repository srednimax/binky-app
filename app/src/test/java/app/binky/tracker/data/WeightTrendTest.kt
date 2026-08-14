package app.binky.tracker.data

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
        val change =
            assertFires(
                "a 250 typed for 2500 must not become a 1375 g baseline",
                series(0 to 250, 1 to 2500, 2 to 2300),
            )
        assertEquals(2500, change.baselineGrams)
        assertEquals(200, change.changeGrams)
    }

    // ---- Interval independence (ADR-0001) ---------------------------------------------------

    @Test
    fun `an acute drop after a long gap still fires`() {
        // Three weeklies, then nothing for a year, then a 300 g fall. The trigger is a level
        // trigger and asks nothing about rate: damping by elapsed time is the one thing ADR-0001
        // says must never silence this signal.
        val change =
            assertFires(
                "a year's gap does not dampen an acute drop",
                series(0 to 2500, 7 to 2500, 14 to 2500, 400 to 2200),
            )
        assertEquals(300, change.changeGrams)
        assertEquals(day(400), change.currentAt)
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

        val change = assertFires("the same readings in any order give the same answer", jumbled)
        assertEquals(300, change.changeGrams)
        assertEquals(day(400), change.currentAt)
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

        assertEquals(500, forwards.changeGrams)
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
        assertEquals(220, flag.change.changeGrams)
    }

    @Test
    fun `acknowledging does not silence a further slide`() {
        // A bunny already flagged *and* acknowledged must not be allowed to slide a further 5 % in
        // silence, which is why the re-raise bar is the floor and not the trigger.
        val change =
            assertFires(
                "100 g below the watermark breaks back through",
                acknowledgedAt2300 + weighing(4, 2200),
                acknowledgment = TrendAcknowledgment(grams = 2300, acknowledgedAt = day(3)),
            )
        assertEquals(2200, change.currentGrams)
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
        val evaluation = evaluateTrend(recovered, ack, GrowthStage.Grown)
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
                GrowthStage.Grown,
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
        val change = assertFires("the baseline moved beneath it", before + weighing(3, 2600))
        assertEquals(2500, change.baselineGrams)
        assertEquals(day(6), change.currentAt)
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
        val change =
            assertFires(
                "the third post-gap reading has a live baseline and fires",
                grewUpUnlogged + weighing(486, 2050) + weighing(516, 1900),
            )
        assertEquals(2050, change.baselineGrams)
        assertEquals(150, change.changeGrams)
    }

    // ---- The gain rule: a six-month anchor, +10 % (ADR-0028) --------------------------------

    @Test
    fun `a rise of a tenth over six months fires, and a hair under it does not`() {
        val change = assertFires("2200 g is exactly 10 % above the anchor", risenTo(2200))
        assertEquals(TrendDirection.Gain, change.direction)
        // The anchor is a real reading with a real date, which is what lets the card say "up 200 g
        // since 1 January" rather than claiming a span it never measured.
        assertEquals(2000, change.baselineGrams)
        assertEquals(day(0), change.baselineAt)
        // Always positive, whichever way the number went — the direction is carried by `direction`.
        assertEquals(200, change.changeGrams)

        assertEquals(
            "one gram under the trigger is not the trigger",
            TrendFlag.Steady,
            flagFor(risenTo(2199)),
        )
    }

    @Test
    fun `no reading in the window makes no claim, which is not the same as steady`() {
        // +19 % in a hundred days, and the app says nothing: the anchor is defined as a reading 4–8
        // months back, and there is none. **Not a statement that the bunny is fine** (ADR-0001) —
        // the rule simply has nothing to judge against, exactly as it has nothing to say about a
        // bunny nobody weighed.
        //
        // `Steady` is the same *value* as the case above it, and deliberately so: nothing in the app
        // renders either as reassurance (`showsBanner` draws neither), so a third variant would be a
        // distinction with no consumer. The difference lives in these two cases, which is where a
        // later reader will look for it.
        assertEquals(
            TrendFlag.Steady,
            flagFor(series(300 to 2000, 330 to 2010, 360 to 2020, 400 to 2400)),
        )
    }

    @Test
    fun `the window is closed at four months and at eight`() {
        // Three priors bunched at the start, so the whole window opens and shuts on one number.
        fun risenOn(day: Int) = series(0 to 2000, 1 to 2000, 2 to 2000, day to 2400)

        assertFires("122 days back is inside the window", risenOn(122))
        assertEquals("121 is outside it", TrendFlag.Steady, flagFor(risenOn(121)))

        assertFires("244 days back is inside the window", risenOn(246))
        assertEquals("245 is outside it", TrendFlag.Steady, flagFor(risenOn(247)))
    }

    @Test
    fun `the anchor is the reading nearest six months, not the oldest one in the window`() {
        // Three candidates, all inside 4–8 months of the current reading: 122, 183 and 244 days
        // back. The middle one is the anchor, and the assertion is its *date* — a rule that took the
        // window's edge would pass a grams-only check by accident here.
        val change =
            assertFires(
                "the nearest reading to six months is the anchor",
                series(122 to 2300, 183 to 2000, 244 to 2200, 366 to 2400),
            )
        assertEquals(2000, change.baselineGrams)
        assertEquals(day(183), change.baselineAt)
    }

    @Test
    fun `a growing kit is silent, and an unknown age fires and asks`() {
        // 1400 g to 2400 g in six months is a healthy kit growing up, and any gain rule is
        // arithmetically correct about it every time.
        val grewUp = series(0 to 1400, 30 to 1600, 60 to 1800, 183 to 2400)

        assertEquals(
            "a bunny under a year old raises nothing",
            TrendFlag.Steady,
            flagFor(grewUp, growth = GrowthStage.Growing),
        )

        // With no birthday on file the app does **not** read the absent field as adulthood, which is
        // the move ADR-0001 bans — it fires and the card asks how old the bunny is.
        val asked = assertFires("an absent birthday is not adulthood", grewUp, growth = GrowthStage.Unknown)
        assertTrue("the card has to carry the question", asked.ageUnknown)

        val known = assertFires("a grown bunny fires without it", grewUp, growth = GrowthStage.Grown)
        assertFalse("nothing to ask once the birthday is known", known.ageUnknown)
    }

    @Test
    fun `the growth gate turns on the day the bunny turns one`() {
        val birthday = java.time.LocalDate.of(2025, 2, 28)

        assertEquals(GrowthStage.Growing, growthStageOn(birthday, birthday.plusMonths(12).minusDays(1)))
        assertEquals(GrowthStage.Grown, growthStageOn(birthday, birthday.plusMonths(12)))
        assertEquals(GrowthStage.Unknown, growthStageOn(null, birthday))
        // A birthday in the future is a typo in a date picker, and the app asks rather than reading
        // it as a newborn it would then be silent about for a year.
        assertEquals(GrowthStage.Unknown, growthStageOn(birthday, birthday.minusDays(1)))
    }

    @Test
    fun `a rise with only one prior stays not enough history`() {
        // Arithmetically the gain rule needs nothing more than an anchor and a current reading, so
        // this case *could* fire. It does not: ADR-0021's "two points do not describe a trend" is a
        // statement about how thin a record this app will speak from at all, and it is not weaker
        // for a rise than for a fall. Deliberate, and pinned so a refactor cannot quietly relax it.
        assertEquals(TrendFlag.NotEnoughHistory, flagFor(series(0 to 2000, 183 to 2400)))
    }

    @Test
    fun `when both triggers hold the loss is what shows`() {
        // Gained 800 g over six months and then dropped 200 g last week. Both rules are true and the
        // card says the most urgent one — which is also what keeps one watermark enough, and the
        // schema at 6 (ADR-0028).
        val change = assertFires("loss takes precedence", gainedThenDropped)
        assertEquals(TrendDirection.Loss, change.direction)
        assertEquals(2800, change.baselineGrams)
        assertEquals(200, change.changeGrams)
    }

    @Test
    fun `a watermark acknowledged for one direction never judges the other`() {
        // 2800 g is where the gain was acknowledged. The bunny has since dropped, so the card is now
        // a loss — judged against a 2800 g baseline the watermark sits *on*, not below. A watermark
        // on the wrong side of the reference was taken for the other direction, so it is discarded
        // rather than allowed to silence this one.
        val forTheGain = TrendAcknowledgment(grams = 2800, acknowledgedAt = day(170))
        val evaluation = evaluateTrend(gainedThenDropped, forTheGain, GrowthStage.Grown)

        assertTrue("the loss is unacknowledged — got ${evaluation.flag}", evaluation.flag is TrendFlag.WorthACloserLook)
        assertTrue(
            "and the row on disk is stale, which is what makes the repository drop it",
            evaluation.watermarkIsStale,
        )

        // The contrast, one gram lower: a watermark genuinely taken for *this* loss silences it.
        val forTheLoss = TrendAcknowledgment(grams = 2600, acknowledgedAt = day(183))
        assertTrue(flagFor(gainedThenDropped, forTheLoss) is TrendFlag.Acknowledged)
    }

    @Test
    fun `an acknowledged gain returns unacknowledged once a loss has interrupted it`() {
        // **A pinned limitation, not a bug** (ADR-0028). The watermark was discarded on the direction
        // change above, so when the loss resolves and the gain still holds, the owner is asked to
        // acknowledge a card they have already seen. The alternative was a second acknowledgment row:
        // a schema bump, a migration and an instrumented run, for a card that would say two things.
        val recovered = gainedThenDropped + weighing(190, 2800)
        val change = assertFires("the gain is back, and unacknowledged", recovered)
        assertEquals(TrendDirection.Gain, change.direction)
    }

    @Test
    fun `an acknowledged gain is silenced until it rises past the bar`() {
        val ack = TrendAcknowledgment(grams = 2200, acknowledgedAt = day(183))
        // The anchor is 2000 g, so the floor is 40 g and the bar is 2240 g. Same rule as the loss
        // side, mirrored: "breaks through by *more than* the floor" is strict on both.
        assertTrue(flagFor(risenTo(2200), ack) is TrendFlag.Acknowledged)
        assertTrue(flagFor(risenTo(2200) + weighing(190, 2240), ack) is TrendFlag.Acknowledged)
        assertTrue(flagFor(risenTo(2200) + weighing(190, 2241), ack) is TrendFlag.WorthACloserLook)
    }

    @Test
    fun `one high reading at the anchor suppresses the flag silently`() {
        // **The other pinned limitation** (ADR-0028). The bunny was 1900 g in January and is 2200 g
        // now — 15 %, comfortably over — but the January weighing was taken in the carrier and reads
        // 2100 g, so the rise measures 4.8 % and nothing is said. A median of a bucket around the
        // anchor would have resisted it; a single reading is the version the card can explain, and
        // this is what that costs. The app does not report having failed here, which is the part
        // worth knowing.
        assertEquals(
            TrendFlag.Steady,
            flagFor(series(0 to 2100, 30 to 1900, 60 to 1950, 183 to 2200)),
        )

        // The same six months with an honest anchor reading fires.
        assertFires("1900 g at the anchor is a 15 % rise", series(0 to 1900, 30 to 1900, 60 to 1950, 183 to 2200))
    }

    // ---- Degenerate input --------------------------------------------------------------------

    @Test
    fun `an empty series makes no claim either way`() {
        // Not "steady": absence of a flag is never evidence of health (ADR-0001).
        val evaluation = evaluateTrend(emptyList(), acknowledgment = null, growth = GrowthStage.Grown)
        assertEquals(TrendFlag.NotEnoughHistory, evaluation.flag)
        assertFalse(evaluation.watermarkIsStale)
    }

    /** The shared setup for the watermark cases: fires at 2300 g on day 3 against a 2500 g baseline. */
    private val acknowledgedAt2300 = series(0 to 2500, 1 to 2500, 2 to 2500, 3 to 2300)

    /**
     * Six months of near-steady weighings and then today's, whatever it says. The day-0 reading is
     * the anchor — exactly 183 days back — and the three recent priors keep the *loss* baseline
     * close to the current number, so nothing here can fire in the other direction by accident.
     */
    private fun risenTo(grams: Int) = series(0 to 2000, 30 to 2010, 60 to 2020, 183 to grams)

    /**
     * Both triggers at once: 2000 g in January, 2800 g by summer, and 200 g off in the last fortnight
     * — a 2800 g trailing baseline for the loss, a 2000 g anchor for the gain.
     */
    private val gainedThenDropped = series(0 to 2000, 150 to 2800, 160 to 2800, 170 to 2800, 183 to 2600)
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

/**
 * **[GrowthStage.Grown] by default, and that is the neutral choice rather than a shortcut.** The loss
 * rule has never asked how old a bunny is, so every case above it reads the same whatever is passed;
 * the gain cases that care state their own.
 */
private fun flagFor(
    series: List<Weighing>,
    acknowledgment: TrendAcknowledgment? = null,
    growth: GrowthStage = GrowthStage.Grown,
): TrendFlag = evaluateTrend(series, acknowledgment, growth).flag

private fun assertFires(
    message: String,
    series: List<Weighing>,
    acknowledgment: TrendAcknowledgment? = null,
    growth: GrowthStage = GrowthStage.Grown,
): TrendChange {
    val flag = flagFor(series, acknowledgment, growth)
    assertTrue("$message — got $flag", flag is TrendFlag.WorthACloserLook)
    return (flag as TrendFlag.WorthACloserLook).change
}
