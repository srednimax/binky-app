package app.binky.tracker.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.binky.tracker.R
import app.binky.tracker.data.backup.BackupScope

/**
 * The three export scopes and what each one carries (ADR-0005).
 *
 * Shared, because the choice is made in **two** places: first-run setup, where ADR-0006 puts it so
 * that a backup buried in settings never gets made, and Backup settings, where it stays editable
 * afterwards. One picker, so the two cannot come to describe the same three scopes differently —
 * and there is no third place, because these are the only two the owner ever picks a scope in.
 */
@Composable
fun BackupScopePicker(
    scope: BackupScope,
    onSelect: (BackupScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    // selectableGroup is what makes a screen reader announce these as one set of radio buttons
    // rather than three unrelated toggles.
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackupScope.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.Top,
                // The **whole row** selects, not just the circle. Watched failing on the phone
                // during first-run setup: a two-line description beside a radio button reads as
                // part of the control, and tapping it did nothing at all. `onClick = null` on the
                // button is the Material pattern for this — the row owns the click and the
                // semantics, so a screen reader announces one radio button rather than two things.
                modifier =
                    Modifier.fillMaxWidth().selectable(
                        selected = option == scope,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
            ) {
                RadioButton(selected = option == scope, onClick = null)
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(option.helpRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
 */
@Composable
fun PhotosNotProtectedNote(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = stringResource(R.string.backup_photos_warning),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
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
