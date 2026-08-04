package app.binky.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One vet, or one clinic's vet — a directory entry, **app-wide and with no bunny FK**.
 *
 * A household's bunnies see the same vet, so scoping this per bunny would make the owner type the
 * clinic's phone number in once per rabbit and keep two copies in step by hand. It is also why a vet
 * **outlives its visits** (ADR-0017): the visit is the health record and the vet is an address book
 * entry, and deleting last year's visit must not lose the number you ring at 22:00.
 *
 * Everything but the name is optional, because the useful moment to add a vet is mid-visit-entry with
 * a rabbit on your lap, and a form that demands a clinic address then is a form that gets skipped.
 */
@Entity(tableName = "vets")
data class VetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val clinic: String? = null,
    val phone: String? = null,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
)

/**
 * One vet visit for one bunny.
 *
 * **There is no cost field, and that is a decision rather than an omission** (ADR-0017). This app
 * records health, not spending: a price column invites a total, a total invites a budget screen, and
 * none of that helps anybody notice a rabbit is eating less. Stated here because the absence is
 * otherwise indistinguishable from something nobody got round to.
 *
 * **[visitedOn] is a [LocalDate], not an [Instant]** — the same distinction 4b drew for care dates. A
 * visit happens on a *day*; storing an instant makes it a different day the first time the owner
 * opens the app in another timezone.
 *
 * [vetId] is `SET NULL` rather than `CASCADE`, which is the schema saying the sentence above: removing
 * a vet from the directory leaves every visit standing, minus the name. [bunnyId] is `CASCADE`,
 * because a visit is meaningless without the rabbit it was for.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = BunnyEntity::class,
            parentColumns = ["id"],
            childColumns = ["bunnyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VetEntity::class,
            parentColumns = ["id"],
            childColumns = ["vetId"],
            // The vet is a directory entry; the visit is the record. The record survives.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    // Every read is "this bunny's visits, newest first"; the `vetId` index is what makes SET_NULL
    // cheap and serves "which visits used this vet" on the directory screen (5c).
    indices = [Index(value = ["bunnyId", "visitedOn"]), Index("vetId")],
)
data class VisitEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bunnyId: String,
    val vetId: String? = null,
    val visitedOn: LocalDate,
    val reason: String,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
)
