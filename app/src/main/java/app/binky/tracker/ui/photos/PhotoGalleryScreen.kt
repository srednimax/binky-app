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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.theme.Spacing
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.common.BinkyDialog
import app.binky.tracker.ui.common.MessageCard
import app.binky.tracker.ui.common.newCameraTarget
import app.binky.tracker.ui.weight.instantDateLabel
import coil3.compose.AsyncImage

/**
 * The grid's bleed to the frame edge — the one place in the app content is not in the 16dp gutter.
 *
 * Carried on the tiles as well as the grid, so the space between two photos is twice it and the
 * space to the screen edge is once: a seam between pictures rather than a margin round each.
 */
private val GridBleed = 2.dp

/** The viewer's own bar, matched to the app bar it opens over. */
private val ViewerBarHeight = 56.dp

/**
 * A bunny's photo gallery: a grid of tiles, and a full-screen viewer behind a tap.
 *
 * A **detail** screen reached from More, like the archived list and Settings — the shell's switcher
 * steps aside while it is open, so the gallery carries its own app bar and the bunny's name in it.
 *
 * [readOnly] is the archived scope (ADR-0004): the write actions are **absent** rather than present
 * and refusing. Archiving destroys nothing, so every photo is still here to look at.
 *
 * ## Phase 7, against `10c` / `10d`
 *
 * **The one screen in Binky where content goes edge to edge**, and the drawing says so in as many
 * words: a photo is not a row, so the grid keeps its 2dp bleed instead of moving into the 16dp
 * gutter every other list sits in. Everything the screen says *about* the photos — the import bar,
 * the viewer's date and caption — stays in the gutter, so the rhythm still holds where there is
 * type.
 *
 * The two things `10c` designs are already the app's: the **determinate** import bar counted in
 * photos rather than a spinner, and the "+" as **one control with two labelled ways in** rather than
 * two glyphs. What changed is where the bar sits and how quietly it reads.
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
    // LocalResources rather than context.resources: the composition is recomposed when the
    // configuration changes (a language switch, ADR-0013), and reading through the context caches a
    // Resources that the switch has already replaced.
    val resources = LocalResources.current
    val onShown by rememberUpdatedState(viewModel::resultShown)
    LaunchedEffect(state.result) {
        val result = state.result ?: return@LaunchedEffect
        val message =
            if (result.unreadable == 0) {
                resources.getQuantityString(R.plurals.photo_import_added, result.added, result.added)
            } else {
                // Pluralised on the *unreadable* count, not the added one: it is the number the
                // trailing clause governs, and the clause is what inflects outside English.
                resources.getQuantityString(
                    R.plurals.photo_import_partial,
                    result.unreadable,
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

    val viewingId = viewing
    when {
        viewingId != null && state.photos.isNotEmpty() -> {
            // Resolved **once**, when the viewer opens; after that the pager owns the position.
            // Re-resolving every recomposition would mean deleting the photo the owner entered on
            // dropped them back to the grid, while deleting one they had swiped to did not — the
            // same gesture with two different outcomes.
            val initialIndex =
                remember(viewingId) { state.photos.indexOfFirst { it.id == viewingId }.coerceAtLeast(0) }
            PhotoViewer(
                photos = state.photos,
                initialIndex = initialIndex,
                bunnyName = state.bunnyName,
                readOnly = readOnly,
                onSetCaption = viewModel::setCaption,
                onDelete = viewModel::delete,
                onClose = { viewing = null },
            )
        }
        // Nothing left to page through. Guarded on `loading` so a viewer restored after a process
        // death is not closed by the empty list the screen has before its first query comes back.
        viewingId != null && !state.loading -> LaunchedEffect(Unit) { viewing = null }
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

/**
 * Determinate, and counted in photos: "adding" with no end in sight reads as a hang.
 *
 * It sits directly under the app bar in the **same 16dp gutter as the type on every other screen**,
 * not in the grid's 2dp bleed — it is the app talking, and the grid below it is the content. The
 * count drops to `onSurfaceVariant` because it is a progress note, not something to read.
 */
@Composable
private fun ImportProgressBar(progress: ImportProgress) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.tight),
        verticalArrangement = Arrangement.spacedBy(Spacing.hair),
    ) {
        Text(
            text = stringResource(R.string.photo_import_progress, progress.done, progress.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The empty state in place, as a card where the first row of the grid would be — `10a`'s rule. */
@Composable
private fun EmptyGallery(
    bunnyName: String,
    readOnly: Boolean,
) {
    MessageCard(
        title = if (readOnly) null else stringResource(R.string.photo_gallery_empty),
        text =
            if (readOnly) {
                stringResource(R.string.photo_gallery_empty)
            } else {
                stringResource(R.string.photo_gallery_empty_help, bunnyName)
            },
        modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.tight),
    )
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
        // The bleed, and the one place it is right. `10c` starts the grid a step below whatever is
        // above it — the app bar, or the import bar when one is running — and takes it to the frame
        // edge on the sides.
        contentPadding = PaddingValues(start = GridBleed, end = GridBleed, top = Spacing.snug, bottom = GridBleed),
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
                .padding(GridBleed)
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
            // A bar of its own rather than a `TopAppBar`: it opens with a **close cross, not a back
            // arrow**, because the viewer renders over the grid instead of being a route the owner
            // navigated to. `10d` keeps that and gives the row the app bar's own 56dp so the two
            // read as the same band when the viewer opens over the grid.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ViewerBarHeight)
                        .padding(horizontal = Spacing.tight),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.photo_close),
                    )
                }
                Box(modifier = Modifier.weight(1f))
                // Delete stays an app-bar icon. A button under the photo would compete with the
                // picture, and it is the only destructive action on the screen.
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
                // The same `surfaceVariant` the tiles take, so a portrait shot fitted into a
                // landscape frame has a ground either side of it rather than a gap.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
        BinkyDialog(
            title = stringResource(R.string.photo_delete),
            onDismiss = { confirmingDelete = false },
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
        ) {
            Text(stringResource(R.string.photo_delete_body))
        }
    }
}

/**
 * The date and the caption.
 *
 * "Taken" and "Added" are deliberately different words: only a picture whose own metadata carried a
 * date can be said to have been *taken* then. Everything else — screenshots, anything through a
 * messaging app — is dated by when it arrived here, and saying "taken" of those would invent a fact.
 *
 * The caption line is the only thing `10d` designs: a **missing** caption stays `onSurfaceVariant`
 * and its action reads *Add one*, so an uncaptioned photo cannot be mistaken for a captioned one
 * whose text failed to load.
 */
@Composable
private fun PhotoDetails(
    photo: GalleryPhoto,
    readOnly: Boolean,
    onEditCaption: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.snug),
        verticalArrangement = Arrangement.spacedBy(Spacing.hair),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text(
                text = photo.caption ?: stringResource(R.string.photo_caption_none),
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (photo.caption != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                // `fill = false` is the whole of `10d`'s caption line: the action belongs *beside*
                // the caption it acts on, not at the far edge of the screen with the width of a
                // phone between them. Compose note — a plain `weight(1f)` makes the text take all
                // the spare room and shove the button to the edge; `fill = false` means "you may
                // grow to the whole row if the caption is long, but take only what you need", so a
                // short caption and its button stay a pair and a long one still wraps rather than
                // pushing the button off.
                modifier = Modifier.weight(1f, fill = false),
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

    BinkyDialog(
        title = stringResource(R.string.photo_caption_label),
        onDismiss = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.photo_caption_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The Photo Picker's ceiling for one selection. Well under the platform's own limit — the number
 * exists to bound how long a single import can run, not to fight the picker.
 */
private const val MAX_PER_IMPORT = 50
