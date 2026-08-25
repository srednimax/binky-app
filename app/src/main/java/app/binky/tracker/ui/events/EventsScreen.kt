package app.binky.tracker.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SectionHeader
import java.time.LocalDate

/**
 * One bunny's timeline: what is coming, then what happened (ADR-0031).
 *
 * A **detail screen off More**, per bunny, reached the same way [app.binky.tracker.ui.photos] and
 * [app.binky.tracker.ui.documents] are — and from Home's card, which is a slice of this same list.
 *
 * **Four sources, one feed, and nothing stored.** Only the *event* rows belong to this screen; the
 * vet visits, care completions and next-due dates are read from the tabs that own them, and every
 * row taps back through to its own screen rather than being editable here. That is what keeps a
 * derived list from becoming a second place to change things (see `Timeline.kt`).
 *
 * The two halves are headed rather than merely ordered. Without a heading the fold is invisible: the
 * upcoming half can legitimately end on a *past* month — an overdue nail trim stays outstanding —
 * so a reader scrolling past a month heading of "February 2026" into another "February 2026" would
 * have no way to tell what changed between them.
 *
 * [readOnly] is the archived scope (ADR-0004). The add button is **absent** rather than disabled, and
 * the rows stop being navigable at all: every destination they lead to is an editor, and a
 * disabled-looking chevron that opened one anyway would be the wrong promise twice over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    bunnyId: String,
    readOnly: Boolean,
    onBack: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenCareReminder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EventsViewModel =
        viewModel(
            // Keyed by the bunny, so switching bunnies builds a new one rather than reusing the
            // previous bunny's flows — the same reason Documents and the photo gallery key theirs.
            key = "events-$bunnyId",
            factory = EventsViewModel.factory(bunnyId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.events_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold is the one owner of window insets; padding here would double it.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.base,
                    end = Spacing.base,
                    top = Spacing.tight,
                    bottom = Spacing.section,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            if (!readOnly) {
                item(key = "add") {
                    Button(
                        onClick = onAddEvent,
                        modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                        shape = RoundedCornerShape(RecordButtonRadius),
                    ) {
                        Text(stringResource(R.string.event_add))
                    }
                }
            }

            if (state.isEmpty) {
                // Only drawn once the flows have arrived. An empty card that flashes before the
                // first emission would say "nothing here" about a bunny with ten years of history.
                if (!state.loading) {
                    item(key = "empty") { EmptyTimeline(readOnly = readOnly) }
                }
            } else {
                state.sections.forEachIndexed { index, section ->
                    val opensHalf = index == 0 || state.sections[index - 1].upcoming != section.upcoming
                    item(key = "section:${section.upcoming}:${section.month}") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                            if (opensHalf) HalfHeader(upcoming = section.upcoming)
                            SectionHeader(timelineMonthLabel(section.month))
                            GroupedCard {
                                section.entries.forEachIndexed { row, entry ->
                                    // Between rows only — the card's own edge separates the two at
                                    // the ends, which is why this is decided from the index rather
                                    // than inside the row.
                                    if (row > 0) RowDivider()
                                    TimelineRow(
                                        entry = entry,
                                        today = state.today,
                                        onOpen =
                                            if (readOnly) {
                                                null
                                            } else {
                                                {
                                                    when (entry) {
                                                        is TimelineEntry.Event -> onOpenEvent(entry.event.id)
                                                        is TimelineEntry.VetVisit ->
                                                            onOpenVisit(entry.details.visit.id)
                                                        is TimelineEntry.CareDone ->
                                                            onOpenCareReminder(entry.completion.reminderId)
                                                        is TimelineEntry.CareDue ->
                                                            onOpenCareReminder(entry.scheduled.reminder.id)
                                                    }
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * *Coming up* / *Already happened* — the fold, said out loud.
 *
 * A real heading rather than a [SectionHeader]: the month labels below it are the quiet ones, and
 * two labels at the same weight would read as one long list of months.
 */
@Composable
private fun HalfHeader(upcoming: Boolean) {
    Text(
        text = stringResource(if (upcoming) R.string.events_upcoming else R.string.events_past),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = Spacing.hair, top = Spacing.tight),
    )
}

/**
 * One dated thing, and the way back to the screen that owns it.
 *
 * A plain [ListRow]: the timeline's whole job is *telling* the owner something, so every row that
 * leads anywhere carries a chevron and nothing here answers a question inline.
 */
@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    today: LocalDate,
    onOpen: (() -> Unit)?,
) {
    ListRow(
        title = timelineTitle(entry),
        subtitle = timelineSubtitle(entry, today),
        onClick = onOpen,
        trailing = if (onOpen == null) null else ({ Chevron() }),
    )
}

/**
 * Nothing on the agenda — which is a statement about the *list*, never about a rabbit (ADR-0001).
 *
 * In the archived scope the invitation drops away and the statement becomes the whole card, which is
 * the shape Documents and the photo gallery already use for the same situation.
 */
@Composable
private fun EmptyTimeline(readOnly: Boolean) {
    MessageCard(
        title = if (readOnly) null else stringResource(R.string.events_empty),
        text = stringResource(if (readOnly) R.string.events_empty else R.string.events_empty_help),
    )
}
