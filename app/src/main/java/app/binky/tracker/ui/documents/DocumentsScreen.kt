package app.binky.tracker.ui.documents

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.binky.tracker.R
import app.binky.tracker.ui.appViewModelExtras
import app.binky.tracker.ui.weight.instantDateLabel
import coil3.compose.AsyncImage

/**
 * A bunny's paperwork: a list of documents, and the scanner behind the "+".
 *
 * A **detail** screen reached from More, like the photo gallery — the shell's switcher steps aside
 * while it is open, so this carries its own app bar and the bunny's name in it.
 *
 * A list rather than the gallery's grid, and that is what a document *is*: a title, a date and a
 * page count are what tell one vaccination card from another, where a thumbnail of a sheet of A4
 * tells you nothing. The thumbnail is there too, small, because it distinguishes a printed form
 * from a handwritten note at a glance.
 *
 * [readOnly] is the archived scope (ADR-0004): scanning is **absent** rather than present and
 * refusing, and every document is still here to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    bunnyId: String,
    readOnly: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenDocument: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DocumentsViewModel =
        viewModel(
            key = "documents-$bunnyId",
            factory = DocumentsViewModel.factory(bunnyId),
            extras = appViewModelExtras(),
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // What a finished scan is called until the owner says otherwise. Read here rather than kept as
    // a constant, so the default is in the owner's language at the moment they scanned it — the
    // title goes into the database and must not change when ADR-0013's switcher does.
    val defaultTitle = stringResource(R.string.document_default_title)

    // **No naming dialog between the scan and the record.** The scan is the work; a modal asking for
    // a title before anything is saved is one more thing to lose to a low-memory kill while the
    // camera is still unwinding. It saves under the default and opens the document, where the title
    // is the first thing on screen and one tap from editable.
    val startScan =
        rememberDocumentScan(scanner = rememberDocumentScanner()) { result ->
            viewModel.save(title = defaultTitle, pages = result.pages, guided = result.guided)
        }

    ScanNoticeHost(
        notice = state.notice,
        onShown = viewModel::noticeShown,
        snackbarHostState = snackbarHostState,
    )

    // A scan that has landed opens its own document, so the owner is looking at what they just
    // scanned rather than at a list they have to find it in.
    val onOpened by rememberUpdatedState(onOpenDocument)
    val openedHandled by rememberUpdatedState(viewModel::openedHandled)
    LaunchedEffect(state.opened) {
        val id = state.opened ?: return@LaunchedEffect
        openedHandled()
        onOpened(id)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.documents_title, state.bunnyName)) },
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
                    IconButton(onClick = startScan, enabled = !state.saving) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.document_scan),
                        )
                    }
                }
            },
            // The shell's Scaffold already padded past the status bar; a TopAppBar pads again.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        when {
            state.loading -> Unit
            state.documents.isEmpty() -> EmptyDocuments(readOnly = readOnly)
            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentListRow(document = document, onClick = { onOpenDocument(document.id) })
                        HorizontalDivider()
                    }
                }
        }
    }
}

@Composable
private fun DocumentListRow(
    document: DocumentRow,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    ) {
        PageThumbnail(document)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(text = document.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    stringResource(
                        // "Dated" only where the owner read a date off the page; everything else says
                        // when it was scanned, because the two are different claims (see DocumentEntity).
                        if (document.hasDate) R.string.document_dated_on else R.string.document_scanned_on,
                        instantDateLabel(document.dated),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.document_page_count,
                        document.pageCount,
                        document.pageCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** **Missing media renders as a placeholder, never a crash** (house rule). */
@Composable
private fun PageThumbnail(document: DocumentRow) {
    val missing = rememberVectorPainter(Icons.Filled.Info)
    Box(
        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = remember(document.thumbnail) { document.thumbnail?.let(Uri::fromFile) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = missing,
            fallback = missing,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EmptyDocuments(readOnly: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.documents_empty), style = MaterialTheme.typography.titleMedium)
        if (!readOnly) {
            Text(
                text = stringResource(R.string.documents_empty_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Says the one thing a scan owes the owner, once, through the shell's snackbar host.
 *
 * The fallback notice is **after** the fact and never a prompt: the scan already worked, and what
 * it lost is auto-crop and page detection — an absence the owner cannot act on is not worth a
 * dialog, and the difference in the result is (ADR-0009).
 */
@Composable
internal fun ScanNoticeHost(
    notice: ScanNotice?,
    onShown: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(notice) {
        val message =
            when (notice) {
                ScanNotice.FellBackToCamera -> resources.getString(R.string.document_scan_camera_used)
                ScanNotice.Failed -> resources.getString(R.string.document_scan_failed)
                null -> return@LaunchedEffect
            }
        snackbarHostState.showSnackbar(message)
        shown()
    }
}
