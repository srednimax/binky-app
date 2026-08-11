package app.binky.tracker.ui.observations

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.IndividualFacts
import app.binky.tracker.data.SymptomEntity
import app.binky.tracker.data.TrayFacts
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.bunny.joinNames
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.ChipRow
import app.binky.tracker.ui.common.DenseFactRow
import app.binky.tracker.ui.common.FabClearance
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.common.TagChip
import app.binky.tracker.ui.shell.ShellUiState
import app.binky.tracker.ui.weight.timeLabel
import java.time.ZoneId

/**
 * Observations — the day-grouped timeline, and the one-tap healthy day.
 *
 * Under "All bunnies" this is the **combined** timeline across every active bunny, with rows sharing
 * a group id collapsed into one entry; selecting a bunny *filters* it. Because an observation can
 * cover several bunnies at once (ADR-0008), the single-bunny view is the special case here — the
 * opposite way round from Weight (ADR-0015).
 *
 * In the archived scope it renders read-only: no "+", no healthy day, no per-row edit or delete
 * (ADR-0004).
 *
 * No Compose UI tests (ADR-0012, as in 1c); the collapse and the day grouping beneath are covered by
 * `ObservationTimelineTest`.
 */
@Composable
fun ObservationsScreen(
    shell: ShellUiState,
    /**
     * The **shell's** host, not one of this screen's own: it has to be the same Scaffold that owns
     * the "+" FAB, or the FAB lands on top of the Undo action. See `Navigation.kt`.
     */
    snackbarHostState: SnackbarHostState,
    onEditObservation: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ObservationsViewModel =
        viewModel(factory = ObservationsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Under "All bunnies" there is no bunny to pre-select a fluffle from, so both write paths ask
    // which bunny first rather than sweeping one tray fact across bunnies that share no tray
    // (ADR-0008). The single-bunny path is untouched and stays one tap.
    var choosingForHealthyDay by rememberSaveable { mutableStateOf(false) }

    HealthyDaySnackbar(
        receipt = state.receipt,
        hostState = snackbarHostState,
        onUndo = viewModel::undoHealthyDay,
        onDismiss = viewModel::dismissReceipt,
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (state.selection) {
            // Momentary, before the first database and preferences emissions arrive.
            BunnySelection.Loading -> Unit
            BunnySelection.Empty ->
                Text(
                    text = stringResource(R.string.add_a_bunny_first),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxSize().padding(Spacing.base),
                )
            else ->
                Timeline(
                    state = state,
                    onHealthyDay = {
                        val bunnyId = state.bunnyId
                        if (bunnyId != null) viewModel.logHealthyDay(bunnyId) else choosingForHealthyDay = true
                    },
                    onEdit = { entry ->
                        val participant = entry.participants.first { it.observationId == entry.id }
                        onEditObservation(participant.bunnyId, entry.id)
                    },
                    onDelete = viewModel::requestDelete,
                )
        }
    }

    if (choosingForHealthyDay) {
        ChooseBunnyDialog(
            title = stringResource(R.string.healthy_day_which_bunny),
            bunnies = shell.activeBunnies,
            onPick = { bunnyId ->
                choosingForHealthyDay = false
                viewModel.logHealthyDay(bunnyId)
            },
            onDismiss = { choosingForHealthyDay = false },
        )
    }

    state.pendingDelete?.let { entry ->
        DeleteObservationDialog(
            entry = entry,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/**
 * The snackbar that names who the healthy day covered, with **Undo** (ADR-0008).
 *
 * A `LaunchedEffect` keyed on the receipt, because `showSnackbar` suspends until the snackbar is
 * dismissed or its action is tapped — the Compose way of saying "show this, then tell me what
 * happened". There is no promise equivalent; the nearest is an effect that awaits a user event.
 */
@Composable
private fun HealthyDaySnackbar(
    receipt: HealthyDayReceipt?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val message =
        receipt?.let {
            // The flag is named beside the bunny it belongs to rather than in a trailing clause, so
            // "Bijou (weight flag) & Nugget" cannot be misread as covering both.
            val names =
                it.names.map { name ->
                    if (name in it.flaggedNames) resources.getString(R.string.healthy_day_name_flagged, name) else name
                }
            val logged = resources.getString(R.string.healthy_day_logged, joinNames(resources, names))
            // ADR-0008 wants the exclusion *and* its reason, and this is the only surface the
            // one-tap path has to put the reason on. Appended rather than shown as a second
            // snackbar: two in a row would make the owner wait to reach Undo, and the whole point
            // of the receipt is that a wrong attribution is reversible immediately.
            if (it.watchedOutNames.isEmpty()) {
                logged
            } else {
                logged + " " +
                    resources.getQuantityString(
                        R.plurals.healthy_day_excluded_watch,
                        it.watchedOutNames.size,
                        joinNames(resources, it.watchedOutNames),
                    )
            }
        }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(receipt) {
        if (message == null) return@LaunchedEffect
        val result = hostState.showSnackbar(message = message, actionLabel = undoLabel)
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onDismiss()
        }
    }
}

@Composable
private fun Timeline(
    state: ObservationsUiState,
    onHealthyDay: () -> Unit,
    onEdit: (TimelineEntry) -> Unit,
    onDelete: (TimelineEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val symptomsById = remember(state.symptoms) { state.symptoms.associateBy { it.id } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // No `verticalArrangement`: the gaps here are not even. A day's header sits Spacing.section
        // from the day above it and Spacing.tight from its own first card, which is the rhythm rule,
        // and one arrangement value cannot say both.
        contentPadding =
            PaddingValues(
                start = Spacing.base,
                end = Spacing.base,
                top = Spacing.tight,
                bottom = FabClearance,
            ),
    ) {
        if (!state.readOnly) {
            item {
                Button(
                    onClick = onHealthyDay,
                    modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                    shape = RoundedCornerShape(RecordButtonRadius),
                ) {
                    Text(stringResource(R.string.healthy_day_action))
                }
                Spacer(Modifier.height(Spacing.hair))
                // The button names what it records, because one tap commits facts on the owner's
                // behalf and they are entitled to know which (ADR-0001). Spacing.hair from the
                // button, so it reads as that button's footnote and not as the next thing down.
                HelpText(
                    text = stringResource(R.string.healthy_day_help),
                    modifier = Modifier.padding(horizontal = Spacing.hair),
                )
            }
        }

        if (state.isEmpty) {
            item {
                Spacer(Modifier.height(Spacing.section))
                Text(
                    // A statement about the record, never about the bunny (ADR-0001).
                    text = stringResource(R.string.observations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.hair),
                )
            }
        }

        state.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.date}") {
                // Nothing above the very first header in the archived scope, where there is no
                // button — a section gap against the top of the screen is a hole, not a rhythm.
                if (dayIndex > 0 || !state.readOnly) Spacer(Modifier.height(Spacing.section))
                SectionHeader(dateLabel(day.date))
                Spacer(Modifier.height(Spacing.tight))
            }
            itemsIndexed(day.entries, key = { _, entry -> entry.id }) { index, entry ->
                if (index > 0) Spacer(Modifier.height(Spacing.tight))
                EntryCard(
                    entry = entry,
                    symptomsById = symptomsById,
                    focusBunnyId = state.bunnyId,
                    readOnly = state.readOnly,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

/**
 * One entry — **one real-world moment**, however many bunnies it covered.
 *
 * The tray facts are rendered once and the individual facts per named bunny, which is the storage
 * model's tray/individual split showing through: rendering the tray per participant would produce
 * two apparently duplicate cards for the same litter tray and inflate the apparent observation count
 * on the one screen meant to give a fluffle-wide read (ADR-0008).
 */
@Composable
private fun EntryCard(
    entry: TimelineEntry,
    symptomsById: Map<String, SymptomEntity>,
    /** Whose timeline this is, or null under "All bunnies". Only affects who the card names. */
    focusBunnyId: String?,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val resources = LocalResources.current
    val hasTray = entry.tray.hasAnything()
    // Lifted out of `IndividualFactLines`, which used to decide for itself whether it had anything
    // to draw: the gap *above* a block belongs to whoever is laying the blocks out, and a Spacer
    // emitted next to a participant who renders nothing would be a hole with no row under it.
    val spoken = entry.participants.filter { it.facts.hasAnything() }

    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeLabel(entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            scopeLabel(entry, focusBunnyId, resources)?.let { TagChip(text = it, dense = true) }
        }

        if (hasTray) {
            Spacer(Modifier.height(Spacing.snug))
            // Named, so the tray facts are visibly facts about a litter tray rather than claims
            // about whichever rabbit's name is above them (ADR-0008).
            BlockHeader(
                stringResource(
                    if (entry.shared) R.string.observation_tray_section_shared else R.string.observation_tray_section,
                ),
            )
            Spacer(Modifier.height(Spacing.hair))
            TrayFactLines(entry.tray)
        }

        spoken.forEachIndexed { index, participant ->
            Spacer(Modifier.height(if (index == 0 && !hasTray) Spacing.snug else Spacing.base))
            IndividualFactLines(
                participant = participant,
                named = entry.participants.size > 1,
                symptomsById = symptomsById,
            )
        }

        if (!readOnly) {
            Spacer(Modifier.height(Spacing.base))
            HorizontalDivider()
            // Pulled back to the card's text edge, as on the trend flag: a text button carries its
            // own padding, so a row of them laid out flush looks indented against everything above.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.offset(x = -Spacing.snug),
            ) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                // Deliberately quieter than Edit. Both are here, but only one of them is what the
                // owner came to do — and this is the destructive one.
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The scope of an entry, for the chip in its card's header — **who this moment covered**, legible
 * before any of the facts below it are read. It was a sentence under the time before Phase 7.
 *
 * Null is a real answer: on one bunny's own timeline an observation of her alone needs no scope
 * declared, because the whole screen already is her.
 */
private fun scopeLabel(
    entry: TimelineEntry,
    /** Whose timeline this is, or null under "All bunnies". Only affects who the chip names. */
    focusBunnyId: String?,
    resources: Resources,
): String? {
    // Only the *others* on one bunny's own timeline — "with Bijou" on Bijou's page names her back
    // to herself. Under "All bunnies" nobody is focused, so this is everyone.
    val others = entry.participants.filterNot { it.bunnyId == focusBunnyId }.map { it.name }
    return when {
        !entry.shared -> others.takeIf { it.isNotEmpty() }?.let { joinNames(resources, it) }
        // "Observed together" comes from the group id, so a lone survivor still says it — never
        // silently downgraded to an individual observation (ADR-0008).
        others.isNotEmpty() -> resources.getString(R.string.observation_with, joinNames(resources, others))
        // ...and says it **un-named**: the housemate was deleted, or is archived and out of this
        // scope. ADR-0008 wants no tombstone of them — the marker alone keeps the record honest,
        // and inventing a name for the gap would not.
        else -> resources.getString(R.string.observation_observed_together_alone)
    }
}

/**
 * The name of one block of facts *inside* an entry's card.
 *
 * [SectionHeader]'s type, without its 4dp start inset: the card has already inset its contents, and
 * a header nudged a further 4dp would sit out of line with the rows it names.
 */
@Composable
private fun BlockHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The tray facts, **only the ones actually recorded**.
 *
 * A field nobody checked prints no line at all. Printing "Droppings: not checked" for every untouched
 * field would bury the recorded ones, and printing a default would be the manufactured "fine"
 * ADR-0001 forbids — so absence is shown by absence, and every line on screen is something the owner
 * really answered.
 */
@Composable
private fun TrayFactLines(tray: TrayFacts) {
    FactBlock {
        tray.droppingsAmount?.let {
            DenseFactRow(
                stringResource(R.string.observation_droppings_amount_label),
                label(it),
            )
        }
        tray.droppingsSize?.let { DenseFactRow(stringResource(R.string.observation_droppings_size_label), label(it)) }
        tray.droppingsForm?.let { DenseFactRow(stringResource(R.string.observation_droppings_form_label), label(it)) }
        tray.cecotropes?.let { DenseFactRow(stringResource(R.string.observation_cecotropes_label), label(it)) }
    }
}

/** True when this bunny's half of an entry has something to show. Never assume the answer is no. */
private fun IndividualFacts.hasAnything(): Boolean =
    appetite != null ||
        mood != null ||
        activity != null ||
        water != null ||
        !note.isNullOrBlank() ||
        symptomsChecked ||
        symptomIds.isNotEmpty()

/** The tray half, same question. */
private fun TrayFacts.hasAnything(): Boolean =
    droppingsAmount != null || droppingsSize != null || droppingsForm != null || cecotropes != null

@Composable
private fun IndividualFactLines(
    participant: TimelineParticipant,
    named: Boolean,
    symptomsById: Map<String, SymptomEntity>,
) {
    val facts = participant.facts
    val ticked = facts.symptomIds.mapNotNull { symptomsById[it] }
    val note = facts.note?.takeIf { it.isNotBlank() }

    // Which of the three parts are here, worked out before anything is drawn.
    //
    // Spacing.snug goes *between* two of them, so a part that turns out to be first under the name
    // must not open with one: a bunny whose whole entry is "looked for symptoms, saw none" would
    // otherwise sit the same 16dp from its own heading as from the next bunny's, and belong to
    // neither. That is the rhythm rule in `Spacing.kt`, and it cannot be answered from inside the
    // part that needs the gap — only from out here, where all three are known.
    val hasRows =
        facts.appetite != null || facts.mood != null || facts.activity != null || facts.water != null
    val hasChips = ticked.isNotEmpty()
    // The affirmative claim, and the whole reason `symptomsChecked` is a column: *looked, none
    // seen*, which no count of links could express (ADR-0010). A sentence rather than a chip,
    // because it is not a thing that was recorded — it is the absence of them.
    val hasNoneSeen = !hasChips && facts.symptomsChecked

    Column {
        if (named) {
            BlockHeader(participant.name)
            Spacer(Modifier.height(Spacing.hair))
        }

        if (hasRows) {
            FactBlock {
                facts.appetite?.let { DenseFactRow(stringResource(R.string.observation_appetite_label), label(it)) }
                facts.mood?.let { DenseFactRow(stringResource(R.string.observation_mood_label), label(it)) }
                facts.activity?.let { DenseFactRow(stringResource(R.string.observation_activity_label), label(it)) }
                facts.water?.let { DenseFactRow(stringResource(R.string.observation_water_label), label(it)) }
            }
        }

        if (hasChips) {
            if (hasRows) Spacer(Modifier.height(Spacing.snug))
            // Hay chips, not apricot: a symptom is something the *owner* ticked, and apricot is
            // reserved for what the app itself raises. Wrapping, so a long name is never cut off
            // the right edge — the same rule the editor's chips follow.
            ChipRow {
                ticked.forEach { symptom -> TagChip(text = symptomLabel(symptom)) }
            }
        }

        if (hasNoneSeen) {
            if (hasRows) Spacer(Modifier.height(Spacing.snug))
            Text(
                text = stringResource(R.string.observation_no_symptoms_seen),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (note != null) {
            if (hasRows || hasChips || hasNoneSeen) Spacer(Modifier.height(Spacing.snug))
            Text(text = note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The attribute pairs of one observation, as a single block.
 *
 * No dividers, deliberately: a divider separates rows that are independent of each other, and four
 * facts about the same tray at the same moment are not. [Spacing.hair] between them rather than the
 * drawing's 2dp, which is below the 4dp grid `Spacing` commits the whole app to.
 */
@Composable
private fun FactBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair), content = content)
}

/**
 * **One** confirmation, naming who it affects.
 *
 * This deletes every participant's row — "that observation was wrong", one event about one moment.
 * Removing a single bunny from it is the form's participant edit, a different claim entirely
 * (ADR-0008).
 */
@Composable
private fun DeleteObservationDialog(
    entry: TimelineEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val names = joinNames(resources, entry.participants.map { it.name })
    BinkyDialog(
        title = stringResource(R.string.observation_delete_title),
        onDismiss = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        Text(
            if (entry.shared) {
                stringResource(R.string.observation_delete_body_shared, names)
            } else {
                stringResource(R.string.observation_delete_body, names)
            },
        )
    }
}
