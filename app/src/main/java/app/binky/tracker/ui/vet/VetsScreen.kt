package app.binky.tracker.ui.vet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.VetEntity
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.ListRowHeight
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.common.RecordButtonHeight
import app.binky.tracker.ui.common.RecordButtonRadius
import app.binky.tracker.ui.common.RowDivider

/**
 * The vet directory: every vet the household uses, in one list (ADR-0017).
 *
 * A **detail screen off More**, not a tab and not per bunny — a clinic's phone number belongs to the
 * household rather than to one rabbit, and a directory per bunny would make the owner type it in
 * twice and then keep two copies in step by hand.
 *
 * Nothing here is scoped by the selected bunny, so it renders the same in the archived scope as
 * anywhere else: the entry outlives the visits (ADR-0017), and it outlives an archive too.
 *
 * ## Phase 7, against `5a` / `5b`
 *
 * Two vets used to take two thirds of the screen: each was a floating card ending in an internal
 * divider and two equal-weight text buttons. They are one grouped card with inset dividers now, so
 * a directory reads as a directory — and a vet with no phone and no note simply has fewer lines
 * rather than a card of the same height with gaps in it, because the divider is what separates them.
 *
 * **The phone number keeps full `onSurface` weight in both themes while the clinic drops to
 * `onSurfaceVariant`**, which is `5b`'s one instruction and the only piece of hierarchy on the
 * screen that is about urgency rather than taste: at two in the morning with a rabbit in stasis, the
 * number is the only thing here that matters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetsScreen(
    onBack: () -> Unit,
    onAddVet: () -> Unit,
    onEditVet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VetsViewModel = viewModel(factory = VetsViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.vets_title)) },
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
            // Above the list it appends to, which is where it already was — `5a` keeps the placement
            // and only changes the weight. This is the one action the route exists for, so it takes
            // the full-width primary shape rather than sitting in a row of peers.
            item(key = "add") {
                Button(
                    onClick = onAddVet,
                    modifier = Modifier.fillMaxWidth().height(RecordButtonHeight),
                    shape = RoundedCornerShape(RecordButtonRadius),
                ) {
                    Text(stringResource(R.string.vet_add))
                }
            }

            if (state.vets.isEmpty()) {
                // About the *directory*, never about a rabbit (ADR-0001), and in a card so that an
                // empty route is the same class of object as a full one.
                item(key = "empty") { MessageCard(stringResource(R.string.vets_empty)) }
            } else {
                item(key = "directory") {
                    GroupedCard {
                        state.vets.forEachIndexed { index, vet ->
                            // Between rows only. The card's own edge separates the two at the ends,
                            // which is why this is decided from the index rather than from inside
                            // the row — a row cannot tell whether it is the first one.
                            if (index > 0) RowDivider()
                            VetRow(vet = vet, onOpen = { onEditVet(vet.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One vet: the name, then whatever else the owner recorded, then the chevron into their entry.
 *
 * Not a [app.binky.tracker.ui.common.ListRow], which carries a title and **one** fact line. A vet is
 * up to four lines at three different weights, and the weights are the point — see `5b` on the phone
 * number. It borrows [ListRowHeight] so a name-only entry keeps the same floor as every other row in
 * the app.
 *
 * **Every line but the name is drawn only when it has something in it.** A card of empty labels reads
 * as missing data rather than as a short entry, and `5a` is explicit that an uneven block is not a
 * hole here because the divider is doing the separating.
 *
 * The row opens the entry; **deleting lives there**, not here. That is `1d`'s finding — the same one
 * that moved deleting a weighing, a course, a reminder and a visit onto their own screens — applied
 * to the last list-plus-editor pair in the app. `5a` draws *Edit* and *Delete* on the row and argues
 * in its own note that Delete should stop being Edit's equal-weight peer; taking it off the row
 * altogether is that argument carried through, and it leaves one grammar for every list in the app:
 * a row that is only *telling* you something carries a chevron.
 */
@Composable
private fun VetRow(
    vet: VetEntity,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .heightIn(min = ListRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.snug),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(text = vet.name, style = MaterialTheme.typography.titleMedium)
            vet.clinic?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vet.phone?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            vet.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Chevron()
    }
}
