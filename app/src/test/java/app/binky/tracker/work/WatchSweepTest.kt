package app.binky.tracker.work

import app.binky.tracker.data.WatchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the morning sweep decides about watches (PLAN 4d, ADR-0024).
 *
 * Every silence here is a rule from ADR-0001 or ADR-0004 made checkable: an archived bunny is never
 * chased, a nag happens once a day, and an owner who has already looked is not asked again. Proving
 * those on a phone means one assertion per morning; proving them here means a table.
 */
class WatchSweepTest {
    private val utc: ZoneId = ZoneId.of("UTC")

    /** 09:00, which is the sweep's default hour. */
    private val morning: Instant = Instant.parse("2026-05-20T09:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 5, 20)

    private fun watched(
        name: String = "Bijou",
        archived: Boolean = false,
        endsIn: Duration = Duration.ofDays(4),
        lastNaggedOn: LocalDate? = null,
        lastObservationAt: Instant? = null,
    ) = WatchedBunny(
        id = name.lowercase(),
        name = name,
        archived = archived,
        watch =
            WatchEntity(
                bunnyId = name.lowercase(),
                startedAt = morning.minus(Duration.ofDays(3)),
                endsAt = morning.plus(endsIn),
                lastNaggedOn = lastNaggedOn,
            ),
        lastObservationAt = lastObservationAt,
    )

    private fun due(vararg bunnies: WatchedBunny) = watchesDueForNagging(bunnies.toList(), morning, utc)

    @Test
    fun `a running watch with nothing logged is nagged about`() {
        assertEquals(listOf(DueNag("bijou", "Bijou")), due(watched()))
    }

    @Test
    fun `an archived bunny is never nagged about`() {
        // Archiving deletes the row, so this is belt-and-braces — and deliberately so. An archived
        // bunny has died or been rehomed, and a daily "have you checked on them?" is the failure
        // ADR-0001 names for a trend flag on a memorial page. The rule lives in the derivation, not
        // in whichever query happened to be run (ADR-0004).
        assertTrue(due(watched(archived = true)).isEmpty())
    }

    @Test
    fun `an expired watch stops nagging immediately, before any prompt is answered`() {
        // Expiry is what ends the chasing; the prompt that follows is only about re-arming. An
        // owner who ignores the dialog for a week is not chased for a week.
        assertTrue(due(watched(endsIn = Duration.ZERO)).isEmpty())
        assertTrue(due(watched(endsIn = Duration.ofDays(-3))).isEmpty())
    }

    @Test
    fun `already nagged today is not nagged again, and tomorrow is a fresh day`() {
        // The sweep can run more than once a day — a retry, a reboot, a Doze window closing — so
        // "once daily" has to be a recorded fact rather than an assumption about the scheduler.
        assertTrue(due(watched(lastNaggedOn = today)).isEmpty())
        assertEquals(1, due(watched(lastNaggedOn = today.minusDays(1))).size)
    }

    @Test
    fun `an observation logged last evening settles this morning`() {
        // 20:00 yesterday is 13 hours before a 09:00 sweep: inside the rolling day, so no nag. The
        // window is rolling precisely so a calendar-day boundary the owner never thought about does
        // not chase somebody who looked before bed.
        val lastEvening = Instant.parse("2026-05-19T20:00:00Z")
        assertTrue(due(watched(lastObservationAt = lastEvening)).isEmpty())
    }

    @Test
    fun `an observation thirty hours ago does not settle this morning`() {
        // The pair that makes the window real: 03:00 the previous day is *yesterday* by the clock
        // and 30 hours by the calendar, and neither reading says anybody has looked since.
        val thirtyHoursAgo = morning.minus(Duration.ofHours(30))
        assertEquals(1, due(watched(lastObservationAt = thirtyHoursAgo)).size)
    }

    @Test
    fun `the window is exactly twenty-four hours`() {
        assertEquals(Duration.ofHours(24), WATCH_SATISFIED_WITHIN)
        // Dead on the boundary counts as too long ago: at 24h the owner last looked a full day
        // back, which is the question being asked.
        assertEquals(1, due(watched(lastObservationAt = morning.minus(WATCH_SATISFIED_WITHIN))).size)
        assertTrue(due(watched(lastObservationAt = morning.minus(WATCH_SATISFIED_WITHIN).plusMillis(1))).isEmpty())
    }

    @Test
    fun `a future-dated observation is not evidence anybody looked`() {
        // The forms reject a future timestamp; this is the sweep declining to trust that they
        // always will, since the failure would be silent — a watch that never nags again.
        assertEquals(1, due(watched(lastObservationAt = morning.plus(Duration.ofDays(1)))).size)
    }

    @Test
    fun `every watched bunny is judged on their own facts`() {
        val nags =
            due(
                watched(name = "Bijou"),
                watched(name = "Nugget", lastNaggedOn = today),
                watched(name = "Clover", lastObservationAt = morning.minusSeconds(3600)),
                watched(name = "Hazel", archived = true),
            )
        assertEquals(listOf("Bijou"), nags.map { it.bunnyName })
    }

    @Test
    fun `a nag id is stable, positive, out of the reserved block, and not a care id`() {
        val id = watchNotificationId("bijou")
        assertEquals(id, watchNotificationId("bijou"))
        assertTrue(id >= RESERVED_NOTIFICATION_IDS)
        // The salt earns its place here: the same string is both a plausible bunny id and a
        // plausible reminder id, and the two are posted by the same sweep on the same morning. Left
        // unsalted they would replace each other's notification.
        assertNotEquals(careNotificationId("bijou"), id)
    }
}
