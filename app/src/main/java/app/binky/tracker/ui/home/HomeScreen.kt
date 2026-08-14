package app.binky.tracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.BunnySelection
import app.binky.tracker.data.WatchDuration
import app.binky.tracker.data.WatchState
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.BunnyAvatar
import app.binky.tracker.ui.bunny.BunnyDialogHost
import app.binky.tracker.ui.bunny.BunnyProfile
import app.binky.tracker.ui.bunny.ageLabel
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.bunny.housematesLabel
import app.binky.tracker.ui.bunny.neuterLabel
import app.binky.tracker.ui.bunny.sexLabel
import app.binky.tracker.ui.common.FabClearance
import app.binky.tracker.ui.common.FactRow
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.RowDivider
import app.binky.tracker.ui.common.SectionHeader
import app.binky.tracker.ui.watch.StartWatchAction
import app.binky.tracker.ui.watch.WatchActiveCard
import app.binky.tracker.ui.weight.TrendFlagBanner
import app.binky.tracker.ui.weight.instantDateLabel
import app.binky.tracker.ui.weight.showsBanner
import app.binky.tracker.ui.weight.weightLabel

/**
 * Home — the selected bunny's profile, and under "All bunnies" the fluffle dashboard (ADR-0015).
 *
 * The dashboard **is the bunny list**; there is deliberately no separate list screen, because two
 * screens rendering the same rows would diverge the moment one of them gained a field.
 *
 * Phase 7 redraws all three states against mockups `1b`/`1c`, `4a`/`4b` and `4c`/`4c2`. Nothing was
 * added or taken away: the same facts, the same actions, the same words. What changed is that the
 * loose label/value lines became rows in a grouped card, the flag stopped being a coloured panel,
 * and the screen picked up the spacing rhythm — hero, then sections 24dp apart with their headers
 * 8dp above their content.
 */
@Composable
fun HomeScreen(
    onAddBunny: () -> Unit,
    onEditBunny: (String) -> Unit,
    onSelectBunny: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.selection) {
        // Momentary, before the first database and preferences emissions arrive. Rendering nothing
        // beats flashing an empty state at an owner who has bunnies.
        BunnySelection.Loading -> Unit
        BunnySelection.Empty -> NoBunniesYet(onAddBunny, modifier)
        BunnySelection.All ->
            AllBunnies(
                state = state,
                onSelectBunny = onSelectBunny,
                onEditBunny = onEditBunny,
                onAcknowledge = viewModel::acknowledge,
                onStartWatch = viewModel::startWatch,
                onCloseWatch = viewModel::closeWatch,
                modifier = modifier,
            )
        else ->
            state.profiles.firstOrNull()?.let { profile ->
                OneBunny(
                    profile = profile,
                    vitals = state.vitalsFor(profile.id),
                    unit = state.unit,
                    readOnly = state.readOnly,
                    onEdit = { onEditBunny(profile.id) },
                    onArchive = { viewModel.requestArchive(profile) },
                    onDelete = { viewModel.requestDelete(profile) },
                    onAcknowledge = { viewModel.acknowledge(profile.id) },
                    onStartWatch = { duration -> viewModel.startWatch(profile.id, duration) },
                    onCloseWatch = { viewModel.closeWatch(profile.id) },
                    modifier = modifier,
                )
            }
    }

    BunnyDialogHost(
        dialog = state.dialog,
        onConfirm = viewModel::confirmDialog,
        onDismiss = viewModel::dismissDialog,
    )
}

/**
 * The first-run screen: a heading, one sentence, one button (`4c`).
 *
 * **The only empty state in the app with a heading**, and it keeps one because there is nothing else
 * on the screen for a heading to compete with. No second action and no paragraph explaining what
 * Binky is — the owner has just installed it.
 *
 * The tabs underneath are deliberately *not* dimmed. A navigation bar drawn as disabled on first run
 * reads as a broken install, and the app does not actually disable them.
 */
@Composable
private fun NoBunniesYet(
    onAddBunny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.base)) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(Spacing.tight))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.base))
        // Hug-width and taller than Material's default, sitting under the sentence rather than
        // stretched across the screen: the three elements have to read as one block, and a
        // full-width button on an otherwise empty screen reads as a form control. This is the app's
        // one filled button and the only saturated thing here, which is the point of it.
        Button(
            onClick = onAddBunny,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(horizontal = 28.dp),
        ) {
            Text(stringResource(R.string.switcher_add_bunny))
        }
    }
}

/** One bunny's profile: who they are, who they live with, and what can be done to the record. */
@Composable
private fun OneBunny(
    profile: BunnyProfile,
    vitals: BunnyVitals,
    unit: WeightUnit,
    readOnly: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAcknowledge: () -> Unit,
    onStartWatch: (WatchDuration) -> Unit,
    onCloseWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both are decided here rather than inside the two composables that render them, because the
    // spacing above each one depends on whether it draws at all. A `Spacer` emitted for a banner
    // that renders nothing is a hole that appears only for the bunnies with no flag — which is
    // almost all of them, almost all of the time.
    val hasFlag = vitals.flag.showsBanner()
    val hasWatch = !readOnly && vitals.watch is WatchState.Active

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base)
                .padding(top = Spacing.tight, bottom = FabClearance),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BunnyAvatar(avatar = profile.avatar, name = profile.name, size = 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                Text(text = profile.name, style = MaterialTheme.typography.headlineSmall)
                ageLabel(profile.birthDate, profile.birthDateApproximate)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                housematesLabel(profile.housemates)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The flag comes first because it is the one thing on this screen the owner has not already
        // told the app.
        if (hasFlag) {
            Spacer(Modifier.height(Spacing.block))
            TrendFlagBanner(
                bunnyName = profile.name,
                flag = vitals.flag,
                unit = unit,
                onAcknowledge = onAcknowledge,
                secondaryAction = watchAction(profile.name, vitals, readOnly, onStartWatch),
                // ADR-0028's age question, which draws only on a gain raised with no birthday on
                // file. It goes to the same editor *Edit* below does — one destination, so
                // answering it is the field the owner was already able to fill in.
                onAskAge = onEdit,
            )
        }
        if (hasWatch) {
            // Tight under the flag when both are up — they are one thought, and the watch is
            // usually the thing the flag talked the owner into.
            Spacer(Modifier.height(if (hasFlag) Spacing.tight else Spacing.block))
            WatchLine(vitals = vitals, readOnly = readOnly, onClose = onCloseWatch)
        }

        Spacer(Modifier.height(if (hasFlag || hasWatch) Spacing.section else Spacing.block))
        SectionHeader(stringResource(R.string.home_about_bunny, profile.name))
        Spacer(Modifier.height(Spacing.tight))
        GroupedCard {
            // Built as a list first so the dividers can go *between* rows without every optional
            // field having to know whether it is the last one. Kotlin note: `buildList` is inline,
            // so `stringResource` — a composable call — is still legal inside it.
            val facts =
                buildList {
                    add(stringResource(R.string.home_last_weight_label) to lastWeighingValue(vitals, unit))
                    add(stringResource(R.string.home_last_observation_label) to lastObservationValue(vitals))
                    add(stringResource(R.string.bunny_sex_label) to sexLabel(profile.sex))
                    add(stringResource(R.string.bunny_neutered_label) to neuterLabel(profile.neutered))
                    // Only an *exact* birthdate is ever shown as a date; an approximate one is an age
                    // and nothing more, or the app invents a precision the owner never had (ADR-0016).
                    if (profile.birthDate != null && !profile.birthDateApproximate) {
                        add(stringResource(R.string.bunny_birthdate_label) to dateLabel(profile.birthDate))
                    }
                    profile.breed?.let { add(stringResource(R.string.bunny_breed_label) to it) }
                    profile.colour?.let { add(stringResource(R.string.bunny_colour_label) to it) }
                }
            facts.forEachIndexed { index, (label, value) ->
                if (index > 0) RowDivider()
                FactRow(label = label, value = value)
            }
        }

        if (!readOnly) {
            Spacer(Modifier.height(Spacing.section))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                // Edit is the tonal one: it is the action an owner opens this screen to take.
                // Delete stays the quietest of the three — a destructive action does not need
                // colour to be found, and colour here would invite the tap.
                FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onArchive) { Text(stringResource(R.string.action_archive)) }
                TextButton(
                    onClick = onDelete,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

/**
 * The last weighing, or the plain fact that there is none.
 *
 * "No weighings yet" is a statement about the **record**, never about the bunny: silence means
 * nobody looked, and the app must not let an empty series read as reassurance (ADR-0001).
 */
@Composable
private fun lastWeighingValue(
    vitals: BunnyVitals,
    unit: WeightUnit,
): String {
    val grams = vitals.lastGrams
    val recordedAt = vitals.lastRecordedAt
    return if (grams == null || recordedAt == null) {
        stringResource(R.string.home_no_weighings)
    } else {
        stringResource(
            R.string.home_last_weight_value,
            weightLabel(grams, unit),
            instantDateLabel(recordedAt),
        )
    }
}

/**
 * When anything was last noticed about this bunny.
 *
 * "None recorded yet" is a statement about the **record**, not about the bunny. Silence means nobody
 * looked, and it must never be shown as though it meant nothing was wrong (ADR-0001).
 */
@Composable
private fun lastObservationValue(vitals: BunnyVitals): String =
    vitals.lastObservationAt?.let { instantDateLabel(it) }
        ?: stringResource(R.string.home_no_observations)

/**
 * The active watch, where it is running (ADR-0001) — *"Watch active · 4 days left"*, with
 * close-early beside it. Nothing at all when no watch is running, which is almost always.
 *
 * Absent in the read-only scope: an archived bunny has no watch, because archiving closes it.
 */
@Composable
private fun WatchLine(
    vitals: BunnyVitals,
    readOnly: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = vitals.watch as? WatchState.Active ?: return
    if (readOnly) return
    WatchActiveCard(active = active, onClose = onClose, modifier = modifier)
}

/**
 * The flag's secondary slot, filled or empty.
 *
 * Empty while a watch is already running — offering to start one on top of one that is running
 * would be a button that says nothing true — and empty in the read-only scope, which writes nothing.
 */
private fun watchAction(
    bunnyName: String,
    vitals: BunnyVitals,
    readOnly: Boolean,
    onStartWatch: (WatchDuration) -> Unit,
): (@Composable () -> Unit)? =
    if (readOnly || vitals.watch is WatchState.Active) {
        null
    } else {
        { StartWatchAction(bunnyName = bunnyName, onStart = onStartWatch) }
    }

/**
 * The fluffle dashboard — **one vitals card per active bunny** (ADR-0015). The dashboard *is* the
 * bunny list; there is deliberately no separate list screen.
 */
@Composable
private fun AllBunnies(
    state: HomeUiState,
    onSelectBunny: (String) -> Unit,
    onEditBunny: (String) -> Unit,
    onAcknowledge: (String) -> Unit,
    onStartWatch: (String, WatchDuration) -> Unit,
    onCloseWatch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.base),
        contentPadding = PaddingValues(top = Spacing.tight, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_all_bunnies_stub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.hair, bottom = Spacing.tight),
            )
        }
        // Kotlin note: `items(list) { }` is the LazyColumn equivalent of `list.map(...)` in JSX —
        // except only the visible rows are composed, so a long list stays cheap.
        items(state.profiles, key = { it.id }) { profile ->
            BunnyCard(
                profile = profile,
                vitals = state.vitalsFor(profile.id),
                unit = state.unit,
                readOnly = state.readOnly,
                onOpen = { onSelectBunny(profile.id) },
                onAskAge = { onEditBunny(profile.id) },
                onAcknowledge = { onAcknowledge(profile.id) },
                onStartWatch = { duration -> onStartWatch(profile.id, duration) },
                onCloseWatch = { onCloseWatch(profile.id) },
            )
        }
    }
}

/**
 * One bunny on the dashboard (`4a`).
 *
 * **The flag stays inside the bunny it belongs to**, in full rather than compressed to a badge: at
 * this level the owner can act on it — acknowledge it, start a watch — without opening the rabbit
 * first, and a dot would take that away to save four lines. It steps up one surface rather than
 * becoming a coloured panel, which is the same rule the flag follows everywhere else.
 *
 * A bunny with no flag simply has a shorter card, and **that difference is the signal**. Nothing is
 * drawn to say "no flag" — an absent flag is not evidence of anything (ADR-0001).
 */
@Composable
private fun BunnyCard(
    profile: BunnyProfile,
    vitals: BunnyVitals,
    unit: WeightUnit,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onAskAge: () -> Unit,
    onAcknowledge: () -> Unit,
    onStartWatch: (WatchDuration) -> Unit,
    onCloseWatch: () -> Unit,
) {
    val hasFlag = vitals.flag.showsBanner()
    val hasWatch = !readOnly && vitals.watch is WatchState.Active

    GroupedCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base)
                    .padding(top = Spacing.snug, bottom = Spacing.tight),
        ) {
            BunnyAvatar(avatar = profile.avatar, name = profile.name, size = 52.dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                housematesLabel(profile.housemates)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // No content description: the whole card is one clickable target already named by the
            // bunny's name and everything under it, and a second announcement would only repeat it.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        RowDivider()
        FactRow(
            label = stringResource(R.string.home_last_weight_label),
            value = lastWeighingValue(vitals, unit),
        )
        RowDivider()
        FactRow(
            label = stringResource(R.string.home_last_observation_label),
            value = lastObservationValue(vitals),
        )
        if (hasWatch) {
            Spacer(Modifier.height(Spacing.snug))
            WatchLine(
                vitals = vitals,
                readOnly = readOnly,
                onClose = onCloseWatch,
                modifier = Modifier.padding(horizontal = Spacing.base),
            )
        }
        if (hasFlag) {
            Spacer(Modifier.height(Spacing.tight))
            TrendFlagBanner(
                bunnyName = profile.name,
                flag = vitals.flag,
                unit = unit,
                onAcknowledge = onAcknowledge,
                nested = true,
                modifier = Modifier.padding(horizontal = Spacing.base),
                secondaryAction = watchAction(profile.name, vitals, readOnly, onStartWatch),
                onAskAge = onAskAge,
            )
        }
        if (hasFlag || hasWatch) Spacer(Modifier.height(Spacing.snug))
    }
}
