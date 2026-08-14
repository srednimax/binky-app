package app.binky.tracker.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.bunny.BunnyAvatar
import app.binky.tracker.ui.bunny.BunnyDialogHost
import app.binky.tracker.ui.bunny.BunnyProfile
import app.binky.tracker.ui.bunny.dateLabel
import app.binky.tracker.ui.bunny.housematesLabel
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.MessageCard
import java.time.ZoneId

/**
 * Archived bunnies, reached from More (ADR-0004): unarchive, delete, or open the bunny read-only.
 *
 * A detail screen, so it carries its own app bar while the shell's switcher and bottom bar step
 * aside — the switcher deliberately does not list archived bunnies.
 *
 * ## Phase 7, against `4f` / `4g` / `4h`
 *
 * **The one place the grouping rule does not apply**, and `4f` says so in as many words: these stay
 * separate cards rather than becoming one grouped list. A block with three actions of its own is not
 * a row, and an inset divider between two of them would read as separating the buttons from the bunny
 * above rather than one bunny from the next.
 *
 * It is also the one list in the sweep that keeps its buttons instead of following `1d`'s move of
 * *Delete* onto the thing's own screen. There is nowhere to move them to: *Open* leads to a
 * **read-only** bunny (ADR-0004), so the destination is a screen that by definition offers no
 * actions, and giving an archived bunny an editor would undo the archive's whole point.
 *
 * `4f`'s change is the weights. *Unarchive* is primary alongside *Open* because it asks nothing and
 * only ever restores; **Delete drops to `onSurfaceVariant`** so three equal-weight peers stop
 * presenting the destructive one as an equally ordinary choice — the same argument `5a` makes about
 * the vet rows, applied where the button cannot leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedBunniesScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArchivedBunniesViewModel =
        viewModel(factory = ArchivedBunniesViewModel.Factory, extras = appViewModelExtras())
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.archived_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // The shell's Scaffold already padded everything NavDisplay renders past the status
            // bar; a TopAppBar that pads for it again leaves a status-bar-sized gap.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        if (state.profiles.isEmpty()) {
            // `4g`: the sentence was already right — it says what archiving *does*, never anything
            // about the bunnies (ADR-0001) — and the only change is that it sits in a card the size
            // of a row, so an empty route reads as the same kind of object as a full one. No action
            // in it, because there is none to offer: bunnies are archived from the bunny editor.
            MessageCard(
                text = stringResource(R.string.archived_empty),
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.base,
                    end = Spacing.base,
                    top = Spacing.tight,
                    bottom = Spacing.section,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                ArchivedBunny(
                    profile = profile,
                    onOpen = { onOpen(profile.id) },
                    onUnarchive = { viewModel.unarchive(profile.id) },
                    onDelete = { viewModel.requestDelete(profile) },
                )
            }
        }
    }

    BunnyDialogHost(
        dialog = state.dialog,
        onConfirm = viewModel::confirmDialog,
        onDismiss = viewModel::dismissDialog,
    )
}

/**
 * One archived bunny: who they are, when they were archived, who they lived with, and the three
 * things that can still be done with the record.
 *
 * The housemates line keeps full `onSurface` weight while the date drops to `onSurfaceVariant`,
 * which is `4f`'s only other instruction and is about what the line is *for*: the date is
 * bookkeeping, and *"Lived with Marzipan"* is the fact that tells the owner which rabbit this was.
 */
@Composable
private fun ArchivedBunny(
    profile: BunnyProfile,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BunnyAvatar(avatar = profile.avatar, name = profile.name)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                    profile.archivedAt?.let { archivedAt ->
                        Text(
                            text =
                                stringResource(
                                    R.string.archived_on,
                                    dateLabel(archivedAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    housematesLabel(profile.housemates)?.let {
                        // Bounded like both Home sites (Phase 7.5 §8), and this is the site where
                        // the label is longest by construction: an archived bunny's row names every
                        // housemate it kept (ADR-0004), on a card built for one line.
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Pulled back to the card's text edge: a text button carries its own padding, so a row
            // of them laid out flush looks indented against the name above.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.offset(x = -Spacing.snug),
            ) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.archived_open)) }
                // Unarchiving asks nothing; it only ever restores (ADR-0004), which is why `4f`
                // gives it the same weight as Open rather than treating it as the risky one.
                TextButton(onClick = onUnarchive) { Text(stringResource(R.string.action_unarchive)) }
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
