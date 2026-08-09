package app.binky.tracker.ui.care

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.DoseStatus
import app.binky.tracker.data.ScheduledDose
import app.binky.tracker.data.interval
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.GroupedCardItem
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.observations.ChooseBunnyDialog
import app.binky.tracker.ui.reminders.ReminderCaveats
import app.binky.tracker.ui.shell.ShellUiState
import app.binky.tracker.ui.weight.gramsLabel

/**
 * Care & Meds — one bunny's medication courses, recurring care due first (ADR-0018), **and its vet
 * visits** (ADR-0017).
 *
 * **The tab is a hub from 1.2**, and that is a decision about where things live rather than a
 * layout: medication courses, care reminders and vet visits are all *this bunny's ongoing care*, so
 * they share the bunny-scoped tab. The **vet directory is not here**: a vet is app-wide, so it lives
 * in More (ADR-0015), and only the visits are per bunny.
 *
 * **Medications go first** (PLAN 5e). A dose has a clock time today; a nail trim has a week. See
 * `MedicationsSection.kt` for why the order is fixed rather than conditional on having any.
 *
 * It is **per bunny, like weight and photos**: under "All bunnies" the tab asks which one and then
 * selects them app-wide, because [app.binky.tracker.CareAndMeds] takes no arguments and selecting is
 * the only thing that can decide whose reminders these are.
 *
 * ## Phase 7, against `3a` / `3b`
 *
 * Four sections of 64dp rows in grouped cards, and the whole route reads down in one pass where it
 * used to be a column of cards each ending in two text buttons. Three things changed structurally:
 *
 * - **The delivery caveat moved to the bottom** and became [ReminderCaveats]. It is a footnote about
 *   Android, not the first thing an owner needs at eight in the morning.
 * - **Destructive actions left the rows.** A 64dp row has nowhere to put a *Delete*, which is the
 *   same finding `Weight` made at `1d`. Deleting a course, a reminder or a visit now lives on that
 *   thing's own screen, one tap behind the chevron — see `MedicationCourseScreen`,
 *   `CareReminderScreen` and `VisitEditorScreen`. Nothing is unreachable and nothing is one tap
 *   closer to being destroyed by accident.
 * - **A row that is *asking* carries the answer; a row that is *telling* carries a chevron.** Today's
 *   doses offer *Given* / *Skipped* inline; a reminder actually due offers *Done*. Everything else
 *   opens.
 */
@Composable
fun CareAndMedsScreen(
    state: ShellUiState,
    onSelectBunny: (String) -> Unit,
    onAddReminder: (String) -> Unit,
    onOpenReminder: (String) -> Unit,
    onRecordWeight: (String) -> Unit,
    onAddVisit: (String) -> Unit,
    onOpenVisit: (String, String) -> Unit,
    onAddCourse: (String) -> Unit,
    onOpenCourse: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CareViewModel = viewModel(factory = CareViewModel.Factory, extras = appViewModelExtras())
    val careState by viewModel.uiState.collectAsStateWithLifecycle()
    var choosingBunny by remember { mutableStateOf(false) }

    when (careState.selection) {
        // Momentary, before the first database and preferences emissions arrive.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> RouteMessage(stringResource(R.string.add_a_bunny_first), modifier)
        BunnySelection.All ->
            RouteMessage(stringResource(R.string.care_pick_a_bunny), modifier) {
                Button(onClick = { choosingBunny = true }) {
                    Text(stringResource(R.string.care_choose_bunny))
                }
            }
        else ->
            CareList(
                state = careState,
                onAdd = { careState.bunnyId?.let(onAddReminder) },
                onOpen = { row -> onOpenReminder(row.id) },
                onComplete = { row ->
                    if (row.completedByWeighing) {
                        careState.bunnyId?.let(onRecordWeight)
                    } else {
                        viewModel.startCompleting(row)
                    }
                },
                onAddVisit = { careState.bunnyId?.let(onAddVisit) },
                onOpenVisit = { row -> careState.bunnyId?.let { onOpenVisit(it, row.id) } },
                onAddCourse = { careState.bunnyId?.let(onAddCourse) },
                onOpenCourse = onOpenCourse,
                onAnswer = viewModel::answer,
                modifier = modifier,
            )
    }

    if (choosingBunny) {
        ChooseBunnyDialog(
            title = stringResource(R.string.care_pick_a_bunny),
            bunnies = state.activeBunnies,
            onPick = { bunnyId ->
                choosingBunny = false
                onSelectBunny(bunnyId)
            },
            onDismiss = { choosingBunny = false },
        )
    }

    careState.completing?.let { row ->
        CompleteCareDialog(
            reminderLabel = careReminderLabel(row.scheduled.reminder),
            onConfirm = viewModel::complete,
            onDismiss = viewModel::cancelCompleting,
        )
    }
}

/**
 * A route with nothing on it: one sentence, in a card the size of a row.
 *
 * `3c`'s only change to the empty state, and it is deliberately that small — *"Add a bunny first"* is
 * already the right sentence, because the record is empty precisely because there is nobody to keep
 * records about. What the card buys is that an empty route is the same **class of object** as a full
 * one rather than loose text under the bar. No heading and no illustration: emptiness should not be
 * the most prominent thing on a screen.
 *
 * [action] is for the *All bunnies* case, which is a question rather than an emptiness — there is
 * data, it just belongs to somebody the scope has not named yet.
 */
@Composable
private fun RouteMessage(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.base, vertical = Spacing.tight)) {
        GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                action?.invoke()
            }
        }
    }
}

@Composable
private fun CareList(
    state: CareUiState,
    onAdd: () -> Unit,
    onOpen: (CareRow) -> Unit,
    onComplete: (CareRow) -> Unit,
    onAddVisit: () -> Unit,
    onOpenVisit: (VisitRow) -> Unit,
    onAddCourse: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onAnswer: (ScheduledDose, DoseStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // No `verticalArrangement`: the gaps are not even. A header sits Spacing.section from the
        // section above it and Spacing.tight from its own card, and one arrangement value cannot say
        // both. No FabClearance either — the global "+" is observation-only (ADR-0015), so this
        // route has no button floating over its last row.
        contentPadding =
            PaddingValues(
                start = Spacing.base,
                end = Spacing.base,
                top = Spacing.tight,
                bottom = Spacing.section,
            ),
    ) {
        medicationsSection(
            state = state,
            onAddCourse = onAddCourse,
            onOpenCourse = onOpenCourse,
            onAnswer = onAnswer,
        )

        careSection(state = state, onAdd = onAdd, onOpen = onOpen, onComplete = onComplete)

        visitsSection(state = state, onAdd = onAddVisit, onOpen = onOpenVisit)

        // **Last, and only where something is actually swept.** An archived bunny is never swept, so
        // a line about how reliably its notifications arrive would describe something that will not
        // happen either way (ADR-0004).
        if (!state.readOnly) {
            item(key = "caveats") { ReminderCaveats(doses = state.anyDoseReminders) }
        }
    }
}

/** The recurring care half: what comes round again, and what is due now. */
private fun LazyListScope.careSection(
    state: CareUiState,
    onAdd: () -> Unit,
    onOpen: (CareRow) -> Unit,
    onComplete: (CareRow) -> Unit,
) {
    sectionHeading("care-heading", R.string.care_reminders_heading)

    if (state.rows.isEmpty()) {
        item(key = "care-empty") { EmptySection(stringResource(R.string.care_empty)) }
    }

    itemsIndexed(state.rows, key = { _, row -> "care-${row.id}" }) { index, row ->
        GroupedCardItem(index = index, count = state.rows.size) {
            ReminderRow(
                row = row,
                readOnly = state.readOnly,
                onOpen = { onOpen(row) },
                onComplete = { onComplete(row) },
            )
        }
    }

    // No add / complete affordances at all in the archived scope, rather than affordances that
    // refuse when tapped (ADR-0004).
    if (!state.readOnly) {
        // A **text** button, where *Add a course* above it is filled: `3a` is explicit that routine
        // care is the quieter of the two, and a screen with three equally loud add buttons has no
        // primary action at all.
        item(key = "care-add") { QuietAction(R.string.care_add, onAdd) }
    }
}

/** The vet visits half (ADR-0017) — not drawn in `3a`, so the language is applied by hand. */
private fun LazyListScope.visitsSection(
    state: CareUiState,
    onAdd: () -> Unit,
    onOpen: (VisitRow) -> Unit,
) {
    sectionHeading("visits-heading", R.string.visits_heading)

    if (state.visits.isEmpty()) {
        item(key = "visits-empty") { EmptySection(stringResource(R.string.visits_empty)) }
    }

    // Prefixed, because a visit and a reminder can be two different rows sharing one UUID space and
    // `LazyColumn` keys have to be unique across the *whole* list rather than per section.
    itemsIndexed(state.visits, key = { _, row -> "visit-${row.id}" }) { index, row ->
        GroupedCardItem(index = index, count = state.visits.size) {
            VisitRow(row = row, readOnly = state.readOnly, onOpen = { onOpen(row) })
        }
    }

    if (!state.readOnly) {
        item(key = "visits-add") { QuietAction(R.string.visit_add, onAdd) }
    }
}

/**
 * A section's name, with the rhythm around it.
 *
 * [Spacing.section] above and [Spacing.tight] below, which is the 1:3 ratio that makes a header read
 * as attached downward and detached upward. Written once because this route has four of them and
 * `Spacing.kt` calls the same value above and below the most-broken thing in the before set.
 */
internal fun LazyListScope.sectionHeading(
    key: String,
    @StringRes text: Int,
    first: Boolean = false,
) {
    item(key = key) {
        // Nothing above the very first header: a section gap against the top of the screen is a
        // hole, not a rhythm.
        if (!first) Spacer(Modifier.height(Spacing.section))
        SectionHeader(stringResource(text))
        Spacer(Modifier.height(Spacing.tight))
    }
}

/**
 * What a section says when it holds nothing — **about the record, never about the bunny**
 * (ADR-0001).
 */
@Composable
internal fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.hair),
    )
}

/**
 * The quieter of the two add buttons.
 *
 * Pulled back to the text edge, as on every other row of text buttons in the redesign: a text button
 * carries its own padding, so one laid out flush looks indented against the card above it.
 */
@Composable
internal fun QuietAction(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().offset(x = -Spacing.snug).padding(top = Spacing.hair)) {
        TextButton(onClick = onClick) { Text(stringResource(label)) }
    }
}

/**
 * One reminder: what it is, how often it comes round, and when it is next due **in words**.
 *
 * The row opens the reminder, where its history and the calendar hand-off live — and, since Phase 7,
 * where deleting it lives too.
 *
 * ***Done* stays on the row, but only while something is actually due.** That is `3a`'s own rule
 * about today's doses applied one section down: a row asking a question carries its answer, and a
 * row merely reporting a date in November does not. Marking a not-yet-due job done early is still
 * possible — it is on the reminder's own screen, which is where an owner doing something unusual is
 * already heading.
 *
 * **Overdue is stated, not coloured.** The words say "2 weeks overdue"; painting them red as well
 * would be the app escalating a fact it has already reported, which is the wallpaper ADR-0001
 * rejects — and this screen carries the state indefinitely, so red would become the normal colour of
 * a row nobody has got round to.
 */
@Composable
private fun ReminderRow(
    row: CareRow,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
) {
    val resources = LocalResources.current
    val due = row.due is CareDue.Today || row.due is CareDue.Yesterday || row.due is CareDue.Overdue

    ListRow(
        title = careReminderLabel(row.scheduled.reminder),
        subtitle =
            joinFacts(
                resources,
                careIntervalLabel(row.scheduled.reminder.interval),
                careDueLabel(row.due),
            ),
        onClick = onOpen,
        trailing = {
            if (due && !readOnly) {
                TextButton(onClick = onComplete) {
                    Text(
                        stringResource(
                            if (row.completedByWeighing) R.string.care_weigh_in_action else R.string.action_done,
                        ),
                    )
                }
            } else {
                Chevron()
            }
        },
    )
}

/**
 * One visit: what it was for, and the day, the vet and the weighing on one line beneath.
 *
 * The weighing is shown because it is the visit's own record of it (ADR-0017) — **the same row** the
 * Weight screen draws, read back through the join rather than copied here. It joins the fact line
 * rather than taking a third: at 64dp a row has two lines, and where the number came from is a fact
 * about the visit, not a second thing to read.
 */
@Composable
private fun VisitRow(
    row: VisitRow,
    readOnly: Boolean,
    onOpen: () -> Unit,
) {
    val resources = LocalResources.current
    ListRow(
        title = row.reason,
        subtitle =
            joinFacts(
                resources,
                dateLabel(row.visitedOn),
                // Null both when the visit named no vet and when the vet it named has since been
                // deleted. The two are indistinguishable on screen and deliberately so: a clinic
                // closing does not make last year's visit a different record.
                row.vetName,
                row.weightGrams?.let { stringResource(R.string.visit_row_weighed, gramsLabel(it)) },
            ),
        // Read-only rows still open: an archived bunny's visits are readable and nothing more
        // (ADR-0004), and the screen behind the chevron enforces that itself.
        onClick = onOpen,
        trailing = { Chevron() },
    )
}

/**
 * Joins the facts a row has into its one subtitle line, dropping the ones it has not got.
 *
 * Takes [Resources] rather than being `@Composable`, for [app.binky.tracker.ui.bunny.joinNames]'
 * reason turned inside out: `reduce` needs a plain lambda, and a `@Composable` cannot be called from
 * one. Returns null rather than an empty string, because [ListRow] draws no second line at all for a
 * row that has nothing to say — an empty line would leave the title floating in a 64dp box.
 */
@Composable
private fun joinFacts(
    resources: Resources,
    vararg parts: String?,
): String? =
    parts
        .filterNot { it.isNullOrBlank() }
        .reduceOrNull { joined, next -> resources.getString(R.string.row_pair, joined, next) }
