package app.binky.tracker.ui.photos

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.newCameraTarget
import app.binky.tracker.ui.weight.instantDateLabel
import coil3.compose.AsyncImage

/**
 * A bunny's photo gallery: a grid of tiles, and a full-screen viewer behind a tap.
 *
 * A **detail** screen reached from More, like the archived list and Settings — the shell's switcher
 * steps aside while it is open, so the gallery carries its own app bar and the bunny's name in it.
 *
 * [readOnly] is the archived scope (ADR-0004): the write actions are **absent** rather than present
 * and refusing. Archiving destroys nothing, so every photo is still here to look at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryScreen(
    bunnyId: String,
    readOnly: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PhotoGalleryViewModel =
        viewModel(
            key = "photo-gallery-$bunnyId",
            factory = PhotoGalleryViewModel.factory(bunnyId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Saveable because the camera app can be in front of us when a low-memory kill lands.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) cameraTarget?.let { viewModel.addAll(listOf(it)) }
        }
    val pickPhotos =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PER_IMPORT)) { uris ->
            // Photo Picker: no storage permission, and only the pictures the owner ticked are ever
            // exposed to the app.
            viewModel.addAll(uris)
        }

    // The import result is announced once, through the shell's snackbar host — the same one the FAB
    // is laid out against, so this cannot end up underneath it.
    val resources = context.resources
    val onShown by rememberUpdatedState(viewModel::resultShown)
    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        val message =
            if (result.unreadable == 0) {
                resources.getQuantityString(R.plurals.photo_import_added, result.added, result.added)
            } else {
                resources.getString(
                    R.string.photo_import_partial,
                    result.added,
                    result.added + result.unreadable,
                    result.unreadable,
                )
            }
        snackbarHostState.showSnackbar(message)
        onShown()
    }

    // The photo the viewer is open on, held by **id** rather than index: an import landing while the
    // viewer is open shifts every index, and the owner would find themselves looking at a different
    // picture than the one they opened.
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.photo_gallery_title, state.bunnyName)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = {
                if (!readOnly) {
                    AddPhotoMenu(
                        onChoose = {
                            pickPhotos.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onTake = {
                            val target = newCameraTarget(context)
                            cameraTarget = target
                            takePhoto.launch(target)
                        },
                    )
                }
            },
            // The shell's Scaffold already padded everything below the status bar; a TopAppBar pads
            // for it again by default.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        state.importing?.let { progress ->
            ImportProgressBar(progress)
        }

        when {
            state.loading -> Unit
            state.photos.isEmpty() -> EmptyGallery(bunnyName = state.bunnyName, readOnly = readOnly)
            else ->
                PhotoGrid(
                    photos = state.photos,
                    bunnyName = state.bunnyName,
                    onOpen = { viewing = it },
                )
        }
    }

    val open = state.photos.indexOfFirst { it.id == viewing }
    if (viewing != null && open >= 0) {
        PhotoViewer(
            photos = state.photos,
            initialIndex = open,
            bunnyName = state.bunnyName,
            readOnly = readOnly,
            onSetCaption = viewModel::setCaption,
            onDelete = viewModel::delete,
            onClose = { viewing = null },
        )
    } else if (viewing != null) {
        // The photo the viewer was open on is gone — deleted from inside it, and the last one at
        // that. Nothing left to page through.
        LaunchedEffect(state.photos.isEmpty()) { viewing = null }
    }
}

/**
 * One "+" rather than two icons in the app bar. Both ways in are labelled with words, which is worth
 * more here than a pair of glyphs — ADR-0012 defers real iconography to the visual pass anyway.
 */
@Composable
private fun AddPhotoMenu(
    onChoose: () -> Unit,
    onTake: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.photo_add),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_add_choose)) },
                onClick = {
                    expanded = false
                    onChoose()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_add_take)) },
                onClick = {
                    expanded = false
                    onTake()
                },
            )
        }
    }
}

/** Determinate, and counted in photos: "adding" with no end in sight reads as a hang. */
@Composable
private fun ImportProgressBar(progress: ImportProgress) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.photo_import_progress, progress.done, progress.total),
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyGallery(
    bunnyName: String,
    readOnly: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.photo_gallery_empty), style = MaterialTheme.typography.titleMedium)
        if (!readOnly) {
            Text(
                text = stringResource(R.string.photo_gallery_empty_help, bunnyName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<GalleryPhoto>,
    bunnyName: String,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        // Adaptive rather than a fixed column count: three columns on a phone, more on a tablet or
        // in landscape, without this screen having to ask how wide it is.
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoTile(
                photo = photo,
                bunnyName = bunnyName,
                onClick = { onOpen(photo.id) },
            )
        }
    }
}

@Composable
private fun PhotoTile(
    photo: GalleryPhoto,
    bunnyName: String,
    onClick: () -> Unit,
) {
    // **Missing media renders as a placeholder, never a crash** (house rule): a restore may
    // legitimately lack pictures, and Coil answers a failed load with this painter rather than
    // throwing.
    val missing = rememberVectorPainter(Icons.Filled.Info)
    Box(
        modifier =
            Modifier
                .padding(2.dp)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = remember(photo.file) { Uri.fromFile(photo.file) },
            contentDescription = photo.caption ?: stringResource(R.string.photo_description, bunnyName),
            contentScale = ContentScale.Crop,
            error = missing,
            fallback = missing,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The full-screen viewer: one photo per page, its date, its caption, and — outside the archived
 * scope — the two things an owner can do to it.
 *
 * Rendered over the grid rather than pushed as its own route. A photo is not a place in the app,
 * and a back stack entry per tap would put "the third picture of Clover" into the restored state
 * after a process death.
 */
@Composable
private fun PhotoViewer(
    photos: List<GalleryPhoto>,
    initialIndex: Int,
    bunnyName: String,
    readOnly: Boolean,
    onSetCaption: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    // The trailing lambda is the page *count*, re-read on every recomposition — so deleting a photo
    // shortens the pager instead of leaving a blank page behind.
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val current = photos.getOrNull(pagerState.currentPage)
    var editingCaption by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    // Back closes the viewer rather than leaving the gallery — the tap that opened it is what Back
    // should undo.
    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.photo_close),
                    )
                }
                Box(modifier = Modifier.weight(1f))
                if (!readOnly && current != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.photo_delete),
                        )
                    }
                }
            }

            val missing = rememberVectorPainter(Icons.Filled.Info)
            HorizontalPager(
                state = pagerState,
                key = { photos[it].id },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                val photo = photos[page]
                AsyncImage(
                    model = remember(photo.file) { Uri.fromFile(photo.file) },
                    contentDescription = photo.caption ?: stringResource(R.string.photo_description, bunnyName),
                    // Fit, not Crop: this is the one place the whole picture is meant to be visible.
                    contentScale = ContentScale.Fit,
                    error = missing,
                    fallback = missing,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (current != null) {
                PhotoDetails(
                    photo = current,
                    readOnly = readOnly,
                    onEditCaption = { editingCaption = true },
                )
            }
        }
    }

    if (editingCaption && current != null) {
        CaptionDialog(
            initial = current.caption.orEmpty(),
            onSave = { caption ->
                editingCaption = false
                onSetCaption(current.id, caption)
            },
            onDismiss = { editingCaption = false },
        )
    }

    if (confirmingDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.photo_delete)) },
            text = { Text(stringResource(R.string.photo_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete(current.id)
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The date and the caption.
 *
 * "Taken" and "Added" are deliberately different words: only a picture whose own metadata carried a
 * date can be said to have been *taken* then. Everything else — screenshots, anything through a
 * messaging app — is dated by when it arrived here, and saying "taken" of those would invent a fact.
 */
@Composable
private fun PhotoDetails(
    photo: GalleryPhoto,
    readOnly: Boolean,
    onEditCaption: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val date = instantDateLabel(photo.takenAt)
        Text(
            text =
                if (photo.dated) {
                    stringResource(R.string.photo_taken_on, date)
                } else {
                    stringResource(R.string.photo_added_on, date)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = photo.caption ?: stringResource(R.string.photo_caption_none),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (photo.caption != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.weight(1f),
            )
            if (!readOnly) {
                TextButton(onClick = onEditCaption) {
                    Text(
                        stringResource(
                            if (photo.caption == null) R.string.photo_caption_add else R.string.photo_caption_edit,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A dialog rather than an inline field: the caption is edited over a photo the owner is looking at,
 * and a field that wrote on every keystroke would put one database write per letter behind it.
 */
@Composable
private fun CaptionDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_caption_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.photo_caption_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The Photo Picker's ceiling for one selection. Well under the platform's own limit — the number
 * exists to bound how long a single import can run, not to fight the picker.
 */
private const val MAX_PER_IMPORT = 50
