package app.bunny.tracker.ui.bunny

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bunny.tracker.R
import app.bunny.tracker.data.NeuterStatus
import app.bunny.tracker.data.Sex
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Add or edit a bunny (ADR-0016). Reached from the switcher's "Add a bunny" and from the profile on
 * Home — there is no separate bunny-list screen to reach it from (ADR-0015).
 *
 * Unlike the top-level destinations this is a **detail** screen: pushed onto the back stack with its
 * own app bar, so the shell's switcher and bottom bar step aside while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunnyEditorScreen(
    bunnyId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BunnyEditorViewModel =
        viewModel(
            // Keyed, or Compose would hand the "edit Clover" screen the ViewModel it built for
            // "add a bunny" — same type, same back stack.
            key = "bunny-editor-${bunnyId ?: "new"}",
            factory = BunnyEditorViewModel.factory(bunnyId),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Kotlin note: `LaunchedEffect(key)` is useEffect with a dependency array — the block runs when
    // the key changes, and is cancelled if the screen leaves first.
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // The camera writes into a file we choose, so the Uri has to exist before the intent is fired.
    // Saveable because a low-memory kill can happen while the camera app is in front of us.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) cameraTarget?.let(viewModel::onAvatarPicked)
        }
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Photo Picker: no storage permission, and the owner only ever exposes the one photo.
            uri?.let(viewModel::onAvatarPicked)
        }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isNew) R.string.bunny_editor_add_title else R.string.bunny_editor_edit_title,
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
            actions = {
                TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) }
            },
            // The shell's Scaffold is the one owner of window insets: it has already padded
            // everything NavDisplay renders down past the status bar. A TopAppBar pads for the
            // status bar itself by default, which would do it a second time.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.loading) return@Column

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AvatarField(
                state = state,
                onTakePhoto = {
                    val target = newCameraTarget(context)
                    cameraTarget = target
                    takePhoto.launch(target)
                },
                onChoosePhoto = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePhoto = viewModel::onAvatarRemoved,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text(stringResource(R.string.bunny_name_label)) },
                isError = state.nameMissing,
                supportingText = { if (state.nameMissing) Text(stringResource(R.string.bunny_name_required)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            BirthDateField(
                birthDate = state.birthDate,
                approximate = state.birthDateApproximate,
                onBirthDateChanged = viewModel::onBirthDateChanged,
                onApproximateChanged = viewModel::onBirthDateApproximateChanged,
            )

            // Sex and neuter status are here because this is a health tracker: an unspayed female
            // carries a high lifetime risk of uterine cancer, which is context a vet wants
            // (ADR-0016). "Unknown" is a real answer, not a missing one.
            ChoiceField(
                label = stringResource(R.string.bunny_sex_label),
                options = Sex.entries,
                selected = state.sex,
                optionLabel = { sexLabel(it) },
                onSelect = viewModel::onSexChanged,
            )
            ChoiceField(
                label = stringResource(R.string.bunny_neutered_label),
                options = NeuterStatus.entries,
                selected = state.neutered,
                optionLabel = { neuterLabel(it) },
                onSelect = viewModel::onNeuteredChanged,
            )

            OutlinedTextField(
                value = state.breed,
                onValueChange = viewModel::onBreedChanged,
                label = { Text(stringResource(R.string.bunny_breed_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.colour,
                onValueChange = viewModel::onColourChanged,
                label = { Text(stringResource(R.string.bunny_colour_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HousemateField(
                candidates = state.candidates,
                selectedId = state.housemateId,
                onSelect = viewModel::onHousemateChanged,
            )
        }
    }
}

@Composable
private fun AvatarField(
    state: BunnyEditorUiState,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BunnyAvatar(avatar = state.avatar, name = state.name, size = 96.dp)
        Column {
            TextButton(onClick = onTakePhoto) { Text(stringResource(R.string.bunny_avatar_take)) }
            TextButton(onClick = onChoosePhoto) { Text(stringResource(R.string.bunny_avatar_choose)) }
            if (state.avatarPath != null) {
                TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.bunny_avatar_remove)) }
            }
            if (state.avatarFailed) {
                Text(
                    text = stringResource(R.string.bunny_avatar_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Birthday, with the **approximate** flag ADR-0016 requires: rescues routinely arrive with a
 * guessed age, and a false-precision date would misrepresent what the owner actually knows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(
    birthDate: LocalDate?,
    approximate: Boolean,
    onBirthDateChanged: (LocalDate?) -> Unit,
    onApproximateChanged: (Boolean) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.bunny_birthdate_label), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = birthDate?.let { dateLabel(it) } ?: stringResource(R.string.bunny_birthdate_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.bunny_birthdate_set)) }
            if (birthDate != null) {
                TextButton(onClick = { onBirthDateChanged(null) }) {
                    Text(stringResource(R.string.bunny_birthdate_clear))
                }
            }
        }
        if (birthDate != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = approximate, onCheckedChange = onApproximateChanged)
                Text(text = stringResource(R.string.bunny_birthdate_approximate))
            }
            Text(
                text = stringResource(R.string.bunny_birthdate_approximate_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (picking) {
        val today = remember { LocalDate.now() }
        val todayUtc = remember(today) { today.toEpochMillisUtc() }
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = birthDate?.toEpochMillisUtc(),
                // A bunny born next month does not exist, and a fat-fingered year would otherwise
                // render as a negative age forever.
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc

                        override fun isSelectableYear(year: Int) = year <= today.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onBirthDateChanged(it.toLocalDateUtc()) }
                        picking = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * "Lives with" (ADR-0008). Picking **any** member of an existing group joins that group rather than
 * forming a rival pair, so one choice is enough however many bunnies already live together.
 */
@Composable
private fun HousemateField(
    candidates: List<HousemateOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.bunny_lives_with_label), style = MaterialTheme.typography.titleSmall)
        if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.bunny_lives_with_nobody_yet),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.bunny_lives_with_alone)) },
            )
            candidates.forEach { candidate ->
                FilterChip(
                    selected = candidate.id == selectedId,
                    onClick = { onSelect(candidate.id) },
                    label = {
                        Text(
                            if (candidate.archived) {
                                stringResource(R.string.bunny_archived_name, candidate.name)
                            } else {
                                candidate.name
                            },
                        )
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.bunny_lives_with_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A closed vocabulary as a row of chips.
 *
 * Kotlin note: `<T>` here is an ordinary generic, but `T : Enum<T>` bounds it to enums, which is
 * what lets one function serve both [Sex] and [NeuterStatus] without either losing its type.
 */
@Composable
private fun <T : Enum<T>> ChoiceField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

/**
 * A file for the camera to write into, behind a `content://` Uri — a `file://` one has been illegal
 * to hand another app since Android 7. It lands in the cache: MediaFiles re-encodes the shot into
 * `avatars/` and this copy is disposable.
 */
private fun newCameraTarget(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

// The date picker speaks epoch millis at UTC midnight; a birthday is a calendar date with no time
// zone. Converting through UTC on both sides keeps the day the owner tapped the day that is stored.
private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
