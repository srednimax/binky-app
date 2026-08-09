package app.binky.tracker.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.data.WeightUnit
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.ErrorText
import app.binky.tracker.ui.common.FieldRadius
import app.binky.tracker.ui.common.GroupedCard
import app.binky.tracker.ui.common.HelpText
import app.binky.tracker.ui.common.RecordedAtField
import app.binky.tracker.ui.watch.StartWatchAction
import java.time.Instant

/**
 * Add or edit one weighing — the route `NavigationKeys.kt` promised from Phase 1 and never built.
 *
 * A **detail** screen like the bunny editor: pushed onto the back stack with its own app bar, so the
 * shell's switcher and bottom bar step aside while it is open. The global "+" is deliberately not a
 * way in here — it stays observation-only (ADR-0015).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntryScreen(
    bunnyId: String,
    weightId: String?,
    onOpenVisit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WeightEntryViewModel =
        viewModel(
            key = "weight-entry-${weightId ?: "new"}",
            factory = WeightEntryViewModel.factory(bunnyId, weightId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.weight_entry_add_title else R.string.weight_entry_edit_title,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            // No Save on a visit-recorded weighing (ADR-0017): the visit owns that number, so the
            // action is to go there rather than one that refuses when tapped.
            actions = {
                if (state.visitId == null) {
                    TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
                }
            },
            // The shell's Scaffold is the one owner of window insets; padding here would double it.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.base,
                        end = Spacing.base,
                        top = Spacing.tight,
                        bottom = Spacing.section,
                    ),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            // The whole form is read-only behind this, and the line says why rather than leaving
            // the owner to discover that nothing here takes a keystroke.
            state.visitId?.let { visitId ->
                GroupedCard(contentPadding = PaddingValues(Spacing.base)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.snug)) {
                        Text(
                            text = stringResource(R.string.weight_visit_owned),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Pulled back to the card's text edge: a text button carries its own
                        // padding, so one laid out flush looks indented against the line above it.
                        Row(modifier = Modifier.offset(x = -Spacing.snug)) {
                            TextButton(onClick = { onOpenVisit(visitId) }) {
                                Text(stringResource(R.string.weight_open_visit))
                            }
                        }
                    }
                }
            }
            GramsField(state = state, onGramsChanged = viewModel::onGramsChanged)
            // Shared with the observation form: back-dating allowed, the future refused with the
            // reason stated. Only the wording differs between the two.
            RecordedAtField(
                label = stringResource(R.string.weight_recorded_at_label),
                helpText = stringResource(R.string.weight_backdating_help),
                futureRejectedText = stringResource(R.string.weight_future_rejected),
                date = state.date,
                time = state.time,
                inFuture = state.inFuture,
                onDateChanged = viewModel::onDateChanged,
                onTimeChanged = viewModel::onTimeChanged,
                // A visit's weighing is stamped from the visit's date — `min(noon, now)` — so
                // changing it here would be a second path to a derived fact (ADR-0017).
                enabled = state.visitId == null,
            )
            // Deleting lives here rather than on the history list, where Phase 7's row carries a
            // value, a timestamp, a change and a chevron and nothing else. The quietest button on
            // the screen: a destructive action does not need colour to be found, and colour would
            // invite the tap. Absent on a new weighing, and on one a visit owns (ADR-0017).
            if (!state.isNew && state.visitId == null) {
                TextButton(
                    onClick = viewModel::requestDelete,
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

    // Kotlin note: read into locals first — the compiler smart-casts a local `val` to non-null
    // after the check, which it will not do for `state.storedGrams` (a property of a data class
    // could in principle change between two reads).
    val storedGrams = state.storedGrams
    val storedAt = state.storedRecordedAt
    if (state.confirmingDelete && storedGrams != null && storedAt != null) {
        DeleteWeighingDialog(
            grams = storedGrams,
            recordedAt = storedAt,
            unit = state.unit,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    if (state.collision.isNotEmpty()) {
        val clashingVisit = state.collisionVisitId
        if (clashingVisit == null) {
            CollisionDialog(
                count = state.collision.size,
                onReplace = viewModel::replaceExisting,
                onAddSecond = viewModel::addSecond,
                onDismiss = viewModel::cancelCollision,
            )
        } else {
            VisitCollisionDialog(
                onAddSecond = viewModel::addSecond,
                onOpenVisit = { onOpenVisit(clashingVisit) },
                onDismiss = viewModel::cancelCollision,
            )
        }
    }

    state.flagDrop?.let { drop ->
        TrendFlagDialog(
            bunnyName = state.bunnyName,
            drop = drop,
            unit = state.unit,
            onAcknowledge = viewModel::acknowledge,
            onDismiss = viewModel::dismissFlag,
            // The third of the flag's three hosts (ADR-0001). A watch cannot already be running
            // here in any way this screen can see — it has no watch flow — so the action is always
            // on offer, and starting one over a running one is an upsert either way.
            secondaryAction = { StartWatchAction(state.bunnyName, viewModel::startWatch) },
        )
    }
}

/**
 * **Entry is always in grams**, whatever the display preference is set to — that is what a scale
 * reads out (house rule). When the owner reads kilograms, the conversion is echoed underneath rather
 * than the field switching unit, so what they type and what they see are never the same box.
 *
 * **`6e` makes this the only oversized input in the app**: 72dp at `headlineMedium`, because one
 * number is the whole point of the route and everything else here is a qualifier on it. The floating
 * label went with the size — the drawing folds it into the help line below, where it stays readable
 * while the owner types instead of shrinking away exactly then.
 *
 * The focused border needs no special colour despite `6f` calling for one. It asks for primary at
 * **tone 80** rather than 40, because 40 at 2dp disappears on a dark container — and an M3 dark
 * scheme already *is* tone 80 at `primary`, so reading the role gives the drawing's answer in both
 * themes for free. The 2dp is M3's own focused-indicator default.
 */
@Composable
private fun GramsField(
    state: WeightEntryUiState,
    onGramsChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        OutlinedTextField(
            value = state.grams,
            onValueChange = onGramsChanged,
            isError = state.gramsInvalid,
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = state.visitId == null,
            singleLine = true,
            shape = RoundedCornerShape(FieldRadius),
            modifier = Modifier.fillMaxWidth().height(HeroFieldHeight),
        )
        // Help and error are siblings rather than `supportingText`, which is Forms.kt's rule and
        // also the only way to keep the box itself 72dp: a text field's height modifier covers its
        // supporting slot too, so a supporting line would come out of the number's own room.
        if (state.gramsInvalid) {
            ErrorText(stringResource(R.string.weight_grams_required))
        } else {
            HelpText(stringResource(R.string.weight_grams_help))
        }

        val grams = state.parsedGrams
        if (grams != null && state.unit == WeightUnit.KILOGRAMS) {
            HelpText(
                stringResource(R.string.weight_grams_as_kilograms, weightLabel(grams, WeightUnit.KILOGRAMS)),
            )
        }

        // `6e`'s one addition. Absent rather than empty on a bunny with no history: "Recent
        // weighings:" followed by nothing would be a sentence about missing data, which is the one
        // thing this app does not do (ADR-0001).
        if (state.recentGrams.isNotEmpty()) {
            HelpText(stringResource(R.string.weight_recent, state.recentGrams.joinToString(", ")))
        }
    }
}

/** `6e`'s 72dp. The only input in the app that is not the height of a line of text. */
private val HeroFieldHeight = 72.dp

/**
 * **One** confirmation. ADR-0004's two-stage ceremony exists for destroying a bunny's whole
 * history; a single weighing is a correction, and the dialog names the reading so the owner can see
 * which one they are about to lose.
 *
 * It names the **stored** reading, not what is in the fields — a half-typed correction is not what
 * is being deleted.
 */
@Composable
private fun DeleteWeighingDialog(
    grams: Int,
    recordedAt: Instant,
    unit: WeightUnit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.weight_delete_body,
                    weightLabel(grams, unit),
                    dateTimeLabel(recordedAt),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The exact-timestamp collision prompt (ADR-0021), **defaulting to replace** — which is the
 * confirm button here, since that is the commonest correction by far: type `250`, watch the flag
 * fire, retype `2500` for the same minute.
 */
@Composable
private fun CollisionDialog(
    count: Int,
    onReplace: () -> Unit,
    onAddSecond: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_collision_title)) },
        text = { Text(pluralStringResource(R.plurals.weight_collision_body, count, count)) },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.weight_collision_replace)) } },
        dismissButton = {
            TextButton(onClick = onAddSecond) { Text(stringResource(R.string.weight_collision_add_second)) }
        },
    )
}

/**
 * The same collision, against a **visit-recorded** weighing — and *replace* is not on it (ADR-0021's
 * amendment).
 *
 * Every visit on a day lands at the same noon, so this is reachable by accident rather than by
 * contrivance: without it, typing a weight for a day the bunny saw the vet would quietly rewrite the
 * vet's number while the row went on claiming the visit. The two honest answers are to keep both
 * readings, or to go and correct the visit — so those are the two buttons, and the third is absent
 * rather than merely not the default.
 */
@Composable
private fun VisitCollisionDialog(
    onAddSecond: () -> Unit,
    onOpenVisit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_collision_title)) },
        text = { Text(stringResource(R.string.weight_collision_visit_body)) },
        confirmButton = {
            TextButton(onClick = onAddSecond) { Text(stringResource(R.string.weight_collision_add_second)) }
        },
        dismissButton = { TextButton(onClick = onOpenVisit) { Text(stringResource(R.string.weight_open_visit)) } },
    )
}
