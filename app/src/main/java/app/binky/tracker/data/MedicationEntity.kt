package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * What was actually done about a scheduled dose. Two values and no third: **there is no `MISSED`**
 * (ADR-0026, ADR-0001). A dose nobody recorded is a dose nobody recorded — the app does not infer
 * from silence, and a status meaning "the app decided you failed" would be exactly that inference
 * given a name.
 *
 * Stored by name, never ordinal (house rule).
 */
enum class DoseStatus { GIVEN, SKIPPED }

/**
 * One course of medication for one bunny: what, how much, from when, until when, and at what times.
 *
 * **[doseAmount] is free text and the app never reasons over it** (ADR-0002). "0.3 ml", "half a
 * tablet", "1 ml twice if she is still off her food" — parsing that into a number and a unit would be
 * a guess about a prescription, and a wrong guess about a prescription is the one class of bug this
 * app must not have. It is stored, displayed and never summed, converted or compared.
 *
 * **[endOn] null means ongoing**, which is the normal state of a course an owner is in the middle of.
 * Closing a course is setting it to today — no separate "active" flag, because two facts that can
 * disagree is the pattern this project keeps refusing (4b's day-of-month, ADR-0017's `source`).
 *
 * [remindersEnabled] is the owner's switch, and it is meaningless without times: a course with no
 * [MedicationTimeEntity] rows has no slots to remind about, so 5e hides the switch rather than
 * offering one that does nothing (ADR-0003).
 */
@Entity(
    tableName = "medication_courses",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bunnyId")],
)
data class MedicationCourseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    val name: String,
    /** Free text, exactly as the vet wrote it. Never parsed. */
    val doseAmount: String,
    val startOn: LocalDate,
    /** `null` = ongoing. Ending a course is setting this to today. */
    val endOn: LocalDate? = null,
    val notes: String? = null,
    val remindersEnabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
)

/**
 * One clock time in a course's daily schedule — 08:00 and 20:00 are two rows, not one string.
 *
 * **A child table rather than a converted list on the course**, for two reasons. The scheduler's
 * question is *"what is the next dose time across every active course"* (ADR-0025), which is a query
 * over times and not a scan that deserialises every course to look inside it. And the unique index on
 * `(courseId, time)` makes "08:00 twice" impossible rather than merely unlikely — a list column would
 * leave that to whoever writes the editor.
 *
 * **[time] is a [LocalTime], and the slot it produces is keyed locally too** (ADR-0002, ADR-0003).
 * 08:00 means eight in the morning wherever the owner is standing; an instant-keyed schedule stops
 * matching its own slots the moment they travel.
 *
 * The row carries a UUID of its own even though `(courseId, time)` already identifies it, so that
 * moving a chip from 08:00 to 09:00 is an update of a row the editor is holding rather than a delete
 * and an insert that a half-finished edit could leave as neither.
 */
@Entity(
    tableName = "medication_times",
    foreignKeys = [
        ForeignKey(
            entity = MedicationCourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Unique, and `courseId` leads it — so it is also the index the foreign key needs, and
    // "this course's times, in order" reads straight off it.
    indices = [Index(value = ["courseId", "time"], unique = true)],
)
data class MedicationTimeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val time: LocalTime,
)

/**
 * One dose, **as recorded by the owner**. Never derived, never written by the app on its own
 * (ADR-0002): due doses are computed from the course and its times, and this table holds only the
 * answers a person gave.
 *
 * **[scheduledOn] and [scheduledTime] are null *together*, and that is an ad-hoc dose** — a rescue
 * dose at 03:00, or last night's 20:00 given at 00:30 after the slot stopped existing. Normal, not an
 * error.
 *
 * **The slot's key is local, never an `Instant`** (ADR-0002). The same 08:00 slot is `06:00Z` in
 * Warsaw and `07:00Z` in London, so an instant-keyed row stops matching its own slot the moment the
 * owner travels — and a dose already given then reads as unanswered and re-arms its alarm.
 * [recordedAt] stays an `Instant` because that one *is* a real moment: it is when the syringe went in.
 *
 * **A recorded dose does not re-specify the amount** (ADR-0002). The amount is the course's, and a
 * copy per dose would be forty chances for the record to disagree with the prescription.
 *
 * The unique index on `(courseId, scheduledOn, scheduledTime)` is what makes a derived slot
 * **answerable exactly once** — it is the join between a row that exists and a slot that does not.
 * NULLs are distinct in SQLite, so ad-hoc doses fall out of the constraint for free, with no partial
 * index and no branch anywhere in the code.
 */
@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationCourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // `courseId` leads, so this doubles as the foreign key's index and as "this course's doses".
    indices = [Index(value = ["courseId", "scheduledOn", "scheduledTime"], unique = true)],
)
data class DoseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    /** Null together with [scheduledTime] for an ad-hoc dose. */
    val scheduledOn: LocalDate? = null,
    val scheduledTime: LocalTime? = null,
    val recordedAt: Instant = Instant.now(),
    val status: DoseStatus,
    val note: String? = null,
)
