package app.binky.tracker.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R

/**
 * More — photos (Phase 3), documents (Phase 5), settings, support (Phase 6), and the archived
 * bunnies list.
 *
 * **ADR-0015's "coming soon" escape hatch is no longer used anywhere in the app.** Rows in here were
 * where it was allowed — a dead row costs one line in a list, where a dead bottom-navigation tab
 * costs a fifth of primary navigation — and Support was the last of them. Two rows below are still
 * *inert*, which is a different thing: they are unavailable for a reason the subtitle gives.
 */
@Composable
fun MoreScreen(
    onOpenArchived: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVets: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenPhotos: (() -> Unit)?,
    onOpenDocuments: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The subtitle an inert row wears. It replaces the summary rather than joining it: a row that
    // describes what it would do, dimmed, explains nothing about why it cannot be tapped — which is
    // ADR-0001's silence on the one screen that used to have a word for it. The failing case is an
    // owner who archived their last bunny, not a fresh install.
    val needsBunny = stringResource(R.string.more_needs_bunny)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        MoreRow(
            title = stringResource(R.string.more_photos),
            // Inert while no bunny exists to have photos of, which is the same row as before with
            // one fewer reason to tap it.
            subtitle = if (onOpenPhotos == null) needsBunny else stringResource(R.string.more_photos_summary),
            onClick = onOpenPhotos,
        )
        // Live at 1.2, and it left the "coming soon" block to get here (PLAN 5g). Bunny-scoped for
        // the same reason photos are: paperwork belongs to a rabbit, and "All bunnies" is not one
        // folder but several — so More asks which before pushing the screen.
        MoreRow(
            title = stringResource(R.string.more_documents),
            subtitle = if (onOpenDocuments == null) needsBunny else stringResource(R.string.more_documents_summary),
            onClick = onOpenDocuments,
        )
        // **Never inert**, unlike photos above: the directory is app-wide (ADR-0017), so it is
        // usable with no bunny in scope and reads the same in the archived one.
        MoreRow(
            title = stringResource(R.string.more_vets),
            subtitle = stringResource(R.string.more_vets_summary),
            onClick = onOpenVets,
        )
        MoreRow(
            title = stringResource(R.string.more_archived_bunnies),
            subtitle = stringResource(R.string.more_archived_bunnies_summary),
            onClick = onOpenArchived,
        )
        MoreRow(
            title = stringResource(R.string.more_settings),
            subtitle = stringResource(R.string.more_settings_summary),
            onClick = onOpenSettings,
        )
        MoreRow(
            title = stringResource(R.string.more_support),
            subtitle = stringResource(R.string.more_support_summary),
            onClick = onOpenSupport,
        )
    }
}

/**
 * A row with no [onClick] is unavailable, and its [subtitle] is what says why.
 *
 * The two halves go together and neither works alone. It used to mean "coming soon", because the
 * block under a divider at the bottom of the screen carried that word for every dimmed row in it;
 * that block is gone (Phase 6, ADR-0015), so the caller now picks a subtitle that explains the
 * greyness instead — otherwise the row is dimmed and mute, which is ADR-0001's silence.
 */
@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (onClick != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
