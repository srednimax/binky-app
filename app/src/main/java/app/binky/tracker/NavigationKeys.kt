package app.binky.tracker

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every route in the app, as a key. ADR-0012 requires the navigation structure decided before the
 * first screen, so these exist from Phase 1 even where the screen behind them is still a stub.
 *
 * Kotlin note: `data object` is a singleton with a sensible `toString`/`equals` — the right shape
 * for a route with no arguments. A route that carries arguments is a `data class` instead, and
 * `@Serializable` is what lets Nav3 save the whole back stack across process death.
 */
@Serializable data object Home : NavKey

@Serializable data object Weight : NavKey

@Serializable data object Observations : NavKey

@Serializable data object CareAndMeds : NavKey

@Serializable data object More : NavKey

/**
 * The global "+" observation entry (ADR-0015), and the edit path behind a timeline row.
 *
 * The route and key were settled in Phase 1 and deliberately left inert — deciding the structure and
 * rendering it are different claims, and the app's primary write action is the worst one to teach the
 * owner is dead. Phase 2f renders the FAB and gives the key its arguments.
 *
 * [bunnyId] is who the observation is about: the subject a new one pre-selects a fluffle from, or the
 * owner of the row an edit is changing the individual facts of. `null` [observationId] adds,
 * mirroring [BunnyEditor] and [WeightEntry].
 */
@Serializable
data class LogObservation(
    val bunnyId: String,
    val observationId: String? = null,
) : NavKey

/** Add or edit a bunny. `null` adds. */
@Serializable
data class BunnyEditor(
    val bunnyId: String? = null,
) : NavKey

/**
 * Add or edit a weighing. `null` [weightId] adds, mirroring [BunnyEditor].
 *
 * This **closes a Phase-1 omission rather than adding scope**: this file promises every route exists
 * from Phase 1 and this one did not. The global "+" stays observation-only and is never the way in
 * (ADR-0015).
 */
@Serializable
data class WeightEntry(
    val bunnyId: String,
    val weightId: String? = null,
) : NavKey

/**
 * The archived bunnies list, reached from More (ADR-0004). A detail screen, not a destination:
 * archived bunnies are deliberately absent from the switcher, and this is the one way to them.
 */
@Serializable data object ArchivedBunnies : NavKey

/** Settings, reached from More — the same shape as [ArchivedBunnies]: a detail route off a tab. */
@Serializable data object Settings : NavKey

/**
 * Backup and restore, reached from Settings (ADR-0005).
 *
 * Its own screen rather than a Settings section: it holds the export scope, the restore flow and
 * every recovery artifact on the phone, and the last of those has to sit beside the restore that can
 * load it back in.
 */
@Serializable data object Backup : NavKey

/**
 * One bunny's photo gallery, reached from More. A detail route off a tab, like [ArchivedBunnies].
 *
 * Keyed by the bunny rather than reading the shell's selection, so the back stack records *whose*
 * gallery is open: restoring after a process death has to reopen the same one, and under "All
 * bunnies" there is no selection to fall back on — More asks which bunny before pushing this.
 */
@Serializable
data class PhotoGallery(
    val bunnyId: String,
) : NavKey

/**
 * Whether a top-level destination is shown, shown as unavailable, or absent (ADR-0015).
 *
 * Defined **here in Phase 1**, so Phase 3's decision to hide Care & Meds from real users is the
 * one-value flip ADR-0015 intends rather than an introduction. Through Phases 1-2 everything is
 * [Live] — there are no users to mislead yet.
 *
 * A dead **tab** is [Hidden] — a fifth of primary navigation spent on a dead end. A dead **row**
 * inside More or Settings may be [ComingSoon], because it costs one line in a list.
 */
enum class DestinationVisibility { Hidden, ComingSoon, Live }

/**
 * The bottom-navigation destinations, fixed by ADR-0015 before the first screen exists.
 *
 * Kotlin note: enum entries can carry constructor arguments, so this is a small lookup table rather
 * than the bare constants a JS enum would give you — and `entries` (Kotlin 2.x) is its
 * `Object.values()`, in declaration order.
 *
 * The icons are placeholders: ADR-0012 defers iconography to the visual pass at the end, and these
 * only have to be distinguishable from each other until then.
 */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val visibility: DestinationVisibility = DestinationVisibility.Live,
) {
    HOME(Home, R.string.destination_home, Icons.Filled.Home),
    WEIGHT(Weight, R.string.destination_weight, Icons.Filled.Star),
    OBSERVATIONS(Observations, R.string.destination_observations, Icons.AutoMirrored.Filled.List),
    CARE(CareAndMeds, R.string.destination_care, Icons.Filled.Favorite),
    MORE(More, R.string.destination_more, Icons.Filled.MoreVert),
}

/** The destination a key belongs to, or null for a detail screen pushed on top of one. */
fun NavKey.asTopLevelDestination(): TopLevelDestination? = TopLevelDestination.entries.find { it.key == this }
