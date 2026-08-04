package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One piece of paperwork: a vaccination card, a lab result, a discharge note.
 *
 * **A document is the paperwork and [DocumentPageEntity] rows are its images**, because a two-page
 * blood panel is one result and not two documents, and because reordering is something a scanner's
 * output actually needs.
 *
 * [visitId] is `SET NULL`: paperwork usually comes *from* a visit, but it stays the bunny's record
 * when that visit row goes — the same survival rule [VisitEntity] gets from its vet.
 *
 * **[capturedAt] is nullable and is not [createdAt]**. It is the date printed on the page, which the
 * owner may know, may type in later, or may never know at all; [createdAt] is when it was scanned.
 * Guessing the first from the second would date a two-year-old vaccination card to this afternoon.
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("bunnyId"), Index("visitId")],
)
data class DocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    val visitId: String? = null,
    val title: String,
    /** The date on the page, if the owner knows it. Not the scan date — see [createdAt]. */
    val capturedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)

/**
 * One scanned page of a [DocumentEntity].
 *
 * **[path] is relative and kind-split** — `documents/<uuid>.jpg`, resolved against `filesDir` at read
 * time (house rule, ADR-0005). Absolute paths change across installs and break restored backups, and
 * the per-kind directory is what makes an export scope a list of directories rather than a filter.
 *
 * [position] is the page order the owner sees; the row's own id is what a reorder moves, so dragging
 * page 3 above page 1 rewrites two integers rather than shuffling file paths between rows.
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Every read is "this document's pages, in order".
    indices = [Index(value = ["documentId", "position"])],
)
data class DocumentPageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    /** Relative, `documents/<uuid>.jpg`. Written by `MediaFiles.persist` and nothing else. */
    val path: String,
    val position: Int,
)
