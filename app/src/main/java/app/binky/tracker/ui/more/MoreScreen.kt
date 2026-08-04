package app.binky.tracker.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R

/**
 * More — photos (Phase 3), documents (Phase 5), settings, support, and the archived bunnies list.
 *
 * Rows inside here are where ADR-0015 allows a "coming soon": a dead row costs one line in a list,
 * where a dead bottom-navigation tab costs a fifth of primary navigation.
 */
@Composable
fun MoreScreen(
    onOpenArchived: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVets: () -> Unit,
    onOpenPhotos: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        MoreRow(
            title = stringResource(R.string.more_photos),
            subtitle = stringResource(R.string.more_photos_summary),
            // Inert while no bunny exists to have photos of, which is the same row as before with
            // one fewer reason to tap it.
            onClick = onOpenPhotos,
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
        HorizontalDivider()
        val comingSoon = stringResource(R.string.more_coming_soon)
        MoreRow(title = stringResource(R.string.more_documents), subtitle = comingSoon)
        MoreRow(title = stringResource(R.string.more_support), subtitle = comingSoon)
    }
}

/** A row with no [onClick] is one of ADR-0015's "coming soon" entries: visible, and inert. */
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
