package app.bunny.tracker

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.bunny.tracker.ui.archive.ArchivedBunniesScreen
import app.bunny.tracker.ui.bunny.BunnyEditorScreen
import app.bunny.tracker.ui.care.CareAndMedsScreen
import app.bunny.tracker.ui.home.HomeScreen
import app.bunny.tracker.ui.more.MoreScreen
import app.bunny.tracker.ui.observations.LogObservationScreen
import app.bunny.tracker.ui.observations.ObservationsScreen
import app.bunny.tracker.ui.shell.AppShellViewModel
import app.bunny.tracker.ui.shell.BunnySwitcher
import app.bunny.tracker.ui.weight.WeightScreen

/**
 * The app shell: the persistent bunny switcher, the bottom-navigation destinations, and the one
 * back stack they share (ADR-0015).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.Factory)
    // Kotlin note: `by` unwraps the State object, so `state` reads as the value itself.
    // `collectAsStateWithLifecycle` subscribes to the Flow only while the screen is on screen —
    // the Compose equivalent of subscribing in an effect and unsubscribing on unmount.
    val state by shellViewModel.uiState.collectAsStateWithLifecycle()

    val backStack = rememberNavBackStack(Home)
    val activity = LocalActivity.current

    // A detail screen pushed on top of a destination keeps that destination selected below it.
    val current = backStack.lastOrNull { it.asTopLevelDestination() != null }?.asTopLevelDestination()
    val onDetailScreen = backStack.lastOrNull()?.asTopLevelDestination() == null

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!onDetailScreen) {
                Column {
                    TopAppBar(
                        title = {
                            BunnySwitcher(
                                state = state,
                                onSelectBunny = shellViewModel::selectBunny,
                                onSelectAllBunnies = shellViewModel::selectAllBunnies,
                                onAddBunny = { backStack.add(BunnyEditor()) },
                            )
                        },
                    )
                    if (state.readOnly) {
                        ArchivedBanner(onLeave = shellViewModel::closeArchivedScope)
                    }
                }
            }
        },
        bottomBar = {
            if (!onDetailScreen) {
                BunnyNavigationBar(
                    current = current,
                    onSelect = { destination -> backStack.showTopLevel(destination) },
                )
            }
        },
    ) { insets ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(insets),
            onBack = {
                // Back from a detail screen returns to its destination; back from any top-level
                // destination returns to Home, which is always the bottom of the stack; back from
                // Home exits (ADR-0015).
                if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
            },
            // Kotlin note: this is a builder DSL, not a map literal — `entry<Home> { … }` registers
            // the composable that renders that key, and the lambda receives the key itself, which
            // is how a key carrying arguments passes them in.
            entryProvider =
                entryProvider {
                    entry<Home> {
                        HomeScreen(
                            onAddBunny = { backStack.add(BunnyEditor()) },
                            onEditBunny = { bunnyId -> backStack.add(BunnyEditor(bunnyId)) },
                            onSelectBunny = shellViewModel::selectBunny,
                        )
                    }
                    entry<Weight> { WeightScreen(state = state) }
                    entry<Observations> { ObservationsScreen(state = state) }
                    entry<CareAndMeds> { CareAndMedsScreen(state = state) }
                    entry<More> { MoreScreen(onOpenArchived = { backStack.add(ArchivedBunnies) }) }
                    entry<ArchivedBunnies> {
                        ArchivedBunniesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpen = { bunnyId ->
                                shellViewModel.openArchivedScope(bunnyId)
                                // The read-only scope is a scope over the ordinary screens, not a
                                // screen of its own, so entering it lands on Home.
                                backStack.showTopLevel(TopLevelDestination.HOME)
                            },
                        )
                    }
                    // Reachable only from Phase 2's "+" — the route is settled now, the FAB is not.
                    entry<LogObservation> { LogObservationScreen(state = state) }
                    entry<BunnyEditor> { key ->
                        BunnyEditorScreen(
                            bunnyId = key.bunnyId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }
}

@Composable
private fun BunnyNavigationBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries
            .filter { it.visibility != DestinationVisibility.Hidden }
            .forEach { destination ->
                NavigationBarItem(
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            // The label below carries the name; describing the icon too would only
                            // make a screen reader say it twice.
                            contentDescription = null,
                        )
                    },
                    label = {
                        // Five destinations on a phone leaves ~70dp per label, and "Observations"
                        // has no break opportunity — left to wrap it splits mid-word and draws over
                        // its neighbours. One line, ellipsised, at the smaller of the two label
                        // styles.
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
    }
}

/**
 * The read-only scope onto an archived bunny (ADR-0015): a banner, and no write actions. Reachable
 * from checkpoint 1d's archived list, and never persisted — a background kill must not reopen the
 * app into a memorial.
 */
@Composable
private fun ArchivedBanner(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.archived_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onLeave) { Text(stringResource(R.string.archived_banner_leave)) }
        }
    }
}

/**
 * **One back stack, and switching top-level destination replaces rather than pushes** (ADR-0015).
 *
 * Home stays at the bottom, so back from Weight / Observations / Care & Meds / More returns to it
 * and back from Home exits. Per-tab back stacks are where Nav3 wiring turns hairy, and this app's
 * detail screens are shallow — pushing destinations would only turn Back into a history tour of the
 * bottom bar.
 */
private fun NavBackStack<NavKey>.showTopLevel(destination: TopLevelDestination) {
    while (size > 1) removeAt(size - 1)
    if (destination.key != Home) add(destination.key)
}
