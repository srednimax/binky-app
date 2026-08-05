package app.binky.tracker.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * A course and the clock times it is taken at — the pair every read of a course actually needs,
 * because a course with no times is a different feature from one with two.
 *
 * Kotlin/Room note: `@Relation` makes Room run the child query and stitch the results together, so
 * this is one flow rather than two combined by hand. It needs `@Transaction` on the query using it —
 * without it the parent and child reads can straddle a write and return a course beside somebody
 * else's times.
 */
data class CourseWithTimes(
    @Embedded val course: MedicationCourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val times: List<MedicationTimeEntity>,
) {
    /** Sorted, because the relation's row order is the database's business and not a schedule. */
    val clockTimes: List<LocalTime> get() = times.map { it.time }.sorted()
}

/**
 * Medication courses, their schedules, and the doses actually recorded against them.
 *
 * **Nothing here returns a due dose**, because none is stored (ADR-0002). What the DAO supplies is
 * the two facts a due dose is derived from — the course and its times — plus the rows that answer the
 * slots, and the derivation lives in `DoseSchedule.kt` where it is provable without a device.
 *
 * Kotlin note: `Flow` reads are cold streams Room re-emits on every write to the tables they touch,
 * so a screen collects one and never asks for a refresh (house rule). The `…Now` suffix marks the
 * `suspend` one-shot variants, used inside write paths and by the alarm rebuild, which have no screen
 * and collect nothing.
 */
@Dao
interface MedicationDao {
    /**
     * One bunny's courses with their times, **active first and ended after**.
     *
     * The grouping is done in SQL because "has this ended" is a comparison against a column, unlike
     * care's due date which is derived and has to be sorted in Kotlin. [today] is a parameter rather
     * than `date('now')` for the reason every date in this app is: SQLite's `now` is UTC, and the day
     * a course ended is the owner's day.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM medication_courses
        WHERE bunnyId = :bunnyId
        ORDER BY
            CASE WHEN endOn IS NULL OR endOn >= :today THEN 0 ELSE 1 END,
            name COLLATE NOCASE,
            createdAt,
            id
        """,
    )
    fun coursesForBunny(
        bunnyId: String,
        today: LocalDate,
    ): Flow<List<CourseWithTimes>>

    @Transaction
    @Query("SELECT * FROM medication_courses WHERE bunnyId = :bunnyId")
    suspend fun coursesForBunnyNow(bunnyId: String): List<CourseWithTimes>

    /**
     * One course with its times, watched. Nullable because the row can be **deleted while its own
     * screen is open** — the emission is what tells that screen to leave.
     */
    @Transaction
    @Query("SELECT * FROM medication_courses WHERE id = :id")
    fun course(id: String): Flow<CourseWithTimes?>

    @Query("SELECT * FROM medication_courses WHERE id = :id")
    suspend fun courseNow(id: String): MedicationCourseEntity?

    @Query("SELECT * FROM medication_times WHERE courseId = :courseId ORDER BY time")
    suspend fun timesNow(courseId: String): List<MedicationTimeEntity>

    /** One course's recorded doses, newest first — the history 5e lets the owner correct. */
    @Query("SELECT * FROM doses WHERE courseId = :courseId ORDER BY recordedAt DESC, id")
    fun doses(courseId: String): Flow<List<DoseEntity>>

    /**
     * The rows that answer this bunny's slots between two days, inclusive.
     *
     * `scheduledOn` non-null is what excludes ad-hoc doses, and it excludes them by definition rather
     * than by a filter someone chose: an ad-hoc dose answers no slot (ADR-0002). Both bounds are
     * `LocalDate`s stored as epoch days, so `BETWEEN` is an integer comparison.
     */
    @Query(
        """
        SELECT d.* FROM doses d
        JOIN medication_courses c ON c.id = d.courseId
        WHERE c.bunnyId = :bunnyId AND d.scheduledOn BETWEEN :from AND :through
        """,
    )
    fun answersForBunny(
        bunnyId: String,
        from: LocalDate,
        through: LocalDate,
    ): Flow<List<DoseEntity>>

    @Query(
        """
        SELECT d.* FROM doses d
        JOIN medication_courses c ON c.id = d.courseId
        WHERE c.bunnyId = :bunnyId AND d.scheduledOn BETWEEN :from AND :through
        """,
    )
    suspend fun answersForBunnyNow(
        bunnyId: String,
        from: LocalDate,
        through: LocalDate,
    ): List<DoseEntity>

    /** The one row that can answer this slot, if it has been answered — the unique index's other end. */
    @Query(
        "SELECT * FROM doses WHERE courseId = :courseId AND scheduledOn = :on AND scheduledTime = :time",
    )
    suspend fun answerNow(
        courseId: String,
        on: LocalDate,
        time: LocalTime,
    ): DoseEntity?

    @Query("SELECT * FROM doses WHERE id = :id")
    suspend fun doseNow(id: String): DoseEntity?

    /**
     * How many recorded doses the `CASCADE` below would take with the course.
     *
     * Read for one reason only: 5e's delete dialog **names the number before it destroys it**. After
     * weights, a course's doses are the most clinically meaningful history the app holds, and "this
     * also deletes 40 recorded doses" is the difference between a confirmation and a formality.
     */
    @Query("SELECT COUNT(*) FROM doses WHERE courseId = :courseId")
    suspend fun doseCount(courseId: String): Int

    @Insert
    suspend fun insertCourse(course: MedicationCourseEntity)

    @Update
    suspend fun updateCourse(course: MedicationCourseEntity)

    /** Takes the times and the doses with it, both by `CASCADE` — 5e counts them before asking. */
    @Query("DELETE FROM medication_courses WHERE id = :id")
    suspend fun deleteCourseById(id: String)

    /** Ending a course is one column, and it is the whole of "closing" it (ADR-0002). */
    @Query("UPDATE medication_courses SET endOn = :on WHERE id = :id")
    suspend fun setEndOn(
        id: String,
        on: LocalDate?,
    )

    @Insert
    suspend fun insertTimes(times: List<MedicationTimeEntity>)

    @Query("DELETE FROM medication_times WHERE courseId = :courseId")
    suspend fun deleteTimesFor(courseId: String)

    @Insert
    suspend fun insertDose(dose: DoseEntity)

    @Update
    suspend fun updateDose(dose: DoseEntity)

    @Query("DELETE FROM doses WHERE id = :id")
    suspend fun deleteDoseById(id: String)
}
