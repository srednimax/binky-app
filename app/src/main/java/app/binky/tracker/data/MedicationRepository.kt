package app.binky.tracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Medication courses, their schedules, and the doses recorded against them.
 *
 * The arithmetic is not here — it is in `DoseSchedule.kt`, pure and JVM-tested. What this class owns
 * is the plumbing that feeds it: which flows to combine, which day is "today" in which zone, and the
 * fact that **nothing writes a due dose** (ADR-0002). Shortening a course, moving its times or
 * deleting one all move the schedule with no bookkeeping at all, because there was never a future row
 * to rewrite.
 *
 * **5f hangs the alarm rebuild off the write paths here**, rather than off their call sites: archiving
 * a bunny and deleting one both change what is due without touching a medication table, so a rebuild
 * remembered per call site is a rebuild that misses them (ADR-0025).
 */
class MedicationRepository(
    private val database: BunnyDatabase,
) {
    private val dao = database.medicationDao()

    /**
     * One bunny's courses with their times — **active first, ended after**.
     *
     * [today] decides which is which and is a parameter for the same reason the zone is one
     * everywhere else: a course that ended is one that ended in the owner's day, not in UTC's.
     */
    fun courses(
        bunnyId: String,
        today: LocalDate = LocalDate.now(),
    ): Flow<List<CourseWithTimes>> = dao.coursesForBunny(bunnyId, today)

    /** One course, watched — null once the row is gone, which is how its editor learns to close. */
    fun course(id: String): Flow<CourseWithTimes?> = dao.course(id)

    suspend fun courseNow(id: String): MedicationCourseEntity? = dao.courseNow(id)

    /** One course's recorded doses, newest first — given, skipped and ad hoc alike. */
    fun doses(courseId: String): Flow<List<DoseEntity>> = dao.doses(courseId)

    /**
     * The derived slots for this bunny over [days] days from today, each with the row that answers it.
     *
     * The window opens **today and never earlier** — that is [DoseWindow]'s constructor, not a check
     * here — so a past day is never derived. What a past day holds is `doses(courseId)`: what was
     * recorded, and never a gap (ADR-0002, ADR-0001).
     *
     * Kotlin note: `combine` re-runs its block whenever *either* flow emits, so answering a dose moves
     * the list with nothing telling it to, and so does editing the course's times in another screen.
     *
     * [zone] and [today] are read once per call, which is what a screen wants: its `ViewModel` builds
     * this flow when the screen opens, so a phone that changed zone since is right by the time anyone
     * looks.
     */
    fun schedule(
        bunnyId: String,
        days: Long = 1,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Flow<List<ScheduledDose>> {
        val window = DoseWindow.of(today, days)
        return combine(
            dao.coursesForBunny(bunnyId, today),
            dao.answersForBunny(bunnyId, window.from, window.through),
        ) { courses, answers -> resolve(courses, answers, window, zone) }
    }

    /** The same read, once — for a write path or 5f's alarm rebuild, which collect nothing. */
    suspend fun scheduleNow(
        bunnyId: String,
        days: Long = 1,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<ScheduledDose> {
        val window = DoseWindow.of(today, days)
        return resolve(
            courses = dao.coursesForBunnyNow(bunnyId),
            answers = dao.answersForBunnyNow(bunnyId, window.from, window.through),
            window = window,
            zone = zone,
        )
    }

    /** Adds a course and its schedule in one transaction — a course half-written has no schedule. */
    suspend fun add(
        course: MedicationCourseEntity,
        times: List<LocalTime> = emptyList(),
    ): String {
        val row = course.validated()
        database.withTransaction {
            dao.insertCourse(row)
            dao.insertTimes(timeRows(row.id, times.distinct().sorted()))
        }
        return row.id
    }

    suspend fun update(course: MedicationCourseEntity) {
        dao.updateCourse(course.validated())
    }

    /**
     * Replaces a course's schedule with [times] — **the rows the editor is holding**, ids and all.
     *
     * Delete-then-insert rather than a per-row diff, and the ids being carried through is what keeps
     * that from being the "delete and insert" [MedicationTimeEntity] warns about: a chip moved from
     * 08:00 to 09:00 arrives as the same row with a new time and leaves as the same row, inside one
     * transaction that can no more be half-applied than an update could. A diff would additionally
     * have to survive two chips *swapping* times, which trips the unique index halfway through — an
     * empty table for the length of the transaction cannot.
     */
    suspend fun setTimes(
        courseId: String,
        times: List<MedicationTimeEntity>,
    ) {
        val rows =
            times
                .map { it.copy(courseId = courseId) }
                .distinctBy { it.time }
                .sortedBy { it.time }
        database.withTransaction {
            dao.deleteTimesFor(courseId)
            dao.insertTimes(rows)
        }
    }

    /**
     * Closes an open course, which is setting its end to today (ADR-0002) — no second "active" flag
     * that could disagree with it.
     *
     * Every dose already recorded stays; only the slots after [on] stop being derived. That is the
     * difference 5e's delete dialog offers as "end course instead".
     */
    suspend fun endCourse(
        id: String,
        on: LocalDate = LocalDate.now(),
    ) {
        val course = dao.courseNow(id) ?: return
        require(!on.isBefore(course.startOn)) { "A course cannot end before it starts" }
        dao.setEndOn(id, on)
    }

    /** The course, its times and **every dose recorded against it**, by cascade. 5e counts them first. */
    suspend fun delete(id: String) {
        dao.deleteCourseById(id)
    }

    /**
     * Answers a derived slot: given or skipped, once.
     *
     * A second answer **corrects the first** rather than throwing. The unique index guarantees at most
     * one row per slot and this is the code that stays on the right side of it — an owner who taps
     * *Given* on the screen having already tapped *Skip* in the shade (5f) has changed their mind, not
     * hit a data error, and a constraint exception at that tap would be the app arguing with them.
     */
    suspend fun answer(
        slot: DueDose,
        status: DoseStatus,
        note: String? = null,
        recordedAt: Instant = Instant.now(),
    ): String {
        val trimmed = note?.trim()?.ifEmpty { null }
        return database.withTransaction {
            val existing = dao.answerNow(slot.courseId, slot.scheduledOn, slot.scheduledTime)
            if (existing == null) {
                val row =
                    DoseEntity(
                        courseId = slot.courseId,
                        scheduledOn = slot.scheduledOn,
                        scheduledTime = slot.scheduledTime,
                        recordedAt = recordedAt,
                        status = status,
                        note = trimmed,
                    )
                dao.insertDose(row)
                row.id
            } else {
                dao.updateDose(existing.copy(status = status, note = trimmed, recordedAt = recordedAt))
                existing.id
            }
        }
    }

    /**
     * Records a dose that answers no slot — **normal, not an error** (ADR-0002).
     *
     * A rescue dose at 03:00, or last night's 20:00 given at 00:30 once the slot had stopped existing.
     * Both halves of the key stay null, which is also what leaves the row out of the unique index, so
     * there can be any number of them.
     *
     * Back-dating is the point and is allowed; the future is refused on the terms every other entry in
     * the app uses — a moment the owner has not reached cannot be something they did.
     */
    suspend fun recordAdHoc(
        courseId: String,
        status: DoseStatus,
        note: String? = null,
        recordedAt: Instant = Instant.now(),
        now: Instant = Instant.now(),
    ): String {
        require(!recordedAt.isAfter(now)) { "A dose cannot have been given in the future" }
        val row =
            DoseEntity(
                courseId = courseId,
                recordedAt = recordedAt,
                status = status,
                note = note?.trim()?.ifEmpty { null },
            )
        dao.insertDose(row)
        return row.id
    }

    suspend fun doseNow(id: String): DoseEntity? = dao.doseNow(id)

    /** Corrects a recorded dose — an hour later the owner notices it went against the wrong slot. */
    suspend fun updateDose(
        dose: DoseEntity,
        now: Instant = Instant.now(),
    ) {
        require(!dose.recordedAt.isAfter(now)) { "A dose cannot have been given in the future" }
        dao.updateDose(dose.copy(note = dose.note?.trim()?.ifEmpty { null }))
    }

    /** Deletes one recorded dose. The slot it answered goes back to unanswered, never to "missed". */
    suspend fun deleteDose(id: String) {
        dao.deleteDoseById(id)
    }

    private fun resolve(
        courses: List<CourseWithTimes>,
        answers: List<DoseEntity>,
        window: DoseWindow,
        zone: ZoneId,
    ): List<ScheduledDose> =
        courses
            .flatMap { withTimes ->
                scheduledDoses(
                    course = withTimes.course,
                    slots = dueDoses(withTimes.course, withTimes.times, window, zone),
                    recorded = answers,
                )
            }.sortedWith(
                compareBy({ it.due.at }, { it.course.name.lowercase() }, { it.course.id }),
            )

    private fun timeRows(
        courseId: String,
        times: List<LocalTime>,
    ): List<MedicationTimeEntity> = times.map { MedicationTimeEntity(courseId = courseId, time = it) }
}

/**
 * What a course has to satisfy whichever screen wrote it — the same shape as the visit's and the care
 * reminder's `validated()`, and for the same reason: the place to catch it is before the database,
 * not in the composable that shrugs at it.
 *
 * **The amount is trimmed and never required**, unlike the name. It is free text the app never reads
 * (ADR-0002), and an owner who was told "one syringe, morning and night" has nothing to type in it —
 * insisting would make them invent a number the app would then display as if the vet had said it.
 */
private fun MedicationCourseEntity.validated(): MedicationCourseEntity {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty()) { "A medication course needs a name" }
    require(endOn == null || !endOn.isBefore(startOn)) { "A course cannot end before it starts" }
    return copy(
        name = trimmedName,
        doseAmount = doseAmount.trim(),
        notes = notes?.trim()?.ifEmpty { null },
    )
}
