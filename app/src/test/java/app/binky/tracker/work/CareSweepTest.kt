package app.binky.tracker.work

import app.binky.tracker.data.CareIntervalUnit
import app.binky.tracker.data.CareReminderEntity
import app.binky.tracker.data.CareType
import app.binky.tracker.data.ScheduledCare
import app.binky.tracker.data.scheduleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the daily sweep decides, as a case table (ADR-0024).
 *
 * The two rules asserted here are the ones whose failure nobody would notice for a week: a
 * notification about an archived bunny's nail trim, and a reminder that starts nagging every morning
 * because "notified once" stopped being true.
 */
class CareSweepTest {
    private val today = LocalDate.of(2026, 3, 14)

    @Test
    fun `an archived bunny is never notified about`() {
        // ADR-0001's memorial-page rule, arriving at the sweep. The reminder is due, the reminder is
        // unnotified, and the bunny has died or been rehomed — so nothing is posted.
        val bunnies =
            listOf(
                sweepBunny("bijou", archived = false, dueOn = today),
                sweepBunny("nugget", archived = true, dueOn = today),
            )

        val due = careDueForNotifying(bunnies, today)

        assertEquals(listOf("bijou"), due.map { it.bunnyId })
    }

    @Test
    fun `a due reminder notifies once and never again`() {
        val reminder = reminder(dueOn = today).copy(notifiedForDueOn = today)
        val bunnies = listOf(SweepBunny("bijou", "Bijou", archived = false, schedule = listOf(scheduled(reminder))))

        assertTrue(careDueForNotifying(bunnies, today).isEmpty())
    }

    @Test
    fun `an overdue reminder still posts if it never has`() {
        // The anchor is in the past and nothing has been notified: the first sweep after the app
        // learns about it is the one that speaks, and every sweep afterwards is silent.
        val bunnies = listOf(sweepBunny("bijou", archived = false, dueOn = today.minusWeeks(3)))

        val due = careDueForNotifying(bunnies, today)

        assertEquals(1, due.size)
        assertTrue(due.single().scheduled.isOverdueOn(today))
    }

    @Test
    fun `a watermark left on a date the schedule has moved off goes stale on its own`() {
        // The whole point of storing the due date rather than the moment (ADR-0002): a completion
        // moved the derived date, so the old watermark no longer matches and the next occurrence is
        // notifiable with nothing having been cleared anywhere.
        val reminder = reminder(dueOn = today).copy(notifiedForDueOn = today.minusWeeks(6))
        val bunnies = listOf(SweepBunny("bijou", "Bijou", archived = false, schedule = listOf(scheduled(reminder))))

        assertEquals(1, careDueForNotifying(bunnies, today).size)
    }

    @Test
    fun `nothing due is nothing posted`() {
        val bunnies = listOf(sweepBunny("bijou", archived = false, dueOn = today.plusDays(1)))

        assertTrue(careDueForNotifying(bunnies, today).isEmpty())
    }

    @Test
    fun `every due reminder across every bunny comes back, carrying whose it is`() {
        // Three reminders across two bunnies is the case the group summary exists for, and the
        // notification's own text names the bunny — so the id has to travel with the reminder.
        val bunnies =
            listOf(
                SweepBunny(
                    "bijou",
                    "Bijou",
                    archived = false,
                    schedule = listOf(scheduled(reminder(today)), scheduled(reminder(today.minusDays(2)))),
                ),
                sweepBunny("nugget", archived = false, dueOn = today),
            )

        val due = careDueForNotifying(bunnies, today)

        assertEquals(3, due.size)
        assertEquals(listOf("Bijou", "Bijou", "Nugget"), due.map { it.bunnyName })
    }

    @Test
    fun `a notification id is stable for a reminder and clear of the reserved block`() {
        // Stability is what makes a sweep that runs twice replace its own notification rather than
        // stack a second copy, and what lets a completion cancel exactly the right one.
        assertEquals(careNotificationId("reminder-1"), careNotificationId("reminder-1"))
        assertNotEquals(careNotificationId("reminder-1"), careNotificationId("reminder-2"))
    }

    @Test
    fun `no reminder id can collide with the debug reminder or the group summary`() {
        // Both live in the reserved low block. A collision would have a completion cancel the
        // summary, or the debug action wipe a real reminder.
        val ids = (1..2000).map { careNotificationId("reminder-$it") }
        assertTrue(ids.all { it >= RESERVED_NOTIFICATION_IDS })
        assertTrue(CARE_SUMMARY_NOTIFICATION_ID < RESERVED_NOTIFICATION_IDS)
    }

    private fun sweepBunny(
        id: String,
        archived: Boolean,
        dueOn: LocalDate,
    ) = SweepBunny(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        archived = archived,
        schedule = listOf(scheduled(reminder(dueOn))),
    )

    /** A reminder never completed, so its derived due date is the anchor unmodified. */
    private fun reminder(dueOn: LocalDate) =
        CareReminderEntity(
            bunnyId = "bunny",
            type = CareType.NAIL_TRIM,
            intervalCount = 6,
            intervalUnit = CareIntervalUnit.WEEK,
            firstDueOn = dueOn,
        )

    private fun scheduled(reminder: CareReminderEntity): ScheduledCare =
        scheduleFor(reminder = reminder, lastCompletedOn = null)
}
