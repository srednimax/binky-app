package app.binky.tracker.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.binky.tracker.R
import app.binky.tracker.data.backup.BackupScope
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.ListRowHeight
import app.binky.tracker.ui.common.NestedCardRadius
import app.binky.tracker.ui.common.RowDivider

/**
 * The three export scopes and what each one carries (ADR-0005).
 *
 * Shared, because the choice is made in **two** places: first-run setup, where ADR-0006 puts it so
 * that a backup buried in settings never gets made, and Backup settings, where it stays editable
 * afterwards. One picker, so the two cannot come to describe the same three scopes differently —
 * and there is no third place, because these are the only two the owner ever picks a scope in.
 *
 * ## Phase 7, against `6c` / `6d`
 *
 * **Rows of a [app.binky.tracker.ui.common.GroupedCard], not a control that draws its own** — the
 * caller supplies the card, because on Backup these three rows share it with the photo warning and
 * the *Export* button. Give that card `contentPadding = PaddingValues(0.dp)`: a selected row is a
 * full-bleed fill, and it is the card's own rounded corners that are meant to clip it at the ends.
 *
 * **The selection is a fill, not a tint of `primary`** — `surfaceContainerHigh`, the same step up the
 * automatic-backup card takes, which is `6d`'s "one mechanism, reused". It is what makes the chosen
 * scope legible without reading all three descriptions, which is the whole complaint the drawing has
 * about the before set.
 */
@Composable
fun BackupScopePicker(
    scope: BackupScope,
    onSelect: (BackupScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    // selectableGroup is what makes a screen reader announce these as one set of radio buttons
    // rather than three unrelated toggles.
    Column(modifier = modifier.selectableGroup()) {
        BackupScope.entries.forEachIndexed { index, option ->
            val selected = option == scope
            // Between rows only — the card's own edge separates the two at the ends — and never
            // against a filled band, on **either** of its sides. The fill's own edge is already the
            // seam, and a divider along one side of it and not the other reads as a rendering fault
            // rather than as a choice (which is exactly how the first device pass looked).
            if (index > 0 && !selected && BackupScope.entries[index - 1] != scope) RowDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
                // The **whole row** selects, not just the circle. Watched failing on the phone
                // during first-run setup: a two-line description beside a radio button reads as
                // part of the control, and tapping it did nothing at all. `onClick = null` on the
                // button is the Material pattern for this — the row owns the click and the
                // semantics, so a screen reader announces one radio button rather than two things.
                //
                // `background` before `selectable` so the ripple lands on top of the fill rather
                // than under it. Compose applies modifiers outside-in, which is the opposite of the
                // intuition CSS gives you.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        ).selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ).heightIn(min = ListRowHeight)
                        .padding(horizontal = Spacing.base, vertical = Spacing.snug),
            ) {
                RadioButton(selected = selected, onClick = null)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                    Text(
                        text = stringResource(option.labelRes),
                        // The chosen scope carries the weight as well as the fill: at a glance down
                        // the card the two say the same thing, which is what makes it readable
                        // without colour alone (and in the accessibility settings that flatten it).
                        style =
                            if (selected) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                    )
                    HelpText(stringResource(option.helpRes))
                }
            }
        }
    }
}

/**
 * The gap in the net, **said out loud rather than merely implemented** (ADR-0005).
 *
 * Photos are outside Android's automatic backup, outside Essential and outside Records, so an owner
 * who was never told that will reasonably assume the net covers everything — and will find out it
 * did not at the one moment nothing can be done about it.
 *
 * `6c` moves it **inside the export block, above the button it qualifies** — it used to float between
 * two sections belonging to neither — and keeps it on a surface of its own so it does not read as one
 * more scope description. [NestedCardRadius], because a card inside a card matching its parent's
 * corner reads as a rendering mistake.
 */
@Composable
fun PhotosNotProtectedNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NestedCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.backup_photos_warning),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(Spacing.snug),
        )
    }
}

/**
 * The scope's name, for the picker, the restore confirmation and the restore report alike.
 *
 * `internal` rather than private to a screen: the setup wizard is in another package and names the
 * same three scopes, and a second `when` over the enum would be a second thing to update when a
 * fourth scope arrives.
 */
internal val BackupScope.labelRes: Int
    get() =
        when (this) {
            BackupScope.Essential -> R.string.backup_scope_essential
            BackupScope.Records -> R.string.backup_scope_records
            BackupScope.Everything -> R.string.backup_scope_everything
        }

internal val BackupScope.helpRes: Int
    get() =
        when (this) {
            BackupScope.Essential -> R.string.backup_scope_essential_help
            BackupScope.Records -> R.string.backup_scope_records_help
            BackupScope.Everything -> R.string.backup_scope_everything_help
        }
