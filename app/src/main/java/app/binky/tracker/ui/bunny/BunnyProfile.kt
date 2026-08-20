package app.binky.tracker.ui.bunny

import app.binky.tracker.data.BunnyEntity
import app.binky.tracker.data.NeuterStatus
import app.binky.tracker.data.Sex
import app.binky.tracker.media.MediaFiles
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** One member of the fluffle, as the profile has to render them. */
data class Housemate(
    val id: String,
    val name: String,
    /**
     * Resolved like [BunnyProfile.avatar] and for the same reason — the database stores a relative
     * path (house rule) and this is where it becomes a [File], so no composable has to know where
     * `filesDir` is. Null, or a file that no longer exists, renders as the placeholder: the fluffle
     * sheet is a list of rabbits, and a restored backup that lacks photos must still list them.
     */
    val avatar: File?,
    /**
     * An archived housemate is shown distinguishably — *"Lives with Hazel (archived)"* — rather
     * than as a current roommate: the fluffle survives archival, and the survivor genuinely did
     * live with them (ADR-0008).
     */
    val archived: Boolean,
)

/**
 * A bunny as the profile card and the list row draw it. Both render the *same* rows, because Home
 * under "All bunnies" **is** the bunny list — two screens rendering the same data would diverge the
 * moment one of them gained a field (ADR-0015).
 *
 * The avatar arrives as a resolved [File]: the database stores a relative path (house rule) and
 * this is the read where it is resolved, so no composable has to know where `filesDir` is. The file
 * may legitimately be missing — a restored backup can lack photos — and renders as a placeholder.
 */
data class BunnyProfile(
    val id: String,
    val name: String,
    val avatar: File?,
    val birthDate: LocalDate?,
    val birthDateApproximate: Boolean,
    val sex: Sex,
    val neutered: NeuterStatus,
    val breed: String?,
    val colour: String?,
    val archivedAt: Instant?,
    val housemates: List<Housemate>,
) {
    val archived: Boolean get() = archivedAt != null
}

/**
 * @param everyBunny active *and* archived bunnies, so an archived housemate still shows up. The
 *   fluffle is read off the shared `fluffleId` here rather than through another query: the caller
 *   already holds every row, and a second query would only be a second source of truth.
 */
fun BunnyEntity.toProfile(
    everyBunny: List<BunnyEntity>,
    media: MediaFiles,
): BunnyProfile =
    BunnyProfile(
        id = id,
        name = name,
        avatar = avatarPath?.let(media::resolve),
        birthDate = birthDate,
        birthDateApproximate = birthDateApproximate,
        sex = sex,
        neutered = neutered,
        breed = breed?.takeIf { it.isNotBlank() },
        colour = colour?.takeIf { it.isNotBlank() },
        archivedAt = archivedAt,
        housemates =
            everyBunny
                .filter { it.id != id && it.fluffleId != null && it.fluffleId == fluffleId }
                .map {
                    Housemate(
                        id = it.id,
                        name = it.name,
                        avatar = it.avatarPath?.let(media::resolve),
                        archived = it.archivedAt != null,
                    )
                },
    )
