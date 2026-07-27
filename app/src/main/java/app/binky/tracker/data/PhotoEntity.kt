package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * One photo in a bunny's gallery — the sentimental half of the app, deliberately separate from the
 * avatar, which is one portrait used for identification (ADR-0015).
 *
 * [path] is **relative**, `photos/<uuid>.jpg`, resolved against `filesDir` at read time (house
 * rule). The file is written before this row exists (ADR-0020), so the worst a crash between the
 * two can do is leave an invisible orphan file rather than a row pointing at nothing.
 *
 * Two timestamps again, and for the same reason weighings have two. [capturedAt] is what the
 * camera said, read off the source before its metadata was stripped; [createdAt] is when the photo
 * was added to the app. The gallery orders by `COALESCE(capturedAt, createdAt)`, because a bulk
 * import from the camera roll lands twenty photos spanning two years within the same millisecond,
 * and ordering those by when they were *added* is arbitrary order for a gallery whose whole point
 * is a bunny growing up.
 *
 * [capturedAt] is nullable because most images genuinely have no date: screenshots, re-shared
 * pictures and anything that has been through a messaging app carry none.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            // A photo is of exactly one bunny, so it goes with them. The *file* does not — Room's
            // cascade deletes rows and never touches the filesystem, which is why
            // `BunnyRepository.delete` collects the paths first.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // Every read is "this bunny's gallery, newest first". The index serves the filter; the ordering
    // expression is a COALESCE and cannot use it, which is the right trade for a per-bunny gallery
    // of a few hundred rows at most.
    indices = [Index(value = ["bunnyId", "createdAt"])],
)
data class PhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    /** Relative, `photos/<uuid>.jpg`. Never absolute — see the house rule. */
    val path: String,
    val caption: String? = null,
    /** What the source's EXIF said, or null when it said nothing. */
    val capturedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
