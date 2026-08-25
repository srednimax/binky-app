package app.binky.tracker

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.SetupState
import app.binky.tracker.data.bunnyId
import app.binky.tracker.ui.archive.ArchivedBunniesScreen
import app.binky.tracker.ui.backup.BackupScreen
import app.binky.tracker.ui.bunny.BunnyEditorScreen
import app.binky.tracker.ui.care.CareAndMedsScreen
import app.binky.tracker.ui.care.CareReminderEditorScreen
import app.binky.tracker.ui.care.CareReminderScreen
import app.binky.tracker.ui.care.MedicationCourseEditorScreen
import app.binky.tracker.ui.care.MedicationCourseScreen
import app.binky.tracker.ui.care.VisitEditorScreen
import app.binky.tracker.ui.documents.DocumentScreen
import app.binky.tracker.ui.documents.DocumentsScreen
import app.binky.tracker.ui.events.EventEditorScreen
import app.binky.tracker.ui.events.EventsScreen
import app.binky.tracker.ui.home.HomeScreen
import app.binky.tracker.ui.more.MoreScreen
import app.binky.tracker.ui.observations.ChooseBunnyDialog
import app.binky.tracker.ui.observations.HealthyDaySnackbar
import app.binky.tracker.ui.observations.HealthyDayViewModel
import app.binky.tracker.ui.observations.ObservationEntryScreen
import app.binky.tracker.ui.observations.ObservationsScreen
import app.binky.tracker.ui.observations.RecordDaySheet
import app.binky.tracker.ui.photos.PhotoGalleryScreen
import app.binky.tracker.ui.settings.SettingsScreen
import app.binky.tracker.ui.setup.SetupBackupStep
import app.binky.tracker.ui.setup.SetupBunnyStep
import app.binky.tracker.ui.setup.SetupRemindersStep
import app.binky.tracker.ui.shell.AppShellViewModel
import app.binky.tracker.ui.shell.BunnySwitcher
import app.binky.tracker.ui.shell.ShellUiState
import app.binky.tracker.ui.support.LicenceTextScreen
import app.binky.tracker.ui.support.LicencesScreen
import app.binky.tracker.ui.support.SupportScreen
import app.binky.tracker.ui.vet.VetEditorScreen
import app.binky.tracker.ui.vet.VetsScreen
import app.binky.tracker.ui.watch.WatchExpiryHost
import app.binky.tracker.ui.weight.WeightEntryScreen
import app.binky.tracker.ui.weight.WeightScreen
import app.binky.tracker.work.ReminderTap

/**
 * What every back-stack entry is wrapped in — above all, **one `ViewModelStore` per entry**.
 *
 * Nav3 does not do this on its own. The ViewModel decorator ships in a separate artifact
 * (`lifecycle-viewmodel-navigation3`), which `navigation3-ui` does not depend on, so `NavDisplay`'s
 * default list cannot contain it. Without it every `viewModel()` resolves to the *Activity's* store
 * and outlives the screen that made it: the bunny editor came back with its `saved` flag still set
 * and bounced straight out of the second "Add a bunny" of a session, and only killing the process
 * cleared it.
 *
 * `rememberSaveableStateHolderNavEntryDecorator` is Nav3's own default, restated because passing
 * the list replaces it. The scene-setup decorator is `internal` to Nav3 and applied by `NavDisplay`
 * itself, so it is not ours to restate.
 *
 * Extracted from [MainNavigation] so `NavigationScopingTest` can assert the scoping directly.
 */
@Composable
internal fun appEntryDecorators(): List<NavEntryDecorator<NavKey>> =
    listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

/**
 * The app, or the wizard that has to run before there is an app to show (ADR-0006).
 *
 * The two are alternatives, each with its own back stack, and neither is drawn until the question
 * is settled — a frame of the shell in front of the wizard that is about to cover it up is the one
 * thing a first run must not do.
 */
@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
    notificationTap: ReminderTap? = null,
    onNotificationHandled: () -> Unit = {},
) {
    val shellViewModel: AppShellViewModel = viewModel(factory = AppShellViewModel.Factory)
    // Kotlin note: `by` unwraps the State object, so `state` reads as the value itself.
    // `collectAsStateWithLifecycle` subscribes to the Flow only while the screen is on screen —
    // the Compose equivalent of subscribing in an effect and unsubscribing on unmount.
    val state by shellViewModel.uiState.collectAsStateWithLifecycle()
    val setup by shellViewModel.setupState.collectAsStateWithLifecycle()

    // Kotlin note: `when` over an enum is exhaustive when it is used as an expression or covers
    // every entry — adding a fourth SetupState would stop this compiling, which is the point.
    when (setup) {
        SetupState.Loading -> Unit
        // A notification cannot exist before setup has run — there are no reminders to be due — so
        // the wizard deliberately ignores one rather than being interrupted by it.
        SetupState.Required -> {
            // Recorded here rather than by any step: showing the wizard is the event, and it is the
            // showing that has to be remembered. Kotlin note: `LaunchedEffect(Unit)` runs its block
            // once when this branch enters composition and cancels it if the branch leaves — the
            // dependency-free `useEffect` of the pair.
            LaunchedEffect(Unit) { shellViewModel.markSetupStarted() }
            SetupNavigation(state = state, modifier = modifier)
        }
        SetupState.Complete ->
            AppShell(
                shellViewModel = shellViewModel,
                state = state,
                notificationTap = notificationTap,
                onNotificationHandled = onNotificationHandled,
                modifier = modifier,
            )
    }
}

/**
 * First-run setup (ADR-0006), on a back stack of its own.
 *
 * **Its own stack, not two more keys on the shell's.** The shell's stack is rooted at Home, so Back
 * out of the first setup step would land on an app that is not set up yet — and with the wizard's
 * key gone from the stack, nothing would bring it back until the next launch. Rooted at
 * [SetupBunny] instead, Back out of the first step exits the app, which is what a first screen
 * should do.
 *
 * The stack is what makes reusing [BunnyEditorScreen] free: [appEntryDecorators] gives its entry a
 * `ViewModelStore` that dies with the entry, exactly as in the shell. Composed outside a
 * `NavDisplay` it would resolve to the Activity's store instead, and come back to a second visit
 * with its `saved` flag still set — the bug `appEntryDecorators` was written for.
 *
 * There is no `onFinish` callback: the last step writes the preference, `setupState` flips, and
 * [MainNavigation] swaps in the shell. One mechanism, and no way for the screen and the stored
 * answer to disagree.
 */
@Composable
private fun SetupNavigation(
    state: ShellUiState,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(SetupBunny)
    val activity = LocalActivity.current

    // A bare Scaffold, for its insets alone: the app draws edge to edge, and every screen in here —
    // the bunny editor above all — is written expecting its caller to have already padded past the
    // status bar, the way the shell's Scaffold does.
    Scaffold(modifier = modifier) { insets ->
        NavDisplay(
            backStack = backStack,
            // The keyboard included, for the same reason and by the same means as the shell's —
            // see [AppShell]. The wizard hosts the bunny editor, so it has a form in it too.
            modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding(),
            entryDecorators = appEntryDecorators(),
            onBack = {
                if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
            },
            entryProvider =
                entryProvider {
                    entry<SetupBunny> {
                        SetupBunnyStep(
                            bunnies = state.activeBunnies,
                            onAddBunny = { backStack.add(BunnyEditor()) },
                            onContinue = { backStack.add(SetupBackup) },
                        )
                    }
                    // The editor verbatim, arguments and all. A wizard-shaped copy of the bunny
                    // form would be a second place for ADR-0016's fields to drift out of.
                    entry<BunnyEditor> { key ->
                        BunnyEditorScreen(
                            bunnyId = key.bunnyId,
                            // Fired on save *and* on cancel, and the step needs no help telling
                            // them apart — it reads whether a bunny now exists.
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<SetupBackup> {
                        SetupBackupStep(
                            onBack = { backStack.removeLastOrNull() },
                            onContinue = { backStack.add(SetupReminders) },
                        )
                    }
                    entry<SetupReminders> { SetupRemindersStep(onBack = { backStack.removeLastOrNull() }) }
                },
        )
    }
}

/**
 * The app shell: the persistent bunny switcher, the bottom-navigation destinations, and the one
 * back stack they share (ADR-0015).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    shellViewModel: AppShellViewModel,
    state: ShellUiState,
    notificationTap: ReminderTap?,
    onNotificationHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A stack restored from a build where Care & Meds was still a live tab comes back naming a
    // destination this build hides (ADR-0015). Repaired during composition, before `NavDisplay` —
    // a child, composed after this line — first reads the list, so the hidden key never reaches the
    // entry provider and no frame of it is ever drawn. An effect would run one frame too late.
    val restored = rememberNavBackStack(Home)
    val backStack = remember(restored) { restored.withoutHiddenDestinationsInPlace() }
    val activity = LocalActivity.current

    // A detail screen pushed on top of a destination keeps that destination selected below it.
    val current = backStack.lastOrNull { it.asTopLevelDestination() != null }?.asTopLevelDestination()
    val onDetailScreen = backStack.lastOrNull()?.asTopLevelDestination() == null

    // **The "+" opens a chooser, not the form** (Phase 7.5 §6). Both ways to record a day are behind
    // it now — the one-tap healthy day and the full observation — because the app used to offer them
    // in two unrelated places with the *longer* one on the discoverable button. See [RecordDaySheet].
    var recordingDay by rememberSaveable { mutableStateOf(false) }

    // The "which bunny?" step the global "+" takes under "All bunnies" (ADR-0008). Lives here rather
    // than in a screen because the FAB does, and the FAB is in the shell precisely so it is the same
    // button on Home and on Observations.
    var choosingBunny by rememberSaveable { mutableStateOf(false) }

    // The same question for the healthy day, kept as its own flag for the reason the three below
    // are: two dialogs sharing one would answer each other's question. Under "All bunnies" there is
    // no fluffle to pre-select from, so the one-tap write asks who it is about rather than sweeping
    // a tray fact across bunnies that share no tray (ADR-0008).
    var choosingForHealthyDay by rememberSaveable { mutableStateOf(false) }

    // Shell-scoped, because the button that starts it is: the "+" is the same button on Home and on
    // Observations, and the receipt's snackbar belongs to the Scaffold below for the same reason.
    val healthyDayViewModel: HealthyDayViewModel = viewModel(factory = HealthyDayViewModel.Factory)
    val receipt by healthyDayViewModel.receipt.collectAsStateWithLifecycle()

    // The same step for More's photo gallery, which is per-bunny for the same reason: "All bunnies"
    // is not a gallery, it is several. Kept as its own flag rather than shared with the "+" so the
    // two dialogs cannot answer each other's question.
    var choosingGalleryBunny by rememberSaveable { mutableStateOf(false) }

    // And the same again for documents, kept separate for the reason above: two dialogs sharing one
    // flag would answer each other's question.
    var choosingDocumentBunny by rememberSaveable { mutableStateOf(false) }

    // And once more for the timeline, which is per bunny for the same reason: "All bunnies" is not
    // one agenda but several. Its own flag, so no two of these dialogs can answer each other.
    var choosingEventBunny by rememberSaveable { mutableStateOf(false) }

    // The snackbar host belongs to the **same** Scaffold as the FAB, or the two lay out in ignorance
    // of each other and the FAB covers the snackbar's action — which for the healthy day means an
    // Undo the owner can see and cannot press. Material3's Scaffold lifts the FAB above whatever its
    // own snackbar host is showing; that only works if it owns both.
    val snackbarHostState = remember { SnackbarHostState() }

    HealthyDaySnackbar(
        receipt = receipt,
        hostState = snackbarHostState,
        onUndo = healthyDayViewModel::undoHealthyDay,
        onDismiss = healthyDayViewModel::dismissReceipt,
    )

    // **Where a reminder notification lands** (PLAN 4c, 4d). A tap writes the app-wide selection
    // through the same path the switcher uses and *then* hands the back stack a destination,
    // because `CareAndMeds` takes no arguments — selecting that bunny is the only thing that can
    // decide whose reminders are on screen, and landing on someone else's would be the app lying
    // about it. A watch nag goes one step further and pushes the observation form on top, which is
    // why its `PendingIntent` has to resolve to a **back stack** and not just to the Activity:
    // Back out of the form has to land somewhere the owner can stay.
    //
    // A bunny archived since the notification was posted falls back to Home **without touching the
    // selection**: `Archived(id)` is deliberately never persisted (ADR-0015), so there is no scope
    // to send them to, and quietly selecting a memorial would be worse than doing nothing.
    LaunchedEffect(notificationTap, state.selection, state.activeBunnies) {
        val tap = notificationTap ?: return@LaunchedEffect
        // Nothing is decidable while the first emissions are still in flight, and acting on an empty
        // list would send an honest tap to Home.
        if (state.selection == BunnySelection.Loading) return@LaunchedEffect

        // Kotlin note: `when` over a sealed interface is exhaustive without an `else`, so another
        // destination would stop this compiling rather than silently falling through to Home.
        val target =
            when (tap) {
                is ReminderTap.Care -> tap.bunnyId
                is ReminderTap.LogObservation -> tap.bunnyId
                is ReminderTap.Medication -> tap.bunnyId
                is ReminderTap.Event -> tap.bunnyId
                // "The app as it stands" means exactly that: nothing to select and nowhere to send
                // them, so the stack is left where the owner last had it.
                ReminderTap.OpenApp -> {
                    onNotificationHandled()
                    return@LaunchedEffect
                }
                // The export prompt names no bunny, so it never reaches the selection below. More
                // first, then Backup on top of it: `showTopLevel` clears back to the root, so this
                // is the two-entry stack the notification promises — Back out of Backup lands where
                // Backup is normally reached from rather than on nothing.
                ReminderTap.OpenBackup -> {
                    backStack.showTopLevel(TopLevelDestination.MORE)
                    backStack.add(Backup)
                    onNotificationHandled()
                    return@LaunchedEffect
                }
            }
        if (state.activeBunnies.any { it.id == target }) {
            shellViewModel.selectBunny(target)
            when (tap) {
                is ReminderTap.Care -> backStack.showTopLevel(TopLevelDestination.CARE)
                is ReminderTap.LogObservation -> {
                    // Home first, then the form on top of it: `showTopLevel` clears back to the
                    // root, so this *is* the two-entry back stack the nag promises — Back out of
                    // the form lands on Home rather than on nothing.
                    backStack.showTopLevel(TopLevelDestination.HOME)
                    backStack.add(LogObservation(target))
                }
                // Care & Meds first, then the course on top: the same two-entry stack, and the tab
                // under it is the one the course is normally reached from. The selection has just
                // been written above, which is what makes `MedicationCourse`'s course-only key safe
                // to land on.
                is ReminderTap.Medication -> {
                    backStack.showTopLevel(TopLevelDestination.CARE)
                    backStack.add(MedicationCourse(tap.courseId))
                }
                // More first, then the timeline on top of it — the same two-entry stack, and
                // the tab under it is where the timeline is normally reached from. The event's own
                // screen is deliberately *not* pushed: the notice names the day rather than one
                // row, and a day can hold several (ADR-0031).
                is ReminderTap.Event -> {
                    backStack.showTopLevel(TopLevelDestination.MORE)
                    backStack.add(Events(target))
                }
                ReminderTap.OpenApp, ReminderTap.OpenBackup -> Unit
            }
        } else {
            backStack.showTopLevel(TopLevelDestination.HOME)
        }
        onNotificationHandled()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        floatingActionButton = {
            if (!onDetailScreen && current.offersLogObservation && state.canLogObservation) {
                FloatingActionButton(onClick = { recordingDay = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.observation_add_title),
                    )
                }
            }
        },
    ) { insets ->
        NavDisplay(
            backStack = backStack,
            // **The keyboard is an inset like any other, and this Scaffold owns them** (PLAN 4f).
            //
            // `enableEdgeToEdge()` sets `decorFitsSystemWindows = false`, and that makes the
            // manifest's `adjustResize` inoperative — the window manager reports `adjust=pan` for
            // this activity whatever the manifest asks for. With nothing consuming `WindowInsets.ime`
            // the system then *pans the whole window* to keep the focused field in view, which slid
            // the top of the observation form under the status bar and pushed its `TopAppBar` —
            // Save included — off the top of the screen. Caught in 4f's landscape and portrait
            // matrix, on the one form long enough to need scrolling.
            //
            // `consumeWindowInsets` before `imePadding` is what stops it double-counting: the
            // keyboard's inset is measured from the bottom of the *screen*, so it already contains
            // the navigation bar height that `padding(insets)` just applied. Consuming says "that
            // part is handled", leaving `imePadding` to add only the rest.
            modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding(),
            entryDecorators = appEntryDecorators(),
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
                            // The fluffle sheet reaches **both kinds** of bunny, and they are two
                            // different navigations rather than one with a flag. An active
                            // housemate is an ordinary selection, persisted like the switcher's;
                            // an archived one is ADR-0015's read-only scope, in memory only,
                            // because a background kill must never reopen the app into a memorial.
                            onOpenHousemate = { housemate ->
                                if (housemate.archived) {
                                    shellViewModel.openArchivedScope(housemate.id)
                                } else {
                                    // Closed first because the archived scope *wins outright* over
                                    // the stored selection (`resolveSelection`): from an archived
                                    // bunny's profile, selecting a live housemate without this
                                    // would write the choice and leave the screen where it was.
                                    shellViewModel.closeArchivedScope()
                                    shellViewModel.selectBunny(housemate.id)
                                }
                            },
                            // Straight to the key, without touching the selection: the card is
                            // already drawn for the bunny on screen, so the id it hands back is the
                            // one whose timeline it slices (ADR-0031).
                            onOpenTimeline = { bunnyId -> backStack.add(Events(bunnyId)) },
                        )
                    }
                    entry<Weight> {
                        WeightScreen(
                            onAddWeight = { bunnyId -> backStack.add(WeightEntry(bunnyId)) },
                            onEditWeight = { bunnyId, weightId ->
                                backStack.add(WeightEntry(bunnyId, weightId))
                            },
                            // A visit-recorded weighing is edited at the visit, never here
                            // (ADR-0017), so the row offers the visit instead of the form.
                            onOpenVisit = { bunnyId, visitId ->
                                backStack.add(VisitEditor(bunnyId, visitId))
                            },
                            // The trend flag's age question (ADR-0028), which is the one action on
                            // that card leading off the tab it is drawn on.
                            onEditBunny = { bunnyId -> backStack.add(BunnyEditor(bunnyId)) },
                        )
                    }
                    entry<Observations> {
                        ObservationsScreen(
                            onEditObservation = { bunnyId, observationId ->
                                backStack.add(LogObservation(bunnyId, observationId))
                            },
                        )
                    }
                    entry<CareAndMeds> {
                        CareAndMedsScreen(
                            state = state,
                            // Picking under "All bunnies" *selects* rather than passing an id
                            // along, for the same reason the notification tap does: the key
                            // carries no bunny, so the selection is what decides.
                            onSelectBunny = shellViewModel::selectBunny,
                            onAddReminder = { bunnyId -> backStack.add(CareReminderEditor(bunnyId)) },
                            onOpenReminder = { reminderId -> backStack.add(CareReminder(reminderId)) },
                            // A weigh-in is completed by weighing, so *Done* opens the weight form
                            // rather than writing a tick with no number behind it.
                            onRecordWeight = { bunnyId -> backStack.add(WeightEntry(bunnyId)) },
                            onAddVisit = { bunnyId -> backStack.add(VisitEditor(bunnyId)) },
                            onOpenVisit = { bunnyId, visitId ->
                                backStack.add(VisitEditor(bunnyId, visitId))
                            },
                            onAddCourse = { bunnyId -> backStack.add(MedicationCourseEditor(bunnyId)) },
                            onOpenCourse = { courseId -> backStack.add(MedicationCourse(courseId)) },
                        )
                    }
                    entry<MedicationCourse> { key ->
                        MedicationCourseScreen(
                            courseId = key.courseId,
                            // The archived scope is the only read-only one, and a course is
                            // reachable only through the bunny it belongs to (ADR-0004).
                            readOnly = state.readOnly,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { bunnyId, courseId ->
                                backStack.add(MedicationCourseEditor(bunnyId, courseId))
                            },
                        )
                    }
                    entry<MedicationCourseEditor> { key ->
                        MedicationCourseEditorScreen(
                            bunnyId = key.bunnyId,
                            courseId = key.courseId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CareReminder> { key ->
                        CareReminderScreen(
                            reminderId = key.reminderId,
                            // The archived scope is the only read-only one, and a reminder is
                            // reachable only through the bunny it belongs to.
                            readOnly = state.readOnly,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { bunnyId, reminderId ->
                                backStack.add(CareReminderEditor(bunnyId, reminderId))
                            },
                            onRecordWeight = { bunnyId -> backStack.add(WeightEntry(bunnyId)) },
                        )
                    }
                    entry<CareReminderEditor> { key ->
                        CareReminderEditorScreen(
                            bunnyId = key.bunnyId,
                            reminderId = key.reminderId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<More> {
                        MoreScreen(
                            onOpenArchived = { backStack.add(ArchivedBunnies) },
                            onOpenSettings = { backStack.add(Settings) },
                            onOpenVets = { backStack.add(Vets) },
                            // Never inert and never bunny-scoped: a bug report is about the app.
                            onOpenSupport = { backStack.add(Support) },
                            // Null while there is no bunny to have a timeline — the row is then
                            // one of ADR-0015's inert entries rather than a way into an empty screen.
                            onOpenEvents =
                                if (state.hasBunnyInScope) {
                                    {
                                        val bunnyId = state.selection.bunnyId
                                        if (bunnyId != null) {
                                            backStack.add(Events(bunnyId))
                                        } else {
                                            choosingEventBunny = true
                                        }
                                    }
                                } else {
                                    null
                                },
                            // The same rule again — the row is inert rather than a way into an
                            // empty screen.
                            onOpenPhotos =
                                if (state.hasBunnyInScope) {
                                    {
                                        val bunnyId = state.selection.bunnyId
                                        if (bunnyId != null) {
                                            backStack.add(PhotoGallery(bunnyId))
                                        } else {
                                            choosingGalleryBunny = true
                                        }
                                    }
                                } else {
                                    null
                                },
                            // The same rule, for the same reason: paperwork belongs to a bunny.
                            onOpenDocuments =
                                if (state.hasBunnyInScope) {
                                    {
                                        val bunnyId = state.selection.bunnyId
                                        if (bunnyId != null) {
                                            backStack.add(Documents(bunnyId))
                                        } else {
                                            choosingDocumentBunny = true
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                    entry<Settings> {
                        SettingsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenBackup = { backStack.add(Backup) },
                        )
                    }
                    entry<Backup> { BackupScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<Support> {
                        SupportScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicences = { backStack.add(Licences) },
                        )
                    }
                    entry<Licences> {
                        LicencesScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenLicenceText = { spdxId, title ->
                                backStack.add(LicenceText(spdxId, title))
                            },
                        )
                    }
                    entry<LicenceText> { key ->
                        LicenceTextScreen(
                            spdxId = key.spdxId,
                            title = key.title,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<WeightEntry> { key ->
                        WeightEntryScreen(
                            bunnyId = key.bunnyId,
                            weightId = key.weightId,
                            onOpenVisit = { visitId ->
                                backStack.add(VisitEditor(key.bunnyId, visitId))
                            },
                            onEditBunny = { backStack.add(BunnyEditor(key.bunnyId)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<VisitEditor> { key ->
                        VisitEditorScreen(
                            bunnyId = key.bunnyId,
                            visitId = key.visitId,
                            // The archived scope is the only read-only one, and a visit is reachable
                            // only through the bunny it belongs to (ADR-0004).
                            readOnly = state.readOnly,
                            snackbarHostState = snackbarHostState,
                            onOpenDocument = { documentId -> backStack.add(DocumentDetail(documentId)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Vets> {
                        VetsScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onAddVet = { backStack.add(VetEditor()) },
                            onEditVet = { vetId -> backStack.add(VetEditor(vetId)) },
                        )
                    }
                    entry<VetEditor> { key ->
                        VetEditorScreen(
                            vetId = key.vetId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
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
                    entry<LogObservation> { key ->
                        ObservationEntryScreen(
                            bunnyId = key.bunnyId,
                            observationId = key.observationId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<BunnyEditor> { key ->
                        BunnyEditorScreen(
                            bunnyId = key.bunnyId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Documents> { key ->
                        DocumentsScreen(
                            bunnyId = key.bunnyId,
                            // The archived scope is the only read-only one, and it pins exactly the
                            // bunny this key carries.
                            readOnly = state.readOnly,
                            snackbarHostState = snackbarHostState,
                            onOpenDocument = { documentId -> backStack.add(DocumentDetail(documentId)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<DocumentDetail> { key ->
                        DocumentScreen(
                            documentId = key.documentId,
                            readOnly = state.readOnly,
                            snackbarHostState = snackbarHostState,
                            // A document names the visit it came from, and the visit editor is that
                            // visit's detail screen. The bunny comes from the document's own row
                            // rather than from the shell's selection, which under "All bunnies"
                            // names nobody.
                            onOpenVisit = { bunnyId, visitId ->
                                backStack.add(VisitEditor(bunnyId, visitId))
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Events> { key ->
                        EventsScreen(
                            bunnyId = key.bunnyId,
                            // The archived scope is the only read-only one, and it pins exactly the
                            // bunny this key carries, so the shell's flag is the right answer here.
                            readOnly = state.readOnly,
                            onBack = { backStack.removeLastOrNull() },
                            onAddEvent = { backStack.add(EventEditor(key.bunnyId)) },
                            onOpenEvent = { eventId -> backStack.add(EventEditor(key.bunnyId, eventId)) },
                            // The other three kinds tap back through to the screen that owns them,
                            // which is what keeps a derived list from becoming a second place to
                            // change things (ADR-0031).
                            onOpenVisit = { visitId -> backStack.add(VisitEditor(key.bunnyId, visitId)) },
                            onOpenCareReminder = { reminderId -> backStack.add(CareReminder(reminderId)) },
                        )
                    }
                    entry<EventEditor> { key ->
                        EventEditorScreen(
                            bunnyId = key.bunnyId,
                            eventId = key.eventId,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PhotoGallery> { key ->
                        PhotoGalleryScreen(
                            bunnyId = key.bunnyId,
                            // The archived scope is the only read-only one, and it pins exactly the
                            // bunny this key carries, so the shell's flag is the right answer here.
                            readOnly = state.readOnly,
                            snackbarHostState = snackbarHostState,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }

    if (recordingDay) {
        RecordDaySheet(
            // Under "All bunnies" there is no fluffle to pre-select from, so either path asks which
            // bunny before it writes anything (ADR-0008). One bunny in scope goes straight through
            // — the healthy day stays a *tap* rather than becoming a form.
            onHealthyDay = {
                recordingDay = false
                val bunnyId = state.selection.bunnyId
                if (bunnyId != null) healthyDayViewModel.logHealthyDay(bunnyId) else choosingForHealthyDay = true
            },
            onObservation = {
                recordingDay = false
                val bunnyId = state.selection.bunnyId
                if (bunnyId != null) backStack.add(LogObservation(bunnyId)) else choosingBunny = true
            },
            onDismiss = { recordingDay = false },
        )
    }

    if (choosingForHealthyDay) {
        ChooseBunnyDialog(
            title = stringResource(R.string.healthy_day_which_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingForHealthyDay = false
                healthyDayViewModel.logHealthyDay(bunnyId)
            },
            onDismiss = { choosingForHealthyDay = false },
        )
    }

    if (choosingBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.observation_which_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingBunny = false
                backStack.add(LogObservation(bunnyId))
            },
            onDismiss = { choosingBunny = false },
        )
    }

    if (choosingGalleryBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.photo_which_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingGalleryBunny = false
                backStack.add(PhotoGallery(bunnyId))
            },
            onDismiss = { choosingGalleryBunny = false },
        )
    }

    if (choosingEventBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.event_which_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingEventBunny = false
                backStack.add(Events(bunnyId))
            },
            onDismiss = { choosingEventBunny = false },
        )
    }

    if (choosingDocumentBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.document_which_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingDocumentBunny = false
                backStack.add(Documents(bunnyId))
            },
            onDismiss = { choosingDocumentBunny = false },
        )
    }

    // The watch's auto-expiry prompt (ADR-0001), hosted here because it is about a bunny rather
    // than about a screen — it has to appear over whatever the owner opened onto. It renders
    // nothing unless a watch has run out, which is almost always. Deliberately *not* inside the
    // wizard's branch: a watch cannot exist before setup has run.
    WatchExpiryHost()
}

/**
 * Whether there is a bunny whose photos there could be — a real selection, or "All bunnies", which
 * is a choice away from one. [BunnySelection.Loading] and [BunnySelection.Empty] are not.
 */
private val ShellUiState.hasBunnyInScope: Boolean
    get() = selection != BunnySelection.Loading && selection != BunnySelection.Empty

/**
 * Where the global "+" renders (ADR-0015): the two destinations an observation belongs to.
 *
 * **Not on More**, which is a settings drawer, and not on Weight, whose own add button carries a
 * bunny id the "+" deliberately does not — the global "+" stays observation-only.
 */
private val TopLevelDestination?.offersLogObservation: Boolean
    get() = this == TopLevelDestination.HOME || this == TopLevelDestination.OBSERVATIONS

/**
 * Whether there is anything to log against: a real bunny in scope, and a scope that permits writing.
 *
 * An archived bunny is read-only, so the "+" is **absent** rather than present-and-refusing
 * (ADR-0004).
 */
private val ShellUiState.canLogObservation: Boolean
    get() = !readOnly && hasBunnyInScope

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
                        // Four destinations at 1.0 and five again when Care & Meds returns, which
                        // is ~70dp per label at its tightest; "Observations" has no break
                        // opportunity, and left to wrap it splits mid-word and draws over its
                        // neighbours. One line at the smaller of the two label styles — sized for
                        // the five-tab case so 1.1 does not have to rediscover this.
                        //
                        // The label *shrinks* rather than ellipsising, which is a translation
                        // decision rather than a visual one: English "Observations" fits at 11sp
                        // and German "Beobachtungen" does not, and there is no shorter German word
                        // for it — CONTEXT.md's vocabulary is the concept, not a phrasing choice.
                        // Ellipsis would make four of nine languages ask the owner to recognise a
                        // truncated tab. autoSize only ever steps *down* from 11sp, so English and
                        // Polish render exactly as they did before.
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            autoSize =
                                TextAutoSize.StepBased(
                                    minFontSize = 8.sp,
                                    maxFontSize = 11.sp,
                                    stepSize = 0.5.sp,
                                ),
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

/**
 * Applies [withoutHiddenDestinations] to a live back stack, and hands the same stack back.
 *
 * The rule itself is the pure function in `NavigationKeys.kt`, where it is provable on the JVM;
 * this is only the part that has to touch a `NavBackStack`. It returns the receiver so the call
 * site is a `remember` that produces a value rather than one that performs a side effect — Compose
 * lint rejects the latter, and it is right to: a `remember` returning `Unit` is a hook that exists
 * only for what it did on the way past, which is the hardest kind to reason about.
 */
private fun NavBackStack<NavKey>.withoutHiddenDestinationsInPlace(): NavBackStack<NavKey> {
    val kept = withoutHiddenDestinations()
    if (kept != toList()) {
        clear()
        addAll(kept)
    }
    return this
}
