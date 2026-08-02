package app.binky.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The export reminder as a **table of cases**, in `CareScheduleTest`'s shape and for its reason.
 *
 * Every clause PLAN 4e and ADR-0005 commit to has a case here that fails if it is quietly changed —
 * above all the two that are one line of code and one paragraph of reasoning each: switching the
 * reminder on does not produce a notification tomorrow morning, and a due prompt notifies once.
 *
 * Nothing in here is Android: the whole point of deriving this in `ExportSchedule.kt` rather than
 * inside the sweep is that a month of waiting becomes a `LocalDate`.
 */
class ExportScheduleTest {
    private val enabled = LocalDate.of(2026, 8, 2)

    private fun monthly(
        enabledOn: LocalDate? = enabled,
        lastExportedOn: LocalDate? = null,
        notifiedForDueOn: LocalDate? = null,
    ) = ExportReminder(
        every = ExportInterval.MONTHLY,
        enabledOn = enabledOn,
        lastExportedOn = lastExportedOn,
        notifiedForDueOn = notifiedForDueOn,
    )

    // ---- Off is off ------------------------------------------------------------------------------

    @Test
    fun `a reminder that has never been switched on is due never and notifies never`() {
        val off = ExportReminder()
        assertNull(off.dueOn())
        assertFalse(off.needsNotifying(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `switching it off silences it even with an export date and a watermark behind it`() {
        // Turning it off keeps the other keys, deliberately — so this is the state a real phone
        // holds the moment after the switch goes off, not a contrived one.
        val off =
            ExportReminder(
                every = null,
                enabledOn = enabled,
                lastExportedOn = LocalDate.of(2026, 1, 1),
                notifiedForDueOn = LocalDate.of(2026, 2, 1),
            )
        assertNull(off.dueOn())
        assertFalse(off.needsNotifying(LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `an interval with no anchor behind it reads as off rather than as long ago`() {
        // The two keys are written together, so this pairing cannot arise from the app. If a
        // hand-edited preferences file or a truncated restore produces it, silence is the safe
        // direction: the alternative is prompting on a schedule nobody chose.
        val orphaned = monthly(enabledOn = null)
        assertNull(orphaned.dueOn())
        assertFalse(orphaned.needsNotifying(LocalDate.of(2030, 1, 1)))
    }

    // ---- The anchor: switching it on does not fire tomorrow ---------------------------------------

    @Test
    fun `switching a monthly reminder on makes the first prompt due one month later`() {
        assertEquals(LocalDate.of(2026, 9, 2), monthly().dueOn())
    }

    @Test
    fun `the morning after switching it on, nothing is due`() {
        // The failure this rules out: opting in and being nagged the next day, which is how an
        // owner learns to switch a reminder back off.
        assertFalse(monthly().needsNotifying(enabled.plusDays(1)))
    }

    @Test
    fun `each interval anchors one of its own units after the switch`() {
        val cases =
            mapOf(
                ExportInterval.WEEKLY to LocalDate.of(2026, 8, 9),
                ExportInterval.FORTNIGHTLY to LocalDate.of(2026, 8, 16),
                ExportInterval.MONTHLY to LocalDate.of(2026, 9, 2),
                ExportInterval.QUARTERLY to LocalDate.of(2026, 11, 2),
            )
        cases.forEach { (interval, expected) ->
            assertEquals(
                "switched on $enabled, every $interval",
                expected,
                ExportReminder(every = interval, enabledOn = enabled).dueOn(),
            )
        }
    }

    // ---- An export is this reminder's completion -------------------------------------------------

    @Test
    fun `an export since the switch was turned on moves the next prompt out by one interval`() {
        val exported = LocalDate.of(2026, 8, 20)
        assertEquals(LocalDate.of(2026, 9, 20), monthly(lastExportedOn = exported).dueOn())
    }

    @Test
    fun `an export from before the switch was turned on does not pull the prompt forward`() {
        // Someone who last exported six months ago and switches a monthly reminder on today is
        // opting into a habit, not confessing to being overdue. Anchoring on the old export would
        // fire the very next morning.
        val ancient = LocalDate.of(2026, 2, 1)
        assertEquals(LocalDate.of(2026, 9, 2), monthly(lastExportedOn = ancient).dueOn())
        assertFalse(monthly(lastExportedOn = ancient).needsNotifying(enabled.plusDays(1)))
    }

    @Test
    fun `an export on the same day the switch was turned on counts as the anchor, not as a completion`() {
        // `isAfter`, not `!isBefore`: the two land on the same date here, and this pins which
        // comparison is meant so a later edit cannot swap it unnoticed.
        assertEquals(LocalDate.of(2026, 9, 2), monthly(lastExportedOn = enabled).dueOn())
    }

    @Test
    fun `the day-of-month is allowed to walk, exactly as a care interval's does`() {
        // java.time clamps 31 January + 1 month to 28 February, and the next prompt is scheduled
        // from the export that answered the last one — so a monthly reminder anchored late in the
        // month settles onto the 28th and stays there. The same trade ADR-0002 takes for care.
        val january = LocalDate.of(2026, 1, 31)
        assertEquals(
            LocalDate.of(2026, 2, 28),
            ExportReminder(every = ExportInterval.MONTHLY, enabledOn = january).dueOn(),
        )
    }

    // ---- Notifies once (ADR-0024) ----------------------------------------------------------------

    @Test
    fun `a prompt due today is posted today`() {
        assertTrue(monthly().needsNotifying(LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `a prompt already posted for this due date is not posted again`() {
        val due = LocalDate.of(2026, 9, 2)
        val reminder = monthly(notifiedForDueOn = due)
        assertFalse(reminder.needsNotifying(due))
        // The sweep can run more than once a day — a retry, a reboot, a Doze window closing — and
        // it must be the recorded watermark that stops the second post, not luck.
        assertFalse(reminder.needsNotifying(due.plusDays(1)))
        assertFalse(reminder.needsNotifying(due.plusWeeks(3)))
    }

    @Test
    fun `a watermark from an older due date does not suppress the next one`() {
        val reminder = monthly(notifiedForDueOn = LocalDate.of(2026, 8, 2))
        assertTrue(reminder.needsNotifying(LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `a prompt ignored for three weeks is still the same prompt`() {
        // Overdue state is carried by the Backup screen, silently. Re-posting every morning until
        // the owner exports is the wallpaper failure ADR-0001 rejects, and this prompt has a lower
        // claim on their attention than an overdue vaccination, not a higher one.
        val due = LocalDate.of(2026, 9, 2)
        assertEquals(due, monthly().dueOn())
        assertTrue(monthly().needsNotifying(due.plusWeeks(3)))
        assertFalse(monthly(notifiedForDueOn = due).needsNotifying(due.plusWeeks(3)))
    }

    @Test
    fun `exporting after a prompt was posted moves the reminder on without clearing anything`() {
        // The watermark is never cleared on any path — it is compared against a *derived* due date,
        // which is ADR-0024's whole argument for storing what was notified about rather than when.
        val due = LocalDate.of(2026, 9, 2)
        val afterExport = monthly(lastExportedOn = due, notifiedForDueOn = due)
        assertEquals(LocalDate.of(2026, 10, 2), afterExport.dueOn())
        assertFalse(afterExport.needsNotifying(due))
        assertTrue(afterExport.needsNotifying(LocalDate.of(2026, 10, 2)))
    }
}
