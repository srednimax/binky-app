package app.bunny.tracker

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
 * The global "+" observation entry (ADR-0015). The route and key exist from Phase 1, but **no FAB
 * renders** until observations exist in Phase 2: deciding the structure and rendering it are
 * different claims, and the app's primary write action is the worst one to teach the owner is inert.
 */
@Serializable data object LogObservation : NavKey

/** Add or edit a bunny. `null` adds. The form itself lands in checkpoint 1d. */
@Serializable
data class BunnyEditor(
    val bunnyId: String? = null,
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
