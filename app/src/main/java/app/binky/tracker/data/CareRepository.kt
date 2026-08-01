package app.binky.tracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads and writes care reminders, and **resolves the two-source last completion** a weigh-in needs
 * (ADR-0018's amendment).
 *
 * The derivation itself is not here — it is in `CareSchedule.kt`, pure and JVM-tested. What this
 * class owns is the plumbing that feeds it: which flows to combine, and the fact that a `WEIGH_IN`
 * reminder's completion history has a second source. That split is what keeps the awkward cases
 * (DST, a late completion, a clamped month) provable without a device.
 *
 * **Nothing here writes a due date, and no path clears [CareReminderEntity.notifiedForDueOn].** Both
 * follow from the same decision (ADR-0002): the due date is derived, and the notification watermark
 * is compared against derived truth, so completing, editing an event, deleting one, changing the
 * interval or back-dating a weight all move the schedule with no bookkeeping at all.
 */
class CareRepository(
    private val database: BunnyDatabase,
) {
    private val careDao = database.careDao()

    /**
     * This bunny's reminders with their schedules worked out, **soonest due first**.
     *
     * Sorted here rather than in SQL because the sort key does not exist in the database — ordering
     * by a derived date is exactly what a stored `dueOn` column would have bought, and it is not
     * worth the column.
     *
     * Kotlin note: `combine` re-runs its block whenever *any* of the three flows emits, so a weight
     * logged in another tab moves a weigh-in's next date with nothing else being told about it.
     *
     * [zone] is read once per call, which is what a screen wants: its `ViewModel` builds this flow
     * when the screen opens, so a phone that changed timezone since is right by the time anyone looks.
     */
    fun schedule(
        bunnyId: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<ScheduledCare>> =
        combine(
            careDao.reminders(bunnyId),
            careDao.latestCompletions(bunnyId),
            careDao.latestWeighing(bunnyId),
        ) { reminders, completions, latestWeighing ->
            resolve(reminders, completions, latestWeighing, zone)
        }

    /** The same read, once, for the sweep — which has no screen and collects nothing. */
    suspend fun scheduleNow(
        bunnyId: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduledCare> =
        resolve(
            reminders = careDao.remindersNow(bunnyId),
            completions = careDao.latestCompletionsNow(bunnyId),
            latestWeighing = careDao.latestWeighingNow(bunnyId),
            zone = zone,
        )

    /** One reminder's completions, newest first. Editable and deletable — both move the schedule. */
    fun events(reminderId: String): Flow<List<CareEventEntity>> = careDao.events(reminderId)

    /**
     * One reminder, watched — the `Flow`/`Now` pair every repository here draws (`bunny` /
     * `bunnyNow`). Null once the row is gone, which is how its own screen learns to close.
     */
    fun reminder(id: String): Flow<CareReminderEntity?> = careDao.reminder(id)

    suspend fun reminderNow(id: String): CareReminderEntity? = careDao.reminderNow(id)

    suspend fun add(reminder: CareReminderEntity): String {
        careDao.insertReminder(reminder.validated())
        return reminder.id
    }

    suspend fun update(reminder: CareReminderEntity) {
        careDao.updateReminder(reminder.validated())
    }

    /** One confirmation's worth of destruction: the reminder and, by cascade, its completions. */
    suspend fun delete(id: String) {
        careDao.deleteReminderById(id)
    }

    /**
     * Records a completion, which is what schedules the next occurrence.
     *
     * **Future completions are rejected**, on the same terms as Phase 2 entry: a date the owner has
     * not reached cannot be something they did, and accepting one would push the next occurrence out
     * by the mistake plus the interval. Back-dating is normal and allowed.
     */
    suspend fun complete(
        reminderId: String,
        completedOn: LocalDate,
        note: String? = null,
        today: LocalDate = LocalDate.now(),
    ): String {
        require(!completedOn.isAfter(today)) { "A care task cannot be completed in the future" }
        val event = CareEventEntity(reminderId = reminderId, completedOn = completedOn, note = note?.ifBlank { null })
        careDao.insertEvent(event)
        return event.id
    }

    /** One completion as it stands, for the edit path that has to copy the fields it is not changing. */
    suspend fun eventNow(id: String): CareEventEntity? = careDao.eventNow(id)

    suspend fun updateEvent(
        event: CareEventEntity,
        today: LocalDate = LocalDate.now(),
    ) {
        require(!event.completedOn.isAfter(today)) { "A care task cannot be completed in the future" }
        careDao.updateEvent(event.copy(note = event.note?.ifBlank { null }))
    }

    suspend fun deleteEvent(id: String) {
        careDao.deleteEventById(id)
    }

    /** Called by the sweep after posting, with the due date it posted **for**. */
    suspend fun markNotified(
        reminderId: String,
        dueOn: LocalDate,
    ) {
        careDao.setNotifiedForDueOn(reminderId, dueOn)
    }

    /** Records that the calendar hand-off happened, so the button stops offering it (ADR-0014). */
    suspend fun markCalendarHandedOff(
        reminderId: String,
        handedOffAt: Instant? = Instant.now(),
    ) {
        careDao.setCalendarHandedOffAt(reminderId, handedOffAt)
    }

    private fun resolve(
        reminders: List<CareReminderEntity>,
        completions: List<LatestCompletion>,
        latestWeighing: Instant?,
        zone: ZoneId,
    ): List<ScheduledCare> {
        val completedOn = completions.associate { it.reminderId to it.completedOn }
        val latestWeightOn = latestWeighing?.atZone(zone)?.toLocalDate()
        return reminders
            .map { reminder ->
                scheduleFor(
                    reminder = reminder,
                    lastCompletedOn = lastCompletedOn(reminder.type, completedOn[reminder.id], latestWeightOn),
                )
            }.sortedWith(compareBy({ it.dueOn }, { it.reminder.createdAt }, { it.reminder.id }))
    }
}

/**
 * The invariants a row has to satisfy whichever screen wrote it.
 *
 * A reminder with neither a label nor a type would have nothing to render — that is the one
 * combination ADR-0018's `{label, interval, optional type}` does not allow, and the place to catch it
 * is before it reaches the database rather than in the composable that shrugs at it.
 */
private fun CareReminderEntity.validated(): CareReminderEntity {
    val trimmed = label?.trim()?.ifEmpty { null }
    require(trimmed != null || type != null) { "A care reminder needs a label or a known type" }
    require(intervalCount > 0) { "A care interval repeats a positive number of times" }
    return copy(label = trimmed)
}
