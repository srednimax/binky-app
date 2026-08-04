package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The two rules a visit-recorded weighing obeys (ADR-0017, ADR-0021's amendment), on the JVM.
 *
 * Both are pure by design, and this is what that buys: the DST case is a test rather than a March
 * morning, and the rule that protects the vet's number from the weight form is checked without a
 * database, a screen or a device anywhere near it.
 */
class VisitWeighingTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val london = ZoneId.of("Europe/London")

    // ---- min(noon on visitedOn, now) --------------------------------------------------------------

    @Test
    fun `a past visit is stamped at noon on the day it happened`() {
        val at =
            visitWeighingAt(
                visitedOn = LocalDate.of(2026, 5, 20),
                now = Instant.parse("2026-06-01T07:00:00Z"),
                zone = warsaw,
            )

        // Warsaw is UTC+2 in May, so local noon is 10:00Z.
        assertEquals(Instant.parse("2026-05-20T10:00:00Z"), at)
        assertEquals(LocalTime.NOON, at.atZone(warsaw).toLocalTime())
    }

    @Test
    fun `a visit typed this morning is stamped now rather than three hours into the future`() {
        // 09:00 in Warsaw, and the visit is dated today: noon has not happened yet.
        val now = Instant.parse("2026-05-20T07:00:00Z")

        val at = visitWeighingAt(visitedOn = LocalDate.of(2026, 5, 20), now = now, zone = warsaw)

        assertEquals(now, at)
    }

    @Test
    fun `a visit typed this afternoon still lands at noon, in the middle of its own day`() {
        val at =
            visitWeighingAt(
                visitedOn = LocalDate.of(2026, 5, 20),
                now = Instant.parse("2026-05-20T14:35:00Z"),
                zone = warsaw,
            )

        assertEquals(Instant.parse("2026-05-20T10:00:00Z"), at)
    }

    @Test
    fun `the clamp is truncated to the minute, so an exact-instant collision is detectable`() {
        // ADR-0021's collision rule is an exact match against a form that stores whole minutes. A
        // clamp carrying seconds would make a visit weighing incapable of colliding with a typed
        // one, and the rule that protects it would then have nothing to protect.
        val at =
            visitWeighingAt(
                visitedOn = LocalDate.of(2026, 5, 20),
                now = Instant.parse("2026-05-20T07:00:45Z"),
                zone = warsaw,
            )

        assertEquals(Instant.parse("2026-05-20T07:00:00Z"), at)
    }

    @Test
    fun `noon survives the spring-forward day, where midnight is the hour that does not exist`() {
        // 2026-03-29 in Warsaw: the clocks go 02:00 → 03:00. `atStartOfDay` on such a date resolves
        // to the next valid instant, which is why the constant is noon and not midnight.
        val at =
            visitWeighingAt(
                visitedOn = LocalDate.of(2026, 3, 29),
                now = Instant.parse("2026-04-05T00:00:00Z"),
                zone = warsaw,
            )

        assertEquals(LocalTime.NOON, at.atZone(warsaw).toLocalTime())
        assertEquals(Instant.parse("2026-03-29T10:00:00Z"), at)
    }

    @Test
    fun `noon is resolved in the given zone, not in UTC`() {
        val visitedOn = LocalDate.of(2026, 5, 20)
        val now = Instant.parse("2026-06-01T07:00:00Z")

        assertEquals(Instant.parse("2026-05-20T10:00:00Z"), visitWeighingAt(visitedOn, now, warsaw))
        assertEquals(Instant.parse("2026-05-20T11:00:00Z"), visitWeighingAt(visitedOn, now, london))
    }

    // ---- What the entry form may overwrite (ADR-0021's amendment) ---------------------------------

    @Test
    fun `a visit-tagged weighing is not offered for replacement`() {
        val visitTagged = weighing(visitId = "visit-1")

        assertEquals(emptyList<WeightEntity>(), listOf(visitTagged).replaceable())
    }

    @Test
    fun `a typed weighing still is`() {
        val manual = weighing(visitId = null)

        assertEquals(listOf(manual), listOf(manual).replaceable())
    }

    @Test
    fun `a clash holding both keeps only the typed one`() {
        val manual = weighing(visitId = null)
        val visitTagged = weighing(visitId = "visit-1")

        assertEquals(listOf(manual), listOf(manual, visitTagged).replaceable())
    }

    private fun weighing(visitId: String?) =
        WeightEntity(
            bunnyId = "bunny-1",
            grams = 2380,
            recordedAt = Instant.parse("2026-05-20T10:00:00Z"),
            visitId = visitId,
        )
}
