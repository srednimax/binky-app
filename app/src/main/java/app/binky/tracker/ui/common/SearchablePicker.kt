package app.binky.tracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.binky.tracker.R

/** One row of a picker. [id] is what gets stored; [label] is only ever what gets shown. */
data class PickerOption(
    val id: String,
    val label: String,
)

/**
 * A searchable list with **add-your-own**, shared by the symptom picker and the breed picker.
 *
 * The two look like different features and are the same one: a list too long to scan, and a
 * vocabulary the owner extends. So the search field *is* the add field — whatever is typed filters
 * the list, and when nothing matches it becomes the thing that can be added. That is what makes "add
 * your own" impossible to miss without a second control for it.
 *
 * The two differ in exactly two ways, both parameters here:
 *
 * - **Selection.** Symptoms are a multi-select (a bunny can have several at once); breed is single,
 *   so picking one closes the dialog.
 * - **What adding means.** A symptom becomes a row in the seeded table (ADR-0010); a breed is
 *   accepted as typed onto the bunny's text column, and it is then in the list for the next bunny —
 *   which is the whole of "add your own" for a field that earns no table of its own.
 *
 * Search matters for ~50 breeds and not for 13 symptoms. It is here for both anyway: one picker with
 * a harmless extra field beats two pickers.
 *
 * @param options already sorted and already resolved into the owner's language by the caller — a
 *   built-in symptom stores no label, so only the caller can put the list in a sensible order
 *   (ADR-0010, ADR-0013).
 * @param onAddTyped what the "add" row does with the trimmed text. The caller decides whether that
 *   means a new row, an existing one unhidden, or a literal string.
 */
@Composable
fun SearchablePickerDialog(
    title: String,
    options: List<PickerOption>,
    selectedIds: Set<String>,
    multiSelect: Boolean,
    @StringRes addLabelRes: Int,
    onToggle: (PickerOption) -> Unit,
    onAddTyped: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable: the dialog survives a rotation, and retyping a half-finished breed name would be a
    // small cruelty.
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()

    val matches =
        remember(options, trimmed) {
            if (trimmed.isEmpty()) options else options.filter { it.label.contains(trimmed, ignoreCase = true) }
        }
    // Only offered when nothing already says the same thing. The repository makes the same check
    // again at write time against labels it can see — this one only keeps the row from appearing
    // when it would obviously do nothing (ADR-0010).
    val offersAdd =
        remember(options, trimmed) {
            trimmed.isNotEmpty() && options.none { it.label.equals(trimmed, ignoreCase = true) }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.picker_search_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (offersAdd) {
                    Text(
                        text = stringResource(addLabelRes, trimmed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddTyped(trimmed)
                                    query = ""
                                    if (!multiSelect) onDismiss()
                                }.padding(vertical = 12.dp),
                    )
                }

                if (matches.isEmpty() && !offersAdd) {
                    Text(
                        text = stringResource(R.string.picker_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Capped rather than free: an AlertDialog that grows past the screen puts its own
                // buttons off the bottom, and the list is what should scroll.
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { it.id }) { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggle(option)
                                        if (!multiSelect) onDismiss()
                                    },
                        ) {
                            if (multiSelect) {
                                Checkbox(checked = option.id in selectedIds, onCheckedChange = null)
                            } else {
                                RadioButton(selected = option.id in selectedIds, onClick = null)
                            }
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (multiSelect) R.string.action_done else R.string.action_cancel))
            }
        },
    )
}
