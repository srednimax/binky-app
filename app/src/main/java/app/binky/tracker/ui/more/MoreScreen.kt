package app.binky.tracker.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.Chevron
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.ListRow
import app.binky.tracker.ui.common.RowDivider

/**
 * More — photos (Phase 3), documents (Phase 5), settings, support (Phase 6), and the archived
 * bunnies list.
 *
 * **ADR-0015's "coming soon" escape hatch is no longer used anywhere in the app.** Rows in here were
 * where it was allowed — a dead row costs one line in a list, where a dead bottom-navigation tab
 * costs a fifth of primary navigation — and Support was the last of them. Two rows below are still
 * *inert*, which is a different thing: they are unavailable for a reason the subtitle gives.
 *
 * ## Phase 7, against `6a` / `6b`
 *
 * Six headline-sized headings with paragraphs beneath, spread down a whole screen, become **six 64dp
 * rows in one grouped card**. The change is structural only: the subtitles are the app's own words,
 * unchanged, because they carry a distinction no icon would — photos and documents belong to one
 * bunny, vets are shared by all of them. Nothing gained a count and nothing gained a badge; the
 * before set had neither, and inventing one here would put a number on a screen whose whole job is
 * to get out of the way.
 *
 * **This route has no raised card and no [app.binky.tracker.ui.common.CaveatCard]**, which `6b` says
 * out loud: a list of destinations has nothing to raise, so there is no `surfaceContainerHigh`
 * anywhere on it. Not every screen spends that budget.
 *
 * **And no [androidx.compose.material3.FloatingActionButton]**, though `6a` draws one. ADR-0015 puts
 * the global "+" on Home and Observations because it logs an *observation*, and nothing on this
 * screen is one — the same call `3a` forced on Care & Meds. Consequently nothing floats over the last
 * row and the screen owes no `FabClearance`.
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

    // Six rows fit on any phone, so a plain scrolling Column rather than a LazyColumn: nothing here
    // is ever off screen for long enough for lazy composition to buy anything, and a GroupedCard of
    // six is not the hundreds-of-rows case GroupedCardItem exists for.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
    ) {
        GroupedCard {
            MoreRow(
                title = stringResource(R.string.more_photos),
                // Inert while no bunny exists to have photos of, which is the same row as before
                // with one fewer reason to tap it.
                subtitle =
                    if (onOpenPhotos == null) {
                        needsBunny
                    } else {
                        stringResource(R.string.more_photos_summary)
                    },
                onClick = onOpenPhotos,
            )
            RowDivider()
            // Live at 1.2, and it left the "coming soon" block to get here (PLAN 5g). Bunny-scoped
            // for the same reason photos are: paperwork belongs to a rabbit, and "All bunnies" is
            // not one folder but several — so More asks which before pushing the screen.
            MoreRow(
                title = stringResource(R.string.more_documents),
                subtitle =
                    if (onOpenDocuments == null) {
                        needsBunny
                    } else {
                        stringResource(R.string.more_documents_summary)
                    },
                onClick = onOpenDocuments,
            )
            RowDivider()
            // **Never inert**, unlike photos above: the directory is app-wide (ADR-0017), so it is
            // usable with no bunny in scope and reads the same in the archived one.
            MoreRow(
                title = stringResource(R.string.more_vets),
                subtitle = stringResource(R.string.more_vets_summary),
                onClick = onOpenVets,
            )
            RowDivider()
            MoreRow(
                title = stringResource(R.string.more_archived_bunnies),
                subtitle = stringResource(R.string.more_archived_bunnies_summary),
                onClick = onOpenArchived,
            )
            RowDivider()
            MoreRow(
                title = stringResource(R.string.more_settings),
                subtitle = stringResource(R.string.more_settings_summary),
                onClick = onOpenSettings,
            )
            RowDivider()
            MoreRow(
                title = stringResource(R.string.more_support),
                subtitle = stringResource(R.string.more_support_summary),
                onClick = onOpenSupport,
            )
        }
    }
}

/**
 * A row with no [onClick] is unavailable, and its [subtitle] is what says why.
 *
 * The two halves go together and neither works alone. It used to mean "coming soon", because the
 * block under a divider at the bottom of the screen carried that word for every dimmed row in it;
 * that block is gone (Phase 6, ADR-0015), so the caller picks a subtitle that explains the greyness
 * instead — otherwise the row is dimmed and mute, which is ADR-0001's silence.
 *
 * The chevron goes with the tap, not with the row: an unavailable row leads nowhere, and drawing the
 * arrow anyway would promise a screen that is not coming. That is the sweep's row grammar read in the
 * one direction the drawings never had to show.
 */
@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = onClick != null,
        trailing = if (onClick == null) null else ({ Chevron() }),
    )
}
