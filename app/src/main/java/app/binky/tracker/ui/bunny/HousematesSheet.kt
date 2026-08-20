package app.binky.tracker.ui.bunny

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing

/**
 * 56dp, the same row height the switcher's menu uses: these rows do the same job — telling two
 * rabbits apart — and carry the same 40dp avatar.
 */
private val HousemateRowHeight = 56.dp

/**
 * *Lives with* — the whole fluffle, one row each, tappable through to the bunny (Phase 9f).
 *
 * The line on the profile names two housemates and folds the rest into "& N others"
 * ([capHousemates]), which is right for a line but leaves the folded names **unreachable
 * everywhere in the app**. This is where they are reachable, and the reason it is a sheet rather
 * than a tooltip is that a tooltip cannot be tapped *through*: seeing the names is half of what the
 * owner wanted, and getting to that rabbit is the other half.
 *
 * **No new strings.** The title is the label the profile already uses for this line and the
 * archived suffix is the one [housematesLabel] already applies, both translated in all nine
 * languages — so this screen owes the translation gate nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousematesSheet(
    housemates: List<Housemate>,
    onOpenHousemate: (Housemate) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // **Skip the half-height state.** A sheet whose content is taller than half the screen opens
        // partially expanded by default, and in landscape a fluffle of four opens showing two —
        // which is the *same* two the line already named, one drag away from the ones it did not.
        // Watched on the phone at 9f: two rows visible in landscape until the handle was dragged.
        // Expanded is the content's own height, so nothing changes in portrait.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // **A sheet is its own window, so the shell's Scaffold pads none of this** (PLAN 4f) — the
        // last row would otherwise sit under the navigation bar. The scroll is the other half: a
        // fluffle large enough to outgrow a landscape screen has nowhere else to go once the
        // expanded height is capped, and a sheet that cannot scroll simply loses whoever did not
        // fit — which would put the names back out of reach. Four fit; this is the guard above.
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.base),
        ) {
            Text(
                text = stringResource(R.string.bunny_lives_with_label),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
            )
            housematesInSheet(housemates).forEach { housemate ->
                HousemateRow(housemate = housemate, onClick = { onOpenHousemate(housemate) })
            }
        }
    }
}

/**
 * One housemate: avatar, name, and *(archived)* where it applies.
 *
 * The archived ones are **marked rather than sunk** — they keep their place in the order the
 * fluffle arrived in and stay tappable. Archiving is not a deletion (ADR-0004), and this is a list
 * of who a rabbit has lived with, not only of who is still here.
 */
@Composable
private fun HousemateRow(
    housemate: Housemate,
    onClick: () -> Unit,
) {
    Row(
        // `clickable` before the padding, so the ripple covers the whole row rather than only the
        // text — the same order [app.binky.tracker.ui.common.ListRow] uses.
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = HousemateRowHeight)
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BunnyAvatar(avatar = housemate.avatar, name = housemate.name, size = 40.dp)
        Text(
            text =
                if (housemate.archived) {
                    stringResource(R.string.bunny_archived_name, housemate.name)
                } else {
                    housemate.name
                },
            style = MaterialTheme.typography.titleMedium,
            // The archived ones step back a shade. That is the whole of the difference: they are
            // still a row and still open, because an owner looking for who Hazel lived with is
            // often looking for Hazel.
            color =
                if (housemate.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
